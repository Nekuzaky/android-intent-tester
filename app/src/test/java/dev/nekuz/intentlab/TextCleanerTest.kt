package dev.nekuz.intentlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCleanerTest {

    private val bidi = listOf(
        '‎', '‏', '؜', '‪', '‫', '‬', '‭', '‮',
        '⁦', '⁧', '⁨', '⁩',
    )

    @Test
    fun labelRemovesEveryBidiControl() {
        bidi.forEach { assertEquals("ab", TextCleaner.label("a${it}b", "x")) }
    }

    @Test
    fun labelRemovesControlAndInvisibleCharacters() {
        assertEquals("ab", TextCleaner.label("a\u0000\u0007\u007F​‍﻿­b", "x"))
        assertEquals("ab", TextCleaner.label("aㅤᅟb", "x"))
    }

    @Test
    fun labelTurnsLineBreaksIntoOneSpace() {
        assertEquals("a b", TextCleaner.label("a\n\r\t     b", "x"))
    }

    @Test
    fun labelFallsBackWhenNothingIsLeft() {
        assertEquals("x", TextCleaner.label(null, "x"))
        assertEquals("x", TextCleaner.label("", "x"))
        assertEquals("x", TextCleaner.label(" ‮\u0000\n ", "x"))
    }

    @Test
    fun labelIsCapped() {
        val label = TextCleaner.label("a".repeat(100_000), "x")
        assertEquals(TextCleaner.MAX_LABEL, label.length)
        assertTrue(label.endsWith("…"))
    }

    @Test
    fun capDoesNotSplitSurrogatePairs() {
        for (length in 1..12) {
            val cleaned = TextCleaner.clean("😀".repeat(20), length, keepNewlines = false, marker = "")
            assertFalse(cleaned, cleaned.withIndex().any { (i, c) -> c.isHighSurrogate() && cleaned.getOrNull(i + 1)?.isLowSurrogate() != true })
            assertTrue(cleaned.length <= length)
        }
    }

    @Test
    fun emojiAndAccentsSurvive() {
        assertEquals("Café 😀 日本語", TextCleaner.label("Café 😀 日本語", "x"))
    }

    @Test
    fun loneSurrogatesAreRemoved() {
        assertEquals("ab", TextCleaner.label("a\uD83Db", "x"))
    }

    @Test
    fun combiningFloodIsLimited() {
        val cleaned = TextCleaner.label("a" + "́".repeat(500) + "b", "x")
        assertEquals("á́́b", cleaned)
    }

    @Test
    fun blockMarksHiddenCharactersAndKeepsLines() {
        assertEquals("a${TextCleaner.MARKER}b\nc  d", TextCleaner.block("a‮b\nc  d", 100))
    }

    @Test
    fun blockIsCapped() {
        assertTrue(TextCleaner.block("line\n".repeat(10_000), 100).length <= 100)
    }

    @Test
    fun lineMarksHiddenCharacters() {
        assertEquals("a${TextCleaner.MARKER}b c", TextCleaner.line("a\u0007b\nc"))
    }

    @Test
    fun nullAndZeroCap() {
        assertEquals("", TextCleaner.line(null))
        assertEquals("", TextCleaner.block("abc", 0))
    }

    @Test
    fun hasHidden() {
        assertTrue(TextCleaner.hasHidden("a‮b"))
        assertTrue(TextCleaner.hasHidden("a\u0000b"))
        assertTrue(TextCleaner.hasHidden("a b"))
        assertFalse(TextCleaner.hasHidden("plain text\n\t"))
        assertFalse(TextCleaner.hasHidden(null))
    }
}
