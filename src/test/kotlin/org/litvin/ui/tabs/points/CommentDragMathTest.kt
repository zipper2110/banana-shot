package org.litvin.ui.tabs.points

import org.litvin.export.comments.CommentAss
import org.litvin.points.CommentPosition
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import java.awt.geom.Rectangle2D
import java.awt.geom.Point2D
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentDragMathTest {
    private val area = Rectangle2D.Double(40.0, 20.0, 800.0, 450.0)

    @Test
    fun dragMovesThePositionByTheFractionOfTheVideoArea() {
        val moved = CommentDragMath.dragged(CommentPosition(0.2, 0.3), dx = 80.0, dy = -45.0, area = area)

        assertEquals(0.3, moved.x, 1e-9)
        assertEquals(0.2, moved.y, 1e-9)
    }

    @Test
    fun dragNearTheDefaultLinesSnapsToThem() {
        // 5 px right of the center line and 6 px over the default lower line.
        val start = CommentPosition(0.5 + 5.0 / 800.0, 0.82 - 6.0 / 450.0)

        assertEquals(CommentPosition.DEFAULT, CommentDragMath.dragged(start, 0.0, 0.0, area))
        val far = CommentDragMath.dragged(start, 20.0, 0.0, area)
        assertEquals(0.5 + 25.0 / 800.0, far.x, 1e-9)
        assertEquals(CommentPosition.DEFAULT.y, far.y)
    }

    @Test
    fun dragStaysInTheFrame() {
        assertEquals(CommentPosition(0.0, 1.0), CommentDragMath.dragged(CommentPosition(0.1, 0.9), -2_000.0, 2_000.0, area))
    }

    @Test
    fun placementUsesTheOverlayPixelsAndGivesComponentPixels() {
        val comment = CommentV1(1, 0, 1_000, "Great point", "#FFFFFF", CommentStyle.CARD, CommentPosition(0.3, 0.4))
        // At 150% display scaling, the overlay has 1.5 device pixels in each component pixel.
        val placement = CommentDragMath.placement(comment, area, scale = 1.5)!!
        val overlay = CommentAss.placement(comment.text, 0.0, 0.0, 1200.0, 675.0, comment.style, comment.position)!!

        assertEquals(40.0 + overlay.box.x / 1.5, placement.box.x, 1e-9)
        assertEquals(20.0 + overlay.box.y / 1.5, placement.box.y, 1e-9)
        assertEquals(overlay.box.width / 1.5, placement.box.width, 1e-9)
        assertEquals(40.0 + 800.0 * 0.3, placement.box.centerX, 0.01)
        assertEquals(CommentPosition(0.3, 0.4), placement.position)
    }

    @Test
    fun cornerDragChangesTheSizeWithTheDistanceFromTheCenter() {
        val box = Rectangle2D.Double(100.0, 100.0, 200.0, 100.0)
        val press = Point2D.Double(300.0, 200.0)

        assertEquals(150, CommentDragMath.resizedPercent(100, box, press, Point2D.Double(350.0, 225.0)))
        assertEquals(60, CommentDragMath.resizedPercent(120, box, press, Point2D.Double(250.0, 175.0)))
        assertEquals(100, CommentDragMath.resizedPercent(100, box, press, Point2D.Double(303.0, 201.5)), "Near the default size, it snaps")
        assertEquals(50, CommentDragMath.resizedPercent(100, box, press, Point2D.Double(200.0, 150.0)), "The size stays in its range")
        assertEquals(200, CommentDragMath.resizedPercent(150, box, press, Point2D.Double(900.0, 900.0)))
    }

    @Test
    fun resizeKeepsTheCenterOfTheComment() {
        val comment = CommentV1(1, 0, 1_000, "Great point", "#FFFFFF", CommentStyle.CARD, CommentPosition(0.4, 0.6))
        val start = CommentDragMath.placement(comment, area, 1.0)!!.box

        val larger = comment.copy(sizePercent = 160)
        val position = CommentDragMath.centeredPosition(larger, start, area, 1.0)
        val box = CommentDragMath.placement(larger.copy(position = position), area, 1.0)!!.box

        assertEquals(start.centerX, box.centerX, 0.5)
        assertEquals(start.centerY, box.centerY, 0.5)
        assertTrue(box.height > start.height * 1.5, "The box is larger: $box")
    }

    @Test
    fun rotationHandleDragTurnsByTheChangeOfTheMouseAngle() {
        val center = Point2D.Double(0.0, 0.0)
        val press = Point2D.Double(0.0, -50.0)
        fun at(degrees: Double) = Point2D.Double(50 * sin(Math.toRadians(degrees)), -50 * cos(Math.toRadians(degrees)))

        assertEquals(90, CommentDragMath.rotatedDeg(0, center, press, Point2D.Double(50.0, 0.0), steps = false), "Right is clockwise")
        assertEquals(30, CommentDragMath.rotatedDeg(10, center, press, at(20.0), steps = false))
        assertEquals(-25, CommentDragMath.rotatedDeg(0, center, press, at(-25.0), steps = false))
        assertEquals(-170, CommentDragMath.rotatedDeg(170, center, press, at(20.0), steps = false), "The angle stays in -180..180")
    }

    @Test
    fun rotationSnapsNearQuarterTurns_andShiftGivesSteps() {
        val center = Point2D.Double(0.0, 0.0)
        val press = Point2D.Double(0.0, -50.0)
        fun at(degrees: Double) = Point2D.Double(50 * sin(Math.toRadians(degrees)), -50 * cos(Math.toRadians(degrees)))

        assertEquals(0, CommentDragMath.rotatedDeg(0, center, press, at(2.5), steps = false))
        assertEquals(90, CommentDragMath.rotatedDeg(0, center, press, at(88.0), steps = false))
        assertEquals(5, CommentDragMath.rotatedDeg(0, center, press, at(5.0), steps = false))
        assertEquals(15, CommentDragMath.rotatedDeg(0, center, press, at(21.0), steps = true))
        assertEquals(30, CommentDragMath.rotatedDeg(0, center, press, at(23.0), steps = true))
    }

    @Test
    fun rotateTurnsAPointClockwiseOnScreen() {
        val turned = CommentDragMath.rotate(Point2D.Double(110.0, 100.0), Point2D.Double(100.0, 100.0), 90.0)

        assertEquals(100.0, turned.x, 1e-9)
        assertEquals(110.0, turned.y, 1e-9, "The y axis points down, so a point on the right turns to below")
    }
}
