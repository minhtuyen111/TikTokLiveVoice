package com.tlv.app.settings

data class AppSettings(
    // Giọng đọc
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLang: String = "vi-VN",
    val ttsVoice: String = "",
    val ttsGender: String = "female", // "female" | "male": giọng nữ/nam của giọng online
    val ttsViOffline: Boolean = true, // tiếng Việt dùng giọng tích hợp trong app trước (chạy mọi ROM, không cần mạng)
    val ttsCloud: Boolean = true, // dùng giọng online chất lượng cao (tự rơi về giọng hệ thống khi mất mạng)
    val readUsername: Boolean = true,
    val readEmoji: Boolean = false,
    val readSymbols: Boolean = true, // đọc dấu câu và biểu tượng cảm xúc dạng chữ (. ! ? :))) < >)
    val ttsVolume: Float = 1.0f,
    val ttsAuto: Boolean = false, // tự nhận ngôn ngữ từng bình luận (Việt/Anh/Trung/Nhật/Hàn)
    // Bộ lọc
    val blockWords: List<String> = emptyList(),
    val priorityWords: List<String> = emptyList(),
    val skipDuplicates: Boolean = true,
    val maxLength: Int = 200,
    val maxPerMinute: Int = 30,
    val queueMax: Int = 50,
    // Kết nối
    val bridgeUrl: String = "", // bộ nhớ đệm địa chỉ máy chủ kết nối (tự lấy từ máy chủ, người dùng không nhập)
    val lastChannel: String = "",
    // Âm thanh sự kiện: "off" | "random" | id âm thanh do admin cung cấp
    val memeFollow: String = "random",
    val memeShare: String = "random",
    val memeGift: String = "random",
    val announceEvents: Boolean = true, // đọc "ai đã theo dõi / chia sẻ / tặng quà"
    val memeIdle: String = "random", // âm thanh phát khi hết hàng chờ
    val memeIdleGap: Int = 15, // giây giữa hai lần phát âm đợi khi vẫn chưa có gì mới
    // Giao diện
    val darkTheme: Boolean = true,
    val accent: Int = 0, // 0 tím, 1 xanh tím, 2 xanh dương, 3 hồng, 4 xanh lá, 5 cam
    val bgStyle: Int = 0, // 0 đêm, 1 đen AMOLED, 2 đại dương, 3 rừng, 4 hoàng hôn, 5 xám đá
    val appLang: String = "vi",
    val reduceMotion: Boolean = false,
    // Thông báo
    val notifShowControls: Boolean = true,
)
