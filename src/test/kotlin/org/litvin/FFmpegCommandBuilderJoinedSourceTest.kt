package org.litvin

import org.junit.jupiter.api.io.TempDir
import org.litvin.media.SourceFile
import org.litvin.media.SourceVideo
import org.litvin.points.PointV1
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-49: the export of a match of several files reads each range from its files and joins them with the concat filter. */
class FFmpegCommandBuilderJoinedSourceTest {
    @TempDir
    lateinit var dir: File

    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private val edl by lazy {
        File(dir, SourceVideo.EDL_FILE_NAME).also { file ->
            SourceVideo.write(
                file,
                listOf(SourceFile("A.MP4", 0, 20_000), SourceFile("B.MP4", 0, 20_000), SourceFile("C.MP4", 0, 20_000)),
            )
        }.absolutePath
    }

    private fun build(
        keeps: List<PointV1> = emptyList(),
        idleTrim: Boolean = true,
        freeze: FFmpegCommandBuilder.FreezeFrame? = null,
        replay: FFmpegCommandBuilder.ReplayRender? = null,
        sourceHasAudio: Boolean = true,
    ) = FFmpegCommandBuilder.build(
        FFmpegCommandBuilder.BuildParams(
            sourcePath = edl,
            outputPath = "out.mp4",
            preset = preset,
            outWidth = 1920,
            outHeight = 1080,
            idleTrim = idleTrim,
            keeps = keeps,
            freezeFrame = freeze,
            replay = replay,
            sourceHasAudio = sourceHasAudio,
        )
    ).args

    private fun List<String>.valueOf(flag: String) = this[indexOf(flag) + 1]

    @Test
    fun `a point inside one file reads only that file`() {
        val args = build(listOf(PointV1(id = "A", startMs = 25_000, endMs = 30_000)))

        assertEquals(listOf("-ss", "5.000", "-t", "5.000", "-i", "B.MP4"), args.subList(args.indexOf("-i") - 4, args.indexOf("-i") + 2))
        assertFalse("concat=n=2" in args.valueOf("-filter_complex"))
    }

    @Test
    fun `a point across a file end reads both files and joins them first`() {
        val args = build(listOf(PointV1(id = "A", startMs = 18_000, endMs = 23_000)))
        val filter = args.valueOf("-filter_complex")

        assertEquals(
            listOf("-ss", "18.000", "-t", "2.000", "-i", "A.MP4", "-ss", "0.000", "-t", "3.000", "-i", "B.MP4"),
            args.subList(args.indexOf("-i") - 4, args.indexOf("-i") + 8),
        )
        assertTrue(
            filter.startsWith(
                "[0:v]setpts=PTS-STARTPTS[j0v0];[0:a]asetpts=PTS-STARTPTS[j0a0];" +
                    "[1:v]setpts=PTS-STARTPTS[j0v1];[1:a]asetpts=PTS-STARTPTS[j0a1];" +
                    "[j0v0][j0a0][j0v1][j0a1]concat=n=2:v=1:a=1[j0v][j0a];",
            ),
            filter,
        )
        assertTrue("[j0v]setpts=PTS-STARTPTS[v0]" in filter, filter)
        assertTrue("[j0a]asetpts=PTS-STARTPTS[a0]" in filter, filter)
    }

    @Test
    fun `the inputs of the next point come after the inputs of a joined point`() {
        val args = build(
            listOf(PointV1(id = "A", startMs = 18_000, endMs = 23_000), PointV1(id = "B", startMs = 45_000, endMs = 47_000)),
        )
        val filter = args.valueOf("-filter_complex")

        assertEquals(listOf("A.MP4", "B.MP4", "C.MP4"), args.indices.filter { args[it] == "-i" }.map { args[it + 1] })
        assertTrue("[2:v]setpts=PTS-STARTPTS[v1]" in filter, filter)
        assertTrue("[2:a]asetpts=PTS-STARTPTS[a1]" in filter, filter)
    }

    @Test
    fun `the whole video reads all files`() {
        val args = build(idleTrim = false)
        val filter = args.valueOf("-filter_complex")

        assertEquals(listOf("A.MP4", "B.MP4", "C.MP4"), args.indices.filter { args[it] == "-i" }.map { args[it + 1] })
        assertTrue("concat=n=3:v=1:a=1[j0v][j0a]" in filter, filter)
        assertEquals("[vsc]", args.valueOf("-map"))
    }

    @Test
    fun `a source without audio joins only the video`() {
        val filter = build(listOf(PointV1(id = "A", startMs = 18_000, endMs = 23_000)), sourceHasAudio = false)
            .valueOf("-filter_complex")

        assertTrue("[j0v0][j0v1]concat=n=2:v=1:a=0[j0v]" in filter, filter)
        assertFalse(":a]" in filter, filter)
    }

    @Test
    fun `the still frame at the end reads the end of the last file`() {
        val args = build(freeze = FFmpegCommandBuilder.FreezeFrame(atMs = null, durationMs = 5_000))

        assertEquals(listOf("-sseof", "-0.250", "-i", "C.MP4"), args.subList(args.indexOf("-i") - 2, args.indexOf("-i") + 2))
    }

    @Test
    fun `a still frame reads its file`() {
        val args = build(freeze = FFmpegCommandBuilder.FreezeFrame(atMs = 41_000, durationMs = 5_000))

        assertEquals(listOf("-ss", "0.750", "-t", "0.250", "-i", "C.MP4"), args.subList(args.indexOf("-i") - 4, args.indexOf("-i") + 2))
    }

    @Test
    fun `a replay across a file end joins its parts`() {
        val replay = FFmpegCommandBuilder.ReplayRender(
            startMs = 39_000,
            endMs = 41_000,
            speed = 0.5,
            zoom = null,
            zoomIn = false,
            muted = false,
            sourceFrameRate = null,
            sampleRate = 48_000,
        )
        val args = build(replay = replay)
        val filter = args.valueOf("-filter_complex")

        assertEquals(listOf("B.MP4", "C.MP4"), args.indices.filter { args[it] == "-i" }.map { args[it + 1] })
        assertTrue("concat=n=2:v=1:a=1[j0v][j0a]" in filter, filter)
        assertTrue("[j0v]" in filter.substringAfter("[j0v][j0a];"), filter)
        assertTrue("[j0a]asetpts=PTS-STARTPTS,asetrate=24000" in filter, filter)
    }

    @Test
    fun `a still frame of the moment strip reads its file at the time in that file`() {
        val args = FFmpegCommandBuilder.stillFrameArgs(edl, atMs = 30_040, outputPath = "f.png", width = 640)

        assertEquals(listOf("-ss", "10.000", "-i", "B.MP4"), args.subList(args.indexOf("-ss"), args.indexOf("-i") + 2))
    }
}
