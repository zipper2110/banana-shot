package org.litvin

import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers a source without an audio stream. Each pass must write silent audio and must not refer to the source audio. */
class FFmpegCommandBuilderSilentSourceTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private fun build(
        keeps: List<PointV1> = emptyList(),
        freeze: FFmpegCommandBuilder.FreezeFrame? = null,
        sourceHasAudio: Boolean = false,
    ) = FFmpegCommandBuilder.build(
        FFmpegCommandBuilder.BuildParams(
            sourcePath = "silent.mp4",
            outputPath = "out.mp4",
            preset = preset,
            outWidth = 1920,
            outHeight = 1080,
            idleTrim = true,
            keeps = keeps,
            freezeFrame = freeze,
            sourceHasAudio = sourceHasAudio,
        )
    ).args

    private fun List<String>.valueOf(flag: String) = this[indexOf(flag) + 1]

    @Test
    fun eachSegmentGetsSilentAudioOfItsOwnDuration() {
        val keeps = listOf(PointV1(id = "A", startMs = 1_000, endMs = 3_500), PointV1(id = "B", startMs = 10_000, endMs = 12_000))
        val filter = build(keeps).valueOf("-filter_complex")

        assertFalse(":a]" in filter, filter)
        assertTrue("anullsrc=r=48000:cl=stereo,atrim=duration=2.500[a0]" in filter, filter)
        assertTrue("anullsrc=r=48000:cl=stereo,atrim=duration=2.000[a1]" in filter, filter)
        assertTrue("[a0][a1]concat=n=2:v=0:a=1[aout]" in filter, filter)
    }

    @Test
    fun theFreezeGetsSilentAudioOfTheCardDuration() {
        val filter = build(freeze = FFmpegCommandBuilder.FreezeFrame(atMs = 5_000, durationMs = 12_000))
            .valueOf("-filter_complex")

        assertFalse("[0:a]" in filter, filter)
        assertTrue("anullsrc=r=48000:cl=stereo,atrim=duration=12.000[aout]" in filter, filter)
    }

    @Test
    fun theFullVideoTakesSilentAudioFromASecondInputAndStopsWithTheVideo() {
        val args = build()

        assertEquals(listOf("-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"), args.subList(args.lastIndexOf("-i") - 2, args.lastIndexOf("-i") + 2))
        assertEquals("0:v:0", args.valueOf("-map"))
        assertTrue("1:a:0" in args)
        assertTrue("-shortest" in args)
    }

    @Test
    fun aSourceWithAudioKeepsItsAudio() {
        val keeps = listOf(PointV1(id = "A", startMs = 1_000, endMs = 3_500))
        val filter = build(keeps, sourceHasAudio = true).valueOf("-filter_complex")

        assertTrue("[0:a]asetpts=PTS-STARTPTS[a0]" in filter, filter)
        assertFalse("anullsrc" in filter, filter)
        assertFalse("lavfi" in build(sourceHasAudio = true))
    }
}
