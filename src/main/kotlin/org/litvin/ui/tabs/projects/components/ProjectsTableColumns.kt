package org.litvin.ui.tabs.projects.components

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.ceil
import kotlin.math.max
import org.litvin.ui.commons.Palette

/**
 * Column layout of the projects table. The header and each [ProjectRow] use this layout.
 * Thus the cells of all rows align below the column names.
 *
 * The columns are the status icon, the project name, the video path, the sport, the duration, the size,
 * the scored points, the favorite points and the row actions. The name and the path share the free width 1 : 1.6.
 */
internal object ProjectsTableColumns {
    /** The names of the figure columns, in display order. */
    val FIGURE_COLUMNS = listOf("Duration", "Size", "Scored", "Fav.")

    const val STATUS = 0
    const val NAME = 1
    const val VIDEO = 2
    const val SPORT = 3
    const val DURATION = 4
    const val SIZE = 5
    const val SCORED = 6
    const val FAVORITES = 7
    const val ACTIONS = 8

    private val FIXED = mapOf(
        STATUS to 20, SPORT to 52, DURATION to 64, SIZE to 76, SCORED to 104, FAVORITES to 48, ACTIONS to 112,
    )
    private const val NAME_MIN = 140
    private const val NAME_SHARE = 1.0
    private const val VIDEO_SHARE = 1.6
    private const val COLUMN_GAP = 14
    private const val SIDE_PADDING = 10

    /** The width of the scored bar and the gap before it. The header text of "Scored" aligns with the number. */
    const val SCORED_BAR = 44
    const val SCORED_BAR_GAP = 8

    private const val COLUMN_COUNT = 9

    /** The smallest width that shows all columns. */
    val minimumWidth: Int = FIXED.values.sum() + NAME_MIN + COLUMN_GAP * (COLUMN_COUNT - 1) + SIDE_PADDING * 2

    /** Returns the left edge and the width of each column for a row of [width] pixels. */
    fun columns(width: Int): List<Pair<Int, Int>> {
        val free = max(0, width - SIDE_PADDING * 2 - COLUMN_GAP * (COLUMN_COUNT - 1) - FIXED.values.sum())
        // As minmax(140px, 1fr) in the design. In a narrow window the name gets at most half, so the path stays visible.
        val nameWidth = maxOf((free * NAME_SHARE / (NAME_SHARE + VIDEO_SHARE)).toInt(), minOf(NAME_MIN, free / 2))
        val videoWidth = free - nameWidth
        var x = SIDE_PADDING
        return (0 until COLUMN_COUNT).map { column ->
            val w = when (column) {
                NAME -> nameWidth
                VIDEO -> videoWidth
                else -> FIXED.getValue(column)
            }
            (x to w).also { x += w + COLUMN_GAP }
        }
    }

    /** Places the children of a row in the columns, in the order of the columns. Each child is as tall as the row. */
    class Layout(private val rowHeight: Int) : LayoutManager {
        override fun addLayoutComponent(name: String?, comp: Component?) = Unit
        override fun removeLayoutComponent(comp: Component?) = Unit
        override fun preferredLayoutSize(parent: Container) = Dimension(minimumWidth, rowHeight)
        override fun minimumLayoutSize(parent: Container) = Dimension(minimumWidth, rowHeight)

        override fun layoutContainer(parent: Container) {
            val columns = columns(parent.width)
            parent.components.forEachIndexed { index, child ->
                val (x, w) = columns.getOrNull(index) ?: return@forEachIndexed
                child.setBounds(x, 0, w, parent.height - 1)
            }
        }
    }

    /** Makes the header row. */
    fun header(): JComponent = HeaderRow()

    private class HeaderRow : JPanel(Layout(HEADER_HEIGHT)) {
        init {
            isOpaque = true
            background = Palette.BG
            name = "projects-table-header"
            add(Gap(0, 0))
            add(HeaderText("Project", SwingConstants.LEFT))
            add(HeaderText("Video", SwingConstants.LEFT))
            add(HeaderText("Sport", SwingConstants.LEFT))
            add(HeaderText(FIGURE_COLUMNS[0], SwingConstants.RIGHT))
            add(HeaderText(FIGURE_COLUMNS[1], SwingConstants.RIGHT))
            add(HeaderText(FIGURE_COLUMNS[2], SwingConstants.RIGHT, SCORED_BAR + SCORED_BAR_GAP))
            add(HeaderText(FIGURE_COLUMNS[3], SwingConstants.RIGHT))
            add(Gap(0, 0))
        }

        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, HEADER_HEIGHT)

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.color = Palette.LINE_2
            g.fillRect(0, height - 1, width, 1)
        }
    }

    /** A column name in small capital letters. [rightInset] moves a right-aligned name away from the right edge. */
    private class HeaderText(text: String, private val align: Int, private val rightInset: Int = 0) : JComponent() {
        private val shown = text.uppercase()
        private val headerFont = ProjectsUi.trackedFont(10.5f, 0.08, ProjectsUi.Weight.SEMIBOLD)

        override fun getPreferredSize() = Dimension(ceil(ProjectsUi.textWidth(shown, headerFont)).toInt() + 2, HEADER_HEIGHT)

        override fun paintComponent(g: Graphics) {
            val g2 = ProjectsUi.smooth(g)
            try {
                val textWidth = ProjectsUi.textWidth(shown, headerFont)
                val x = if (align == SwingConstants.RIGHT) width - rightInset - textWidth else 0f
                ProjectsUi.drawText(g2, shown, headerFont, Palette.FG_3, x, 0f, height.toFloat())
            } finally {
                g2.dispose()
            }
        }
    }

    private const val HEADER_HEIGHT = 30
}
