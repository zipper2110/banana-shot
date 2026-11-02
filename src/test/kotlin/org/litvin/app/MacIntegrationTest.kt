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
            assertEquals("Fn+F1", AppShortcuts.HELP.display)
        } else {
            assertEquals("DELETE", AppShortcuts.DELETE.keyStroke)
            assertEquals(emptyList(), AppShortcuts.alsoBound(AppShortcuts.DELETE))
            assertEquals("F1", AppShortcuts.HELP.display)
        }
    }
}
