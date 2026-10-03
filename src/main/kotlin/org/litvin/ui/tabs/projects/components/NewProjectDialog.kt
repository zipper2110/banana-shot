package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.media.FfprobeVideoReadCheck
import org.litvin.media.VideoProblem
import org.litvin.media.VideoReadCheck
import org.litvin.projects.NewProjectRules
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dialog
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
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
 *
 * "Create project" also opens the video with [videoCheck] in the background. A file that does not open,
 * for example a copy that is not finished, keeps the dialog open with the problem under the video field.
 */
class NewProjectDialog private constructor(
    owner: Window?,
    initial: NewProjectRequest,
    private val chooseVideo: (dialog: Component, currentPath: String) -> String?,
    private val videoCheck: VideoReadCheck,
) : JDialog(owner, "New project", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : NewProjectEditor {
        private val videoCheck = FfprobeVideoReadCheck()

        override fun edit(
            parent: Component,
            initial: NewProjectRequest,
            chooseVideo: (dialog: Component, currentPath: String) -> String?,
        ): NewProjectRequest? {
            val dialog = NewProjectDialog(SwingUtilities.getWindowAncestor(parent), initial, chooseVideo, videoCheck)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    private var result: NewProjectRequest? = null

    /** The name that the dialog suggested for the current video. The user did not change the name while it is equal to this value. */
    private var suggestedName = initial.name

    private val nameField = JTextField(initial.name, 36).apply { name = "new-project-name" }
    private val nameError = DialogKit.errorLine("new-project-name-error")
    private val videoField = JTextField(initial.sourceVideoPath, 36).apply { name = "new-project-video" }
    private val videoError = DialogKit.errorLine("new-project-video-error")

    /** What happened to a video that does not open, and what the user can do. It shows only after a failed video check. */
    private val videoExplanation = WrapText(" ", UiKit.font(12.5f), Palette.FG_2, lineFactor = 1.45f).apply {
        name = "new-project-video-explanation"
        isVisible = false
    }

    /** True while the video check runs. */
    private var checking = false
    private val browseButton = UiButton("Browse…", buttonHeight = 34).apply {
        name = "new-project-browse"
        toolTipText = "Select a different match video"
        addActionListener { browse() }
    }
    private val createButton = UiButton("Create project", Material2AL.ADD, UiButton.Kind.LIME).apply {
        name = "new-project-create"
        addActionListener { create() }
    }

    init {
        name = "new-project-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val cancelButton = UiButton("Cancel").apply {
            name = "new-project-cancel"
            addActionListener { dispose() }
        }

        contentPane = DialogKit.content(
            DialogKit.MEDIUM,
            DialogKit.head("New project"),
            form(),
            DialogKit.footer(right = listOf(cancelButton, createButton)),
        )
        rootPane.defaultButton = createButton
        DialogKit.onEscape(this) { dispose() }

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
            add(DialogKit.inputBox(videoField) { videoError.text.isNotBlank() }, BorderLayout.CENTER)
            add(browseButton, BorderLayout.EAST)
        }
        return DialogKit.form(
            DialogKit.field("Project name", DialogKit.inputBox(nameField) { nameError.text.isNotBlank() }),
            nameError,
            DialogKit.field("Match video", videoRow, note = "The app never changes the video file."),
            videoError,
            videoExplanation,
            gap = 6,
        )
    }

    /** Shows the error messages and enables "Create project" only for correct values. Returns true for correct values. */
    private fun validateFields(): Boolean {
        val nameMessage = NewProjectRules.nameError(nameField.text)
        val videoMessage = NewProjectRules.sourceVideoError(videoField.text)
        showError(nameError, nameMessage)
        showError(videoError, videoMessage)
        showVideoExplanation(null)
        val valid = nameMessage == null && videoMessage == null
        createButton.isEnabled = valid && !checking
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
        if (checking || !validateFields()) return
        val request = currentRequest()
        checking = true
        createButton.isEnabled = false
        createButton.text = "Checking video…"
        Thread({
            val problem = videoCheck.problem(request.sourceVideoPath)
            SwingUtilities.invokeLater { onVideoChecked(request, problem) }
        }, "new-project-video-check").apply { isDaemon = true }.start()
    }

    private fun onVideoChecked(request: NewProjectRequest, problem: VideoProblem?) {
        checking = false
        createButton.text = "Create project"
        if (!isDisplayable) return
        // The user changed a field during the check. The next click checks the new values.
        if (request != currentRequest()) {
            validateFields()
            return
        }
        if (problem == null) {
            result = request
            dispose()
            return
        }
        // Keep "Create project" enabled: after the copy is finished, the user clicks it again.
        validateFields()
        showError(videoError, problem.title)
        showVideoExplanation(problem.explanation)
        contentPane?.repaint()
    }

    private fun currentRequest() = NewProjectRequest(name = nameField.text.trim(), sourceVideoPath = videoField.text.trim())

    private fun showVideoExplanation(explanation: String?) {
        videoExplanation.runs = listOf(TextRun(explanation ?: " ", UiKit.font(12.5f), Palette.FG_2))
        if (videoExplanation.isVisible == (explanation != null)) return
        videoExplanation.isVisible = explanation != null
        // The explanation adds some lines, so the dialog gets taller.
        pack()
    }

    private fun showError(label: JLabel, message: String?) {
        // A space keeps the height of the row, so the dialog does not change size.
        label.text = message ?: " "
    }
}
