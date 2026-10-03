package org.litvin.ui.feedback

import org.litvin.feedback.FeedbackPreferences
import org.litvin.feedback.FeedbackSendResult
import org.litvin.feedback.FeedbackSender
import org.litvin.feedback.FeedbackSystemInfo
import org.litvin.feedback.FeedbackTopic
import org.litvin.ui.feedback.presenter.DefaultFeedbackPresenter
import org.litvin.ui.feedback.presenter.FeedbackIntent
import org.litvin.ui.feedback.presenter.FeedbackPhase
import org.litvin.ui.feedback.presenter.FeedbackRequest
import java.awt.Component
import java.awt.Container
import java.util.UUID
import java.util.prefs.Preferences
import javax.swing.AbstractButton
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Swing view of the form forwards the input and shows the state of the presenter (T2 of B-8). */
class FeedbackPanelTest {
    private val node: Preferences = Preferences.userRoot().node("bananashot-test/feedback-panel-${UUID.randomUUID()}")
    private var closes = 0

    @AfterTest
    fun removePreferences() {
        node.removeNode()
    }

    private fun presenter(withSender: Boolean) = DefaultFeedbackPresenter(
        sender = if (withSender) FeedbackSender { FeedbackSendResult.Sent } else null,
        readLog = { null },
        preferences = FeedbackPreferences(node),
        system = FeedbackSystemInfo("1.0.0", "Windows 11", "10.0", "21.0.4"),
        contactEmail = "author@example.test",
        copyToClipboard = {},
        openMail = { true },
        background = { it.run() },
        ui = { it.run() },
    )

    private fun panel(presenter: DefaultFeedbackPresenter) =
        FeedbackPanel(presenter, onClose = { closes++ }).also { presenter.attach(it) }

    private fun <T : Component> find(root: Component, name: String, type: Class<T>): T =
        all(root).filter { it.name == name }.filterIsInstance(type).single()

    private fun all(root: Component): List<Component> = buildList {
        add(root)
        if (root is Container) root.components.forEach { addAll(all(it)) }
    }

    /** True when the component and all its parents up to [root] are visible. */
    private fun shown(component: Component, root: Component): Boolean =
        generateSequence(component) { if (it === root) null else it.parent }.all { it.isVisible }

    @Test
    fun `the user writes, sends, and sees thank you`() {
        System.setProperty("java.awt.headless", "true")
        val presenter = presenter(withSender = true)
        SwingUtilities.invokeAndWait {
            val panel = panel(presenter)
            val send = find(panel, "feedback-send", AbstractButton::class.java)
            assertFalse(send.isEnabled)
            find(panel, "feedback-topic-1", AbstractButton::class.java).doClick()
            find(panel, "feedback-message", JTextArea::class.java).text = "An idea"
            find(panel, "feedback-email", JTextField::class.java).text = "user@example.test"
            assertTrue(send.isEnabled)
            assertFalse(find(panel, "feedback-copy", AbstractButton::class.java).isVisible)

            send.doClick()
            assertEquals(FeedbackPhase.SENT, presenter.state.phase)
            assertEquals(FeedbackTopic.IDEA, presenter.state.topic)
            assertEquals("An idea", presenter.state.message)
            assertFalse(send.isVisible)
            assertTrue(find(panel, "feedback-new", AbstractButton::class.java).isVisible)
            assertFalse(find(panel, "feedback-message", JTextArea::class.java).isEnabled)
        }
    }

    @Test
    fun `without an endpoint the form shows copy and email in place of Send`() {
        System.setProperty("java.awt.headless", "true")
        SwingUtilities.invokeAndWait {
            val panel = panel(presenter(withSender = false))
            assertFalse(find(panel, "feedback-send", AbstractButton::class.java).isVisible)
            assertTrue(find(panel, "feedback-copy", AbstractButton::class.java).isVisible)
            assertTrue(find(panel, "feedback-write-email", AbstractButton::class.java).isVisible)
        }
    }

    @Test
    fun `an error request shows the error text and the user can remove it`() {
        System.setProperty("java.awt.headless", "true")
        val presenter = presenter(withSender = true)
        SwingUtilities.invokeAndWait {
            presenter.onIntent(FeedbackIntent.Open(FeedbackRequest(FeedbackTopic.PROBLEM, "Export failed", attachLog = true)))
            val panel = panel(presenter)
            val error = find(panel, "feedback-error", JTextArea::class.java)
            assertEquals("Export failed", error.text)
            assertTrue(shown(error, panel))
            assertTrue(find(panel, "feedback-attach-log", AbstractButton::class.java).isSelected)

            find(panel, "feedback-remove-error", AbstractButton::class.java).doClick()
            assertNull(presenter.state.error)
            assertFalse(shown(error, panel))
        }
    }

    @Test
    fun `the form shows the data that the app adds and the reply address`() {
        System.setProperty("java.awt.headless", "true")
        SwingUtilities.invokeAndWait {
            val panel = panel(presenter(withSender = true))
            val texts = all(panel).filterIsInstance<org.litvin.ui.commons.WrapText>().joinToString("\n") { it.text }
            assertTrue("1.0.0 · Windows 11 10.0 · Java 21.0.4" in texts)
            assertTrue("The author can reply only if you give an address. The reply comes from author@example.test." in texts)
            assertTrue("They do not contain your videos" in texts)
        }
    }

    @Test
    fun `Close calls the close action`() {
        System.setProperty("java.awt.headless", "true")
        SwingUtilities.invokeAndWait {
            find(panel(presenter(withSender = true)), "feedback-close", AbstractButton::class.java).doClick()
        }
        assertEquals(1, closes)
    }
}
