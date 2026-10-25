package org.litvin.ui.tabs.points.replay

import org.litvin.media.OverlayShape
import org.litvin.media.SwingMediaPlayer
import org.litvin.points.ReplayZoom
import org.litvin.points.Replays
import org.litvin.ui.commons.Palette
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import kotlin.math.abs
import kotlin.math.max

/**
 * The zoom frame of a replay on the video of the replay editor (B-50 T4). The frame has the aspect of the video.
 * A drag inside the frame moves it, and a drag of a corner changes its size. The area outside the frame is dark.
 * The player draws the shapes ([SwingMediaPlayer.setEditorOverlay]), and the mouse events come from its component.
 */
internal class ReplayZoomOverlay(
    private val player: SwingMediaPlayer,
    private val onChanged: (ReplayZoom) -> Unit,
) {
    private enum class Target { NONE, MOVE, NW, NE, SW, SE }

    /** The frame, or null for no zoom. */
    var zoom: ReplayZoom? = null
        set(value) {
            field = value
            redraw()
        }

    /** False hides the frame, for example during the preview. */
    var visible = true
        set(value) {
            field = value
            redraw()
        }

    private var drag = Target.NONE
    private var dragStartX = 0.0
    private var dragStartY = 0.0
    private var dragStartZoom: ReplayZoom? = null

    init {
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!visible) return
                drag = hit(e.x.toDouble(), e.y.toDouble())
                dragStartX = e.x.toDouble()
                dragStartY = e.y.toDouble()
                dragStartZoom = zoom
            }

            override fun mouseDragged(e: MouseEvent) {
                if (drag == Target.NONE) return
                dragTo(e.x.toDouble(), e.y.toDouble())
            }

            override fun mouseReleased(e: MouseEvent) {
                drag = Target.NONE
            }

            override fun mouseMoved(e: MouseEvent) {
                player.component.cursor = when (if (visible) hit(e.x.toDouble(), e.y.toDouble()) else Target.NONE) {
                    Target.MOVE -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    Target.NW -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
                    Target.NE -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
                    Target.SW -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR)
                    Target.SE -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
                    Target.NONE -> Cursor.getDefaultCursor()
                }
            }
        }
        player.component.addMouseListener(mouse)
        player.component.addMouseMotionListener(mouse)
        player.onVideoBoundsChanged = { redraw() }
    }

    /** True while the user drags the frame. A click on the video does not play it then. */
    val dragging: Boolean get() = drag != Target.NONE

    /** The frame in component pixels, or null. */
    private fun frameRect(): Rectangle2D.Double? {
        val z = zoom ?: return null
        val area = player.videoBounds() ?: return null
        return Rectangle2D.Double(area.x + z.x * area.width, area.y + z.y * area.height, z.width * area.width, z.width * area.height)
    }

    private fun hit(x: Double, y: Double): Target {
        val r = frameRect() ?: return Target.NONE
        fun near(px: Double, py: Double) = abs(x - px) <= HANDLE_HIT && abs(y - py) <= HANDLE_HIT
        return when {
            near(r.minX, r.minY) -> Target.NW
            near(r.maxX, r.minY) -> Target.NE
            near(r.minX, r.maxY) -> Target.SW
            near(r.maxX, r.maxY) -> Target.SE
            r.contains(x, y) -> Target.MOVE
            else -> Target.NONE
        }
    }

    private fun dragTo(x: Double, y: Double) {
        val start = dragStartZoom ?: return
        val area = player.videoBounds() ?: return
        val dx = (x - dragStartX) / area.width
        val dy = (y - dragStartY) / area.height
        val next = when (drag) {
            Target.MOVE -> ReplayZoom(
                (start.x + dx).coerceIn(0.0, 1.0 - start.width),
                (start.y + dy).coerceIn(0.0, 1.0 - start.width),
                start.width,
            )
            Target.NONE -> return
            else -> {
                // The corner across from the dragged corner stays in its place.
                val left = drag == Target.NW || drag == Target.SW
                val upper = drag == Target.NW || drag == Target.NE
                val anchorX = if (left) start.x + start.width else start.x
                val anchorY = if (upper) start.y + start.width else start.y
                val pointerX = (x - area.x) / area.width
                val pointerY = (y - area.y) / area.height
                val room = minOf(if (left) anchorX else 1.0 - anchorX, if (upper) anchorY else 1.0 - anchorY)
                val width = max(abs(pointerX - anchorX), abs(pointerY - anchorY))
                    .coerceIn(Replays.MIN_ZOOM_WIDTH, room.coerceAtLeast(Replays.MIN_ZOOM_WIDTH))
                ReplayZoom(if (left) anchorX - width else anchorX, if (upper) anchorY - width else anchorY, width)
            }
        }
        zoom = next
        onChanged(next)
    }

    fun redraw() {
        val r = frameRect()
        val area = player.videoBounds()
        if (!visible || r == null || area == null) {
            player.setEditorOverlay(null)
            return
        }
        val dim = Palette.SCRIM
        val shapes = listOf(
            OverlayShape.Rect(area.x, area.y, area.width, r.minY - area.y, fill = dim),
            OverlayShape.Rect(area.x, r.maxY, area.width, area.maxY - r.maxY, fill = dim),
            OverlayShape.Rect(area.x, r.minY, r.minX - area.x, r.height, fill = dim),
            OverlayShape.Rect(r.maxX, r.minY, area.maxX - r.maxX, r.height, fill = dim),
            OverlayShape.Rect(r.x, r.y, r.width, r.height, stroke = Palette.LIME, strokeWidth = 2.0),
        ) + listOf(r.minX to r.minY, r.maxX to r.minY, r.minX to r.maxY, r.maxX to r.maxY).map { (cx, cy) ->
            OverlayShape.Rect(cx - HANDLE / 2, cy - HANDLE / 2, HANDLE, HANDLE, fill = Palette.LIME)
        }
        player.setEditorOverlay(shapes)
    }

    private companion object {
        const val HANDLE = 10.0
        const val HANDLE_HIT = 12.0
    }
}
