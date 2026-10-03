package org.litvin.ui.expiry

import org.litvin.license.update.UpdateAndRestart
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import java.awt.Component
import java.awt.EventQueue
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executor

/**
 * The user interface of "Update and restart" (build-expiry-spec.md, "Update and restart"; E9-S1, E9-S2).
 * The update notice, the expiry warning, the banner, and the dialog of expired mode share one runner. Thus, all
 * their buttons show the same progress, and only one download runs. Use the runner on the EDT only.
 */
internal class UpdateRunner(
    private val update: UpdateAndRestart,
    private val background: Executor,
    /** True when an export runs now. The runner reads it on the EDT. */
    private val exportRuns: () -> Boolean,
    /** The normal close of the app: the project, the changes that wait for a save, and the saved time. */
    private val closeSequence: () -> Unit,
    /** Ends the process after the start of the setup. */
    private val exit: () -> Unit,
    private val openUrl: (String) -> Unit,
    private val parent: () -> Component?,
) {
    /** The text of "Update and restart" while the download runs, or null when no download runs. */
    var progress: String? = null
        private set

    private val listeners = mutableListOf<() -> Unit>()

    /** [listener] runs on the EDT after each change of [progress]. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun openDownloadPage(url: String) = openUrl(url)

    fun updateAndRestart(installerUrl: String) {
        check(EventQueue.isDispatchThread()) { "Use the update runner on the EDT" }
        if (progress != null) return
        val runs = exportRuns()
        if (runs && !confirmExportRestart()) return
        show(ExpiryTexts.DOWNLOADING)
        background.execute {
            val result = update.run(
                installerUrl = installerUrl,
                exportRuns = { runs },
                // The user confirmed already on the EDT.
                confirmExportRestart = { true },
                progress = { bytes, total -> EventQueue.invokeLater { if (progress != null) show(ExpiryTexts.downloading(bytes, total)) } },
                closeSequence = { onEdtAndWait(closeSequence) },
                exit = exit,
            )
            EventQueue.invokeLater { finished(result) }
        }
    }

    private fun finished(result: UpdateAndRestart.Result) {
        show(null)
        if (result is UpdateAndRestart.Result.DownloadFailed) {
            MessageDialog.show(parent(), MessageKind.ERROR, ExpiryTexts.DOWNLOAD_FAILED_TITLE, ExpiryTexts.downloadFailed(result.message))
        }
    }

    private fun confirmExportRestart(): Boolean = MessageDialog.confirm(
        parent(),
        ExpiryTexts.EXPORT_RUNS_TITLE,
        ExpiryTexts.EXPORT_RUNS,
        confirmLabel = ExpiryTexts.UPDATE_AND_RESTART,
    )

    private fun show(text: String?) {
        progress = text
        listeners.forEach { it() }
    }

    private fun onEdtAndWait(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) return action()
        try {
            EventQueue.invokeAndWait(action)
        } catch (failure: InvocationTargetException) {
            throw failure.cause ?: failure
        }
    }
}
