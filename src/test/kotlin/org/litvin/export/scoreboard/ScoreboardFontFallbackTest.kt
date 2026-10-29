package org.litvin.export.scoreboard

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A style font that cannot show the letters of a language gets a fallback font (B-11 E7-T4). The Ink Free font of
 * Windows 10 and Windows Server 2022 has no Cyrillic letters. The Ink Free font of Windows 11 has them, so this test
 * uses the symbol font Wingdings, which has no letters.
 */
class ScoreboardFontFallbackTest {
    private val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()

    @Test
    fun `a Russian text in a font without Cyrillic letters uses Segoe UI`() {
        assumeTrue("Wingdings" in installed && "Segoe UI" in installed, "Wingdings or Segoe UI is not installed")
        I18n.inVideo(AppLanguage.RUSSIAN) {
            assertEquals("Segoe UI", ScoreboardFonts.family("Wingdings", "Решающее очко"))
        }
    }

    @Test
    fun `a text that the style font shows keeps the style font`() {
        assumeTrue(INK_FREE in installed, "Ink Free is not installed")
        I18n.inVideo(AppLanguage.RUSSIAN) {
            assertEquals(INK_FREE, ScoreboardFonts.family(INK_FREE, "Nadal 40"))
        }
    }

    @Test
    fun `a Chinese text that no fallback font shows keeps the style font`() {
        assumeTrue("Georgia" in installed, "Georgia is not installed")
        I18n.inVideo(AppLanguage.RUSSIAN) {
            assertEquals("Georgia", ScoreboardFonts.family("Georgia", "田中"))
        }
    }
}
