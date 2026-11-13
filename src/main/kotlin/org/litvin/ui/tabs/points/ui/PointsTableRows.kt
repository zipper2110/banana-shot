package org.litvin.ui.tabs.points.ui

import org.litvin.i18n.DisplayFormats
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.Html
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.RowIconButton
import org.litvin.ui.commons.formatSeconds
import org.litvin.ui.tabs.points.CommentDto
import org.litvin.ui.tabs.points.PointDto
import org.litvin.ui.tabs.points.ReplayRowDto
import java.awt.Cursor
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.ceil
import org.litvin.i18n.tr

/**
 * The columns of the points table: #, Start, Length, the marks, ★ and the row actions.
 * The widths: 34 px, 70 px, 50 px, the rest, 24 px and 82 px, with 5 px gaps.
 * The marks (game, set and serve) are at the right end of their column.
 */
internal data class TableColumns(val width: Int) {
    val number = PAD_LEFT
    val start = number + 34 + GAP
    val length = start + 70 + GAP
    val lengthRight = length + 50
    val actionsRight = width - PAD_RIGHT
    val actions = actionsRight - ACTIONS
    val star = actions - GAP - STAR
    val marksRight = star - GAP

    companion object {
        const val PAD_LEFT = 14
        const val PAD_RIGHT = 8
        const val GAP = 5
        const val STAR = 24
        const val ACTIONS = 82
    }
}

/** The caption row of the points table. */
internal class TableHeaderRow : JComponent() {
    private val captionFont = UiKit.trackedFont(10.5f, 0.07)
    private val starIcon = UiKit.icon(Material2MZ.STAR, 11, Palette.FG_3)

    override fun getPreferredSize() = Dimension(0, HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            g2.color = Palette.INSET
            g2.fillRect(0, 0, width, height)
            g2.color = Palette.LINE
            g2.fillRect(0, 0, width, 1)
            g2.fillRect(0, height - 1, width, 1)
            val columns = TableColumns(width - scrollBarWidth)
            val top = 1f
            val h = height - 2f
            fun caption(text: String, x: Float) = UiKit.drawText(g2, text, captionFont, Palette.FG_3, x, top, h)
            caption("#", columns.number.toFloat())
            caption(DisplayFormats.upper(tr("points.column.start")), columns.start.toFloat())
            val length = DisplayFormats.upper(tr("points.column.length"))
            caption(length, columns.lengthRight - UiKit.textWidth(length, captionFont))
            // A star icon, because some fonts have no "★" glyph.
            starIcon.paintIcon(this, g2, columns.star + (TableColumns.STAR - starIcon.iconWidth) / 2, (height - starIcon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    /** The width of the list scroll bar, so that the captions stay above their columns. */
    var scrollBarWidth: Int = 0
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    companion object {
        const val HEIGHT = 26
    }
}

/** A row of the points table. The table sets [hovered] and [selected]; the row paints them. */
internal abstract class TableRow(val visualIndex: Int, val startMs: Long) : JPanel(null) {
    var hovered = false
        set(value) {
            if (field == value) return
            field = value
            onStateChanged()
            repaint()
        }

    var selected = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    /** The title of the row, for example "#4" or "Comment #1". */
    abstract val title: String

    /** Rows that can have the selected state. A comment row has no selected state. */
    abstract val selectable: Boolean

    /** The height of the row. A replay row is lower than a point row or a comment row. */
    open val rowHeight: Int get() = HEIGHT

    /** The place of a point row in its pack in advanced mode (B-68), or null. The list adds a gap around a pack. */
    open val packPosition: PackPosition? get() = null

    protected open fun onStateChanged() = Unit

    init {
        isOpaque = false
    }

    override fun getPreferredSize() = Dimension(0, rowHeight)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, rowHeight)
    override fun getMinimumSize() = Dimension(0, rowHeight)

    /** [lineFrom] is the x where the line at the bottom of the row starts. The rows of a pack have an indented line. */
    protected fun paintRowBackground(g2: Graphics2D, rest: Color?, bar: Color?, barWidth: Int = BAR, lineFrom: Int = 0) {
        val fill = when {
            selected && selectable -> Palette.SELECTED
            hovered -> Palette.ROW_HOVER
            else -> rest
        }
        if (fill != null) {
            g2.color = fill
            g2.fillRect(0, 0, width, height)
        }
        g2.color = Palette.ROW_LINE
        g2.fillRect(lineFrom, height - 1, width - lineFrom, 1)
        if (bar != null) {
            g2.color = bar
            g2.fillRect(0, 0, barWidth, height)
        }
    }

    /** Places the action buttons at the right end of the actions column, 2 px apart. */
    protected fun layoutActions(buttons: List<JComponent>) {
        val columns = TableColumns(width)
        var x = columns.actionsRight
        for (button in buttons.asReversed()) {
            val size = button.preferredSize
            x -= size.width
            button.setBounds(x, (height - size.height) / 2, size.width, size.height)
            x -= 2
        }
    }

    companion object {
        const val HEIGHT = 45

        /** The visual index of a row that is not a timeline event, for example a replay row. */
        const val NO_INDEX = -1

        /** The width of the selection bar and the comment color bar at the left edge. */
        const val BAR = 3

        /** The width of the winner color strip of a scored point. */
        const val WINNER_STRIP = 8

        /** The gap in px before and after a pack with two clips or more (B-68). */
        const val PACK_GAP = 6
    }
}

/**
 * The game, set, match and serve marks of a point.
 * [milestone] is the "MATCH", "SET" or "GAME" badge text. [milestoneIsSet] is true for the outline badge of SET and MATCH.
 * A null [serveColor] means that the point has no serve mark.
 */
internal data class PointMarks(
    val milestone: String? = null,
    val milestoneIsSet: Boolean = false,
    val milestoneColor: Color = Palette.FG,
    /** The text color on [milestoneColor]. */
    val milestoneOnColor: Color = Palette.BG,
    val milestoneTip: String = "",
    val serveColor: Color? = null,
    /** True when the racket stands on a light disc, because [serveColor] is dark. */
    val serveOnBadge: Boolean = false,
    val serveTip: String = "",
    /** True when the point has a score that the user typed (Override score). */
    val overridden: Boolean = false,
    /** True when the point comes after the end of the match. The score does not count it, so the row is dim. */
    val afterMatch: Boolean = false,
    /** True for the point that ends the match. The badge is a trophy, because a word does not fit in all languages. */
    val matchPoint: Boolean = false,
)

/** One marked point: number, start, length, the marks, the favorite star, and Edit and Delete on hover. */
internal class PointRow(
    visualIndex: Int,
    private val number: Int,
    val point: PointDto,
    /** The winner color strip. */
    private val stripColor: () -> Color? = { null },
    /** The game, set and serve marks. */
    private val marks: () -> PointMarks? = { null },
    /** The pack number, the pack position, and the label in advanced mode (B-68). Null in basic mode. */
    private val info: () -> PointRowInfo? = { null },
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    /** Opens the replay editor of the point (B-50). */
    onReplays: () -> Unit = {},
) : TableRow(visualIndex, point.startMs) {

    /** The number of the row: the pack number in advanced mode (B-68), else the position of the clip in the list. */
    private val shownNumber: Int get() = info()?.number ?: number

    override val title: String get() = "#$shownNumber"
    override val selectable = true
    override val packPosition: PackPosition? get() = info()?.position

    /** The label of the row in advanced mode, for example "2nd serve · UE net", or null in basic mode. */
    internal val labelText: String? get() = info()?.label?.text()

    val favoriteButton = RowIconButton(
        if (point.favorite) Material2MZ.STAR else Material2MZ.STAR_BORDER,
        tr("points.favorite.tooltip", "key" to "A"),
        color = if (point.favorite) Palette.YELLOW else Palette.FG_3,
        side = 22,
    ).apply { name = "favorite-point-${point.id}" }
    val replayButton = RowIconButton(Material2MZ.SLOW_MOTION_VIDEO, tr("points.replay.row.tooltip")).apply { name = "replays-point-${point.id}" }
    val editButton = RowIconButton(Material2AL.EDIT, tr("points.row.edit.tooltip")).apply { name = "edit-point-${point.id}" }
    val deleteButton = RowIconButton(Material2AL.CLOSE, tr("pointEdit.delete.title"), danger = true).apply { name = "delete-point-${point.id}" }

    private val textFont get() = UiKit.font(12.5f)
    private val labelFont get() = UiKit.font(11.5f)
    private val numberFont get() = UiKit.font(12.5f, UiKit.Weight.BOLD)
    private val badgeFont get() = UiKit.trackedFont(9.5f, 0.05, UiKit.Weight.BOLD)
    private val racketIcons = HashMap<Color, javax.swing.Icon>()
    private val trophyIcons = HashMap<Color, javax.swing.Icon>()
    private val overrideIcon by lazy { UiKit.icon(Material2AL.EDIT, OVERRIDE, Palette.LIME) }

    init {
        // The row shows the tooltips of the marks (see getToolTipText).
        toolTipText = ""
        add(favoriteButton)
        add(replayButton)
        add(editButton)
        add(deleteButton)
        favoriteButton.addActionListener { onToggleFavorite() }
        replayButton.addActionListener { onReplays() }
        editButton.addActionListener { onEdit() }
        deleteButton.addActionListener { onDelete() }
        onStateChanged()
    }

    override fun onStateChanged() {
        // The star of a favorite stays visible. The other buttons show only while the pointer is over the row.
        favoriteButton.isVisible = point.favorite || hovered
        replayButton.isVisible = hovered
        editButton.isVisible = hovered
        deleteButton.isVisible = hovered
    }

    override fun doLayout() {
        val columns = TableColumns(width)
        val star = favoriteButton.preferredSize
        favoriteButton.setBounds(columns.star + (TableColumns.STAR - star.width) / 2, (height - star.height) / 2, star.width, star.height)
        layoutActions(listOf(replayButton, editButton, deleteButton))
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val showSelection = selected
            val marks = marks()
            val info = info()
            val afterMatch = marks?.afterMatch == true
            // A fault clip and a let clip do not give the point (B-68): their strip and their texts are dim, as for a
            // point after the end of the match.
            val dim = afterMatch || info?.dimmed == true
            // A point after the end of the match does not count: its winner strip and its texts are dim.
            val strip = stripColor()?.let { if (dim) Palette.withAlpha(it, AFTER_MATCH_ALPHA) else it }
            val columns = TableColumns(width)
            // The rows of a pack have an indented line between them, and the last row has the full line.
            val position = info?.position ?: PackPosition.SINGLE
            val lineFrom = if (position == PackPosition.FIRST || position == PackPosition.MIDDLE) columns.number else 0
            if (strip != null) paintRowBackground(g2, null, strip, WINNER_STRIP, lineFrom)
            else paintRowBackground(g2, null, if (showSelection) Palette.LIME else null, lineFrom = lineFrom)
            // A scored point keeps its winner strip; the selection shows as a lime outline.
            if (showSelection && strip != null) UiKit.paintBox(g2, 0, 0, width, height, 0, null, Palette.LIME_LINE)
            if (position.grouped) paintPackLine(g2, position)
            // In advanced mode, the row has two lines: the number and the times, then the label (B-68).
            val label = info?.label?.text()
            val top = if (label != null) LINE_TOP else 0f
            val h = if (label != null) LINE_HEIGHT else height - 1f
            val numberColor = when {
                showSelection -> Palette.LIME
                dim -> Palette.FG_3
                else -> Palette.FG_2
            }
            // The number of a pack shows only on its first row.
            if (info?.showNumber != false) {
                UiKit.drawText(g2, shownNumber.toString(), numberFont, numberColor, columns.number.toFloat(), top, h)
            }
            UiKit.drawText(g2, Timecode.format(point.startMs), textFont, if (dim) Palette.FG_3 else Palette.FG, columns.start.toFloat(), top, h)
            val length = point.endMs?.let { formatSeconds(it - point.startMs) } ?: ""
            UiKit.drawText(g2, length, textFont, Palette.FG_3, columns.lengthRight - UiKit.textWidth(length, textFont), top, h)
            val marksLeft = marks?.let { paintMarks(g2, it, columns.marksRight) } ?: columns.marksRight
            if (label != null) {
                // The label ends before the marks. A label that does not fit ends with "…".
                val right = minOf(marksLeft, columns.marksRight) - TableColumns.GAP
                val shown = UiKit.ellipsize(label, labelFont, (right - columns.number).toFloat())
                UiKit.drawText(g2, shown, labelFont, if (dim) Palette.FG_3 else Palette.FG_2, columns.number.toFloat(), top + h, LABEL_HEIGHT)
            }
        } finally {
            g2.dispose()
        }
    }

    /** The thin line at the left of the rows of a pack (B-68). It starts in the first row and ends in the last row. */
    private fun paintPackLine(g2: Graphics2D, position: PackPosition) {
        val from = if (position == PackPosition.FIRST) PACK_LINE_INSET else 0
        val to = if (position == PackPosition.LAST) height - PACK_LINE_INSET else height
        g2.color = Palette.LINE_5
        g2.fillRect(PACK_LINE_X, from, PACK_LINE_WIDTH, to - from)
    }

    private fun badgeWidth(text: String) = ceil(UiKit.textWidth(text, badgeFont)).toInt() + 10

    private fun milestoneWidth(marks: PointMarks, text: String) = if (marks.matchPoint) TROPHY_BADGE else badgeWidth(text)

    private fun racketWidth(marks: PointMarks) = if (marks.serveOnBadge) RACKET_BADGE else RACKET

    /**
     * The GAME or SET badge at the right end of the marks column, and the serve racket before it.
     * Returns the left end of the painted marks, with a gap.
     */
    private fun paintMarks(g2: Graphics2D, marks: PointMarks, marksRight: Int): Int {
        var right = marksRight
        marks.milestone?.let { text ->
            val w = milestoneWidth(marks, text)
            val x = right - w
            val y = (height - BADGE_HEIGHT) / 2
            // SET and MATCH are outline badges: the player color on its contrast color. GAME is the filled badge.
            if (marks.matchPoint) {
                UiKit.paintBox(g2, x, y, w, BADGE_HEIGHT, 3, marks.milestoneOnColor, marks.milestoneColor)
                val icon = trophyIcons.getOrPut(marks.milestoneColor) { UiKit.icon(Material2AL.EMOJI_EVENTS, TROPHY, marks.milestoneColor) }
                icon.paintIcon(this, g2, x + (w - icon.iconWidth) / 2, y + (BADGE_HEIGHT - icon.iconHeight) / 2)
            } else if (marks.milestoneIsSet) {
                UiKit.paintBox(g2, x, y, w, BADGE_HEIGHT, 3, marks.milestoneOnColor, marks.milestoneColor)
                UiKit.drawText(g2, text, badgeFont, marks.milestoneColor, x + 5f, y.toFloat(), BADGE_HEIGHT.toFloat())
            } else {
                UiKit.paintBox(g2, x, y, w, BADGE_HEIGHT, 3, marks.milestoneColor, null)
                UiKit.drawText(g2, text, badgeFont, marks.milestoneOnColor, x + 5f, y.toFloat(), BADGE_HEIGHT.toFloat())
            }
            right = x - MARK_GAP
        }
        marks.serveColor?.let { color ->
            val side = racketWidth(marks)
            val x = right - side
            if (marks.serveOnBadge) {
                g2.color = Palette.NEUTRAL_LIGHT
                g2.fillOval(x, (height - side) / 2, side, side)
            }
            val icon = racketIcons.getOrPut(color) { UiKit.icon(Material2MZ.SPORTS_TENNIS, RACKET, color) }
            icon.paintIcon(this, g2, x + (side - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
            right = x - MARK_GAP
        }
        if (marks.overridden) {
            overrideIcon.paintIcon(this, g2, right - OVERRIDE, (height - overrideIcon.iconHeight) / 2)
            right -= OVERRIDE + MARK_GAP
        }
        return right
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val marks = marks() ?: return null
        var right = TableColumns(width).marksRight
        marks.milestone?.let { text ->
            val left = right - milestoneWidth(marks, text)
            if (event.x in left..right) return marks.milestoneTip
            right = left - MARK_GAP
        }
        if (marks.serveColor != null) {
            val left = right - racketWidth(marks)
            if (event.x in left..right) return marks.serveTip
            right = left - MARK_GAP
        }
        if (marks.overridden && event.x in (right - OVERRIDE)..right) return tr("points.row.override.tooltip")
        return if (marks.afterMatch) tr("points.row.afterMatch.tooltip") else null
    }

    private companion object {
        const val BADGE_HEIGHT = 16
        const val MARK_GAP = 4
        const val RACKET = 15
        const val RACKET_BADGE = 21
        const val OVERRIDE = 14
        const val TROPHY = 13
        const val TROPHY_BADGE = 24

        /** The alpha of the winner strip of a point after the end of the match. */
        const val AFTER_MATCH_ALPHA = 70

        /** The two lines of a row with a label (B-68): the top of the first line and the height of each line. */
        const val LINE_TOP = 5f
        const val LINE_HEIGHT = 19f
        const val LABEL_HEIGHT = 16f

        /** The line at the left of the rows of a pack: after the winner strip, before the number. */
        const val PACK_LINE_X = TableRow.WINNER_STRIP + 2
        const val PACK_LINE_WIDTH = 2
        const val PACK_LINE_INSET = 8
    }
}

/** One comment: a color bar, the number, the time and duration, one line of text, and Edit and Delete on hover. */
internal class CommentRow(
    visualIndex: Int,
    val comment: CommentDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) : TableRow(visualIndex, comment.startMs) {

    override val title = tr("points.row.comment", "number" to comment.id)
    override val selectable = false

    private val color: Color = runCatching { Color.decode(comment.colorHex) }.getOrDefault(Palette.FG)
    private val bubble = UiKit.icon(Material2AL.CHAT_BUBBLE, 14, color)
    val editButton = RowIconButton(Material2AL.EDIT, tr("points.row.editComment.tooltip")).apply { name = "edit-comment-${comment.id}" }
    val deleteButton = RowIconButton(Material2AL.CLOSE, tr("points.row.deleteComment.tooltip"), danger = true).apply { name = "delete-comment-${comment.id}" }

    init {
        toolTipText = Html.wrappedTooltip(comment.text)
        add(editButton)
        add(deleteButton)
        editButton.addActionListener { onEdit() }
        deleteButton.addActionListener { onDelete() }
        onStateChanged()
    }

    override fun onStateChanged() {
        editButton.isVisible = hovered
        deleteButton.isVisible = hovered
    }

    override fun doLayout() = layoutActions(listOf(editButton, deleteButton))

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            paintRowBackground(g2, Palette.INSET, color)
            val columns = TableColumns(width)
            val left = TableColumns.PAD_LEFT.toFloat()
            val textRight = columns.actions - TableColumns.GAP

            // Line 1: bubble icon, "Comment #1" in the comment color, then the time and duration.
            val lineTop = 5f
            val lineHeight = 16f
            bubble.paintIcon(this, g2, left.toInt(), (lineTop + (lineHeight - bubble.iconHeight) / 2f).toInt())
            val titleFont = UiKit.font(11.5f, UiKit.Weight.BOLD)
            var x = left + bubble.iconWidth + 4
            UiKit.drawText(g2, title, titleFont, color, x, lineTop, lineHeight)
            x += UiKit.textWidth(title, titleFont) + 8
            val metaFont = UiKit.font(11.5f)
            val meta = UiKit.ellipsize("${Timecode.format(comment.startMs)} · ${formatSeconds(comment.durationMs)}", metaFont, textRight - x)
            UiKit.drawText(g2, meta, metaFont, Palette.FG_3, x, lineTop, lineHeight)

            // Line 2: the comment text on one line.
            val textFont = UiKit.font(12.5f)
            val text = UiKit.ellipsize(comment.text.replace('\n', ' '), textFont, textRight - left)
            UiKit.drawText(g2, text, textFont, Palette.FG, left, lineTop + lineHeight, 18f)
        } finally {
            g2.dispose()
        }
    }
}

/**
 * One instant replay under the row of its point (B-50): the replay icon, "Replay 1", the speed, the zoom, and the time
 * that the replay adds. A click opens the replay editor on the replay. Move up, Move down, and Delete
 * show on hover. The replays of a point play in the order of the rows.
 * The row is not a timeline event, so it has no visual index and no selected state.
 */
internal class ReplayRow(
    val pointId: String,
    val replay: ReplayRowDto,
    pointEndMs: Long,
    private val onOpen: () -> Unit,
    onDelete: () -> Unit,
    /** Moves the replay: -1 plays it earlier, 1 plays it later. */
    onMove: (Int) -> Unit = {},
    private val isFirst: Boolean = true,
    private val isLast: Boolean = true,
) : TableRow(NO_INDEX, pointEndMs) {

    override val title = tr("replay.editor.tab", "number" to replay.index + 1)
    override val selectable = false
    override val rowHeight: Int get() = REPLAY_HEIGHT

    private val icon = UiKit.icon(Material2MZ.SLOW_MOTION_VIDEO, 14, Palette.LIME)
    val upButton = RowIconButton(Material2AL.ARROW_UPWARD, tr("replay.moveEarlier"))
        .apply { name = "move-up-replay-$pointId-${replay.index}" }
    val downButton = RowIconButton(Material2AL.ARROW_DOWNWARD, tr("replay.moveLater"))
        .apply { name = "move-down-replay-$pointId-${replay.index}" }
    val deleteButton = RowIconButton(Material2AL.CLOSE, tr("points.replay.row.delete"), danger = true)
        .apply { name = "delete-replay-$pointId-${replay.index}" }

    init {
        name = "replay-row-$pointId-${replay.index}"
        toolTipText = tr("points.replay.row.edit")
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        add(upButton)
        add(downButton)
        add(deleteButton)
        upButton.addActionListener { onMove(-1) }
        downButton.addActionListener { onMove(1) }
        deleteButton.addActionListener { onDelete() }
        onStateChanged()
    }

    /** Opens the replay editor on this replay. */
    fun open() = onOpen()

    override fun onStateChanged() {
        // The first replay cannot move up and the last cannot move down. A hidden button keeps its place.
        upButton.isVisible = hovered && !isFirst
        downButton.isVisible = hovered && !isLast
        deleteButton.isVisible = hovered
    }

    override fun doLayout() = layoutActions(listOf(upButton, downButton, deleteButton))

    /** The speed, the zoom, the still frame, and a muted sound, each with its icon. */
    private val detailItems = ReplayDetails.of(replay.speed, replay.zoomFactor, replay.muted, replay.freezeMs, replay.commentCount)

    /** The details as plain text, for example "0.5× · 2×". */
    internal val details: String get() = ReplayDetails.text(detailItems)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            paintRowBackground(g2, null, null)
            val columns = TableColumns(width)
            val h = height - 1f
            // A short line from the point row above to the icon shows that the replay belongs to the point.
            g2.color = Palette.LIME_DIM_LINE
            val lineX = columns.number + 6
            g2.fillRect(lineX, 0, 1, height / 2)
            g2.fillRect(lineX, height / 2, 6, 1)
            icon.paintIcon(this, g2, lineX + 8, (height - icon.iconHeight) / 2)
            // Start: "Replay 1". Length: the time that the replay adds. After it: the details.
            val textFont = UiKit.font(12f)
            val added = "+" + formatSeconds(replay.addedMs)
            val addedX = columns.lengthRight - UiKit.textWidth(added, textFont)
            val titleFont = UiKit.font(12f, UiKit.Weight.SEMIBOLD)
            val shownTitle = UiKit.ellipsize(title, titleFont, addedX - TITLE_GAP - columns.start)
            UiKit.drawText(g2, shownTitle, titleFont, Palette.LIME, columns.start.toFloat(), 0f, h)
            UiKit.drawText(g2, added, textFont, Palette.FG_3, addedX, 0f, h)
            // A replay row has no marks and no star, so the details can use these columns, up to Delete on hover.
            val metaX = columns.lengthRight + DETAILS_GAP
            val metaRight = if (hovered) columns.actions - TableColumns.GAP else columns.actionsRight
            ReplayDetails.paint(g2, detailItems, textFont, Palette.FG_3, metaX, 0f, h, metaRight)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val REPLAY_HEIGHT = 30
        private const val TITLE_GAP = 6
        private const val DETAILS_GAP = 12
    }
}
