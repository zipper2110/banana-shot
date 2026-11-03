package org.litvin.app

import org.litvin.ui.commons.AppShortcuts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacIntegrationTest {
    @Test
    fun `the system name tells macOS`() {
        assertTrue(MacIntegration.isMac("Mac OS X"))
        assertFalse(MacIntegration.isMac("Windows 11"))
    }

    @Test
    fun `on macOS the delete shortcut is the delete key of a Mac keyboard and forward delete also works`() {
        if (MacIntegration.isMac()) {
            assertEquals("BACK_SPACE", AppShortcuts.DELETE.keyStroke)
            assertEquals(listOf("DELETE"), AppShortcuts.alsoBound(AppShortcuts.DELETE))
            assertEquals("⌘?", AppShortcuts.HELP.display)
            assertEquals("⌘?", AppShortcuts.helpKey)
        } else {
            assertEquals("DELETE", AppShortcuts.DELETE.keyStroke)
            assertEquals(emptyList(), AppShortcuts.alsoBound(AppShortcuts.DELETE))
            assertEquals("F1", AppShortcuts.HELP.display)
            assertEquals("F1", AppShortcuts.helpKey)
        }
    }

    @Test
    fun `the Help menu opens the help with Cmd+?`() {
        var opened = 0
        val bar = MacIntegration.helpMenuBar { opened++ }
        val item = bar.getMenu(0).getItem(0)
        assertEquals('?', item.accelerator.keyChar)
        assertEquals(java.awt.event.InputEvent.META_DOWN_MASK, item.accelerator.modifiers and java.awt.event.InputEvent.META_DOWN_MASK)
        item.doClick(0)
        assertEquals(1, opened)
    }

    @Test
    fun `Cmd+? is the Help key on a US and on a Russian layout`() {
        val source = javax.swing.JPanel()
        fun press(code: Int, char: Char, modifiers: Int) =
            java.awt.event.KeyEvent(source, java.awt.event.KeyEvent.KEY_PRESSED, 0L, modifiers, code, char)
        val cmd = java.awt.event.InputEvent.META_DOWN_MASK
        val shift = java.awt.event.InputEvent.SHIFT_DOWN_MASK
        // The key events that Java gave on the M1 Mac (2026-11-03).
        assertTrue(MacIntegration.isHelpKey(press(java.awt.event.KeyEvent.VK_SLASH, '/', cmd or shift)))
        assertTrue(MacIntegration.isHelpKey(press(java.awt.event.KeyEvent.VK_SLASH, '?', cmd or shift)))
        assertFalse(MacIntegration.isHelpKey(press(java.awt.event.KeyEvent.VK_SLASH, '/', cmd)))
        assertFalse(MacIntegration.isHelpKey(press(java.awt.event.KeyEvent.VK_7, '&', cmd or shift)))
        assertFalse(MacIntegration.isHelpKey(press(java.awt.event.KeyEvent.VK_SLASH, '?', shift)))
    }
}
