package org.litvin.ui.commons

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.ApplicationLayout
import org.litvin.FFmpegCommandBuilder
import org.litvin.FfmpegColorAdjustmentStrategy
import org.litvin.ToneRange
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.GeometryPlan
import org.litvin.export.ExportResolutionProbe
import org.litvin.export.SourceToneRangeProbe
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/** One video frame: the source, the time, and the adjustments of the project, as the export shows it. */
data class VideoFrameRequest(
    val sourcePath: String,
    /** The source time of the frame. */
    val atMs: Long,
    val adjustments: AdjustmentsV1,
)

/**
 * Reads one video frame with ffmpeg, on a background thread, for a still preview such as the Scoreboard style window.
 * The last [cacheSize] frames stay in a cache, so a second request for the same frame does not run ffmpeg again.
 */
class VideoFrameLoader(
    private val cacheSize: Int = DEFAULT_CACHE_SIZE,
    private val extract: (VideoFrameRequest) -> BufferedImage? = ::extractWithFfmpeg,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "video-frame-loader").apply { isDaemon = true }
    }
    private val cache = object : LinkedHashMap<VideoFrameRequest, BufferedImage>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<VideoFrameRequest, BufferedImage>) = size > cacheSize
    }

    /** The frame when it is in the cache, or null. */
    fun cached(request: VideoFrameRequest): BufferedImage? = synchronized(cache) { cache[request] }

    /** Reads the frame and calls [onLoaded] on the event thread. The image is null when ffmpeg cannot read the frame. */
    fun load(request: VideoFrameRequest, onLoaded: (BufferedImage?) -> Unit) {
        cached(request)?.let { image -> return onLoaded(image) }
        executor.execute {
            val image = cached(request) ?: try {
                extract(request)?.also { synchronized(cache) { cache[request] = it } }
            } catch (failure: Exception) {
                logger.warn(failure) { "Cannot read the video frame for the preview" }
                null
            }
            SwingUtilities.invokeLater { onLoaded(image) }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        const val DEFAULT_CACHE_SIZE = 4

        /** The width of the frame. The preview makes it smaller or larger to fit. */
        private const val FRAME_WIDTH = 1280

        private fun extractWithFfmpeg(request: VideoFrameRequest): BufferedImage? {
            if (!File(request.sourcePath).isFile) return null
            val layout = ApplicationLayout.current()
            // The crop needs the source size, as in the export. Probe it only when the project has a crop or a rotation.
            val sourceSize = request.adjustments
                .takeIf { !GeometryPlan.of(it, FRAME_WIDTH, FRAME_WIDTH * 9 / 16).isIdentity }
                ?.let { ExportResolutionProbe.probe(request.sourcePath, layout.ffprobeExecutable) }
            val toneRange = request.adjustments
                .takeIf { FfmpegColorAdjustmentStrategy.map(it).hasToneAdjustments }
                ?.let { SourceToneRangeProbe.probe(request.sourcePath, layout.ffprobeExecutable) }
                ?: ToneRange.LIMITED
            val output = File.createTempFile("video-frame", ".png")
            try {
                val args = listOf(layout.ffmpegExecutable) + FFmpegCommandBuilder.stillFrameArgs(
                    sourcePath = request.sourcePath,
                    atMs = request.atMs,
                    outputPath = output.absolutePath,
                    width = FRAME_WIDTH,
                    adjustments = request.adjustments,
                    sourceWidth = sourceSize?.width,
                    sourceHeight = sourceSize?.height,
                    toneRange = toneRange,
                )
                val process = ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    logger.warn { "ffmpeg did not read the preview frame in 30 s" }
                    return null
                }
                if (process.exitValue() != 0 || output.length() == 0L) return null
                return ImageIO.read(output)
            } finally {
                output.delete()
            }
        }
    }
}
