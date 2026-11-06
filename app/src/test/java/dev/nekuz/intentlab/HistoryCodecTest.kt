package dev.nekuz.intentlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryCodecTest {

    private val awkward = "a|b\\c\nd\re\\p\\n'\"\u0001‮😀 "

    private val full = IntentSpec(
        action = awkward,
        data = "content://x/$awkward",
        mimeType = "text/plain",
        categories = listOf("c.one", "", awkward),
        packageName = "com.example",
        className = ".Main",
        flags = listOf("FLAG_ACTIVITY_NEW_TASK", "FLAG_GRANT_READ_URI_PERMISSION"),
        extras = ExtraType.entries.map { ExtraSpec("$it|$awkward", it, awkward) } + ExtraSpec("", ExtraType.INT, ""),
        mode = SendMode.ACTIVITY_FOR_RESULT,
        chooser = true,
    )

    @Test
    fun specRoundTrip() {
        assertEquals(full, HistoryCodec.decodeSpec(HistoryCodec.encodeSpec(full)))
        assertEquals(IntentSpec(), HistoryCodec.decodeSpec(HistoryCodec.encodeSpec(IntentSpec())))
    }

    @Test
    fun everyModeRoundTrips() {
        SendMode.entries.forEach { mode ->
            val spec = IntentSpec(mode = mode)
            assertEquals(spec, HistoryCodec.decodeSpec(HistoryCodec.encodeSpec(spec)))
        }
    }

    @Test
    fun singleEmptyListItemIsKept() {
        val spec = IntentSpec(categories = listOf(""))
        assertEquals(spec, HistoryCodec.decodeSpec(HistoryCodec.encodeSpec(spec)))
    }

    @Test
    fun entryRoundTrip() {
        val entry = HistoryEntry("id-1", awkward, 1234567890123L, "Sent", full)
        assertEquals(entry, HistoryCodec.decodeEntry(HistoryCodec.encodeEntry(entry)))
    }

    @Test
    fun encodedEntryIsOneLine() {
        val encoded = HistoryCodec.encodeEntry(HistoryEntry("i", awkward, 0, "", full))
        assertFalse(encoded.contains('\n'))
        assertFalse(encoded.contains('\r'))
    }

    @Test
    fun listRoundTrip() {
        val entries = listOf(
            HistoryEntry("1", "", 1, "Sent", full),
            HistoryEntry("2", "name", 2, "Failed", IntentSpec(action = "a")),
        )
        assertEquals(entries, HistoryCodec.decodeAll(HistoryCodec.encodeAll(entries)))
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.decodeAll(""))
    }

    @Test
    fun corruptLinesAreSkipped() {
        val good = HistoryEntry("1", "", 1, "Sent", IntentSpec(action = "a"))
        val text = listOf(
            "garbage",
            "6|1|x",
            "abc|def",
            "6|1|id|n|not-a-time|o|spec",
            "1|\\x",
            HistoryCodec.encodeEntry(good),
            HistoryCodec.encodeEntry(good).dropLast(3),
        ).joinToString("\n")
        assertEquals(listOf(good), HistoryCodec.decodeAll(text))
    }

    @Test
    fun corruptSpecsReturnNull() {
        assertNull(HistoryCodec.decodeSpec(""))
        assertNull(HistoryCodec.decodeSpec("nonsense"))
        val encoded = HistoryCodec.encodeSpec(IntentSpec(mode = SendMode.BROADCAST))
        assertNull(HistoryCodec.decodeSpec(encoded.replace("BROADCAST", "NOPE")))
        assertNull(HistoryCodec.decodeSpec(encoded.replace("false", "maybe")))
    }

    @Test
    fun unknownVersionIsRejected() {
        val encoded = HistoryCodec.encodeSpec(IntentSpec())
        assertNull(HistoryCodec.decodeSpec("11|9" + encoded.removePrefix("11|1")))
    }

    @Test
    fun unknownFlagsAreDropped() {
        val encoded = HistoryCodec.encodeSpec(IntentSpec(flags = listOf("FLAG_ACTIVITY_NEW_TASK", "FLAG_MADE_UP")))
        assertEquals(listOf("FLAG_ACTIVITY_NEW_TASK"), HistoryCodec.decodeSpec(encoded)?.flags)
    }
}
