package org.litvin.media.mpv

import org.litvin.ApplicationLayout
import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import org.litvin.media.FfprobeSourceFileProbe
import org.litvin.media.SourceVideo
import org.litvin.points.PointV1
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Manual check for B-49 (not a unit test): the export of a match of several files against the preview.
 *
 * The check writes the EDL file of the video files of a folder (sorted by name), exports a range across the first file
 * end with the export command of the app, and compares the frames of the export with the frames that mpv shows for
 * the EDL file at the same times. A good export shows "offset 0 frames" at all times.
 *
 * Run: mvn -q test-compile exec:java -Dexec.classpathScope=test
 *        -Dapp.mainClass=org.litvin.media.mpv.JoinedExportSpikeMainKt
 *        -Dexec.args="<folder of the files> <output folder> <frame ms> [<range start ms> <range end ms>]"
 */
fun main(args: Array<String>) {
    val folder = File(args[0])
    val outDir = File(args[1]).apply { mkdirs() }
    val frameMs = args[2].toDouble()
    val files = folder.listFiles { file -> file.extension.equals("mp4", ignoreCase = true) && file.name != "whole.mp4" }!!
        .sortedBy { it.name }
    val probe = FfprobeSourceFileProbe()
    val parts = files.map { probe.probe(it.absolutePath) ?: error("Cannot probe $it") }
    parts.forEach { println("${File(it.file.path).name}: start ${it.file.startMs} ms, length ${it.file.durationMs} ms, ${it.format}") }
    val edl = File(outDir, SourceVideo.EDL_FILE_NAME)
    SourceVideo.write(edl, parts.map { it.file })
    val firstEnd = parts.first().file.durationMs
    val startMs = args.getOrNull(3)?.toLong() ?: (firstEnd - 1_500)
    val endMs = args.getOrNull(4)?.toLong() ?: (firstEnd + 2_000)

    val preset = ExportPresetsIO.load().let { it[ExportPresetsIO.defaultBalancedIndex(it)] }
    val output = File(outDir, "range.mp4")
    val build = FFmpegCommandBuilder.build(
        FFmpegCommandBuilder.BuildParams(
            sourcePath = edl.absolutePath,
            outputPath = output.absolutePath,
            preset = preset,
            outWidth = 1280,
            outHeight = 720,
            encoderLabel = "H.264 (libx264)",
            idleTrim = true,
            keeps = listOf(PointV1(id = "range", startMs = startMs.toInt(), endMs = endMs.toInt())),
        )
    )
    println(build.preview)
    val process = ProcessBuilder(listOf(ApplicationLayout.current().ffmpegExecutable) + build.args)
        .redirectErrorStream(true).start()
    val log = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) {
        println(log.lines().takeLast(20).joinToString("\n"))
        error("ffmpeg failed")
    }
    println("Export: ${output.absolutePath}")

    MpvFrameReader().use { reader ->
        val lengthMs = endMs - startMs
        // The times are in the middle of a frame at 30 fps, so that the "first frame at or after" read does not pick a neighbor.
        for (t in listOf(100L, 1_450, 1_483, 1_517, 1_550, 1_617, 2_017, lengthMs - 283)) {
            val exported = reader.read(output.absolutePath, t, "null") ?: continue
            val matches = (-3..3).map { step ->
                val preview = reader.read(edl.absolutePath, startMs + t + Math.round(step * frameMs), "null")
                step to (preview?.let { difference(exported, it) } ?: Double.MAX_VALUE)
            }
            val best = matches.minBy { it.second }
            println(String.format(Locale.US, "export %6d ms = source %6d ms: offset %+d frames (difference %.2f)", t, startMs + t, best.first, best.second))
        }
    }
    System.exit(0)
}

/** The mean difference of the pixel channels of two frames of the same size. */
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
