package org.litvin.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-11 E4-T8: the window-size option for a check of the layout. */
class WindowSizeTest {
    @Test
    fun `a size has the form width x height`() {
        assertEquals(WindowSize(1200, 800), WindowSize.parse("1200x800"))
        assertEquals(WindowSize(1366, 768), WindowSize.parse(" 1366 X 768 "))
        assertEquals(WindowSize(1024, 640), WindowSize.parse("1024×640"))
    }

    @Test
    fun `a wrong or a missing size gives no size`() {
        listOf(null, "", "1200", "1200x", "x800", "1200,800", "big", "-1200x800", "100x800", "1200x99999").forEach { value ->
            assertNull(WindowSize.parse(value), "$value")
        }
    }

    @Test
    fun `the program argument gives the value, and the last argument wins`() {
        assertEquals("1200x800", WindowSize.argumentValue(arrayOf("--restart", "--window-size=1200x800")))
        assertEquals("1024x640", WindowSize.argumentValue(arrayOf("--window-size=1200x800", "--window-size=1024x640")))
        assertNull(WindowSize.argumentValue(arrayOf("--restart")))
    }

    @Test
    fun `a restart keeps the window size`() {
        val launcher = File("C:/Apps/BananaShot/BananaShot.exe")

        assertEquals(
            listOf(launcher.absolutePath, AppRestart.ARGUMENT, "--window-size=1200x800"),
            AppRestart.command(launcher, WindowSize(1200, 800)),
        )
        assertEquals(listOf(launcher.absolutePath, AppRestart.ARGUMENT), AppRestart.command(launcher, windowSize = null))
    }
}
