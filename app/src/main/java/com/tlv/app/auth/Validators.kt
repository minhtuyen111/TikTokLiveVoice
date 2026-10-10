package com.tlv.app.auth

object Validators {
    private val USERNAME = Regex("^[A-Za-z0-9_.]{3,20}$")
    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun username(s: String): String? = when {
        !USERNAME.matches(s) -> "Tên đăng nhập 3–20 ký tự: chữ, số, dấu _ hoặc ."
        else -> null
    }

    fun email(s: String): String? = if (EMAIL.matches(s.trim())) null else "Email không hợp lệ"

    fun password(s: String): String? = when {
        s.length < 8 -> "Mật khẩu tối thiểu 8 ký tự"
        s.none { it.isDigit() } -> "Mật khẩu cần ít nhất 1 chữ số"
        s.none { it.isLetter() } -> "Mật khẩu cần ít nhất 1 chữ cái"
        else -> null
    }

    /** 0..4 */
    fun strength(s: String): Int {
        var n = 0
        if (s.length >= 8) n++
        if (s.length >= 12) n++
        if (s.any { it.isUpperCase() } && s.any { it.isLowerCase() }) n++
        if (s.any { it.isDigit() } && s.any { !it.isLetterOrDigit() }) n++
        return n
    }
}
