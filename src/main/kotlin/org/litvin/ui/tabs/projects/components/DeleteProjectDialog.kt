package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2OutlinedAL
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Window
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.ceil

/** Asks the user to confirm the deletion of a project. Returns true after "Delete project". Tests replace the dialog with a fake. */
fun interface ProjectDeleteConfirmer {
    fun confirm(parent: Component, projectName: String): Boolean
}

/**
 * Modal "Delete project" dialog: a red delete icon, the question with the project name, and the note that the video stays on the disk.
 * "Cancel" has the focus, so Enter does not delete the project.
 */
class DeleteProjectDialog private constructor(
    owner: Window?,
    projectName: String,
) : JDialog(owner, "Delete project", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : ProjectDeleteConfirmer {
        override fun confirm(parent: Component, projectName: String): Boolean {
            val dialog = DeleteProjectDialog(SwingUtilities.getWindowAncestor(parent), projectName)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.confirmed
        }

        private const val ICON_COLUMN = 28
        private const val ICON_GAP = 12
        private const val SIDE_PADDING = 20
        private const val TEXT_WIDTH = ProjectsDialogKit.NARROW - 2 * SIDE_PADDING - ICON_COLUMN - ICON_GAP
    }

    private var confirmed = false

    init {
        name = "delete-project-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val cancelButton = ProjectsButton("Cancel").apply {
            name = "delete-project-cancel"
            addActionListener { dispose() }
        }
        val deleteButton = ProjectsButton("Delete project", kind = ProjectsButton.Kind.DANGER).apply {
            name = "delete-project-confirm"
            addActionListener {
                confirmed = true
                dispose()
            }
        }

        val textFont = ProjectsUi.font(13f)
        val text = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("Delete project").apply {
                font = ProjectsUi.font(15f, ProjectsUi.Weight.BOLD)
                foreground = ProjectsUi.FG
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Gap(0, 6).leftAligned())
            add(
                RunsLabel(
                    listOf(
                        TextRun("Delete the project \"", textFont, ProjectsUi.FG_2),
                        TextRun(projectName, ProjectsUi.font(13f, ProjectsUi.Weight.BOLD), ProjectsUi.FG),
                        TextRun("\"?", textFont, ProjectsUi.FG_2),
                    ),
                    TEXT_WIDTH,
                ).apply { name = "delete-project-question" },
            )
            add(Gap(0, 6).leftAligned())
            add(
                WrapLabel(
                    "The app removes the points, the scores, and the settings of the project. The video file stays on the disk.",
                    textFont,
                    ProjectsUi.FG_2,
                    TEXT_WIDTH,
                    1.55f,
                ),
            )
        }
        val body = JPanel(BorderLayout(ICON_GAP, 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(18, SIDE_PADDING, 12, SIDE_PADDING)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                preferredSize = Dimension(ICON_COLUMN, ICON_COLUMN)
                add(JLabel(ProjectsUi.icon(Material2OutlinedAL.DELETE, 26, ProjectsUi.RED)), BorderLayout.NORTH)
            }, BorderLayout.WEST)
            add(text, BorderLayout.CENTER)
        }

        contentPane = ProjectsDialogKit.content(
            ProjectsDialogKit.NARROW,
            JPanel().apply { isOpaque = false },
            body,
            ProjectsDialogKit.footer(cancelButton, deleteButton),
        )
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "delete-project-cancel")
        rootPane.actionMap.put("delete-project-cancel", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = dispose()
        })
        pack()
        minimumSize = size
        cancelButton.requestFocusInWindow()
    }
}

/** A part of a text with its own font and color. */
private data class TextRun(val text: String, val font: Font, val color: Color)

/**
 * A text with parts in different fonts and colors, for example a bold project name in a sentence.
 * The text wraps between words on as many lines as necessary for [wrapWidth].
 */
private class RunsLabel(runs: List<TextRun>, private val wrapWidth: Int) : JLabel(runs.joinToString("") { it.text }) {
    private class Piece(val text: String, val run: TextRun, val width: Float)

    // Each word keeps the space after it, so the pieces of one line join without extra spaces.
    private val pieces = runs.flatMap { run ->
        Regex("\\S+\\s*|\\s+").findAll(run.text)
            // A word longer than a line breaks at any character.
            .flatMap { ProjectsUi.wrap(it.value, run.font, wrapWidth.toFloat()).asSequence() }
            .map { Piece(it, run, ProjectsUi.textWidth(it, run.font)) }
            .toList()
    }
    private val lineHeight = runs.maxOf { ProjectsUi.lineHeight(it.font, 1.55f) }

    init {
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun lines(width: Int): List<List<Piece>> {
        val lines = mutableListOf(mutableListOf<Piece>())
        var x = 0f
        pieces.forEach { piece ->
            val visibleWidth = ProjectsUi.textWidth(piece.text.trimEnd(), piece.run.font)
            if (x > 0f && x + visibleWidth > width) {
                lines += mutableListOf<Piece>()
                x = 0f
            }
            lines.last() += piece
            x += piece.width
        }
        return lines
    }

    override fun getPreferredSize() = Dimension(wrapWidth, ceil(lines(wrapWidth).size * lineHeight).toInt())
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            lines(if (width > 0) width else wrapWidth).forEachIndexed { index, line ->
                var x = 0f
                line.forEach { piece ->
                    ProjectsUi.drawText(g2, piece.text, piece.run.font, piece.run.color, x, index * lineHeight, lineHeight)
                    x += piece.width
                }
            }
        } finally {
            g2.dispose()
        }
    }
}
