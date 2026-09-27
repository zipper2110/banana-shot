package org.litvin.ui.tabs.points.ui

import org.litvin.points.CommentV1
import org.litvin.points.PointV1
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwingTimelineComponentTest {
    @Test
    fun clickingRightAttachedCommentBoxSelectsCommentAndSeeksToItsStart() {
        SwingUtilities.invokeAndWait {
            val selected = mutableListOf<Int>()
            val seeks = mutableListOf<Long>()
            val timeline = timelineWithComments(
                comments = listOf(CommentV1(12, 5_000, 1_000, "In", "#FFFFFF")),
                onCommentSelected = { selected += it },
                onSeek = { seeks += it },
            )
            timeline.setSize(200, timeline.preferredSize.height)
            val box = timeline.commentMarkerLayoutsForTest().single().boxBounds

            timeline.dispatchEvent(
                MouseEvent(timeline, MouseEvent.MOUSE_PRESSED, 0, 0, box.centerX.toInt(), box.centerY.toInt(), 1, false),
            )

            assertEquals(listOf(12), selected)
            assertEquals(listOf(5_000L), seeks)
        }
    }

    @Test
    fun collidingCommentLabelsUseLanesAndMarkerBarsCoverAllTracks() {
        SwingUtilities.invokeAndWait {
            val timeline = timelineWithComments(
                comments = listOf(
                    CommentV1(1, 5_000, 1_000, "In", "#FFFFFF"),
                    CommentV1(2, 5_100, 1_000, "Out", "#FFFFFF"),
                ),
            )
            timeline.setSize(200, timeline.preferredSize.height)
            val layouts = timeline.commentMarkerLayoutsForTest()

            assertEquals(2, timeline.commentLaneCountForTest())
            assertEquals(2, layouts.size)
            assertFalse(layouts[0].boxBounds.intersects(layouts[1].boxBounds))
            assertTrue(layouts.all { it.boxBounds.x == it.lineX + 3 })
            assertTrue(layouts.all { it.lineTop == timeline.videoTrackTopForTest() })
            assertTrue(layouts.all { it.lineBottom == timeline.commentTrackBottomForTest() })
        }
    }

    @Test
    fun cursorSignalsHandOverCommentsAndMarksAndCrosshairOverTheScrubbableRest() {
        SwingUtilities.invokeAndWait {
            val timeline = timelineWithComments(
                comments = listOf(CommentV1(12, 5_000, 1_000, "In", "#FFFFFF")),
                points = listOf(PointV1("p1", 1_000, 2_000)),
            )
            timeline.setSize(200, timeline.preferredSize.height)
            val box = timeline.commentMarkerLayoutsForTest().single().boxBounds

            assertEquals(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                timeline.cursorAtForTest(box.centerX.toInt(), box.centerY.toInt()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                timeline.cursorAtForTest(30, timeline.marksTrackCenterYForTest()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
                timeline.cursorAtForTest(150, timeline.marksTrackCenterYForTest()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
                timeline.cursorAtForTest(150, timeline.videoTrackTopForTest() + 2),
            )
        }
    }

    @Test
    fun draggingOnTheScrubbableAreaScrubsFastAndSeeksExactlyOnRelease() {
        SwingUtilities.invokeAndWait {
            val seeks = mutableListOf<Long>()
            val scrubs = mutableListOf<Long>()
            val timeline = timelineWithComments(
                comments = emptyList(),
                onSeek = { seeks += it },
                onScrub = { scrubs += it },
            )
            timeline.setSize(200, timeline.preferredSize.height)
            val y = timeline.videoTrackTopForTest() + 2

            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_PRESSED, 20, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_DRAGGED, 100, y))
            assertEquals(5_000L, timeline.scrubTimeMs)
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_DRAGGED, 150, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_RELEASED, 150, y))

            assertEquals(listOf(1_000L, 5_000L, 7_500L), scrubs)
            assertEquals(listOf(7_500L), seeks)
            assertEquals(null, timeline.scrubTimeMs)
        }
    }

    @Test
    fun draggingFromAMarkSeeksToTheMarkThenScrubsAfterASmallMovement() {
        SwingUtilities.invokeAndWait {
            val seeks = mutableListOf<Long>()
            val scrubs = mutableListOf<Long>()
            val timeline = timelineWithComments(
                comments = emptyList(),
                points = listOf(PointV1("p1", 1_000, 2_000)),
                onSeek = { seeks += it },
                onScrub = { scrubs += it },
            )
            timeline.setSize(200, timeline.preferredSize.height)
            val y = timeline.marksTrackCenterYForTest()

            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_PRESSED, 30, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_DRAGGED, 31, y))
            assertEquals(emptyList(), scrubs)
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_DRAGGED, 60, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_RELEASED, 60, y))

            assertEquals(listOf(3_000L), scrubs)
            assertEquals(listOf(1_000L, 3_000L), seeks)
        }
    }

    private fun mouse(timeline: SwingTimelineComponent, id: Int, x: Int, y: Int): MouseEvent {
        val modifiers = if (id == MouseEvent.MOUSE_RELEASED) 0 else MouseEvent.BUTTON1_DOWN_MASK
        return MouseEvent(timeline, id, 0, modifiers, x, y, 1, false, MouseEvent.BUTTON1)
    }

    private fun timelineWithComments(
        comments: List<CommentV1>,
        points: List<PointV1> = emptyList(),
        onCommentSelected: (Int) -> Unit = {},
        onSeek: (Long) -> Unit = {},
        onScrub: (Long) -> Unit = onSeek,
    ) = SwingTimelineComponent(
        timeProvider = { 0L },
        durationProvider = { 10_000L },
        pointsProvider = { points },
        onSeekRequested = onSeek,
        commentsProvider = { comments },
        onCommentSelected = onCommentSelected,
        onScrubRequested = onScrub,
    )
}
