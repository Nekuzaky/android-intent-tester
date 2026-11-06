package dev.nekuz.intentlab

object PosixSplit {
    fun split(line: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var inToken = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '\'' -> {
                    val end = line.indexOf('\'', index + 1)
                    require(end >= 0) { "Unterminated quote" }
                    current.append(line, index + 1, end)
                    inToken = true
                    index = end
                }
                char == '\\' -> {
                    require(index + 1 < line.length) { "Trailing backslash" }
                    current.append(line[index + 1])
                    inToken = true
                    index++
                }
                char == ' ' -> {
                    if (inToken) tokens.add(current.toString())
                    current.setLength(0)
                    inToken = false
                }
                char in "\"$`;&|<>()*?[]#~\n\t" ->
                    throw IllegalArgumentException("Unquoted shell metacharacter: $char")
                else -> {
                    current.append(char)
                    inToken = true
                }
            }
            index++
        }
        if (inToken) tokens.add(current.toString())
        return tokens
    }
}
