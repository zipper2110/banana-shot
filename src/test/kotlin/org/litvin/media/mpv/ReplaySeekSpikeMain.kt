package org.litvin.media.mpv

import com.sun.jna.Native
import org.litvin.adjustments.AdjustmentsV1
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.roundToLong

/**
 * Manual prototype for B-50 T0 (not a unit test): the freeze at each replay seek.
 *
 * For each test point, the player plays to the point end, seeks back to the replay part, sets the speed and a
 * zoom crop, plays the part, and seeks forward to the point end again. It measures:
 * - "restart": the time from the seek command to the playback-restart event of mpv;
 * - "freeze": the largest wall-clock gap between two time-pos updates around the seek;
 * - "stutter": the largest gap between two time-pos updates in the first second after the restart.
 *
 * Run: mvn -q test-compile exec:java -Dexec.classpathScope=test
 *        -Dapp.mainClass=org.litvin.media.mpv.ReplaySeekSpikeMainKt
 *        -Dexec.args="<video> <output dir> <variant: default|cache> [points]"
 */
fun main(args: Array<String>) {
    val video = File(args.getOrElse(0) { "D:\\PXL_20261021_060501904.mp4" })
    val outDir = File(args.getOrElse(1) { "target/replay-seek-spike" }).apply { mkdirs() }
    val variant = args.getOrElse(2) { "default" }
    val pointCount = args.getOrElse(3) { "6" }.toInt()
    val spike = ReplaySeekSpike(video, variant)
    val report = spike.run(pointCount)
    val file = File(outDir, "${video.nameWithoutExtension}-$variant.txt")
    file.writeText(report)
    println(report)
    println("Report: ${file.absolutePath}")
    System.exit(0)
}

private class ReplaySeekSpike(private val video: File, private val variant: String) {
    private val times = ConcurrentLinkedQueue<Pair<Long, Double>>() // (wall ns, time-pos s)
    private val restarts = LinkedBlockingQueue<Long>()
    private val loaded = CountDownLatch(1)
    @Volatile private var timePos = 0.0
    private lateinit var core: MpvCore
    private lateinit var frame: JFrame

    private val report = StringBuilder()
    private fun log(line: String) {
        println(line)
        report.appendLine(line)
    }

    fun run(pointCount: Int): String {
        val canvas = Canvas().apply { background = Color.BLACK }
        SwingUtilities.invokeAndWait {
            frame = JFrame("B-50 replay seek prototype")
            frame.contentPane.add(canvas, BorderLayout.CENTER)
            frame.setSize(1300, 780)
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
        }
        Thread.sleep(500)
        val lib = LibMpv.instance()
        core = MpvCore(lib, options(Native.getComponentID(canvas)), "replay-spike", ::onEvent)
        core.observe("time-pos", LibMpv.FORMAT_DOUBLE)
        core.command("loadfile", video.absolutePath, "replace")
        check(loaded.await(30, TimeUnit.SECONDS)) { "mpv did not load the file" }
        Thread.sleep(1500)
        val durationS = core.getProperty("duration")?.toDoubleOrNull() ?: error("no duration")
        val width = core.getProperty("video-params/w")?.toIntOrNull() ?: 0
        val height = core.getProperty("video-params/h")?.toIntOrNull() ?: 0
        log("file=${video.name} variant=$variant size=${width}x$height fps=${core.getProperty("container-fps")} " +
            "codec=${core.getProperty("video-codec")} hwdec=${core.getProperty("hwdec-current")} " +
            "duration=${"%.0f".format(Locale.US, durationS)} s")

        val zoom = MpvShaderParams.build(
            AdjustmentsV1(zoom = 2.0f, panX = 0.2f, panY = -0.1f),
            MpvVideoInfo(width, height),
        )
        val plain = MpvShaderParams.build(AdjustmentsV1(), MpvVideoInfo(width, height))
        val back = mutableListOf<Measure>()
        val forward = mutableListOf<Measure>()
        val drops0 = dropCount()

        // The test points are spread over the file, away from the start and the end.
        val span = (durationS - 120.0).coerceAtLeast(30.0)
        for (index in 0 until pointCount) {
            val pointEnd = 60.0 + span * (index + 0.5) / pointCount
            val partStart = pointEnd - REPLAY_BACK_S
            val partEnd = partStart + PART_S

            // Play the last part of the point at normal speed.
            core.commandSync("set", "pause", "yes")
            core.command("seek", fmt(pointEnd - LEAD_IN_S), "absolute+exact")
            awaitRestart()
            core.setProperty("pause", "no")
            waitUntil { timePos >= pointEnd }

            // Into the replay: seek back, slow speed, zoom.
            back += seek(partStart) {
                core.setProperty("speed", "0.5")
                core.setProperty("glsl-shader-opts", zoom)
            }
            waitUntil { timePos >= partEnd }

            // Out of the replay: seek forward to the point end, normal speed, no zoom.
            forward += seek(pointEnd) {
                core.setProperty("speed", "1")
                core.setProperty("glsl-shader-opts", plain)
            }
            Thread.sleep(1200)
            log(String.format(Locale.US, "point %d at %6.0f s: back %s | forward %s", index + 1, pointEnd, back.last(), forward.last()))
        }
        val drops = dropCount() - drops0
        log("")
        log("into the replay (seek back ${"%.0f".format(Locale.US, REPLAY_BACK_S)} s): ${summary(back)}")
        log("out of the replay (seek forward): ${summary(forward)}")
        log("frame drops during the run (decoder + vo): $drops")

        core.close()
        SwingUtilities.invokeAndWait { frame.dispose() }
        return report.toString()
    }

    private fun options(wid: Long): List<Pair<String, String>> = buildList {
        add("config" to "no")
        add("load-scripts" to "no")
        add("osc" to "no")
        add("terminal" to "no")
        add("input-default-bindings" to "no")
        add("osd-level" to "0")
        add("idle" to "yes")
        add("keep-open" to "always")
        add("pause" to "yes")
        add("mute" to "yes")
        add("vo" to "gpu-next")
        add("gpu-api" to "d3d11")
        add("hwdec" to "d3d11va,auto-safe")
        add("hdr-compute-peak" to "no")
        add("hr-seek" to "yes")
        add("hr-seek-framedrop" to "no")
        add("wid" to wid.toString())
        MpvShaderFile.path()?.let { add("glsl-shaders" to it.absolutePath) }
        if (variant == "cache") {
            add("cache" to "yes")
            add("demuxer-seekable-cache" to "yes")
            add("demuxer-max-bytes" to "300MiB")
            add("demuxer-max-back-bytes" to "300MiB")
        }
    }

    private fun onEvent(event: MpvEvent) {
        when (event.id) {
            LibMpv.EVENT_FILE_LOADED -> loaded.countDown()
            LibMpv.EVENT_PLAYBACK_RESTART -> restarts.offer(System.nanoTime())
            LibMpv.EVENT_PROPERTY_CHANGE -> if (event.propertyName() == "time-pos" &&
                event.propertyFormat() == LibMpv.FORMAT_DOUBLE
            ) {
                val value = event.propertyDouble() ?: return
                timePos = value
                times.add(System.nanoTime() to value)
            }
        }
    }

    /** Sends one seek with its property changes and measures it. */
    private fun seek(targetS: Double, alsoSet: () -> Unit): Measure {
        restarts.clear()
        val before = lastUpdateNs()
        val start = System.nanoTime()
        core.command("seek", fmt(targetS), "absolute+exact")
        alsoSet()
        val restart = restarts.poll(10, TimeUnit.SECONDS) ?: error("no playback-restart after the seek to $targetS")
        Thread.sleep(1100)
        // The first update after the restart that is near the target shows the new picture.
        val after = times.filter { (ns, pos) -> ns >= restart && pos >= targetS - 0.05 }.map { it.first }.sorted()
        val firstNew = after.firstOrNull() ?: restart
        val stutter = after.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0L
        return Measure(
            restartMs = ms(restart - start),
            freezeMs = ms(firstNew - before),
            stutterMs = ms(stutter),
        )
    }

    private fun lastUpdateNs(): Long = times.maxOfOrNull { it.first } ?: System.nanoTime()

    private fun awaitRestart() {
        restarts.poll(10, TimeUnit.SECONDS) ?: error("no playback-restart")
        restarts.clear()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timeout while playing" }
            Thread.sleep(2)
        }
    }

    private fun dropCount(): Long =
        (core.getProperty("frame-drop-count")?.toLongOrNull() ?: 0L) +
            (core.getProperty("vo-drop-frame-count")?.toLongOrNull() ?: 0L)

    private fun summary(list: List<Measure>): String {
        fun stats(values: List<Long>): String = values.sorted().let {
            "median ${it[it.size / 2]} ms, max ${it.last()} ms"
        }
        return "restart ${stats(list.map { it.restartMs })}; freeze ${stats(list.map { it.freezeMs })}; " +
            "stutter ${stats(list.map { it.stutterMs })}"
    }

    private data class Measure(val restartMs: Long, val freezeMs: Long, val stutterMs: Long) {
        override fun toString() = "restart %4d ms, freeze %4d ms, stutter %3d ms".format(restartMs, freezeMs, stutterMs)
    }

    private companion object {
        /** The replay part starts this far before the point end, as for a typical point of 8 to 10 s. */
        const val REPLAY_BACK_S = 8.0
        const val PART_S = 2.0
        const val LEAD_IN_S = 2.0

        fun fmt(seconds: Double): String = String.format(Locale.US, "%.3f", seconds)
        fun ms(ns: Long): Long = (ns / 1_000_000.0).roundToLong()
    }
}
