package org.litvin.media

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-49: a source video of several files plays as one timeline. */
class SourceVideoTest {
    @TempDir
    lateinit var dir: File

    private val a = SourceFile("C:\\v\\DJI_0001.MP4", startMs = 45, durationMs = 21_021)
    private val b = SourceFile("C:\\v\\DJI_0002.MP4", startMs = 0, durationMs = 20_000)
    private val c = SourceFile("C:\\v\\DJI_0003.MP4", startMs = 0, durationMs = 19_000)

    private fun joined(vararg files: SourceFile): SourceVideo {
        val edl = File(dir, SourceVideo.EDL_FILE_NAME)
        SourceVideo.write(edl, files.toList())
        return SourceVideo.of(edl.absolutePath)
    }

    @Test
    fun `one video file gives the same times on that file`() {
        val source = SourceVideo.of("C:\\v\\match.mp4")

        assertFalse(source.isJoined)
        assertEquals(listOf("C:\\v\\match.mp4"), source.files)
        assertEquals(null, source.durationMs)
        assertEquals(listOf(SourceVideo.Span("C:\\v\\match.mp4", 1_000, 2_000)), source.spans(1_000, 3_000))
        assertEquals(listOf(SourceVideo.Span("C:\\v\\match.mp4", 1_000, null)), source.spans(1_000, null))
        assertEquals(SourceVideo.Span("C:\\v\\match.mp4", 5_000, null), source.at(5_000))
    }

    @Test
    fun `the EDL file keeps the files, the start times and the lengths`() {
        val source = joined(a, b, c)

        assertTrue(source.isJoined)
        assertEquals(listOf(a.path, b.path, c.path), source.files)
        assertEquals(60_021L, source.durationMs)
        assertEquals(listOf(0L, 21_021L, 41_021L), source.parts.map { it.offsetMs })
        assertEquals(45L, source.parts.first().startMs)
    }

    @Test
    fun `the EDL text has the byte length of each path, so mpv reads a path with a comma or a non-ASCII letter`() {
        val text = SourceVideo.edlText(listOf(SourceFile("C:\\Видео\\a,b.mp4", 45, 21_021)))

        assertEquals("# mpv EDL v0\n%21%C:\\Видео\\a,b.mp4,start=0.045,length=21.021\n", text)
        val parsed = SourceVideo.parse("x.edl", text)!!
        assertEquals("C:\\Видео\\a,b.mp4", parsed.firstFile)
        assertEquals(21_021L, parsed.durationMs)
    }

    @Test
    fun `a range inside one file gives one input in the time of that file`() {
        val source = joined(a, b, c)

        assertEquals(listOf(SourceVideo.Span(b.path, 4_979, 5_000)), source.spans(26_000, 31_000))
    }

    @Test
    fun `a range across the end of a file gives one input for each file`() {
        val source = joined(a, b, c)

        assertEquals(
            listOf(SourceVideo.Span(a.path, 20_000, 1_021), SourceVideo.Span(b.path, 0, 3_979)),
            source.spans(20_000, 25_000),
        )
    }

    @Test
    fun `a range that ends at a file end does not add an empty input`() {
        val source = joined(a, b, c)

        assertEquals(listOf(SourceVideo.Span(a.path, 20_000, 1_021)), source.spans(20_000, 21_021))
        assertEquals(listOf(SourceVideo.Span(b.path, 0, 1_000)), source.spans(21_021, 22_021))
    }

    @Test
    fun `the whole timeline gives all files, and the last input goes to the end of its file`() {
        val source = joined(a, b, c)

        assertEquals(
            listOf(
                SourceVideo.Span(a.path, 0, 21_021),
                SourceVideo.Span(b.path, 0, 20_000),
                SourceVideo.Span(c.path, 0, null),
            ),
            source.spans(0, null),
        )
    }

    @Test
    fun `a range past the end stays in the last file, as for one video file`() {
        val source = joined(a, b)

        assertEquals(listOf(SourceVideo.Span(b.path, 19_000, 2_000)), source.spans(40_021, 42_021))
    }

    @Test
    fun `a time gives its file and the time in that file`() {
        val source = joined(a, b, c)

        assertEquals(SourceVideo.Span(a.path, 0, null), source.at(0))
        assertEquals(SourceVideo.Span(a.path, 21_020, null), source.at(21_020))
        assertEquals(SourceVideo.Span(b.path, 0, null), source.at(21_021))
        assertEquals(SourceVideo.Span(c.path, 18_979, null), source.at(60_000))
    }

    @Test
    fun `a range splits at the file ends`() {
        val source = joined(a, b, c)

        assertEquals(listOf(20_000L to 21_021L, 21_021L to 41_021L, 41_021L to 42_000L), source.splitAtFiles(20_000, 42_000))
        assertEquals(listOf(1_000L to 2_000L), source.splitAtFiles(1_000, 2_000))
        assertEquals(listOf(1_000L to 2_000L), SourceVideo.of("C:\\v\\match.mp4").splitAtFiles(1_000, 2_000))
    }

    @Test
    fun `missing files are the files that are not on the disk`() {
        val present = File(dir, "present.mp4").apply { writeText("video") }
        val source = joined(SourceFile(present.absolutePath, 0, 1_000), SourceFile(File(dir, "gone.mp4").absolutePath, 0, 1_000))

        assertEquals(listOf(File(dir, "gone.mp4").absolutePath), source.missingFiles())
        assertFalse(source.filesExist())
    }

    @Test
    fun `a new EDL text replaces the old file`() {
        joined(a, b)
        val source = joined(a, c)

        assertEquals(listOf(a.path, c.path), source.files)
    }
}
