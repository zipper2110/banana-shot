package org.litvin.media

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.litvin.ApplicationLayout
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoReadCheckTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun fromErrorsFindsTheProblem() {
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }
        // An MP4 file starts with a box size (4 bytes) and the box type "ftyp".
        val mp4Start = tempDir.resolve("start.mp4").toFile().apply { writeBytes(byteArrayOf(0, 0, 0, 32) + "ftypisom".toByteArray()) }

        assertEquals(VideoProblem.NOT_FOUND, VideoProblem.fromErrors(tempDir.resolve("missing.mp4").toFile(), emptyList()))
        val noMoov = listOf("mov,mp4,m4a,3gp,3g2,mj2: moov atom not found", "avformat_open_input() failed")
        assertEquals(VideoProblem.INCOMPLETE, VideoProblem.fromErrors(mp4Start, noMoov))
        assertEquals(VideoProblem.UNREADABLE, VideoProblem.fromErrors(video, noMoov))
        assertEquals(VideoProblem.UNREADABLE, VideoProblem.fromErrors(video, listOf("Invalid data found when processing input")))
        assertEquals(VideoProblem.UNREADABLE, VideoProblem.fromErrors(video, emptyList()))
    }

    @Test
    fun ffprobeCheckFindsAnIncompleteCopy() {
        val ffprobe = ApplicationLayout.current().ffprobeExecutable
        assumeTrue(runCatching { ProcessBuilder(ffprobe, "-version").start().waitFor() == 0 }.getOrDefault(false), "ffprobe is not available")
        val bytes = requireNotNull(javaClass.getResourceAsStream("/media/ui-smoke.mp4")).use { it.readBytes() }
        val complete = tempDir.resolve("complete.mp4").toFile().apply { writeBytes(bytes) }
        // The fixture has the moov atom at the end, as phone videos have. Half of the file has no moov atom.
        val incomplete = tempDir.resolve("incomplete.mp4").toFile().apply { writeBytes(bytes.copyOf(bytes.size / 2)) }
        val notVideo = tempDir.resolve("notes.mp4").toFile().apply { writeText("These are notes, not a video.") }
        val check = FfprobeVideoReadCheck({ ffprobe })

        assertNull(check.problem(complete.absolutePath))
        assertNull(check.problem("  ${complete.absolutePath}  "))
        assertEquals(VideoProblem.INCOMPLETE, check.problem(incomplete.absolutePath))
        assertEquals(VideoProblem.UNREADABLE, check.problem(notVideo.absolutePath))
        assertEquals(VideoProblem.NOT_FOUND, check.problem(tempDir.resolve("missing.mp4").toString()))
    }

    @Test
    fun ffprobeCheckDoesNotStopTheUserWhenFfprobeIsMissing() {
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }
        val check = FfprobeVideoReadCheck({ File(tempDir.toFile(), "no-ffprobe.exe").absolutePath })

        assertNull(check.problem(video.absolutePath))
    }
}
