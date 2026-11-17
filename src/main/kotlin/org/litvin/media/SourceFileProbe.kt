package org.litvin.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.ApplicationLayout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The times and the format of one video file of a match of several files (B-49).
 * [format] has the codec, the frame size, the frame rate and the pixel format of each video stream, and the codec,
 * the sample rate and the channels of each audio stream. Two files with the same [format] join correctly.
 */
data class SourceFileInfo(val file: SourceFile, val format: List<String>)

/** Reads the times and the format of a video file. Returns null when the file cannot be read. */
fun interface SourceFileProbe {
    fun probe(path: String): SourceFileInfo?
}

/** Reads the times and the format with one ffprobe call. */
class FfprobeSourceFileProbe(
    private val ffprobeExecutable: () -> String = { ApplicationLayout.current().ffprobeExecutable },
) : SourceFileProbe {
    override fun probe(path: String): SourceFileInfo? {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable(),
                "-v", "error",
                "-show_entries",
                "format=start_time,duration:stream=codec_type,codec_name,width,height,r_frame_rate,pix_fmt,sample_rate,channels",
                "-of", "flat",
                File(path).absolutePath,
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) {
                logger.warn { "ffprobe cannot read $path: ${output.trim()}" }
                return null
            }
            parse(File(path).absolutePath, output)
        } catch (e: Exception) {
            logger.warn(e) { "Cannot probe $path" }
            null
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /**
         * Parses the `-of flat` output of ffprobe, for example `format.duration="21.021016"`.
         * Other streams than video and audio (for example the data streams of action cameras) are not in the format.
         */
        internal fun parse(path: String, output: String): SourceFileInfo? {
            val values = output.lineSequence()
                .mapNotNull { line -> line.trim().split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key to value.trim().removeSurrounding("\"") }
            val duration = values["format.duration"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
            val start = values["format.start_time"]?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            val streams = values.keys
                .mapNotNull { Regex("""streams\.stream\.(\d+)\.""").find(it)?.groupValues?.get(1)?.toInt() }
                .distinct().sorted()
            val format = streams.mapNotNull { index ->
                fun value(key: String) = values["streams.stream.$index.$key"].orEmpty()
                when (value("codec_type")) {
                    "video" -> "video ${value("codec_name")} ${value("width")}x${value("height")} ${value("r_frame_rate")} ${value("pix_fmt")}"
                    "audio" -> "audio ${value("codec_name")} ${value("sample_rate")} Hz ${value("channels")} ch"
                    else -> null
                }
            }
            return SourceFileInfo(SourceFile(path, Math.round(start * 1000), Math.round(duration * 1000)), format)
        }
    }
}

/** The files of a match of several files that have a different format from the first file (B-49). */
object SourceFormats {
    /** The files with a format different from the first file. Empty when all files have the same format. */
    fun different(files: List<SourceFileInfo>): List<SourceFileInfo> {
        val first = files.firstOrNull() ?: return emptyList()
        return files.drop(1).filter { it.format != first.format }
    }

    /** A text for a feedback report: the format of each file. */
    fun report(files: List<SourceFileInfo>): String = files.joinToString("\n") { info ->
        "${File(info.file.path).name}: ${info.format.joinToString("; ")}"
    }
}
