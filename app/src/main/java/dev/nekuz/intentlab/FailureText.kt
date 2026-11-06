package dev.nekuz.intentlab

object FailureText {
    private const val MAX_DETAIL = 300
    private const val MAX_CHAIN = 6

    fun describe(error: Throwable): String {
        val names = chainNames(error)
        val summary = when {
            "ActivityNotFoundException" in names -> "No activity matches this intent."
            "SecurityException" in names ->
                "Android refused this. IntentLab does not hold the needed permission, or the target is not exported."
            "FileUriExposedException" in names ->
                "Android blocks file:// URIs sent to other apps. Use a content:// URI."
            "TransactionTooLargeException" in names -> "The intent is too large to send. Make the extras smaller."
            "BadParcelableException" in names -> "A value in this intent could not be read."
            "BackgroundServiceStartNotAllowedException" in names ||
                "IllegalStateException" in names -> "Android does not allow this start at the moment."
            "IllegalArgumentException" in names -> "Android rejected this intent as invalid."
            "OutOfMemoryError" in names -> "There is not enough memory for this."
            else -> "This could not be sent."
        }
        val detail = detailOf(error)
        val name = ResultFormatter.errorName(error)
        return if (detail.isEmpty()) "$summary\n$name" else "$summary\n$name: $detail"
    }

    private fun chainNames(error: Throwable): Set<String> {
        val names = HashSet<String>()
        var current: Throwable? = error
        var depth = 0
        try {
            while (current != null && depth < MAX_CHAIN) {
                var type: Class<*>? = current.javaClass
                while (type != null) {
                    names.add(type.simpleName)
                    type = type.superclass
                }
                current = current.cause
                depth++
            }
        } catch (nested: Throwable) {
            names.add("error")
        }
        return names
    }

    private fun detailOf(error: Throwable): String = try {
        TextCleaner.line(error.message, MAX_DETAIL)
    } catch (nested: Throwable) {
        ""
    }
}
