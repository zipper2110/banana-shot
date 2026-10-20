package org.litvin.i18n

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.concurrent.Executors

/** B-11 E5: the texts in the video have their own language. */
class VideoLanguageTest {
    private val savedLocale = DisplayFormats.formatLocale

    @AfterEach
    fun restore() {
        DisplayFormats.formatLocale = savedLocale
        I18n.install(Messages.english())
    }

    @Test
    fun `a video block gives the texts of the video language and then the app texts again`() {
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))

        val inVideo = I18n.inVideo(AppLanguage.ENGLISH) { tr("stats.card.match") to I18n.videoLanguage }

        assertEquals("Match statistics" to AppLanguage.ENGLISH, inVideo)
        assertEquals("Estadísticas del partido", tr("stats.card.match"))
        assertNull(I18n.videoLanguage)
    }

    @Test
    fun `a video block in a video block gives the inner language and then the outer language`() {
        val texts = I18n.inVideo(AppLanguage.SPANISH_SPAIN) {
            val inner = I18n.inVideo(AppLanguage.ENGLISH) { tr("stats.card.match") }
            inner to tr("stats.card.match")
        }

        assertEquals("Match statistics" to "Estadísticas del partido", texts)
    }

    @Test
    fun `a video block on one thread does not change the texts of another thread`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val other = I18n.inVideo(AppLanguage.SPANISH_SPAIN) { executor.submit<String> { tr("stats.card.match") }.get() }

            assertEquals("Match statistics", other)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a failure in a video block does not keep the video language`() {
        runCatching { I18n.inVideo(AppLanguage.SPANISH_SPAIN) { error("render failed") } }

        assertEquals("Match statistics", tr("stats.card.match"))
    }

    @Test
    fun `the video in the app language uses the app texts, also the test language`() {
        I18n.install(Messages.of(AppLanguage.ENGLISH, pseudo = true))

        val text = I18n.inVideo(AppLanguage.ENGLISH) { tr("stats.card.match") }

        assertEquals(tr("stats.card.match"), text)
        assertEquals('[', text.first())
    }

    @Test
    fun `the upper case of a video text uses the video language`() {
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))

        assertEquals("MATCH STATISTICS", I18n.inVideo(AppLanguage.ENGLISH) { DisplayFormats.upper(tr("stats.card.match")) })
    }

    @Test
    fun `numbers in the video use the regional format when its language is the video language`() {
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-MX")

        val spanish = I18n.inVideo(AppLanguage.SPANISH_SPAIN) { DisplayFormats.decimal(4.2, 1) to DisplayFormats.percent(64) }

        assertEquals("4.2" to "64%", spanish)
    }

    @Test
    fun `numbers in the video use the format of the video language when the regional format has another language`() {
        DisplayFormats.formatLocale = Locale.US

        val spanish = I18n.inVideo(AppLanguage.SPANISH_SPAIN) { DisplayFormats.decimal(4.2, 1) to DisplayFormats.percent(64) }
        val english = I18n.inVideo(AppLanguage.ENGLISH) { DisplayFormats.decimal(4.2, 1) to DisplayFormats.percent(64) }

        // Spanish has a no-break space before the percent sign.
        assertEquals("4,2" to "64\u00A0%", spanish)
        assertEquals("4.2" to "64%", english)
        // The app interface keeps the regional format.
        assertEquals("4.2", DisplayFormats.decimal(4.2, 1))
    }

    @Test
    fun `an English video with an Arabic regional format has only the digits 0-9`() {
        DisplayFormats.formatLocale = Locale.forLanguageTag("ar-EG")

        val numbers = I18n.inVideo(AppLanguage.ENGLISH) {
            listOf(DisplayFormats.decimal(4.2, 1), DisplayFormats.percent(64), DisplayFormats.integer(1234))
        }

        assertEquals(listOf("4.2", "64%", "1,234"), numbers)
    }
}
