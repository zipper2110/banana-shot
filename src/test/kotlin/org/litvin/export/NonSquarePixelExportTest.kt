package org.litvin.export

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.litvin.ApplicationLayout
import org.litvin.AssOverlayWriter
import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.stats.StatsCard
import org.litvin.stats.StatsCardVideo
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-59: an overlay on a video with non-square pixels keeps its shape. The test video is 720x486 with a sample
 * aspect ratio of 40:33. The test burns in a green square and measures it in the stored frame. A player makes
 * the stored frame 40/33 wider, so a square in the stored frame is 33/40 as wide as it is high.
 * The test runs the real ffmpeg. It skips when ffmpeg is not on this computer.
 */
class NonSquarePixelExportTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `a square on the overlay is square on the screen and the export keeps the sample aspect ratio`() {
        val layout = ApplicationLayout.current()
        assumeTrue(runs(listOf(layout.ffmpegExecutable, "-hide_banner", "-version")), "ffmpeg is not available")
        assumeTrue(runs(listOf(layout.ffprobeExecutable, "-hide_banner", "-version")), "ffprobe is not available")
        val source = File(javaClass.getResource("/media/ui-smoke.mp4")!!.toURI())
        val resolution = requireNotNull(ExportSourceProbe.probe(source.absolutePath, layout.ffprobeExecutable).resolution)
        assertEquals(SampleAspect(40, 33), resolution.sampleAspect)

        // A square of 600 card units is 270 display pixels high on a 486 pixel frame.
        val side = 600.0
        val page = ScoreboardScene(
            width = StatsCard.HEIGHT * resolution.displayWidth / resolution.height,
            height = StatsCard.HEIGHT,
            items = listOf(
                SceneItem.Box(200.0, 200.0, side, side, rgb = 0x00FF00),
                SceneItem.Label(1000.0, 500.0, "MMMM", "Arial", 200.0, rgb = 0x00FF00),
            ),
        )
        val ass = dir.resolve("card.ass")
        AssOverlayWriter.writeStatsCard(ass, StatsCardVideo(listOf(page)), resolution.displayWidth, resolution.height)
        val output = dir.resolve("export.mp4")
        val presets = ExportPresetsIO.load()
        val command = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = source.absolutePath,
                outputPath = output.absolutePath,
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = resolution.width,
                outHeight = resolution.height,
                sampleAspect = resolution.sampleAspect,
                idleTrim = false,
                subtitlesAssPath = ass.absolutePath,
            ),
        )
        run(listOf(layout.ffmpegExecutable) + command.args, "export")
        val frame = dir.resolve("frame.png")
        run(listOf(layout.ffmpegExecutable, "-y", "-v", "error", "-i", output.absolutePath, "-frames:v", "1", frame.absolutePath), "frame")

        val exported = requireNotNull(ExportResolutionProbe.probe(output.absolutePath, layout.ffprobeExecutable))
        assertEquals(720, exported.width)
        assertEquals(486, exported.height)
        assertEquals(SampleAspect(40, 33), exported.sampleAspect)

        // The same overlay on a black video of the display size with square pixels gives the text width on the screen.
        val reference = dir.resolve("reference.png")
        run(
            listOf(
                layout.ffmpegExecutable, "-y", "-v", "error", "-f", "lavfi",
                "-i", "color=c=black:s=${resolution.displayWidth}x${resolution.height}:d=1",
                "-vf", FFmpegCommandBuilder.subtitlesFilter(ass.absolutePath, resolution.displayWidth, resolution.height, SampleAspect.SQUARE),
                "-frames:v", "1", reference.absolutePath,
            ),
            "reference",
        )
        val textWidth = greenBox(frame, fromX = TEXT_FROM_X).first * 40.0 / 33.0
        val referenceTextWidth = greenBox(reference, fromX = resolution.displayWidth / 2).first.toDouble()
        assertTrue(abs(textWidth - referenceTextWidth) <= 6, "text width on the screen $textWidth, expected $referenceTextWidth")

        val (width, height) = greenBox(frame, toX = TEXT_FROM_X)
        val expectedHeight = side * resolution.height / StatsCard.HEIGHT
        assertTrue(abs(height - expectedHeight) <= 4, "height $height, expected $expectedHeight")
        // On the screen, the width is the stored width multiplied by 40/33.
        val screenWidth = width * 40.0 / 33.0
        assertTrue(abs(screenWidth - height) <= 5, "screen width $screenWidth, height $height: the square is not square")
    }

    @Test
    fun `an export with change points keeps the sample aspect ratio`() {
        val layout = ApplicationLayout.current()
        assumeTrue(runs(listOf(layout.ffmpegExecutable, "-hide_banner", "-version")), "ffmpeg is not available")
        assumeTrue(runs(listOf(layout.ffprobeExecutable, "-hide_banner", "-version")), "ffprobe is not available")
        val source = File(javaClass.getResource("/media/ui-smoke.mp4")!!.toURI())
        val output = dir.resolve("change-points.mp4")
        val presets = ExportPresetsIO.load()
        val command = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = source.absolutePath,
                outputPath = output.absolutePath,
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = 720,
                outHeight = 486,
                sampleAspect = SampleAspect(40, 33),
                idleTrim = true,
                keeps = listOf(
                    org.litvin.points.PointV1(id = "A", startMs = 0, endMs = 1_000),
                    org.litvin.points.PointV1(id = "B", startMs = 1_500, endMs = 3_000),
                ),
                adjustments = org.litvin.adjustments.AdjustmentsV1(
                    geometryKeys = listOf(org.litvin.adjustments.GeometryKeyV1(atMs = 2_000, zoom = 1.5f)),
                ),
                sourceWidth = 720,
                sourceHeight = 486,
            ),
        )
        run(listOf(layout.ffmpegExecutable) + command.args, "change-points")

        val exported = requireNotNull(ExportResolutionProbe.probe(output.absolutePath, layout.ffprobeExecutable))
        assertEquals(SampleAspect(40, 33), exported.sampleAspect)
    }

    /** The width and the height of the green pixels in [file], in the columns from [fromX] to [toX]. */
    private fun greenBox(file: File, fromX: Int = 0, toX: Int = Int.MAX_VALUE): Pair<Int, Int> {
        val image = ImageIO.read(file)
        var minX = Int.MAX_VALUE
        var maxX = -1
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in 0 until image.height) {
            for (x in fromX until minOf(toX, image.width)) {
                val rgb = image.getRGB(x, y)
                val r = rgb shr 16 and 0xFF
                val g = rgb shr 8 and 0xFF
                val b = rgb and 0xFF
                if (g > 180 && r < 100 && b < 100) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        assertTrue(maxX >= 0, "the frame has no green square")
        return (maxX - minX + 1) to (maxY - minY + 1)
    }

    private companion object {
        /** The square ends and the text starts between these columns of the stored frame. */
        const val TEXT_FROM_X = 360
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
