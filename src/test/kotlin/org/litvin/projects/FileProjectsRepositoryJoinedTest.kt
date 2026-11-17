package org.litvin.projects

import org.junit.jupiter.api.io.TempDir
import org.litvin.license.AllowNewWork
import org.litvin.media.SourceFile
import org.litvin.media.SourceFileInfo
import org.litvin.media.SourceVideo
import org.litvin.scoring.Sport
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-49: a project of several video files of one match. */
class FileProjectsRepositoryJoinedTest {
    @TempDir
    lateinit var dir: File

    private val formats = mutableMapOf<String, List<String>>()
    private val unreadable = mutableSetOf<String>()

    private val repository by lazy {
        FileProjectsRepository(
            dir.resolve("projects"),
            AllowNewWork,
            sourceFileProbe = { path ->
                if (File(path).name in unreadable) null
                else SourceFileInfo(SourceFile(path, 0, 20_000), formats[File(path).name] ?: listOf("video h264 1920x1080 30/1 yuv420p"))
            },
        )
    }

    private fun video(name: String, folder: File = dir.resolve("videos")) =
        folder.apply { mkdirs() }.resolve(name).apply { writeText("video") }

    @Test
    fun `the manifest keeps the files in play order and the project plays the EDL file`() {
        val a = video("DJI_0001.MP4")
        val b = video("DJI_0002.MP4")

        val created = repository.createProject(listOf(a.absolutePath, b.absolutePath), "Final", Sport.TENNIS, Sport.TENNIS.defaultRules())
        val manifest = repository.readManifest(created.summary.path)

        assertNull(created.formatReport)
        assertEquals(a.absolutePath, manifest.sourceVideo)
        assertEquals(listOf(a.absolutePath, b.absolutePath), manifest.sourceVideos?.map { it.path })
        val media = ProjectSource.mediaPath(created.summary.path)!!
        assertTrue(media.endsWith(SourceVideo.EDL_FILE_NAME))
        assertEquals(listOf(a.absolutePath, b.absolutePath), SourceVideo.of(media).files)
        assertEquals(40_000L, SourceVideo.of(media).durationMs)
    }

    @Test
    fun `files with different formats give a format report`() {
        val a = video("A.MP4")
        val b = video("B.MP4")
        formats["B.MP4"] = listOf("video hevc 3840x2160 60/1 yuv420p")

        val created = repository.createProject(listOf(a.absolutePath, b.absolutePath), "Mixed", Sport.TENNIS, Sport.TENNIS.defaultRules())

        val report = assertNotNull(created.formatReport)
        assertTrue("A.MP4: video h264 1920x1080 30/1 yuv420p" in report, report)
        assertTrue("B.MP4: video hevc 3840x2160 60/1 yuv420p" in report, report)
    }

    @Test
    fun `a file that cannot be read stops the creation`() {
        val a = video("A.MP4")
        val b = video("B.MP4")
        unreadable += "B.MP4"

        val error = assertFailsWith<IllegalArgumentException> {
            repository.createProject(listOf(a.absolutePath, b.absolutePath), "Bad", Sport.TENNIS, Sport.TENNIS.defaultRules())
        }
        assertTrue("B.MP4" in error.message.orEmpty())
    }

    @Test
    fun `the stats count all files`() {
        val a = video("A.MP4")
        val b = video("B.MP4")
        val created = repository.createProject(listOf(a.absolutePath, b.absolutePath), "Stats", Sport.TENNIS, Sport.TENNIS.defaultRules())

        val stats = repository.stats(created.summary.path)

        assertEquals(false, stats.videoMissing)
        assertEquals(40_000L, stats.durationMs)
        assertEquals(10L, stats.fileSizeBytes)
    }

    @Test
    fun `after a move, one located file finds the other files in its folder`() {
        val a = video("A.MP4")
        val b = video("B.MP4")
        val created = repository.createProject(listOf(a.absolutePath, b.absolutePath), "Moved", Sport.TENNIS, Sport.TENNIS.defaultRules())
        val moved = dir.resolve("moved").apply { mkdirs() }
        a.renameTo(moved.resolve("A.MP4"))
        b.renameTo(moved.resolve("B.MP4"))
        assertTrue(repository.stats(created.summary.path).videoMissing)

        repository.openProject(created.summary.path, moved.resolve("B.MP4").absolutePath)

        val manifest = repository.readManifest(created.summary.path)
        assertEquals(
            listOf(moved.resolve("A.MP4").absolutePath, moved.resolve("B.MP4").absolutePath),
            manifest.sourceVideos?.map { it.path },
        )
        assertEquals(moved.resolve("A.MP4").absolutePath, manifest.sourceVideo)
        assertEquals(manifest.sourceVideos?.map { it.path }, SourceVideo.of(ProjectSource.mediaPath(created.summary.path)!!).files)
    }

    @Test
    fun `one file gives a project without a file list`() {
        val a = video("A.MP4")

        val created = repository.createProject(listOf(a.absolutePath), "Single", Sport.TENNIS, Sport.TENNIS.defaultRules())
        val manifest = repository.readManifest(created.summary.path)

        assertEquals(a.absolutePath, manifest.sourceVideo)
        assertNull(manifest.sourceVideos)
        assertEquals(a.absolutePath, ProjectSource.mediaPath(created.summary.path))
    }
}
