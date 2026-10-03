package org.litvin.ui.feedback.presenter

import org.litvin.feedback.FeedbackTopic

/** What an entry point fills in (T3 of B-8). The user can change all of it. */
data class FeedbackRequest(
    val topic: FeedbackTopic? = null,
    val error: String? = null,
    val attachLog: Boolean = false,
)

/** After a successful send, the form closes and starts a new report, so there is no "sent" phase. */
enum class FeedbackPhase { EDITING, SENDING, FAILED }

data class FeedbackViewState(
    val topic: FeedbackTopic? = null,
    val message: String = "",
    val email: String = "",
    val attachLog: Boolean = false,
    /** The text of the error dialog that opened the form, or null. */
    val error: String? = null,
    /** The app version, the version of Windows, and the Java version. */
    val systemSummary: String = "",
    /** The address that the author replies from (decision 7 of B-8). */
    val contactEmail: String = "",
    val phase: FeedbackPhase = FeedbackPhase.EDITING,
    /** The line under the form: the progress, the result, or the reason of a failure. */
    val status: String = "",
    val statusIsError: Boolean = false,
    val emailError: String? = null,
    val messageError: String? = null,
    /** False when the build has no endpoint. Then "Copy report" and "Write an email" replace "Send". */
    val canSendOnline: Boolean = true,
    val sendEnabled: Boolean = false,
    /** True after a failure that a new request can correct. Then "Try again" replaces "Send". */
    val showTryAgain: Boolean = false,
    /** "Copy report" and "Write an email". */
    val showFallbacks: Boolean = false,
    val showDataEnabled: Boolean = false,
    val inputsEnabled: Boolean = true,
)

sealed interface FeedbackIntent {
    data class Open(val request: FeedbackRequest) : FeedbackIntent
    data class SelectTopic(val topic: FeedbackTopic) : FeedbackIntent
    data class EditMessage(val text: String) : FeedbackIntent
    data class EditEmail(val text: String) : FeedbackIntent
    data class SetAttachLog(val attach: Boolean) : FeedbackIntent
    data object RemoveError : FeedbackIntent
    data object Send : FeedbackIntent
    data object ShowData : FeedbackIntent
    data object CopyReport : FeedbackIntent
    data object WriteEmail : FeedbackIntent
}

sealed interface FeedbackViewEffect {
    /** Opens a window with the exact data that the app sends. */
    data class ShowData(val text: String) : FeedbackViewEffect

    /** The server has the report. The form closes, and a popup shows [text] with the report ID. */
    data class Sent(val text: String) : FeedbackViewEffect
}

interface FeedbackView {
    fun render(state: FeedbackViewState)
    fun renderEffect(effect: FeedbackViewEffect)
}

interface FeedbackPresenter {
    fun attach(view: FeedbackView)
    fun detach()
    fun onIntent(intent: FeedbackIntent)
}
