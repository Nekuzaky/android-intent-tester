package dev.nekuz.intentlab

enum class DomainVerdict(val label: String) {
    VERIFIED("Verified"),
    SELECTED("User selected"),
    NONE("Not verified"),
    NOT_DECLARED("Not declared"),
}

sealed class DeepLinkParse {
    data class Ok(
        val uri: String,
        val scheme: String,
        val host: String,
        val isWeb: Boolean,
        val warnings: List<String>,
    ) : DeepLinkParse()

    data class Invalid(val message: String) : DeepLinkParse()
}

object DeepLinks {
    private val schemePattern = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

    fun parse(raw: String): DeepLinkParse {
        val uri = raw.trim()
        if (uri.isEmpty()) return DeepLinkParse.Invalid("Paste a URL or a custom scheme link.")
        val match = schemePattern.find(uri)
            ?: return DeepLinkParse.Invalid("A link must start with a scheme, such as https:// or myapp://.")
        val scheme = match.groupValues[1]
        val isWeb = scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
        val authority = authorityOf(uri, match.range.last + 1)
        val host = hostOf(authority)
        if (isWeb && host.isEmpty()) return DeepLinkParse.Invalid("A web link needs a host name.")
        return DeepLinkParse.Ok(uri, scheme, host, isWeb, warningsFor(uri, scheme, host, authority))
    }

    fun verdictFor(host: String, states: Map<String, DomainVerdict>): DomainVerdict {
        val name = host.lowercase()
        val exact = states.entries.firstOrNull { it.key.lowercase() == name }
        if (exact != null) return exact.value
        val wildcard = states.entries
            .filter { it.key.startsWith("*.") && name.endsWith(it.key.substring(1).lowercase()) }
            .maxByOrNull { it.key.length }
        return wildcard?.value ?: DomainVerdict.NOT_DECLARED
    }

    private fun warningsFor(uri: String, scheme: String, host: String, authority: String): List<String> {
        val warnings = ArrayList<String>()
        if (TextCleaner.hasHidden(uri)) warnings.add("The link contains invisible characters.")
        if (uri.any { it.isWhitespace() }) warnings.add("The link contains spaces.")
        if (scheme != scheme.lowercase()) {
            warnings.add("Android matches schemes exactly, and filters normally use lower case.")
        }
        if (host != host.lowercase()) {
            warnings.add("Android matches hosts exactly, and filters normally use lower case.")
        }
        if (authority.contains('@')) {
            warnings.add("The part before @ is a user name, and the host is what follows it.")
        }
        if (authority.contains('\\')) {
            warnings.add("The link has a backslash in the host part, and apps can read that host differently.")
        }
        if (host.any { it.code > 0x7F }) {
            warnings.add("The host has non-ASCII characters, which can imitate another domain.")
        }
        return warnings
    }

    private fun authorityOf(uri: String, afterScheme: Int): String {
        if (!uri.startsWith("//", afterScheme)) return ""
        val start = afterScheme + 2
        val end = uri.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) uri.length else it }
        return uri.substring(start, end)
    }

    private fun hostOf(authority: String): String {
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            return if (end < 0) "" else hostPort.substring(0, end + 1)
        }
        return hostPort.substringBefore(':')
    }
}
