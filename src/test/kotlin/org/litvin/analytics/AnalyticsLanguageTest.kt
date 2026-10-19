package org.litvin.analytics

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The conversion of a locale to a value of the language list (B-11 E2, E2-T3). */
class AnalyticsLanguageTest {
    private val defaultLocale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun code(tag: String) = AnalyticsEvent.Language.of(Locale.forLanguageTag(tag)).key

    @Test
    fun `a regional locale gives its primary language`() {
        assertEquals("es", code("es-AR"))
        assertEquals("es", code("es-419"))
        assertEquals("en", code("en-GB"))
        assertEquals("zh", code("zh-Hant-TW"))
        assertEquals("sr", code("sr-Latn-RS"))
    }

    @Test
    fun `a language that is not in the list gives other`() {
        assertEquals("other", code("sw-KE"))
        assertEquals("other", code("fil-PH"))
        assertEquals("other", code("yi"))
    }

    @Test
    fun `an empty language and the Windows pseudo-locales give other`() {
        listOf("und", "qps-ploc", "qps-ploca", "qps-plocm", "qps").forEach { assertEquals("other", code(it), it) }
        assertEquals("other", AnalyticsEvent.Language.of(Locale.ROOT).key)
        assertEquals("other", AnalyticsEvent.Language.of(null).key)
        assertEquals("other", AnalyticsEvent.Language.ofCode(null).key)
        assertEquals("other", AnalyticsEvent.Language.ofCode("").key)
    }

    @Test
    fun `the old codes and Norwegian give the current codes`() {
        assertEquals("he", AnalyticsEvent.Language.ofCode("iw").key)
        assertEquals("id", AnalyticsEvent.Language.ofCode("in").key)
        assertEquals("other", AnalyticsEvent.Language.ofCode("ji").key)
        assertEquals("nb", AnalyticsEvent.Language.ofCode("no").key)
        assertEquals("nb", code("no-NO"))
        assertEquals("nb", code("nb-NO"))
    }

    @Test
    fun `the lower case does not use the default locale`() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        assertEquals("it", AnalyticsEvent.Language.of(Locale.forLanguageTag("it-IT")).key)
        assertEquals("it", AnalyticsEvent.Language.ofCode("IT").key)
    }

    @Test
    fun `each available locale gives a value of the list`() {
        Locale.getAvailableLocales().forEach { locale ->
            assertTrue(AnalyticsEvent.Language.of(locale).key in AnalyticsEvent.Language.CODES, locale.toLanguageTag())
        }
    }
}
