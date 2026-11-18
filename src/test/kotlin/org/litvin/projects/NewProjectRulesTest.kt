package org.litvin.projects

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NewProjectRulesTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun suggestedNameIsTheVideoFileNameWithoutTheExtension() {
        // The paths of this system: "C:\\videos\\..." on Windows, "/videos/..." on macOS.
        val videos = java.io.File(java.io.File.listRoots().first(), "videos")
        assertEquals("Club final", NewProjectRules.suggestedName(java.io.File(videos, "Club final.mp4").path))
        assertEquals("match.2026", NewProjectRules.suggestedName(java.io.File(videos, "match.2026.mov").path))
        assertEquals(NewProjectRules.DEFAULT_NAME, NewProjectRules.suggestedName(java.io.File(videos, ".mp4").path))
    }

    @Test
    fun theFilesOfOneMatchSortByNameWithTheNumbersAsNumbers() {
        val folder = java.io.File(java.io.File.listRoots().first(), "videos")
        fun path(name: String) = java.io.File(folder, name).path

        assertEquals(
            listOf(path("DJI_0001.MP4"), path("DJI_0002.MP4"), path("DJI_0010.MP4")),
            NewProjectRules.sortedVideoPaths(listOf(path("DJI_0010.MP4"), path("DJI_0001.MP4"), path("DJI_0002.MP4"))),
        )
        assertEquals(
            listOf(path("part2.mp4"), path("Part10.mp4")),
            NewProjectRules.sortedVideoPaths(listOf(path("Part10.mp4"), path("part2.mp4"), path("part2.mp4"))),
        )
    }

    @Test
    fun addedFilesComeAtTheEndSortedByNameWithoutTheFilesThatAreInTheList() {
        val root = java.io.File.listRoots().first()
        fun path(folder: String, name: String) = java.io.File(java.io.File(root, folder), name).path
        val current = listOf(path("a", "GH03.MP4"), path("a", "GH01.MP4"))

        assertEquals(
            current + listOf(path("b", "GH02.MP4"), path("b", "GH10.MP4")),
            NewProjectRules.appendVideoPaths(
                current,
                listOf(path("b", "GH10.MP4"), path("A", "gh01.mp4"), path("b", "GH02.MP4"), path("b", "GH02.MP4")),
            ),
        )
        assertEquals(current, NewProjectRules.appendVideoPaths(current, listOf(path("a", "GH03.MP4"))))
        assertEquals(listOf(path("a", "x.mp4")), NewProjectRules.appendVideoPaths(emptyList(), listOf(path("a", "x.mp4"))))
    }

    @Test
    fun nameMustNotBeEmptyOrTooLong() {
        assertNull(NewProjectRules.nameError("Club final"))
        assertNull(NewProjectRules.nameError("  Club final (2)  "))
        assertNotNull(NewProjectRules.nameError(""))
        assertNotNull(NewProjectRules.nameError("   "))
        assertNull(NewProjectRules.nameError("x".repeat(NewProjectRules.MAX_NAME_LENGTH)))
        assertNotNull(NewProjectRules.nameError("x".repeat(NewProjectRules.MAX_NAME_LENGTH + 1)))
    }

    @Test
    fun nameCanHaveCharactersThatWindowsDoesNotPermitInAFileName() {
        assertNull(NewProjectRules.nameError("Final 3:2"))
        assertNull(NewProjectRules.nameError("Who won?"))
        assertNull(NewProjectRules.nameError("a/b"))
        assertNull(NewProjectRules.nameError("Final."))
        assertNull(NewProjectRules.nameError("con"))
    }

    @Test
    fun folderNameIsACorrectWindowsFileName() {
        assertEquals("Final 3_2", NewProjectRules.folderName("Final 3:2"))
        assertEquals("Who won_", NewProjectRules.folderName("  Who won?  "))
        assertEquals("con_", NewProjectRules.folderName("con"))
        assertEquals("Club final", NewProjectRules.folderName("Club final"))
        assertEquals("Project", NewProjectRules.folderName("..."))
    }

    @Test
    fun sourceVideoMustBeAnExistingNonEmptySupportedVideoFile() {
        val video = tempDir.resolve("match.MP4").toFile().apply { writeText("video") }
        val empty = tempDir.resolve("empty.mp4").toFile().apply { createNewFile() }
        val text = tempDir.resolve("notes.txt").toFile().apply { writeText("notes") }

        assertNull(NewProjectRules.sourceVideoError(video.absolutePath))
        assertNull(NewProjectRules.sourceVideoError("  ${video.absolutePath}  "))
        assertNotNull(NewProjectRules.sourceVideoError(""))
        assertNotNull(NewProjectRules.sourceVideoError(tempDir.resolve("missing.mp4").toString()))
        assertNotNull(NewProjectRules.sourceVideoError(tempDir.toString()))
        assertNotNull(NewProjectRules.sourceVideoError(empty.absolutePath))
        assertNotNull(NewProjectRules.sourceVideoError(text.absolutePath))
    }
}
