package org.litvin.media.mpv

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Reads still frames with a hidden mpv player. The player has no window (vo=null).
 *
 * The player keeps the video open and uses the hardware decoder. Thus a seek takes much less time than
 * a new ffmpeg process, which must open the file and decode up to one second of video in software.
 * The Share tab uses it, so that a click on the moment strip shows the frame quickly, as on the Points tab.
 *
 * The player closes after [idleCloseMs] without a read, because the decoder uses much memory.
 * Call [read] from one thread at a time. The calls are synchronized.
 */
class MpvFrameReader(private val idleCloseMs: Long = DEFAULT_IDLE_CLOSE_MS) : AutoCloseable {
    private var core: MpvCore? = null
    private var loadedPath: String? = null
    private var loadedFilter: String? = null
    private val events = LinkedBlockingQueue<Event>()
    private var idleClose: ScheduledFuture<*>? = null
    private var reads = 0L

    private data class Event(val id: Int, val endReason: Int? = null)

    /**
     * Returns the first frame at or after [seekMs], as `ffmpeg -ss`, with the FFmpeg filters [filters] applied.
     * The result is null when libmpv is not available or mpv cannot read the frame. Then use ffmpeg.
     */
    @Synchronized
    fun read(sourcePath: String, seekMs: Long, filters: String): BufferedImage? {
        idleClose?.cancel(false)
        val read = ++reads
        try {
            return readFrame(sourcePath, seekMs, filters)
        } catch (failure: Exception) {
            logger.warn(failure) { "mpv cannot read the frame at $seekMs ms of $sourcePath" }
            closeCore()
            return null
        } finally {
            idleClose = timer.schedule({ closeIfIdle(read) }, idleCloseMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun readFrame(sourcePath: String, seekMs: Long, filters: String): BufferedImage? {
        val mpv = core ?: createCore() ?: return null
        // mpv keeps the frames up to 5 ms before the seek target. ffmpeg starts at the target. With 5 ms more, the frame is the same.
        val seconds = String.format(Locale.US, "%.3f", (seekMs + HR_SEEK_TOLERANCE_MS) / 1000.0)
        events.clear()
        if (sourcePath != loadedPath || filters != loadedFilter) {
            // A new filter applies only after a new load. A change of the filter of a loaded video
            // makes mpv seek again, and that seek can end after the seek of this request.
            loadedPath = null
            loadedFilter = null
            // The ffmpeg frame has square pixels. Without "setsar", mpv can make the frame one pixel wider for the aspect.
            val graph = "$filters,setsar=1"
            if (mpv.setPropertySync("vf", "lavfi=graph=%${graph.toByteArray().size}%$graph") < 0) return null
            mpv.commandSync("loadfile", sourcePath, "replace", "-1", "start=$seconds")
            // A video that played before can send events. Only the events after FILE_LOADED are for this video.
            if (!await(LibMpv.EVENT_FILE_LOADED) || !await(LibMpv.EVENT_PLAYBACK_RESTART)) return fail(sourcePath)
            loadedPath = sourcePath
            loadedFilter = filters
            logger.info { "mpv frame reader loaded $sourcePath: hwdec=${mpv.getProperty("hwdec-current")}" }
        } else {
            if (mpv.commandSync("seek", seconds, "absolute+exact") < 0) return fail(sourcePath)
            if (!await(LibMpv.EVENT_PLAYBACK_RESTART)) return fail(sourcePath)
        }
        return mpv.commandResult("screenshot-raw", "video", "bgr0", read = ::toImage)
    }

    private fun fail(sourcePath: String): BufferedImage? {
        logger.warn { "mpv did not show the frame of $sourcePath" }
        closeCore()
        return null
    }

    /** Waits for the event [id]. False when the video cannot play, or when the time ends. */
    private fun await(id: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS)
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) return false
            val event = events.poll(left, TimeUnit.NANOSECONDS) ?: return false
            if (event.id == id) return true
            if (event.id == LibMpv.EVENT_END_FILE && event.endReason == LibMpv.END_FILE_REASON_ERROR) return false
        }
    }

    /** "bgr0" has the bytes B, G, R, 0 for each pixel. As an int in little-endian order, this is the RGB int of Java. */
    private fun toImage(node: MpvNode): BufferedImage? {
        val values = node.map()
        val width = values["w"]?.long()?.toInt() ?: return null
        val height = values["h"]?.long()?.toInt() ?: return null
        val stride = values["stride"]?.long() ?: return null
        val data = values["data"]?.bytes() ?: return null
        if (width <= 0 || height <= 0 || values["format"]?.string() != "bgr0") return null
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        for (row in 0 until height) data.read(row * stride, pixels, row * width, width)
        return image
    }

    private fun createCore(): MpvCore? {
        val lib = LibMpv.instanceOrNull() ?: return null
        val options = listOf(
            "config" to "no",
            "load-scripts" to "no",
            "ytdl" to "no",
            "osc" to "no",
            "terminal" to "no",
            "input-default-bindings" to "no",
            "idle" to "yes",
            "keep-open" to "always",
            "pause" to "yes",
            "vo" to "null",
            "aid" to "no",
            "sid" to "no",
            // Without a video window, the decoder copies each frame to the main memory.
            "hwdec" to "auto-copy-safe",
            "hr-seek" to "yes",
            // The decoder can skip the frames before the seek target. The filters do not change the frame times.
            "hr-seek-framedrop" to "yes",
        )
        return MpvCore(lib, options, "frame-reader") { event ->
            when (event.id) {
                LibMpv.EVENT_FILE_LOADED, LibMpv.EVENT_PLAYBACK_RESTART -> events.offer(Event(event.id))
                LibMpv.EVENT_END_FILE -> events.offer(Event(event.id, event.endFileReason()))
            }
        }.also {
            core = it
            logger.info { "Created the mpv frame reader" }
        }
    }

    /** A task of an older read does nothing. */
    @Synchronized
    private fun closeIfIdle(read: Long) {
        if (read == reads) closeCore()
    }

    private fun closeCore() {
        val old = core ?: return
        core = null
        loadedPath = null
        loadedFilter = null
        old.close()
    }

    @Synchronized
    override fun close() {
        idleClose?.cancel(false)
        closeCore()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        const val DEFAULT_IDLE_CLOSE_MS = 120_000L
        private const val TIMEOUT_MS = 10_000L
        /** mpv keeps the frames up to 5 ms before the target of an exact seek. A seek 5 ms later gives the frame of `ffmpeg -ss`. */
        const val HR_SEEK_TOLERANCE_MS = 5L

        private val timer = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "mpv-frame-reader-idle").apply { isDaemon = true }
        }
    }
}
