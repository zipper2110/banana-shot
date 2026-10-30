package org.litvin.export

import org.litvin.ToneRange
import java.util.concurrent.TimeUnit

object ExportResolutionProbe {
    fun probe(sourcePath: String, ffprobeExecutable: String): ExportResolution? {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height,sample_aspect_ratio", "-of", "csv=p=0", sourcePath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            parse(process.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Throwable) {
            null
        }
    }

    /** Parses the `csv=p=0` output of ffprobe, for example `720,486,40:33`. */
    internal fun parse(output: String): ExportResolution? {
        val fields = output.lineSequence().firstOrNull()?.trim()?.split(',') ?: return null
        val width = fields.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val height = fields.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return ExportResolution(
            ExportVideoOptions.resolutionLabel(width, height), width, height,
            SampleAspect.parse(fields.getOrNull(2)),
        )
    }
}

/**
 * Reads the luma range of the source video for the shadows/highlights curve.
 * A "pc" color range or a "yuvj" pixel format is full range. Anything else, and a failed probe, is limited range.
 */
object SourceToneRangeProbe {
    fun probe(sourcePath: String, ffprobeExecutable: String): ToneRange {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=pix_fmt,color_range", "-of", "default=nw=1", sourcePath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ToneRange.LIMITED
            }
            if (process.exitValue() != 0) return ToneRange.LIMITED
            val output = process.inputStream.bufferedReader().use { it.readText() }
            parse(output)
        } catch (_: Throwable) {
            ToneRange.LIMITED
        }
    }

    /** Parses the `key=value` lines of the ffprobe output. */
    internal fun parse(output: String): ToneRange {
        val entries = output.lineSequence()
            .mapNotNull { line -> line.trim().split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key to value }
        val full = entries["color_range"] == "pc" || entries["pix_fmt"].orEmpty().startsWith("yuvj")
        return if (full) ToneRange.FULL else ToneRange.LIMITED
    }
}

/**
 * Finds if the source has an audio stream. A camera or a screen recorder can write a video with no audio.
 * A failed probe returns true, so the export uses the source audio as before.
 */
object SourceAudioProbe {
    fun probe(sourcePath: String, ffprobeExecutable: String): Boolean {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable, "-v", "error", "-select_streams", "a",
                "-show_entries", "stream=codec_type", "-of", "csv=p=0", sourcePath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return true
            }
            if (process.exitValue() != 0) return true
            parse(process.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Throwable) {
            true
        }
    }

    /** Parses the `csv=p=0` output of ffprobe: one `audio` line for each audio stream. */
    internal fun parse(output: String): Boolean = output.lineSequence().any { it.trim() == "audio" }
}

/**
 * Finds the sample rate of the first audio stream of the source. The slow sound of a replay (B-50) changes the
 * rate and then converts back to it. A failed probe returns [DEFAULT_RATE].
 */
object SourceSampleRateProbe {
    const val DEFAULT_RATE = 48000

    fun probe(sourcePath: String, ffprobeExecutable: String): Int {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable, "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=sample_rate", "-of", "csv=p=0", sourcePath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return DEFAULT_RATE
            }
            if (process.exitValue() != 0) return DEFAULT_RATE
            parse(process.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Throwable) {
            DEFAULT_RATE
        }
    }

    /** Parses the `csv=p=0` output of ffprobe: the sample rate on the first line. */
    internal fun parse(output: String): Int =
        output.lineSequence().firstNotNullOfOrNull { it.trim().trimEnd(',').toIntOrNull()?.takeIf { rate -> rate > 0 } }
            ?: DEFAULT_RATE
}
