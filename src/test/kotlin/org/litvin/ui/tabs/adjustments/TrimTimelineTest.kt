package org.litvin.ui.tabs.adjustments

import org.litvin.adjustments.TrimRange
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The trim timeline under the video on the Trim sub-tab (B-62). */
class TrimTimelineTest {
    private val seeks = mutableListOf<Long>()
    private val commits = mutableListOf<Pair<TrimEnd, Long>>()
    private val timeline = TrimTimeline(onSeek = { seeks += it }, onCommit = { end, ms -> commits += end to ms }).apply {
        setSize(800, preferredSize.height)
        render(DURATION, TrimRange(10_000, 50_000), listOf(5_000L to 8_000L, 20_000L to 25_000L))
    }

    private fun mouse(id: Int, x: Int) {
        val modifiers = if (id == MouseEvent.MOUSE_RELEASED) 0 else MouseEvent.BUTTON1_DOWN_MASK
        val event = MouseEvent(timeline, id, 0L, modifiers, x, timeline.trackMiddle, 1, false, MouseEvent.BUTTON1)
        timeline.dispatchEvent(event)
    }

    private fun onEdt(body: () -> Unit) = SwingUtilities.invokeAndWait(body)

    @Test
    fun `a drag of the start handle seeks with it and the release commits the new start`() = onEdt {
        val from = timeline.handleX(TrimEnd.START)
        mouse(MouseEvent.MOUSE_PRESSED, from)
        mouse(MouseEvent.MOUSE_DRAGGED, from + 40)
        mouse(MouseEvent.MOUSE_RELEASED, from + 40)
        val (end, ms) = commits.single()
        assertEquals(TrimEnd.START, end)
        assertTrue(ms in 12_000L..14_000L, "start $ms")
        assertEquals(ms, seeks.last())
    }

    @Test
    fun `a handle cannot pass the other handle`() = onEdt {
        val from = timeline.handleX(TrimEnd.END)
        mouse(MouseEvent.MOUSE_PRESSED, from)
        mouse(MouseEvent.MOUSE_DRAGGED, 0)
        mouse(MouseEvent.MOUSE_RELEASED, 0)
        assertEquals(TrimEnd.END to 10_000L + TrimRange.MIN_LENGTH_MS, commits.single())
    }

    @Test
    fun `a click on the track seeks and does not change the trim`() = onEdt {
        mouse(MouseEvent.MOUSE_PRESSED, 400)
        mouse(MouseEvent.MOUSE_RELEASED, 400)
        assertEquals(emptyList(), commits)
        assertTrue(seeks.single() in 29_000L..31_000L, "seek ${seeks.single()}")
    }

    private companion object {
        const val DURATION = 60_000L
    }
}
