package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.projects.NewProjectRules
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dialog
import java.awt.Window
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** The name and the source video of a new project. */
data class NewProjectRequest(val name: String, val sourceVideoPath: String)

/** Opens the "New project" dialog and returns the confirmed values, or null after Cancel. Tests replace the dialog with a fake. */
fun interface NewProjectEditor {
    /**
     * Shows [initial] for edit.
     * [chooseVideo] opens the video picker over the dialog at the current path and returns the selected path,
     * or null after Cancel.
     */
    fun edit(
        parent: Component,
        initial: NewProjectRequest,
        chooseVideo: (dialog: Component, currentPath: String) -> String?,
    ): NewProjectRequest?
}

/**
 * Modal "New project" dialog: the project name and the match video.
 *
 * "Create project" is disabled while the name is empty or the path is not a supported video file.
 * A message under each field tells the user what to correct.
 */
class NewProjectDialog private constructor(
    owner: Window?,
    initial: NewProjectRequest,
    private val chooseVideo: (dialog: Component, currentPath: String) -> String?,
) : JDialog(owner, "New project", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : NewProjectEditor {
        override fun edit(
            parent: Component,
            initial: NewProjectRequest,
            chooseVideo: (dialog: Component, currentPath: String) -> String?,
        ): NewProjectRequest? {
            val dialog = NewProjectDialog(SwingUtilities.getWindowAncestor(parent), initial, chooseVideo)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    private var result: NewProjectRequest? = null

    /** The name that the dialog suggested for the current video. The user did not change the name while it is equal to this value. */
    private var suggestedName = initial.name

    private val nameField = JTextField(initial.name, 36).apply { name = "new-project-name" }
    private val nameError = ProjectsDialogKit.errorLine("new-project-name-error")
    private val videoField = JTextField(initial.sourceVideoPath, 36).apply { name = "new-project-video" }
    private val videoError = ProjectsDialogKit.errorLine("new-project-video-error")
    private val browseButton = ProjectsButton("Browse…", buttonHeight = 34).apply {
        name = "new-project-browse"
        toolTipText = "Select a different match video"
        addActionListener { browse() }
    }
    private val createButton = ProjectsButton("Create project", Material2AL.ADD, ProjectsButton.Kind.LIME, 30, 12.5f).apply {
        name = "new-project-create"
        addActionListener { create() }
    }

    init {
        name = "new-project-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val cancelButton = ProjectsButton("Cancel").apply {
            name = "new-project-cancel"
            addActionListener { dispose() }
        }

        contentPane = ProjectsDialogKit.content(
            ProjectsDialogKit.WIDE,
            ProjectsDialogKit.head("New project"),
            form(),
            ProjectsDialogKit.footer(cancelButton, createButton),
        )
        rootPane.defaultButton = createButton
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "new-project-cancel")
        rootPane.actionMap.put("new-project-cancel", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = dispose()
        })

        val revalidate = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { validateFields() }
            override fun removeUpdate(e: DocumentEvent?) { validateFields() }
            override fun changedUpdate(e: DocumentEvent?) { validateFields() }
        }
        nameField.document.addDocumentListener(revalidate)
        videoField.document.addDocumentListener(revalidate)

        validateFields()
        pack()
        minimumSize = size
        nameField.selectAll()
    }

    private fun form(): JComponent {
        val videoRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(ProjectsDialogKit.inputBox(videoField) { videoError.text.isNotBlank() }, BorderLayout.CENTER)
            add(browseButton, BorderLayout.EAST)
        }
        return ProjectsDialogKit.form(
            ProjectsDialogKit.field("Project name", ProjectsDialogKit.inputBox(nameField) { nameError.text.isNotBlank() }),
            nameError,
            ProjectsDialogKit.field("Match video", videoRow),
            videoError,
        )
    }

    /** Shows the error messages and enables "Create project" only for correct values. Returns true for correct values. */
    private fun validateFields(): Boolean {
        val nameMessage = NewProjectRules.nameError(nameField.text)
        val videoMessage = NewProjectRules.sourceVideoError(videoField.text)
        showError(nameError, nameMessage)
        showError(videoError, videoMessage)
        val valid = nameMessage == null && videoMessage == null
        createButton.isEnabled = valid
        // The input boxes paint a red border for an error.
        contentPane?.repaint()
        return valid
    }

    private fun browse() {
        val selected = chooseVideo(this, videoField.text.trim()) ?: return
        // Follow the new video with the name, but keep a name that the user typed.
        if (nameField.text.trim() == suggestedName.trim()) {
            nameField.text = NewProjectRules.suggestedName(selected)
        }
        suggestedName = NewProjectRules.suggestedName(selected)
        videoField.text = selected
    }

    private fun create() {
        if (!validateFields()) return
        result = NewProjectRequest(name = nameField.text.trim(), sourceVideoPath = videoField.text.trim())
        dispose()
    }

    private fun showError(label: JLabel, message: String?) {
        // A space keeps the height of the row, so the dialog does not change size.
        label.text = message ?: " "
    }
}
