package com.tlv.app.settings

data class AppSettings(
    // Giọng đọc
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLang: String = "vi-VN",
    val ttsVoice: String = "",
    val readUsername: Boolean = true,
    val readEmoji: Boolean = false,
    // Bộ lọc
    val blockWords: List<String> = emptyList(),
    val priorityWords: List<String> = emptyList(),
    val skipDuplicates: Boolean = true,
    val maxLength: Int = 200,
    val maxPerMinute: Int = 30,
    val queueMax: Int = 50,
    // Kết nối
    val bridgeUrl: String = "",
    val lastChannel: String = "",
    // Giao diện
    val darkTheme: Boolean = true,
    val accent: Int = 0, // 0 tím, 1 xanh tím, 2 xanh dương
    val reduceMotion: Boolean = false,
    // Thông báo
    val notifShowControls: Boolean = true,
)
