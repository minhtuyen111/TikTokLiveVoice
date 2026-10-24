package com.tlv.app.filter

/**
 * Chuẩn hóa tiếng Việt trước khi đọc để máy KHÔNG bỏ sót/đọc sai: số -> chữ, "5k" -> "năm nghìn",
 * chữ viết tắt/tiếng lóng chat ("ko", "đc", "vs"...), tiếng cười ("kkk", "hihi"), chữ kéo dài ("quááááá").
 * Thuần Kotlin để dễ kiểm thử.
 */
object ViText {
    private val D = arrayOf("không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín")

    private val SLANG = mapOf(
        "ko" to "không", "k" to "không", "hok" to "không", "hông" to "không", "kh" to "không", "khg" to "không",
        "đc" to "được", "dc" to "được", "đk" to "được", "vs" to "với", "j" to "gì", "r" to "rồi",
        "ok" to "ô kê", "oke" to "ô kê", "okie" to "ô kê", "okay" to "ô kê",
        "mn" to "mọi người", "ae" to "anh em", "bn" to "bao nhiêu", "bt" to "bình thường", "cx" to "cũng",
        "mik" to "mình", "mk" to "mình", "ny" to "người yêu", "nc" to "nói chuyện", "ib" to "nhắn tin",
        "cmt" to "bình luận", "kq" to "kết quả", "ntn" to "như thế nào", "tks" to "cảm ơn", "thks" to "cảm ơn",
        "thanks" to "cảm ơn", "pls" to "làm ơn", "plz" to "làm ơn", "sdt" to "số điện thoại", "dt" to "điện thoại",
        "fb" to "phây búc", "ig" to "in-sta-gram", "tt" to "tích tốc", "vn" to "Việt Nam", "ad" to "quản trị viên",
        "admin" to "quản trị viên", "live" to "lai", "follow" to "theo dõi", "share" to "chia sẻ", "like" to "lai",
    )

    private val LAUGH = Regex("^(?:k{2,}|(?:h[aeiêăâ]){2,}h?|(?:ha)+h?)$", RegexOption.IGNORE_CASE)
    private val REPEAT = Regex("(\\p{L})\\1{2,}")
    private val K_SUFFIX = Regex("(\\d+)\\s?[kK](?![\\p{L}\\d])")
    private val THOUSANDS = Regex("\\d{1,3}(?:\\.\\d{3})+(?!\\d)")
    private val DECIMAL = Regex("(\\d+)[.,](\\d+)")
    private val DIGITS = Regex("\\d+")
    private val WORD = Regex("\\p{L}+")

    fun normalize(text: String): String {
        var t = text
        // Nhận tiếng cười TRƯỚC khi thu gọn chữ lặp (nếu không "kkkkk" bị thu thành "k").
        t = WORD.replace(t) { m -> if (LAUGH.matches(m.value)) "ha ha" else m.value }
        t = REPEAT.replace(t) { it.groupValues[1] } // quááááá -> quá
        t = K_SUFFIX.replace(t) { numWords(it.groupValues[1]) + " nghìn" }
        t = THOUSANDS.replace(t) { numWords(it.value.replace(".", "")) }
        t = DECIMAL.replace(t) { m ->
            val frac = m.groupValues[2]
            val fracWords = if (frac.startsWith("0") || frac.length > 2) spell(frac) else numWords(frac)
            numWords(m.groupValues[1]) + " phẩy " + fracWords
        }
        t = DIGITS.replace(t) { numWords(it.value) }
        t = WORD.replace(t) { m ->
            val w = m.value
            if (LAUGH.matches(w)) "ha ha" else SLANG[w.lowercase()] ?: w
        }
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** Đọc từng chữ số (số điện thoại, mã…). */
    fun spell(digits: String): String = digits.map { D[it - '0'] }.joinToString(" ")

    /** Số nguyên dương dạng chuỗi -> chữ. Số bắt đầu bằng 0 hoặc quá dài đọc từng chữ số. */
    fun numWords(s: String): String {
        if (s.length > 1 && s.startsWith("0") || s.length > 12) return spell(s)
        return numToVi(s.toLong())
    }

    fun numToVi(n: Long): String {
        if (n == 0L) return D[0]
        val units = listOf("", " nghìn", " triệu", " tỷ")
        val groups = ArrayList<Int>()
        var x = n
        while (x > 0) { groups.add((x % 1000).toInt()); x /= 1000 }
        val sb = StringBuilder()
        var leading = true
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            if (g == 0) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(group(g, leading)).append(units[i])
            leading = false
        }
        return sb.toString().trim()
    }

    private fun group(n: Int, leading: Boolean): String {
        val h = n / 100; val t = (n / 10) % 10; val u = n % 10
        val sb = StringBuilder()
        if (h > 0 || !leading) sb.append(D[h]).append(" trăm")
        when {
            t > 1 -> {
                sb.append(' ').append(D[t]).append(" mươi")
                when (u) { 0 -> {}; 1 -> sb.append(" mốt"); 5 -> sb.append(" lăm"); else -> sb.append(' ').append(D[u]) }
            }
            t == 1 -> {
                sb.append(" mười")
                when (u) { 0 -> {}; 5 -> sb.append(" lăm"); else -> sb.append(' ').append(D[u]) }
            }
            u > 0 -> if (h > 0 || !leading) sb.append(" linh ").append(D[u]) else sb.append(D[u])
        }
        return sb.toString().trim()
    }
}
