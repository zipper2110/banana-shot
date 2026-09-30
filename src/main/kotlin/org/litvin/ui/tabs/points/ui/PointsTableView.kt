package org.litvin.ui.tabs.points.ui

import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.applyDarkScrollbar
import org.litvin.ui.tabs.points.CommentDto
import org.litvin.ui.tabs.points.PointEventDto
import org.litvin.ui.tabs.points.PointsActions
import org.litvin.ui.tabs.points.PointsViewState
import org.litvin.ui.tabs.points.TimelineEventDto
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingUtilities

/**
 * The points table of the side column: one row for each marked point and each comment, in time order.
 * A click on a row selects it and seeks to its start. The pending point is not a row; [MarkPanel] shows it.
 */
class PointsTableView(
    private val actions: PointsActions,
) : JPanel(BorderLayout()) {

    private val header = TableHeaderRow()
    private val rowsPanel = RowsPanel()
    private val scroll = JScrollPane(rowsPanel).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val rows = mutableListOf<TableRow>()
    private var lastState: PointsViewState? = null
    private var hoveredRow: TableRow? = null

    private val hoverListener = object : MouseAdapter() {
        override fun mouseEntered(event: MouseEvent) {
            rowOf(event.component)?.let(::setHovered)
        }

        override fun mouseExited(event: MouseEvent) {
            val row = rowOf(event.component) ?: return
            // The pointer can go from the row into one of its buttons. Keep the hover while it is inside the row.
            SwingUtilities.invokeLater {
                val inside = runCatching {
                    val pointer = MouseInfo.getPointerInfo()?.location ?: return@runCatching false
                    row.isShowing && Rectangle(row.locationOnScreen, row.size).contains(pointer)
                }.getOrDefault(false)
                if (!inside && hoveredRow === row) setHovered(null)
            }
        }

        override fun mouseClicked(event: MouseEvent) {
            if (!SwingUtilities.isLeftMouseButton(event)) return
            val row = event.component as? TableRow ?: return
            actions.selectByVisualIndex(row.visualIndex)
            actions.seekTo(row.startMs)
        }
    }

    init {
        isOpaque = true
        background = Palette.BG
        add(header, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)
        runCatching { applyDarkScrollbar(scroll, Palette.BG) }
        scroll.verticalScrollBar.unitIncrement = TableRow.HEIGHT
        scroll.verticalScrollBar.addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) = syncHeader()
            override fun componentHidden(e: ComponentEvent) = syncHeader()
            override fun componentResized(e: ComponentEvent) = syncHeader()
        })
    }

    private fun syncHeader() {
        header.scrollBarWidth = if (scroll.verticalScrollBar.isVisible) scroll.verticalScrollBar.width else 0
    }

    fun setState(state: PointsViewState) {
        val previousKeys = lastState?.events.orEmpty().map { it.stableKey }.toSet()
        lastState = state
        val ordered = orderedEvents(state)
        val newIndex = ordered.indexOfLast { it.stableKey !in previousKeys }
            .takeIf { previousKeys.isNotEmpty() && it >= 0 }

        rebuild(state, ordered)
        newIndex?.let { index -> SwingUtilities.invokeLater { scrollToVisualIndex(index) } }
    }

    fun updateSelection(selectedIndex: Int) {
        lastState = lastState?.copy(selectedVisualIndex = selectedIndex)
        rows.forEach { it.selected = it.visualIndex == selectedIndex }
    }

    /** Scrolls the row into view. An index without a row, such as the pending point, does nothing. */
    fun scrollToVisualIndex(index: Int) {
        if (rows.getOrNull(index) == null) return
        // Calculate the rectangle from the index. After a rebuild, the new rows have empty bounds until the next
        // layout, and a scroll to an empty rectangle at (0, 0) moves the list to the top.
        rowsPanel.scrollRectToVisible(Rectangle(0, index * TableRow.HEIGHT, rowsPanel.width, TableRow.HEIGHT))
    }

    internal fun visibleTitles(): List<String> = rows.map { it.title }

    internal fun visibleText(): String = rows.joinToString("\n") {
        when (it) {
            is CommentRow -> it.comment.text
            is PointRow -> it.point.label.orEmpty()
            else -> ""
        }
    }

    /** Title of the selected row, or null when no row shows the selected state. */
    internal fun selectedTitle(): String? = rows.firstOrNull { it.selected && it.selectable }?.title

    internal fun rowCount(): Int = rows.size

    private fun rebuild(state: PointsViewState, ordered: List<TimelineEventDto>) {
        rowsPanel.removeAll()
        rows.clear()
        hoveredRow = null

        var pointNumber = 0
        ordered.forEachIndexed { visualIndex, event ->
            val row = when (event) {
                is PointEventDto -> {
                    pointNumber++
                    val number = pointNumber
                    PointRow(
                        visualIndex = visualIndex,
                        number = number,
                        point = event.point,
                        onToggleFavorite = { actions.toggleFavorite(event.point.id) },
                        onEdit = { EditPointDialog.show(this, event.point, number, actions) },
                        onDelete = {
                            actions.selectByVisualIndex(visualIndex)
                            actions.deletePoint(event.point.id)
                        },
                    )
                }
                is CommentDto -> CommentRow(
                    visualIndex = visualIndex,
                    comment = event,
                    onEdit = { EditCommentDialog.showEdit(this, event, actions) },
                    onDelete = { actions.deleteComment(event.id) },
                )
            }
            row.selected = visualIndex == state.selectedVisualIndex
            row.addMouseListener(hoverListener)
            row.components.forEach { it.addMouseListener(hoverListener) }
            rows += row
            rowsPanel.add(row)
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
        // A click on a star rebuilds the rows. The pointer stays on the new row, but Swing sends no enter event.
        SwingUtilities.invokeLater(::restoreHover)
    }

    private fun restoreHover() {
        val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
        if (!rowsPanel.isShowing) return
        val local = pointer.location.also { SwingUtilities.convertPointFromScreen(it, rowsPanel) }
        if (!rowsPanel.visibleRect.contains(local)) return
        setHovered(rows.firstOrNull { it.bounds.contains(local) })
    }

    private fun setHovered(row: TableRow?) {
        if (hoveredRow === row) return
        hoveredRow?.hovered = false
        hoveredRow = row
        row?.hovered = true
    }

    private fun rowOf(component: Component): TableRow? =
        component as? TableRow ?: SwingUtilities.getAncestorOfClass(TableRow::class.java, component) as? TableRow

    private fun orderedEvents(state: PointsViewState): List<TimelineEventDto> =
        state.events.sortedWith(compareBy<TimelineEventDto> { it.startMs }.thenBy { it.stableKey })

    /** The rows, one under the other. It follows the viewport width and shows the empty text without rows. */
    private inner class RowsPanel : JPanel(null), Scrollable {
        init {
            isOpaque = true
            background = Palette.BG
        }

        override fun doLayout() {
            var y = 0
            for (child in components) {
                child.setBounds(0, y, width, TableRow.HEIGHT)
                y += TableRow.HEIGHT
            }
        }

        override fun getPreferredSize(): Dimension {
            val viewportWidth = (parent as? JViewport)?.width ?: 0
            return Dimension(viewportWidth, if (componentCount == 0) EMPTY_HEIGHT else componentCount * TableRow.HEIGHT)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (componentCount > 0) return
            val g2 = UiKit.smooth(g)
            try {
                val font = UiKit.font(13f)
                UiKit.drawText(g2, EMPTY_TEXT, font, Palette.FG_3, (width - UiKit.textWidth(EMPTY_TEXT, font)) / 2f, 28f, 18f)
            } finally {
                g2.dispose()
            }
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = TableRow.HEIGHT
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }

    private companion object {
        const val EMPTY_TEXT = "No marked points yet."
        const val EMPTY_HEIGHT = 80
    }
}
