package org.litvin.export

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.litvin.ApplicationLayout
import org.litvin.AssOverlayWriter
import org.litvin.CommentOverlaySpan
import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import org.litvin.OverlaySpan
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-11 E3-T23: a user text with emoji or with letters that the scoreboard font does not have does not make the export
 * fail. The test runs the real ffmpeg on the test video. It skips when ffmpeg is not on this computer.
 */
class UserTextExportTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `an export with emoji in the player names and in a comment does not fail`() {
        val ffmpeg = ApplicationLayout.current().ffmpegExecutable
        assumeTrue(runs(listOf(ffmpeg, "-hide_banner", "-version")), "ffmpeg is not available: $ffmpeg")
        val source = File(javaClass.getResource("/media/ui-smoke.mp4")!!.toURI())
        val ass = dir.resolve("overlay.ass")
        AssOverlayWriter.write(
            file = ass,
            scoreboardSpans = listOf(OverlaySpan(0L, 2_000L, "", p1Name = "Ana 🎾", p2Name = "李明 🏆")),
            commentSpans = listOf(CommentOverlaySpan(1, 0L, 2_000L, "Ace! 🔥👏 {\\b1}", "#FFFFFF")),
            outWidth = 720,
            outHeight = 486,
        )
        val output = dir.resolve("export.mp4")
        val presets = ExportPresetsIO.load()
        val command = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = source.absolutePath,
                outputPath = output.absolutePath,
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = 720,
                outHeight = 486,
                idleTrim = false,
                subtitlesAssPath = ass.absolutePath,
            ),
        )

        val log = dir.resolve("ffmpeg.log")
        val process = ProcessBuilder(listOf(ffmpeg) + command.args)
            .directory(dir)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "ffmpeg did not stop in 2 minutes")
        assertEquals(0, process.exitValue(), log.readText().takeLast(3_000))
        assertTrue(output.isFile && output.length() > 0, "the export wrote no video")
    }

    private fun runs(command: List<String>): Boolean = try {
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}
