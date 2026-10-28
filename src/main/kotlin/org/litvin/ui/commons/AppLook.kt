package org.litvin.ui.commons

import org.litvin.i18n.I18n
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.JComponent
import javax.swing.UIManager

/**
 * The look of the app: the theme, the fonts of the app language, and the locale of the Swing texts. `main` and the
 * UI flow tests use it, so the tests and their screenshots show the fonts of the real app (B-11 E8-T3). Without it,
 * Swing uses the Java font "Dialog", which shows the Japanese kana with the full width.
 */
internal object AppLook {
    /** Sets [theme] and the default font. Call it after the app language is installed and before the app makes a component. */
    fun start(theme: AppTheme, accent: Color = theme.accent) {
        Theme.start(theme, accent)
        UIManager.put("defaultFont", UiFonts.withFallback(UiFonts.scriptFamily ?: "Segoe UI", Font.PLAIN, 14f))
        // The texts of Swing and FlatLaf follow the app language. The default locale of the JVM does not change.
        val locale = I18n.language.locale
        JComponent.setDefaultLocale(locale)
        UIManager.getLookAndFeelDefaults().defaultLocale = locale
        UIManager.getDefaults().defaultLocale = locale
    }

    /** Gives all Swing components the UI font of the app language. Call it on the EDT before the main window opens. */
    fun installBaseFont() {
        val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        // A language with other letters (Japanese) uses the UI font of its script (B-11 E8-T3).
        val family = UiFonts.scriptFamily ?: when {
            families.contains("Segoe UI Variable") -> "Segoe UI Variable"
            families.contains("Segoe UI") -> "Segoe UI"
            else -> "Tahoma"
        }
        val baseSize = (UIManager.getFont("Label.font")?.size2D ?: 13f).coerceAtLeast(13f)
        // The fallback fonts show a user text in any script, for example a Japanese project name in English.
        val baseFont = UiFonts.withFallback(family, Font.PLAIN, baseSize.toInt().toFloat())

        val keys = UIManager.getDefaults().keys()
        while (keys.hasMoreElements()) {
            val key = keys.nextElement()
            if (key.toString().endsWith(".font")) UIManager.put(key, baseFont)
        }
        UIManager.put("defaultFont", baseFont)
        UIManager.put("ToolTip.hideAccelerator", true)
    }
}
