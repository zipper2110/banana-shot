package org.litvin.ui.tabs.points.ui

import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointStartButtonTest {
    @Test
    fun withoutAPendingPointTheButtonShowsNoTime() {
        SwingUtilities.invokeAndWait {
            val button = PointStartButton(onStart = {})
            button.setState(pendingStartMs = null)

            assertFalse(button.pending)
            assertEquals("", button.value)
            assertEquals("Set point start at the playhead [Enter]", button.toolTipText)
        }
    }

    @Test
    fun aPendingPointShowsItsStart() {
        SwingUtilities.invokeAndWait {
            val button = PointStartButton(onStart = {})
            button.setState(pendingStartMs = 1_107_500)

            assertTrue(button.pending)
            assertEquals("00:18:27.5", button.value)

            button.setState(pendingStartMs = null)
            assertFalse(button.pending)
            assertEquals("", button.value)
        }
    }

    @Test
    fun aClickStartsAPoint() {
        val calls = mutableListOf<String>()
        SwingUtilities.invokeAndWait {
            PointStartButton(onStart = { calls += "start" }).doClick()
        }
        assertEquals(listOf("start"), calls)
    }
}
