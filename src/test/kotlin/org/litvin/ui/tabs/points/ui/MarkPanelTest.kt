package org.litvin.ui.tabs.points.ui

import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities
import kotlin.test.assertEquals

class MarkPanelTest {
    @Test
    fun withoutAPendingPointPointStartIsTheNextStep() {
        SwingUtilities.invokeAndWait {
            val panel = MarkPanel(onStart = {}, onEnd = {})
            panel.setState(pendingStartMs = null, currentMs = 1_092_000)

            assertEquals(StepTile.State.NEXT, panel.startTile.state)
            assertEquals(StepTile.State.IDLE, panel.endTile.state)
            assertEquals("—", panel.startTile.value)
            assertEquals("—", panel.endTile.value)
            assertEquals("", panel.stepOfText)
        }
    }

    @Test
    fun aPendingPointShowsItsStartAndTheEndFollowsThePlayhead() {
        SwingUtilities.invokeAndWait {
            val panel = MarkPanel(onStart = {}, onEnd = {})
            panel.setState(pendingStartMs = 1_107_500, currentMs = 1_112_000)

            assertEquals(StepTile.State.DONE, panel.startTile.state)
            assertEquals(StepTile.State.LIVE, panel.endTile.state)
            assertEquals("00:18:27.5", panel.startTile.value)
            assertEquals("00:18:32.0", panel.endTile.value)
            assertEquals("Pending point", panel.stepOfText)

            panel.setState(pendingStartMs = 1_107_500, currentMs = 1_113_000)
            assertEquals("00:18:33.0", panel.endTile.value)
        }
    }

    @Test
    fun theTilesCallTheMarkActions() {
        val calls = mutableListOf<String>()
        SwingUtilities.invokeAndWait {
            val panel = MarkPanel(onStart = { calls += "start" }, onEnd = { calls += "end" })
            panel.startTile.doClick()
            panel.endTile.doClick()
        }
        assertEquals(listOf("start", "end"), calls)
    }
}
