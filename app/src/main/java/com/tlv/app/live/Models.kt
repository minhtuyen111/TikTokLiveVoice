package com.tlv.app.live

enum class CommentStatus { PENDING, SPEAKING, SPOKEN, FILTERED }

data class Comment(
    val id: Long,
    val user: String,
    val text: String,
    val time: Long,
    val status: CommentStatus,
    val filterNote: String = "",
)

sealed interface ConnState {
    data object Idle : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
    data class Reconnecting(val attempt: Int, val inMs: Long) : ConnState
    data class Failed(val message: String) : ConnState
    data object Ended : ConnState
}

data class Stats(val received: Int = 0, val spoken: Int = 0, val queued: Int = 0, val filtered: Int = 0)

object ChannelParser {
    private val FROM_URL = Regex("@([A-Za-z0-9_.]{2,24})")
    private val PLAIN = Regex("^[A-Za-z0-9_.]{2,24}$")

    /** Chấp nhận "@name", "name" hoặc đường dẫn tiktok.com/@name/live. Trả null nếu không hợp lệ. */
    fun parse(input: String): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        if (t.contains("tiktok.com", ignoreCase = true)) return FROM_URL.find(t)?.groupValues?.get(1)
        val n = t.removePrefix("@")
        return if (PLAIN.matches(n)) n else null
    }
}
