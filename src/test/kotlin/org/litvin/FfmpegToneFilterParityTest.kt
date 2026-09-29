package org.litvin

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * Runs the export `lutyuv` filter in the real ffmpeg on a frame with every 8-bit luma and chroma code, and
 * compares each output code with [FfmpegColorAdjustmentStrategy.toneCurve] and [FfmpegColorAdjustmentStrategy.chromaCurve].
 * The preview shader copies the same curves. The test is skipped when ffmpeg is not available.
 */
class FfmpegToneFilterParityTest {
    private val ffmpeg = ApplicationLayout.current().ffmpegExecutable

    /** Slider values: brightness, shadows, highlights, temperature. */
    private val cases = listOf(
        listOf(0, -100, 0, 0), listOf(0, 100, 0, 0), listOf(0, 0, -100, 0), listOf(0, 0, 100, 0),
        listOf(0, 37, -63, 0), listOf(0, -45, 80, 0), listOf(0, 100, 100, 0), listOf(0, -100, -100, 0), listOf(0, 5, -5, 0),
        listOf(100, 0, 0, 0), listOf(-100, 0, 0, 0), listOf(-37, 0, 0, 0), listOf(1, 0, 0, 0), listOf(60, -50, 40, 0),
        listOf(0, 0, 0, 100), listOf(0, 0, 0, -100), listOf(0, 0, 0, 50), listOf(0, 0, 0, -1), listOf(-20, 30, -40, 73),
    )

    @Test
    fun `the export lookup filter gives the same codes as the reference curves`() {
        assumeTrue(ffmpegRuns(), "ffmpeg is not available")
        for (range in ToneRange.entries) {
            for ((brightness, shadows, highlights, temperature) in cases) {
                val values = FfmpegColorAdjustmentStrategy.map(
                    AdjustmentsUiConverter.slidersToModel(brightness, 0, 0, shadows, highlights, temperature), range,
                )
                val output = runFilter(FFmpegCommandBuilder.lutFilter(values))
                val at = "brightness=$brightness shadows=$shadows highlights=$highlights temperature=$temperature $range"
                val expected = Planes(
                    y = IntArray(256) { FfmpegColorAdjustmentStrategy.toneCurve(values, it) },
                    u = IntArray(256) { FfmpegColorAdjustmentStrategy.chromaCurve(values.cbShift, it) },
                    v = IntArray(256) { FfmpegColorAdjustmentStrategy.chromaCurve(values.crShift, it) },
                )
                assertEquals(emptyList(), mismatches(output.y, expected.y), "luma at $at")
                assertEquals(emptyList(), mismatches(output.u, expected.u), "Cb at $at")
                assertEquals(emptyList(), mismatches(output.v, expected.v), "Cr at $at")
            }
        }
    }

    private class Planes(val y: IntArray, val u: IntArray, val v: IntArray)

    private fun mismatches(actual: IntArray, expected: IntArray): List<String> =
        (0..255).filter { actual[it] != expected[it] }.map { "$it->${actual[it]} (expected ${expected[it]})" }

    /** Returns the output code for each input code 0..255, on each plane. */
    private fun runFilter(filter: String): Planes {
        val dir = Files.createTempDirectory("lut-parity").toFile()
        try {
            // A 512x2 yuv420p frame. Luma column x has code x % 256, and chroma column x has code x.
            val width = 512
            val height = 2
            val lumaSize = width * height
            val chromaSize = lumaSize / 4
            val frame = ByteArray(lumaSize + 2 * chromaSize)
            for (y in 0 until height) for (x in 0 until width) frame[y * width + x] = (x % 256).toByte()
            for (x in 0 until width / 2) {
                frame[lumaSize + x] = x.toByte()
                frame[lumaSize + chromaSize + x] = x.toByte()
            }
            val input = File(dir, "in.yuv").apply { writeBytes(frame) }
            val output = File(dir, "out.yuv")
            val process = ProcessBuilder(
                ffmpeg, "-v", "error", "-y",
                "-f", "rawvideo", "-pix_fmt", "yuv420p", "-s", "${width}x$height", "-i", input.absolutePath,
                "-vf", filter, "-f", "rawvideo", "-pix_fmt", "yuv420p", output.absolutePath,
            ).redirectErrorStream(true).start()
            val log = process.inputStream.bufferedReader().readText()
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed for $filter: $log" }
            val bytes = output.readBytes()
            fun code(index: Int) = bytes[index].toInt() and 0xFF
            return Planes(
                y = IntArray(256) { code(it) },
                u = IntArray(256) { code(lumaSize + it) },
                v = IntArray(256) { code(lumaSize + chromaSize + it) },
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun ffmpegRuns(): Boolean = try {
        val process = ProcessBuilder(ffmpeg, "-version").redirectErrorStream(true).start()
        process.inputStream.readAllBytes()
        process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}
