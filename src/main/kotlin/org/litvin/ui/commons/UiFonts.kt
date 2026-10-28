package org.litvin.ui.commons

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.ScriptFonts
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.TextAttribute
import javax.swing.JComboBox
import javax.swing.ListCellRenderer
import javax.swing.text.StyleContext

/**
 * The UI font families of the app language (B-11 E8-T3). "Segoe UI" has no Japanese or Chinese letters, and Swing
 * uses no fallback font for it. Thus a Japanese app uses "Yu Gothic UI", and a Chinese app uses the Chinese UI font
 * of its script (see [ScriptFonts]). The language does not change while the app runs, so each value is read one time.
 */
internal object UiFonts {
    private const val SEGOE_SEMIBOLD = "Segoe UI Semibold"

    private val installed: Set<String> by lazy {
        runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet() }.getOrDefault(emptySet())
    }

    /** The family of the script of the app language, or null when "Segoe UI" shows the letters of the language. */
    val scriptFamily: String? by lazy { ScriptFonts.uiFamily(I18n.language, installed) }

    /** The semibold family of the app language when the PC has it, or null. Then the app uses the bold font. */
    val semiboldFamily: String? by lazy {
        if (scriptFamily != null) ScriptFonts.uiSemiboldFamily(I18n.language, installed) else SEGOE_SEMIBOLD.takeIf { it in installed }
    }

    /**
     * [font], or a font with the same size and style that shows [text] in [language]. A language name in its own
     * language ("日本語") can need a font that the UI font of the app language is not. The font is the UI font of
     * [language] when the PC has it, else the Java font "Dialog", which has a fallback font for each script.
     */
    fun forText(font: Font, text: String, language: AppLanguage): Font {
        if (font.canDisplayUpTo(text) == -1) return font
        val script = ScriptFonts.uiFamilyName(language)?.takeIf { it in installed }?.let { font.withFamily(it) }
        return script?.takeIf { it.canDisplayUpTo(text) == -1 } ?: font.withFamily(Font.DIALOG)
    }

    private fun Font.withFamily(family: String): Font = deriveFont(mapOf(TextAttribute.FAMILY to family))

    /**
     * A font of [family] that also shows the letters that [family] does not have (B-11 E3-T23). A plain
     * `Font("Segoe UI")` shows an empty box for a Japanese letter or an emoji in a user text, for example a project
     * name. This font uses the Java fallback fonts for such a letter. The size and the line height of the letters of
     * [family] do not change.
     */
    fun withFallback(family: String, style: Int, size: Float): Font =
        StyleContext.getDefaultStyleContext().getFont(family, style, 1).deriveFont(size)

    /**
     * Makes each entry of [combo] show in a font that has its letters. [languageOf] gives the language of the text of
     * an entry, or null when the UI font is correct.
     */
    fun <T> showLanguageNames(combo: JComboBox<T>, languageOf: (T) -> AppLanguage?) {
        val base = combo.renderer
        combo.renderer = ListCellRenderer { list, value, index, selected, focused ->
            base.getListCellRendererComponent(list, value, index, selected, focused).also { cell ->
                val language = value?.let(languageOf) ?: return@also
                cell.font = forText(cell.font, value.toString(), language)
            }
        }
    }
}
