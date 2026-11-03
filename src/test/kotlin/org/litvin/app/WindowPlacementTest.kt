package org.litvin.app

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-13: the saved main window opens on a connected screen. */
class WindowPlacementTest {
    private val laptop = Rectangle(0, 25, 1440, 875)
    private val display = Rectangle(-560, -1440, 2560, 1440)

    @Test
    fun `a window on a connected screen keeps its place`() {
        val onLaptop = Rectangle(100, 100, 1200, 700)
        val onDisplay = Rectangle(-402, -974, 1510, 816)
        assertEquals(onLaptop, WindowPlacement.onScreen(onLaptop, listOf(laptop), laptop))
        assertEquals(onDisplay, WindowPlacement.onScreen(onDisplay, listOf(laptop, display), laptop))
    }

    @Test
    fun `a window on a disconnected screen moves to the middle of the main screen`() {
        val onDisplay = Rectangle(-402, -974, 1510, 816)
        // The window is wider than the laptop screen, so it gets the width of the screen.
        assertEquals(Rectangle(0, 54, 1440, 816), WindowPlacement.onScreen(onDisplay, listOf(laptop), laptop))
    }

    @Test
    fun `a window with only a small part of its title bar on the screen moves`() {
        val almostGone = Rectangle(1400, 100, 1000, 700)
        assertEquals(Rectangle(220, 112, 1000, 700), WindowPlacement.onScreen(almostGone, listOf(laptop), laptop))
    }
}
