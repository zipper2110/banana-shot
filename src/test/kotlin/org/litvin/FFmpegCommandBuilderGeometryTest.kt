package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.GeometryPlan
import org.litvin.points.PointV1
import org.litvin.media.mpv.MpvShaderParams
import org.litvin.media.mpv.MpvVideoInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FFmpegCommandBuilderGeometryTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private fun build(adjustments: AdjustmentsV1?, idleTrim: Boolean = false, subtitles: String? = null) =
        FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1920,
                outHeight = 1080,
                idleTrim = idleTrim,
                keeps = if (idleTrim) listOf(PointV1(id = "A", startMs = 0, endMs = 1000)) else emptyList(),
                subtitlesAssPath = subtitles,
                adjustments = adjustments,
                sourceWidth = 3840,
                sourceHeight = 2160,
            )
        ).args

    private fun vf(args: List<String>): String = args[args.indexOf("-vf") + 1]

    @Test
    fun `identity geometry adds no rotate or crop filter`() {
        val filter = vf(build(AdjustmentsV1()))

        assertFalse(filter.contains("rotate="), filter)
        assertFalse(filter.contains("crop="), filter)
    }

    @Test
    fun `rotation adds rotate and the inscribed crop before the scale filter`() {
        val filter = vf(build(AdjustmentsV1(rotationDeg = 5.0f)))

        assertTrue(filter.startsWith("rotate=0.087266:ow=iw:oh=ih:c=black,crop=3334:1876:252:142,"), filter)
        assertTrue(filter.indexOf("crop=") < filter.indexOf("scale="), filter)
    }

    @Test
    fun `zoom and pan add only a crop in even source pixels`() {
        val filter = vf(build(AdjustmentsV1(zoom = 2.0f, panX = 1.0f, panY = 1.0f)))

        assertEquals("crop=1920:1080:1920:0,scale=1920:1080", filter)
    }

    @Test
    fun `unknown source size falls back to a crop with frame fractions`() {
        val args = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1920,
                outHeight = 1080,
                idleTrim = false,
                adjustments = AdjustmentsV1(zoom = 2.0f, panX = 1.0f, panY = 1.0f),
            )
        ).args

        assertEquals("crop=w=iw*0.500000:h=ih*0.500000:x=iw*0.500000:y=ih*0.000000,scale=1920:1080", vf(args))
    }

    @Test
    fun `a crop scales to the exact output size`() {
        // 15 degrees on 720x486 crops 532x360. With "scale=720:-2" the height was 488, not 486.
        val args = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 720,
                outHeight = 486,
                idleTrim = false,
                adjustments = AdjustmentsV1(rotationDeg = 15.0f),
                sourceWidth = 720,
                sourceHeight = 486,
            )
        ).args

        assertTrue(vf(args).contains(",crop=532:360:94:62,scale=720:486"), vf(args))
    }

    @Test
    fun `without geometry the scale keeps the source aspect`() {
        assertTrue(vf(build(AdjustmentsV1())).startsWith("scale=1920:-2"), vf(build(AdjustmentsV1())))
    }

    @Test
    fun `geometry comes before color and subtitles`() {
        val filter = vf(build(AdjustmentsV1(rotationDeg = -3.0f, contrast = 1.3f), subtitles = "overlay.ass"))

        val rotate = filter.indexOf("rotate=")
        val scale = filter.indexOf("scale=")
        val eq = filter.indexOf("eq=")
        val subtitles = filter.indexOf("subtitles=")
        assertTrue(rotate in 0 until scale && scale < eq && eq < subtitles, filter)
    }

    @Test
    fun `idle trim path applies the geometry after concat`() {
        val args = build(AdjustmentsV1(zoom = 1.5f), idleTrim = true)
        val graph = args[args.indexOf("-filter_complex") + 1]

        assertTrue(graph.contains("[vcat]crop=2560:1440:640:360,scale=1920:1080[vsc]"), graph)
    }

    @Test
    fun `export and preview use the same crop rectangle`() {
        val adjustments = AdjustmentsV1(rotationDeg = 7.5f, zoom = 1.4f, panX = -0.3f, panY = 0.6f)
        val plan = GeometryPlan.of(adjustments, 3840, 2160)
        val shader = MpvShaderParams.build(adjustments, MpvVideoInfo(3840, 2160, "bt.709", "limited"))
            .split(",").associate { it.substringBefore("=") to it.substringAfter("=").toDouble() }

        assertEquals(plan.crop.x, shader.getValue("tr_crop_x"), 1e-6)
        assertEquals(plan.crop.y, shader.getValue("tr_crop_y"), 1e-6)
        assertEquals(plan.crop.width, shader.getValue("tr_crop_w"), 1e-6)
        assertEquals(plan.crop.height, shader.getValue("tr_crop_h"), 1e-6)
        assertEquals(plan.rotationDeg, shader.getValue("tr_rotation_deg"), 1e-6)
        val (w, h, x, y) = plan.cropPixels(3840, 2160).toList()
        assertTrue(vf(build(adjustments)).contains("crop=$w:$h:$x:$y,"), vf(build(adjustments)))
        assertTrue(listOf(w, h, x, y).all { it % 2 == 0 }, "even pixels: $w $h $x $y")
    }

    private val changePoints = AdjustmentsV1(
        geometryKeys = listOf(org.litvin.adjustments.GeometryKeyV1(atMs = 5_000, zoom = 1.5f, rotationDeg = 2.0f)),
    )

    private fun buildKeeps(adjustments: AdjustmentsV1, keeps: List<PointV1>) = FFmpegCommandBuilder.build(
        FFmpegCommandBuilder.BuildParams(
            sourcePath = "input.mp4",
            outputPath = "out.mp4",
            preset = preset,
            outWidth = 1920,
            outHeight = 1080,
            idleTrim = true,
            keeps = keeps,
            adjustments = adjustments,
            sourceWidth = 3840,
            sourceHeight = 2160,
        )
    ).args

    @Test
    fun `with change points each segment gets the geometry of its time before the concat`() {
        val args = buildKeeps(
            changePoints,
            listOf(PointV1(id = "A", startMs = 0, endMs = 3_000), PointV1(id = "B", startMs = 6_000, endMs = 8_000)),
        )
        val graph = args[args.indexOf("-filter_complex") + 1]

        assertTrue(graph.contains("[0:v]setpts=PTS-STARTPTS,scale=1920:1080,setsar=1[v0]"), graph)
        assertTrue(graph.contains("[1:v]setpts=PTS-STARTPTS,rotate=0.034907:ow=iw:oh=ih:c=black,crop="), graph)
        assertTrue(graph.contains("[vcat]null[vsc]"), graph)
        assertFalse(graph.contains("eval=frame"), graph)
    }

    @Test
    fun `a segment with a blend changes the angle and the crop at each frame`() {
        val args = buildKeeps(changePoints, listOf(PointV1(id = "A", startMs = 3_000, endMs = 7_000)))
        val graph = args[args.indexOf("-filter_complex") + 1]
        val segment = graph.substringBefore("[v0]")

        assertTrue(segment.contains("rotate=a='("), segment)
        assertTrue(segment.contains("(t+3.000)"), segment)
        assertTrue(segment.contains("lt((t+3.000),4.000)"), segment)
        assertTrue(segment.contains(":eval=frame,crop=w=1920:h=1080:"), segment)
        assertTrue(segment.endsWith("setsar=1"), segment)
    }

    @Test
    fun `a still frame uses the geometry of its time`() {
        val before = FFmpegCommandBuilder.stillFrameArgs("in.mp4", 1_000, "out.png", 1280, changePoints, 3840, 2160)
        val after = FFmpegCommandBuilder.stillFrameArgs("in.mp4", 9_000, "out.png", 1280, changePoints, 3840, 2160)

        assertFalse(before[before.indexOf("-vf") + 1].contains("rotate="))
        assertTrue(after[after.indexOf("-vf") + 1].startsWith("rotate=0.034907"), after.toString())
    }

    private val colorPoint = AdjustmentsV1(colorKeys = listOf(org.litvin.adjustments.ColorKeyV1(atMs = 5_000, brightness = 1.3f)))

    @Test
    fun `a segment with a color blend fades between the old and the new colors`() {
        val args = buildKeeps(colorPoint, listOf(PointV1(id = "A", startMs = 3_000, endMs = 7_000)))
        val graph = args[args.indexOf("-filter_complex") + 1]

        // The keep input gives the audio. Three more inputs without audio give the hold, the fade and the hold.
        assertEquals(listOf("-an", "-ss", "3.000", "-t", "1.000", "-i"), args.subList(args.indexOf("-an"), args.indexOf("-an") + 6))
        assertEquals(4, args.count { it == "-i" })
        assertTrue(graph.contains("[2:v]setpts=PTS-STARTPTS,scale=1920:1080,setsar=1,split[v0p1a][v0p1b]"), graph)
        assertTrue(graph.contains("[v0p1from][v0p1to]xfade=transition=fade:duration=1.000:offset=0[v0p1]"), graph)
        assertTrue(graph.contains("[v0p0][v0p1][v0p2]concat=n=3:v=1:a=0[v0]"), graph)
        assertTrue(graph.contains("[0:a]asetpts=PTS-STARTPTS[a0]"), graph)
    }

    @Test
    fun `the whole video with color change points maps the audio of the first input`() {
        val args = build(colorPoint)

        assertFalse(args.contains("-vf"))
        assertEquals("0:a:0", args[args.lastIndexOf("-map") + 1])
        val lastInput = args.subList(args.lastIndexOf("-ss"), args.lastIndexOf("-i"))
        assertEquals(listOf("-ss", "5.000"), lastInput, "The last piece has no length")
    }
}
