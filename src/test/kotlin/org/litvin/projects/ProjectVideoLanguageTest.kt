package org.litvin.projects

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import org.litvin.license.AllowNewWork
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** B-11 E5-T1: the project keeps the video language. */
class ProjectVideoLanguageTest {
    @TempDir
    lateinit var dir: File

    private val repository by lazy { FileProjectsRepository(dir.resolve("projects"), AllowNewWork) }
    private val video by lazy { dir.resolve("match.mp4").apply { writeText("video") } }

    @AfterEach
    fun restore() {
        I18n.install(Messages.english())
    }

    @Test
    fun `a project keeps the selected video language`() {
        val project = repository.createProject(video.absolutePath, "Final")

        ProjectVideoLanguage.save(project.path, AppLanguage.SPANISH_LATIN_AMERICA)

        assertEquals("es-419", ManifestIO.read(project.path).videoLanguage)
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, ProjectVideoLanguage.read(project.path))
    }

    @Test
    fun `a project without a selection follows the app language`() {
        val project = repository.createProject(video.absolutePath, "Final")

        assertEquals(AppLanguage.ENGLISH, ProjectVideoLanguage.read(project.path))
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        assertEquals(AppLanguage.SPANISH_SPAIN, ProjectVideoLanguage.read(project.path))
        assertFalse(File(project.path).readText().contains("videoLanguage"))
    }

    @Test
    fun `a manifest of the current release gives the app language`() {
        val manifestPath = dir.resolve("old.trproj").apply {
            writeText(
                """
                {
                  "id" : "0d6c8c1e-4b8e-4f8e-9d55-1f1b1c3f0a11",
                  "name" : "Old project",
                  "createdAt" : "2026-09-01T10:00:00Z",
                  "lastOpenedAt" : "2026-10-01T10:00:00Z",
                  "version" : 1,
                  "sourceVideo" : "C:\\Videos\\match.mp4"
                }
                """.trimIndent(),
            )
        }.absolutePath
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))

        assertEquals(AppLanguage.SPANISH_SPAIN, ProjectVideoLanguage.read(manifestPath))
    }

    @Test
    fun `an unknown or broken language gives the app language and stays in the project`() {
        for (stored in listOf("eo", "12!")) {
            val project = repository.createProject(video.absolutePath, "Project $stored".replace("!", ""))
            ManifestIO.write(project.path, ManifestIO.read(project.path).copy(videoLanguage = stored))

            assertEquals(AppLanguage.ENGLISH, ProjectVideoLanguage.read(project.path), stored)
            // The app saves the project again without a change of the user.
            repository.openProject(project.path, null)
            repository.renameProject(project.path, "Renamed $stored".replace("!", ""))

            assertEquals(stored, ManifestIO.read(project.path).videoLanguage)
        }
    }

    @Test
    fun `a manifest that the app cannot read gives the app language`() {
        assertEquals(AppLanguage.ENGLISH, ProjectVideoLanguage.read(dir.resolve("missing.trproj").absolutePath))
        assertEquals(AppLanguage.ENGLISH, ProjectVideoLanguage.read(null))
    }
}
