package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.Html
import org.litvin.ui.commons.Palette
import org.litvin.ui.tabs.projects.presenter.ProjectCardState
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * One row of the projects table: the status icon, the name, the video path, the figures and the row actions.
 * The open project has a lime mark on the left. It has no Open button.
 * A click on the row of a different project opens that project. The row then shows the hand cursor.
 * [showStats] changes the figures in place, so the buttons stay the same while the presenter loads the figures.
 */
internal class ProjectRow(
    project: ProjectCardState,
    private val isCurrent: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) : JPanel(ProjectsTableColumns.Layout(ROW_HEIGHT)) {
    private var project = project

    /** True while the pointer is over the row or over one of its children. */
    private val hovered get() = isShowing && getMousePosition(true) != null

    private val mouse = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) = repaint()
        override fun mouseExited(e: MouseEvent) = repaint()
        override fun mouseClicked(e: MouseEvent) {
            // A double-click also sends a first event with one click, so only that event opens the project.
            if (e.clickCount != 1 || !SwingUtilities.isLeftMouseButton(e) || e.component is AbstractButton) return
            openButton?.doClick()
        }
    }

    /** The Open button. The open project has no Open button. */
    private val openButton: JButton? = if (isCurrent) {
        null
    } else {
        ProjectsButton("Open", buttonHeight = 26, fontSize = 12f, limeHover = true).apply {
            name = "projects-open-${project.id}"
            toolTipText = "Open the project and continue in the Points tab. A click on the row also opens it."
            addActionListener { onOpen() }
        }
    }

    init {
        isOpaque = false
        name = "projects-row-${project.id}"
        alignmentX = Component.LEFT_ALIGNMENT
        // The cells do not set a cursor, so they show the cursor of the row.
        if (!isCurrent) cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        add(JLabel(project.name).apply {
            name = "projects-name-${project.id}"
            font = ProjectsUi.font(13f, ProjectsUi.Weight.SEMIBOLD)
            foreground = Palette.FG
            toolTipText = project.name
        })
        add(actions(project, onRename, onDelete))
        listenDeep(this, mouse)
        addStatsCells()
    }

    /** True when this row shows [other] with the same name, path and open state. The figures can be different. */
    fun shows(other: ProjectCardState, current: Boolean): Boolean =
        current == isCurrent && other.copy(stats = null) == project.copy(stats = null)

    /** Replaces the cells that depend on the figures: the status icon, the path and the figure columns. */
    fun showStats(other: ProjectCardState) {
        if (other.stats == project.stats) return
        project = other
        STATS_COLUMNS.sortedDescending().forEach { remove(it) }
        addStatsCells()
        revalidate()
        repaint()
    }

    private fun addStatsCells() {
        val stats = project.stats
        val id = project.id
        val cells = listOf(
            ProjectsTableColumns.STATUS to statusCell(),
            ProjectsTableColumns.VIDEO to PathCell(project.secondary, stats?.videoMissingMessage).apply { name = "projects-video-$id" },
            ProjectsTableColumns.SPORT to SportCell(stats?.sport).apply { name = "projects-sport-$id" },
            ProjectsTableColumns.DURATION to FigureCell(stats?.duration, skeletonWidth = 44).apply { name = "projects-duration-$id" },
            ProjectsTableColumns.SIZE to FigureCell(stats?.fileSize, skeletonWidth = 56).apply { name = "projects-size-$id" },
            ProjectsTableColumns.SCORED to ScoredCell(stats?.scoredCount, stats?.pointCount, stats?.scoredPoints)
                .apply { name = "projects-scored-$id" },
            ProjectsTableColumns.FAVORITES to FavoritesCell(stats?.favoritePoints).apply { name = "projects-favorites-$id" },
        )
        // The layout places the children by index, so each cell goes to the index of its column.
        cells.forEach { (column, cell) ->
            add(cell, column)
            cell.addMouseListener(mouse)
        }
    }

    override fun getPreferredSize() = Dimension(ProjectsTableColumns.minimumWidth, ROW_HEIGHT)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, ROW_HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            when {
                isCurrent -> {
                    g2.color = Palette.LIME_WASH
                    g2.fillRect(0, 0, width, height)
                    ProjectsUi.paintBox(g2, -3, 8, 6, height - 16, 3, Palette.LIME, null)
                }
                hovered -> {
                    g2.color = Palette.ROW_HOVER
                    g2.fillRect(0, 0, width, height)
                }
            }
            g2.color = Palette.ROW_LINE
            g2.fillRect(0, height - 1, width, 1)
        } finally {
            g2.dispose()
        }
    }

    private fun statusCell(): JComponent {
        val missingMessage = project.stats?.videoMissingMessage
        val (ikon: Ikon, color: Color, tip: String?) = when {
            isCurrent -> Triple(Material2MZ.PLAY_CIRCLE_FILLED, Palette.LIME, "Open project")
            missingMessage != null -> Triple(Material2OutlinedMZ.VIDEOCAM_OFF, Palette.RED, missingMessage)
            !project.hasVideo -> Triple(Material2OutlinedAL.DESCRIPTION, Palette.FG_3, null)
            else -> Triple(Material2OutlinedMZ.MOVIE, Palette.FG_3, null)
        }
        return JLabel(ProjectsUi.icon(ikon, 17, color)).apply {
            horizontalAlignment = SwingConstants.LEFT
            toolTipText = tip
            if (missingMessage != null) name = "projects-video-missing-${project.id}"
        }
    }

    private fun actions(project: ProjectCardState, onRename: () -> Unit, onDelete: () -> Unit): JComponent {
        val iconColor = { if (hovered) Palette.FG_2 else Palette.FG_3 }
        val rename = ProjectsIconButton(Material2AL.EDIT, "Rename project", restColor = iconColor).apply {
            name = "projects-rename-${project.id}"
            addActionListener { onRename() }
        }
        val delete = ProjectsIconButton(Material2AL.DELETE, "Delete project", restColor = iconColor).apply {
            name = "projects-delete-${project.id}"
            addActionListener { onDelete() }
            if (isCurrent) {
                isEnabled = false
                toolTipText = "You cannot delete the open project."
            }
        }
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())
            add(rename)
            add(delete)
            openButton?.let {
                add(Box.createHorizontalStrut(6))
                add(it)
            }
        }
    }

    private companion object {
        const val ROW_HEIGHT = 44
        val STATS_COLUMNS = listOf(
            ProjectsTableColumns.STATUS,
            ProjectsTableColumns.VIDEO,
            ProjectsTableColumns.SPORT,
            ProjectsTableColumns.DURATION,
            ProjectsTableColumns.SIZE,
            ProjectsTableColumns.SCORED,
            ProjectsTableColumns.FAVORITES,
        )

        /** Adds [listener] to [c] and to all its children. Buttons get it too, for the hover of the row. */
        fun listenDeep(c: Component, listener: MouseAdapter) {
            c.addMouseListener(listener)
            if (c is Container) c.components.forEach { listenDeep(it, listener) }
        }
    }
}

/**
 * The video path of a row: one absolute path, folder first. A long path loses the start of the folder,
 * so the file name stays visible. The tooltip shows the full path.
 * For a missing video, the cell starts with "Video not found" in red.
 */
internal class PathCell(private val path: String, private val missingMessage: String?) : JLabel(path) {
    private val pathFont = ProjectsUi.font(12f)
    private val missFont = ProjectsUi.font(12f, ProjectsUi.Weight.SEMIBOLD)

    init {
        toolTipText = if (missingMessage != null) {
            "<html>${Html.escapeHtml(missingMessage)}<br>${Html.escapeHtml(path)}</html>"
        } else {
            path
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            var x = 0f
            if (missingMessage != null) {
                ProjectsUi.drawText(g2, MISSING, missFont, Palette.RED, x, 0f, height.toFloat())
                x += ProjectsUi.textWidth(MISSING, missFont) + 6f
            }
            val shown = ProjectsUi.ellipsizeStart(path, pathFont, width - x - 1f)
            val color = if (missingMessage != null) Palette.FG_3 else Palette.FG_2
            ProjectsUi.drawText(g2, shown, pathFont, color, x, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val MISSING = "Video not found"
    }
}

/**
 * A right-aligned figure. A null text shows a gray placeholder bar while the presenter loads the figures.
 * The dash of an unknown value is gray.
 */
internal open class FigureCell(
    text: String?,
    private val skeletonWidth: Int,
    private val figureIcon: Icon? = null,
) : JLabel(text.orEmpty()) {
    protected val loading = text == null
    protected val figureFont: Font = ProjectsUi.font(12.5f)

    init {
        horizontalAlignment = SwingConstants.RIGHT
    }

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            var right = width.toFloat()
            if (loading) {
                ProjectsUi.paintBox(g2, width - skeletonWidth, (height - 10) / 2, skeletonWidth, 10, 3, Palette.LINE, null)
                right -= skeletonWidth + 3
            } else {
                val value = text.orEmpty()
                val textWidth = ProjectsUi.textWidth(value, figureFont)
                right -= textWidth
                val color = if (value == UNKNOWN) Palette.FG_3 else Palette.FG
                ProjectsUi.drawText(g2, value, figureFont, color, right, 0f, height.toFloat())
                right -= 3
            }
            figureIcon?.let { it.paintIcon(this, g2, (right - it.iconWidth).toInt(), (height - it.iconHeight) / 2) }
        } finally {
            g2.dispose()
        }
    }
}

/** The sport of the project, left-aligned. A null text shows a gray placeholder bar while the presenter loads it. */
internal class SportCell(text: String?) : JLabel(text.orEmpty()) {
    private val loading = text == null
    private val cellFont: Font = ProjectsUi.font(12.5f)

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            if (loading) {
                ProjectsUi.paintBox(g2, 0, (height - 10) / 2, 40, 10, 3, Palette.LINE, null)
            } else {
                ProjectsUi.drawText(g2, text.orEmpty(), cellFont, Palette.FG_2, 0f, 0f, height.toFloat())
            }
        } finally {
            g2.dispose()
        }
    }
}

/** The text of an unknown figure, for example the duration of a missing video. */
private const val UNKNOWN = "—"

/** The favorite points with a yellow star. The star is gray when the project has no favorite points. */
internal class FavoritesCell(text: String?) : FigureCell(
    text,
    skeletonWidth = 16,
    figureIcon = ProjectsUi.icon(Material2MZ.STAR, 14, if (text == null || text == "0") Palette.FG_3 else Palette.YELLOW),
)

/**
 * The scored points of all points, for example "96/128", and a thin progress bar.
 * The bar is lime when all points have a score, and sage when some points have a score.
 */
internal class ScoredCell(
    private val scored: Int?,
    private val total: Int?,
    text: String?,
) : JLabel(text.orEmpty()) {
    private val figureFont = ProjectsUi.font(12.5f)

    init {
        if (scored != null && total != null) toolTipText = "$scored of $total points have a score"
    }

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            val barX = width - ProjectsTableColumns.SCORED_BAR
            val barY = (height - 4) / 2
            ProjectsUi.paintBox(g2, barX, barY, ProjectsTableColumns.SCORED_BAR, 4, 2, Palette.TRACK, null)
            val numberRight = (barX - ProjectsTableColumns.SCORED_BAR_GAP).toFloat()
            if (scored == null || total == null) {
                ProjectsUi.paintBox(g2, numberRight.toInt() - 40, (height - 10) / 2, 40, 10, 3, Palette.LINE, null)
                return
            }
            if (total > 0 && scored > 0) {
                val fill = (ProjectsTableColumns.SCORED_BAR * scored.coerceAtMost(total) / total.toFloat()).toInt().coerceAtLeast(2)
                val color = if (scored >= total) Palette.LIME else Palette.SAGE
                ProjectsUi.paintBox(g2, barX, barY, fill, 4, 2, color, null)
            }
            val suffix = "/$total"
            val suffixWidth = ProjectsUi.textWidth(suffix, figureFont)
            val main = scored.toString()
            val mainWidth = ProjectsUi.textWidth(main, figureFont)
            ProjectsUi.drawText(g2, suffix, figureFont, Palette.FG_3, numberRight - suffixWidth, 0f, height.toFloat())
            ProjectsUi.drawText(g2, main, figureFont, Palette.FG, numberRight - suffixWidth - mainWidth, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }
}
