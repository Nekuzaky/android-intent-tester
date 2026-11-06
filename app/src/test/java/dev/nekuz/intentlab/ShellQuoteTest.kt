package dev.nekuz.intentlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ShellQuoteTest {

    private val hostile = listOf(
        "'; rm -rf / #",
        "$(reboot)",
        "`reboot`",
        "a'b'c",
        "'",
        "''",
        "\\'",
        "a\nreboot\nb",
        "a && reboot",
        "a | sh",
        "a > /sdcard/x",
        "\${IFS}",
        "*",
        "~",
        "#comment",
        "\"double\"",
        "-n evil/.Activity",
        "%s%n",
        "\u202Egnp.exe",
        "   ",
    )

    @Test
    fun quotedHostileInputIsOneLiteralWord() {
        hostile.forEach { value ->
            assertEquals(value, listOf(value), PosixSplit.split(ShellQuote.quote(value)))
        }
    }

    @Test
    fun singleQuoteUsesStandardEscape() {
        assertEquals("'a'\\''b'", ShellQuote.quote("a'b"))
    }

    @Test
    fun emptyValueStaysAnArgument() {
        assertEquals("''", ShellQuote.quote(""))
        assertEquals("''", ShellQuote.quoteIfNeeded(""))
        assertEquals(listOf(""), PosixSplit.split("''"))
    }

    @Test
    fun safeValuesAreLeftBare() {
        assertEquals("android.intent.action.VIEW", ShellQuote.quoteIfNeeded("android.intent.action.VIEW"))
        assertEquals("https://example.com/a", ShellQuote.quoteIfNeeded("https://example.com/a"))
        assertTrue(ShellQuote.isSafe("0x10000000"))
    }

    @Test
    fun metacharactersAreNeverSafe() {
        "'\"$`;&|<>()*?[]#~ \n\t\\!{}^".forEach { char ->
            assertFalse("$char", ShellQuote.isSafe("a${char}b"))
        }
        assertFalse(ShellQuote.isSafe(""))
    }

    @Test
    fun nulIsRemovedBecauseArgumentsCannotHoldIt() {
        assertEquals("'ab'", ShellQuote.quote("a\u0000b"))
    }

    @Test
    fun randomStringsSurviveQuoting() {
        val random = Random(42)
        val alphabet = "ab '\"\\$`;&|<>()*?#~\n\t-".toCharArray()
        repeat(2000) {
            val value = String(CharArray(random.nextInt(24)) { alphabet[random.nextInt(alphabet.size)] })
            assertEquals(listOf(value), PosixSplit.split(ShellQuote.quote(value)))
        }
    }
}
