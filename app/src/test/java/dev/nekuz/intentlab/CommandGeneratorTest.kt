package dev.nekuz.intentlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandGeneratorTest {

    private fun deviceTokens(spec: IntentSpec) = PosixSplit.split(CommandGenerator.generate(spec).deviceCommand)

    @Test
    fun startWithActionAndData() {
        val command = CommandGenerator.generate(
            IntentSpec(action = "android.intent.action.VIEW", data = "https://example.com")
        )
        assertEquals("am start -a android.intent.action.VIEW -d https://example.com", command.deviceCommand)
        assertEquals("adb shell am start -a android.intent.action.VIEW -d https://example.com", command.adbCommand)
    }

    @Test
    fun modesPickTheRightSubcommand() {
        val base = IntentSpec(action = "a.b")
        assertEquals("am start -a a.b", CommandGenerator.generate(base).deviceCommand)
        assertEquals(
            "am start -a a.b",
            CommandGenerator.generate(base.copy(mode = SendMode.ACTIVITY_FOR_RESULT)).deviceCommand,
        )
        assertEquals("am broadcast -a a.b", CommandGenerator.generate(base.copy(mode = SendMode.BROADCAST)).deviceCommand)
        assertEquals("am startservice -a a.b", CommandGenerator.generate(base.copy(mode = SendMode.SERVICE)).deviceCommand)
    }

    @Test
    fun targetOptions() {
        assertEquals(
            listOf("am", "start", "-p", "com.example"),
            deviceTokens(IntentSpec(packageName = "com.example")),
        )
        assertEquals(
            listOf("am", "start", "-n", "com.example/com.example.Main"),
            deviceTokens(IntentSpec(packageName = "com.example", className = "com.example.Main")),
        )
        assertEquals(
            listOf("am", "start", "-n", "com.example/com.example.Main"),
            deviceTokens(IntentSpec(packageName = "com.example", className = ".Main")),
        )
    }

    @Test
    fun everyOptionAppears() {
        val tokens = deviceTokens(
            IntentSpec(
                action = "a.b",
                data = "x://y",
                mimeType = "text/plain",
                categories = listOf("c.one", "c.two"),
                flags = listOf("FLAG_ACTIVITY_NEW_TASK", "FLAG_ACTIVITY_CLEAR_TOP"),
            )
        )
        assertEquals(
            listOf(
                "am", "start", "-a", "a.b", "-d", "x://y", "-t", "text/plain",
                "-c", "c.one", "-c", "c.two", "-f", "0x14000000",
            ),
            tokens,
        )
    }

    @Test
    fun typedExtrasUseCorrectOptions() {
        val tokens = deviceTokens(
            IntentSpec(
                action = "a.b",
                extras = listOf(
                    ExtraSpec("s", ExtraType.STRING, "hello world"),
                    ExtraSpec("i", ExtraType.INT, " 42 "),
                    ExtraSpec("l", ExtraType.LONG, "9000000000"),
                    ExtraSpec("b", ExtraType.BOOLEAN, "TRUE"),
                    ExtraSpec("f", ExtraType.FLOAT, "1.5"),
                    ExtraSpec("d", ExtraType.DOUBLE, "2.5"),
                    ExtraSpec("a", ExtraType.STRING_ARRAY, "x,y"),
                ),
            )
        )
        assertEquals(
            listOf(
                "am", "start", "-a", "a.b",
                "--es", "s", "hello world",
                "--ei", "i", "42",
                "--el", "l", "9000000000",
                "--ez", "b", "true",
                "--ef", "f", "1.5",
                "--ed", "d", "2.5",
                "--esa", "a", "x,y",
            ),
            tokens,
        )
    }

    @Test
    fun maliciousValuesStayLiteralAtBothShellLevels() {
        val attack = "x'; reboot; echo '\$(id)`id`"
        val spec = IntentSpec(
            action = "a.b",
            data = "https://e.com/?a=1&b=$attack",
            extras = listOf(ExtraSpec("k$attack", ExtraType.STRING, attack)),
        )
        val command = CommandGenerator.generate(spec)
        val local = PosixSplit.split(command.adbCommand)
        assertEquals(listOf("adb", "shell", command.deviceCommand), local)
        val device = PosixSplit.split(local[2])
        assertEquals(
            listOf(
                "am", "start", "-a", "a.b", "-d", "https://e.com/?a=1&b=$attack",
                "--es", "k$attack", attack,
            ),
            device,
        )
    }

    @Test
    fun ampersandInUrlIsQuotedForBothShells() {
        val command = CommandGenerator.generate(IntentSpec(data = "https://e.com/?a=1&b=2"))
        assertEquals("am start -d 'https://e.com/?a=1&b=2'", command.deviceCommand)
        assertEquals("adb shell 'am start -d '\\''https://e.com/?a=1&b=2'\\'''", command.adbCommand)
    }

    @Test
    fun newlineCannotBreakOutOfTheCommand() {
        val command = CommandGenerator.generate(IntentSpec(action = "a.b", data = "x\nreboot"))
        val device = PosixSplit.split(PosixSplit.split(command.adbCommand)[2])
        assertEquals(listOf("am", "start", "-a", "a.b", "-d", "x\nreboot"), device)
    }

    @Test
    fun invalidExtrasAreLeftOut() {
        val tokens = deviceTokens(IntentSpec(action = "a.b", extras = listOf(ExtraSpec("i", ExtraType.INT, "x"))))
        assertEquals(listOf("am", "start", "-a", "a.b"), tokens)
    }

    @Test
    fun arrayWithCommaEscapesAndWarns() {
        val command = CommandGenerator.generate(
            IntentSpec(extras = listOf(ExtraSpec("a", ExtraType.STRING_ARRAY, "x\\,y,z")))
        )
        assertEquals(listOf("am", "start", "--esa", "a", "x\\,y,z"), PosixSplit.split(command.deviceCommand))
        assertTrue(command.warnings.any { it.contains("escaped comma") })
    }

    @Test
    fun warnings() {
        assertTrue(CommandGenerator.generate(IntentSpec()).warnings.any { it.contains("no intent options") })
        assertTrue(
            CommandGenerator.generate(IntentSpec(action = "a", mode = SendMode.ACTIVITY_FOR_RESULT))
                .warnings.any { it.contains("cannot request a result") }
        )
        assertTrue(
            CommandGenerator.generate(IntentSpec(action = "a", chooser = true)).warnings.any { it.contains("chooser") }
        )
        assertTrue(CommandGenerator.generate(IntentSpec(data = "a\u0000b")).warnings.any { it.contains("NUL") })
        assertTrue(CommandGenerator.generate(IntentSpec(data = "a\u202Eb")).warnings.any { it.contains("invisible") })
        assertFalse(CommandGenerator.generate(IntentSpec(action = "a")).warnings.any())
    }
}
