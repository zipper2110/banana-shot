package org.litvin.export

import org.litvin.AppInfo
import org.litvin.MacSecurityBlock
import org.litvin.RenderJob
import org.litvin.WindowsSecurityBlock
import org.litvin.i18n.tr

/** What the user can do after a failed export. */
object ExportFailureAdvice {
    val HARDWARE_ENCODER: String get() = tr("export.advice.hardware")

    val FFMPEG_BLOCKED: String get() = tr("export.advice.blocked", "app" to AppInfo.NAME)

    val FFMPEG_BLOCKED_MAC: String get() = tr("export.advice.blocked.mac", "app" to AppInfo.NAME)

    /**
     * The advice for a failed [job], or null when the app has no advice.
     * A hardware encoder can pass the test encode and still fail, for example after a graphics driver update.
     * The software encoder has no such advice, because its failures have other causes.
     * When Windows or macOS blocked FFmpeg, the failure text already tells what to do, and the encoder is not the cause.
     */
    fun of(job: RenderJob): String? {
        val reason = job.failureReason
        if (reason != null && (reason.startsWith(FFMPEG_BLOCKED) || reason.startsWith(FFMPEG_BLOCKED_MAC))) return null
        return HARDWARE_ENCODER.takeIf { ExportEncoder.entries.any { it.hardware && it.jobLabel == job.encoderLabel } }
    }

    /** The failure text when FFmpeg does not start. It names Windows security when Windows blocked the file (B-22). */
    fun startFailure(cause: Throwable, osName: String = System.getProperty("os.name").orEmpty()): String {
        val technical = "${cause.javaClass.simpleName}: ${cause.message}"
        if (MacSecurityBlock.isMac(osName) && MacSecurityBlock.isLoadBlock(cause)) return macBlockedText()
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

    /**
     * The failure text when FFmpeg exits with [exitCode] and wrote [stderr], or null when the app has no special
     * text. On macOS, a program with a code signature that is not valid starts, but macOS stops it at once (B-13).
     */
    fun exitFailure(exitCode: Int, stderr: String, osName: String = System.getProperty("os.name").orEmpty()): String? =
        macBlockedText().takeIf { MacSecurityBlock.isMac(osName) && MacSecurityBlock.isKilledAtStart(exitCode, stderr) }

    private fun macBlockedText(): String = listOf(
        FFMPEG_BLOCKED_MAC + " " + tr("export.advice.blocked.mac.cause", "app" to AppInfo.NAME),
        tr("export.advice.blocked.action", "app" to AppInfo.NAME),
    ).joinToString("\n\n")
}
