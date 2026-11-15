package org.litvin.ui.commons

import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

class ClickListenerTest {
    /** A press and a release at a different point inside the component is a click. Swing sends no mouseClicked then. */
    @Test
    fun aMoveInsideTheComponentKeepsTheClick() {
        val clicks = mutableListOf<Int>()
        val panel = panel(ClickListener { clicks += it.x })

        panel.dispatchEvent(event(panel, MouseEvent.MOUSE_PRESSED, 10))
        panel.dispatchEvent(event(panel, MouseEvent.MOUSE_RELEASED, 11))

        assertEquals(listOf(11), clicks)
    }

    /** A release outside the component is not a click. */
    @Test
    fun aReleaseOutsideTheComponentIsNotAClick() {
        var clicks = 0
        val panel = panel(ClickListener { clicks++ })

        panel.dispatchEvent(event(panel, MouseEvent.MOUSE_PRESSED, 10))
        panel.dispatchEvent(event(panel, MouseEvent.MOUSE_RELEASED, 150))

        assertEquals(0, clicks)
    }

    /** A release without a press on the same component is not a click. */
    @Test
    fun aReleaseAfterAPressOnAnotherComponentIsNotAClick() {
        var clicks = 0
        val listener = ClickListener { clicks++ }
        val first = panel(listener)
        val second = panel(listener)

        first.dispatchEvent(event(first, MouseEvent.MOUSE_PRESSED, 10))
        second.dispatchEvent(event(second, MouseEvent.MOUSE_RELEASED, 10))
        second.dispatchEvent(event(second, MouseEvent.MOUSE_RELEASED, 10))

        assertEquals(0, clicks)
    }

    private fun panel(listener: ClickListener) = JPanel().apply {
        setSize(100, 40)
        addMouseListener(listener)
    }

    private fun event(target: Component, id: Int, x: Int) =
        MouseEvent(target, id, System.currentTimeMillis(), 0, x, 20, 1, false, MouseEvent.BUTTON1)
}
