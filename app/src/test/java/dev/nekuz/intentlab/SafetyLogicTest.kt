package dev.nekuz.intentlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityNotFoundException(message: String?) : RuntimeException(message)
class TransactionTooLargeException : Exception()

class SafetyLogicTest {

    @Test
    fun noReviewWithoutGrantOrContentUri() {
        assertNull(UriGrants.review(IntentSpec(action = "a", data = "https://example.com")))
        assertNull(UriGrants.review(IntentSpec(flags = listOf("FLAG_ACTIVITY_NEW_TASK"))))
    }

    @Test
    fun everyGrantFlagTriggersReview() {
        IntentFlags.all.filter { it.group == FlagGroup.URI_GRANT }.forEach { flag ->
            val review = UriGrants.review(IntentSpec(flags = listOf(flag.name)))!!
            assertEquals(listOf(flag.name), review.flags)
            assertTrue(UriGrants.describe(review).contains(flag.name))
        }
        assertEquals(4, IntentFlags.all.count { it.group == FlagGroup.URI_GRANT })
    }

    @Test
    fun contentUriTriggersReviewEverywhere() {
        assertEquals(
            listOf("content://a/1"),
            UriGrants.review(IntentSpec(data = "content://a/1"))?.contentUris,
        )
        assertEquals(
            listOf("content://a/2"),
            UriGrants.review(IntentSpec(extras = listOf(ExtraSpec("k", ExtraType.STRING, "CONTENT://a/2".lowercase()))))?.contentUris,
        )
        assertEquals(
            listOf("content://a/3"),
            UriGrants.review(IntentSpec(extras = listOf(ExtraSpec("k", ExtraType.STRING_ARRAY, "x,content://a/3"))))?.contentUris,
        )
    }

    @Test
    fun reviewSaysWhatIsAndIsNotGranted() {
        val none = UriGrants.describe(UriGrants.review(IntentSpec(data = "content://a/1"))!!)
        assertTrue(none.contains("no access is granted"))
        val read = UriGrants.describe(
            UriGrants.review(
                IntentSpec(
                    data = "content://a/1",
                    packageName = "com.target",
                    flags = listOf("FLAG_GRANT_READ_URI_PERMISSION", "FLAG_GRANT_PREFIX_URI_PERMISSION"),
                )
            )!!
        )
        assertTrue(read.contains("Read access"))
        assertTrue(read.contains("under this prefix"))
        assertTrue(read.contains("content://a/1"))
        assertTrue(read.contains("com.target"))
    }

    @Test
    fun reviewTextCleansHiddenCharacters() {
        val text = UriGrants.describe(UriGrants.review(IntentSpec(data = "content://a‮/1"))!!)
        assertFalse(text.contains('‮'))
    }

    @Test
    fun reviewNamesChooserTarget() {
        val review = UriGrants.review(IntentSpec(data = "content://a", chooser = true))!!
        assertTrue(review.target.contains("chooser"))
    }

    @Test
    fun flagsCombineAndDebugLoggingIsNotOffered() {
        assertEquals(0x14000000, IntentFlags.combine(listOf("FLAG_ACTIVITY_NEW_TASK", "FLAG_ACTIVITY_CLEAR_TOP")))
        assertEquals(0, IntentFlags.combine(listOf("FLAG_MADE_UP")))
        assertEquals("0x10000000", IntentFlags.hex(0x10000000))
        assertFalse(IntentFlags.isKnown("FLAG_DEBUG_LOG_RESOLUTION"))
    }

    @Test
    fun validatorFlagsBadFields() {
        fun errors(spec: IntentSpec) = SpecValidator.validate(spec).errors
        assertTrue(errors(IntentSpec(action = "a b")).isNotEmpty())
        assertTrue(errors(IntentSpec(action = "a‮b")).isNotEmpty())
        assertTrue(errors(IntentSpec(mimeType = "text")).isNotEmpty())
        assertTrue(errors(IntentSpec(packageName = "com/evil")).isNotEmpty())
        assertTrue(errors(IntentSpec(className = "Main")).isNotEmpty())
        assertTrue(errors(IntentSpec(packageName = "a.b", className = "a b")).isNotEmpty())
        assertTrue(errors(IntentSpec(categories = listOf("a b"))).isNotEmpty())
        assertTrue(errors(IntentSpec(extras = listOf(ExtraSpec("k", ExtraType.INT, "x")))).isNotEmpty())
        val twice = listOf(ExtraSpec("k", ExtraType.INT, "1"), ExtraSpec("k", ExtraType.INT, "2"))
        assertTrue(errors(IntentSpec(extras = twice)).any { it.contains("twice") })
    }

    @Test
    fun validatorAcceptsNormalIntents() {
        val validation = SpecValidator.validate(
            IntentSpec(
                action = "android.intent.action.VIEW",
                data = "https://example.com",
                mimeType = "*/*",
                packageName = "com.example.app",
                className = ".ui.Main\$Inner",
                extras = listOf(ExtraSpec("k", ExtraType.BOOLEAN, "true")),
            )
        )
        assertTrue(validation.errors.toString(), validation.isValid)
    }

    @Test
    fun validatorWarnings() {
        fun warnings(spec: IntentSpec) = SpecValidator.validate(spec).warnings
        assertTrue(warnings(IntentSpec()).any { it.contains("empty") })
        assertTrue(warnings(IntentSpec(action = "a", mode = SendMode.SERVICE)).any { it.contains("explicit") })
        assertTrue(warnings(IntentSpec(data = "a b")).isNotEmpty())
        assertTrue(
            warnings(IntentSpec(action = "a", mode = SendMode.BROADCAST, chooser = true)).any { it.contains("chooser") }
        )
        assertTrue(
            warnings(
                IntentSpec(action = "a", mode = SendMode.ACTIVITY_FOR_RESULT, flags = listOf("FLAG_ACTIVITY_NEW_TASK"))
            ).any { it.contains("RESULT_CANCELED") }
        )
    }

    @Test
    fun failuresAreReadable() {
        assertTrue(FailureText.describe(ActivityNotFoundException("x")).startsWith("No activity matches"))
        assertTrue(FailureText.describe(SecurityException("denied")).startsWith("Android refused"))
        assertTrue(FailureText.describe(IllegalArgumentException("bad")).startsWith("Android rejected"))
        assertTrue(FailureText.describe(IllegalStateException("late")).startsWith("Android does not allow"))
        assertTrue(FailureText.describe(RuntimeException("x", TransactionTooLargeException())).contains("too large"))
        assertTrue(FailureText.describe(OutOfMemoryError()).contains("memory"))
        assertTrue(FailureText.describe(Exception("other")).startsWith("This could not be sent"))
    }

    @Test
    fun failureDetailIsCleanedAndCapped() {
        val text = FailureText.describe(SecurityException("a‮" + "b".repeat(5000)))
        assertFalse(text.contains('‮'))
        assertTrue(text.length < 500)
    }

    @Test
    fun failureWithHostileMessageGetterIsHandled() {
        val hostile = object : RuntimeException() {
            override val message: String get() = throw IllegalStateException("boom")
        }
        assertTrue(FailureText.describe(hostile).isNotEmpty())
    }

    @Test
    fun resultCodes() {
        assertEquals("RESULT_OK (-1)", ResultFormatter.resultCodeName(-1))
        assertEquals("RESULT_CANCELED (0)", ResultFormatter.resultCodeName(0))
        assertEquals("Custom (42)", ResultFormatter.resultCodeName(42))
    }

    @Test
    fun resultValuesAreFormattedAsText() {
        assertEquals("null", ResultFormatter.formatValue(null))
        assertEquals("7", ResultFormatter.formatValue(7))
        assertEquals("true", ResultFormatter.formatValue(true))
        assertEquals("[1, 2]", ResultFormatter.formatValue(intArrayOf(1, 2)))
        assertEquals("[a, b]", ResultFormatter.formatValue(arrayOf("a", "b")))
        assertEquals("[a, null]", ResultFormatter.formatValue(listOf("a", null)))
        assertEquals("2 bytes: 00ff", ResultFormatter.formatValue(byteArrayOf(0, -1)))
        assertEquals("{a=1}", ResultFormatter.formatValue(mapOf("a" to 1)))
        assertEquals("unreadable (BadParcelableException)", ResultFormatter.formatValue(Unreadable("BadParcelableException")))
    }

    @Test
    fun hostileToStringNeverThrows() {
        val hostile = object {
            override fun toString(): String = throw OutOfMemoryError("boom")
        }
        assertTrue(ResultFormatter.formatValue(hostile).startsWith("unreadable"))
        assertTrue(ResultFormatter.formatValue(listOf(hostile, "ok")).contains("ok"))
    }

    @Test
    fun resultTextIsCleanedAndCapped() {
        val value = ResultFormatter.formatValue("a‮b" + "c".repeat(100_000))
        assertFalse(value.contains('‮'))
        assertTrue(value.length <= ResultFormatter.MAX_VALUE_CHARS)
        val array = ResultFormatter.formatValue(Array(10_000) { "item$it" })
        assertTrue(array.contains("more"))
        assertTrue(array.length <= ResultFormatter.MAX_VALUE_CHARS)
    }

    @Test
    fun deepNestingStops() {
        var nested: Any = listOf(1)
        repeat(1000) { nested = listOf(nested) }
        assertTrue(ResultFormatter.formatValue(nested).contains("1 items"))
    }

    @Test
    fun selfReferenceStops() {
        val list = ArrayList<Any>()
        list.add(list)
        assertTrue(ResultFormatter.formatValue(list).contains("items"))
    }

    @Test
    fun entriesAreSortedCleanedAndCapped() {
        val text = ResultFormatter.formatEntries(mapOf("b‮" to 1, "a" to "x"))
        assertEquals("a (String): x\nb${TextCleaner.MARKER} (Integer): 1", text)
        val many = (0 until 5000).associate { "key$it" to "v".repeat(1000) }
        val capped = ResultFormatter.formatEntries(many)
        assertTrue(capped.length < ResultFormatter.MAX_TOTAL_CHARS + 100)
        assertTrue(capped.contains("Output cut"))
    }

    @Test
    fun nestedBundlesExpandThroughCallback() {
        class FakeBundle
        val text = ResultFormatter.formatValue(FakeBundle(), { mapOf("inner" to 1) })
        assertEquals("{inner=1}", text)
    }

    @Test
    fun deepLinkParsing() {
        val web = DeepLinks.parse(" https://Example.com:443/a?b#c ") as DeepLinkParse.Ok
        assertEquals("https", web.scheme)
        assertEquals("Example.com", web.host)
        assertTrue(web.isWeb)
        assertEquals("https://Example.com:443/a?b#c", web.uri)
        assertTrue(web.warnings.any { it.contains("hosts exactly") })
        val custom = DeepLinks.parse("myapp://open/item") as DeepLinkParse.Ok
        assertEquals("open", custom.host)
        assertFalse(custom.isWeb)
        assertEquals("", (DeepLinks.parse("tel:123") as DeepLinkParse.Ok).host)
    }

    @Test
    fun invalidDeepLinks() {
        listOf("", "   ", "example.com", "/path", "1abc://x", "https://", "https:///path").forEach {
            assertTrue(it, DeepLinks.parse(it) is DeepLinkParse.Invalid)
        }
    }

    @Test
    fun deepLinkSpoofingIsReported() {
        fun warnings(link: String) = (DeepLinks.parse(link) as DeepLinkParse.Ok).warnings
        val userInfo = DeepLinks.parse("https://good.com@evil.com/") as DeepLinkParse.Ok
        assertEquals("evil.com", userInfo.host)
        assertTrue(userInfo.warnings.any { it.contains("user name") })
        assertTrue(warnings("https://evil.com\\@good.com/").any { it.contains("backslash") })
        assertTrue(warnings("https://example.com/‮a").any { it.contains("invisible") })
        assertTrue(warnings("https://exаmple.com/").any { it.contains("non-ASCII") })
        assertTrue(warnings("MyApp://x").any { it.contains("schemes") })
        assertTrue(warnings("https://example.com/a b").any { it.contains("spaces") })
        assertEquals("[::1]", (DeepLinks.parse("http://[::1]:80/") as DeepLinkParse.Ok).host)
    }

    @Test
    fun domainVerdicts() {
        val states = mapOf(
            "example.com" to DomainVerdict.VERIFIED,
            "*.shop.com" to DomainVerdict.SELECTED,
            "*.a.shop.com" to DomainVerdict.NONE,
        )
        assertEquals(DomainVerdict.VERIFIED, DeepLinks.verdictFor("EXAMPLE.com", states))
        assertEquals(DomainVerdict.SELECTED, DeepLinks.verdictFor("www.shop.com", states))
        assertEquals(DomainVerdict.NONE, DeepLinks.verdictFor("x.a.shop.com", states))
        assertEquals(DomainVerdict.NOT_DECLARED, DeepLinks.verdictFor("shop.com", states))
        assertEquals(DomainVerdict.NOT_DECLARED, DeepLinks.verdictFor("evilshop.com", states))
        assertEquals(DomainVerdict.NOT_DECLARED, DeepLinks.verdictFor("example.com.evil.net", states))
    }

    @Test
    fun catalogsHaveNoDuplicates() {
        assertEquals(IntentCatalog.actions.size, IntentCatalog.actions.toSet().size)
        assertEquals(IntentCatalog.categories.size, IntentCatalog.categories.toSet().size)
        assertEquals(IntentCatalog.mimeTypes.size, IntentCatalog.mimeTypes.toSet().size)
        assertEquals(IntentFlags.all.size, IntentFlags.all.map { it.name }.toSet().size)
    }
}
