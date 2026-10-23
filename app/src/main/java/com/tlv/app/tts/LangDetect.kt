package com.tlv.app.tts

/** 5 ngôn ngữ giọng đọc được hỗ trợ; tiếng Việt luôn đứng đầu. */
data class VoiceLang(val tag: String, val native: String, val sample: String)

val VOICE_LANGS = listOf(
    VoiceLang("vi-VN", "Tiếng Việt", "Xin chào, đây là giọng đọc tiếng Việt."),
    VoiceLang("en-US", "English", "Hello, this is the English voice."),
    VoiceLang("zh-CN", "中文", "你好，这是中文语音。"),
    VoiceLang("ja-JP", "日本語", "こんにちは、これは日本語の音声です。"),
    VoiceLang("ko-KR", "한국어", "안녕하세요, 한국어 음성입니다."),
)

fun voiceLangOf(tag: String): VoiceLang =
    VOICE_LANGS.firstOrNull { it.tag.equals(tag, true) }
        ?: VOICE_LANGS.firstOrNull { it.tag.substringBefore('-').equals(tag.substringBefore('-'), true) }
        ?: VOICE_LANGS.first()

/** Nhận ngôn ngữ của một bình luận theo chữ viết; chữ Latin không dấu mơ hồ nên rơi về ngôn ngữ đã chọn. */
object LangDetect {
    private const val VI_CHARS = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"
    private val enWords = setOf("the", "and", "you", "is", "are", "i", "to", "hello", "hi", "love", "thanks", "thank", "please",
        "good", "nice", "wow", "like", "what", "how", "my", "your", "this", "that", "it", "for", "with", "can", "me", "we", "so", "lol")

    fun detect(text: String, fallback: String): String {
        var han = 0; var kana = 0; var hangul = 0
        for (ch in text) {
            val c = ch.code
            when {
                c in 0xAC00..0xD7AF || c in 0x1100..0x11FF -> hangul++
                c in 0x3040..0x30FF -> kana++
                c in 0x4E00..0x9FFF -> han++
            }
        }
        if (hangul > 0) return "ko-KR"
        if (kana > 0) return "ja-JP"
        if (han > 0) return "zh-CN"
        val lower = text.lowercase()
        if (lower.any { it in VI_CHARS }) return "vi-VN"
        val words = lower.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if (words.isNotEmpty() && words.any { it in enWords }) return "en-US"
        return fallback
    }
}
