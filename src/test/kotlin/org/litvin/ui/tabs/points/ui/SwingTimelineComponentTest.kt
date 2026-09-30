package org.litvin.ui.tabs.points.ui

import org.litvin.points.CommentV1
import org.litvin.points.PointV1
import org.litvin.ui.commons.Palette
import java.awt.Color
import java.awt.Cursor
import java.awt.image.BufferedImage
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.math.abs
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
            timeline.setSize(WIDTH, timeline.preferredSize.height)
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
            timeline.setSize(WIDTH, timeline.preferredSize.height)
            val layouts = timeline.commentMarkerLayoutsForTest()

            assertEquals(2, timeline.commentLaneCountForTest())
            assertEquals(2, layouts.size)
            assertFalse(layouts[0].boxBounds.intersects(layouts[1].boxBounds))
            assertTrue(layouts.all { it.boxBounds.x == it.lineX + 3 })
            assertTrue(layouts.all { it.lineTop == timeline.commentsTrackTopForTest() })
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
            timeline.setSize(WIDTH, timeline.preferredSize.height)
            val box = timeline.commentMarkerLayoutsForTest().single().boxBounds

            assertEquals(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                timeline.cursorAtForTest(box.centerX.toInt(), box.centerY.toInt()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                timeline.cursorAtForTest(GUTTER + 30, timeline.marksTrackCenterYForTest()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
                timeline.cursorAtForTest(GUTTER + 150, timeline.marksTrackCenterYForTest()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
                timeline.cursorAtForTest(GUTTER + 150, timeline.videoTrackTopForTest() + 2),
            )
            assertEquals(
                Cursor.getDefaultCursor(),
                timeline.cursorAtForTest(GUTTER / 2, timeline.marksTrackCenterYForTest()),
                "the label column is not a part of the tracks",
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
            timeline.setSize(WIDTH, timeline.preferredSize.height)
            val y = timeline.videoTrackTopForTest() + 2

            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_PRESSED, GUTTER + 20, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_DRAGGED, GUTTER + 100, y))
            assertEquals(5_000L, timeline.scrubTimeMs)
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_DRAGGED, GUTTER + 150, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_RELEASED, GUTTER + 150, y))

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
            timeline.setSize(WIDTH, timeline.preferredSize.height)
            val y = timeline.marksTrackCenterYForTest()

            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_PRESSED, GUTTER + 30, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_DRAGGED, GUTTER + 31, y))
            assertEquals(emptyList(), scrubs)
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_DRAGGED, GUTTER + 60, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_RELEASED, GUTTER + 60, y))

            assertEquals(listOf(3_000L), scrubs)
            assertEquals(listOf(1_000L, 3_000L), seeks)
        }
    }

    @Test
    fun aPressInTheLabelColumnDoesNotSeek() {
        SwingUtilities.invokeAndWait {
            val seeks = mutableListOf<Long>()
            val timeline = timelineWithComments(comments = emptyList(), onSeek = { seeks += it })
            timeline.setSize(WIDTH, timeline.preferredSize.height)
            val y = timeline.videoTrackTopForTest() + 2

            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_PRESSED, 10, y))
            timeline.dispatchEvent(mouse(timeline, MouseEvent.MOUSE_RELEASED, 10, y))

            assertEquals(emptyList(), seeks)
        }
    }

    @Test
    fun paintsTheSelectedFavoriteAndThePendingPointWithoutErrors() {
        SwingUtilities.invokeAndWait {
            val timeline = SwingTimelineComponent(
                timeProvider = { 6_000L },
                durationProvider = { 10_000L },
                pointsProvider = { listOf(PointV1("p1", 1_000, 2_000, favorite = true), PointV1("p2", 3_000, 4_000)) },
                onSeekRequested = {},
                commentsProvider = { listOf(CommentV1(1, 5_000, 1_000, "In", "#4FC3F7")) },
                selectedPointIdProvider = { "p1" },
                pendingStartProvider = { 5_000L },
            )
            timeline.setSize(WIDTH, timeline.preferredSize.height)
            val image = BufferedImage(timeline.width, timeline.height, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            timeline.paint(g)
            g.dispose()

            // The favorite is yellow, and the pending range (5 s to 6 s) is lime.
            val favorite = Color(image.getRGB(GUTTER + 30, timeline.marksTrackCenterYForTest()))
            assertTrue(favorite.red > 200 && favorite.green > 180 && favorite.blue < 120, "favorite color was $favorite")
            val center = timeline.marksTrackCenterYForTest()
            val lime = Palette.LIME
            val limePixels = (GUTTER + 100..GUTTER + 120).sumOf { x ->
                (center - 8..center + 8).count { y ->
                    Color(image.getRGB(x, y)).let {
                        abs(it.red - lime.red) + abs(it.green - lime.green) + abs(it.blue - lime.blue) <= 30
                    }
                }
            }
            assertTrue(limePixels > 10, "the pending range should have a lime border, found $limePixels lime pixels")
        }
    }

    private fun mouse(timeline: SwingTimelineComponent, id: Int, x: Int, y: Int): MouseEvent {
        val modifiers = if (id == MouseEvent.MOUSE_RELEASED) 0 else MouseEvent.BUTTON1_DOWN_MASK
        return MouseEvent(timeline, id, 0, modifiers, x, y, 1, false, MouseEvent.BUTTON1)
    }

    private companion object {
        const val GUTTER = SwingTimelineComponent.GUTTER
        const val WIDTH = GUTTER + 200
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
