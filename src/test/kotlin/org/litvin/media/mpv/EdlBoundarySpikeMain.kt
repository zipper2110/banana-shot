package org.litvin.media.mpv

import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Manual check for B-49 (not a unit test): an mpv EDL of split files against the original whole video.
 *
 * For each time of [times], the check reads the frame of the EDL. Then it finds the frame of the whole video that
 * is equal, in a range of some frames before and after the same time. A good EDL shows "offset 0 frames" at all times,
 * also near the file boundaries. The check also writes the duration of the EDL that mpv reports.
 *
 * Run: mvn -q test-compile exec:java -Dexec.classpathScope=test
 *        -Dapp.mainClass=org.litvin.media.mpv.EdlBoundarySpikeMainKt
 *        -Dexec.args="<whole video> <edl file> <frame ms> <time s>[,<time s>...]"
 */
fun main(args: Array<String>) {
    val whole = File(args[0]).absolutePath
    val edl = File(args[1]).absolutePath
    val frameMs = args[2].toDouble()
    val times = args[3].split(',').map { it.trim().toDouble() }
    println("EDL duration: ${duration(edl)} s, whole duration: ${duration(whole)} s")
    MpvFrameReader().use { reader ->
        for (time in times) {
            val atMs = Math.round(time * 1000)
            val edlFrame = reader.read(edl, atMs, "null")
            if (edlFrame == null) {
                println(String.format(Locale.US, "%8.3f s: the EDL frame is not available", time))
                continue
            }
            val matches = (-4..4).map { step ->
                val wholeFrame = reader.read(whole, atMs + Math.round(step * frameMs), "null")
                step to (wholeFrame?.let { difference(edlFrame, it) } ?: Double.MAX_VALUE)
            }
            val best = matches.minBy { it.second }
            println(String.format(Locale.US, "%8.3f s: offset %+d frames (difference %.2f)", time, best.first, best.second))
        }
    }
    System.exit(0)
}

/** The duration that mpv reports for [path]. */
private fun duration(path: String): String? {
    val lib = LibMpv.instanceOrNull() ?: return "no libmpv"
    val loaded = CountDownLatch(1)
    val options = listOf("config" to "no", "vo" to "null", "ao" to "null", "pause" to "yes", "idle" to "yes", "terminal" to "no")
    MpvCore(lib, options, "edl-spike") { event ->
        if (event.id == LibMpv.EVENT_FILE_LOADED || event.id == LibMpv.EVENT_END_FILE) loaded.countDown()
    }.use { core ->
        core.commandSync("loadfile", path)
        loaded.await(10, TimeUnit.SECONDS)
        return core.getProperty("duration")
    }
}

/** The mean difference of the pixel channels of two frames. Zero for equal frames. */
private fun difference(a: BufferedImage, b: BufferedImage): Double {
    if (a.width != b.width || a.height != b.height) return Double.MAX_VALUE
    var sum = 0L
    var count = 0L
    for (y in 0 until a.height step 4) {
        for (x in 0 until a.width step 4) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            sum += abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) +
                abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)) +
                abs((p and 0xFF) - (q and 0xFF))
            count += 3
        }
    }
    return sum.toDouble() / count
}
