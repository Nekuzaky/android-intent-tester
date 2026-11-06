package dev.nekuz.intentlab

sealed class ParsedValue {
    data class Text(val value: String) : ParsedValue()
    data class IntValue(val value: Int) : ParsedValue()
    data class LongValue(val value: Long) : ParsedValue()
    data class BooleanValue(val value: Boolean) : ParsedValue()
    data class FloatValue(val value: Float) : ParsedValue()
    data class DoubleValue(val value: Double) : ParsedValue()
    data class TextArray(val value: List<String>) : ParsedValue()
}

sealed class ParsedExtra {
    data class Ok(val key: String, val value: ParsedValue) : ParsedExtra()
    data class Invalid(val message: String) : ParsedExtra()
}

object ExtrasParser {
    private val integerPattern = Regex("[+-]?[0-9]+")
    private val decimalPattern = Regex("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?")

    fun parse(extra: ExtraSpec): ParsedExtra {
        val name = TextCleaner.line(extra.key, 60)
        if (extra.key.isBlank()) return ParsedExtra.Invalid("An extra has no key.")
        val trimmed = extra.value.trim()
        val shown = TextCleaner.line(trimmed, 60)
        val value: ParsedValue = when (extra.type) {
            ExtraType.STRING -> ParsedValue.Text(extra.value)
            ExtraType.INT -> parseInteger(trimmed)?.toIntOrNull()?.let { ParsedValue.IntValue(it) }
                ?: return invalid(name, "\"$shown\" is not a whole number between -2147483648 and 2147483647")
            ExtraType.LONG -> parseInteger(trimmed)?.toLongOrNull()?.let { ParsedValue.LongValue(it) }
                ?: return invalid(name, "\"$shown\" is not a whole number that fits in a long")
            ExtraType.BOOLEAN -> when (trimmed.lowercase()) {
                "true" -> ParsedValue.BooleanValue(true)
                "false" -> ParsedValue.BooleanValue(false)
                else -> return invalid(name, "\"$shown\" is not true or false")
            }
            ExtraType.FLOAT -> parseDecimal(trimmed)?.toFloat()?.takeIf { it.isFinite() }
                ?.let { ParsedValue.FloatValue(it) }
                ?: return invalid(name, "\"$shown\" is not a finite float")
            ExtraType.DOUBLE -> parseDecimal(trimmed)?.takeIf { it.isFinite() }
                ?.let { ParsedValue.DoubleValue(it) }
                ?: return invalid(name, "\"$shown\" is not a finite double")
            ExtraType.STRING_ARRAY -> ParsedValue.TextArray(splitArray(extra.value))
        }
        return ParsedExtra.Ok(extra.key, value)
    }

    fun splitArray(raw: String): List<String> {
        if (raw.isEmpty()) return emptyList()
        val items = ArrayList<String>()
        val current = StringBuilder()
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            val next = raw.getOrNull(index + 1)
            when {
                char == '\\' && (next == ',' || next == '\\') -> {
                    current.append(next)
                    index++
                }
                char == ',' -> {
                    items.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(char)
            }
            index++
        }
        items.add(current.toString())
        return items
    }

    fun amValue(value: ParsedValue): String = when (value) {
        is ParsedValue.Text -> value.value
        is ParsedValue.IntValue -> value.value.toString()
        is ParsedValue.LongValue -> value.value.toString()
        is ParsedValue.BooleanValue -> value.value.toString()
        is ParsedValue.FloatValue -> value.value.toString()
        is ParsedValue.DoubleValue -> value.value.toString()
        is ParsedValue.TextArray -> value.value.joinToString(",") { it.replace(",", "\\,") }
    }

    private fun invalid(name: String, problem: String) = ParsedExtra.Invalid("Extra \"$name\": $problem.")

    private fun parseInteger(text: String): String? = text.takeIf { integerPattern.matches(it) }

    private fun parseDecimal(text: String): Double? =
        if (decimalPattern.matches(text)) text.toDoubleOrNull() else null
}
