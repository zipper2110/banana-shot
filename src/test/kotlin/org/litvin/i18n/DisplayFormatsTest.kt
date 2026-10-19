package org.litvin.i18n

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
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
        assertEquals("64 %", DisplayFormats.percent(64))
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
        assertEquals(-2.0, DisplayFormats.parseDecimal("−2"))
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
    fun `upper case uses the rules of the app language`() {
        assertEquals("STRASSE", DisplayFormats.upper("straße"))
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        assertEquals("PÁDEL", DisplayFormats.upper("pádel"))
    }
}
