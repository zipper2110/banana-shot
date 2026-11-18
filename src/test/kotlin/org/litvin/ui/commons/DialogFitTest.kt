package org.litvin.ui.commons

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.litvin.points.CommentStyle
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.io.File
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A dialog must fit in the screen height. When the dialog is taller than the screen, the user cannot click the
 * buttons in the foot, for example Save in "Edit comment" with the Size, Opacity, and Rotation sliders.
 * [DialogKit.content] keeps all dialogs that use it in the screen height. The body scrolls, and the foot stays visible.
 */
class DialogFitTest {
    @Test
    fun `a content that is taller than the limit gets the limit, and the foot stays in the content`() {
        val footer = fixedHeight(50)
        val content = DialogKit.content(DialogKit.SMALL, fixedHeight(40), fixedHeight(2000), footer) as DialogContent
        content.heightLimit = { 400 }

        assertEquals(400, content.preferredSize.height)
        content.size = content.preferredSize
        content.doLayout()
        assertEquals(400, footer.y + footer.height)
        val body = content.components.single { it is JScrollPane }
        assertEquals(400 - 40 - 50, body.height)
    }

    @Test
    fun `a content that fits keeps its natural height`() {
        val content = DialogKit.content(DialogKit.SMALL, fixedHeight(40), fixedHeight(200), fixedHeight(50)) as DialogContent
        content.heightLimit = { 400 }
        assertEquals(290, content.preferredSize.height)
    }

    @Test
    fun `a body that is a scroll pane already does not get a second scroll pane`() {
        val body = DialogKit.scrollBody(fixedHeight(200))
        val content = DialogKit.content(DialogKit.SMALL, null, body, null)
        assertTrue(content.components.single() === body)
    }

    @Test
    fun `the content height limit is the screen area without the frame and the margin`() {
        // A MacBook Air: 900 px screen height, the menu bar and the Dock take 25 + 70, the title bar takes 28.
        assertEquals(900 - 25 - 70 - 28 - DialogKit.SCREEN_MARGIN, DialogKit.fittedContentHeight(900 - 25 - 70, 28))
        assertEquals(DialogKit.MIN_CONTENT_HEIGHT, DialogKit.fittedContentHeight(200, 28))
    }

    /** The comment dialog with all its sliders: on a low screen, the Save button stays in the dialog. */
    @Test
    fun `the Save button of the comment dialog stays visible on a low screen`() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        EventQueue.invokeAndWait {
            val frame = JFrame()
            try {
                val dialog = CommentDialog.buildEdit(
                    frame, "test", 3, CommentInput(1_000L, 5_000L, "Text", "#FFFFFF", CommentStyle.BUBBLE),
                ) {}
                val content = dialog.contentPane as DialogContent
                val natural = content.preferredSize.height
                content.heightLimit = { 420 }
                dialog.pack()
                content.validate()
                val save = find(content, "test-comment-save")
                val bottom = SwingUtilities.convertPoint(save.parent, save.x, save.y + save.height, content).y
                assertTrue(natural > 420, "The test needs a dialog that is taller than the limit, but it is $natural")
                assertEquals(420, content.height)
                assertTrue(bottom <= content.height, "Save ends at $bottom, below the content height ${content.height}")
                dialog.dispose()
            } finally {
                frame.dispose()
            }
        }
    }

    /**
     * Each new dialog must use [DialogKit.content] (also through [MessageDialog.content]), so that it fits in the
     * screen height. The dialogs in [OWN_SIZE] set their size in their own code.
     */
    @Test
    fun `each dialog uses the dialog content that fits in the screen height`() {
        val makesDialog = Regex("""JDialog\(|DialogKit\.modal\(""")
        val usesContent = Regex("""DialogKit\.content\(|MessageDialog\.content\(""")
        val problems = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in OWN_SIZE }
            .filter { makesDialog.containsMatchIn(it.readText(Charsets.UTF_8)) }
            .filterNot { usesContent.containsMatchIn(it.readText(Charsets.UTF_8)) }
            .map { it.name }
            .toList()
        assertEquals(emptyList(), problems, "Use DialogKit.content for the dialog, or add the file to OWN_SIZE with the reason")
    }

    private fun fixedHeight(height: Int): JComponent = JPanel().apply { preferredSize = Dimension(100, height) }

    private fun find(root: Container, name: String): Component =
        root.components.firstNotNullOfOrNull { child ->
            if (child.name == name) child else (child as? Container)?.let { runCatching { find(it, name) }.getOrNull() }
        } ?: error("No component $name")

    private companion object {
        /** The files that make a dialog without [DialogKit.content], and the reasons. */
        val OWN_SIZE = mapOf(
            "DialogKit.kt" to "it makes the dialogs",
            "OverrideScoreDialog.kt" to "DialogKit.packToScreen",
            "ScoreboardSettingsDialog.kt" to "fitToScreen gives a size in the screen area",
            "HelpDialog.kt" to "a fixed size of 900 x 650; the user can make it smaller",
            "MoreDialog.kt" to "a fixed size of 820 x 560; the user can make it smaller",
            "ExportPreviewWindow.kt" to "80% of the usable screen area, in the center of the screen of the owner",
        ).keys
    }
}
