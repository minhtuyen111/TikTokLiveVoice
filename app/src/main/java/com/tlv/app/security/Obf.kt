package com.tlv.app.security

import java.security.MessageDigest
import java.util.Base64

/** Giải mã chuỗi cấu hình đã được che (XOR + Base64) lúc build. Không để lộ URL/khóa dạng chữ trong APK. */
object Obf {
    private val key: ByteArray by lazy { MessageDigest.getInstance("SHA-256").digest("com.tlv.app/vlt".toByteArray()) }

    fun enc(s: String): String {
        val b = s.toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(ByteArray(b.size) { (b[it].toInt() xor key[it % key.size].toInt()).toByte() })
    }

    fun dec(s: String): String {
        if (s.isEmpty()) return ""
        val b = runCatching { Base64.getDecoder().decode(s) }.getOrNull() ?: return ""
        return String(ByteArray(b.size) { (b[it].toInt() xor key[it % key.size].toInt()).toByte() }, Charsets.UTF_8)
    }
}
