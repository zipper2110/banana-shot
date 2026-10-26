package org.litvin.ui.tabs.points.replay

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.i18n.tr
import org.litvin.points.ReplayV1
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.RowIconButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.ui.ReplayDetails
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
 * The replays of the point in the replay editor, one row under the other, in the play order (B-50 decision 17).
 * A click on a row selects the replay. Each row has Move up, Move down, and Delete. The selected row always shows
 * these buttons. The other rows show them on hover.
 */
internal class ReplayList(
    private val onSelect: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
    /** Moves the replay at the index: -1 plays it earlier, 1 plays it later. */
    private val onMove: (Int, Int) -> Unit,
) : JPanel() {

    init {
        name = "replay-list"
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)
    }

    /** Shows [replays] and selects the row at [selectedIndex], or no row when it is -1. */
    fun setState(replays: List<ReplayV1>, selectedIndex: Int) {
        removeAll()
        replays.forEachIndexed { index, replay ->
            if (index > 0) add(Box.createVerticalStrut(2))
            add(Row(index, replay, index == selectedIndex, isFirst = index == 0, isLast = index == replays.lastIndex))
        }
        isVisible = replays.isNotEmpty()
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

    private inner class Row(
        private val index: Int,
        replay: ReplayV1,
        private val selected: Boolean,
        private val isFirst: Boolean,
        private val isLast: Boolean,
    ) : JPanel(null) {
        private val title = tr("replay.editor.tab", "number" to index + 1)
        private val details = ReplayDetails.of(
            speed = replay.speed,
            repeats = replay.repeats,
            zoomFactor = replay.zoom?.let { 1.0 / it.width },
            muted = replay.muted,
        )
        private var hovered = false
        private val upButton = RowIconButton(Material2AL.ARROW_UPWARD, tr("replay.moveEarlier"), side = BUTTON)
            .apply { name = "replay-list-up-$index" }
        private val downButton = RowIconButton(Material2AL.ARROW_DOWNWARD, tr("replay.moveLater"), side = BUTTON)
            .apply { name = "replay-list-down-$index" }
        private val deleteButton = RowIconButton(Material2AL.CLOSE, tr("points.replay.row.delete"), danger = true, side = BUTTON)
            .apply { name = "replay-list-delete-$index" }
        private val buttons = listOf(upButton, downButton, deleteButton)
        private val icon = UiKit.icon(Material2MZ.SLOW_MOTION_VIDEO, 14, if (selected) Palette.LIME else Palette.FG_3)

        init {
            name = "replay-list-row-$index"
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = LEFT_ALIGNMENT
            buttons.forEach { add(it) }
            upButton.addActionListener { onMove(index, -1) }
            downButton.addActionListener { onMove(index, 1) }
            deleteButton.addActionListener { onDelete(index) }
            val mouse = object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) = setHovered(true)

                // The pointer can go from the row onto one of its buttons. The row stays hovered then.
                override fun mouseExited(e: MouseEvent) = setHovered(getMousePosition(true) != null)

                override fun mouseClicked(e: MouseEvent) {
                    if (e.source === this@Row) onSelect(index)
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

        /** The first replay cannot move up and the last cannot move down. A hidden button keeps its place. */
        private fun refreshButtons() {
            upButton.isVisible = buttonsShown && !isFirst
            downButton.isVisible = buttonsShown && !isLast
            deleteButton.isVisible = buttonsShown
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
                val titleFont = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
                UiKit.drawText(g2, title, titleFont, if (selected || hovered) Palette.FG else Palette.FG_2, x.toFloat(), 0f, h)
                x += UiKit.textWidth(title, titleFont).toInt() + 10
                // The details use the room up to the buttons when the buttons show.
                val right = if (buttonsShown) upButton.x - BUTTON_GAP else width - END_GAP
                val detailsFont = UiKit.font(12f)
                ReplayDetails.paint(g2, details, detailsFont, Palette.FG_3, x, 0f, h, right)
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val PAD = 4
        const val ROW_HEIGHT = 32
        const val BUTTON = 24
        const val BUTTON_GAP = 2
        const val END_GAP = 4
        const val TEXT_START = 10
    }
}
