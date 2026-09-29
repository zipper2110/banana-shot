package org.litvin.ui.tabs.projects.components

import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.Component
import java.awt.Dialog
import java.awt.Window
import javax.swing.JDialog
import javax.swing.SwingUtilities

/** Asks the user to confirm the deletion of a project. Returns true after "Delete project". Tests replace the dialog with a fake. */
fun interface ProjectDeleteConfirmer {
    fun confirm(parent: Component, projectName: String): Boolean
}

/**
 * Modal "Delete project" dialog in the message box design: a red delete icon, the question with the project name,
 * and the note that the video stays on the disk. "Cancel" has the focus, so Enter does not delete the project.
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
    }

    private var confirmed = false

    init {
        name = "delete-project-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val cancelButton = UiButton("Cancel").apply {
            name = "delete-project-cancel"
            addActionListener { dispose() }
        }
        val deleteButton = UiButton("Delete project", kind = UiButton.Kind.DANGER).apply {
            name = "delete-project-confirm"
            addActionListener {
                confirmed = true
                dispose()
            }
        }

        val textFont = UiKit.font(13f)
        val question = WrapText(
            listOf(
                TextRun("Delete the project \"", textFont, UiKit.FG_2),
                TextRun(projectName, UiKit.font(13f, UiKit.Weight.SEMIBOLD), UiKit.FG),
                TextRun("\"?", textFont, UiKit.FG_2),
            ),
            1.55f,
        ).apply { name = "delete-project-question" }
        val note = MessageDialog.paragraph(
            "The app removes the points, the scores, and the settings of the project. The video file stays on the disk.",
        )

        contentPane = MessageDialog.content(
            MessageKind.DANGER,
            "Delete project",
            listOf(question, note),
            DialogKit.footer(right = listOf(cancelButton, deleteButton)),
        )
        // Enter clicks the focused safe button.
        rootPane.defaultButton = cancelButton
        DialogKit.onEscape(this) { dispose() }
        isResizable = false
        pack()
        cancelButton.requestFocusInWindow()
    }
}
