package org.litvin.ui.tabs.points.replay

import org.litvin.points.CommentV1
import org.litvin.points.ReplayV1
import org.litvin.points.Replays
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import java.awt.BasicStroke
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The bar under the video of the replay editor (B-50 T4): the window of 3 s around the point, the point, the other
 * replays as dashed outlines, the selected part in lime with an edge at each end, the still frame as a white mark,
 * and the playhead. A drag of an edge or of the still frame mark moves it. A click or a drag in another place moves
 * the playhead.
 *
 * The comments of the selected replay are small bars in the upper half of the part, from the start to the end of each
 * comment (decision 24). A press on a comment selects it, and a drag moves its start.
 *
 * In pick mode (decision 23), the window is the selected part only. The bar shows a tick for each frame, and a click or
 * a drag only moves the playhead.
 */
internal class ReplayPartBar(
    private val onStart: (Long) -> Unit,
    private val onEnd: (Long) -> Unit,
    private val onSeek: (Long) -> Unit,
    /** Moves the still frame of the selected replay (decision 22). */
    private val onFreeze: (Long) -> Unit = {},
    /** Selects a comment of the selected replay (decision 24). */
    private val onCommentSelect: (Int) -> Unit = {},
    /** Moves the start of a comment of the selected replay to the source time. */
    private val onCommentMove: (Int, Long) -> Unit = { _, _ -> },
) : JComponent() {
    private var windowStart = 0L
    private var windowEnd = 1L
    private var pointStart = 0L
    private var pointEnd = 0L
    private var replays: List<ReplayV1> = emptyList()
    private var selected = -1
    private var playheadMs = 0L

    /** True hides the edges, for example during the preview. */
    var locked = false
        set(value) {
            field = value
            repaint()
        }

    /** True shows the bar of pick mode (decision 23). The window must then be the selected part. */
    var picking = false
        set(value) {
            field = value
            repaint()
        }

    /** The selected comment of the selected replay, or null. */
    var selectedCommentId: Int? = null
        set(value) {
            field = value
            repaint()
        }

    /** The comment that the mouse drags, and the time from its start to the press. */
    private var dragComment: Int? = null
    private var dragGrabMs = 0L

    /** The time of one frame, for the frame ticks of pick mode, or null when it is not known. */
    var frameMs: Double? = null
        set(value) {
            field = value
            repaint()
        }

    private enum class Drag { NONE, START, END, FREEZE, COMMENT, SEEK }

    private var drag = Drag.NONE

    init {
        name = "replay-part-bar"
        isOpaque = false
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!isEnabled || locked) return
                drag = hit(e.x, e.y)
                if (drag == Drag.COMMENT) {
                    val mark = commentAt(e.x, e.y) ?: return
                    dragComment = mark.comment.id
                    dragGrabMs = timeAt(e.x) - mark.comment.startMs
                    onCommentSelect(mark.comment.id)
                    return
                }
                handle(e.x)
            }

            override fun mouseDragged(e: MouseEvent) {
                if (drag != Drag.NONE) handle(e.x)
            }

            override fun mouseReleased(e: MouseEvent) {
                drag = Drag.NONE
                dragComment = null
            }

            override fun mouseMoved(e: MouseEvent) {
                cursor = when (if (locked) Drag.SEEK else hit(e.x, e.y)) {
                    Drag.START, Drag.END, Drag.FREEZE -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
                    Drag.COMMENT -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    else -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                }
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    fun setState(
        windowStartMs: Long,
        windowEndMs: Long,
        pointStartMs: Long,
        pointEndMs: Long,
        replays: List<ReplayV1>,
        selectedIndex: Int,
    ) {
        windowStart = windowStartMs
        windowEnd = windowEndMs.coerceAtLeast(windowStartMs + 1)
        pointStart = pointStartMs
        pointEnd = pointEndMs
        this.replays = replays
        selected = selectedIndex
        repaint()
    }

    fun setPlayhead(ms: Long) {
        if (playheadMs == ms) return
        playheadMs = ms
        repaint()
    }

    private fun handle(x: Int) {
        val ms = timeAt(x)
        when (drag) {
            Drag.START -> onStart(ms)
            Drag.END -> onEnd(ms)
            Drag.FREEZE -> onFreeze(ms)
            Drag.COMMENT -> dragComment?.let { onCommentMove(it, ms - dragGrabMs) }
            Drag.SEEK -> onSeek(ms)
            Drag.NONE -> Unit
        }
    }

    private fun hit(x: Int, y: Int): Drag {
        if (picking) return Drag.SEEK
        val part = replays.getOrNull(selected) ?: return Drag.SEEK
        val startX = xAt(part.startMs.toLong())
        val endX = xAt(part.endMs.toLong())
        // The knob of the still frame mark is in the lower half, so that it does not hide an edge at the same place.
        val freezeX = part.freeze?.let { xAt(it.atMs.toLong()) }
        return when {
            freezeX != null && y >= height / 2 && abs(x - freezeX) <= FREEZE_HIT -> Drag.FREEZE
            abs(x - startX) <= HANDLE_HIT && abs(x - startX) <= abs(x - endX) -> Drag.START
            abs(x - endX) <= HANDLE_HIT -> Drag.END
            commentAt(x, y) != null -> Drag.COMMENT
            else -> Drag.SEEK
        }
    }

    /** A comment bar on the bar: its comment, its lane, and its left and right x. */
    private class CommentMark(val comment: CommentV1, val lane: Int, val x1: Double, val x2: Double)

    /**
     * The comment bars of the selected replay. A comment that overlaps an earlier comment goes into the second lane,
     * so that two comments at the same time both show.
     */
    private fun commentMarks(): List<CommentMark> {
        val part = replays.getOrNull(selected) ?: return emptyList()
        var laneEnd = DoubleArray(COMMENT_LANES) { Double.NEGATIVE_INFINITY }
        return part.comments.sortedWith(compareBy<CommentV1> { it.startMs }.thenBy { it.id }).map { comment ->
            val x1 = xAt(comment.startMs.toLong())
            val x2 = maxOf(xAt(Replays.commentSourceEndMs(part, comment)), x1 + MIN_COMMENT_WIDTH)
            val lane = laneEnd.indices.firstOrNull { laneEnd[it] <= x1 } ?: (COMMENT_LANES - 1)
            laneEnd = laneEnd.copyOf().also { it[lane] = x2 }
            CommentMark(comment, lane, x1, x2)
        }
    }

    private fun commentTop(lane: Int) = 4.0 + COMMENT_TOP + lane * (COMMENT_HEIGHT + COMMENT_GAP)

    /** The comment under (x, y). The selected comment comes first, then the later comments. */
    private fun commentAt(x: Int, y: Int): CommentMark? {
        if (picking) return null
        val marks = commentMarks().filter { mark ->
            val top = commentTop(mark.lane)
            y >= top - COMMENT_HIT_Y && y <= top + COMMENT_HEIGHT + COMMENT_HIT_Y && x >= mark.x1 - COMMENT_HIT_X && x <= mark.x2 + COMMENT_HIT_X
        }
        return marks.firstOrNull { it.comment.id == selectedCommentId } ?: marks.lastOrNull()
    }

    private val trackLeft get() = PAD
    private val trackWidth get() = (width - 2 * PAD).coerceAtLeast(1)

    private fun xAt(ms: Long): Double = trackLeft + (ms - windowStart).toDouble() / (windowEnd - windowStart) * trackWidth

    private fun timeAt(x: Int): Long =
        (windowStart + (x - trackLeft).toDouble() / trackWidth * (windowEnd - windowStart)).roundToLong().coerceIn(windowStart, windowEnd)

    override fun getPreferredSize() = Dimension(400, BAR_HEIGHT)

    override fun getMinimumSize() = Dimension(120, BAR_HEIGHT)

    /** Pick mode: the part fills the track, with a tick for each frame, the still frame, and the playhead. */
    private fun paintPick(g2: Graphics2D, top: Double, h: Double) {
        val part = replays.getOrNull(selected)
        g2.color = Palette.LIME_GLOW
        g2.fill(Rectangle2D.Double(trackLeft.toDouble(), top + 6, trackWidth.toDouble(), h * 0.4))
        frameMs?.takeIf { it > 0.0 }?.let { step ->
            val count = ((windowEnd - windowStart) / step).toInt()
            val spacing = trackWidth.toDouble() / count.coerceAtLeast(1)
            if (spacing >= MIN_TICK_SPACING) {
                g2.color = Palette.LIME_LINE
                for (i in 0..count) {
                    val x = xAt(windowStart + (i * step).roundToLong())
                    g2.draw(java.awt.geom.Line2D.Double(x, top + h * 0.62, x, top + h - 4))
                }
            }
        }
        part?.freeze?.let { freeze ->
            val fx = xAt(freeze.atMs.toLong())
            g2.color = Palette.FG
            g2.stroke = BasicStroke(2f)
            g2.draw(java.awt.geom.Line2D.Double(fx, top + 4, fx, top + h - 4))
        }
        val px = xAt(playheadMs.coerceIn(windowStart, windowEnd))
        g2.color = Palette.PLAYHEAD
        g2.stroke = BasicStroke(2f)
        g2.drawLine(px.toInt(), 1, px.toInt(), height - 1)
    }

    /** The comment bars: each one in its own color, with a lime outline for the selected comment. */
    private fun paintComments(g2: Graphics2D) {
        for (mark in commentMarks()) {
            val top = commentTop(mark.lane)
            val shape = RoundRectangle2D.Double(mark.x1, top, mark.x2 - mark.x1, COMMENT_HEIGHT, COMMENT_HEIGHT, COMMENT_HEIGHT)
            g2.color = runCatching { java.awt.Color.decode(mark.comment.colorHex) }.getOrDefault(Palette.FG)
            g2.fill(shape)
            val chosen = mark.comment.id == selectedCommentId
            g2.color = if (chosen) Palette.LIME else Palette.INSET
            g2.stroke = BasicStroke(if (chosen) 2f else 1f)
            g2.draw(shape)
        }
        g2.stroke = BasicStroke(1f)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val top = 4.0
            val h = height - 8.0
            g2.color = Palette.INSET
            g2.fill(RoundRectangle2D.Double(trackLeft.toDouble(), top, trackWidth.toDouble(), h, 6.0, 6.0))
            g2.color = Palette.LINE_2
            g2.draw(RoundRectangle2D.Double(trackLeft.toDouble(), top, trackWidth.toDouble(), h, 6.0, 6.0))
            if (picking) {
                paintPick(g2, top, h)
                return
            }
            // The point
            g2.color = Palette.withAlpha(Palette.GREEN, 128)
            g2.fill(Rectangle2D.Double(xAt(pointStart), top + h * 0.55, xAt(pointEnd) - xAt(pointStart), h * 0.3))
            // The other replays
            val dashed = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f, 3f), 0f)
            replays.forEachIndexed { index, replay ->
                if (index == selected) return@forEachIndexed
                g2.color = Palette.LIME_LINE
                g2.stroke = dashed
                val x = xAt(replay.startMs.toLong())
                g2.draw(Rectangle2D.Double(x, top + 6, xAt(replay.endMs.toLong()) - x, h * 0.4))
            }
            g2.stroke = BasicStroke(1f)
            // The selected part with its edges
            replays.getOrNull(selected)?.let { part ->
                val x1 = xAt(part.startMs.toLong())
                val x2 = xAt(part.endMs.toLong())
                g2.color = Palette.LIME_GLOW
                g2.fill(Rectangle2D.Double(x1, top + 6, x2 - x1, h * 0.4))
                g2.color = Palette.LIME
                g2.draw(Rectangle2D.Double(x1, top + 6, x2 - x1, h * 0.4))
                if (!locked) {
                    for (x in listOf(x1, x2)) {
                        g2.fill(RoundRectangle2D.Double(x - 3, top + 2, 6.0, h - 4, 3.0, 3.0))
                    }
                }
                paintComments(g2)
                // The still frame: a white line over the part, with a knob in the lower half.
                part.freeze?.let { freeze ->
                    val fx = xAt(freeze.atMs.toLong())
                    g2.color = Palette.FG
                    g2.stroke = BasicStroke(2f)
                    g2.draw(java.awt.geom.Line2D.Double(fx, top + 4, fx, top + h - 4))
                    g2.stroke = BasicStroke(1f)
                    if (!locked) {
                        g2.fill(RoundRectangle2D.Double(fx - 5, top + h * 0.6, 10.0, h * 0.32, 3.0, 3.0))
                        g2.color = Palette.INSET
                        g2.fill(Rectangle2D.Double(fx - 2.5, top + h * 0.66, 1.5, h * 0.2))
                        g2.fill(Rectangle2D.Double(fx + 1.0, top + h * 0.66, 1.5, h * 0.2))
                    }
                }
            }
            // The playhead
            val px = xAt(playheadMs.coerceIn(windowStart, windowEnd))
            g2.color = Palette.PLAYHEAD
            g2.stroke = BasicStroke(2f)
            g2.drawLine(px.toInt(), 1, px.toInt(), height - 1)
        } finally {
            (g2 as Graphics2D).dispose()
        }
    }

    private companion object {
        const val BAR_HEIGHT = 54
        const val PAD = 4
        const val HANDLE_HIT = 8

        /** The still frame knob is small, so its hit area is wider than its drawing. */
        const val FREEZE_HIT = 12

        /** Fewer pixels between frame ticks would show a solid band. */
        const val MIN_TICK_SPACING = 4.0

        // The comment bars, in the upper half of the part.
        const val COMMENT_LANES = 2
        const val COMMENT_TOP = 8.0
        const val COMMENT_HEIGHT = 5.0
        const val COMMENT_GAP = 2.0
        const val MIN_COMMENT_WIDTH = 6.0
        const val COMMENT_HIT_X = 3
        const val COMMENT_HIT_Y = 2
    }
}
