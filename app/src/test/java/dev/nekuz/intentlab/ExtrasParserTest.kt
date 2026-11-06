package dev.nekuz.intentlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasParserTest {

    private fun ok(type: ExtraType, value: String): ParsedValue {
        val parsed = ExtrasParser.parse(ExtraSpec("k", type, value))
        assertTrue("$type $value: $parsed", parsed is ParsedExtra.Ok)
        return (parsed as ParsedExtra.Ok).value
    }

    private fun assertInvalid(type: ExtraType, value: String) {
        val parsed = ExtrasParser.parse(ExtraSpec("k", type, value))
        assertTrue("$type $value: $parsed", parsed is ParsedExtra.Invalid)
    }

    @Test
    fun strings() {
        assertEquals(ParsedValue.Text(""), ok(ExtraType.STRING, ""))
        assertEquals(ParsedValue.Text("  spaced "), ok(ExtraType.STRING, "  spaced "))
    }

    @Test
    fun ints() {
        assertEquals(ParsedValue.IntValue(7), ok(ExtraType.INT, "7"))
        assertEquals(ParsedValue.IntValue(-7), ok(ExtraType.INT, " -7 "))
        assertEquals(ParsedValue.IntValue(7), ok(ExtraType.INT, "+7"))
        assertEquals(ParsedValue.IntValue(Int.MAX_VALUE), ok(ExtraType.INT, "2147483647"))
        assertEquals(ParsedValue.IntValue(Int.MIN_VALUE), ok(ExtraType.INT, "-2147483648"))
    }

    @Test
    fun invalidInts() {
        listOf("", " ", "abc", "1.5", "2147483648", "-2147483649", "0x10", "1e3", "١٢", "1 2", "--1").forEach {
            assertInvalid(ExtraType.INT, it)
        }
    }

    @Test
    fun longs() {
        assertEquals(ParsedValue.LongValue(Long.MAX_VALUE), ok(ExtraType.LONG, "9223372036854775807"))
        assertInvalid(ExtraType.LONG, "9223372036854775808")
        assertInvalid(ExtraType.LONG, "12L")
        assertInvalid(ExtraType.LONG, "")
    }

    @Test
    fun booleans() {
        assertEquals(ParsedValue.BooleanValue(true), ok(ExtraType.BOOLEAN, "true"))
        assertEquals(ParsedValue.BooleanValue(false), ok(ExtraType.BOOLEAN, " FALSE "))
        listOf("", "1", "0", "yes", "t").forEach { assertInvalid(ExtraType.BOOLEAN, it) }
    }

    @Test
    fun floatsAndDoubles() {
        assertEquals(ParsedValue.FloatValue(1.5f), ok(ExtraType.FLOAT, "1.5"))
        assertEquals(ParsedValue.FloatValue(0.5f), ok(ExtraType.FLOAT, ".5"))
        assertEquals(ParsedValue.DoubleValue(-2.0e3), ok(ExtraType.DOUBLE, "-2e3"))
    }

    @Test
    fun invalidDecimals() {
        listOf("", "abc", "NaN", "Infinity", "-Infinity", "1f", "1d", "0x1p3", "1,5", "1e", "1e999").forEach {
            assertInvalid(ExtraType.DOUBLE, it)
        }
        listOf("NaN", "Infinity", "1f", "1e39").forEach { assertInvalid(ExtraType.FLOAT, it) }
    }

    @Test
    fun emptyKeyIsInvalid() {
        assertTrue(ExtrasParser.parse(ExtraSpec("", ExtraType.STRING, "v")) is ParsedExtra.Invalid)
        assertTrue(ExtrasParser.parse(ExtraSpec("  ", ExtraType.INT, "1")) is ParsedExtra.Invalid)
    }

    @Test
    fun invalidMessagesAreCleanedAndCapped() {
        val parsed = ExtrasParser.parse(ExtraSpec("k‮", ExtraType.INT, "‮" + "9".repeat(500) + "x"))
        val message = (parsed as ParsedExtra.Invalid).message
        assertTrue(message.length < 200)
        assertTrue(!message.contains('‮'))
    }

    @Test
    fun arraysSplitOnUnescapedCommas() {
        assertEquals(emptyList<String>(), ExtrasParser.splitArray(""))
        assertEquals(listOf("a"), ExtrasParser.splitArray("a"))
        assertEquals(listOf("a", "b", "c"), ExtrasParser.splitArray("a,b,c"))
        assertEquals(listOf("a,b", "c"), ExtrasParser.splitArray("a\\,b,c"))
        assertEquals(listOf("a\\", "b"), ExtrasParser.splitArray("a\\\\,b"))
        assertEquals(listOf("", "", ""), ExtrasParser.splitArray(",,"))
        assertEquals(listOf("trailing\\"), ExtrasParser.splitArray("trailing\\"))
    }

    @Test
    fun amValueIsNormalized() {
        assertEquals("42", ExtrasParser.amValue(ok(ExtraType.INT, "+42")))
        assertEquals("true", ExtrasParser.amValue(ok(ExtraType.BOOLEAN, "TRUE")))
        assertEquals("a\\,b,c", ExtrasParser.amValue(ok(ExtraType.STRING_ARRAY, "a\\,b,c")))
    }
}
