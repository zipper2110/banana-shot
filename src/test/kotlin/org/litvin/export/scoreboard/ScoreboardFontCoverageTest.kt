package org.litvin.export.scoreboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File
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
 *
 * The style fonts have no Japanese or Chinese letters. For a Japanese or a Chinese video, the check uses the font
 * that [ScoreboardFonts.family] gives for the text (B-11 E8-T2). The letters are the punctuation of the script, the
 * kana for Japanese, and all characters of the catalog of the language, not the CLDR list of more than 2,000
 * characters.
 */
class ScoreboardFontCoverageTest {
    @Test
    fun `each installed scoreboard font shows all letters of each language`() {
        val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val fonts = FONTS.filter { it in installed }
        assumeTrue(fonts.isNotEmpty(), "No scoreboard font is installed")
        val failures = fonts.flatMap { family ->
            AppLanguage.entries.flatMap { language ->
                val letters = LETTERS[language.baseCode] ?: scriptLetters(language)
                val text = Normalizer.normalize(letters, Normalizer.Form.NFC)
                val shown = I18n.inVideo(language) { ScoreboardFonts.family(family, text) }
                val font = Font(shown, Font.PLAIN, 24)
                val missing = text.filterNot { font.canDisplay(it) }.toSet()
                if (missing.isEmpty()) emptyList() else listOf("$shown ${language.code}: " + missing.joinToString(" ") { "U+%04X".format(it.code) })
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    /**
     * The punctuation of the script, the full-width digits, and each character of the catalog of [language]. Japanese
     * also has all kana.
     */
    private fun scriptLetters(language: AppLanguage): String {
        val kana = if (language == AppLanguage.JAPANESE) ('ぁ'..'ゖ') + ('ァ'..'ヺ') + "ー・".toList() else emptyList()
        val punctuation = "「」『』“”、。，：；（）！？～—…·　".toList() + ('０'..'９')
        val suffix = "_" + language.code.replace('-', '_') + ".properties"
        val catalog = File("src/main/resources/i18n").listFiles { file -> file.name.endsWith(suffix) }.orEmpty()
            .flatMap { file -> file.readLines(Charsets.UTF_8).filterNot { it.startsWith("#") }.map { it.substringAfter('=') } }
            .joinToString("")
        assertTrue(catalog.isNotEmpty(), "No catalog for ${language.code}")
        return (kana + punctuation + catalog.toList()).filterNot { it.isWhitespace() && it != '　' }.distinct().joinToString("")
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
            "de" to "${LATIN}äöüßÄÖÜ„“‚‘–—…·×",
            "ru" to "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ0123456789«»„“–—…№·×",
        )
    }
}
