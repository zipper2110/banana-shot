package org.litvin

import org.litvin.export.ExportFrameRate
import org.litvin.export.PassFade
import org.litvin.points.PointV1
import org.litvin.points.ReplayZoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the replay pass and the dips of the chunks next to a replay (B-50). */
class FFmpegCommandBuilderReplayTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private fun render(
        zoom: ReplayZoom? = null,
        zoomIn: Boolean = true,
        zoomOut: Boolean = true,
        muted: Boolean = false,
    ) = FFmpegCommandBuilder.ReplayRender(
        startMs = 12_000,
        endMs = 14_000,
        speed = 0.5,
        zoom = zoom,
        zoomIn = zoomIn,
        zoomOut = zoomOut,
        muted = muted,
        sourceFrameRate = ExportFrameRate("30000/1001", 29.97),
        sampleRate = 44_100,
    )

    private fun build(
        replay: FFmpegCommandBuilder.ReplayRender? = null,
        keeps: List<PointV1> = emptyList(),
        fadeIn: PassFade? = null,
        fadeOut: PassFade? = null,
        subtitles: String? = null,
        sourceHasAudio: Boolean = true,
    ) = FFmpegCommandBuilder.build(
        FFmpegCommandBuilder.BuildParams(
            sourcePath = "input.mp4",
            outputPath = "out.mp4.part.chunk3.ts",
            preset = preset,
            outWidth = 1920,
            outHeight = 1080,
            encoderLabel = "H.264 (libx264)",
            idleTrim = true,
            keeps = keeps,
            subtitlesAssPath = subtitles,
            chunkOutput = true,
            fadeIn = fadeIn,
            fadeOut = fadeOut,
            replay = replay,
            sourceHasAudio = sourceHasAudio,
        )
    ).args

    private fun List<String>.valueOf(flag: String) = this[indexOf(flag) + 1]

    @Test
    fun theReplayReadsOnlyItsPartOfTheSource() {
        val args = build(render())

        assertEquals("12.000", args.valueOf("-ss"))
        assertEquals("2.000", args.valueOf("-t"))
        assertEquals(1, args.count { it == "-i" })
    }

    @Test
    fun theReplaySlowsTheVideoKeepsTheFrameRateAndLowersThePitch() {
        val filter = build(render(), fadeIn = PassFade(false, 250), fadeOut = PassFade(true, 250), subtitles = "replay.ass")
            .valueOf("-filter_complex")

        assertTrue("setpts=PTS/0.500000,fps=30000/1001" in filter, filter)
        assertTrue(
            "subtitles='replay.ass',fade=t=in:st=0:d=0.250:color=black,fade=t=out:st=3.750:d=0.250:color=white[vout]" in filter,
            filter,
        )
        assertTrue(
            "asetrate=22050,aresample=44100,apad,atrim=duration=4.000,afade=t=in:st=0:d=0.250,afade=t=out:st=3.750:d=0.250[aout]" in filter,
            filter,
        )
        assertFalse("perspective" in filter, filter)
    }

    @Test
    fun aMutedReplayAndASourceWithoutAudioHaveSilentAudio() {
        val muted = build(render(muted = true)).valueOf("-filter_complex")
        assertTrue("[0:a]asetpts=PTS-STARTPTS,volume=0,apad,atrim=duration=4.000[aout]" in muted, muted)

        val silent = build(render(), sourceHasAudio = false).valueOf("-filter_complex")
        assertTrue("anullsrc=r=48000:cl=stereo,atrim=duration=4.000[aout]" in silent, silent)
    }

    @Test
    fun theZoomIsAPerspectiveBeforeTheScaleAndTheSpeed() {
        val filter = build(render(zoom = ReplayZoom(0.25, 0.25, 0.5))).valueOf("-filter_complex")

        val perspective = filter.indexOf("perspective=")
        assertTrue(perspective > 0, filter)
        assertTrue(perspective < filter.indexOf("scale=1920"), filter)
        assertTrue(perspective < filter.indexOf("setpts=PTS/"), filter)
        assertTrue("sense=source:eval=frame" in filter, filter)
    }

    @Test
    fun theZoomGoesInAtTheFirstPlayAndOutAtTheLastPlay() {
        val zoom = ReplayZoom(0.25, 0.25, 0.5)
        // 29.97 fps x 0.4 s x 0.5 speed: the zoom takes about 6 source frames.
        val first = FFmpegCommandBuilder.replayZoomFilter(render(zoom, zoomIn = true, zoomOut = false))!!
        assertTrue("clip(in/5.994000,0,1)" in first, first)
        assertFalse("-in)" in first, first)

        // 2 s of 29.97 fps: the last frame is 58.94.
        val last = FFmpegCommandBuilder.replayZoomFilter(render(zoom, zoomIn = false, zoomOut = true))!!
        assertTrue("clip((58.940000-in)/5.994000,0,1)" in last, last)

        // A middle repeat keeps the zoom frame, so the filter computes it once.
        val middle = FFmpegCommandBuilder.replayZoomFilter(render(zoom, zoomIn = false, zoomOut = false))!!
        assertTrue("x0='W*(0.250000*1)'" in middle, middle)
        assertTrue("x1='W*(0.250000*1+(1-0.500000*1))'" in middle, middle)
        assertTrue(middle.endsWith("eval=init"), middle)

        assertNull(FFmpegCommandBuilder.replayZoomFilter(render(zoom = null)))
    }

    @Test
    fun aChunkNextToAReplayDipsAtItsEnds() {
        val keeps = listOf(PointV1(id = "a", startMs = 0, endMs = 5_000), PointV1(id = "b", startMs = 10_000, endMs = 13_000))
        val filter = build(keeps = keeps, fadeIn = PassFade(false, 250), fadeOut = PassFade(true, 250), subtitles = "board.ass")
            .valueOf("-filter_complex")

        assertTrue("subtitles='board.ass'[vfd]" in filter, filter)
        assertTrue("[vfd]fade=t=in:st=0:d=0.250:color=black,fade=t=out:st=7.750:d=0.250:color=white[vout]" in filter, filter)
        assertTrue("concat=n=2:v=0:a=1[acat];[acat]afade=t=in:st=0:d=0.250,afade=t=out:st=7.750:d=0.250[aout]" in filter, filter)
    }

    @Test
    fun aChunkWithoutFadesHasNoFadeFilters() {
        val keeps = listOf(PointV1(id = "a", startMs = 0, endMs = 5_000))
        val filter = build(keeps = keeps).valueOf("-filter_complex")

        assertFalse("fade" in filter, filter)
        assertTrue("concat=n=1:v=0:a=1[aout]" in filter, filter)
    }
}
