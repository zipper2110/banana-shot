package org.litvin.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.AppInfo
import org.litvin.ApplicationLayout
import java.awt.Component
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Why a video file does not open, in words for the user. [title] is one short sentence.
 * [explanation] tells what the application found and what the user can do. A blank line separates the paragraphs.
 * Each text states only what a check proved (see [find]). [UNREADABLE] is for all other cases.
 */
data class VideoProblem(val title: String, val explanation: String) {
    companion object {
        private val APP = AppInfo.NAME

        val NOT_FOUND = VideoProblem(
            "The video file is not found.",
            "$APP could not find the file. The file was moved, renamed, or deleted, " +
                "or the drive with the file is not connected.\n\n" +
                "Put the file back or connect the drive, then try again.",
        )
        val STILL_WRITING = VideoProblem(
            "A different program is still writing this file.",
            "A different program is writing to this file now, for example a copy in File Explorer. " +
                "$APP can open the video only after that program closes the file.\n\n" +
                "Wait until the copy is finished, then try again.",
        )
        val TRUNCATED = VideoProblem(
            "This video cannot be played.",
            "The end of the file is missing: the file is smaller than its own header says. " +
                "This occurs when a copy stopped before the end.\n\n" +
                "Copy the video from the device again.",
        )
        val UNWRITTEN_END = VideoProblem(
            "This video cannot be played.",
            "The last part of the file contains only zeros. A copy sets the full file size first " +
                "and then writes the data, so the copy of this file stopped before the end.\n\n" +
                "Copy the video from the device again.",
        )
        val NOT_FINALIZED = VideoProblem(
            "This recording was not finished correctly.",
            "The file contains video data, but the index that a video player needs is missing. " +
                "The device writes the index when the recording stops. This occurs when the recording stopped " +
                "unexpectedly, for example because the phone turned off or the camera app closed.\n\n" +
                "A new copy does not help, because the file on the device has the same problem. " +
                "A video repair tool can sometimes recover the video.",
        )
        val UNREADABLE = VideoProblem(
            "This video cannot be played.",
            "$APP could not open the file. The file is damaged, or its format is not supported.\n\n" +
                "Make sure that the video plays in a different video player. " +
                "If it does not play, copy the video from the device again.",
        )
        val NO_VIDEO = VideoProblem(
            "This file has no video.",
            "The file has no video stream. For example, it can be an audio file.\n\nSelect a different file.",
        )

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
