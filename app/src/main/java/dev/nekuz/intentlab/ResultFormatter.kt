package dev.nekuz.intentlab

data class Unreadable(val reason: String)

object ResultFormatter {
    const val MAX_KEYS = 100
    const val MAX_VALUE_CHARS = 2000
    const val MAX_TOTAL_CHARS = 32000
    private const val MAX_DEPTH = 3
    private const val MAX_ITEMS = 50
    private const val MAX_BYTES_SHOWN = 32

    fun resultCodeName(code: Int): String = when (code) {
        -1 -> "RESULT_OK (-1)"
        0 -> "RESULT_CANCELED (0)"
        1 -> "RESULT_FIRST_USER (1)"
        else -> "Custom ($code)"
    }

    fun formatEntries(
        entries: Map<String, Any?>,
        totalKeys: Int = entries.size,
        expand: (Any) -> Map<String, Any?>? = { null },
    ): String {
        val text = StringBuilder()
        for ((key, value) in entries.entries.sortedBy { it.key }.take(MAX_KEYS)) {
            if (text.length >= MAX_TOTAL_CHARS) break
            text.append(TextCleaner.line(key, 120).ifEmpty { "(empty key)" })
            text.append(" (").append(typeName(value)).append("): ")
            text.append(formatValue(value, expand))
            text.append('\n')
        }
        if (text.length > MAX_TOTAL_CHARS) {
            text.setLength(MAX_TOTAL_CHARS)
        }
        val shown = minOf(totalKeys, MAX_KEYS)
        if (text.length >= MAX_TOTAL_CHARS || totalKeys > shown) {
            text.append("\nOutput cut. The result has $totalKeys extras.")
        }
        return text.toString().trimEnd()
    }

    fun formatValue(value: Any?, expand: (Any) -> Map<String, Any?>? = { null }, depth: Int = 0): String =
        try {
            render(value, expand, depth)
        } catch (error: Throwable) {
            "unreadable (${errorName(error)})"
        }

    fun typeName(value: Any?): String = try {
        when (value) {
            null -> "null"
            is Unreadable -> "unreadable"
            else -> TextCleaner.line(value.javaClass.simpleName.ifEmpty { value.javaClass.name }, 60)
        }
    } catch (error: Throwable) {
        "unknown"
    }

    fun errorName(error: Throwable): String = try {
        TextCleaner.line(error.javaClass.simpleName, 60).ifEmpty { "error" }
    } catch (nested: Throwable) {
        "error"
    }

    private fun render(value: Any?, expand: (Any) -> Map<String, Any?>?, depth: Int): String = when (value) {
        null -> "null"
        is Unreadable -> "unreadable (${TextCleaner.line(value.reason, 60)})"
        is CharSequence -> TextCleaner.block(value, MAX_VALUE_CHARS)
        is Number, is Boolean, is Char -> TextCleaner.line(value.toString(), 80)
        is ByteArray -> renderBytes(value)
        is BooleanArray -> renderItems(value.asList(), value.size, expand, depth)
        is CharArray -> renderItems(value.asList(), value.size, expand, depth)
        is ShortArray -> renderItems(value.asList(), value.size, expand, depth)
        is IntArray -> renderItems(value.asList(), value.size, expand, depth)
        is LongArray -> renderItems(value.asList(), value.size, expand, depth)
        is FloatArray -> renderItems(value.asList(), value.size, expand, depth)
        is DoubleArray -> renderItems(value.asList(), value.size, expand, depth)
        is Array<*> -> renderItems(value.asList(), value.size, expand, depth)
        is Collection<*> -> renderItems(value, value.size, expand, depth)
        is Map<*, *> -> renderMap(value.entries.associate { it.key.toString() to it.value }, expand, depth)
        else -> {
            val expanded = expand(value)
            if (expanded != null) {
                renderMap(expanded, expand, depth)
            } else {
                TextCleaner.block(value.toString(), MAX_VALUE_CHARS)
            }
        }
    }

    private fun renderBytes(bytes: ByteArray): String {
        val head = bytes.take(MAX_BYTES_SHOWN).joinToString("") { "%02x".format(it) }
        val more = if (bytes.size > MAX_BYTES_SHOWN) "…" else ""
        return "${bytes.size} bytes: $head$more"
    }

    private fun renderItems(
        items: Collection<*>,
        size: Int,
        expand: (Any) -> Map<String, Any?>?,
        depth: Int,
    ): String {
        if (depth >= MAX_DEPTH) return "[$size items]"
        val shown = items.take(MAX_ITEMS).joinToString(", ") { formatValue(it, expand, depth + 1) }
        val more = if (size > MAX_ITEMS) ", … ${size - MAX_ITEMS} more" else ""
        return capped("[$shown$more]")
    }

    private fun renderMap(entries: Map<String, Any?>, expand: (Any) -> Map<String, Any?>?, depth: Int): String {
        if (depth >= MAX_DEPTH) return "{${entries.size} entries}"
        val shown = entries.entries.take(MAX_ITEMS).joinToString(", ") {
            "${TextCleaner.line(it.key, 120)}=${formatValue(it.value, expand, depth + 1)}"
        }
        val more = if (entries.size > MAX_ITEMS) ", … ${entries.size - MAX_ITEMS} more" else ""
        return capped("{$shown$more}")
    }

    private fun capped(text: String): String =
        if (text.length <= MAX_VALUE_CHARS) text else text.take(MAX_VALUE_CHARS - 1) + "…"
}
