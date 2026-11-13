package org.litvin.ui.tabs.points.ui

import org.litvin.points.PointV1
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-68 E3b: a clip of zero length on the timeline, and the dimmed fault clips and let clips. */
class TimelineMarksTest {
    @Test
    fun `a clip of zero length is a thin mark with its center at the clip time`() {
        assertEquals(99 to TimelineMarks.THIN, TimelineMarks.span(100, 100, zeroLength = true))
        assertEquals(100 to 2, TimelineMarks.span(100, 101, zeroLength = false))
        assertEquals(100 to 40, TimelineMarks.span(100, 140, zeroLength = false))
    }

    @Test
    fun `a thin mark has a wider hit area`() {
        assertTrue(TimelineMarks.hits(97, 100, 100, zeroLength = true))
        assertTrue(TimelineMarks.hits(103, 100, 100, zeroLength = true))
        assertFalse(TimelineMarks.hits(104, 100, 100, zeroLength = true))
        assertTrue(TimelineMarks.hits(101, 100, 100, zeroLength = false))
        assertFalse(TimelineMarks.hits(99, 100, 140, zeroLength = false))
    }

    @Test
    fun `a click at the time of a clip of zero length finds that clip`() {
        val zero = PointV1("F", 5_000, 5_000)
        val clip = PointV1("P", 6_000, 9_000)
        assertEquals(zero, TimelineMarks.clipAt(listOf(zero, clip), 5_000))
        assertEquals(clip, TimelineMarks.clipAt(listOf(zero, clip), 6_000))
        assertNull(TimelineMarks.clipAt(listOf(zero, clip), 5_500))
        assertTrue(TimelineMarks.isZeroLength(zero))
        assertFalse(TimelineMarks.isZeroLength(clip))
    }

    @Test
    fun `a click on a clip of zero length seeks to it, also next to a clip`() {
        SwingUtilities.invokeAndWait {
            val seeks = mutableListOf<Long>()
            // The zero-length clip is 1 px after the end of the clip before it.
            val points = listOf(PointV1("P", 1_000, 4_950), PointV1("F", 5_000, 5_000))
            val timeline = SwingTimelineComponent(
                timeProvider = { 0L },
                durationProvider = { 10_000L },
                pointsProvider = { points },
                onSeekRequested = { seeks += it },
                dimmedProvider = { it == "F" },
            )
            timeline.setSize(GUTTER + 200, timeline.preferredSize.height)
            val x = timeline.xAtForTest(5_000)
            val y = timeline.marksTrackCenterYForTest()
            assertEquals("F", timeline.markAtForTest(x, y)?.id)
            assertEquals("F", timeline.markAtForTest(x + 2, y)?.id)

            timeline.dispatchEvent(MouseEvent(timeline, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false))
            assertEquals(listOf(5_000L), seeks)

            // The paint of a thin, dimmed mark does not fail.
            val image = BufferedImage(timeline.width, timeline.height, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try {
                timeline.paint(g)
            } finally {
                g.dispose()
            }
        }
    }

    private companion object {
        val GUTTER = SwingTimelineComponent.GUTTER
    }
}
