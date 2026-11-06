package dev.nekuz.intentlab

object ShellQuote {
    private val safe = Regex("[A-Za-z0-9_@%+=:,./-]+")

    fun isSafe(value: String): Boolean = safe.matches(value)

    fun quote(value: String): String =
        "'" + value.replace("\u0000", "").replace("'", "'\\''") + "'"

    fun quoteIfNeeded(value: String): String = if (isSafe(value)) value else quote(value)
}
