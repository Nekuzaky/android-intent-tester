package dev.nekuz.intentlab

data class HistoryEntry(
    val id: String,
    val name: String,
    val timeMillis: Long,
    val outcome: String,
    val spec: IntentSpec,
)

object HistoryCodec {
    private const val VERSION = "1"
    private const val SEPARATOR = '|'

    fun encodeSpec(spec: IntentSpec): String = pack(
        listOf(
            VERSION,
            spec.action,
            spec.data,
            spec.mimeType,
            pack(spec.categories),
            spec.packageName,
            spec.className,
            pack(spec.flags),
            pack(spec.extras.map { pack(listOf(it.key, it.type.name, it.value)) }),
            spec.mode.name,
            spec.chooser.toString(),
        )
    )

    fun decodeSpec(text: String): IntentSpec? {
        val fields = unpack(text) ?: return null
        if (fields.size != 11 || fields[0] != VERSION) return null
        val categories = unpack(fields[4]) ?: return null
        val flags = unpack(fields[7]) ?: return null
        val packedExtras = unpack(fields[8]) ?: return null
        val extras = packedExtras.map { decodeExtra(it) ?: return null }
        val mode = SendMode.entries.firstOrNull { it.name == fields[9] } ?: return null
        val chooser = fields[10].toBooleanStrictOrNull() ?: return null
        return IntentSpec(
            action = fields[1],
            data = fields[2],
            mimeType = fields[3],
            categories = categories,
            packageName = fields[5],
            className = fields[6],
            flags = flags.filter { IntentFlags.isKnown(it) },
            extras = extras,
            mode = mode,
            chooser = chooser,
        )
    }

    fun encodeEntry(entry: HistoryEntry): String = pack(
        listOf(VERSION, entry.id, entry.name, entry.timeMillis.toString(), entry.outcome, encodeSpec(entry.spec))
    )

    fun decodeEntry(line: String): HistoryEntry? {
        val fields = unpack(line) ?: return null
        if (fields.size != 6 || fields[0] != VERSION || fields[1].isEmpty()) return null
        val time = fields[3].toLongOrNull() ?: return null
        val spec = decodeSpec(fields[5]) ?: return null
        return HistoryEntry(fields[1], fields[2], time, fields[4], spec)
    }

    fun encodeAll(entries: List<HistoryEntry>): String =
        entries.joinToString("\n") { encodeEntry(it) }

    fun decodeAll(text: String): List<HistoryEntry> =
        text.split('\n').filter { it.isNotEmpty() }.mapNotNull { decodeEntry(it) }

    private fun decodeExtra(text: String): ExtraSpec? {
        val fields = unpack(text) ?: return null
        if (fields.size != 3) return null
        val type = ExtraType.entries.firstOrNull { it.name == fields[1] } ?: return null
        return ExtraSpec(fields[0], type, fields[2])
    }

    private fun pack(fields: List<String>): String = buildString {
        append(fields.size)
        fields.forEach { append(SEPARATOR).append(escape(it)) }
    }

    private fun unpack(text: String): List<String>? {
        val parts = text.split(SEPARATOR)
        val count = parts[0].toIntOrNull() ?: return null
        if (count != parts.size - 1) return null
        return parts.drop(1).map { unescape(it) ?: return null }
    }

    private fun escape(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                SEPARATOR -> append("\\p")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    private fun unescape(value: String): String? {
        if ('\\' !in value) return value
        val text = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\') {
                text.append(char)
                index++
                continue
            }
            when (value.getOrNull(index + 1)) {
                '\\' -> text.append('\\')
                'p' -> text.append(SEPARATOR)
                'n' -> text.append('\n')
                'r' -> text.append('\r')
                else -> return null
            }
            index += 2
        }
        return text.toString()
    }
}
