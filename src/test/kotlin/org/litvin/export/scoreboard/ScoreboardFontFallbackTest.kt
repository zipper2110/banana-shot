package org.litvin.export.scoreboard

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A style font that cannot show the letters of a language gets a fallback font (B-11 E7-T4). The bundled fonts
 * (B-13, step 10): Gelasio and Archivo Black have no Cyrillic letters, and Caveat has no Greek letters. The app has
 * the fonts on each system, so these checks run on each system.
 */
class ScoreboardFontFallbackTest {
    @Test
    fun `a Russian text in a font without Cyrillic letters uses Inter`() {
        I18n.inVideo(AppLanguage.RUSSIAN) {
            assertEquals(INTER, ScoreboardFonts.family(GELASIO, "Решающее очко"))
        }
    }

    @Test
    fun `a Russian text in Archivo Black uses the heavy Inter Black`() {
        I18n.inVideo(AppLanguage.RUSSIAN) {
            assertEquals(INTER_BLACK, ScoreboardFonts.family(ARCHIVO_BLACK, "Решающее очко"))
        }
    }

    @Test
    fun `a text that the style font shows keeps the style font`() {
        I18n.inVideo(AppLanguage.RUSSIAN) {
            assertEquals(CAVEAT, ScoreboardFonts.family(CAVEAT, "Nadal 40"))
            assertEquals(CAVEAT, ScoreboardFonts.family(CAVEAT, "Решающее очко"))
            assertEquals(GELASIO, ScoreboardFonts.family(GELASIO, "Nadal 40"))
        }
    }

    @Test
    fun `a Greek text in Caveat uses Inter`() {
        assertEquals(INTER, ScoreboardFonts.family(CAVEAT, "Παπαδόπουλος"))
    }

    @Test
    fun `a Chinese text that no fallback font shows keeps the style font`() {
        I18n.inVideo(AppLanguage.RUSSIAN) {
            assertEquals(GELASIO, ScoreboardFonts.family(GELASIO, "田中"))
        }
    }
}
