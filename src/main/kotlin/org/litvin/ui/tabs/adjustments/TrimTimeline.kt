package org.litvin.ui.tabs.adjustments

import org.litvin.adjustments.TrimRange
import org.litvin.i18n.tr
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import java.awt.BasicStroke
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The trim timeline under the video on the Trim sub-tab (B-62). It shows the whole source: the kept part between two
 * lime handles, a hatched area outside the trim, and the points as small bars (red when the trim cuts them off).
 * A lime pill above each handle shows its time. The playhead looks as on the Points tab: a thin line with a small
 * triangle on top.
 *
 * A click on the track seeks the video. A drag of a handle moves that end of the trim and seeks the video with it,
 * so the user sees the frame. While the user drags, [onPreview] gets the time of the handle, so the other controls
 * can show the new trim. The release of the handle calls [onPreview] with null and [onCommit] with the new time.
 * Component ID: trim-timeline
 */
internal class TrimTimeline(
    private val onSeek: (ms: Long) -> Unit,
    private val onCommit: (end: TrimEnd, ms: Long) -> Unit,
    private val onPreview: (end: TrimEnd, ms: Long?) -> Unit = { _, _ -> },
) : JComponent() {
    private var durationMs = 0L
    private var startMs = 0L
    private var endMs = 0L
    private var points: List<Pair<Long, Long>> = emptyList()
    private var playheadMs = 0L

    /** The handle that the user drags, or null. */
    private var dragging: TrimEnd? = null

    /** The time of the dragged handle. */
    private var dragMs = 0L

    /** True while the user drags on the track to seek. */
    private var scrubbing = false

    private val headerFont = UiKit.font(13f)
    private val pillFont = UiKit.font(12f, UiKit.Weight.BOLD)
    private val labelFont = UiKit.font(12f)

    init {
        name = "trim-timeline"
        isOpaque = true
        background = Palette.BG
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e) || durationMs <= 0) return
                val grab = handleAt(e.x, e.y)
                if (grab != null) {
                    dragging = grab
                    dragMs = timeOf(grab)
                } else if (e.y >= trackTop - PILL_GAP) {
                    scrubbing = true
                    seekTo(msAt(e.x))
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                val end = dragging
                if (end != null) {
                    dragMs = clampHandle(end, msAt(e.x))
                    onSeek(seekTimeOf(end, dragMs))
                    onPreview(end, dragMs)
                    repaint()
                } else if (scrubbing) {
                    seekTo(msAt(e.x))
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                val end = dragging
                dragging = null
                scrubbing = false
                if (end != null) {
                    val ms = dragMs
                    // The time stays on the handle until the new trim comes back from the presenter.
                    if (end == TrimEnd.START) startMs = ms else endMs = ms
                    onPreview(end, null)
                    onCommit(end, ms)
                    repaint()
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                cursor = Cursor.getPredefinedCursor(if (handleAt(e.x, e.y) != null) Cursor.E_RESIZE_CURSOR else Cursor.HAND_CURSOR)
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    /** Shows the trim [range] on a source of [durationMs], with [points] as start and end times. */
    fun render(durationMs: Long, range: TrimRange?, points: List<Pair<Long, Long>>) {
        val start = range?.startMs ?: 0L
        val end = range?.endMs ?: durationMs
        if (this.durationMs == durationMs && startMs == start && endMs == end && this.points == points) return
        this.durationMs = durationMs
        if (dragging == null) {
            startMs = start
            endMs = end
        }
        this.points = points
        repaint()
    }

    /** Moves the playhead. */
    fun setPlayhead(ms: Long) {
        if (ms == playheadMs) return
        playheadMs = ms
        repaint()
    }

    /** The x of the center of the [end] handle, for the tests. */
    internal fun handleX(end: TrimEnd): Int = xAt(timeOf(end)).toInt()

    /** The y of the middle of the track, for the tests. */
    internal val trackMiddle: Int get() = trackTop + TRACK_HEIGHT / 2

    private fun seekTo(ms: Long) {
        playheadMs = ms
        onSeek(ms)
        repaint()
    }

    private fun timeOf(end: TrimEnd): Long = when {
        dragging == end -> dragMs
        end == TrimEnd.START -> startMs
        else -> endMs
    }

    /** The end of the trim is just after its last frame, so a seek goes just before it. */
    private fun seekTimeOf(end: TrimEnd, ms: Long) = if (end == TrimEnd.END) (ms - 1).coerceAtLeast(0L) else ms

    /** The trimmed video keeps at least [TrimRange.MIN_LENGTH_MS], so a handle cannot pass the other handle. */
    private fun clampHandle(end: TrimEnd, ms: Long): Long = when (end) {
        TrimEnd.START -> ms.coerceIn(0L, (endMs - TrimRange.MIN_LENGTH_MS).coerceAtLeast(0L))
        TrimEnd.END -> ms.coerceIn((startMs + TrimRange.MIN_LENGTH_MS).coerceAtMost(durationMs), durationMs)
    }

    private val trackTop get() = HEADER_TOP + HEADER_LINE + PILLS_GAP + PILL_HEIGHT + PILL_GAP
    private val trackLeft get() = PAD_X + HANDLE_WIDTH / 2
    private val trackWidth get() = (width - trackLeft * 2).coerceAtLeast(1)

    private fun xAt(ms: Long): Double =
        trackLeft + if (durationMs > 0) trackWidth * (ms.coerceIn(0L, durationMs).toDouble() / durationMs) else 0.0

    private fun msAt(x: Int): Long =
        if (durationMs <= 0) 0L else ((x - trackLeft).toDouble() / trackWidth * durationMs).roundToLong().coerceIn(0L, durationMs)

    /** The handle at [x], [y], or the nearer one when both are in reach. */
    private fun handleAt(x: Int, y: Int): TrimEnd? {
        if (durationMs <= 0 || y < trackTop - PILL_GAP - PILL_HEIGHT || y > trackTop + TRACK_HEIGHT + HANDLE_OVER) return null
        return TrimEnd.entries
            .map { it to abs(xAt(timeOf(it)) - x) }
            .filter { it.second <= GRAB }
            .minByOrNull { it.second }
            ?.first
    }

    override fun getPreferredSize() = Dimension(400, PREFERRED_HEIGHT)
    override fun getMinimumSize() = Dimension(0, PREFERRED_HEIGHT)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, PREFERRED_HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            g2.color = Palette.OVERLAY
            g2.fillRect(0, 0, width, height)
            g2.color = Palette.LINE
            g2.fillRect(0, 0, width, 1)
            paintHeader(g2)
            if (durationMs > 0) {
                paintTrack(g2)
                paintPills(g2)
            } else {
                UiKit.paintBox(g2, trackLeft, trackTop, trackWidth, TRACK_HEIGHT, TRACK_RADIUS, Palette.INSET, Palette.LINE)
            }
            paintLabels(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintHeader(g2: Graphics2D) {
        val top = HEADER_TOP.toFloat()
        UiKit.drawText(g2, tr("adjust.trim.drag"), headerFont, Palette.FG_2, PAD_X.toFloat(), top, HEADER_LINE.toFloat())
    }

    private fun paintTrack(g2: Graphics2D) {
        val top = trackTop.toDouble()
        val h = TRACK_HEIGHT.toDouble()
        val track = RoundRectangle2D.Double(trackLeft.toDouble(), top, trackWidth.toDouble(), h, TRACK_RADIUS * 2.0, TRACK_RADIUS * 2.0)
        g2.color = Palette.INSET
        g2.fill(track)
        val startX = xAt(timeOf(TrimEnd.START))
        val endX = xAt(timeOf(TrimEnd.END))

        // The parts outside the trim are hatched: the export and the Points timeline leave them out.
        val oldClip = g2.clip
        g2.clip(track)
        g2.color = Palette.LINE_3
        g2.stroke = BasicStroke(1.2f)
        listOf(trackLeft.toDouble() to startX, endX to (trackLeft + trackWidth).toDouble()).forEach { (from, to) ->
            if (to - from < 1) return@forEach
            val saved = g2.clip
            g2.clip(Rectangle2D.Double(from, top, to - from, h))
            var x = from - h
            while (x < to) {
                g2.draw(Line2D.Double(x, top + h, x + h, top))
                x += HATCH_STEP
            }
            g2.clip = saved
        }
        g2.color = Palette.LIME_WASH
        g2.fill(Rectangle2D.Double(startX, top, endX - startX, h))
        g2.clip = oldClip
        g2.color = Palette.LIME_EDGE
        g2.stroke = BasicStroke(1f)
        g2.draw(Line2D.Double(startX, top + 0.5, endX, top + 0.5))
        g2.draw(Line2D.Double(startX, top + h - 0.5, endX, top + h - 0.5))

        // The points, as bars across the middle of the track.
        val startMsNow = timeOf(TrimEnd.START)
        val endMsNow = timeOf(TrimEnd.END)
        points.forEach { (from, to) ->
            val outside = from < startMsNow || to > endMsNow
            g2.color = if (outside) Palette.RED else Palette.SAGE
            val x1 = xAt(from)
            val w = (xAt(to) - x1).coerceAtLeast(MIN_POINT_WIDTH)
            g2.fill(RoundRectangle2D.Double(x1, top + (h - POINT_HEIGHT) / 2, w, POINT_HEIGHT, 2.0, 2.0))
        }

        TrimEnd.entries.forEach { end -> paintHandle(g2, xAt(timeOf(end)), top, h, end == dragging) }
        paintPlayhead(g2, xAt(playheadMs), top, h)
    }

    /** The playhead as on the Points tab: a 2 px line with a small triangle on top. */
    private fun paintPlayhead(g2: Graphics2D, x: Double, top: Double, h: Double) {
        val headTop = top - HEAD_HEIGHT
        g2.color = Palette.PLAYHEAD
        g2.fill(Rectangle2D.Double(x - 1, headTop, 2.0, h + HEAD_HEIGHT))
        g2.fill(Path2D.Double().apply {
            moveTo(x - HEAD_HALF_WIDTH, headTop)
            lineTo(x + HEAD_HALF_WIDTH, headTop)
            lineTo(x, headTop + HEAD_HEIGHT)
            closePath()
        })
    }

    private fun paintHandle(g2: Graphics2D, x: Double, top: Double, h: Double, active: Boolean) {
        val w = HANDLE_WIDTH.toDouble()
        g2.color = if (active) Palette.LIME_HOVER else Palette.LIME
        g2.fill(RoundRectangle2D.Double(x - w / 2, top - HANDLE_OVER, w, h + HANDLE_OVER * 2, w, w))
    }

    private fun paintPills(g2: Graphics2D) {
        val top = trackTop - PILL_GAP - PILL_HEIGHT
        val left = Rectangle(PAD_X, top, 0, PILL_HEIGHT)
        TrimEnd.entries.forEach { end ->
            val text = TrimControls.clock(timeOf(end))
            val w = UiKit.textWidth(text, pillFont).toInt() + PILL_PAD * 2
            // The pills stay inside the card and do not cover each other.
            var x = (xAt(timeOf(end)) - w / 2.0).toInt().coerceIn(PAD_X, width - PAD_X - w)
            if (end == TrimEnd.END) x = x.coerceAtLeast(left.x + left.width + 4)
            UiKit.paintBox(g2, x, top, w, PILL_HEIGHT, PILL_HEIGHT / 2, Palette.LIME, null)
            UiKit.drawText(g2, text, pillFont, Palette.ON_LIME, (x + PILL_PAD).toFloat(), top.toFloat(), PILL_HEIGHT.toFloat())
            if (end == TrimEnd.START) left.setBounds(x, top, w, PILL_HEIGHT)
        }
    }

    private fun paintLabels(g2: Graphics2D) {
        val top = (trackTop + TRACK_HEIGHT + LABEL_GAP).toFloat()
        val zero = TrimControls.shortClock(0)
        UiKit.drawText(g2, zero, labelFont, Palette.FG_3, trackLeft.toFloat(), top, LABEL_LINE.toFloat())
        if (durationMs <= 0) return
        val total = TrimControls.clock(durationMs)
        UiKit.drawText(g2, total, labelFont, Palette.FG_3, trackLeft + trackWidth - UiKit.textWidth(total, labelFont), top, LABEL_LINE.toFloat())
        if (points.isNotEmpty()) {
            val count = tr("adjust.trim.points", "count" to points.size)
            UiKit.drawText(g2, count, labelFont, Palette.FG_3, (width - UiKit.textWidth(count, labelFont)) / 2f, top, LABEL_LINE.toFloat())
        }
    }

    private companion object {
        const val PAD_X = 16
        const val HEADER_TOP = 8
        const val HEADER_LINE = 20
        const val PILLS_GAP = 2
        const val PILL_HEIGHT = 20
        const val PILL_PAD = 8
        const val PILL_GAP = 6
        const val TRACK_HEIGHT = 34
        const val TRACK_RADIUS = 5
        const val HANDLE_WIDTH = 4
        const val HANDLE_OVER = 4
        const val GRAB = 10.0
        const val HATCH_STEP = 8.0
        const val POINT_HEIGHT = 14.0
        const val MIN_POINT_WIDTH = 2.0
        const val LABEL_GAP = 4
        const val HEAD_HALF_WIDTH = 6.0
        const val HEAD_HEIGHT = 7.0
        const val LABEL_LINE = 16
        const val PREFERRED_HEIGHT = HEADER_TOP + HEADER_LINE + PILLS_GAP + PILL_HEIGHT + PILL_GAP + TRACK_HEIGHT + LABEL_GAP + LABEL_LINE + 4
    }
}
