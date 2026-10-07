package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.points.CommentV1
import org.litvin.points.PointV1
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.Html
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.ToolTipManager
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit

internal data class CommentMarkerLayout(
    val id: Int,
    val startMs: Long,
    val lineX: Int,
    val boxBounds: Rectangle,
    val lane: Int,
    val lineTop: Int,
    val lineBottom: Int,
)

/**
 * Three-track editor timeline for VIDEO, MARKS, and source-pinned COMMENTS.
 * A label column on the left names the tracks and shows the counts; the tracks start after it.
 * The MARKS track paints favorites yellow, draws an outline around the selected point, and shows
 * a pending point as a hatched range from its start to the playhead.
 */
class SwingTimelineComponent(
    private val timeProvider: () -> Long,
    private val durationProvider: () -> Long,
    private val pointsProvider: () -> List<PointV1>,
    private val onSeekRequested: (Long) -> Unit,
    private val commentsProvider: () -> List<CommentV1> = { emptyList() },
    private val onCommentSelected: (Int) -> Unit = {},
    /** Called for each drag step. It must be fast. The release calls [onSeekRequested] for the exact frame. */
    private val onScrubRequested: (Long) -> Unit = onSeekRequested,
    private val selectedPointIdProvider: () -> String? = { null },
    private val pendingStartProvider: () -> Long? = { null },
) : JComponent() {

    private val gutterBg get() = Palette.INSET
    private val rowLine get() = Palette.ROW_LINE
    private val tickColor get() = Palette.LINE_3
    private val defaultCommentColor get() = Palette.YELLOW
    private val tagText get() = Palette.ON_LIGHT
    private val gutterFont = UiKit.trackedFont(10.5f, 0.06, UiKit.Weight.SEMIBOLD)
    private val countFont = UiKit.font(10.5f)
    private val tickFont = UiKit.font(10.5f)
    private val tagFont = UiKit.font(10.5f, UiKit.Weight.BOLD)
    private val videoIcon: Icon = UiKit.icon(Material2MZ.MOVIE, 14, Palette.FG_3)
    private val marksIcon: Icon = UiKit.icon(Material2AL.FLAG, 14, Palette.FG_3)
    private val commentsIcon: Icon = UiKit.icon(Material2AL.CHAT_BUBBLE_OUTLINE, 14, Palette.FG_3)
    private var lastKnownLaneCount = -1
    private var pressPoint: Point? = null

    /** The drag position while the user drags the playhead. Null when no drag occurs. */
    var scrubTimeMs: Long? = null
        private set

    init {
        isOpaque = true
        background = Palette.PANEL
        ToolTipManager.sharedInstance().registerComponent(this)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = handlePress(event)
            override fun mouseReleased(event: MouseEvent) = handleRelease()
            override fun mouseExited(event: MouseEvent) = updateCursor(null)
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(event: MouseEvent) = updateCursor(event.point)
            override fun mouseDragged(event: MouseEvent) = handleDrag(event)
        })
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                val laneCount = commentLaneCount()
                if (laneCount != lastKnownLaneCount) {
                    lastKnownLaneCount = laneCount
                    revalidate()
                }
            }
        })
    }

    /** Shows the comment time and text when the pointer is over a comment marker. */
    override fun getToolTipText(event: MouseEvent): String? {
        val layout = commentMarkerAt(event.point) ?: return null
        val comment = commentsProvider().firstOrNull { it.id == layout.id } ?: return null
        val header = "#${comment.id} · ${Timecode.format(comment.startMs.toLong())}"
        return Html.wrappedTooltip(header + "\n" + comment.text)
    }

    private fun handlePress(event: MouseEvent) {
        if (event.x < GUTTER) return
        pressPoint = event.point
        val comment = commentMarkerAt(event.point)
        if (comment != null) {
            onCommentSelected(comment.id)
            onSeekRequested(comment.startMs)
            return
        }

        val mark = markAt(event.point)
        if (mark != null) {
            onSeekRequested(mark.startMs.toLong())
            return
        }
        scrubTo(event.x)
    }

    /** A drag moves the playhead. A drag that starts on a comment or a mark starts after a small movement. */
    private fun handleDrag(event: MouseEvent) {
        val start = pressPoint ?: return
        if (scrubTimeMs == null && start.distance(event.point) < DRAG_THRESHOLD_PX) return
        scrubTo(event.x)
    }

    private fun handleRelease() {
        pressPoint = null
        val target = scrubTimeMs ?: return
        scrubTimeMs = null
        onSeekRequested(target)
        repaint()
    }

    private fun scrubTo(x: Int) {
        val target = timeAtX(x)
        if (target == scrubTimeMs) return
        scrubTimeMs = target
        onScrubRequested(target)
        repaint()
    }

    private fun lanesWidth(): Int = (width - GUTTER).coerceAtLeast(1)

    private fun timeAtX(x: Int): Long {
        val total = max(1L, durationProvider())
        return (((x - GUTTER).toDouble() / lanesWidth().toDouble()) * total).toLong().coerceIn(0L, total)
    }

    private fun xAt(ms: Long, pxPerMs: Double): Int = GUTTER + (ms * pxPerMs).toInt()

    private fun commentMarkerAt(point: Point): CommentMarkerLayout? =
        commentMarkerLayouts().firstOrNull { it.boxBounds.contains(point) }

    private fun markAt(point: Point): PointV1? {
        val markRect = Rectangle(GUTTER, marksTrackTop(), lanesWidth(), TRACK_HEIGHT)
        if (!markRect.contains(point)) return null
        val pxPerMs = pxPerMs(max(1L, durationProvider()), lanesWidth())
        return pointsProvider().firstOrNull { mark ->
            val x1 = xAt(mark.startMs.toLong(), pxPerMs)
            val x2 = xAt(mark.endMs.toLong(), pxPerMs)
            point.x in x1..max(x1 + 1, x2)
        }
    }

    /** Hand over the discrete targets (comment labels, marks), crosshair over the scrubbable rest. */
    private fun cursorFor(point: Point?): Cursor = when {
        point == null || point.x < GUTTER -> Cursor.getDefaultCursor()
        commentMarkerAt(point) != null || markAt(point) != null -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        else -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
    }

    private fun updateCursor(point: Point?) {
        val next = cursorFor(point)
        if (cursor != next) cursor = next
    }

    private fun videoTrackTop(): Int = TOP_BORDER + RULER_HEIGHT
    private fun marksTrackTop(): Int = videoTrackTop() + TRACK_HEIGHT
    private fun commentsTrackTop(): Int = marksTrackTop() + TRACK_HEIGHT

    private fun commentTrackHeight(laneCount: Int): Int = max(TRACK_HEIGHT, laneCount * TAG_LANE + 5)

    private fun pxPerMs(totalMs: Long, availableWidth: Int): Double =
        if (totalMs <= 0) 0.0 else availableWidth.toDouble() / totalMs.toDouble()

    override fun paintComponent(graphics: Graphics) {
        val g = UiKit.smooth(graphics)
        try {
            paintTimeline(g)
        } finally {
            g.dispose()
        }
    }

    private fun paintTimeline(g: Graphics2D) {
        g.color = Palette.PANEL
        g.fillRect(0, 0, width, height)
        g.color = Palette.LINE
        g.fillRect(0, 0, width, TOP_BORDER)

        val total = max(1L, durationProvider())
        val points = pointsProvider()
        val comments = commentsProvider()
        val layouts = commentMarkerLayouts(total, comments)
        val laneCount = (layouts.maxOfOrNull { it.lane } ?: -1) + 1
        val pxPerMs = pxPerMs(total, lanesWidth())

        paintGutter(g, points.size, comments.size, commentTrackHeight(laneCount))
        paintRuler(g, total)
        paintVideoTrack(g)
        paintMarksTrack(g, points, pxPerMs)
        paintCommentsTrack(g, comments, layouts, commentTrackHeight(laneCount))

        val current = (scrubTimeMs ?: timeProvider()).coerceIn(0L, total)
        paintPlayhead(g, xAt(current, pxPerMs))
    }

    private fun paintGutter(g: Graphics2D, markCount: Int, commentCount: Int, commentHeight: Int) {
        g.color = gutterBg
        g.fillRect(0, TOP_BORDER, GUTTER, height - TOP_BORDER)
        g.color = Palette.LINE
        g.fillRect(GUTTER - 1, TOP_BORDER, 1, height - TOP_BORDER)
        g.color = rowLine
        listOf(videoTrackTop(), marksTrackTop(), commentsTrackTop()).forEach { g.fillRect(0, it, GUTTER - 1, 1) }

        fun label(icon: Icon, text: String, count: String, top: Int, rowHeight: Int) {
            icon.paintIcon(this, g, GUTTER_PAD, top + (rowHeight - icon.iconHeight) / 2)
            UiKit.drawText(g, text, gutterFont, Palette.FG_2, (GUTTER_PAD + icon.iconWidth + 6).toFloat(), top.toFloat(), rowHeight.toFloat())
            if (count.isNotEmpty()) {
                val x = GUTTER - 1 - GUTTER_PAD - UiKit.textWidth(count, countFont)
                UiKit.drawText(g, count, countFont, Palette.FG_3, x, top.toFloat(), rowHeight.toFloat())
            }
        }
        label(videoIcon, "VIDEO", "", videoTrackTop(), TRACK_HEIGHT)
        label(marksIcon, "MARKS", markCount.toString(), marksTrackTop(), TRACK_HEIGHT)
        label(commentsIcon, "COMMENTS", if (commentCount > 0) commentCount.toString() else "", commentsTrackTop(), commentHeight)
    }

    private fun paintRuler(g: Graphics2D, total: Long) {
        val top = TOP_BORDER
        val lanes = lanesWidth()
        g.color = gutterBg
        g.fillRect(GUTTER, top, lanes, RULER_HEIGHT)
        g.color = Palette.LINE
        g.fillRect(GUTTER, top + RULER_HEIGHT - 1, lanes, 1)
        for (index in 0..TICKS) {
            val x = GUTTER + (index * (lanes.toDouble() / TICKS)).toInt()
            g.color = tickColor
            g.fillRect(min(x, width - 1), top, 1, RULER_HEIGHT - 1)
            // A narrow timeline labels every second tick only.
            if (lanes < ALL_TICK_LABELS_WIDTH && index % 2 == 1) continue
            val text = Timecode.format(index * (total / TICKS.toLong()))
            val textX = if (index == TICKS) x - 4 - UiKit.textWidth(text, tickFont) else x + 4f
            UiKit.drawText(g, text, tickFont, Palette.FG_3, textX, top.toFloat(), RULER_HEIGHT - 1f)
        }
    }

    private fun paintVideoTrack(g: Graphics2D) {
        val top = videoTrackTop()
        g.color = Palette.VIDEO_RANGE
        g.fill(RoundRectangle2D.Double(GUTTER.toDouble(), top + 7.0, lanesWidth().toDouble(), 8.0, 4.0, 4.0))
    }

    private fun paintMarksTrack(g: Graphics2D, points: List<PointV1>, pxPerMs: Double) {
        val top = marksTrackTop()
        g.color = rowLine
        g.fillRect(GUTTER, top, lanesWidth(), 1)
        val selectedId = selectedPointIdProvider()
        var selected: Rectangle? = null
        points.forEach { point ->
            val x1 = xAt(point.startMs.toLong(), pxPerMs)
            val w = max(2, xAt(point.endMs.toLong(), pxPerMs) - x1)
            if (point.id == selectedId) {
                selected = Rectangle(x1, top + 1, w, TRACK_HEIGHT - 2)
                return@forEach
            }
            g.color = if (point.favorite) Palette.YELLOW else Palette.GREEN
            g.fill(RoundRectangle2D.Double(x1.toDouble(), top + 4.0, w.toDouble(), TRACK_HEIGHT - 8.0, 2.0, 2.0))
        }
        pendingStartProvider()?.let { start -> paintPending(g, start, pxPerMs, top) }
        selected?.let { box ->
            val favorite = points.firstOrNull { it.id == selectedId }?.favorite == true
            g.color = if (favorite) Palette.YELLOW else Palette.GREEN
            g.fill(RoundRectangle2D.Double(box.x.toDouble(), box.y.toDouble(), box.width.toDouble(), box.height.toDouble(), 2.0, 2.0))
            g.color = Palette.FG_STRONG
            g.stroke = BasicStroke(1.5f)
            g.draw(RoundRectangle2D.Double(box.x - 0.75, box.y - 0.75, box.width + 1.5, box.height + 1.5, 3.0, 3.0))
        }
    }

    /** The pending point: a hatched range with a dashed lime border from its start to the playhead. */
    private fun paintPending(g: Graphics2D, startMs: Long, pxPerMs: Double, top: Int) {
        val now = scrubTimeMs ?: timeProvider()
        val x1 = xAt(min(startMs, now), pxPerMs)
        val w = max(3, xAt(max(startMs, now), pxPerMs) - x1)
        val box = Rectangle(x1, top + 3, w, TRACK_HEIGHT - 6)
        val clip = g.clip
        g.clip(box)
        g.color = Palette.LIME_EDGE
        g.stroke = BasicStroke(4f * 0.7071f)
        var x = box.x - box.height
        while (x < box.x + box.width + box.height) {
            g.drawLine(x, box.y + box.height, x + box.height, box.y)
            x += 8
        }
        g.clip = clip
        g.color = Palette.LIME
        g.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(3f, 3f), 0f)
        g.draw(RoundRectangle2D.Double(box.x + 0.5, box.y + 0.5, box.width - 1.0, box.height - 1.0, 3.0, 3.0))
        g.stroke = BasicStroke(1f)
    }

    private fun paintCommentsTrack(g: Graphics2D, comments: List<CommentV1>, layouts: List<CommentMarkerLayout>, trackHeight: Int) {
        val top = commentsTrackTop()
        g.color = rowLine
        g.fillRect(GUTTER, top, lanesWidth(), 1)
        val byId = comments.associateBy { it.id }
        for (layout in layouts) {
            val color = byId[layout.id]?.colorHex?.let(::colorFor) ?: defaultCommentColor
            g.color = Palette.withAlpha(color, 204)
            g.fillRect(layout.lineX, layout.lineTop, 2, max(1, layout.lineBottom - layout.lineTop))
        }
        for (layout in layouts) {
            val comment = byId[layout.id] ?: continue
            val box = layout.boxBounds
            g.color = colorFor(comment.colorHex)
            g.fill(RoundRectangle2D.Double(box.x.toDouble(), box.y.toDouble(), box.width.toDouble(), box.height.toDouble(), 6.0, 6.0))
            UiKit.drawText(g, "#${layout.id}", tagFont, tagText, box.x + TAG_PAD.toFloat(), box.y.toFloat(), box.height.toFloat())
        }
    }

    private fun paintPlayhead(g: Graphics2D, x: Int) {
        g.color = Palette.PLAYHEAD
        g.fillRect(x - 1, TOP_BORDER, 2, height - TOP_BORDER)
        val head = Path2D.Double().apply {
            moveTo(x - 6.0, TOP_BORDER.toDouble())
            lineTo(x + 6.0, TOP_BORDER.toDouble())
            lineTo(x.toDouble(), TOP_BORDER + 7.0)
            closePath()
        }
        g.fill(head)
    }

    private fun commentMarkerLayouts(
        totalMs: Long = max(1L, durationProvider()),
        comments: List<CommentV1> = commentsProvider(),
    ): List<CommentMarkerLayout> {
        val pxPerMs = pxPerMs(totalMs, lanesWidth())
        val laneEnds = mutableListOf<Int>()
        val result = mutableListOf<CommentMarkerLayout>()
        val sorted = comments.asSequence()
            .filter { it.id > 0 && it.startMs >= 0 }
            .sortedWith(compareBy<CommentV1> { it.startMs }.thenBy { it.id })
            .toList()
        val top = commentsTrackTop()
        for (comment in sorted) {
            val lineX = xAt(comment.startMs.toLong(), pxPerMs).coerceIn(GUTTER, max(GUTTER, width - 2))
            val boxLeft = lineX + 3
            val boxWidth = ceil(UiKit.textWidth("#${comment.id}", tagFont)).toInt() + TAG_PAD * 2
            val lane = laneEnds.indexOfFirst { previousEnd -> previousEnd < boxLeft }
                .let { if (it >= 0) it else laneEnds.size }
            if (lane == laneEnds.size) laneEnds += boxLeft + boxWidth else laneEnds[lane] = boxLeft + boxWidth
            val boxTop = top + 3 + lane * TAG_LANE
            result += CommentMarkerLayout(
                id = comment.id,
                startMs = comment.startMs.toLong(),
                lineX = lineX,
                boxBounds = Rectangle(boxLeft, boxTop, boxWidth, TAG_HEIGHT),
                lane = lane,
                lineTop = top,
                lineBottom = top,
            )
        }
        val finalBottom = top + commentTrackHeight(laneEnds.size)
        return result.map { it.copy(lineBottom = finalBottom) }
    }

    private fun commentLaneCount(): Int = (commentMarkerLayouts().maxOfOrNull { it.lane } ?: -1) + 1

    private fun colorFor(hex: String): Color = runCatching { Color.decode(hex) }.getOrDefault(defaultCommentColor)

    internal fun commentMarkerLayoutsForTest(): List<CommentMarkerLayout> = commentMarkerLayouts()
    internal fun commentLaneCountForTest(): Int = commentLaneCount()
    internal fun videoTrackTopForTest(): Int = videoTrackTop()
    internal fun marksTrackCenterYForTest(): Int = marksTrackTop() + TRACK_HEIGHT / 2
    internal fun commentsTrackTopForTest(): Int = commentsTrackTop()
    internal fun commentTrackBottomForTest(): Int = commentsTrackTop() + commentTrackHeight(commentLaneCountForTest())
    internal fun cursorAtForTest(x: Int, y: Int): Cursor = cursorFor(Point(x, y))

    override fun getPreferredSize(): Dimension {
        val laneCount = if (width > 0) commentLaneCount() else 1
        return Dimension(400, commentsTrackTop() + commentTrackHeight(laneCount))
    }

    internal companion object {
        /** The width of the label column. */
        const val GUTTER = 124
        const val TOP_BORDER = 1
        const val RULER_HEIGHT = 20
        const val TRACK_HEIGHT = 22
        const val TAG_HEIGHT = 16
        const val TAG_LANE = 19
        const val TAG_PAD = 5
        const val GUTTER_PAD = 10
        const val TICKS = 10
        const val ALL_TICK_LABELS_WIDTH = 1300
        const val DRAG_THRESHOLD_PX = 3.0
    }
}
