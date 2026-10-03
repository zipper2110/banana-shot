package org.litvin.ui.feedback.presenter

import org.litvin.feedback.FeedbackFailure
import org.litvin.feedback.FeedbackPreferences
import org.litvin.feedback.FeedbackReport
import org.litvin.feedback.FeedbackRules
import org.litvin.feedback.FeedbackSendResult
import org.litvin.feedback.FeedbackSender
import org.litvin.feedback.FeedbackSystemInfo
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executor

/**
 * The feedback form (T2 of B-8). It keeps the draft while the dialog is closed, so the user does not lose the text.
 *
 * The send and the read of the log run on [background]. [ui] runs the result on the UI thread. One report ID stays
 * until the author has the report, so "Try again" sends the same `report_id`.
 *
 * @param sender null when the build has no endpoint. Then the user can only copy the report or write an email.
 * @param readLog the log text of decision 12, or null when there is no log file.
 */
class DefaultFeedbackPresenter(
    private val sender: FeedbackSender?,
    private val readLog: () -> String?,
    private val preferences: FeedbackPreferences,
    private val system: FeedbackSystemInfo,
    private val contactEmail: String,
    private val copyToClipboard: (String) -> Unit,
    private val openMail: (URI) -> Boolean,
    private val background: Executor,
    private val ui: (Runnable) -> Unit,
    private val newReportId: () -> UUID = UUID::randomUUID,
) : FeedbackPresenter {
    private var view: FeedbackView? = null
    private var reportId: UUID = newReportId()

    /** The log of [reportId]. "Show the data" and "Send" use the same text. */
    private var log: CachedLog? = null

    private class CachedLog(val reportId: UUID, val text: String?)

    /** The failure of the last send, or null. */
    private var failure: FeedbackFailure? = null

    var state: FeedbackViewState = derive(emptyDraft())
        private set

    private fun emptyDraft() = FeedbackViewState(
        email = preferences.email,
        systemSummary = system.summary,
        contactEmail = contactEmail,
        canSendOnline = sender != null,
    )

    override fun attach(view: FeedbackView) {
        this.view = view
        view.render(state)
    }

    override fun detach() {
        view = null
    }

    override fun onIntent(intent: FeedbackIntent) {
        when (intent) {
            is FeedbackIntent.Open -> open(intent.request)
            is FeedbackIntent.SelectTopic -> edit { copy(topic = intent.topic) }
            is FeedbackIntent.EditMessage -> edit { copy(message = intent.text) }
            is FeedbackIntent.EditEmail -> edit { copy(email = intent.text) }
            is FeedbackIntent.SetAttachLog -> edit { copy(attachLog = intent.attach) }
            FeedbackIntent.RemoveError -> edit { copy(error = null) }
            FeedbackIntent.Send -> send()
            FeedbackIntent.ShowData -> showData()
            FeedbackIntent.CopyReport -> copyReport()
            FeedbackIntent.WriteEmail -> writeEmail()
        }
    }

    private fun open(request: FeedbackRequest) {
        if (state.phase == FeedbackPhase.SENDING) return
        // A request with no values (the sidebar button) keeps the draft as it is.
        if (request.topic == null && request.error == null) return
        update(
            state.copy(
                topic = request.topic ?: state.topic,
                error = FeedbackRules.errorText(request.error) ?: state.error,
                attachLog = request.attachLog || state.attachLog,
            ),
        )
    }

    private fun edit(change: FeedbackViewState.() -> FeedbackViewState) {
        if (!state.inputsEnabled) return
        val changed = state.change()
        // A change after a failure goes back to a normal draft. The report ID stays.
        update(if (changed.phase == FeedbackPhase.FAILED) changed.copy(phase = FeedbackPhase.EDITING, status = "", statusIsError = false) else changed)
    }

    private fun send() {
        if (!state.sendEnabled && !state.showTryAgain) return
        val sender = sender ?: return
        val draft = state
        preferences.email = draft.email
        update(draft.copy(phase = FeedbackPhase.SENDING, status = "Sending the report…", statusIsError = false))
        val id = reportId
        background.execute {
            val result = runCatching { sender.send(report(draft, logFor(id, draft.attachLog))) }
                .getOrElse { FeedbackSendResult.Failed(FeedbackFailure.NO_CONNECTION) }
            ui.invoke(Runnable { onSent(id, draft, result) })
        }
    }

    private fun onSent(id: UUID, draft: FeedbackViewState, result: FeedbackSendResult) {
        if (id != reportId) return
        when (result) {
            FeedbackSendResult.Sent -> {
                // The next open starts a new report. The email address stays in the preferences.
                newReport()
                view?.renderEffect(FeedbackViewEffect.Sent(thanks(id, draft.email)))
            }
            is FeedbackSendResult.Failed -> {
                failure = result.failure
                update(state.copy(phase = FeedbackPhase.FAILED, status = failureText(result.failure), statusIsError = true))
            }
        }
    }

    private fun showData() {
        if (!state.showDataEnabled) return
        val draft = state
        val id = reportId
        background.execute {
            val text = FeedbackReport.dataText(report(draft, logFor(id, draft.attachLog)))
            ui.invoke(Runnable { view?.renderEffect(FeedbackViewEffect.ShowData(text)) })
        }
    }

    private fun copyReport() {
        copyToClipboard(plainText(state))
        update(state.copy(status = "The report is on the clipboard. Paste it in an email to $contactEmail.", statusIsError = false))
    }

    private fun writeEmail() {
        val text = plainText(state)
        val body = if (text.length <= MAIL_BODY_LIMIT) text else {
            copyToClipboard(text)
            text.take(MAIL_BODY_LIMIT) + "\n\n[The text is too long for this email. The full report is on the clipboard. Paste it here.]"
        }
        val subject = "BananaShot: " + (state.topic?.title ?: "Feedback")
        val opened = openMail(URI("mailto:$contactEmail?subject=${encode(subject)}&body=${encode(body)}"))
        update(
            if (opened) state.copy(status = "Your email app opened with the report. Send the email from there.", statusIsError = false)
            else state.copy(status = "The app cannot open your email app. Copy the report and send it to $contactEmail.", statusIsError = true),
        )
    }

    private fun newReport() {
        reportId = newReportId()
        log = null
        failure = null
        update(emptyDraft())
    }

    private fun logFor(id: UUID, attach: Boolean): String? {
        if (!attach) return null
        synchronized(this) {
            log?.takeIf { it.reportId == id }?.let { return it.text }
        }
        val text = runCatching { readLog() }.getOrNull()
        synchronized(this) { log = CachedLog(id, text) }
        return text
    }

    private fun report(draft: FeedbackViewState, logText: String?) = FeedbackReport(
        reportId = reportId,
        topic = draft.topic!!,
        message = draft.message.trim(),
        email = draft.email.trim().ifEmpty { null },
        system = system,
        error = draft.error,
        log = logText,
    )

    private fun plainText(draft: FeedbackViewState): String = buildString {
        appendLine("Topic: ${draft.topic?.title ?: "-"}")
        appendLine("Report ID: $reportId")
        appendLine("App: ${draft.systemSummary}")
        appendLine()
        appendLine(draft.message.trim())
        draft.error?.let {
            appendLine()
            appendLine("Error:")
            appendLine(it)
        }
    }.trimEnd()

    private fun update(next: FeedbackViewState) {
        state = derive(next)
        view?.render(state)
    }

    /** The values that follow from the others. */
    private fun derive(s: FeedbackViewState): FeedbackViewState {
        val email = s.email.trim()
        val emailError = if (email.isEmpty() || FeedbackRules.isValidEmail(email)) null
        else "Write the full address, for example name@example.com, or leave the field empty."
        val messageError = if (s.message.length > FeedbackRules.MAX_MESSAGE)
            "The message is too long: ${s.message.length} of ${FeedbackRules.MAX_MESSAGE} characters." else null
        val complete = s.topic != null && s.message.isNotBlank() && messageError == null && emailError == null
        val editable = s.phase == FeedbackPhase.EDITING || s.phase == FeedbackPhase.FAILED
        val tryAgain = s.phase == FeedbackPhase.FAILED && failure?.canTryAgain == true && complete
        return s.copy(
            emailError = emailError,
            messageError = messageError,
            inputsEnabled = editable,
            // After a failure, only "Try again" sends, and only when a new try can succeed. An edit enables Send again.
            sendEnabled = s.canSendOnline && s.phase == FeedbackPhase.EDITING && complete,
            showTryAgain = tryAgain,
            showFallbacks = !s.canSendOnline || s.phase == FeedbackPhase.FAILED,
            showDataEnabled = editable && complete,
            status = if (!s.canSendOnline && s.status.isEmpty()) NO_ENDPOINT else s.status,
        )
    }

    private fun thanks(id: UUID, email: String): String = buildString {
        append("Thank you. The author has your report. Report ID: $id.")
        if (email.isBlank()) append(" You gave no email address, so the author cannot reply.")
        else append(" The author can reply to ${email.trim()}.")
    }

    private companion object {
        /** An email app can refuse a longer `mailto:` link. */
        const val MAIL_BODY_LIMIT = 1500
        const val NO_ENDPOINT = "This version of the app cannot send reports. Copy the report or write an email."

        fun failureText(failure: FeedbackFailure): String = when (failure) {
            FeedbackFailure.NO_CONNECTION -> "The app cannot connect to the server. Check the internet connection and try again."
            FeedbackFailure.SERVER -> "The server cannot take the report now. Try again later."
            FeedbackFailure.DISABLED -> "The server does not take reports now. Copy the report or write an email."
            FeedbackFailure.RATE_LIMITED -> "Too many reports came from your network in this hour. Try again later or write an email."
            FeedbackFailure.REFUSED -> "The server refused the report. Copy the report or write an email."
        } + " The app keeps your text."

        fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
    }
}
