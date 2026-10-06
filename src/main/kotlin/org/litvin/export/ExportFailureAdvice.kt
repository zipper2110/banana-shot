package org.litvin.export

import org.litvin.AppInfo
import org.litvin.RenderJob
import org.litvin.WindowsSecurityBlock

/** What the user can do after a failed export. */
object ExportFailureAdvice {
    const val HARDWARE_ENCODER =
        "The export used a hardware encoder. Select Advanced and then Software (x264), and export again. " +
            "The software encoder is slower, but it works on all PCs."

    val FFMPEG_BLOCKED = "Windows blocked FFmpeg, the video encoder of ${AppInfo.NAME}."

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
            return "Failed to start FFmpeg: $technical. Run ${AppInfo.NAME} distribution diagnostics for details."
        }
        return "$FFMPEG_BLOCKED Windows security, for example Smart App Control, blocked the file. " +
            "Thus ${AppInfo.NAME} cannot export videos.\n\n" +
            "Install the latest version of ${AppInfo.NAME} from the website. If the problem continues, report it.\n\n" +
            "Windows error $windowsError."
    }
}
