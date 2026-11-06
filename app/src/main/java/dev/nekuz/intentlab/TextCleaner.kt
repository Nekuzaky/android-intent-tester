package dev.nekuz.intentlab

object TextCleaner {
    const val MAX_LABEL = 80
    const val MAX_LINE = 300
    const val MARKER = "�"
    private const val MAX_COMBINING_RUN = 3
    private const val ELLIPSIS = "…"
    private val invisibleLetters = setOf(0x034F, 0x115F, 0x1160, 0x17B4, 0x17B5, 0x3164, 0xFFA0)

    fun label(raw: CharSequence?, fallback: String): String {
        val cleaned = clean(raw, MAX_LABEL, keepNewlines = false, marker = "").trim()
        return cleaned.ifEmpty { fallback }
    }

    fun line(raw: CharSequence?, max: Int = MAX_LINE): String =
        clean(raw, max, keepNewlines = false, marker = MARKER).trim()

    fun block(raw: CharSequence?, max: Int): String =
        clean(raw, max, keepNewlines = true, marker = MARKER)

    fun hasHidden(raw: CharSequence?): Boolean {
        if (raw == null) return false
        var index = 0
        while (index < raw.length) {
            val codePoint = Character.codePointAt(raw, index)
            index += Character.charCount(codePoint)
            if (isHidden(codePoint)) return true
        }
        return false
    }

    fun clean(raw: CharSequence?, max: Int, keepNewlines: Boolean, marker: String): String {
        if (raw == null || max <= 0) return ""
        val text = StringBuilder()
        var index = 0
        var combiningRun = 0
        var pendingSpace = false
        while (index < raw.length) {
            val codePoint = Character.codePointAt(raw, index)
            index += Character.charCount(codePoint)
            if (keepNewlines && (codePoint == '\n'.code)) {
                if (text.length + 1 > max) return truncated(text, max)
                text.append('\n')
                pendingSpace = false
                combiningRun = 0
                continue
            }
            if (isSpaceLike(codePoint)) {
                if (keepNewlines) {
                    if (text.length + 1 > max) return truncated(text, max)
                    text.append(' ')
                    combiningRun = 0
                } else {
                    pendingSpace = true
                }
                continue
            }
            if (isHidden(codePoint)) {
                if (marker.isEmpty()) continue
                if (pendingSpace && text.isNotEmpty() && text.last() != '\n') text.append(' ')
                pendingSpace = false
                combiningRun = 0
                if (text.length + marker.length > max) return truncated(text, max)
                text.append(marker)
                continue
            }
            if (isCombining(codePoint)) {
                combiningRun++
                if (combiningRun > MAX_COMBINING_RUN) continue
            } else {
                combiningRun = 0
            }
            val spaceLength = if (pendingSpace && text.isNotEmpty() && text.last() != '\n') 1 else 0
            if (text.length + spaceLength + Character.charCount(codePoint) > max) return truncated(text, max)
            if (spaceLength == 1) text.append(' ')
            pendingSpace = false
            text.appendCodePoint(codePoint)
        }
        return text.toString()
    }

    private fun truncated(text: StringBuilder, max: Int): String {
        if (max <= ELLIPSIS.length) return text.toString()
        if (text.length + ELLIPSIS.length > max) {
            var end = max - ELLIPSIS.length
            if (end > 0 && Character.isLowSurrogate(text[end]) && Character.isHighSurrogate(text[end - 1])) end--
            text.setLength(end)
        }
        return text.append(ELLIPSIS).toString()
    }

    private fun isSpaceLike(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
        else -> codePoint == '\n'.code || codePoint == '\r'.code || codePoint == '\t'.code
    }

    private fun isCombining(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
        else -> false
    }

    private fun isHidden(codePoint: Int): Boolean {
        if (codePoint in invisibleLetters) return true
        if (codePoint == '\n'.code || codePoint == '\r'.code || codePoint == '\t'.code) return false
        return when (Character.getType(codePoint).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE -> true
            Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
            else -> false
        }
    }
}
