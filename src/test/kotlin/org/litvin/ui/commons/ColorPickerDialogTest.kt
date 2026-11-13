package org.litvin.ui.commons

import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorPickerDialogTest {
    /**
     * A press on a swatch selects its color. The test sends no click event,
     * because Swing does not send a click when the mouse moves between press and release.
     */
    @Test
    fun pressOnSwatchSelectsColor() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        var picked: Color? = null
        EventQueue.invokeAndWait {
            val frame = JFrame()
            try {
                val dialog = ColorPickerDialog.build(frame, "test", Color.RED) { picked = it }
                val swatch = find<Component>(dialog.contentPane, "color-picker-swatch")
                swatch.dispatchEvent(press(swatch, clickCount = 1))
                find<AbstractButton>(dialog.contentPane, "color-picker-ok").doClick()
                dialog.dispose()
            } finally {
                frame.dispose()
            }
        }
        // The first swatch is white.
        assertEquals(Color.WHITE, picked)
    }

    /** A double press on a swatch confirms its color without OK. */
    @Test
    fun doublePressOnSwatchConfirmsColor() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        var picked: Color? = null
        EventQueue.invokeAndWait {
            val frame = JFrame()
            try {
                val dialog = ColorPickerDialog.build(frame, "test", Color.RED) { picked = it }
                val swatch = find<Component>(dialog.contentPane, "color-picker-swatch")
                swatch.dispatchEvent(press(swatch, clickCount = 1))
                swatch.dispatchEvent(press(swatch, clickCount = 2))
                dialog.dispose()
            } finally {
                frame.dispose()
            }
        }
        assertEquals(Color.WHITE, picked)
    }

    private fun press(target: Component, clickCount: Int) =
        MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 5, 5, clickCount, false, MouseEvent.BUTTON1)

    private inline fun <reified T : Component> find(root: Container, name: String): T {
        val found = all(root).filterIsInstance<T>().firstOrNull { it.name == name }
        assertTrue(found != null, "No component \"$name\"")
        return found
    }

    private fun all(root: Component): Sequence<Component> = sequence {
        yield(root)
        if (root is Container) root.components.forEach { yieldAll(all(it)) }
    }
}
