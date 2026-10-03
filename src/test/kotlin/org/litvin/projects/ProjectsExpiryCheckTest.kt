package org.litvin.projects

import org.junit.jupiter.api.io.TempDir
import org.litvin.license.ExpiredVersionException
import org.litvin.license.NewWorkGate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** E7-S2: the function that opens a project, and the creation of a project, refuse in expired mode. */
class ProjectsExpiryCheckTest {
    @TempDir
    lateinit var dir: File

    private var allowed = true
    private val repository by lazy { FileProjectsRepository(dir.resolve("projects"), NewWorkGate { allowed }) }
    private val video by lazy { dir.resolve("match.mp4").apply { writeText("") } }

    @Test
    fun `opening a project refuses in expired mode and does not change the manifest`() {
        val project = repository.createProject(video.absolutePath, "Match")
        val manifestBefore = File(project.path).readText()
        allowed = false

        assertFailsWith<ExpiredVersionException> { repository.openProject(project.path) }

        assertEquals(manifestBefore, File(project.path).readText())
    }

    @Test
    fun `creating a project refuses in expired mode and makes no folder`() {
        allowed = false

        assertFailsWith<ExpiredVersionException> { repository.createProject(video.absolutePath, "Match") }

        assertTrue(dir.resolve("projects").listFiles().isNullOrEmpty())
    }

    @Test
    fun `in normal mode both functions work`() {
        val project = repository.createProject(video.absolutePath, "Match")

        assertEquals(project.path, repository.openProject(project.path).path)
    }
}
