package org.litvin.ui.help

import java.awt.Component
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpPanelTest {
    @Test
    fun selectingPagesUpdatesContentSelectionAndNavigationOrder() {
        System.setProperty("java.awt.headless", "true")

        SwingUtilities.invokeAndWait {
            val panel = HelpPanel()
            assertEquals(HelpPage.entries.map { it.title }, panel.pageTitles())
            assertEquals(HelpPage.OVERVIEW, panel.selectedPage)

            panel.selectPage(HelpPage.EXPORT)
            assertEquals(HelpPage.EXPORT, panel.selectedPage)

            panel.selectPage(HelpPage.CROP)
            assertEquals(HelpPage.CROP, panel.selectedPage)
        }
    }

    @Test
    fun `each page ends with the Tell us link to the feedback form`() {
        System.setProperty("java.awt.headless", "true")
        var opened = 0

        SwingUtilities.invokeAndWait {
            val panel = HelpPanel(onTellUs = { opened++ })
            HelpPage.entries.forEach { page ->
                panel.selectPage(page)
                val link = buttons(panel).single { it.name == "help-tell-us" }
                link.doClick()
            }
            assertTrue(buttons(HelpPanel()).none { it.name == "help-tell-us" })
        }

        assertEquals(HelpPage.entries.size, opened)
    }

    private fun buttons(root: Component): List<AbstractButton> = buildList {
        if (root is AbstractButton) add(root)
        if (root is Container) root.components.forEach { addAll(buttons(it)) }
    }
}
