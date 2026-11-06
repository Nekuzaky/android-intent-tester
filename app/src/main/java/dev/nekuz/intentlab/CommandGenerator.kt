package dev.nekuz.intentlab

data class GeneratedCommand(val deviceCommand: String, val adbCommand: String, val warnings: List<String>)

object CommandGenerator {

    fun generate(spec: IntentSpec): GeneratedCommand {
        val warnings = ArrayList<String>()
        val tokens = ArrayList<String>()
        tokens.add("am")
        tokens.add(
            when (spec.mode) {
                SendMode.ACTIVITY, SendMode.ACTIVITY_FOR_RESULT -> "start"
                SendMode.BROADCAST -> "broadcast"
                SendMode.SERVICE -> "startservice"
            }
        )
        if (spec.packageName.isNotEmpty() && spec.className.isNotEmpty()) {
            tokens.add("-n")
            tokens.add("${spec.packageName}/${spec.fullClassName}")
        } else if (spec.packageName.isNotEmpty()) {
            tokens.add("-p")
            tokens.add(spec.packageName)
        }
        option(tokens, "-a", spec.action)
        option(tokens, "-d", spec.data)
        option(tokens, "-t", spec.mimeType)
        spec.categories.forEach { option(tokens, "-c", it) }
        val flags = spec.flagsValue
        if (flags != 0) {
            tokens.add("-f")
            tokens.add(IntentFlags.hex(flags))
        }
        spec.extras.forEach { addExtra(tokens, it, warnings) }
        collectWarnings(spec, tokens, warnings)
        val device = tokens.joinToString(" ") { ShellQuote.quoteIfNeeded(it) }
        val adb = if (tokens.all { ShellQuote.isSafe(it) }) {
            "adb shell $device"
        } else {
            "adb shell ${ShellQuote.quote(device)}"
        }
        return GeneratedCommand(device, adb, warnings)
    }

    private fun option(tokens: MutableList<String>, name: String, value: String) {
        if (value.isEmpty()) return
        tokens.add(name)
        tokens.add(value)
    }

    private fun addExtra(tokens: MutableList<String>, extra: ExtraSpec, warnings: MutableList<String>) {
        val parsed = ExtrasParser.parse(extra) as? ParsedExtra.Ok ?: return
        tokens.add(extra.type.amOption)
        tokens.add(parsed.key)
        tokens.add(ExtrasParser.amValue(parsed.value))
        val value = parsed.value
        if (value is ParsedValue.TextArray) {
            if (value.value.isEmpty()) {
                warnings.add("am turns an empty string array into an array with one empty string.")
            }
            if (value.value.any { it.contains(',') || it.endsWith("\\") }) {
                warnings.add("am keeps the backslash of an escaped comma, so array items with commas differ.")
            }
        }
        if (extra.type == ExtraType.DOUBLE) {
            warnings.add("Older versions of am do not accept --ed.")
        }
    }

    private fun collectWarnings(spec: IntentSpec, tokens: List<String>, warnings: MutableList<String>) {
        if (tokens.size == 2) warnings.add("am refuses a command with no intent options.")
        if (spec.mode == SendMode.ACTIVITY_FOR_RESULT) {
            warnings.add("am cannot request a result, so this is a plain am start.")
        }
        if (spec.chooser && spec.mode.usesActivity) {
            warnings.add("The chooser is not part of this command.")
        }
        if (tokens.any { it.contains('\u0000') }) {
            warnings.add("NUL characters cannot be passed in a shell command and were removed.")
        } else if (tokens.any { TextCleaner.hasHidden(it) }) {
            warnings.add("The command contains invisible characters. They appear as ${TextCleaner.MARKER} on screen and are copied as they are.")
        }
    }
}
