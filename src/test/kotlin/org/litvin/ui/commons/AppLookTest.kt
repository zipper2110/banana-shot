package org.litvin.ui.commons

import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-11 E3-T23: a user can type a text in any script, also when the app is in English. The UI fonts use the fallback
 * fonts of Java for the letters that "Segoe UI" does not have. Without them, "決勝 🎾" showed empty boxes.
 */
class AppLookTest {
    private val text = "決勝 🎾 山田 李华 Финал Τελικός"

    @Test
    fun `a font with the fallback fonts shows all scripts and keeps the size of the English letters`() {
        val family = listOf("Segoe UI", Font.SANS_SERIF).first { it in families() }
        val plain = Font(family, Font.PLAIN, 1).deriveFont(14f)
        val font = UiFonts.withFallback(family, Font.PLAIN, 14f)

        assertEquals(-1, font.canDisplayUpTo(text))
        assertEquals(UiKit.textWidth("Projects Thumbnail", plain), UiKit.textWidth("Projects Thumbnail", font))
        val metrics = font.getLineMetrics("Hg", UiKit.frc)
        val plainMetrics = plain.getLineMetrics("Hg", UiKit.frc)
        assertEquals(plainMetrics.ascent, metrics.ascent)
        assertEquals(plainMetrics.descent, metrics.descent)
        assertEquals(-1, UiFonts.withFallback(family, Font.BOLD, 13f).canDisplayUpTo(text))
        assertEquals(-1, UiKit.trackedFont(11f, 0.05).canDisplayUpTo(text))
    }

    @Test
    fun `a text field and a label of the app look show a user text in any script`() {
        val previous = UIManager.getLookAndFeel()
        val fontKeys = UIManager.getDefaults().keys().toList().map { it.toString() }.filter { it.endsWith(".font") }
        try {
            SwingUtilities.invokeAndWait {
                AppLook.start(AppTheme.DEFAULT)
                AppLook.installBaseFont()
                assertEquals(-1, JTextField(text).font.canDisplayUpTo(text))
                assertEquals(-1, JLabel(text).font.canDisplayUpTo(text))
            }
        } finally {
            SwingUtilities.invokeAndWait {
                // AppLook puts the fonts into the user defaults. Remove them, so that the other tests keep their fonts.
                (fontKeys + "defaultFont" + "ToolTip.hideAccelerator").forEach { UIManager.put(it, null) }
                UIManager.setLookAndFeel(previous)
            }
        }
    }

    private fun families(): Set<String> = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
}
