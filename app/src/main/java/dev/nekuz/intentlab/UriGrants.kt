package dev.nekuz.intentlab

data class GrantReview(val flags: List<String>, val contentUris: List<String>, val target: String) {
    val grantsAccess: Boolean get() = flags.isNotEmpty()
}

object UriGrants {
    private const val MAX_URIS = 8
    private const val MAX_URI_CHARS = 300

    fun review(spec: IntentSpec): GrantReview? {
        val flags = spec.flags.filter { IntentFlags.isGrant(it) }
        val uris = contentUris(spec)
        if (flags.isEmpty() && uris.isEmpty()) return null
        return GrantReview(flags, uris, target(spec))
    }

    fun describe(review: GrantReview): String = buildString {
        if (review.grantsAccess) {
            append("Android will try to give the target access to the URIs in this intent:\n")
            review.flags.forEach { append("• ").append(flagMeaning(it)).append('\n') }
        } else {
            append("No grant flag is selected, so no access is granted. Only the URI text is passed on.\n")
        }
        append("\nContent URIs:\n")
        if (review.contentUris.isEmpty()) {
            append("• None\n")
        } else {
            review.contentUris.forEach { append("• ").append(TextCleaner.line(it, MAX_URI_CHARS)).append('\n') }
        }
        append("\nTarget: ").append(review.target)
        if (review.grantsAccess) {
            append("\n\nIntentLab can only pass on access it holds itself. If it holds none, Android refuses the send.")
        }
    }

    private fun contentUris(spec: IntentSpec): List<String> {
        val found = LinkedHashSet<String>()
        if (isContentUri(spec.data)) found.add(spec.data.trim())
        for (extra in spec.extras) {
            when (extra.type) {
                ExtraType.STRING -> if (isContentUri(extra.value)) found.add(extra.value.trim())
                ExtraType.STRING_ARRAY -> ExtrasParser.splitArray(extra.value)
                    .filter { isContentUri(it) }
                    .forEach { found.add(it.trim()) }
                else -> Unit
            }
        }
        return found.take(MAX_URIS)
    }

    private fun isContentUri(value: String): Boolean =
        value.trimStart().startsWith("content:", ignoreCase = true)

    private fun target(spec: IntentSpec): String = when {
        spec.chooser && spec.mode.usesActivity -> "the app you pick in the chooser"
        spec.className.isNotEmpty() -> TextCleaner.line("${spec.packageName}/${spec.fullClassName}")
        spec.packageName.isNotEmpty() -> TextCleaner.line(spec.packageName)
        else -> "any app that matches this intent"
    }

    private fun flagMeaning(name: String): String = when (name) {
        "FLAG_GRANT_READ_URI_PERMISSION" -> "Read access (FLAG_GRANT_READ_URI_PERMISSION)"
        "FLAG_GRANT_WRITE_URI_PERMISSION" -> "Write access (FLAG_GRANT_WRITE_URI_PERMISSION)"
        "FLAG_GRANT_PERSISTABLE_URI_PERMISSION" -> "Access the target can keep across reboots (FLAG_GRANT_PERSISTABLE_URI_PERMISSION)"
        "FLAG_GRANT_PREFIX_URI_PERMISSION" -> "Access to every URI under this prefix (FLAG_GRANT_PREFIX_URI_PERMISSION)"
        else -> name
    }
}
