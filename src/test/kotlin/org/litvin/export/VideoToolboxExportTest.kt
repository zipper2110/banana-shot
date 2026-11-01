package org.litvin.export

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.litvin.ApplicationLayout
import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCapabilities
import org.litvin.FFmpegCommandBuilder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-13: an export with the VideoToolbox encoder of a Mac. The test runs the real ffmpeg with the arguments of the
 * export. It skips when the ffmpeg has no working VideoToolbox encoder.
 */
@EnabledOnOs(OS.MAC)
class VideoToolboxExportTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `an export with VideoToolbox gives an H264 video with about the bitrate of the export`() {
        val layout = ApplicationLayout.current()
        assumeTrue(FFmpegCapabilities.canEncode(ExportEncoder.VIDEOTOOLBOX.id), "VideoToolbox does not work with this ffmpeg")
        val source = File(javaClass.getResource("/media/ui-smoke.mp4")!!.toURI())
        val output = dir.resolve("export.mp4")
        val presets = ExportPresetsIO.load()
        val command = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = source.absolutePath,
                outputPath = output.absolutePath,
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = 720,
                outHeight = 486,
                encoderLabel = ExportEncoder.VIDEOTOOLBOX.jobLabel,
                idleTrim = false,
                videoBitrateK = 4_000,
            ),
        )
        assertTrue(command.args.containsAll(listOf("-c:v", "h264_videotoolbox")), command.args.joinToString(" "))
        run(listOf(layout.ffmpegExecutable) + command.args, "export")

        val probe = dir.resolve("probe.txt")
        run(
            listOf(
                layout.ffprobeExecutable, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=codec_name,profile,bit_rate", "-of", "default=nw=1", "-o", probe.absolutePath,
                output.absolutePath,
            ),
            "probe",
        )
        val values = probe.readLines().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key to value }
        assertEquals("h264", values["codec_name"])
        assertEquals("High", values["profile"])
        // Without "-profile:v high -prio_speed 0", VideoToolbox used much less than the asked bitrate.
        val bitrateK = values.getValue("bit_rate").toLong() / 1000
        assertTrue(bitrateK > 1_000, "bitrate $bitrateK kbit/s for an export of 4000 kbit/s")
    }

    private fun run(command: List<String>, name: String) {
        val log = dir.resolve("$name.log")
        val process = ProcessBuilder(command).directory(dir).redirectErrorStream(true).redirectOutput(log).start()
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "ffmpeg did not stop in 2 minutes")
        assertEquals(0, process.exitValue(), log.readText().takeLast(3_000))
    }
}
