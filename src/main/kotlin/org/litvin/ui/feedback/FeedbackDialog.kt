package org.litvin.ui.feedback

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.feedback.FeedbackTopic
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.MonoFont
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import org.litvin.ui.commons.applyDarkScrollbar
import org.litvin.ui.feedback.presenter.FeedbackIntent
import org.litvin.ui.feedback.presenter.FeedbackPhase
import org.litvin.ui.feedback.presenter.FeedbackPresenter
import org.litvin.ui.feedback.presenter.FeedbackRequest
import org.litvin.ui.feedback.presenter.FeedbackView
import org.litvin.ui.feedback.presenter.FeedbackViewEffect
import org.litvin.ui.feedback.presenter.FeedbackViewState
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

/** Opens the feedback form. One form exists for the window, so the draft stays while the form is closed. */
class FeedbackLauncher(private val owner: Window?, private val presenter: FeedbackPresenter) {
    private var dialog: FeedbackDialog? = null

    fun open(request: FeedbackRequest = FeedbackRequest()) {
        presenter.onIntent(FeedbackIntent.Open(request))
        val shown = dialog ?: FeedbackDialog(owner, presenter).also { dialog = it }
        shown.open()
    }

    val isOpen: Boolean get() = dialog?.isVisible == true

    fun close() {
        dialog?.dispose()
        dialog = null
    }
}

/**
 * The window of the feedback form (T2 of B-8). It is not modal: the user can continue to use the app while the
 * report goes. A close only hides the window, so the draft stays.
 */
internal class FeedbackDialog(owner: Window?, private val presenter: FeedbackPresenter) :
    JDialog(owner, "Send feedback", Dialog.ModalityType.MODELESS) {
    private val panel = FeedbackPanel(presenter, onClose = { isVisible = false }, onSizeChanged = { if (isDisplayable) pack() })

    init {
        name = "feedback-dialog"
        defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        DialogKit.onEscape(this) { isVisible = false }
        contentPane = panel
        presenter.attach(panel)
        pack()
        setLocationRelativeTo(owner)
    }

    fun open() {
        if (!isVisible) {
            setLocationRelativeTo(owner)
            isVisible = true
        }
        toFront()
        SwingUtilities.invokeLater { panel.focusMessage() }
    }

    override fun dispose() {
        presenter.detach()
        super.dispose()
    }
}

/**
 * The feedback form. The presenter has all the state. This panel only shows it and forwards the user actions.
 * [onSizeChanged] runs when the preferred size changes (a part shows or hides, or a text gets more lines), so the
 * window can change its size.
 */
internal class FeedbackPanel(
    private val presenter: FeedbackPresenter,
    private val onClose: () -> Unit,
    private val onSizeChanged: () -> Unit = {},
) : JPanel(BorderLayout()), FeedbackView {

    /** True while [render] changes the inputs, so the listeners do not send the change back. */
    private var rendering = false

    private val topic = SegmentedChoice("feedback-topic", FeedbackTopic.entries.map { SegmentedChoice.Option(it, it.title) }).apply {
        onChange { presenter.onIntent(FeedbackIntent.SelectTopic(it)) }
    }
    private val message = JTextArea(7, 40).apply {
        name = "feedback-message"
        lineWrap = true
        wrapStyleWord = true
        onEdit { presenter.onIntent(FeedbackIntent.EditMessage(text)) }
    }
    private val messageError = DialogKit.errorLine("feedback-message-error")
    private val email = JTextField(30).apply {
        name = "feedback-email"
        onEdit { presenter.onIntent(FeedbackIntent.EditEmail(text)) }
    }
    private val emailError = DialogKit.errorLine("feedback-email-error")
    private val attachLog = SwitchBox("Attach the log files").apply {
        name = "feedback-attach-log"
        addActionListener { if (!rendering) presenter.onIntent(FeedbackIntent.SetAttachLog(isSelected)) }
    }
    private val showData = UiButton("Show the data", Material2MZ.VISIBILITY, UiButton.Kind.GHOST, buttonHeight = UiButton.SMALL_HEIGHT).apply {
        name = "feedback-show-data"
        toolTipText = "Show the exact data that the app sends"
        addActionListener { presenter.onIntent(FeedbackIntent.ShowData) }
    }
    private val errorText = JTextArea(3, 40).apply {
        name = "feedback-error"
        isEditable = false
        lineWrap = true
        font = MonoFont.of(12f)
    }
    private val errorPanel: JComponent
    private val replyNote = paragraph("", "feedback-reply-note", secondary = true)
    private val system = paragraph("", "feedback-system")
    private val status = paragraph("", "feedback-status")

    private val copyReport = UiButton("Copy report", Material2AL.CONTENT_COPY).apply {
        name = "feedback-copy"
        addActionListener { presenter.onIntent(FeedbackIntent.CopyReport) }
    }
    private val writeEmail = UiButton("Write an email", Material2MZ.MAIL_OUTLINE).apply {
        name = "feedback-write-email"
        addActionListener { presenter.onIntent(FeedbackIntent.WriteEmail) }
    }
    private val close = UiButton("Close").apply {
        name = "feedback-close"
        addActionListener { onClose() }
    }
    private val send = UiButton("Send", Material2MZ.SEND, UiButton.Kind.LIME).apply {
        name = "feedback-send"
        addActionListener { presenter.onIntent(FeedbackIntent.Send) }
    }
    private val tryAgain = UiButton("Try again", Material2MZ.REFRESH, UiButton.Kind.LIME).apply {
        name = "feedback-try-again"
        addActionListener { presenter.onIntent(FeedbackIntent.Send) }
    }
    private val newReport = UiButton("New report", Material2AL.ADD).apply {
        name = "feedback-new"
        addActionListener { presenter.onIntent(FeedbackIntent.NewReport) }
    }

    init {
        name = "feedback-panel"
        errorPanel = DialogKit.field(
            "Error text",
            JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(DialogKit.inputBox(errorText, boxHeight = 64), BorderLayout.CENTER)
                add(UiButton("Remove", buttonHeight = UiButton.SMALL_HEIGHT).apply {
                    name = "feedback-remove-error"
                    toolTipText = "Do not send the error text"
                    addActionListener { presenter.onIntent(FeedbackIntent.RemoveError) }
                }, BorderLayout.EAST)
            },
            note = "The app sends it with the report",
        )
        val logRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(attachLog, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
                add(showData)
            }, BorderLayout.EAST)
        }
        val form = DialogKit.form(
            DialogKit.field("Topic", topic),
            DialogKit.field("Message", DialogKit.inputBox(message, boxHeight = 150, padding = 6) { messageError.text.isNotBlank() }),
            messageError,
            DialogKit.field("Your email address", DialogKit.inputBox(email) { emailError.text.isNotBlank() }, note = "optional"),
            replyNote,
            emailError,
            logRow,
            paragraph(LOG_NOTE, "feedback-log-note", secondary = true),
            errorPanel,
            system,
            status,
            gap = 6,
        )
        add(
            DialogKit.content(
                DialogKit.WIDE,
                DialogKit.head("Send feedback", "Tell about a problem, an idea, or a question. The author reads each report.", DialogKit.WIDE),
                form,
                DialogKit.footer(left = listOf(copyReport, writeEmail), right = listOf(newReport, close, send, tryAgain)),
            ),
            BorderLayout.CENTER,
        )
    }

    fun focusMessage() {
        message.requestFocusInWindow()
    }

    override fun render(state: FeedbackViewState) {
        rendering = true
        val sizeBefore = preferredSize
        try {
            topic.selected = state.topic
            setText(message, state.message)
            setText(email, state.email)
            attachLog.isSelected = state.attachLog
            errorPanel.isVisible = state.error != null
            setText(errorText, state.error ?: "")
            messageError.text = state.messageError ?: " "
            emailError.text = state.emailError ?: " "
            replyNote.runs = listOf(TextRun(replyNote(state.contactEmail), UiKit.font(12f), Palette.FG_3))
            system.runs = listOf(TextRun("The app adds: BananaShot ${state.systemSummary}", UiKit.font(12f), Palette.FG_3))
            status.runs = listOf(TextRun(state.status.ifEmpty { " " }, UiKit.font(12.5f), if (state.statusIsError) Palette.RED_TEXT else Palette.FG_2))
            status.name = "feedback-status"

            listOf(topic, message, email, attachLog).forEach { it.isEnabled = state.inputsEnabled }
            showData.isEnabled = state.showDataEnabled
            send.isVisible = state.canSendOnline && !state.showTryAgain && state.phase != FeedbackPhase.SENT
            send.isEnabled = state.sendEnabled
            send.text = if (state.phase == FeedbackPhase.SENDING) "Sending…" else "Send"
            tryAgain.isVisible = state.showTryAgain
            copyReport.isVisible = state.showFallbacks
            writeEmail.isVisible = state.showFallbacks
            newReport.isVisible = state.phase == FeedbackPhase.SENT
            SwingUtilities.getRootPane(this)?.defaultButton = when {
                tryAgain.isVisible -> tryAgain
                send.isVisible -> send
                else -> null
            }
            // A longer status or a shown part needs more height. The window must grow, or the text is cut off.
            invalidate()
            if (preferredSize != sizeBefore) onSizeChanged()
            revalidate()
            repaint()
        } finally {
            rendering = false
        }
    }

    override fun renderEffect(effect: FeedbackViewEffect) {
        when (effect) {
            is FeedbackViewEffect.ShowData -> DataWindow(SwingUtilities.getWindowAncestor(this), effect.text).isVisible = true
        }
    }

    private fun setText(field: JTextComponent, text: String) {
        if (field.text != text) field.text = text
    }

    private fun JTextComponent.onEdit(action: () -> Unit) {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = changed()
            override fun removeUpdate(e: DocumentEvent?) = changed()
            override fun changedUpdate(e: DocumentEvent?) = changed()
            private fun changed() {
                if (!rendering) action()
            }
        })
    }

    private companion object {
        fun replyNote(contactEmail: String) =
            "The author can reply only if you give an address. The reply comes from $contactEmail. " +
                "The app remembers your address for the next report."
        const val LOG_NOTE = "The log files help to find the cause of a problem. They contain the names and folders of your videos " +
            "and projects. They do not contain your videos."

        fun paragraph(text: String, componentName: String, secondary: Boolean = false): WrapText =
            WrapText(text, UiKit.font(12f), if (secondary) Palette.FG_3 else Palette.FG_2, 1.45f, DialogKit.WIDE - DialogKit.PAD_X * 2).apply {
                name = componentName
            }
    }
}

/** The exact data that the app sends, in a read-only text area. */
private class DataWindow(owner: Window?, text: String) : JDialog(owner, "The data that the app sends", Dialog.ModalityType.APPLICATION_MODAL) {
    init {
        name = "feedback-data-window"
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        DialogKit.onEscape(this) { dispose() }
        val area = JTextArea(text).apply {
            name = "feedback-data-text"
            isEditable = false
            lineWrap = true
            font = MonoFont.of(12f)
            caretPosition = 0
        }
        DialogKit.styleInput(area)
        area.font = MonoFont.of(12f)
        area.border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
        val scroll = JScrollPane(area).apply {
            border = BorderFactory.createEmptyBorder()
            viewport.background = Palette.INSET
            applyDarkScrollbar(this, Palette.INSET)
        }
        contentPane = JPanel(BorderLayout()).apply {
            background = Palette.OVERLAY
            add(JLabel("The app sends only this data. It sends it only when you click Send.").apply {
                font = UiKit.font(12.5f)
                foreground = Palette.FG_2
                border = BorderFactory.createEmptyBorder(12, DialogKit.PAD_X, 8, DialogKit.PAD_X)
            }, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
            add(DialogKit.footer(right = listOf(UiButton("Close", kind = UiButton.Kind.LIME).apply {
                name = "feedback-data-close"
                addActionListener { dispose() }
            })), BorderLayout.SOUTH)
        }
        size = Dimension(720, 520)
        setLocationRelativeTo(owner)
    }
}
