package org.litvin.ui.feedback.presenter

import org.litvin.feedback.FeedbackFailure
import org.litvin.feedback.FeedbackPreferences
import org.litvin.feedback.FeedbackReport
import org.litvin.feedback.FeedbackSendResult
import org.litvin.feedback.FeedbackSender
import org.litvin.feedback.FeedbackSystemInfo
import org.litvin.feedback.FeedbackTopic
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T2 and T3 of B-8, without Swing. */
class DefaultFeedbackPresenterTest {
    private val node: Preferences = Preferences.userRoot().node("bananashot-test/feedback-${UUID.randomUUID()}")
    private val preferences = FeedbackPreferences(node)
    private val sent = mutableListOf<FeedbackReport>()
    private val results = ArrayDeque<FeedbackSendResult>()
    private val sender = FeedbackSender { report -> sent += report; results.removeFirstOrNull() ?: FeedbackSendResult.Sent }
    private val queued = ArrayDeque<Runnable>()
    private val clipboard = mutableListOf<String>()
    private val mails = mutableListOf<URI>()
    private var mailOpens = true
    private var logReads = 0
    private var ids = 0
    private val view = RecordingView()

    @AfterTest
    fun removePreferences() {
        node.removeNode()
    }

    private fun presenter(withSender: Boolean = true) = DefaultFeedbackPresenter(
        sender = if (withSender) sender else null,
        readLog = { logReads++; "log line $logReads\n" },
        preferences = preferences,
        system = FeedbackSystemInfo("1.0.0", "Windows 11", "10.0", "21.0.4"),
        contactEmail = "author@example.test",
        copyToClipboard = { clipboard += it },
        openMail = { mails += it; mailOpens },
        background = { queued += it },
        ui = { it.run() },
        newReportId = { UUID.fromString("00000000-0000-4000-8000-%012d".format(++ids)) },
    ).also { it.attach(view) }

    private fun runBackground() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }

    private fun DefaultFeedbackPresenter.fill(topic: FeedbackTopic = FeedbackTopic.IDEA, message: String = "My idea", email: String = "") {
        onIntent(FeedbackIntent.SelectTopic(topic))
        onIntent(FeedbackIntent.EditMessage(message))
        onIntent(FeedbackIntent.EditEmail(email))
    }

    private class RecordingView : FeedbackView {
        val states = mutableListOf<FeedbackViewState>()
        val effects = mutableListOf<FeedbackViewEffect>()
        override fun render(state: FeedbackViewState) { states += state }
        override fun renderEffect(effect: FeedbackViewEffect) { effects += effect }
    }

    @Test
    fun `Send is not available without a topic or a message`() {
        val presenter = presenter()
        assertFalse(presenter.state.sendEnabled)
        presenter.onIntent(FeedbackIntent.EditMessage("Text"))
        assertFalse(presenter.state.sendEnabled)
        presenter.onIntent(FeedbackIntent.SelectTopic(FeedbackTopic.OTHER))
        assertTrue(presenter.state.sendEnabled)
        presenter.onIntent(FeedbackIntent.EditMessage("  \n "))
        assertFalse(presenter.state.sendEnabled)
        presenter.onIntent(FeedbackIntent.Send)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `the form shows the data that the app adds`() {
        assertEquals("1.0.0 · Windows 11 10.0 · Java 21.0.4", presenter().state.systemSummary)
    }

    @Test
    fun `a wrong email address or a long message stops the send`() {
        val presenter = presenter()
        presenter.fill(email = "name@")
        assertTrue(presenter.state.emailError != null)
        assertFalse(presenter.state.sendEnabled)
        presenter.onIntent(FeedbackIntent.EditEmail(""))
        assertTrue(presenter.state.sendEnabled)
        presenter.onIntent(FeedbackIntent.EditMessage("x".repeat(10_001)))
        assertTrue(presenter.state.messageError != null)
        assertFalse(presenter.state.sendEnabled)
    }

    @Test
    fun `the send runs in the background and then tells thank you and the report ID`() {
        val presenter = presenter()
        presenter.attach(view)
        presenter.fill(email = "user@example.test")
        presenter.onIntent(FeedbackIntent.Send)

        assertEquals(FeedbackPhase.SENDING, presenter.state.phase)
        assertFalse(presenter.state.inputsEnabled)
        assertTrue(sent.isEmpty())

        runBackground()
        val text = (view.effects.single() as FeedbackViewEffect.Sent).text
        assertEquals(
            "Thank you. The author has your report. Report ID: 00000000-0000-4000-8000-000000000001. " +
                "The author can reply to user@example.test.",
            text,
        )
        val report = sent.single()
        assertEquals(FeedbackTopic.IDEA, report.topic)
        assertEquals("My idea", report.message)
        assertEquals("user@example.test", report.email)
        assertNull(report.log)
    }

    @Test
    fun `the form remembers the email address and the user can delete it`() {
        presenter().apply { fill(email = " user@example.test "); onIntent(FeedbackIntent.Send) }
        runBackground()
        assertEquals("user@example.test", presenter().state.email)

        presenter().apply { fill(email = ""); onIntent(FeedbackIntent.Send) }
        runBackground()
        assertEquals("", presenter().state.email)
    }

    @Test
    fun `the log goes only when the checkbox is on`() {
        val presenter = presenter()
        presenter.fill()
        presenter.onIntent(FeedbackIntent.SetAttachLog(true))
        presenter.onIntent(FeedbackIntent.Send)
        runBackground()
        assertEquals("log line 1\n", sent.single().log)
    }

    @Test
    fun `Show the data shows the exact log that the send uses`() {
        val presenter = presenter()
        presenter.fill()
        presenter.onIntent(FeedbackIntent.SetAttachLog(true))
        presenter.onIntent(FeedbackIntent.ShowData)
        runBackground()
        val data = assertIs<FeedbackViewEffect.ShowData>(view.effects.single()).text
        assertTrue(data.endsWith("log line 1\n"))

        presenter.onIntent(FeedbackIntent.Send)
        runBackground()
        assertEquals(1, logReads)
        assertEquals(FeedbackReport.dataText(sent.single()), data)
    }

    @Test
    fun `a failure keeps the text and offers try again, copy, and email`() {
        listOf(FeedbackFailure.NO_CONNECTION, FeedbackFailure.SERVER, FeedbackFailure.DISABLED, FeedbackFailure.RATE_LIMITED).forEach { failure ->
            results += FeedbackSendResult.Failed(failure)
            val presenter = presenter()
            presenter.fill(message = "Keep me")
            presenter.onIntent(FeedbackIntent.Send)
            runBackground()

            val state = presenter.state
            assertEquals(FeedbackPhase.FAILED, state.phase, failure.name)
            assertEquals("Keep me", state.message)
            assertTrue(state.showTryAgain && state.showFallbacks && state.statusIsError, failure.name)
            assertTrue("keeps your text" in state.status)
        }
    }

    @Test
    fun `without an email address the thank-you text tells that the author cannot reply`() {
        val presenter = presenter()
        presenter.attach(view)
        presenter.fill()
        presenter.onIntent(FeedbackIntent.Send)
        runBackground()
        val text = (view.effects.single() as FeedbackViewEffect.Sent).text
        assertTrue(text.endsWith("You gave no email address, so the author cannot reply."), text)
    }

    @Test
    fun `Try again sends the same report ID`() {
        results += FeedbackSendResult.Failed(FeedbackFailure.NO_CONNECTION)
        val presenter = presenter()
        presenter.fill()
        presenter.onIntent(FeedbackIntent.SetAttachLog(true))
        presenter.onIntent(FeedbackIntent.Send)
        runBackground()
        presenter.onIntent(FeedbackIntent.Send)
        runBackground()

        assertEquals(2, sent.size)
        assertEquals(sent[0].reportId, sent[1].reportId)
        assertEquals(sent[0].log, sent[1].log)
        assertEquals(FeedbackPhase.EDITING, presenter.state.phase)
        assertEquals("", presenter.state.message)
    }

    @Test
    fun `a refused report offers only copy and email`() {
        results += FeedbackSendResult.Failed(FeedbackFailure.REFUSED)
        val presenter = presenter()
        presenter.fill()
        presenter.onIntent(FeedbackIntent.Send)
        runBackground()
        assertFalse(presenter.state.showTryAgain)
        assertFalse(presenter.state.sendEnabled)
        assertTrue(presenter.state.showFallbacks)
    }

    @Test
    fun `a sender that throws is a failure with no connection`() {
        val presenter = DefaultFeedbackPresenter(
            sender = { error("boom") }, readLog = { null }, preferences = preferences,
            system = FeedbackSystemInfo("1", "w", "1", "21"), contactEmail = "a@b.test",
            copyToClipboard = {}, openMail = { true }, background = { it.run() }, ui = { it.run() },
        )
        presenter.fill()
        presenter.onIntent(FeedbackIntent.Send)
        assertEquals(FeedbackPhase.FAILED, presenter.state.phase)
        assertTrue(presenter.state.showTryAgain)
    }

    @Test
    fun `without an endpoint, copy and email replace Send`() {
        val presenter = presenter(withSender = false)
        presenter.fill()
        assertFalse(presenter.state.canSendOnline)
        assertFalse(presenter.state.sendEnabled)
        assertTrue(presenter.state.showFallbacks)
        assertTrue("cannot send" in presenter.state.status)
        presenter.onIntent(FeedbackIntent.Send)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `Copy report puts the text on the clipboard`() {
        val presenter = presenter(withSender = false)
        presenter.fill(message = "My text")
        presenter.onIntent(FeedbackIntent.Open(FeedbackRequest(FeedbackTopic.PROBLEM, "Export failed\n\ncode 1", attachLog = true)))
        presenter.onIntent(FeedbackIntent.CopyReport)
        val text = clipboard.single()
        assertTrue("Topic: Problem" in text)
        assertTrue("My text" in text)
        assertTrue("Export failed\n\ncode 1" in text)
        assertTrue("1.0.0 · Windows 11 10.0 · Java 21.0.4" in text)
        assertTrue("author@example.test" in presenter.state.status)
    }

    @Test
    fun `Write an email opens a mailto link to the author with the report`() {
        val presenter = presenter(withSender = false)
        presenter.fill(message = "Hello & bye")
        presenter.onIntent(FeedbackIntent.WriteEmail)
        val uri = mails.single().toString()
        assertTrue(uri.startsWith("mailto:author@example.test?subject=BananaShot%3A%20Idea&body="))
        assertTrue("Hello & bye" in URLDecoder.decode(uri.substringAfter("body="), Charsets.UTF_8))
        assertTrue(clipboard.isEmpty())
    }

    @Test
    fun `a long email body is cut, and the full text goes to the clipboard`() {
        val presenter = presenter(withSender = false)
        presenter.fill(message = "x".repeat(5000))
        presenter.onIntent(FeedbackIntent.WriteEmail)
        assertTrue(mails.single().toString().length < 3000)
        assertTrue("x".repeat(5000) in clipboard.single())
    }

    @Test
    fun `the form tells when the email app does not open`() {
        mailOpens = false
        val presenter = presenter(withSender = false)
        presenter.fill()
        presenter.onIntent(FeedbackIntent.WriteEmail)
        assertTrue(presenter.state.statusIsError)
        assertTrue("cannot open your email app" in presenter.state.status)
    }

    @Test
    fun `an error entry point fills in the topic, the error text, and the log checkbox`() {
        val presenter = presenter()
        presenter.onIntent(FeedbackIntent.Open(FeedbackRequest(FeedbackTopic.PROBLEM, "Unexpected error\n\nboom", attachLog = true)))
        assertEquals(FeedbackTopic.PROBLEM, presenter.state.topic)
        assertEquals("Unexpected error\n\nboom", presenter.state.error)
        assertTrue(presenter.state.attachLog)

        presenter.onIntent(FeedbackIntent.SelectTopic(FeedbackTopic.OTHER))
        presenter.onIntent(FeedbackIntent.SetAttachLog(false))
        presenter.onIntent(FeedbackIntent.RemoveError)
        assertEquals(FeedbackTopic.OTHER, presenter.state.topic)
        assertFalse(presenter.state.attachLog)
        assertNull(presenter.state.error)
    }

    @Test
    fun `the sidebar entry point keeps the draft, and the Help entry point sets the topic Question`() {
        val presenter = presenter()
        presenter.fill(topic = FeedbackTopic.IDEA, message = "Draft")
        presenter.onIntent(FeedbackIntent.Open(FeedbackRequest()))
        assertEquals(FeedbackTopic.IDEA, presenter.state.topic)
        assertEquals("Draft", presenter.state.message)

        presenter.onIntent(FeedbackIntent.Open(FeedbackRequest(FeedbackTopic.QUESTION)))
        assertEquals(FeedbackTopic.QUESTION, presenter.state.topic)
        assertEquals("Draft", presenter.state.message)
    }

    @Test
    fun `a sent report clears the form for a new report with a new ID and keeps the email address`() {
        val presenter = presenter()
        presenter.fill(email = "user@example.test")
        presenter.onIntent(FeedbackIntent.SetAttachLog(true))
        presenter.onIntent(FeedbackIntent.Send)
        runBackground()
        presenter.onIntent(FeedbackIntent.Open(FeedbackRequest()))
        assertEquals(FeedbackPhase.EDITING, presenter.state.phase)
        assertEquals(null, presenter.state.topic)
        assertEquals("", presenter.state.message)
        assertFalse(presenter.state.attachLog)
        assertEquals("", presenter.state.status)
        assertEquals("user@example.test", presenter.state.email)

        presenter.fill()
        presenter.onIntent(FeedbackIntent.Send)
        runBackground()
        assertTrue(sent[0].reportId != sent[1].reportId)
    }

    @Test
    fun `an open during a send changes nothing`() {
        val presenter = presenter()
        presenter.fill()
        presenter.onIntent(FeedbackIntent.Send)
        presenter.onIntent(FeedbackIntent.Open(FeedbackRequest(FeedbackTopic.PROBLEM, "error")))
        presenter.onIntent(FeedbackIntent.EditMessage("changed"))
        runBackground()
        assertEquals("My idea", sent.single().message)
        assertNull(presenter.state.error)
    }
}
