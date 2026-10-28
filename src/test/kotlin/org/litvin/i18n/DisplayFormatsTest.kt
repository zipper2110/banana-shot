package org.litvin.i18n

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.litvin.export.RenderFormatting
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class DisplayFormatsTest {
    private val savedLocale = DisplayFormats.formatLocale

    @AfterEach
    fun restore() {
        DisplayFormats.formatLocale = savedLocale
        I18n.install(Messages.english())
    }

    @Test
    fun `numbers follow the regional format`() {
        DisplayFormats.formatLocale = Locale.US
        assertEquals("12,345", DisplayFormats.integer(12345))
        assertEquals("1.5", DisplayFormats.decimal(1.5, 1))
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-ES")
        assertEquals("12.345", DisplayFormats.integer(12345))
        assertEquals("1,5", DisplayFormats.decimal(1.5, 1))
        assertEquals("1,25", DisplayFormats.decimal(1.25, 0, 2))
    }

    @Test
    fun `a percentage follows the regional format`() {
        DisplayFormats.formatLocale = Locale.US
        assertEquals("64%", DisplayFormats.percent(64))
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-ES")
        assertEquals("64\u00A0%", DisplayFormats.percent(64))
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-MX")
        assertEquals("64%", DisplayFormats.percent(64))
    }

    @Test
    fun `a regional format with other digits gets the digits 0 to 9`() {
        DisplayFormats.formatLocale = Locale.forLanguageTag("ar-EG")
        assertEquals("42", DisplayFormats.integer(42))
    }

    @Test
    fun `a typed number can use a comma or a point`() {
        assertEquals(1.5, DisplayFormats.parseDecimal("1,5"))
        assertEquals(1.5, DisplayFormats.parseDecimal(" 1.5 "))
        assertEquals(-2.0, DisplayFormats.parseDecimal("\u22122"))
        assertNull(DisplayFormats.parseDecimal("1,234.5"))
        assertNull(DisplayFormats.parseDecimal(""))
        assertNull(DisplayFormats.parseDecimal("abc"))
    }

    @Test
    fun `a date has month names only when the regional format has the app language`() {
        val date = LocalDate.of(2026, 10, 19)
        DisplayFormats.formatLocale = Locale.US
        assertEquals("Oct 19, 2026", DisplayFormats.date(date))
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-ES")
        assertEquals("19/10/26", DisplayFormats.date(date))
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        assertEquals("19 oct 2026", DisplayFormats.date(date))
    }

    @Test
    fun `a time follows the regional format`() {
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-ES")
        assertEquals("14:05", DisplayFormats.time(LocalTime.of(14, 5)))
    }


    @Test
    fun `the acceptance table of E1-T5 gives the expected texts`() {
        val date = LocalDate.of(2026, 10, 10)
        val time = LocalTime.of(15, 45)
        val rows = listOf(
            "en-US" to listOf("Oct 10, 2026", "3:45\u202FPM", "2.35 GB", "64%"),
            "es-ES" to listOf("10/10/26", "15:45", "2,35 GB", "64\u00A0%"),
            "es-MX" to listOf("10/10/26", "3:45 p.m.", "2.35 GB", "64%"),
            "de-DE" to listOf("10.10.26", "15:45", "2,35 GB", "64\u00A0%"),
        )
        rows.forEach { (tag, expected) ->
            DisplayFormats.formatLocale = Locale.forLanguageTag(tag)
            val actual = listOf(DisplayFormats.date(date), DisplayFormats.time(time), RenderFormatting.formatSize(2_350_000_000), DisplayFormats.percent(64))
            assertEquals(expected, actual, tag)
        }
    }

    @Test
    fun `a regional format with other digits or another calendar gets the digits 0 to 9 and the Gregorian year`() {
        val date = LocalDate.of(2026, 10, 10)
        listOf("ar-EG", "fa-IR", "th-TH", "th-TH-u-nu-thai", "ja-JP-u-ca-japanese", "ar-SA-u-ca-islamic").forEach { tag ->
            DisplayFormats.formatLocale = Locale.forLanguageTag(tag)
            val texts = listOf(
                DisplayFormats.date(date),
                DisplayFormats.dateTime(LocalDateTime.of(date, LocalTime.of(15, 45))),
                DisplayFormats.time(LocalTime.of(15, 45)),
                RenderFormatting.formatSize(2_350_000_000),
                DisplayFormats.percent(64),
                DisplayFormats.integer(1_234_567),
            )
            texts.forEach { text ->
                assertTrue(text.none { Character.isDigit(it) && it !in '0'..'9' }, "$tag: $text has other digits")
            }
            assertTrue("26" in texts[0] || "2026" in texts[0], "$tag: ${texts[0]} has no Gregorian year")
            assertTrue("69" !in texts[0] && "2569" !in texts[0], "$tag: ${texts[0]} has the Buddhist year")
        }
    }

    @Test
    fun `numbers have the group separator and the minus sign of the regional format`() {
        DisplayFormats.formatLocale = Locale.forLanguageTag("de-DE")
        assertEquals("1.234.567", DisplayFormats.integer(1_234_567))
        DisplayFormats.formatLocale = Locale.forLanguageTag("sv-SE")
        assertEquals("\u22122,5", DisplayFormats.decimal(-2.5, 1))
    }

    @Test
    fun `an empty or unknown regional format gives en-US`() {
        listOf(Locale.ROOT, Locale.forLanguageTag("und"), Locale.forLanguageTag("xx-XX")).forEach { locale ->
            assertEquals(Locale.US, DisplayFormats.usableFormatLocale(locale), locale.toLanguageTag())
        }
        DisplayFormats.formatLocale = DisplayFormats.usableFormatLocale(Locale.forLanguageTag("xx-XX"))
        assertEquals("Oct 10, 2026", DisplayFormats.date(LocalDate.of(2026, 10, 10)))
        assertEquals("2.35 GB", RenderFormatting.formatSize(2_350_000_000))
        listOf("es-ES", "de-DE", "th-TH-u-nu-thai", "sv-SE").forEach { tag ->
            assertEquals(Locale.forLanguageTag(tag), DisplayFormats.usableFormatLocale(Locale.forLanguageTag(tag)))
        }
    }
    @Test
    fun `upper case uses the rules of the app language`() {
        assertEquals("STRASSE", DisplayFormats.upper("straße"))
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        assertEquals("PÁDEL", DisplayFormats.upper("pádel"))
    }
}
