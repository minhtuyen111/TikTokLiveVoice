@file:Suppress("DEPRECATION")

package com.tlv.app.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.tlv.app.BuildConfig
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

enum class Verdict { OK, DEBUGGER, HOOK, TAMPER }

/**
 * Lớp phòng thủ chạy lúc thực thi (bản release): phát hiện trình gỡ lỗi, framework hook (Frida/Xposed)
 * và APK bị đóng gói lại (chữ ký khác chữ ký phát hành). Không chặn máy root thông thường để tránh báo nhầm.
 * Đây là rào cản làm khó dịch ngược chứ không phải bảo đảm tuyệt đối.
 */
object Guard {
    fun check(ctx: Context): Verdict {
        if (BuildConfig.DEBUG) return Verdict.OK
        if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return Verdict.DEBUGGER
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger() || tracerPid() > 0) return Verdict.DEBUGGER
        if (hooked()) return Verdict.HOOK
        if (!signatureOk(ctx)) return Verdict.TAMPER
        return Verdict.OK
    }

    /** Gói của các công cụ bẻ khóa/can thiệp thanh toán phổ biến (Lucky Patcher, Freedom, GameGuardian, v.v.). */
    val CHEAT_PACKAGES = listOf(
        "com.chelpus.lackypatch", "com.dimonvideo.luckypatcher", "com.forpda.lp", "uret.jasi2169.patcher",
        "com.android.vending.billing.InAppBillingService.LUCK", "com.android.vending.billing.InAppBillingService.LOCK",
        "com.android.vending.billing.InAppBillingService.CLON", "com.android.vending.billing.InAppBillingService.COIN",
        "cc.madkite.freedom", "com.blackmartalpha", "org.sbtools.gamehack", "com.zune.gamekiller",
        "com.cih.game_cih", "com.xmodgame", "org.creeplays.hack",
    )

    /** Công cụ can thiệp đang cài trên máy (chỉ để chặn chức năng thanh toán, không chặn cả app). */
    fun cheatTools(ctx: Context): List<String> = CHEAT_PACKAGES.filter {
        runCatching { ctx.packageManager.getPackageInfo(it, 0); true }.getOrDefault(false)
    }

    private fun tracerPid(): Int = runCatching {
        File("/proc/self/status").useLines { l ->
            l.firstOrNull { it.startsWith("TracerPid:") }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        }
    }.getOrDefault(0)

    private val badMaps = listOf("frida", "gum-js", "linjector", "libsubstrate", "xposed", "lsposed")

    private fun hooked(): Boolean {
        val inMaps = runCatching {
            File("/proc/self/maps").useLines { l -> l.any { ln -> badMaps.any { ln.contains(it, ignoreCase = true) } } }
        }.getOrDefault(false)
        if (inMaps) return true
        if (runCatching { Class.forName("de.robv.android.xposed.XposedBridge"); true }.getOrDefault(false)) return true
        return runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 120); true } }.getOrDefault(false)
    }

    private fun signatureOk(ctx: Context): Boolean {
        val expected = Obf.dec(BuildConfig.SIG_E).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (expected.isEmpty()) return true // chưa cấu hình vân tay chữ ký: bỏ qua kiểm tra
        val sigs = runCatching {
            val pm = ctx.packageManager
            if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
            } else {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.map { it.toByteArray() }.orEmpty()
            }
        }.getOrDefault(emptyList())
        if (sigs.isEmpty()) return false
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.all { s -> md.digest(s).joinToString("") { "%02x".format(it) } in expected }
    }
}
