package org.litvin.ui.commons

import javax.swing.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** B-68 E3b: the key names of Ctrl+R and Cmd+R, and the key strokes of the pack keys. */
class AppShortcutsTest {
    @Test
    fun `the key name shows Shift, Ctrl, and Cmd`() {
        assertEquals("Shift+R", AppShortcuts.displayName("shift R"))
        assertEquals("Ctrl+R", AppShortcuts.displayName("ctrl R"))
        assertEquals("Ctrl+Shift+R", AppShortcuts.displayName("ctrl shift R"))
        assertEquals("Cmd+R", AppShortcuts.displayName("meta R"))
        assertEquals("Cmd+Shift+R", AppShortcuts.displayName("meta shift R"))
        assertEquals("Ctrl+Left", AppShortcuts.displayName("ctrl LEFT"))
    }

    @Test
    fun `the pack keys are Ctrl+R and Ctrl+Shift+R, or Cmd on macOS, and are not R`() {
        val mac = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)
        val modifier = if (mac) "Cmd" else "Ctrl"
        assertEquals("$modifier+R", AppShortcuts.NEXT_PACK.display)
        assertEquals("$modifier+Shift+R", AppShortcuts.PREVIOUS_PACK.display)
        val strokes = listOf(AppShortcuts.NEXT_POINT, AppShortcuts.PREVIOUS_POINT, AppShortcuts.NEXT_PACK, AppShortcuts.PREVIOUS_PACK)
            .map { assertNotNull(KeyStroke.getKeyStroke(it.keyStroke), it.keyStroke) }
        assertTrue(strokes.toSet().size == 4, "Each key stroke must be different: $strokes")
    }
}
