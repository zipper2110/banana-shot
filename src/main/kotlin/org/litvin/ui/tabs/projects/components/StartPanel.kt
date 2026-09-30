package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.geom.Path2D
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Scrollable
import javax.swing.ScrollPaneConstants

/**
 * The start panel on the left of the Projects tab: the brand, the current project, the "New project" block
 * with the workflow of the app, and the note about the automatic save at the bottom.
 * The panel has a fixed width. It scrolls when the window is not tall enough.
 */
internal class StartPanel(onImportNewMatch: () -> Unit) : JPanel(BorderLayout()) {
    private val currentSlot = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val newProject = NewProjectBlock(CONTENT_WIDTH, onImportNewMatch)

    val importButton: JButton get() = newProject.importButton

    init {
        isOpaque = true
        background = Palette.PANEL
        name = "projects-start-panel"
        val blocks = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(ProjectsHeader())
            add(Gap(0, BLOCK_GAP).leftAligned())
            add(Caption("Current project").leftAligned())
            add(Gap(0, 8).leftAligned())
            add(currentSlot)
            add(Gap(0, BLOCK_GAP).leftAligned())
            add(Caption("New project").leftAligned())
            add(Gap(0, 8).leftAligned())
            add(newProject)
        }
        val content = ScrollContent().apply {
            border = BorderFactory.createEmptyBorder(22, 20, 18, 20)
            add(blocks, BorderLayout.NORTH)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(BLOCK_GAP, 0, 0, 0)
                add(SaveNote(CONTENT_WIDTH), BorderLayout.SOUTH)
            }, BorderLayout.SOUTH)
        }
        add(JScrollPane(content).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 18
            applyDarkScrollbar(this, Palette.PANEL)
        }, BorderLayout.CENTER)
    }

    /** Shows the current project, or the "No open project" card for a null [name]. */
    fun showCurrentProject(name: String?, videoPath: String?, onRename: (() -> Unit)?) {
        currentSlot.removeAll()
        currentSlot.add(CurrentProjectCard(name, videoPath, CONTENT_WIDTH, onRename), BorderLayout.CENTER)
        currentSlot.revalidate()
        currentSlot.repaint()
    }

    override fun getPreferredSize(): Dimension = Dimension(PANEL_WIDTH, super.getPreferredSize().height)
    override fun getMinimumSize(): Dimension = Dimension(PANEL_WIDTH, 0)
    override fun getMaximumSize(): Dimension = Dimension(PANEL_WIDTH, Int.MAX_VALUE)

    override fun paintChildren(g: Graphics) {
        super.paintChildren(g)
        g.color = Palette.LINE
        g.fillRect(width - 1, 0, 1, height)
    }

    /** The scroll view: as tall as the viewport when the viewport is taller, so the save note stays at the bottom. */
    private class ScrollContent : JPanel(BorderLayout()), Scrollable {
        init {
            isOpaque = false
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 18
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = (parent?.height ?: 0) > preferredSize.height
    }

    companion object {
        const val PANEL_WIDTH = 320

        /** The width inside the 20 px side padding. */
        const val CONTENT_WIDTH = PANEL_WIDTH - 40
        private const val BLOCK_GAP = 22
    }
}

/** The note that the app saves all changes. The icon is a laptop with a check mark: the changes stay on this computer. */
private class SaveNote(contentWidth: Int) : JPanel(BorderLayout(10, 0)) {
    init {
        isOpaque = false
        name = "projects-save-note"
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(1, 0, 0, 0)
            add(IconBox(SavedLocallyIcon()), BorderLayout.NORTH)
        }, BorderLayout.WEST)
        add(
            WrapLabel(
                "The app saves all changes automatically across all tabs. No need to save anything manually.",
                ProjectsUi.font(13f),
                Palette.FG_2,
                contentWidth - 34,
                1.5f,
            ),
            BorderLayout.CENTER,
        )
    }

    private class IconBox(private val icon: Icon) : JComponent() {
        override fun getPreferredSize() = Dimension(icon.iconWidth, icon.iconHeight)
        override fun paintComponent(g: Graphics) {
            val g2 = ProjectsUi.smooth(g)
            try {
                icon.paintIcon(this, g2, 0, 0)
            } finally {
                g2.dispose()
            }
        }
    }

    /** A laptop with a check mark on the screen, as the `sync_saved_locally` symbol of the design. */
    private class SavedLocallyIcon : Icon {
        private val laptop = ProjectsUi.icon(Material2OutlinedAL.LAPTOP, SIZE, Palette.SAGE)

        override fun getIconWidth() = SIZE
        override fun getIconHeight() = SIZE

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            laptop.paintIcon(c, g, x, y)
            val g2 = ProjectsUi.smooth(g)
            try {
                g2.color = Palette.SAGE
                g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.draw(Path2D.Double().apply {
                    moveTo(x + 9.0, y + 10.0)
                    lineTo(x + 11.2, y + 12.2)
                    lineTo(x + 15.2, y + 8.0)
                })
            } finally {
                g2.dispose()
            }
        }

        private companion object {
            const val SIZE = 24
        }
    }
}
