package com.tlv.app.filter

/**
 * Đổi dấu câu, ký hiệu và biểu tượng cảm xúc thành chữ để máy đọc được ("!" -> "chấm than", ":)))" -> "cười").
 * Thuần Kotlin để dễ kiểm thử. Hỗ trợ 5 ngôn ngữ giọng đọc: vi, en, zh, ja, ko.
 */
object SpeechText {
    private val LANGS = listOf("vi", "en", "zh", "ja", "ko")

    // Biểu tượng cảm xúc dạng chữ: khớp theo thứ tự từ trên xuống (mẫu cụ thể trước).
    private class Emo(val re: Regex, val words: List<String>)
    private val EMOS = listOf(
        Emo(Regex("<3+"), listOf("trái tim", "heart", "爱心", "ハート", "하트")),
        Emo(Regex(";-?[)D]+"), listOf("nháy mắt", "wink", "眨眼", "ウインク", "윙크")),
        Emo(Regex("[:=]-?D+|xD+|XD+"), listOf("cười lớn", "big laugh", "大笑", "大笑い", "크게 웃음")),
        Emo(Regex("[:=]-?[Pp]+(?![A-Za-z])"), listOf("lè lưỡi", "tongue out", "吐舌头", "あっかんべー", "혀 내밀기")),
        Emo(Regex("[:=]-?[Oo0](?![A-Za-z0-9])"), listOf("ngạc nhiên", "surprised", "惊讶", "びっくり", "놀람")),
        Emo(Regex("[:=]'-?\\(+|T[_.]T|;[_;]"), listOf("khóc", "crying", "哭", "泣く", "울음")),
        Emo(Regex("[:=]-?[(\\[]+|\\)+-?[:=]"), listOf("buồn", "sad", "难过", "悲しい", "슬픔")),
        Emo(Regex("[:=]-?[)\\]]+|\\^[_.]?\\^|\\(:"), listOf("cười", "smile", "微笑", "にっこり", "미소")),
        Emo(Regex("[8B]-?\\)"), listOf("ngầu", "cool", "酷", "クール", "멋짐")),
        Emo(Regex("-_-+"), listOf("chán", "bored", "无聊", "退屈", "지루함")),
        Emo(Regex(":\\*+"), listOf("hôn", "kiss", "亲亲", "キス", "뽀뽀")),
    )

    // Tên ký hiệu theo từng ngôn ngữ: [vi, en, zh, ja, ko].
    private val SYMBOLS: Map<Char, List<String>> = mapOf(
        '.' to listOf("chấm", "dot", "点", "ten", "점"),
        ',' to listOf("phẩy", "comma", "逗号", "コンマ", "쉼표"),
        '!' to listOf("chấm than", "exclamation mark", "感叹号", "びっくりマーク", "느낌표"),
        '?' to listOf("chấm hỏi", "question mark", "问号", "はてなマーク", "물음표"),
        ':' to listOf("hai chấm", "colon", "冒号", "コロン", "콜론"),
        ';' to listOf("chấm phẩy", "semicolon", "分号", "セミコロン", "세미콜론"),
        '<' to listOf("nhỏ hơn", "less than", "小于", "より小さい", "보다 작음"),
        '>' to listOf("lớn hơn", "greater than", "大于", "より大きい", "보다 큼"),
        '(' to listOf("mở ngoặc", "open bracket", "左括号", "かっこ開く", "괄호 열기"),
        ')' to listOf("đóng ngoặc", "close bracket", "右括号", "かっこ閉じる", "괄호 닫기"),
        '/' to listOf("gạch chéo", "slash", "斜杠", "スラッシュ", "슬래시"),
        '@' to listOf("a còng", "at", "艾特", "アットマーク", "골뱅이"),
        '#' to listOf("thăng", "hash", "井号", "シャープ", "샵"),
        '%' to listOf("phần trăm", "percent", "百分号", "パーセント", "퍼센트"),
        '+' to listOf("cộng", "plus", "加", "プラス", "플러스"),
        '=' to listOf("bằng", "equals", "等于", "イコール", "등호"),
        '*' to listOf("sao", "star", "星号", "アスタリスク", "별표"),
        '&' to listOf("và", "and", "和", "アンド", "앤드"),
        '~' to listOf("ngã", "tilde", "波浪号", "チルダ", "물결표"),
    )
    private val TIMES = listOf("lần", "times", "次", "回", "번")

    private fun idx(lang: String): Int = LANGS.indexOf(lang.substringBefore('-').lowercase()).let { if (it < 0) 1 else it }

    fun convert(text: String, lang: String): String {
        val li = idx(lang)
        var t = text
        for (e in EMOS) t = t.replace(e.re, " ${e.words[li]} ")
        val sb = StringBuilder(t.length + 16)
        var i = 0
        while (i < t.length) {
            val ch = t[i]
            val names = SYMBOLS[ch]
            if (names == null) { sb.append(ch); i++; continue }
            var j = i
            while (j < t.length && t[j] == ch) j++
            val n = j - i
            // Giữ nguyên dấu nằm giữa hai chữ số: 3.5, 1,000, 10:30
            val prevDigit = i > 0 && t[i - 1].isDigit()
            val nextDigit = j < t.length && t[j].isDigit()
            if (n == 1 && prevDigit && nextDigit && (ch == '.' || ch == ',' || ch == ':')) { sb.append(ch); i = j; continue }
            sb.append(' ').append(names[li])
            if (n >= 2) sb.append(' ').append(n).append(' ').append(TIMES[li])
            sb.append(' ')
            i = j
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
