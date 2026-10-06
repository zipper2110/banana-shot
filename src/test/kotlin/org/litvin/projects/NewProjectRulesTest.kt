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
        assertEquals("Club final", NewProjectRules.suggestedName("C:\\videos\\Club final.mp4"))
        assertEquals("match.2026", NewProjectRules.suggestedName("C:\\videos\\match.2026.mov"))
        assertEquals(NewProjectRules.DEFAULT_NAME, NewProjectRules.suggestedName("C:\\videos\\.mp4"))
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
