package org.litvin.ui.tabs.points

import org.litvin.export.comments.CommentAss
import org.litvin.export.comments.CommentPlacement
import org.litvin.media.OverlayShape
import org.litvin.media.SwingMediaPlayer
import org.litvin.points.CommentPosition
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.ui.commons.Palette
import java.awt.Cursor
import java.awt.EventQueue
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lets the user move and resize a comment on the video of the Points tab (B-51).
 *
 * It reads the mouse on [SwingMediaPlayer.component]. It draws the outline and the corner handles of the comment
 * under the mouse with [SwingMediaPlayer.setEditorOverlay], so mpv draws them in the video window. No other window
 * is necessary. A drag inside the outline moves the comment. A drag of a corner handle changes the size of the
 * comment, and the center of the comment stays at its place. A drag of the round handle above the outline turns the
 * comment around its center. The outline and the handles turn with the comment.
 * During a drag, the comment changes on the video and stays on screen, also after the end of its duration.
 * The project gets the change when the user releases the mouse button.
 * A click on a comment without a drag selects the comment in the list, and does not play or pause the video.
 */
internal class CommentDragController(
    private val player: SwingMediaPlayer,
    private val enabled: () -> Boolean,
    private val shownComments: () -> List<CommentV1>,
    private val onPreview: () -> Unit,
    private val onChanged: (id: Int, position: CommentPosition, sizePercent: Int, rotationDeg: Int) -> Unit,
    private val onClicked: (id: Int) -> Unit,
) {
    /** The part of a comment under the mouse: the body moves the comment, a corner resizes it, the handle turns it. */
    enum class Target(val cursor: Int) {
        BODY(Cursor.MOVE_CURSOR),
        NW(Cursor.NW_RESIZE_CURSOR),
        NE(Cursor.NE_RESIZE_CURSOR),
        SW(Cursor.SW_RESIZE_CURSOR),
        SE(Cursor.SE_RESIZE_CURSOR),
        ROTATE(Cursor.HAND_CURSOR),
    }

    private class Hit(val comment: CommentV1, val placement: CommentPlacement, val target: Target)

    private class Drag(val comment: CommentV1, val target: Target, val start: Point2D.Double, val startBox: Rectangle2D.Double,
        val startPosition: CommentPosition) {
        var position: CommentPosition = startPosition
        var sizePercent: Int = comment.sizePercent
        var rotationDeg: Int = comment.rotationDeg
        var moved = false
        var snapX = false
        var snapY = false

        fun current(): CommentV1 = comment.copy(position = position, sizePercent = sizePercent, rotationDeg = rotationDeg)
    }

    private val target = player.component
    private var drag: Drag? = null
    private var hoverId: Int? = null
    private var hoverTarget: Target? = null
    private var mouse: Point2D.Double? = null

    /** True when the last mouse press was on a comment. Then the click does not play or pause the video. */
    var pressedOnComment: Boolean = false
        private set

    private val adapter = object : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) {
            mouse = Point2D.Double(e.x.toDouble(), e.y.toDouble())
            if (drag == null) updateHover()
        }

        override fun mouseExited(e: MouseEvent) {
            mouse = null
            if (drag == null) updateHover()
        }

        override fun mousePressed(e: MouseEvent) {
            pressedOnComment = false
            if (!SwingUtilities.isLeftMouseButton(e) || !enabled()) return
            val area = player.videoBounds() ?: return
            val point = Point2D.Double(e.x.toDouble(), e.y.toDouble())
            val hit = hit(point, area) ?: return
            pressedOnComment = true
            drag = Drag(hit.comment, hit.target, point, hit.placement.box, hit.placement.position)
            redraw()
        }

        override fun mouseDragged(e: MouseEvent) {
            val current = drag ?: return
            val area = player.videoBounds() ?: return
            mouse = Point2D.Double(e.x.toDouble(), e.y.toDouble())
            val dx = e.x - current.start.x
            val dy = e.y - current.start.y
            if (!current.moved && hypot(dx, dy) < DRAG_THRESHOLD_PX) return
            current.moved = true
            val scale = player.overlayScale()
            if (current.target == Target.BODY) {
                val wanted = CommentDragMath.dragged(current.startPosition, dx, dy, area)
                current.snapX = wanted.x == CommentPosition.DEFAULT.x
                current.snapY = wanted.y == CommentPosition.DEFAULT.y
                // Keep the position that the video shows, after the move into the frame.
                current.position = CommentDragMath.placement(current.comment.copy(position = wanted), area, scale)
                    ?.position ?: wanted
            } else if (current.target == Target.ROTATE) {
                val center = Point2D.Double(current.startBox.centerX, current.startBox.centerY)
                val rotation = CommentDragMath.rotatedDeg(current.comment.rotationDeg, center, current.start, mouse!!, e.isShiftDown)
                current.rotationDeg = rotation
                // A turned comment can need more space, so it can move inside the frame.
                current.position = CommentDragMath.placement(
                    current.comment.copy(position = current.startPosition, rotationDeg = rotation), area, scale,
                )?.position ?: current.startPosition
            } else {
                val size = CommentDragMath.resizedPercent(current.comment.sizePercent, current.startBox, current.start, mouse!!)
                current.sizePercent = size
                current.position = CommentDragMath.centeredPosition(
                    current.comment.copy(position = current.startPosition, sizePercent = size), current.startBox, area, scale,
                )
            }
            onPreview()
        }

        override fun mouseReleased(e: MouseEvent) {
            val current = drag ?: return
            drag = null
            mouse = Point2D.Double(e.x.toDouble(), e.y.toDouble())
            if (current.moved) {
                onChanged(current.comment.id, current.position, current.sizePercent, current.rotationDeg)
                // The project change redraws the comments later in the event queue. After that, remove a comment
                // that stayed on screen only for the drag.
                EventQueue.invokeLater { onPreview() }
            } else {
                onClicked(current.comment.id)
                onPreview()
            }
        }
    }

    init {
        target.addMouseListener(adapter)
        target.addMouseMotionListener(adapter)
        player.onVideoBoundsChanged = { redraw() }
    }

    /**
     * The comments to draw over the video. During a drag, the dragged comment has its new position and size, and it
     * is on screen also when the playhead is after the end of its duration.
     */
    fun displayed(comments: List<CommentV1>): List<CommentV1> {
        val current = drag ?: return comments
        if (!current.moved) return comments
        val changed = current.current()
        return if (comments.any { it.id == changed.id }) {
            comments.map { if (it.id == changed.id) changed else it }
        } else {
            comments + changed
        }
    }

    /** Draws the outline again, for example after the comments on screen changed. */
    fun refresh() {
        if (drag == null) updateHover(force = true) else redraw()
    }

    fun dispose() {
        target.removeMouseListener(adapter)
        target.removeMouseMotionListener(adapter)
        player.onVideoBoundsChanged = null
        player.setEditorOverlay(null)
    }

    private fun updateHover(force: Boolean = false) {
        val area = player.videoBounds()
        val point = mouse
        val hit = if (area != null && point != null && enabled()) hit(point, area) else null
        val nextId = hit?.comment?.id
        val nextTarget = hit?.target
        if (nextId == hoverId && nextTarget == hoverTarget && !force) return
        hoverId = nextId
        hoverTarget = nextTarget
        target.cursor = nextTarget?.let { Cursor.getPredefinedCursor(it.cursor) } ?: Cursor.getDefaultCursor()
        redraw()
    }

    /**
     * The comment part under [point], the top comment first. The comments draw in list order, so the last is on top.
     * The rotation handle and the corner handles of a comment come before its body.
     * The test turns the mouse point back by the angle of the comment, so it can use the outline without a rotation.
     */
    private fun hit(point: Point2D.Double, area: Rectangle2D.Double): Hit? {
        val scale = player.overlayScale()
        for (comment in displayed(shownComments()).asReversed()) {
            val placement = CommentDragMath.placement(comment, area, scale) ?: continue
            val outline = outline(placement.box)
            val pivot = Point2D.Double(placement.box.centerX, placement.box.centerY)
            val local = CommentDragMath.rotate(point, pivot, -comment.rotationDeg.toDouble())
            if (local.distance(rotationHandle(outline, comment, area).handle) <= HANDLE_HIT_PX / 2) {
                return Hit(comment, placement, Target.ROTATE)
            }
            for ((corner, center) in corners(outline)) {
                if (abs(local.x - center.x) <= HANDLE_HIT_PX / 2 && abs(local.y - center.y) <= HANDLE_HIT_PX / 2) {
                    return Hit(comment, placement, corner)
                }
            }
            if (outline.contains(local)) return Hit(comment, placement, Target.BODY)
        }
        return null
    }

    private fun redraw() {
        val area = player.videoBounds()
        val current = drag
        val id = current?.comment?.id ?: hoverId
        val comment = id?.let { wanted -> displayed(shownComments()).firstOrNull { it.id == wanted } }
            ?: current?.current()
        val placement = if (area != null && comment != null) CommentDragMath.placement(comment, area, player.overlayScale()) else null
        if (area == null || comment == null || placement == null) {
            player.setEditorOverlay(null)
            return
        }
        val shapes = mutableListOf<OverlayShape>()
        if (current?.moved == true && current.target == Target.BODY) {
            if (current.snapX) {
                val x = area.x + area.width * CommentPosition.DEFAULT.x
                shapes += OverlayShape.Line(x, area.y, x, area.y + area.height, Palette.LIME_LINE, 1.0)
            }
            if (current.snapY) {
                val y = area.y + area.height * CommentPosition.DEFAULT.y
                shapes += OverlayShape.Line(area.x, y, area.x + area.width, y, Palette.LIME_LINE, 1.0)
            }
        }
        val active = current != null
        val stroke = if (active) Palette.LIME else Palette.FG_STRONG
        val outline = outline(placement.box)
        val degrees = comment.rotationDeg.toDouble()
        val pivot = Point2D.Double(placement.box.centerX, placement.box.centerY)
        fun turned(point: Point2D.Double) = CommentDragMath.rotate(point, pivot, degrees)
        shapes += OverlayShape.Rect(
            outline.x, outline.y, outline.width, outline.height,
            stroke = stroke, strokeWidth = if (active) 2.0 else 1.5, radius = OUTLINE_RADIUS_PX, rotationDeg = degrees,
        )
        for ((_, local) in corners(outline)) {
            val center = turned(local)
            shapes += OverlayShape.Rect(
                center.x - HANDLE_PX / 2, center.y - HANDLE_PX / 2, HANDLE_PX, HANDLE_PX,
                fill = Palette.BG, stroke = stroke, strokeWidth = 1.5, rotationDeg = degrees,
            )
        }
        val rotation = rotationHandle(outline, comment, area)
        val edge = turned(rotation.edge)
        val handle = turned(rotation.handle)
        shapes += OverlayShape.Line(edge.x, edge.y, handle.x, handle.y, stroke, 1.4)
        shapes += OverlayShape.Circle(handle.x, handle.y, HANDLE_PX / 2, fill = Palette.BG, stroke = stroke, strokeWidth = 1.5)
        player.setEditorOverlay(shapes)
    }

    private fun outline(box: Rectangle2D.Double) = Rectangle2D.Double(
        box.x - OUTLINE_MARGIN_PX, box.y - OUTLINE_MARGIN_PX, box.width + 2 * OUTLINE_MARGIN_PX, box.height + 2 * OUTLINE_MARGIN_PX,
    )

    private fun corners(outline: Rectangle2D.Double): List<Pair<Target, Point2D.Double>> = listOf(
        Target.NW to Point2D.Double(outline.minX, outline.minY),
        Target.NE to Point2D.Double(outline.maxX, outline.minY),
        Target.SW to Point2D.Double(outline.minX, outline.maxY),
        Target.SE to Point2D.Double(outline.maxX, outline.maxY),
    )

    /** The rotation handle and the point on the outline where its line starts, both before the rotation. */
    private class RotationHandle(val edge: Point2D.Double, val handle: Point2D.Double)

    /**
     * The rotation handle is above the top edge of the outline. When the video area has no space for it there, the
     * handle is below the lower edge.
     */
    private fun rotationHandle(outline: Rectangle2D.Double, comment: CommentV1, area: Rectangle2D.Double): RotationHandle {
        val pivot = Point2D.Double(outline.centerX, outline.centerY)
        val above = RotationHandle(
            Point2D.Double(outline.centerX, outline.minY),
            Point2D.Double(outline.centerX, outline.minY - ROTATION_HANDLE_OFFSET_PX),
        )
        val shown = CommentDragMath.rotate(above.handle, pivot, comment.rotationDeg.toDouble())
        val margin = HANDLE_PX / 2
        val fits = shown.x - margin >= area.minX && shown.x + margin <= area.maxX &&
            shown.y - margin >= area.minY && shown.y + margin <= area.maxY
        if (fits) return above
        return RotationHandle(
            Point2D.Double(outline.centerX, outline.maxY),
            Point2D.Double(outline.centerX, outline.maxY + ROTATION_HANDLE_OFFSET_PX),
        )
    }

    private companion object {
        /** A press and a release closer than this are a click, not a drag. */
        const val DRAG_THRESHOLD_PX = 3.0
        const val OUTLINE_MARGIN_PX = 4.0
        const val OUTLINE_RADIUS_PX = 6.0
        const val HANDLE_PX = 8.0
        const val HANDLE_HIT_PX = 14.0

        /** The distance from the outline to the rotation handle, the same as the crop editor. */
        const val ROTATION_HANDLE_OFFSET_PX = 24.0
    }
}

/** The geometry of a comment drag. All values are component pixels, except the positions and the sizes. */
internal object CommentDragMath {
    /** A comment snaps to the default center line and to the default lower line in this distance. */
    const val SNAP_PX = 8.0

    /** A resize snaps to the default size in this distance, in percent. */
    const val SIZE_SNAP_PERCENT = 4

    /** A rotation snaps to 0°, 90°, -90°, and 180° in this distance, in degrees. */
    const val ROTATION_SNAP_DEG = 3.0

    /** With Shift, a rotation changes in steps of this size, in degrees. */
    const val ROTATION_STEP_DEG = 15.0

    /**
     * Where the video shows [comment] in the video [area] of the component. [scale] is
     * [SwingMediaPlayer.overlayScale]: the overlay calculates the comment in overlay pixels, so this does too.
     */
    fun placement(comment: CommentV1, area: Rectangle2D.Double, scale: Double): CommentPlacement? {
        val placed = CommentAss.placement(
            comment.text, 0.0, 0.0, area.width * scale, area.height * scale, comment.style, comment.position,
            comment.sizePercent, comment.rotationDeg,
        ) ?: return null
        val box = placed.box
        val component = Rectangle2D.Double(area.x + box.x / scale, area.y + box.y / scale, box.width / scale, box.height / scale)
        return CommentPlacement(component, placed.position)
    }

    /**
     * The position after the mouse moved ([dx], [dy]) from the press. Near the default center line or the default
     * lower line, the position snaps to that line. The result is in the frame (0 to 1).
     */
    fun dragged(start: CommentPosition, dx: Double, dy: Double, area: Rectangle2D.Double): CommentPosition {
        var x = start.x + dx / area.width
        var y = start.y + dy / area.height
        if (abs(x - CommentPosition.DEFAULT.x) * area.width <= SNAP_PX) x = CommentPosition.DEFAULT.x
        if (abs(y - CommentPosition.DEFAULT.y) * area.height <= SNAP_PX) y = CommentPosition.DEFAULT.y
        return CommentPosition(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0))
    }

    /**
     * The size after a corner drag from [press] to [mouse]. The size changes with the distance from the center of
     * the box at the press ([startBox]). Near the default size, the size snaps to it.
     */
    fun resizedPercent(startPercent: Int, startBox: Rectangle2D.Double, press: Point2D.Double, mouse: Point2D.Double): Int {
        val startDistance = max(press.distance(startBox.centerX, startBox.centerY), 1.0)
        val distance = mouse.distance(startBox.centerX, startBox.centerY)
        val percent = (startPercent * distance / startDistance).roundToInt()
            .coerceIn(CommentV1.MIN_SIZE_PERCENT, CommentV1.MAX_SIZE_PERCENT)
        return if (abs(percent - CommentV1.DEFAULT_SIZE_PERCENT) <= SIZE_SNAP_PERCENT) CommentV1.DEFAULT_SIZE_PERCENT else percent
    }

    /**
     * The position that puts the center of the box of [comment] (with its new size) at the center of [startBox].
     * The result is the position after the move into the frame.
     */
    fun centeredPosition(comment: CommentV1, startBox: Rectangle2D.Double, area: Rectangle2D.Double, scale: Double): CommentPosition {
        val start = comment.position ?: CommentPosition.DEFAULT
        val box = placement(comment, area, scale)?.box ?: return start
        val wanted = CommentPosition(
            (start.x + (startBox.centerX - box.centerX) / area.width).coerceIn(0.0, 1.0),
            (start.y + (startBox.centerY - box.centerY) / area.height).coerceIn(0.0, 1.0),
        )
        return placement(comment.copy(position = wanted), area, scale)?.position ?: wanted
    }

    /**
     * The angle after a drag of the rotation handle from [press] to [mouse]. The comment turns by the change of the
     * mouse angle around [center]. Near 0°, 90°, -90°, and 180°, the angle snaps to that value. With [steps], the
     * angle is a multiple of [ROTATION_STEP_DEG]. The result is in -180..180.
     */
    fun rotatedDeg(startDeg: Int, center: Point2D.Double, press: Point2D.Double, mouse: Point2D.Double, steps: Boolean): Int {
        val pressAngle = Math.toDegrees(atan2(press.y - center.y, press.x - center.x))
        val mouseAngle = Math.toDegrees(atan2(mouse.y - center.y, mouse.x - center.x))
        // The y axis points down, so a larger angle is clockwise, the same as a positive rotation.
        var angle = startDeg + mouseAngle - pressAngle
        angle -= 360.0 * Math.floor((angle + 180.0) / 360.0)
        if (steps) {
            angle = (angle / ROTATION_STEP_DEG).roundToInt() * ROTATION_STEP_DEG
        } else {
            val nearest = (angle / 90.0).roundToInt() * 90.0
            if (abs(angle - nearest) <= ROTATION_SNAP_DEG) angle = nearest
        }
        return EdlIO.normalizeRotationDeg(angle.roundToInt())
    }

    /** [point] turned by [degrees] around [center]. The y axis points down, so a positive angle turns it clockwise. */
    fun rotate(point: Point2D.Double, center: Point2D.Double, degrees: Double): Point2D.Double {
        if (degrees == 0.0) return Point2D.Double(point.x, point.y)
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians)
        val sine = sin(radians)
        val dx = point.x - center.x
        val dy = point.y - center.y
        return Point2D.Double(center.x + dx * cosine - dy * sine, center.y + dx * sine + dy * cosine)
    }
}
