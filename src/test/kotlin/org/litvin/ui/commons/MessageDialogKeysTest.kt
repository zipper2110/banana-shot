package org.litvin.ui.commons

import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeFalse

/** The Delete key in a confirmation that has `confirmKeys` (the deletion of a point). */
class MessageDialogKeysTest {
    private var clicked = -1
    private var time = 0L

    private fun dialog() = MessageDialog.build(
        null, MessageKind.DANGER, "Delete point", "Delete?",
        listOf(MessageButton("Cancel"), MessageButton("Delete", UiButton.Kind.DANGER)), defaultIndex = 1,
    ) { clicked = it }.also { MessageDialog.bindDeleteKey(it, 1) { time } }

    private fun key(dialog: javax.swing.JDialog, released: Boolean) {
        val stroke = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0, released)
        val name = dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke)
        dialog.rootPane.actionMap.get(name).actionPerformed(ActionEvent(dialog, ActionEvent.ACTION_PERFORMED, ""))
    }

    @Test
    fun `Enter clicks the delete button`() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val dialog = dialog()
        assertEquals("message-button-1", dialog.rootPane.defaultButton.name)
        dialog.dispose()
    }

    @Test
    fun `a Delete press after a release deletes`() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val dialog = dialog()
        key(dialog, released = true)
        key(dialog, released = false)
        assertEquals(1, clicked)
    }

    @Test
    fun `a held Delete key does not delete`() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val dialog = dialog()
        time = 450
        key(dialog, released = false)
        time = 2_000
        key(dialog, released = false)
        assertEquals(-1, clicked)
        key(dialog, released = true)
        key(dialog, released = false)
        assertEquals(1, clicked)
    }

    @Test
    fun `a Delete press in a dialog that the mouse opened deletes after the guard time`() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val dialog = dialog()
        time = MessageDialog.KEY_GUARD_MS
        key(dialog, released = false)
        assertEquals(1, clicked)
    }
}
