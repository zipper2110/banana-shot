package org.litvin.ui.tabs.projects.components

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.ceil
import kotlin.math.max
import org.litvin.i18n.tr
import org.litvin.scoring.Sport
import org.litvin.ui.commons.Palette

/**
 * Column layout of the projects table. The header and each [ProjectRow] use this layout.
 * Thus the cells of all rows align below the column names.
 *
 * The columns are the status icon, the project name, the video path, the sport, the duration, the size,
 * the scored points, the favorite points and the row actions. The name and the path share the free width 1 : 1.6.
 * The sport column and the actions column fit their texts in the current language, for example "Beach tennis"
 * and the Open button in Russian.
 */
internal object ProjectsTableColumns {
    /** The names of the figure columns, in display order. */
    val FIGURE_COLUMNS: List<String>
        get() = listOf(tr("projects.column.duration"), tr("projects.column.size"), tr("projects.column.scored"), tr("projects.column.favorites"))

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
        STATUS to 20, DURATION to 64, SIZE to 76, SCORED to 104, FAVORITES to 48,
    )
    private const val SPORT_MIN = 52
    private const val ACTIONS_MIN = 112

    /** The rename button, the delete button, and the gap before the Open button. */
    private const val ACTION_ICONS = 26 + 26 + 6
    private const val NAME_MIN = 140
    private const val NAME_SHARE = 1.0
    private const val VIDEO_SHARE = 1.6
    private const val COLUMN_GAP = 14
    private const val SIDE_PADDING = 10

    /** The width of the scored bar and the gap before it. The header text of "Scored" aligns with the number. */
    const val SCORED_BAR = 44
    const val SCORED_BAR_GAP = 8

    private const val COLUMN_COUNT = 9

    /** The font of the sport names in the rows. */
    val sportFont: Font get() = ProjectsUi.font(12.5f)

    /** The font of the column names in the header. */
    private val headerFont: Font get() = ProjectsUi.trackedFont(10.5f, 0.08, ProjectsUi.Weight.SEMIBOLD)

    /** The width of the sport column: the longest sport name or the column name, in the current language. */
    private fun sportWidth(): Int {
        val longestName = Sport.entries.maxOf { ProjectsUi.textWidth(it.title, sportFont) }
        val columnName = ProjectsUi.textWidth(tr("projects.column.sport").uppercase(), headerFont)
        return maxOf(SPORT_MIN, ceil(maxOf(longestName, columnName)).toInt() + 2)
    }

    /** Makes the Open button of a row. The actions column measures this button. */
    fun openButton(): ProjectsButton = ProjectsButton(tr("common.open"), buttonHeight = 26, fontSize = 12f, limeHover = true)

    /** The text and the width of the last measured actions column. The layout runs often, so it keeps the width. */
    private var actionsCache: Pair<String, Int>? = null

    /** The width of the actions column: the two icon buttons and the Open button, in the current language. */
    private fun actionsWidth(): Int {
        val text = tr("common.open")
        actionsCache?.let { (cachedText, width) -> if (cachedText == text) return width }
        return maxOf(ACTIONS_MIN, ACTION_ICONS + openButton().preferredSize.width).also { actionsCache = text to it }
    }

    private fun fixedWidth(): Int = FIXED.values.sum() + sportWidth() + actionsWidth()

    /** The smallest width that shows all columns. */
    val minimumWidth: Int get() = fixedWidth() + NAME_MIN + COLUMN_GAP * (COLUMN_COUNT - 1) + SIDE_PADDING * 2

    /** Returns the left edge and the width of each column for a row of [width] pixels. */
    fun columns(width: Int): List<Pair<Int, Int>> {
        val sport = sportWidth()
        val actions = actionsWidth()
        val free = max(0, width - SIDE_PADDING * 2 - COLUMN_GAP * (COLUMN_COUNT - 1) - FIXED.values.sum() - sport - actions)
        // As minmax(140px, 1fr) in the design. In a narrow window the name gets at most half, so the path stays visible.
        val nameWidth = maxOf((free * NAME_SHARE / (NAME_SHARE + VIDEO_SHARE)).toInt(), minOf(NAME_MIN, free / 2))
        val videoWidth = free - nameWidth
        var x = SIDE_PADDING
        return (0 until COLUMN_COUNT).map { column ->
            val w = when (column) {
                NAME -> nameWidth
                VIDEO -> videoWidth
                SPORT -> sport
                ACTIONS -> actions
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
            add(HeaderText(tr("projects.column.project"), SwingConstants.LEFT))
            add(HeaderText(tr("projects.column.video"), SwingConstants.LEFT))
            add(HeaderText(tr("projects.column.sport"), SwingConstants.LEFT))
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
        private val headerFont = ProjectsTableColumns.headerFont

        override fun getPreferredSize() = Dimension(ceil(ProjectsUi.textWidth(shown, headerFont)).toInt() + 2, HEADER_HEIGHT)

        override fun paintComponent(g: Graphics) {
            val g2 = ProjectsUi.smooth(g)
            try {
                val textWidth = ProjectsUi.textWidth(shown, headerFont)
                // A long name (for example in Spanish) does not fit beside the inset. It then moves over the inset.
                val x = when {
                    align != SwingConstants.RIGHT -> 0f
                    textWidth <= width - rightInset -> width - rightInset - textWidth
                    else -> maxOf(0f, width - textWidth)
                }
                ProjectsUi.drawText(g2, shown, headerFont, Palette.FG_3, x, 0f, height.toFloat())
            } finally {
                g2.dispose()
            }
        }
    }

    private const val HEADER_HEIGHT = 30
}
