package org.litvin.export

import org.litvin.AppInfo
import org.litvin.RenderJob
import org.litvin.WindowsSecurityBlock
import org.litvin.i18n.tr

/** What the user can do after a failed export. */
object ExportFailureAdvice {
    val HARDWARE_ENCODER: String get() = tr("export.advice.hardware")

    val FFMPEG_BLOCKED: String get() = tr("export.advice.blocked", "app" to AppInfo.NAME)

    /**
     * The advice for a failed [job], or null when the app has no advice.
     * A hardware encoder can pass the test encode and still fail, for example after a graphics driver update.
     * The software encoder has no such advice, because its failures have other causes.
     * When Windows blocked FFmpeg, the failure text already tells what to do, and the encoder is not the cause.
     */
    fun of(job: RenderJob): String? {
        if (job.failureReason?.startsWith(FFMPEG_BLOCKED) == true) return null
        return HARDWARE_ENCODER.takeIf { ExportEncoder.entries.any { it.hardware && it.jobLabel == job.encoderLabel } }
    }

    /** The failure text when FFmpeg does not start. It names Windows security when Windows blocked the file (B-22). */
    fun startFailure(cause: Throwable): String {
        val technical = "${cause.javaClass.simpleName}: ${cause.message}"
        val windowsError = WindowsSecurityBlock.createProcessError(cause)
        if (!WindowsSecurityBlock.isBlockError(windowsError)) {
            return tr("export.advice.startFailed", "detail" to technical, "app" to AppInfo.NAME)
        }
        return listOf(
            FFMPEG_BLOCKED + " " + tr("export.advice.blocked.cause", "app" to AppInfo.NAME),
            tr("export.advice.blocked.action", "app" to AppInfo.NAME),
            tr("export.advice.blocked.code", "code" to windowsError.toString()),
        ).joinToString("\n\n")
    }
}
