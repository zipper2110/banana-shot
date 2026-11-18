package org.litvin.ui.tabs.projects.components

import org.litvin.projects.NewProjectRules
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.UiButton
import java.awt.Component
import java.awt.Dialog
import java.awt.Window
import javax.swing.JDialog
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import org.litvin.i18n.tr

/** Opens the "Rename project" dialog and returns the new name, or null after Cancel. Tests replace the dialog with a fake. */
fun interface ProjectNameEditor {
    /** [videoPaths] are the video files of the project in play order. The dialog shows them, but cannot change them. */
    fun edit(parent: Component, currentName: String, videoPaths: List<String>): String?
}

/**
 * Modal "Rename project" dialog with one field for the project name.
 *
 * "Rename" is disabled while the name is not correct. A message under the field tells the user what to correct.
 * Under the name, the dialog shows the read-only list of the video files of the project (B-49).
 */
class RenameProjectDialog private constructor(
    owner: Window?,
    currentName: String,
    videoPaths: List<String>,
) : JDialog(owner, tr("projects.rename.tooltip"), Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : ProjectNameEditor {
        override fun edit(parent: Component, currentName: String, videoPaths: List<String>): String? {
            val dialog = RenameProjectDialog(SwingUtilities.getWindowAncestor(parent), currentName, videoPaths)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    private var result: String? = null

    private val nameField = JTextField(currentName, 36).apply { name = "rename-project-name" }
    private val nameError = DialogKit.errorLine("rename-project-name-error")
    private val renameButton = UiButton(tr("projects.rename"), kind = UiButton.Kind.LIME).apply {
        name = "rename-project-save"
        addActionListener { rename() }
    }

    init {
        name = "rename-project-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val cancelButton = UiButton(tr("common.cancel")).apply {
            name = "rename-project-cancel"
            addActionListener { dispose() }
        }

        val rows = mutableListOf(
            DialogKit.field(tr("projects.name"), DialogKit.inputBox(nameField) { nameError.text.isNotBlank() }),
            nameError,
        )
        if (videoPaths.isNotEmpty()) {
            rows += DialogKit.field(
                tr("projects.videos.caption", "count" to videoPaths.size),
                ProjectVideosList(videoPaths),
                note = tr("projects.videos.fixed"),
            )
        }
        contentPane = DialogKit.content(
            // The paths need more width than the name.
            if (videoPaths.isEmpty()) DialogKit.SMALL else DialogKit.WIDE,
            DialogKit.head(tr("projects.rename.tooltip")),
            DialogKit.form(*rows.toTypedArray(), gap = 6),
            DialogKit.footer(right = listOf(cancelButton, renameButton)),
        )
        rootPane.defaultButton = renameButton
        DialogKit.onEscape(this) { dispose() }

        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { validateName() }
            override fun removeUpdate(e: DocumentEvent?) { validateName() }
            override fun changedUpdate(e: DocumentEvent?) { validateName() }
        })

        validateName()
        pack()
        minimumSize = size
        nameField.selectAll()
    }

    /** Shows the error message and enables "Rename" only for a correct name. Returns true for a correct name. */
    private fun validateName(): Boolean {
        val message = NewProjectRules.nameError(nameField.text)
        nameError.text = message ?: " "
        val valid = message == null
        renameButton.isEnabled = valid
        // The input box paints a red border for an error.
        contentPane?.repaint()
        return valid
    }

    private fun rename() {
        if (!validateName()) return
        result = nameField.text.trim()
        dispose()
    }
}
