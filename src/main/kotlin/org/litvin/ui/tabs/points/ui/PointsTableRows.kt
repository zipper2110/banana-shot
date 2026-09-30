package org.litvin.ui.tabs.points.ui

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
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The columns of the points table: #, Start, End, Length, ★ and the row actions.
 * The widths come from the design: 34 px, 70 px, 70 px, the rest, 24 px and 54 px, with 5 px gaps.
 */
internal data class TableColumns(val width: Int) {
    val number = PAD_LEFT
    val start = number + 34 + GAP
    val end = start + 70 + GAP
    val length = end + 70 + GAP
    val actionsRight = width - PAD_RIGHT
    val actions = actionsRight - ACTIONS
    val star = actions - GAP - STAR
    val lengthRight = star - GAP

    companion object {
        const val PAD_LEFT = 14
        const val PAD_RIGHT = 8
        const val GAP = 5
        const val STAR = 24
        const val ACTIONS = 54
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
            caption("START", columns.start.toFloat())
            caption("END", columns.end.toFloat())
            caption("LENGTH", columns.lengthRight - UiKit.textWidth("LENGTH", captionFont))
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

    protected open fun onStateChanged() = Unit

    init {
        isOpaque = false
    }

    override fun getPreferredSize() = Dimension(0, HEIGHT)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, HEIGHT)
    override fun getMinimumSize() = Dimension(0, HEIGHT)

    protected fun paintRowBackground(g2: Graphics2D, rest: Color?, bar: Color?) {
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
        g2.fillRect(0, height - 1, width, 1)
        if (bar != null) {
            g2.color = bar
            g2.fillRect(0, 0, 3, height)
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
    }
}

/** One marked point: number, start, end, length, the favorite star, and Edit and Delete on hover. */
internal class PointRow(
    visualIndex: Int,
    private val number: Int,
    val point: PointDto,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) : TableRow(visualIndex, point.startMs) {

    override val title = "#$number"
    override val selectable = true

    val favoriteButton = RowIconButton(
        if (point.favorite) Material2MZ.STAR else Material2MZ.STAR_BORDER,
        "Favorite [A]",
        color = if (point.favorite) Palette.YELLOW else Palette.FG_3,
        side = 22,
    ).apply { name = "favorite-point-${point.id}" }
    val editButton = RowIconButton(Material2AL.EDIT, "Edit times/label").apply { name = "edit-point-${point.id}" }
    val deleteButton = RowIconButton(Material2AL.CLOSE, "Delete point", danger = true).apply { name = "delete-point-${point.id}" }

    private val textFont get() = UiKit.font(12.5f)
    private val numberFont get() = UiKit.font(12.5f, UiKit.Weight.BOLD)

    init {
        add(favoriteButton)
        add(editButton)
        add(deleteButton)
        favoriteButton.addActionListener { onToggleFavorite() }
        editButton.addActionListener { onEdit() }
        deleteButton.addActionListener { onDelete() }
        onStateChanged()
    }

    override fun onStateChanged() {
        // The star of a favorite stays visible. The other buttons show only while the pointer is over the row.
        favoriteButton.isVisible = point.favorite || hovered
        editButton.isVisible = hovered
        deleteButton.isVisible = hovered
    }

    override fun doLayout() {
        val columns = TableColumns(width)
        val star = favoriteButton.preferredSize
        favoriteButton.setBounds(columns.star + (TableColumns.STAR - star.width) / 2, (height - star.height) / 2, star.width, star.height)
        layoutActions(listOf(editButton, deleteButton))
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val showSelection = selected
            paintRowBackground(g2, null, if (showSelection) Palette.LIME else null)
            val columns = TableColumns(width)
            val h = height - 1f
            UiKit.drawText(g2, number.toString(), numberFont, if (showSelection) Palette.LIME else Palette.FG_2, columns.number.toFloat(), 0f, h)
            UiKit.drawText(g2, Timecode.format(point.startMs), textFont, Palette.FG, columns.start.toFloat(), 0f, h)
            UiKit.drawText(g2, point.endMs?.let(Timecode::format) ?: "—", textFont, Palette.FG, columns.end.toFloat(), 0f, h)
            val length = point.endMs?.let { formatSeconds(it - point.startMs) } ?: ""
            UiKit.drawText(g2, length, textFont, Palette.FG_3, columns.lengthRight - UiKit.textWidth(length, textFont), 0f, h)
        } finally {
            g2.dispose()
        }
    }
}

/** One comment: a color bar, the number, the time and duration, one line of text, and Edit and Delete on hover. */
internal class CommentRow(
    visualIndex: Int,
    val comment: CommentDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) : TableRow(visualIndex, comment.startMs) {

    override val title = "Comment #${comment.id}"
    override val selectable = false

    private val color: Color = runCatching { Color.decode(comment.colorHex) }.getOrDefault(Palette.FG)
    private val bubble = UiKit.icon(Material2AL.CHAT_BUBBLE, 14, color)
    val editButton = RowIconButton(Material2AL.EDIT, "Edit comment").apply { name = "edit-comment-${comment.id}" }
    val deleteButton = RowIconButton(Material2AL.CLOSE, "Delete comment", danger = true).apply { name = "delete-comment-${comment.id}" }

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
