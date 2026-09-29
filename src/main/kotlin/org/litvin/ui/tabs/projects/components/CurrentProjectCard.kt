package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.ceil

/**
 * A label that shows its text on as many lines as necessary.
 * [wrapWidth] is the width for the preferred size, because the start panel has a fixed width.
 * The label paints the lines for its real width.
 */
internal class WrapLabel(
    text: String,
    private val textFont: Font,
    private val color: Color,
    private val wrapWidth: Int,
    private val lineFactor: Float = 1.4f,
) : JLabel(text) {
    init {
        font = textFont
        foreground = color
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun lineHeight() = ProjectsUi.lineHeight(textFont, lineFactor)

    private fun lines(width: Int) = ProjectsUi.wrap(text.orEmpty(), textFont, width.toFloat())

    override fun getPreferredSize(): Dimension =
        Dimension(wrapWidth, ceil(lines(wrapWidth).size * lineHeight()).toInt())

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            val lineHeight = lineHeight()
            lines(if (width > 0) width else wrapWidth).forEachIndexed { index, line ->
                ProjectsUi.drawText(g2, line, textFont, foreground, 0f, index * lineHeight, lineHeight)
            }
        } finally {
            g2.dispose()
        }
    }
}

/** The lime "OPEN" tag with a dot, the `.tag` element of the design. */
internal class OpenTag : JComponent() {
    private val text = "OPEN"
    private val tagFont = ProjectsUi.trackedFont(10.5f, 0.04, ProjectsUi.Weight.BOLD)

    override fun getPreferredSize() = Dimension(7 + 6 + 4 + ceil(ProjectsUi.textWidth(text, tagFont)).toInt() + 8, 18)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            ProjectsUi.paintBox(g2, 0, 0, width, height, height / 2, Color(161, 254, 0, 31), null)
            g2.color = ProjectsUi.LIME
            g2.fillOval(7, (height - 6) / 2, 6, 6)
            ProjectsUi.drawText(g2, text, tagFont, ProjectsUi.LIME, 17f, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }
}

/**
 * The card of the current project in the start panel.
 * With a project it shows the name, the "OPEN" tag, the absolute video path and the Rename button.
 * Without a project it shows a dashed card with a short text.
 */
internal class CurrentProjectCard(
    projectName: String?,
    videoPath: String?,
    contentWidth: Int,
    onRename: (() -> Unit)?,
) : JPanel() {
    private val isOpen = projectName != null

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        // The left inset is smaller, so that the ghost Rename button can start 8 px left of the text, as in the design.
        border = BorderFactory.createEmptyBorder(PADDING, PADDING - RENAME_SHIFT, PADDING, PADDING)
        val innerWidth = contentWidth - 2 * PADDING
        if (projectName != null) {
            val tag = OpenTag()
            val nameLabel = WrapLabel(
                projectName,
                ProjectsUi.font(16f, ProjectsUi.Weight.SEMIBOLD),
                ProjectsUi.FG,
                innerWidth - tag.preferredSize.width - 8,
                1.3f,
            ).apply { name = "projects-current-name" }
            add(shifted(JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(nameLabel, BorderLayout.CENTER)
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = BorderFactory.createEmptyBorder(2, 0, 0, 0)
                    add(tag, BorderLayout.NORTH)
                }, BorderLayout.EAST)
            }))
            if (videoPath != null) {
                add(Gap(0, 10).leftAligned())
                val pathLabel = WrapLabel(videoPath, ProjectsUi.font(12f), ProjectsUi.FG_2, innerWidth - 24, 1.4f).apply {
                    name = "projects-current-video"
                    toolTipText = videoPath
                }
                add(shifted(JPanel(BorderLayout(6, 0)).apply {
                    isOpaque = false
                    add(JPanel(BorderLayout()).apply {
                        isOpaque = false
                        border = BorderFactory.createEmptyBorder(1, 0, 0, 0)
                        preferredSize = Dimension(18, 16)
                        add(JLabel(ProjectsUi.icon(Material2OutlinedMZ.MOVIE, 15, ProjectsUi.FG_3)), BorderLayout.NORTH)
                    }, BorderLayout.WEST)
                    add(pathLabel, BorderLayout.CENTER)
                }))
            }
            if (onRename != null) {
                add(Gap(0, 12).leftAligned())
                add(RenameFoot(onRename))
            }
        } else {
            add(shifted(WrapLabel("No open project", ProjectsUi.font(14f, ProjectsUi.Weight.SEMIBOLD), ProjectsUi.FG_2, innerWidth, 1.3f)
                .apply { name = "projects-current-name" }))
            add(Gap(0, 6).leftAligned())
            add(shifted(WrapLabel(
                "Import a new match below, or open a project from the list.",
                ProjectsUi.font(12f),
                ProjectsUi.FG_3,
                innerWidth,
                1.5f,
            )))
        }
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            if (isOpen) {
                ProjectsUi.paintBox(g2, 0, 0, width, height, 8, ProjectsUi.CARD, ProjectsUi.LIME_LINE)
            } else {
                ProjectsUi.paintDashedBox(g2, 0, 0, width, height, 8, ProjectsUi.LINE_2)
            }
        } finally {
            g2.dispose()
        }
    }

    /** Moves a row to the text start, see the left inset of the card. */
    private fun shifted(row: JComponent): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(0, RENAME_SHIFT, 0, 0)
        add(row, BorderLayout.CENTER)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    /** The line and the Rename button at the bottom of the card. */
    private class RenameFoot(onRename: () -> Unit) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(11, 0, 0, 0)
            add(ProjectsButton("Rename", Material2AL.EDIT, ProjectsButton.Kind.GHOST, 26, 12f).apply {
                name = "projects-current-rename"
                toolTipText = "Rename project"
                addActionListener { onRename() }
            }, BorderLayout.WEST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        override fun paintComponent(g: Graphics) {
            g.color = ProjectsUi.LINE
            g.fillRect(RENAME_SHIFT, 0, width - RENAME_SHIFT, 1)
        }
    }

    private companion object {
        // The 14 px padding of the design and the 1 px border.
        const val PADDING = 15
        const val RENAME_SHIFT = 8
    }
}
