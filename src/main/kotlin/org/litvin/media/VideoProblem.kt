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
 * [explanation] tells what happened, the probable cause, and what the user can do. A blank line separates the paragraphs.
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
        val INCOMPLETE = VideoProblem(
            "This video cannot be played.",
            "$APP could not open the file because the end of the file is missing. " +
                "This occurs when the copy from the phone or camera is not finished, " +
                "or when the copy stopped before the end.\n\n" +
                "If the file is still copying, wait until the copy is finished, then try again. " +
                "If the copy is finished, copy the video from the device again.",
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

        /** Finds the problem from the error lines of FFmpeg or mpv. */
        fun fromErrors(file: File, errorLines: List<String>): VideoProblem = when {
            !file.isFile -> NOT_FOUND
            // An MP4 file from a phone has the index (the moov atom) at the end. A copy that is not finished has no index.
            // A file that is not an MP4 file gives the same error, so the file must also start with an MP4 header.
            errorLines.any { it.contains("moov atom not found", ignoreCase = true) } && startsWithMp4Header(file) -> INCOMPLETE
            else -> UNREADABLE
        }

        /** True when the file starts with an `ftyp` box, as MP4 and MOV files do. */
        private fun startsWithMp4Header(file: File): Boolean = runCatching {
            val head = ByteArray(8)
            val count = file.inputStream().use { it.readNBytes(head, 0, head.size) }
            count == head.size && String(head, 4, 4, Charsets.US_ASCII) == "ftyp"
        }.getOrDefault(false)
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

/** Reads the header of the first video stream with ffprobe. This takes less than one second for a local file. */
class FfprobeVideoReadCheck(
    private val ffprobeExecutable: () -> String = { ApplicationLayout.current().ffprobeExecutable },
    private val timeoutSeconds: Long = 10,
) : VideoReadCheck {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun problem(videoPath: String): VideoProblem? {
        val file = File(videoPath.trim())
        if (!file.isFile) return VideoProblem.NOT_FOUND
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
        return VideoProblem.fromErrors(file, lines)
    }
}
