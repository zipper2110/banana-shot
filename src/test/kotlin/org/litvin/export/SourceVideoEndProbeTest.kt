package org.litvin.export

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.litvin.ApplicationLayout
import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceVideoEndProbeTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `the end of the video stream is its start time and its duration`() {
        assertEquals(30_033L, SourceVideoEndProbe.parse("start_time=0.000000\nduration=30.033333\nstart_time=0.000000\n"))
        assertEquals(10_500L, SourceVideoEndProbe.parse("start_time=1.500000\nduration=10.000000\nstart_time=1.000000\n"))
        assertEquals(4_000L, SourceVideoEndProbe.parse("start_time=N/A\nduration=4.000000\nstart_time=N/A\n"))
    }

    @Test
    fun `a stream with no duration gives no end`() {
        assertNull(SourceVideoEndProbe.parse("start_time=0.000000\nduration=N/A\nstart_time=0.000000\n"))
        assertNull(SourceVideoEndProbe.parse(""))
    }

    @Test
    fun `the still frame is never after the end of the video`() {
        assertEquals(30_033L, SourceVideoEndProbe.stillFrameAtMs(null, 30_033L))
        assertEquals(30_033L, SourceVideoEndProbe.stillFrameAtMs(30_500L, 30_033L))
        assertEquals(12_000L, SourceVideoEndProbe.stillFrameAtMs(12_000L, 30_033L))
        assertEquals(12_000L, SourceVideoEndProbe.stillFrameAtMs(12_000L, null))
        assertNull(SourceVideoEndProbe.stillFrameAtMs(null, null))
    }

    /**
     * The statistics card of a full video export froze a window at the end of the file. When the audio is longer than
     * the video, that window has no video frame, and ffmpeg failed with "Could not open encoder before EOF".
     * The test runs the real ffmpeg. It skips when ffmpeg is not on this computer.
     */
    @Test
    fun `the card freezes the last video frame when the audio is longer than the video`() {
        val layout = ApplicationLayout.current()
        assumeTrue(runs(listOf(layout.ffmpegExecutable, "-hide_banner", "-version")), "ffmpeg is not available")
        assumeTrue(runs(listOf(layout.ffprobeExecutable, "-hide_banner", "-version")), "ffprobe is not available")
        // 2 seconds of video and 3 seconds of audio.
        val source = dir.resolve("long-audio.mp4")
        run(
            listOf(
                layout.ffmpegExecutable, "-y", "-v", "error",
                "-f", "lavfi", "-i", "testsrc2=s=320x240:r=30:d=2",
                "-f", "lavfi", "-i", "sine=f=440:r=48000:d=3",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", source.absolutePath,
            ),
            "source",
        )
        val videoEndMs = assertNotNull(SourceVideoEndProbe.probe(source.absolutePath, layout.ffprobeExecutable))
        assertTrue(abs(videoEndMs - 2_000L) <= 40, "video end $videoEndMs ms")

        val output = dir.resolve("card.ts")
        val presets = ExportPresetsIO.load()
        val command = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = source.absolutePath,
                outputPath = output.absolutePath,
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = 320,
                outHeight = 240,
                idleTrim = false,
                chunkOutput = true,
                freezeFrame = FFmpegCommandBuilder.FreezeFrame(SourceVideoEndProbe.stillFrameAtMs(null, videoEndMs), 1_000L),
            ),
        )
        run(listOf(layout.ffmpegExecutable) + command.args, "card")

        val frames = dir.resolve("frames.txt")
        run(
            listOf(
                layout.ffprobeExecutable, "-v", "error", "-select_streams", "v:0", "-count_frames",
                "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", "-o", frames.absolutePath, output.absolutePath,
            ),
            "frames",
        )
        val count = frames.readLines().first { it.isNotBlank() }.trim().trimEnd(',').toInt()
        assertTrue(count >= 25, "the card has $count video frames, expected about 30")
    }

    private fun run(command: List<String>, name: String) {
        val log = dir.resolve("$name.log")
        val process = ProcessBuilder(command).directory(dir).redirectErrorStream(true).redirectOutput(log).start()
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "ffmpeg did not stop in 2 minutes")
        assertEquals(0, process.exitValue(), log.readText().takeLast(3_000))
    }

    private fun runs(command: List<String>): Boolean = try {
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}
