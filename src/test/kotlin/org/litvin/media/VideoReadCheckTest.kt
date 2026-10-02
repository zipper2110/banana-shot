package org.litvin.media

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.litvin.ApplicationLayout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoReadCheckTest {
    @TempDir
    lateinit var tempDir: Path

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    @Test
    fun findReportsOnlyProvedProblems() {
        val bytes = fixture()
        val complete = tempDir.resolve("complete.mp4").toFile().apply { writeBytes(bytes) }
        val half = tempDir.resolve("half.mp4").toFile().apply { writeBytes(bytes.copyOf(bytes.size / 2)) }
        val notVideo = tempDir.resolve("notes.mp4").toFile().apply { writeText("These are notes, not a video.") }

        assertNull(VideoProblem.find(complete))
        assertNull(VideoProblem.find(notVideo))
        assertEquals(VideoProblem.TRUNCATED, VideoProblem.find(half))
        assertEquals(VideoProblem.NOT_FOUND, VideoProblem.find(tempDir.resolve("missing.mp4").toFile()))
    }

    @Test
    fun aProgramThatWritesTheFileIsFound() {
        assumeTrue(isWindows, "The check works only on Windows")
        val video = tempDir.resolve("copying.mp4").toFile().apply { writeBytes(fixture()) }

        assertFalse(FileWriteCheck.isOpenForWriting(video))
        FileOutputStream(video, true).use {
            assertTrue(FileWriteCheck.isOpenForWriting(video))
            assertEquals(VideoProblem.STILL_WRITING, VideoProblem.find(video))
        }
        // A program that only reads the file, for example a video player, is not a writer.
        FileInputStream(video).use { assertFalse(FileWriteCheck.isOpenForWriting(video)) }
        assertFalse(FileWriteCheck.isOpenForWriting(tempDir.resolve("missing.mp4").toFile()))
    }

    @Test
    fun ffprobeCheckReportsTheProblem() {
        val ffprobe = ApplicationLayout.current().ffprobeExecutable
        assumeTrue(runCatching { ProcessBuilder(ffprobe, "-version").start().waitFor() == 0 }.getOrDefault(false), "ffprobe is not available")
        val bytes = fixture()
        val complete = tempDir.resolve("complete.mp4").toFile().apply { writeBytes(bytes) }
        val half = tempDir.resolve("half.mp4").toFile().apply { writeBytes(bytes.copyOf(bytes.size / 2)) }
        val notVideo = tempDir.resolve("notes.mp4").toFile().apply { writeText("These are notes, not a video.") }
        val check = FfprobeVideoReadCheck({ ffprobe })

        assertNull(check.problem(complete.absolutePath))
        assertNull(check.problem("  ${complete.absolutePath}  "))
        assertEquals(VideoProblem.TRUNCATED, check.problem(half.absolutePath))
        assertEquals(VideoProblem.UNREADABLE, check.problem(notVideo.absolutePath))
        assertEquals(VideoProblem.NOT_FOUND, check.problem(tempDir.resolve("missing.mp4").toString()))
    }

    @Test
    fun ffprobeCheckDoesNotStopTheUserWhenFfprobeIsMissing() {
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }
        val check = FfprobeVideoReadCheck({ File(tempDir.toFile(), "no-ffprobe.exe").absolutePath })

        assertNull(check.problem(video.absolutePath))
    }

    private fun fixture(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/media/ui-smoke.mp4")).use { it.readBytes() }
}
