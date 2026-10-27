package org.litvin.ui.commons

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.ApplicationLayout
import org.litvin.FFmpegCommandBuilder
import org.litvin.FfmpegColorAdjustmentStrategy
import org.litvin.ToneRange
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.ColorTimeline
import org.litvin.adjustments.GeometryTimeline
import org.litvin.export.ExportResolution
import org.litvin.export.ExportResolutionProbe
import org.litvin.export.SourceToneRangeProbe
import org.litvin.media.mpv.MpvFrameReader
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** One video frame: the source, the time, and the adjustments of the project, as the export shows it. */
data class VideoFrameRequest(
    val sourcePath: String,
    /** The source time of the frame. */
    val atMs: Long,
    val adjustments: AdjustmentsV1,
    /** The width of the frame. The height is 9/16 of it. The Share tab reads a larger frame for its zoom. */
    val width: Int = VideoFrameLoader.FRAME_WIDTH,
)

/** The key frames of a time range of the source, for a strip of small frames such as the moment strip of the Thumbnail tab. */
data class KeyFramesRequest(
    val sourcePath: String,
    val startMs: Long,
    val endMs: Long,
    val adjustments: AdjustmentsV1,
    val width: Int,
)

/** One key frame and its source time. */
class KeyFrame(val atMs: Long, val image: BufferedImage)

/**
 * Reads one video frame with ffmpeg, on a background thread, for a still preview such as the Scoreboard style window.
 * The last [cacheSize] frames stay in a cache, so a second request for the same frame does not run ffmpeg again.
 * It also reads all key frames of a time range with one ffmpeg process ([loadKeyFrames]).
 */
class VideoFrameLoader(
    private val cacheSize: Int = DEFAULT_CACHE_SIZE,
    private val extractKeyFrames: (KeyFramesRequest) -> List<KeyFrame> = ::extractKeyFramesWithFfmpeg,
    private val extract: (VideoFrameRequest) -> BufferedImage? = ::extractWithFfmpeg,
) {
    @Volatile
    private var worker: Thread? = null
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "video-frame-loader").apply { isDaemon = true }.also { worker = it }
    }
    private val cache = object : LinkedHashMap<VideoFrameRequest, BufferedImage>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<VideoFrameRequest, BufferedImage>) = size > cacheSize
    }

    private val keyFrameCache = object : LinkedHashMap<KeyFramesRequest, List<KeyFrame>>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<KeyFramesRequest, List<KeyFrame>>) = size > cacheSize
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

    /** The key frames when they are in the cache, or null. */
    fun cachedKeyFrames(request: KeyFramesRequest): List<KeyFrame>? = synchronized(keyFrameCache) { keyFrameCache[request] }

    /**
     * Reads the key frames of the range and calls [onLoaded] on the event thread.
     * The list is empty when ffmpeg cannot read the video or the range has no key frame.
     */
    fun loadKeyFrames(request: KeyFramesRequest, onLoaded: (List<KeyFrame>) -> Unit) {
        cachedKeyFrames(request)?.let { frames -> return onLoaded(frames) }
        executor.execute {
            val frames = cachedKeyFrames(request) ?: try {
                extractKeyFrames(request).also { frames ->
                    if (frames.isNotEmpty()) synchronized(keyFrameCache) { keyFrameCache[request] = frames }
                }
            } catch (failure: Exception) {
                logger.warn(failure) { "Cannot read the key frames for the preview" }
                emptyList()
            }
            SwingUtilities.invokeLater { onLoaded(frames) }
        }
    }

    /**
     * Removes the requests that did not start. Their [load] callbacks are not called.
     * For example, the frame strip of the Thumbnail tab drops the frames of the point before.
     * With [stopRunning], it also stops the ffmpeg process that runs now. The callback of that request gets no image.
     */
    fun cancelPending(stopRunning: Boolean = false) {
        executor.queue.clear()
        if (stopRunning) worker?.let { running[it] }?.destroyForcibly()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        const val DEFAULT_CACHE_SIZE = 4

        /** The default width of the frame. The preview makes it smaller or larger to fit. */
        const val FRAME_WIDTH = 1280

        /** The ffmpeg process that each loader thread runs now, so that [cancelPending] can stop it. */
        private val running = ConcurrentHashMap<Thread, Process>()

        /** ffprobe opens the whole file, which takes time for a long video. So the app probes each source only once. */
        private val sourceSizes = ConcurrentHashMap<String, Optional<ExportResolution>>()
        private val toneRanges = ConcurrentHashMap<String, ToneRange>()

        private fun sourceSize(adjustments: AdjustmentsV1, sourcePath: String, width: Int): ExportResolution? {
            // The crop needs the source size, as in the export. Probe it only when the project has a crop or a rotation.
            if (!GeometryTimeline.hasGeometry(adjustments, width, width * 9 / 16)) return null
            return sourceSizes.getOrPut(sourcePath) {
                Optional.ofNullable(ExportResolutionProbe.probe(sourcePath, ApplicationLayout.current().ffprobeExecutable))
            }.orElse(null)
        }

        private fun toneRange(adjustments: AdjustmentsV1, sourcePath: String): ToneRange {
            val tone = ColorTimeline.allColors(adjustments).any { FfmpegColorAdjustmentStrategy.map(it).hasToneAdjustments }
            if (!tone) return ToneRange.LIMITED
            return toneRanges.getOrPut(sourcePath) { SourceToneRangeProbe.probe(sourcePath, ApplicationLayout.current().ffprobeExecutable) }
        }

        /**
         * Reads the frame with a hidden mpv player that keeps the video open. A seek is then much faster than a new ffmpeg process.
         * When mpv cannot read the frame, ffmpeg reads it. The filters are the same, so the frame is the same.
         */
        fun mpvOrFfmpeg(reader: MpvFrameReader = MpvFrameReader()): (VideoFrameRequest) -> BufferedImage? = { request ->
            extractWithMpv(reader, request) ?: extractWithFfmpeg(request)
        }

        private fun extractWithMpv(reader: MpvFrameReader, request: VideoFrameRequest): BufferedImage? {
            if (!File(request.sourcePath).isFile) return null
            val sourceSize = sourceSize(request.adjustments, request.sourcePath, request.width)
            val filters = FFmpegCommandBuilder.stillFrameFilters(
                atMs = request.atMs,
                width = request.width,
                adjustments = request.adjustments,
                sourceWidth = sourceSize?.width,
                sourceHeight = sourceSize?.height,
                toneRange = toneRange(request.adjustments, request.sourcePath),
                lgpl = true,
            )
            return reader.read(request.sourcePath, FFmpegCommandBuilder.stillFrameSeekMs(request.atMs), filters)
        }

        private fun extractWithFfmpeg(request: VideoFrameRequest): BufferedImage? {
            if (!File(request.sourcePath).isFile) return null
            val sourceSize = sourceSize(request.adjustments, request.sourcePath, request.width)
            val args = listOf(ApplicationLayout.current().ffmpegExecutable) + FFmpegCommandBuilder.stillFrameArgs(
                sourcePath = request.sourcePath,
                atMs = request.atMs,
                outputPath = FFmpegCommandBuilder.PIPE,
                width = request.width,
                adjustments = request.adjustments,
                sourceWidth = sourceSize?.width,
                sourceHeight = sourceSize?.height,
                toneRange = toneRange(request.adjustments, request.sourcePath),
            )
            return runForImages(args)?.firstOrNull()
        }

        private fun extractKeyFramesWithFfmpeg(request: KeyFramesRequest): List<KeyFrame> {
            if (!File(request.sourcePath).isFile) return emptyList()
            val sourceSize = sourceSize(request.adjustments, request.sourcePath, request.width)
            val args = listOf(ApplicationLayout.current().ffmpegExecutable) + FFmpegCommandBuilder.keyFramesArgs(
                sourcePath = request.sourcePath,
                startMs = request.startMs,
                endMs = request.endMs,
                width = request.width,
                adjustments = request.adjustments,
                sourceWidth = sourceSize?.width,
                sourceHeight = sourceSize?.height,
                toneRange = toneRange(request.adjustments, request.sourcePath),
            )
            val times = Collections.synchronizedList(mutableListOf<Long>())
            val images = runForImages(args) { line -> FFmpegCommandBuilder.keyFrameTimeMs(line)?.let(times::add) } ?: return emptyList()
            // Each image has one "showinfo" line. Without the times, the app cannot put the frames in the strip.
            if (times.size != images.size) {
                logger.warn { "ffmpeg gave ${images.size} key frames and ${times.size} times" }
                return emptyList()
            }
            return images.mapIndexed { i, image -> KeyFrame(request.startMs.coerceAtLeast(0) + times[i], image) }
        }

        /**
         * Runs ffmpeg and reads the BMP images of its standard output. [onErrorLine] gets the lines of the error output.
         * The result is null when ffmpeg fails or does not stop in [TIMEOUT_SECONDS].
         */
        private fun runForImages(args: List<String>, onErrorLine: (String) -> Unit = {}): List<BufferedImage>? {
            val process = ProcessBuilder(args).start()
            running[Thread.currentThread()] = process
            try {
                return readImages(process, onErrorLine)
            } finally {
                running.remove(Thread.currentThread(), process)
            }
        }

        private fun readImages(process: Process, onErrorLine: (String) -> Unit): List<BufferedImage>? {
            val timedOut = AtomicBoolean(false)
            CompletableFuture.delayedExecutor(TIMEOUT_SECONDS, TimeUnit.SECONDS).execute {
                if (process.isAlive) {
                    timedOut.set(true)
                    process.destroyForcibly()
                }
            }
            val errors = thread(isDaemon = true, name = "video-frame-loader-log") {
                process.errorStream.bufferedReader().useLines { lines -> lines.forEach(onErrorLine) }
            }
            val images = try {
                readBmpImages(process.inputStream)
            } catch (failure: IOException) {
                logger.warn(failure) { "Cannot read the images from ffmpeg" }
                null
            }
            process.waitFor()
            errors.join(1_000)
            if (timedOut.get()) logger.warn { "ffmpeg did not read the preview frames in $TIMEOUT_SECONDS s" }
            if (timedOut.get() || process.exitValue() != 0) return null
            return images
        }

        /** Reads BMP files that follow one another. Each BMP file starts with "BM" and its size in bytes. */
        internal fun readBmpImages(input: InputStream): List<BufferedImage> {
            val data = DataInputStream(input.buffered())
            val images = mutableListOf<BufferedImage>()
            val header = ByteArray(BMP_HEADER)
            while (true) {
                val first = data.read()
                if (first < 0) return images
                header[0] = first.toByte()
                data.readFully(header, 1, BMP_HEADER - 1)
                if (header[0] != 'B'.code.toByte() || header[1] != 'M'.code.toByte()) throw IOException("Not a BMP image")
                val size = ByteBuffer.wrap(header, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (size < BMP_HEADER) throw IOException("Wrong BMP size: $size")
                val file = header.copyOf(size)
                data.readFully(file, BMP_HEADER, size - BMP_HEADER)
                images += ImageIO.read(ByteArrayInputStream(file)) ?: throw IOException("Cannot read a BMP image")
            }
        }

        private const val BMP_HEADER = 14
        private const val TIMEOUT_SECONDS = 30L
    }
}
