package org.litvin.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.litvin.export.scoreboard.ScoreboardFonts
import java.awt.Font
import java.awt.GraphicsEnvironment

/** B-11 E8-T2 and E8-T3: the fonts of the Japanese and the Chinese letters in the UI and in the video. */
class ScriptFontsTest {
    @Test
    fun `Japanese uses the Japanese UI font, and the other languages use Segoe UI`() {
        assertEquals("Yu Gothic UI", ScriptFonts.uiFamily(AppLanguage.JAPANESE, setOf("Yu Gothic UI", "Yu Gothic UI Semibold")))
        assertEquals("Yu Gothic UI Semibold", ScriptFonts.uiSemiboldFamily(AppLanguage.JAPANESE, setOf("Yu Gothic UI", "Yu Gothic UI Semibold")))
        assertNull(ScriptFonts.uiFamily(AppLanguage.GERMAN, setOf("Yu Gothic UI")))
        assertNull(ScriptFonts.uiSemiboldFamily(AppLanguage.GERMAN, setOf("Yu Gothic UI Semibold")))
    }

    @Test
    fun `Simplified and Traditional Chinese use the Chinese UI font of their script`() {
        val installed = setOf("Microsoft YaHei UI", "Microsoft JhengHei UI")
        assertEquals("Microsoft YaHei UI", ScriptFonts.uiFamily(AppLanguage.CHINESE_SIMPLIFIED, installed))
        assertEquals("Microsoft JhengHei UI", ScriptFonts.uiFamily(AppLanguage.CHINESE_TRADITIONAL, installed))
        // The Chinese UI fonts have no semibold form. The app then uses the bold form.
        assertNull(ScriptFonts.uiSemiboldFamily(AppLanguage.CHINESE_SIMPLIFIED, installed + "Segoe UI Semibold"))
    }

    @Test
    fun `a missing Chinese UI font gives the Dialog font`() {
        assertEquals(Font.DIALOG, ScriptFonts.uiFamily(AppLanguage.CHINESE_SIMPLIFIED, setOf("Segoe UI", "Microsoft JhengHei UI")))
        assertEquals(Font.DIALOG, ScriptFonts.uiFamily(AppLanguage.CHINESE_TRADITIONAL, setOf("Segoe UI", "Microsoft YaHei UI")))
    }

    @Test
    fun `a Chinese video uses the video font of its script, and a text with kana uses the Japanese font`() {
        assertEquals("Microsoft YaHei", ScriptFonts.videoFamily(AppLanguage.CHINESE_SIMPLIFIED, "决胜分"))
        assertEquals("Microsoft JhengHei", ScriptFonts.videoFamily(AppLanguage.CHINESE_TRADITIONAL, "決勝分"))
        assertEquals("Yu Gothic", ScriptFonts.videoFamily(AppLanguage.CHINESE_SIMPLIFIED, "たなか"))
    }

    @Test
    fun `a scoreboard label in Chinese uses the Chinese font, and a Latin label keeps the style font`() {
        val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        assumeTrue("Microsoft YaHei" in installed && "Microsoft JhengHei" in installed, "The Chinese fonts are not installed")
        I18n.inVideo(AppLanguage.CHINESE_SIMPLIFIED) {
            assertEquals("Microsoft YaHei", ScoreboardFonts.family("Arial Black", "决胜分"))
            assertEquals("Arial Black", ScoreboardFonts.family("Arial Black", "40"))
        }
        I18n.inVideo(AppLanguage.CHINESE_TRADITIONAL) {
            assertEquals("Microsoft JhengHei", ScoreboardFonts.family("Arial Black", "決勝分"))
            assertEquals("Arial Black", ScoreboardFonts.family("Arial Black", "Nadal"))
        }
    }

    /**
     * The UI font of a script shows each character of the catalog of its language. Swing uses no fallback font for
     * a physical font, so a missing character shows as an empty box (for example "−" in Microsoft JhengHei UI).
     */
    @Test
    fun `the UI font of each script shows all characters of its catalog`() {
        val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val failures = AppLanguage.entries.mapNotNull { language ->
            val family = ScriptFonts.uiFamilyName(language)?.takeIf { it in installed } ?: return@mapNotNull null
            val font = Font(family, Font.PLAIN, 14)
            val missing = MessageCatalog.loadWithParents(language).values.joinToString("").filterNot { it.isWhitespace() || font.canDisplay(it) }.toSet()
            missing.takeIf { it.isNotEmpty() }?.let { "$family ${language.code}: " + it.joinToString(" ") { char -> "U+%04X".format(char.code) } }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `a missing Japanese UI font gives the Dialog font`() {
        assertEquals(Font.DIALOG, ScriptFonts.uiFamily(AppLanguage.JAPANESE, setOf("Segoe UI")))
        assertNull(ScriptFonts.uiSemiboldFamily(AppLanguage.JAPANESE, setOf("Segoe UI")))
    }

    @Test
    fun `a Japanese video and a text with kana use the Japanese video font`() {
        assertEquals("Yu Gothic", ScriptFonts.videoFamily(AppLanguage.JAPANESE, "試合"))
        assertEquals("Yu Gothic", ScriptFonts.videoFamily(AppLanguage.ENGLISH, "ゲーム"))
        assertNull(ScriptFonts.videoFamily(AppLanguage.ENGLISH, "试合"))
    }

    @Test
    fun `a scoreboard label in Japanese uses the Japanese font, and a Latin label keeps the style font`() {
        assumeTrue("Yu Gothic" in GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames, "Yu Gothic is not installed")
        I18n.inVideo(AppLanguage.JAPANESE) {
            assertEquals("Yu Gothic", ScoreboardFonts.family("Arial Black", "ゴールデンポイント"))
            assertEquals("Arial Black", ScoreboardFonts.family("Arial Black", "40"))
            assertEquals("Arial Black", ScoreboardFonts.family("Arial Black", "Nadal"))
        }
        // In an English video, a name with only Chinese characters can be Chinese, so the style font stays.
        I18n.inVideo(AppLanguage.ENGLISH) {
            assertEquals("Georgia", ScoreboardFonts.family("Georgia", "田中"))
            assertEquals("Yu Gothic", ScoreboardFonts.family("Georgia", "たなか"))
        }
    }
}
