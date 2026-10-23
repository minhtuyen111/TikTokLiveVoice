@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tlv.app.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tlv.app.BuildConfig
import com.tlv.app.Graph

@Composable
private fun IconHeader(cover: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    PlanCover(cover, modifier) {
        Box(Modifier.align(Alignment.Center)) { PulseIcon(icon, Color.White, 58.dp) }
    }
}

/**
 * Hộp thoại toàn app: (1) thông báo của quản trị, (2) có bản cập nhật mới, (3) xin bỏ tối ưu hóa pin.
 * Hộp thoại pin chỉ hiện khi chưa được cấp; cấp rồi thì không hiện lại.
 */
@Composable
fun AppDialogs() {
    val ctx = LocalContext.current
    val session by Graph.auth.session.collectAsState()
    val upd by Graph.updateInfo.collectAsState()
    val ann by Graph.announcement.collectAsState()
    val tick by Graph.resumeTick.collectAsState()
    var updLater by rememberSaveable { mutableStateOf(false) }
    var batLater by rememberSaveable { mutableStateOf(false) }

    val needBattery = remember(tick) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        pm != null && !pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    val u = upd
    val force = u != null && u.force(BuildConfig.VERSION_CODE)
    if (u != null && (!updLater || force)) {
        FancyDialog(
            onDismiss = { updLater = true }, dismissible = !force, glow = Sky, headerHeight = 112.dp,
            header = { IconHeader(0, Icons.Filled.SystemUpdate, Modifier.matchParentSize()) },
        ) {
            androidx.compose.material3.Text("Có bản cập nhật mới", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = PopupText)
            androidx.compose.material3.Text("v${u.name.ifBlank { u.code.toString() }}", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold)
            if (u.notes.isNotBlank()) androidx.compose.material3.Text(u.notes, color = PopupDim, lineHeight = 21.sp)
            if (force) androidx.compose.material3.Text("Bản này đã cũ, bạn cần cập nhật để tiếp tục sử dụng.", color = PopupDim)
            NeonButton("Cập nhật ngay", {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.SystemUpdate)
            if (!force) NeonButton("Để sau", { updLater = true }, Modifier.fillMaxWidth(), outlined = true)
        }
        return
    }

    val a = ann
    if (session != null && a != null) {
        AnnouncementDialog(a.title, a.body) { Graph.dismissAnnouncement() }
        return
    }

    if (session != null && needBattery && !batLater) {
        FancyDialog(
            onDismiss = { batLater = true }, glow = Ok, headerHeight = 112.dp,
            header = { IconHeader(2, Icons.Filled.BatteryChargingFull, Modifier.matchParentSize()) },
        ) {
            androidx.compose.material3.Text("Cho phép chạy nền không giới hạn", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PopupText)
            androidx.compose.material3.Text(
                "Để app đọc bình luận liên tục khi tắt màn hình hoặc đóng app, hãy cho phép bỏ qua tối ưu hóa pin. Bạn chỉ cần làm một lần.",
                color = PopupDim, lineHeight = 21.sp,
            )
            NeonButton("Cho phép", {
                batLater = true
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, Modifier.fillMaxWidth())
            NeonButton("Để sau", { batLater = true }, Modifier.fillMaxWidth(), outlined = true)
        }
    }
}
