package org.litvin.projects

import org.junit.jupiter.api.io.TempDir
import org.litvin.license.AllowNewWork
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** B-36: the project name and the project folder name are different. */
class FileProjectsRepositoryNamesTest {
    @TempDir
    lateinit var dir: File

    private val root by lazy { dir.resolve("projects") }
    private val repository by lazy { FileProjectsRepository(root, AllowNewWork) }
    private val video by lazy { dir.resolve("match.mp4").apply { writeText("video") } }

    @Test
    fun `a name that is not a correct file name gets a safe folder and keeps the name`() {
        val final = repository.createProject(video.absolutePath, "Final 3:2")
        val question = repository.createProject(video.absolutePath, "Who won?")

        assertEquals("Final 3:2", final.name)
        assertEquals(File(root, "Final 3_2/Final 3_2.trproj").absolutePath, final.path)
        assertEquals("Who won?", question.name)
        assertEquals(File(root, "Who won_/Who won_.trproj").absolutePath, question.path)
        assertTrue(File(question.path).isFile)
        assertEquals("Who won?", repository.readManifest(question.path).name)
        assertEquals(listOf("Final 3:2", "Who won?"), repository.getRecents().map { it.name }.sorted())
    }

    @Test
    fun `two names with the same safe folder name get different folders`() {
        val first = repository.createProject(video.absolutePath, "Who won?")
        val second = repository.createProject(video.absolutePath, "Who won*")

        assertEquals("Who won?", first.name)
        assertEquals("Who won*", second.name)
        assertNotEquals(File(first.path).parentFile, File(second.path).parentFile)
        assertEquals("Who won_ (2)", File(second.path).parentFile.name)
        assertEquals(setOf("Who won?", "Who won*"), repository.getRecents().map { it.name }.toSet())
    }

    @Test
    fun `the same name twice gets a suffix in the name and in the folder`() {
        repository.createProject(video.absolutePath, "Who won?")
        val second = repository.createProject(video.absolutePath, "who won?")

        assertEquals("who won? (2)", second.name)
        assertEquals("who won_ (2)", File(second.path).parentFile.name)
    }

    @Test
    fun `an existing project whose name is the folder name still opens`() {
        val folder = File(root, "Club final").apply { mkdirs() }
        val manifestPath = File(folder, "Club final.trproj").absolutePath
        ManifestIO.write(
            manifestPath,
            ProjectManifestV1(id = "old", name = "Club final", createdAt = "2026-01-01T00:00:00Z", lastOpenedAt = "2026-01-01T00:00:00Z", version = 1, sourceVideo = video.absolutePath),
        )

        assertEquals("Club final", repository.openProject(manifestPath).name)
        assertEquals("Club final", repository.getRecents().single().name)
    }

    @Test
    fun `a rename to a name that is not a correct file name keeps the folder`() {
        val project = repository.createProject(video.absolutePath, "Match")

        val renamed = repository.renameProject(project.path, "Final 3:2")

        assertEquals("Final 3:2", renamed.name)
        assertEquals(project.path, renamed.path)
        assertTrue(File(renamed.path).isFile)
    }
}
