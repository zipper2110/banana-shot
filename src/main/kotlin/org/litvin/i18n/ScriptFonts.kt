package org.litvin.i18n

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Font

/**
 * The fonts of the languages whose letters the Latin fonts of the app ("Segoe UI", the scoreboard fonts) do not
 * have (B-11 E8-T2, E8-T3).
 *
 * Japanese and Chinese use the same code points for many characters, but the correct shapes are different (Han
 * unification). Simplified and Traditional Chinese also have different shapes. Thus the font follows the language,
 * not only the characters.
 *
 * The fonts are Windows fonts. Yu Gothic, Microsoft YaHei, Microsoft JhengHei, and their UI forms are in all
 * installations of Windows 10 and 11, so the app bundles no font and the installer does not get larger (plan,
 * "Decisions", "CJK fonts").
 */
object ScriptFonts {
    private val logger = KotlinLogging.logger {}

    private const val JAPANESE_VIDEO = "Yu Gothic"

    /** The UI font, the semibold UI font (or null), and the video font of each language with its own script. */
    private class Fonts(val ui: String, val uiSemibold: String?, val video: String)

    private val FONTS = mapOf(
        AppLanguage.JAPANESE to Fonts("Yu Gothic UI", "Yu Gothic UI Semibold", JAPANESE_VIDEO),
        // Microsoft YaHei and Microsoft JhengHei have no semibold form. The app uses their bold form.
        AppLanguage.CHINESE_SIMPLIFIED to Fonts("Microsoft YaHei UI", null, "Microsoft YaHei"),
        AppLanguage.CHINESE_TRADITIONAL to Fonts("Microsoft JhengHei UI", null, "Microsoft JhengHei"),
    )

    /**
     * The UI font family of [language], or null when "Segoe UI" shows its letters. When the PC does not have the
     * font of the language ([installed] is the list of the installed families), the family is the Java logical font
     * "Dialog", which has a fallback font for each script. Call it one time, because it logs a warning.
     */
    fun uiFamily(language: AppLanguage, installed: Set<String>): String? {
        val family = uiFamilyName(language) ?: return null
        if (family in installed) return family
        logger.warn { "The UI font \"$family\" of ${language.code} is not installed. The app uses the font \"${Font.DIALOG}\"." }
        return Font.DIALOG
    }

    /** The name of the UI font family of [language], or null when "Segoe UI" shows its letters. It does not log. */
    fun uiFamilyName(language: AppLanguage): String? = FONTS[language]?.ui

    /** The semibold UI font family of [language] when the PC has it, or null. */
    fun uiSemiboldFamily(language: AppLanguage, installed: Set<String>): String? =
        FONTS[language]?.uiSemibold?.takeIf { it in installed }

    /**
     * The video font family for a [text] that the font of a scoreboard style cannot show, or null. The language is
     * the language of the video texts. A text with kana is Japanese in all languages. A text with only Chinese
     * characters in a video of a language without these characters keeps the style font, because the text can be
     * Japanese or Chinese.
     */
    fun videoFamily(language: AppLanguage, text: String): String? = when {
        text.any(::isKana) -> JAPANESE_VIDEO
        else -> FONTS[language]?.video
    }

    /** Hiragana, katakana, and the half-width katakana. */
    private fun isKana(char: Char): Boolean = char in '぀'..'ヿ' || char in 'ｦ'..'ﾟ'
}
