package org.litvin.export.scoreboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.litvin.i18n.AppLanguage
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.text.Normalizer

/**
 * B-11 E7-T4: each scoreboard font can show all letters of each shipped language, so that the video texts and
 * the player names never show empty boxes. The letters are the CLDR exemplar characters in the composed form (NFC).
 *
 * The decomposed form (NFD) is not checked yet: Arial Black, Georgia, Trebuchet MS, and Ink Free have no combining
 * accents (U+0301, U+0303, U+0306, U+0308). E7-T4 records this gap.
 *
 * The fonts are the Windows fonts of the scoreboards, the statistics card, and the thumbnails. A font that is not
 * installed (for example on a server image) is not checked.
 */
class ScoreboardFontCoverageTest {
    @Test
    fun `each installed scoreboard font shows all letters of each language`() {
        val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val fonts = FONTS.filter { it in installed }
        assumeTrue(fonts.isNotEmpty(), "No scoreboard font is installed")
        val failures = fonts.flatMap { family ->
            val font = Font(family, Font.PLAIN, 24)
            AppLanguage.entries.flatMap { language ->
                val letters = LETTERS.getValue(language.baseCode)
                val text = Normalizer.normalize(letters, Normalizer.Form.NFC)
                val missing = text.filterNot { font.canDisplay(it) }.toSet()
                if (missing.isEmpty()) emptyList() else listOf("$family ${language.code}: " + missing.joinToString(" ") { "U+%04X".format(it.code) })
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    private companion object {
        val FONTS = listOf(SEGOE, "Segoe UI Semibold", "Segoe UI Variable", SEGOE_BLACK, ARIAL, ARIAL_BLACK, GEORGIA, TREBUCHET, TAHOMA, CONSOLAS, INK_FREE)

        private const val LATIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        /** The CLDR exemplar characters and the punctuation of the catalog of each language. */
        val LETTERS = mapOf(
            "en" to "$LATIN–—…“”‘’·×",
            "es" to "${LATIN}áéíóúüñÁÉÍÓÚÜÑ¿¡«»–—…·×",
            "pt" to "${LATIN}áàâãçéêíóôõúüÁÀÂÃÇÉÊÍÓÔÕÚÜºª«»–—…“”·×",
            "fr" to "${LATIN}àâæçéèêëîïôœùûüÿÀÂÆÇÉÈÊËÎÏÔŒÙÛÜŸº«»’–—…“”·×${Char(0xA0)}",
            "ru" to "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ0123456789«»„“–—…№·×",
        )
    }
}
