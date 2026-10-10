package com.tlv.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val userId: String,
    val email: String,
    val username: String,
    val emailVerified: Boolean,
)

/** Lưu phiên đăng nhập, mã hóa bằng khóa trong Android Keystore. Không ghi log. */
class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "session_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Tùy chọn "ghi nhớ": nếu tắt, phiên chỉ sống trong RAM. */
    @Volatile private var memoryOnly: Session? = null

    fun save(s: Session, remember: Boolean) {
        if (remember) {
            memoryOnly = null
            prefs.edit()
                .putString("at", s.accessToken).putString("rt", s.refreshToken)
                .putLong("exp", s.expiresAtMs).putString("uid", s.userId)
                .putString("email", s.email).putString("user", s.username)
                .putBoolean("ver", s.emailVerified).putBoolean("remember", true)
                .apply()
        } else {
            prefs.edit().clear().apply()
            memoryOnly = s
        }
    }

    fun load(): Session? {
        memoryOnly?.let { return it }
        val at = prefs.getString("at", null) ?: return null
        val rt = prefs.getString("rt", null) ?: return null
        return Session(
            at, rt, prefs.getLong("exp", 0), prefs.getString("uid", "") ?: "",
            prefs.getString("email", "") ?: "", prefs.getString("user", "") ?: "",
            prefs.getBoolean("ver", false),
        )
    }

    fun remembered() = prefs.getBoolean("remember", false)

    fun clear() {
        memoryOnly = null
        prefs.edit().clear().apply()
    }
}
