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
        muted: Boolean = false,
    ) = FFmpegCommandBuilder.ReplayRender(
        startMs = 12_000,
        endMs = 14_000,
        speed = 0.5,
        zoom = zoom,
        zoomIn = zoomIn,
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
    fun theStillFrameRepeatsTheFrameAfterTheCutAndTheSoundIsSilent() {
        val replay = render().copy(freezeAtMs = 2_000, freezeMs = 800)
        assertEquals(4_800L, replay.durationMs)
        val filter = build(replay, fadeIn = PassFade(false, 250), fadeOut = PassFade(false, 250), subtitles = "replay.ass")
            .valueOf("-filter_complex")

        assertTrue("fps=30000/1001,split[rfa][rfb];[rfa]trim=end=2.000,setpts=PTS-STARTPTS[rfa1]" in filter, filter)
        assertTrue("[rfb]trim=start=2.000,setpts=PTS-STARTPTS,tpad=start_mode=clone:start_duration=0.800[rfb1]" in filter, filter)
        // The subtitles and the fades come after the join, and the fade out is at the end of the longer play.
        assertTrue(
            "[rfa1][rfb1]concat=n=2:v=1:a=0,subtitles='replay.ass',fade=t=in:st=0:d=0.250:color=black,fade=t=out:st=4.550:d=0.250:color=black[vout]" in filter,
            filter,
        )
        assertTrue("aresample=44100,asplit[rsa][rsb];[rsa]atrim=end=2.000,asetpts=PTS-STARTPTS[rsa1]" in filter, filter)
        assertTrue("[rsb]atrim=start=2.000,asetpts=PTS-STARTPTS,adelay=delays=800:all=1[rsb1]" in filter, filter)
        assertTrue("[rsa1][rsb1]concat=n=2:v=0:a=1,apad,atrim=duration=4.800," in filter, filter)
    }

    @Test
    fun aStillFrameAtTheStartOrTheEndNeedsNoCut() {
        val atStart = build(render().copy(freezeAtMs = 0, freezeMs = 500)).valueOf("-filter_complex")
        assertTrue("fps=30000/1001,tpad=start_mode=clone:start_duration=0.500[vout]" in atStart, atStart)
        assertTrue("aresample=44100,adelay=delays=500:all=1,apad,atrim=duration=4.500[aout]" in atStart, atStart)
        assertFalse("split" in atStart, atStart)

        val atEnd = build(render().copy(freezeAtMs = 4_000, freezeMs = 500)).valueOf("-filter_complex")
        assertTrue("fps=30000/1001,tpad=stop_mode=clone:stop_duration=0.500[vout]" in atEnd, atEnd)
        assertTrue("aresample=44100,apad,atrim=duration=4.500[aout]" in atEnd, atEnd)
        assertFalse("split" in atEnd, atEnd)

        val muted = build(render(muted = true).copy(freezeAtMs = 2_000, freezeMs = 500)).valueOf("-filter_complex")
        assertTrue("[0:a]asetpts=PTS-STARTPTS,volume=0,apad,atrim=duration=4.500[aout]" in muted, muted)
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
    fun theZoomGoesInAtTheFirstReplayAndDoesNotGoOut() {
        val zoom = ReplayZoom(0.25, 0.25, 0.5)
        // 29.97 fps x 0.4 s x 0.5 speed: the zoom takes about 6 source frames.
        val first = FFmpegCommandBuilder.replayZoomFilter(render(zoom, zoomIn = true))!!
        assertTrue("clip(in/5.994000,0,1)" in first, first)
        assertFalse("-in)" in first, first)

        // The next replays of the point keep the zoom frame to the end (decision 12), so the filter computes it once.
        val next = FFmpegCommandBuilder.replayZoomFilter(render(zoom, zoomIn = false))!!
        assertTrue("x0='W*(0.250000*1)'" in next, next)
        assertTrue("x1='W*(0.250000*1+(1-0.500000*1))'" in next, next)
        assertTrue(next.endsWith("eval=init"), next)

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
