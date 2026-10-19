package org.litvin.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.AppInfo
import org.litvin.ApplicationLayout
import org.litvin.WindowsSecurityBlock
import org.litvin.i18n.tr
import java.awt.Component
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Why a video file does not open, in words for the user. [title] is one short sentence.
 * [explanation] tells what the application found and what the user can do. A blank line separates the paragraphs.
 * Each text states only what a check proved (see [find]). [UNREADABLE] is for all other cases.
 * When [reportable] is true, the cause can be a bug of the application, and the view shows "Report this problem".
 * [details] is the technical cause for the report. The view does not show it.
 */
data class VideoProblem(
    val title: String,
    val explanation: String,
    val reportable: Boolean = false,
    val details: String? = null,
) {
    /** The text for the feedback form: the explanation and the technical cause. */
    fun reportText(): String = explanation + (details?.let { "\n\n$it" } ?: "")

    companion object {
        private val APP = AppInfo.NAME

        // The problems are getters, so that the texts are in the app language.
        val NOT_FOUND: VideoProblem
            get() = VideoProblem(tr("video.notFound"), tr("video.notFound.text", "app" to APP))
        val STILL_WRITING: VideoProblem
            get() = VideoProblem(tr("video.stillWriting"), tr("video.stillWriting.text", "app" to APP))
        val TRUNCATED: VideoProblem
            get() = VideoProblem(tr("video.cannotPlay"), tr("video.truncated.text", "app" to APP))
        val UNWRITTEN_END: VideoProblem
            get() = VideoProblem(tr("video.cannotPlay"), tr("video.unwrittenEnd.text", "app" to APP))
        val NOT_FINALIZED: VideoProblem
            get() = VideoProblem(tr("video.notFinalized"), tr("video.notFinalized.text", "app" to APP))
        val UNREADABLE: VideoProblem
            get() = VideoProblem(tr("video.cannotPlay"), tr("video.unreadable.text", "app" to APP), reportable = true)
        val PREVIEW_BLOCKED: VideoProblem
            get() = VideoProblem(tr("video.previewBlocked"), tr("video.previewBlocked.text", "app" to APP), reportable = true)
        val PREVIEW_FAILED: VideoProblem
            get() = VideoProblem(tr("video.previewFailed"), tr("video.previewFailed.text", "app" to APP), reportable = true)
        val NO_VIDEO: VideoProblem
            get() = VideoProblem(tr("video.noVideo"), tr("video.noVideo.text", "app" to APP))

        /**
         * Returns the problem that the checks prove, or null when they find no problem.
         * The checks read only some bytes of the file, so they are fast also for a large file.
         */
        fun find(file: File): VideoProblem? = when {
            !file.isFile -> NOT_FOUND
            FileWriteCheck.isOpenForWriting(file) -> STILL_WRITING
            else -> when (Mp4Structure.inspect(file)) {
                Mp4Defect.TRUNCATED -> TRUNCATED
                Mp4Defect.UNWRITTEN_END -> UNWRITTEN_END
                Mp4Defect.NOT_FINALIZED -> NOT_FINALIZED
                null -> null
            }
        }

        /** The problem when the video player does not start. [windowsError] is the Windows error of the DLL load. */
        fun previewFailure(cause: Throwable?, windowsError: Int?): VideoProblem {
            val details = listOfNotNull(
                windowsError?.let { "Windows error $it." },
                cause?.let { "${it.javaClass.simpleName}: ${it.message}" },
            ).joinToString("\n").ifEmpty { null }
            val problem = if (WindowsSecurityBlock.isBlockError(windowsError)) PREVIEW_BLOCKED else PREVIEW_FAILED
            return problem.copy(details = details)
        }
    }
}

/** A view that a player shows in place of the video when the video does not open. */
interface VideoErrorView {
    val component: Component
    fun showProblem(problem: VideoProblem)
}

/** Makes a [VideoErrorView]. [onRetry] loads the video again. */
fun interface VideoErrorViewFactory {
    fun create(onRetry: () -> Unit): VideoErrorView
}

/** Checks that a video file opens, before the application uses it. */
fun interface VideoReadCheck {
    /** Returns the problem, or null when the file opens. Also returns null when the check cannot run. */
    fun problem(videoPath: String): VideoProblem?
}

/**
 * Runs the checks of [VideoProblem.find] first. Then it reads the header of the first video stream with ffprobe.
 * This takes less than one second for a local file.
 */
class FfprobeVideoReadCheck(
    private val ffprobeExecutable: () -> String = { ApplicationLayout.current().ffprobeExecutable },
    private val timeoutSeconds: Long = 10,
) : VideoReadCheck {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun problem(videoPath: String): VideoProblem? {
        val file = File(videoPath.trim())
        VideoProblem.find(file)?.let { return it }
        val process = try {
            ProcessBuilder(
                ffprobeExecutable(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_type",
                "-of", "csv=p=0",
                file.absolutePath,
            ).redirectErrorStream(true).start()
        } catch (e: IOException) {
            // A missing ffprobe must not stop the user. The player shows the problem later.
            logger.warn(e) { "Cannot start ffprobe to check $file" }
            return null
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            logger.warn { "ffprobe did not check $file in ${timeoutSeconds}s" }
            return null
        }
        val lines = process.inputStream.bufferedReader().use { it.readLines() }.map { it.trim() }.filter { it.isNotEmpty() }
        if (process.exitValue() == 0) return if ("video" in lines) null else VideoProblem.NO_VIDEO
        logger.warn { "ffprobe cannot read $file: ${lines.joinToString(" | ")}" }
        return VideoProblem.UNREADABLE
    }
}
