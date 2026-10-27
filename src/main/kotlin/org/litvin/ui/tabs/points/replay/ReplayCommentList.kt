package org.litvin.ui.tabs.points.replay

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.i18n.tr
import org.litvin.points.CommentV1
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.RowIconButton
import org.litvin.ui.commons.UiKit
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * The comments of the selected replay in the replay editor (decision 24), one row under the other, in time order.
 * A click on a row selects the comment, and a click on the selected row clears the selection. A double click edits
 * the comment. Each row has Edit and Delete. The selected row always shows these buttons. The other rows show them on
 * hover.
 */
internal class ReplayCommentList(
    private val onSelect: (Int?) -> Unit,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
    /** The time text of a comment, for example "+0.4 s · 2 s". */
    private val timeText: (CommentV1) -> String,
) : JPanel() {

    init {
        name = "replay-comment-list"
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)
    }

    /** Shows [comments] in time order and selects the comment [selectedId], or no comment when it is null. */
    fun setState(comments: List<CommentV1>, selectedId: Int?) {
        removeAll()
        comments.sortedWith(compareBy<CommentV1> { it.startMs }.thenBy { it.id }).forEachIndexed { index, comment ->
            if (index > 0) add(Box.createVerticalStrut(2))
            add(Row(comment, comment.id == selectedId))
        }
        isVisible = comments.isNotEmpty()
        revalidate()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 6, Palette.INSET, Palette.LINE_2)
        } finally {
            g2.dispose()
        }
    }

    private inner class Row(private val comment: CommentV1, private val selected: Boolean) : JPanel(null) {
        private val time = timeText(comment)
        private var hovered = false
        private val editButton = RowIconButton(Material2AL.EDIT, tr("replay.editor.comments.edit"), side = BUTTON)
            .apply { name = "replay-comment-edit-${comment.id}" }
        private val deleteButton = RowIconButton(Material2AL.CLOSE, tr("replay.editor.comments.delete"), danger = true, side = BUTTON)
            .apply { name = "replay-comment-delete-${comment.id}" }
        private val buttons = listOf(editButton, deleteButton)
        private val color: Color = runCatching { Color.decode(comment.colorHex) }.getOrDefault(Palette.FG)
        private val icon = UiKit.icon(Material2AL.CHAT_BUBBLE, 14, color)

        init {
            name = "replay-comment-row-${comment.id}"
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = comment.text
            buttons.forEach { add(it) }
            editButton.addActionListener { onEdit(comment.id) }
            deleteButton.addActionListener { onDelete(comment.id) }
            val mouse = object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) = setHovered(true)

                // The pointer can go from the row onto one of its buttons. The row stays hovered then.
                override fun mouseExited(e: MouseEvent) = setHovered(getMousePosition(true) != null)

                override fun mouseClicked(e: MouseEvent) {
                    if (e.source !== this@Row) return
                    if (e.clickCount >= 2) onEdit(comment.id) else onSelect(if (selected) null else comment.id)
                }
            }
            addMouseListener(mouse)
            buttons.forEach { it.addMouseListener(mouse) }
            refreshButtons()
        }

        private val buttonsShown get() = selected || hovered

        private fun setHovered(value: Boolean) {
            if (hovered == value) return
            hovered = value
            refreshButtons()
            repaint()
        }

        private fun refreshButtons() {
            buttons.forEach { it.isVisible = buttonsShown }
        }

        override fun getPreferredSize() = Dimension(super.getPreferredSize().width, ROW_HEIGHT)
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, ROW_HEIGHT)
        override fun getMinimumSize() = Dimension(0, ROW_HEIGHT)

        override fun doLayout() {
            val top = (height - BUTTON) / 2
            buttons.forEachIndexed { i, button ->
                button.setBounds(width - END_GAP - (buttons.size - i) * (BUTTON + BUTTON_GAP) + BUTTON_GAP, top, BUTTON, BUTTON)
            }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                when {
                    selected -> UiKit.paintBox(g2, 0, 0, width, height, 4, Palette.RAISED_2, Palette.LIME_LINE)
                    hovered -> UiKit.paintBox(g2, 0, 0, width, height, 4, Palette.OVERLAY, null)
                }
                val h = height.toFloat()
                var x = TEXT_START
                icon.paintIcon(this, g2, x, (height - icon.iconHeight) / 2)
                x += icon.iconWidth + 8
                // The time is at the right, before the buttons. The text gets the room that is left.
                val right = if (buttonsShown) editButton.x - BUTTON_GAP else width - END_GAP
                val timeFont = UiKit.font(12f)
                val timeWidth = UiKit.textWidth(time, timeFont).toInt()
                UiKit.drawText(g2, time, timeFont, Palette.FG_3, (right - timeWidth).toFloat(), 0f, h)
                val textFont = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
                val room = (right - timeWidth - TIME_GAP - x).toFloat()
                if (room > 0f) {
                    val line = comment.text.lineSequence().firstOrNull().orEmpty()
                    val text = UiKit.ellipsize(line, textFont, room)
                    UiKit.drawText(g2, text, textFont, if (selected || hovered) Palette.FG else Palette.FG_2, x.toFloat(), 0f, h)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val PAD = 4
        const val ROW_HEIGHT = 30
        const val BUTTON = 24
        const val BUTTON_GAP = 2
        const val END_GAP = 4
        const val TEXT_START = 10
        const val TIME_GAP = 8
    }
}
