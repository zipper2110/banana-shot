package org.litvin.ui.tabs.points.replay

import org.litvin.points.ReplayV1
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
 * replays as dashed outlines, the selected part in lime with an edge at each end, and the playhead. A drag of an edge
 * moves it. A click or a drag in another place moves the playhead.
 */
internal class ReplayPartBar(
    private val onStart: (Long) -> Unit,
    private val onEnd: (Long) -> Unit,
    private val onSeek: (Long) -> Unit,
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

    private enum class Drag { NONE, START, END, SEEK }

    private var drag = Drag.NONE

    init {
        name = "replay-part-bar"
        isOpaque = false
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!isEnabled || locked) return
                drag = hit(e.x)
                handle(e.x)
            }

            override fun mouseDragged(e: MouseEvent) {
                if (drag != Drag.NONE) handle(e.x)
            }

            override fun mouseReleased(e: MouseEvent) {
                drag = Drag.NONE
            }

            override fun mouseMoved(e: MouseEvent) {
                cursor = when (if (locked) Drag.SEEK else hit(e.x)) {
                    Drag.START, Drag.END -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
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
            Drag.SEEK -> onSeek(ms)
            Drag.NONE -> Unit
        }
    }

    private fun hit(x: Int): Drag {
        val part = replays.getOrNull(selected) ?: return Drag.SEEK
        val startX = xAt(part.startMs.toLong())
        val endX = xAt(part.endMs.toLong())
        return when {
            abs(x - startX) <= HANDLE_HIT && abs(x - startX) <= abs(x - endX) -> Drag.START
            abs(x - endX) <= HANDLE_HIT -> Drag.END
            else -> Drag.SEEK
        }
    }

    private val trackLeft get() = PAD
    private val trackWidth get() = (width - 2 * PAD).coerceAtLeast(1)

    private fun xAt(ms: Long): Double = trackLeft + (ms - windowStart).toDouble() / (windowEnd - windowStart) * trackWidth

    private fun timeAt(x: Int): Long =
        (windowStart + (x - trackLeft).toDouble() / trackWidth * (windowEnd - windowStart)).roundToLong().coerceIn(windowStart, windowEnd)

    override fun getPreferredSize() = Dimension(400, BAR_HEIGHT)

    override fun getMinimumSize() = Dimension(120, BAR_HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val top = 4.0
            val h = height - 8.0
            g2.color = Palette.INSET
            g2.fill(RoundRectangle2D.Double(trackLeft.toDouble(), top, trackWidth.toDouble(), h, 6.0, 6.0))
            g2.color = Palette.LINE_2
            g2.draw(RoundRectangle2D.Double(trackLeft.toDouble(), top, trackWidth.toDouble(), h, 6.0, 6.0))
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
    }
}
