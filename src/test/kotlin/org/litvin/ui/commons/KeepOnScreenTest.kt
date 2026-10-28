package org.litvin.ui.commons

import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-11 E3-T22: a dialog that grows with a longer translation stays above the taskbar. On a screen of
 * 1536 × 960 with a taskbar of 48, the padel rules of the New project dialog in `qps` reached y = 946.
 */
class KeepOnScreenTest {
    private val area = Rectangle(0, 0, 1536, 912)

    @Test
    fun `a dialog below the taskbar moves up by the smallest step`() {
        assertEquals(Point(570, 247), DialogKit.keptLocation(Rectangle(570, 281, 552, 665), area))
    }

    @Test
    fun `a dialog in the area does not move`() {
        assertEquals(Point(570, 281), DialogKit.keptLocation(Rectangle(570, 281, 552, 600), area))
    }

    @Test
    fun `a dialog out of the left or the top edge moves in, and a dialog larger than the area keeps its top left corner`() {
        assertEquals(Point(0, 0), DialogKit.keptLocation(Rectangle(-20, -5, 400, 300), area))
        assertEquals(Point(0, 0), DialogKit.keptLocation(Rectangle(100, 100, 1600, 1000), area))
    }
}
