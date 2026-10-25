package org.litvin.ui.commons

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.litvin.points.CommentStyle
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JSlider
import javax.swing.JTextArea
import javax.swing.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentDialogTest {
    /** Enter in the text box adds a new line. It does not save. Save keeps all the lines. */
    @Test
    fun enterAddsLineAndSaveKeepsLines() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        var saved: CommentInput? = null
        var savedBeforeSave: CommentInput? = null
        var text = ""
        EventQueue.invokeAndWait {
            val frame = JFrame()
            try {
                val dialog = CommentDialog.buildCreate(frame, "test", 1_000L, "#FFFFFF", CommentStyle.OUTLINE) { saved = it }
                val area = find<JTextArea>(dialog.contentPane, "test-comment-text")
                area.text = "First line"
                area.caretPosition = area.document.length
                // The text box handles Enter itself, so the default button (Save) does not get it.
                val binding = area.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
                area.actionMap.get(binding).actionPerformed(ActionEvent(area, ActionEvent.ACTION_PERFORMED, "\n"))
                area.document.insertString(area.document.length, "Second line", null)
                savedBeforeSave = saved
                text = area.text
                find<AbstractButton>(dialog.contentPane, "test-comment-save").doClick()
                dialog.dispose()
            } finally {
                frame.dispose()
            }
        }
        assertNull(savedBeforeSave, "Enter saved the dialog")
        assertEquals("First line\nSecond line", text)
        assertEquals("First line\nSecond line", saved?.text)
    }

    /** The Rotation slider starts at the angle of the comment. Save returns the new angle. */
    @Test
    fun editKeepsTheRotationSliderValue() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        var saved: CommentInput? = null
        var start = 0
        EventQueue.invokeAndWait {
            val frame = JFrame()
            try {
                val initial = CommentInput(0L, 2_000L, "Turned", "#FFFFFF", CommentStyle.CARD, 100, 100, rotationDeg = -20)
                val dialog = CommentDialog.buildEdit(frame, "test", 1, initial) { saved = it }
                val rotation = find<JSlider>(dialog.contentPane, "test-comment-rotation")
                start = rotation.value
                rotation.value = 45
                find<AbstractButton>(dialog.contentPane, "test-comment-save").doClick()
                dialog.dispose()
            } finally {
                frame.dispose()
            }
        }
        assertEquals(-20, start)
        assertEquals(45, saved?.rotationDeg)
    }

    /** A new comment starts with the size and the opacity that the panel gives. Save returns the slider values. */
    @Test
    fun createStartsWithTheGivenSizeAndOpacity() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        var saved: CommentInput? = null
        var startSize = 0
        var startOpacity = 0
        EventQueue.invokeAndWait {
            val frame = JFrame()
            try {
                val dialog = CommentDialog.buildCreate(
                    frame, "test", 1_000L, "#FFFFFF", CommentStyle.CARD, defaultSizePercent = 140, defaultOpacityPercent = 60,
                ) { saved = it }
                val size = find<JSlider>(dialog.contentPane, "test-comment-size")
                val opacity = find<JSlider>(dialog.contentPane, "test-comment-opacity")
                startSize = size.value
                startOpacity = opacity.value
                opacity.value = 90
                find<JTextArea>(dialog.contentPane, "test-comment-text").text = "Big"
                find<AbstractButton>(dialog.contentPane, "test-comment-save").doClick()
                dialog.dispose()
            } finally {
                frame.dispose()
            }
        }
        assertEquals(140, startSize)
        assertEquals(60, startOpacity)
        assertEquals(140, saved?.sizePercent)
        assertEquals(90, saved?.opacityPercent)
    }

    /** Each change goes to the preview at once. Cancel stops the preview and does not save. */
    @Test
    fun changesPreviewAtOnceAndCancelDiscardsThem() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val previews = mutableListOf<CommentInput?>()
        var saved: CommentInput? = null
        EventQueue.invokeAndWait {
            val frame = JFrame()
            try {
                val initial = CommentInput(2_000L, 4_000L, "Old", "#FFFFFF", CommentStyle.OUTLINE, 100, 100)
                val dialog = CommentDialog.buildEdit(frame, "test", 3, initial, onPreview = { previews += it }) { saved = it }
                find<JTextArea>(dialog.contentPane, "test-comment-text").text = "New"
                find<JSlider>(dialog.contentPane, "test-comment-size").value = 150
                find<JSlider>(dialog.contentPane, "test-comment-opacity").value = 40
                find<AbstractButton>(dialog.contentPane, "test-comment-cancel").doClick()
            } finally {
                frame.dispose()
            }
        }
        // The close event comes later in the event queue.
        EventQueue.invokeAndWait {}
        val last = previews.dropLast(1).last()
        assertEquals("New", last?.text)
        assertEquals(150, last?.sizePercent)
        assertEquals(40, last?.opacityPercent)
        assertEquals(2_000L, last?.startMs)
        assertNull(previews.last(), "Cancel did not stop the preview")
        assertNull(saved, "Cancel saved the comment")
    }

    /** Save keeps the changed values and then stops the preview. */
    @Test
    fun saveKeepsTheChangesAndStopsThePreview() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val previews = mutableListOf<CommentInput?>()
        var saved: CommentInput? = null
        EventQueue.invokeAndWait {
            val frame = JFrame()
            try {
                val dialog = CommentDialog.buildCreate(frame, "test", 1_000L, "#FFFFFF", CommentStyle.OUTLINE, onPreview = { previews += it }) {
                    saved = it
                }
                find<JTextArea>(dialog.contentPane, "test-comment-text").text = "Hello"
                find<AbstractButton>(dialog.contentPane, "test-comment-save").doClick()
            } finally {
                frame.dispose()
            }
        }
        EventQueue.invokeAndWait {}
        assertEquals("Hello", previews.first()?.text)
        assertNull(previews.last(), "Save did not stop the preview")
        assertEquals(1, previews.count { it == null }, "The preview stopped more than one time")
        assertEquals("Hello", saved?.text)
    }

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
