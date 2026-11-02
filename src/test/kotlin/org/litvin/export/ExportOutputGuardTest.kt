package org.litvin.export

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportOutputGuardTest {
    @TempDir
    lateinit var dir: File

    private fun source(): File = File(dir, "match.mp4").apply { writeText("video") }

    @Test
    fun `the source path is the source video`() {
        val source = source()

        assertTrue(ExportOutputGuard.isSourceVideo(File(source.path), source, windows = false))
    }

    @Test
    fun `a path with dot segments to the source is the source video`() {
        val source = source()
        File(dir, "sub").mkdirs()

        assertTrue(ExportOutputGuard.isSourceVideo(File(dir, "sub/../match.mp4"), source, windows = false))
    }

    @Test
    fun `on Windows a different letter case is the source video`() {
        val source = File(dir, "match.mp4")

        // The files do not exist, so only the path comparison can find the match.
        assertTrue(ExportOutputGuard.isSourceVideo(File(dir, "MATCH.MP4"), source, windows = true))
        assertFalse(ExportOutputGuard.isSourceVideo(File(dir, "MATCH.MP4"), source, windows = false))
    }

    @Test
    fun `a symbolic link to the source is the source video`() {
        val source = source()
        val link = File(dir, "link.mp4")
        Files.createSymbolicLink(link.toPath(), source.toPath())

        assertTrue(ExportOutputGuard.isSourceVideo(link, source, windows = false))
    }

    @Test
    fun `a hard link to the source is the source video`() {
        val source = source()
        val link = File(dir, "hard.mp4")
        Files.createLink(link.toPath(), source.toPath())

        assertTrue(ExportOutputGuard.isSourceVideo(link, source, windows = false))
    }

    @Test
    fun `a different file is not the source video`() {
        val source = source()
        val other = File(dir, "other.mp4").apply { writeText("video") }

        assertFalse(ExportOutputGuard.isSourceVideo(other, source, windows = false))
        assertFalse(ExportOutputGuard.isSourceVideo(File(dir, "new.mp4"), source, windows = false))
        assertFalse(ExportOutputGuard.isSourceVideo(File(dir, "match.mp4.part"), source, windows = true))
    }
}
