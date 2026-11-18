package org.litvin.ui.tabs.projects.components

import java.io.File
import java.awt.CardLayout
import org.kordamp.ikonli.material2.Material2AL
import org.litvin.media.FfprobeVideoReadCheck
import org.litvin.media.VideoProblem
import org.litvin.media.VideoReadCheck
import org.litvin.projects.NewProjectRules
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Sport
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
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
import org.litvin.i18n.tr

/**
 * The name, the source video, the sport, and the match rules of a new project.
 * The dialog edits [rules] only for padel. A tennis project gets the tennis default rules.
 */
data class NewProjectRequest(
    val name: String,
    val sourceVideoPath: String,
    val sport: Sport = Sport.TENNIS,
    val rules: MatchRulesV1 = sport.defaultRules(),
    /** All video files of the match in play order (B-49). The first is [sourceVideoPath]. */
    val sourceVideoPaths: List<String> = listOf(sourceVideoPath),
)

/** Opens the "New project" dialog and returns the confirmed values, or null after Cancel. Tests replace the dialog with a fake. */
fun interface NewProjectEditor {
    /**
     * Shows [initial] for edit.
     * [chooseVideo] opens the video picker over the dialog at the current path and returns the selected paths,
     * or null after Cancel. Several paths are the files of one match (B-49). A folder path opens the picker in that
     * folder with no selected file ("Add videos").
     */
    fun edit(
        parent: Component,
        initial: NewProjectRequest,
        chooseVideo: (dialog: Component, currentPath: String) -> List<String>?,
    ): NewProjectRequest?
}

/**
 * Modal "New project" dialog: the project name, the match video, and the sport.
 * For padel, the dialog also shows the padel rules: the format, the deuce rule, and the Americano points.
 *
 * "Create project" is disabled while the name is empty or the path is not a supported video file.
 * A message under each field tells the user what to correct.
 *
 * "Create project" also opens the video with [videoCheck] in the background. A file that does not open,
 * for example a copy that is not finished, keeps the dialog open with the problem under the video field.
 *
 * A match of several files (B-49) shows a list of the files in place of the path field. The files play in the
 * order of the list. The user can move a file up or down and remove it.
 * "Change video" replaces the video. "Add videos" adds files at the end of the list. The files can be in different
 * folders.
 */
class NewProjectDialog private constructor(
    owner: Window?,
    initial: NewProjectRequest,
    private val chooseVideo: (dialog: Component, currentPath: String) -> List<String>?,
    private val videoCheck: VideoReadCheck,
) : JDialog(owner, tr("projects.new.title"), Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : NewProjectEditor {
        private val videoCheck = FfprobeVideoReadCheck()

        override fun edit(
            parent: Component,
            initial: NewProjectRequest,
            chooseVideo: (dialog: Component, currentPath: String) -> List<String>?,
        ): NewProjectRequest? {
            val dialog = NewProjectDialog(SwingUtilities.getWindowAncestor(parent), initial, chooseVideo, videoCheck)
            dialog.setLocationRelativeTo(parent)
            DialogKit.keepOnScreen(dialog)
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

    /** The files of a match of several files (B-49). Empty for one file: then [videoField] has the path. */
    private var files: List<String> = initial.sourceVideoPaths.takeIf { it.size > 1 }.orEmpty()
    private val filesList = VideoFilesList(onChange = ::onFilesChanged).apply { paths = files }

    /** Adds files at the end of the list (B-49). */
    private val addVideosButton = UiButton(tr("projects.new.add"), Material2AL.ADD, buttonHeight = 30).apply {
        name = "new-project-add-videos"
        toolTipText = tr("projects.new.add.tooltip")
        addActionListener { addVideos() }
    }
    private val videoCards = CardLayout()
    private val videoArea = JPanel(videoCards).apply { isOpaque = false }
    private val videoHint = DialogKit.errorLine("new-project-video-hint").apply {
        font = UiKit.font(11.5f)
        foreground = Palette.FG_3
    }

    /** What happened to a video that does not open, and what the user can do. It shows only after a failed video check. */
    private val videoExplanation = WrapText(" ", UiKit.font(12.5f), Palette.FG_2, lineFactor = 1.45f).apply {
        name = "new-project-video-explanation"
        isVisible = false
    }

    /** True while the video check runs. */
    private var checking = false
    /** Replaces the video. */
    private val browseButton = UiButton(tr("projects.new.change"), buttonHeight = 34).apply {
        name = "new-project-browse"
        toolTipText = tr("projects.new.browse.tooltip")
        addActionListener { browse() }
    }
    private val createButton = UiButton(tr("projects.new.create"), Material2AL.ADD, UiButton.Kind.LIME).apply {
        name = "new-project-create"
        addActionListener { create() }
    }

    private var sport = initial.sport
    private val sportChoice = SegmentedChoice(
        "new-project-sport",
        Sport.entries.map { SegmentedChoice.Option(it, it.title) },
    ).apply {
        selected = sport
        onChange { selectSport(it) }
    }

    /** The padel rules. The dialog keeps them while the user looks at tennis, so a switch back shows them again. */
    private val padelRules = PadelRulesGroup(if (initial.sport == Sport.PADEL) initial.rules else Sport.PADEL.defaultRules()).apply {
        component.isVisible = sport == Sport.PADEL
    }

    init {
        name = "new-project-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val cancelButton = UiButton(tr("common.cancel")).apply {
            name = "new-project-cancel"
            addActionListener { dispose() }
        }

        contentPane = DialogKit.content(
            DialogKit.WIDE,
            DialogKit.head(tr("projects.new.title")),
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

        showFilesMode()
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
        videoArea.add(videoRow, CARD_ONE_FILE)
        videoArea.add(filesList, CARD_FILES)
        val videoInput = JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(videoArea, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(addVideosButton, BorderLayout.WEST)
            }, BorderLayout.SOUTH)
        }
        return DialogKit.form(
            DialogKit.field(tr("projects.name"), DialogKit.inputBox(nameField) { nameError.text.isNotBlank() }),
            nameError,
            DialogKit.field(tr("projects.new.video"), videoInput, note = tr("projects.new.video.note")),
            videoHint,
            videoError,
            videoExplanation,
            DialogKit.field(tr("projects.new.sport"), sportChoice),
            padelRules.component,
            gap = 6,
        )
    }

    private fun selectSport(selected: Sport) {
        sport = selected
        padelRules.component.isVisible = selected == Sport.PADEL
        // The padel rules make the dialog taller. Tennis makes it short again.
        minimumSize = null
        pack()
        minimumSize = size
        // A taller dialog can reach below the taskbar on a small screen.
        DialogKit.keepOnScreen(this)
    }

    /** Shows the error messages and enables "Create project" only for correct values. Returns true for correct values. */
    private fun validateFields(): Boolean {
        val nameMessage = NewProjectRules.nameError(nameField.text)
        val videoMessage = if (files.size > 1) {
            files.firstNotNullOfOrNull { path -> NewProjectRules.sourceVideoError(path)?.let { "$it (${File(path).name})" } }
        } else {
            NewProjectRules.sourceVideoError(videoField.text)
        }
        showError(nameError, nameMessage)
        showError(videoError, videoMessage)
        showVideoExplanation(null)
        val valid = nameMessage == null && videoMessage == null
        createButton.isEnabled = valid && !checking
        // The input boxes paint a red border for an error.
        contentPane?.repaint()
        return valid
    }

    /** "Change video": the selected files replace the video. */
    private fun browse() {
        val current = files.firstOrNull() ?: videoField.text.trim()
        val selected = NewProjectRules.sortedVideoPaths(chooseVideo(this, current) ?: return)
        if (selected.isEmpty()) return
        showPaths(selected)
    }

    /** "Add videos" (B-49): the selected files come at the end of the list. The picker opens in the folder of the last file. */
    private fun addVideos() {
        val current = currentPaths()
        val folder = current.lastOrNull()?.let { File(it).absoluteFile.parent }
        val selected = chooseVideo(this, folder ?: "") ?: return
        val changed = NewProjectRules.appendVideoPaths(current, selected)
        if (changed == current) return
        showPaths(changed)
    }

    /** The files in play order: the list, or the path of the field. Empty when the field is empty. */
    private fun currentPaths(): List<String> = files.ifEmpty { listOfNotNull(videoField.text.trim().takeIf { it.isNotEmpty() }) }

    /** Shows [paths]: one file in the path field, or several files in the list. */
    private fun showPaths(paths: List<String>) {
        files = paths.takeIf { it.size > 1 }.orEmpty()
        filesList.paths = files
        followName(paths.first())
        videoField.text = paths.first()
        showFilesMode()
    }

    /** The user moved or removed a file of the list (B-49). One file left goes back to the path field. */
    private fun onFilesChanged(changed: List<String>) {
        changed.firstOrNull()?.let(::followName)
        if (changed.size < 2) {
            files = emptyList()
            videoField.text = changed.firstOrNull().orEmpty()
            showFilesMode()
        } else {
            files = changed
            validateFields()
        }
    }

    /** Follows the first video with the name, but keeps a name that the user typed. */
    private fun followName(firstVideo: String) {
        if (nameField.text.trim() == suggestedName.trim()) {
            nameField.text = NewProjectRules.suggestedName(firstVideo)
        }
        suggestedName = NewProjectRules.suggestedName(firstVideo)
    }

    /** Shows the path field for one file, or the list for several files, and the hint under them. */
    private fun showFilesMode() {
        val several = files.size > 1
        videoCards.show(videoArea, if (several) CARD_FILES else CARD_ONE_FILE)
        videoHint.text = tr(if (several) "projects.new.files.order" else "projects.new.files.add.hint")
        validateFields()
        if (isDisplayable) {
            minimumSize = null
            pack()
            minimumSize = size
            DialogKit.keepOnScreen(this)
        }
    }

    private fun create() {
        if (checking || !validateFields()) return
        val request = currentRequest()
        checking = true
        createButton.isEnabled = false
        createButton.text = tr("projects.new.checking")
        Thread({
            // Each file of a match of several files must open (B-49). The problem names the file.
            val problem = request.sourceVideoPaths.firstNotNullOfOrNull { path ->
                videoCheck.problem(path)?.let { problem ->
                    if (request.sourceVideoPaths.size > 1) problem.copy(title = "${problem.title} (${File(path).name})") else problem
                }
            }
            SwingUtilities.invokeLater { onVideoChecked(request, problem) }
        }, "new-project-video-check").apply { isDaemon = true }.start()
    }

    private fun onVideoChecked(request: NewProjectRequest, problem: VideoProblem?) {
        checking = false
        createButton.text = tr("projects.new.create")
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

    private fun currentRequest(): NewProjectRequest {
        val paths = files.takeIf { it.size > 1 } ?: listOf(videoField.text.trim())
        return NewProjectRequest(
            name = nameField.text.trim(),
            sourceVideoPath = paths.first(),
            sport = sport,
            rules = if (sport == Sport.PADEL) padelRules.rules else sport.defaultRules(),
            sourceVideoPaths = paths,
        )
    }

    private fun showVideoExplanation(explanation: String?) {
        videoExplanation.runs = listOf(TextRun(explanation ?: " ", UiKit.font(12.5f), Palette.FG_2))
        if (videoExplanation.isVisible == (explanation != null)) return
        videoExplanation.isVisible = explanation != null
        // The explanation adds some lines, so the dialog gets taller.
        pack()
        DialogKit.keepOnScreen(this)
    }

    private fun showError(label: JLabel, message: String?) {
        // A space keeps the height of the row, so the dialog does not change size.
        label.text = message ?: " "
    }
}

private const val CARD_ONE_FILE = "one-file"
private const val CARD_FILES = "files"
