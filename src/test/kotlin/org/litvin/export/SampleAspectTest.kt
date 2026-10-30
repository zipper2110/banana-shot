package org.litvin.export

import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import org.litvin.RenderJob
import org.litvin.overlayWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-59: overlays on videos with non-square pixels. */
class SampleAspectTest {
    @Test
    fun `parses the ffprobe value and reduces it`() {
        assertEquals(SampleAspect(40, 33), SampleAspect.parse("40:33"))
        assertEquals(SampleAspect(4, 3), SampleAspect.parse("8:6"))
        assertEquals(SampleAspect(4, 3), SampleAspect.parse("4/3"))
    }

    @Test
    fun `a missing or empty value gives square pixels`() {
        assertEquals(SampleAspect.SQUARE, SampleAspect.parse(null))
        assertEquals(SampleAspect.SQUARE, SampleAspect.parse("N/A"))
        assertEquals(SampleAspect.SQUARE, SampleAspect.parse("0:1"))
        assertEquals(SampleAspect.SQUARE, SampleAspect.parse("1:1"))
        assertEquals(SampleAspect.SQUARE, SampleAspect.parse("33:33"))
        assertEquals(SampleAspect.SQUARE, SampleAspect.parse("bad"))
    }

    @Test
    fun `the display width is the stored width multiplied by the sample aspect ratio`() {
        assertEquals(873, SampleAspect(40, 33).displayWidth(720))
        assertEquals(1920, SampleAspect(4, 3).displayWidth(1440))
        assertEquals(1920, SampleAspect.SQUARE.displayWidth(1920))
    }

    @Test
    fun `the flat ffprobe output gives the sample aspect ratio of the source`() {
        val info = ExportSourceProbe.parseFlat(
            """
            streams.stream.0.width=720
            streams.stream.0.height=486
            streams.stream.0.sample_aspect_ratio="40:33"
            """.trimIndent(),
        )

        assertEquals(ExportResolution("720x486", 720, 486, SampleAspect(40, 33)), info.resolution)
        assertEquals(873, info.resolution?.displayWidth)
    }

    @Test
    fun `the csv ffprobe output gives the size and the sample aspect ratio`() {
        assertEquals(ExportResolution("720x486", 720, 486, SampleAspect(40, 33)), ExportResolutionProbe.parse("720,486,40:33\n"))
        assertEquals(ExportResolution("1080p", 1920, 1080), ExportResolutionProbe.parse("1920,1080,N/A"))
        assertEquals(ExportResolution("1080p", 1920, 1080), ExportResolutionProbe.parse("1920,1080"))
        assertEquals(null, ExportResolutionProbe.parse("0,1080,1:1"))
    }

    @Test
    fun `a smaller resolution keeps the pixel shape of the source`() {
        val source = ExportResolution("1080p", 1440, 1080, SampleAspect(4, 3))
        val choice720 = ExportVideoOptions.resolutionChoices(source).first { it.title == "720p" }

        assertEquals(SampleAspect(4, 3), choice720.resolution.sampleAspect)
        assertEquals(960, choice720.resolution.width)
        assertEquals(1280, choice720.resolution.displayWidth)
    }

    @Test
    fun `the overlay of a job uses the display width`() {
        val job = RenderJob(
            sourcePath = "dv.mp4", presetId = "balanced", outWidth = 720, outHeight = 486,
            sampleAspect = SampleAspect(40, 33), encoderLabel = "H.264 (libx264)", idleTrim = false, outputPath = "out.mp4",
        )

        assertEquals(873, job.overlayWidth)
        assertEquals(1920, job.copy(outWidth = 1920, outHeight = 1080, sampleAspect = SampleAspect.SQUARE).overlayWidth)
    }

    @Test
    fun `the burn-in gives the display size to libass only for non-square pixels`() {
        assertEquals(
            "subtitles='a.ass':original_size=873x486",
            FFmpegCommandBuilder.subtitlesFilter("a.ass", 720, 486, SampleAspect(40, 33)),
        )
        assertEquals("subtitles='a.ass'", FFmpegCommandBuilder.subtitlesFilter("a.ass", 1920, 1080, SampleAspect.SQUARE))
    }

    @Test
    fun `each burn-in of an export with non-square pixels has the display size`() {
        val presets = ExportPresetsIO.load()
        val args = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "dv.mp4",
                outputPath = "out.mp4",
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = 720,
                outHeight = 486,
                sampleAspect = SampleAspect(40, 33),
                idleTrim = false,
                subtitlesAssPath = "overlay.ass",
            ),
        ).args
        val vf = args[args.indexOf("-vf") + 1]

        assertTrue(vf.endsWith("subtitles='overlay.ass':original_size=873x486"), vf)
        assertFalse(vf.contains("setsar=1"), vf)
    }

    @Test
    fun `a change point keeps the pixel shape of the source`() {
        val presets = ExportPresetsIO.load()
        val args = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "dv.mp4",
                outputPath = "out.mp4",
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = 720,
                outHeight = 486,
                sampleAspect = SampleAspect(40, 33),
                idleTrim = true,
                keeps = listOf(org.litvin.points.PointV1(id = "A", startMs = 3_000, endMs = 7_000)),
                adjustments = org.litvin.adjustments.AdjustmentsV1(
                    geometryKeys = listOf(org.litvin.adjustments.GeometryKeyV1(atMs = 5_000, zoom = 1.5f, rotationDeg = 2.0f)),
                ),
                sourceWidth = 720,
                sourceHeight = 486,
                subtitlesAssPath = "overlay.ass",
            ),
        ).args
        val graph = args[args.indexOf("-filter_complex") + 1]

        assertTrue(graph.contains("setsar=40/33"), graph)
        assertFalse(graph.contains("setsar=1"), graph)
        assertTrue(graph.contains("subtitles='overlay.ass':original_size=873x486"), graph)
    }
}
