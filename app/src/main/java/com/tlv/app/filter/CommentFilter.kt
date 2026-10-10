package com.tlv.app.filter

import com.tlv.app.settings.AppSettings

enum class Reason { EMPTY, TOO_LONG, BLOCKED, DUPLICATE }

data class Decision(
    val accepted: Boolean,
    val priority: Boolean = false,
    val text: String = "",
    val reason: Reason? = null,
)

/** Thuần Kotlin (không phụ thuộc Android) để dễ kiểm thử. Chỉ ảnh hưởng hiển thị/đọc. */
object CommentFilter {
    private val EMOJI = Regex("[\\p{So}\\p{Cs}\\u200d\\ufe0f\\u20e3]")
    private val CONTROL = Regex("[\\p{Cc}\\p{Cf}&&[^\\u200d]]")
    private val SPACES = Regex("\\s+")

    /** Làm sạch văn bản bất thường: ký tự điều khiển, khoảng trắng thừa, (tùy chọn) emoji. */
    fun clean(raw: String, keepEmoji: Boolean): String {
        var t = raw.replace(CONTROL, " ")
        if (!keepEmoji) t = t.replace(EMOJI, " ")
        return t.replace(SPACES, " ").trim()
    }

    fun key(text: String) = text.lowercase().replace(SPACES, " ")

    /**
     * @param recent các khóa văn bản gần đây (để bỏ trùng)
     */
    fun decide(s: AppSettings, rawText: String, recent: Collection<String>): Decision {
        val shown = clean(rawText, keepEmoji = true)
        val spoken = clean(rawText, keepEmoji = s.readEmoji)
        if (shown.isEmpty() || spoken.none { it.isLetterOrDigit() }) return Decision(false, reason = Reason.EMPTY)
        if (shown.length > s.maxLength) return Decision(false, reason = Reason.TOO_LONG)
        val low = shown.lowercase()
        if (s.blockWords.any { it.isNotBlank() && low.contains(it.lowercase()) })
            return Decision(false, reason = Reason.BLOCKED)
        if (s.skipDuplicates && recent.contains(key(shown))) return Decision(false, reason = Reason.DUPLICATE)
        val prio = s.priorityWords.any { it.isNotBlank() && low.contains(it.lowercase()) }
        return Decision(true, prio, spoken)
    }
}
