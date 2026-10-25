package org.litvin.ui.commons

import org.litvin.i18n.AppLanguage
import java.awt.Font
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UiFontsTest {
    @Test
    fun `a font that shows the text stays the same`() {
        val font = Font("Segoe UI", Font.PLAIN, 13)
        assertSame(font, UiFonts.forText(font, "Deutsch", AppLanguage.GERMAN))
    }

    @Test
    fun `each language name in the list shows without empty boxes`() {
        System.setProperty("java.awt.headless", "true")

        SwingUtilities.invokeAndWait {
            // The entries are the native names, as in the language lists of the app.
            val names = AppLanguage.entries.map { it.nativeName }
            val combo = JComboBox(names.toTypedArray()).apply {
                font = Font("Segoe UI", Font.PLAIN, 13)
                UiFonts.showLanguageNames(this) { name -> AppLanguage.entries.first { it.nativeName == name } }
            }
            val list = JList(names.toTypedArray()).apply { font = combo.font }
            val missing = names.filter { name ->
                val cell = combo.renderer.getListCellRendererComponent(list, name, 0, false, false)
                cell.font.canDisplayUpTo(name) != -1
            }
            assertEquals(emptyList(), missing)
        }
    }
}
