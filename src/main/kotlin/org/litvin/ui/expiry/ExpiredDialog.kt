package org.litvin.ui.expiry

import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.Component
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog

/**
 * The modal dialog of expired mode (E8-S3). One dialog object stays for the session. The state changes its texts
 * while it shows, for example "Checking…" and then the "cannot connect" text.
 */
internal class ExpiredDialog(
    private val parent: Component,
    updateButtons: UpdateButtons,
    onCheckNow: () -> Unit,
    private val onClose: () -> Unit,
) {
    private val dialogWidth = DialogKit.WIDE
    private val textWidth = dialogWidth - 84

    private fun line(name: String, color: java.awt.Color = Palette.FG_2) =
        WrapText("", UiKit.font(13f), color, 1.55f, textWidth).apply { this.name = name }

    /** The rule `message` or the default text. Plain text: the component does not show HTML (E8-S8). */
    private val message = line("expired-dialog-message", Palette.FG)
    private val newVersion = line("expired-dialog-new-version", Palette.LIME)
    private val dateUsed = line("expired-dialog-date")
    private val checkState = line("expired-dialog-check-state")

    val checkNow = UiButton(ExpiryTexts.CHECK_NOW).apply {
        name = "expired-dialog-check-now"
        addActionListener { onCheckNow() }
    }

    private val close = UiButton(ExpiryTexts.CLOSE).apply {
        name = "expired-dialog-close"
        addActionListener { onClose() }
    }

    val dialog: JDialog = DialogKit.modal(parent, ExpiryTexts.EXPIRED_TITLE).apply {
        name = "expired-dialog"
        // The controller decides when the dialog closes: each way to close it goes through onClose.
        defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
        DialogKit.onEscape(this) { onClose() }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = onClose()
        })
        contentPane = MessageDialog.content(
            MessageKind.ERROR,
            ExpiryTexts.EXPIRED_TITLE,
            listOf(message, newVersion, dateUsed, checkState),
            DialogKit.footer(left = listOf(checkNow), right = updateButtons.all + close),
            dialogWidth,
        )
        isResizable = false
    }

    /** Sets the texts. A visible dialog changes at once. */
    fun update(content: ExpiredContent) {
        set(message, content.message)
        set(newVersion, content.newVersion)
        set(dateUsed, content.dateUsed)
        set(checkState, content.checkState)
        checkNow.isEnabled = !content.checking
        checkNow.text = if (content.checking) ExpiryTexts.CHECKING else ExpiryTexts.CHECK_NOW
        if (dialog.isVisible) dialog.pack()
    }

    /** The plain texts that show, for tests. */
    val shownText: String
        get() = listOf(message, newVersion, dateUsed, checkState).filter { it.isVisible }.joinToString("\n") { it.text }

    fun show() {
        if (!dialog.isVisible) {
            dialog.pack()
            dialog.setLocationRelativeTo(parent)
            // Modal: this call returns when the dialog hides. The state can change the texts while it shows.
            dialog.isVisible = true
        }
    }

    fun hide() {
        if (dialog.isVisible) dialog.isVisible = false
    }

    private fun set(line: WrapText, value: String?) {
        line.isVisible = value != null
        if (value != null && line.text != value) line.runs = listOf(TextRun(value, line.runs.first().font, line.runs.first().color))
    }
}

/** The texts of the banner and the dialog of expired mode. */
internal data class ExpiredContent(
    val message: String,
    val newVersion: String?,
    val dateUsed: String,
    val checkState: String?,
    val checking: Boolean,
)
