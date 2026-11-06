package dev.nekuz.intentlab

data class Validation(val errors: List<String>, val warnings: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object SpecValidator {
    private val whitespace = Regex("\\s")
    private val mimePattern = Regex("[^\\s/]+/[^\\s/]+")
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")
    private val classPattern = Regex("\\.?[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)*")

    fun validate(spec: IntentSpec): Validation {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        checkToken(spec.action, "The action", errors)
        spec.categories.forEach { checkToken(it, "A category", errors) }
        if (spec.mimeType.isNotEmpty() && !mimePattern.matches(spec.mimeType)) {
            errors.add("The MIME type must look like type/subtype.")
        }
        if (spec.packageName.isNotEmpty() && !packagePattern.matches(spec.packageName)) {
            errors.add("The package name is not valid.")
        }
        if (spec.className.isNotEmpty()) {
            if (spec.packageName.isEmpty()) {
                errors.add("A component class also needs a package name.")
            }
            if (!classPattern.matches(spec.className)) {
                errors.add("The component class name is not valid.")
            }
        }
        checkExtras(spec.extras, errors)
        if (spec.data.any { it.isWhitespace() } || TextCleaner.hasHidden(spec.data)) {
            warnings.add("The data URI contains spaces or invisible characters.")
        }
        if (spec.extras.any { TextCleaner.hasHidden(it.key) }) {
            warnings.add("An extra key contains invisible characters.")
        }
        val empty = spec.action.isEmpty() && spec.data.isEmpty() && spec.mimeType.isEmpty() &&
            spec.categories.isEmpty() && spec.packageName.isEmpty()
        if (empty) warnings.add("The intent is empty, so nothing can match it.")
        if (spec.mode == SendMode.SERVICE && spec.packageName.isEmpty()) {
            warnings.add("Android only accepts explicit service intents, so set a package or component.")
        }
        if (spec.mode == SendMode.BROADCAST && spec.packageName.isEmpty()) {
            warnings.add("Most apps do not receive implicit broadcasts in manifest receivers since Android 8.")
        }
        if (spec.chooser && !spec.mode.usesActivity) {
            warnings.add("The chooser only applies to activity sends and is ignored here.")
        }
        if (spec.mode == SendMode.ACTIVITY_FOR_RESULT && "FLAG_ACTIVITY_NEW_TASK" in spec.flags) {
            warnings.add("With FLAG_ACTIVITY_NEW_TASK, Android returns RESULT_CANCELED at once.")
        }
        val groups = spec.flags.mapNotNull { name -> IntentFlags.all.firstOrNull { it.name == name }?.group }
        if (spec.mode.usesActivity && FlagGroup.BROADCAST in groups) {
            warnings.add("Broadcast flags share values with activity flags and act as activity flags here.")
        }
        if (spec.mode == SendMode.BROADCAST && FlagGroup.ACTIVITY in groups) {
            warnings.add("Activity flags share values with broadcast flags and act as broadcast flags here.")
        }
        return Validation(errors, warnings)
    }

    private fun checkToken(value: String, name: String, errors: MutableList<String>) {
        if (whitespace.containsMatchIn(value) || TextCleaner.hasHidden(value)) {
            errors.add("$name must not contain spaces or invisible characters.")
        }
    }

    private fun checkExtras(extras: List<ExtraSpec>, errors: MutableList<String>) {
        val keys = HashSet<String>()
        for (extra in extras) {
            val parsed = ExtrasParser.parse(extra)
            if (parsed is ParsedExtra.Invalid) errors.add(parsed.message)
            if (extra.key.isNotBlank() && !keys.add(extra.key)) {
                errors.add("Extra \"${TextCleaner.line(extra.key, 60)}\" is listed twice.")
            }
        }
    }
}
