package org.litvin.ui.commons

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShowInFolderTest {
    @TempDir
    lateinit var tempDir: Path

    private val commands = mutableListOf<List<String>>()
    private val opened = mutableListOf<File>()

    private fun showInFolder(osName: String, openFails: Boolean = false) = ShowInFolder(
        osName = osName,
        start = { commands += it },
        openFolder = { if (openFails) throw IOException("no file manager") else opened += it },
    )

    @Test
    fun finderSelectsAnExistingFileOnMacos() {
        val file = tempDir.resolve("match.mp4").toFile().apply { writeText("x") }

        showInFolder("Mac OS X").show(file)

        assertEquals(listOf(listOf("open", "-R", file.absolutePath)), commands)
    }

    @Test
    fun explorerSelectsAnExistingFileOnWindows() {
        val file = tempDir.resolve("match.mp4").toFile().apply { writeText("x") }

        showInFolder("Windows 11").show(file)

        assertEquals(listOf(listOf("explorer.exe", "/select,", file.absolutePath)), commands)
    }

    @Test
    fun aMissingFileOpensItsFolder() {
        val file = tempDir.resolve("missing.mp4").toFile()

        showInFolder("Mac OS X").show(file)

        assertEquals(listOf(tempDir.toFile().absoluteFile), opened)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun theSystemCommandOpensTheFolderWhenDesktopFails() {
        val file = tempDir.resolve("missing.mp4").toFile()

        showInFolder("Mac OS X", openFails = true).show(file)
        showInFolder("Windows 11", openFails = true).show(file)

        val folder = tempDir.toFile().absolutePath
        assertEquals(listOf(listOf("open", folder), listOf("explorer.exe", folder)), commands)
        assertFailsWith<IOException> { showInFolder("Linux", openFails = true).show(file) }
    }
}
