package com.tlv.app.filter

/** Câu đọc "ai đã theo dõi / chia sẻ / tặng quà" cho 5 ngôn ngữ. Thuần Kotlin để dễ kiểm thử. */
object EventText {
    private val LANGS = listOf("vi", "en", "zh", "ja", "ko")
    private val SOMEONE = listOf("Một người xem", "Someone", "有人", "誰か", "누군가")

    private val GIFTS_VI = mapOf(
        "rose" to "hoa hồng", "heart" to "trái tim", "finger heart" to "tim ngón tay", "gg" to "gi gi",
        "hand hearts" to "tim đôi tay", "ice cream cone" to "kem ốc quế", "doughnut" to "bánh vòng",
        "perfume" to "nước hoa", "love you" to "yêu bạn", "galaxy" to "thiên hà", "lion" to "sư tử",
        "universe" to "vũ trụ", "corgi" to "chó corgi", "swan" to "thiên nga", "tiktok" to "tích tốc",
    )

    private fun idx(lang: String) = LANGS.indexOf(lang.substringBefore('-').lowercase()).let { if (it < 0) 0 else it }

    /** Làm sạch tên hiển thị: bỏ emoji/ký hiệu lạ, đổi _ và . thành khoảng trắng, tối đa 30 ký tự. */
    fun cleanName(raw: String): String {
        val t = CommentFilter.clean(raw, keepEmoji = false)
            .map { if (it.isLetterOrDigit() || it == ' ') it else if (it == '_' || it == '.' || it == '-') ' ' else ' ' }
            .joinToString("").replace(Regex("\\s+"), " ").trim().take(30)
        return if (t.any { it.isLetterOrDigit() }) t else ""
    }

    fun line(lang: String, kind: String, user: String, gift: String, count: Int): String {
        val li = idx(lang)
        val who = user.ifBlank { SOMEONE[li] }
        val n = count.coerceAtLeast(1)
        val g = gift.trim().let { if (li == 0) GIFTS_VI[it.lowercase()] ?: it else it }
        return when (kind) {
            "follow" -> when (li) { 0 -> "$who đã theo dõi"; 1 -> "$who followed you"; 2 -> "$who 关注了你"; 3 -> "$who さんがフォローしました"; else -> "$who 님이 팔로우했어요" }
            "share" -> when (li) { 0 -> "$who đã chia sẻ"; 1 -> "$who shared the live"; 2 -> "$who 分享了直播"; 3 -> "$who さんがシェアしました"; else -> "$who 님이 공유했어요" }
            else -> when (li) {
                0 -> "$who đã tặng " + if (g.isBlank()) "quà" else (if (n > 1) "$n " else "") + g
                1 -> if (g.isBlank()) "$who sent a gift" else "$who sent " + (if (n > 1) "$n " else "") + g
                2 -> if (g.isBlank()) "$who 送出了礼物" else "$who 送出了 " + (if (n > 1) "$n 个 " else "") + g
                3 -> if (g.isBlank()) "$who さんがギフトを贈りました" else "$who さんが " + (if (n > 1) "$n 個の " else "") + g + " を贈りました"
                else -> if (g.isBlank()) "$who 님이 선물을 보냈어요" else "$who 님이 $g " + (if (n > 1) "${n}개를 " else "") + "선물했어요"
            }
        }
    }
}
