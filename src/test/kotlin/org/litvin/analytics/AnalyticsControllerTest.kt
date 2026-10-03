package org.litvin.analytics

import java.util.Properties
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsControllerTest {
    @Test
    fun `does not create delivery until enabled and clears it immediately when disabled`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val session = RecordingSession()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _ -> session }, FakeHooks())

        controller.record(AnalyticsEvent.ProjectCreated)
        assertTrue(session.events.isEmpty())

        controller.enable()
        controller.record(AnalyticsEvent.ProjectCreated)
        assertEquals(AnalyticsEvent.ProjectCreated, session.events.last())
        assertTrue(preferences.resolve().isEnabled)

        controller.disable()
        controller.record(AnalyticsEvent.ProjectOpened)
        assertTrue(session.closed)
        assertFalse(session.finalSendStarted)
        assertFalse(preferences.resolve().isEnabled)
        assertFalse(AnalyticsEvent.ProjectOpened in session.events)
    }

    @Test
    fun `close at app shutdown stops delivery and keeps the consent choice`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val session = RecordingSession()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _ -> session }, FakeHooks())
        controller.enable()

        controller.close()
        controller.record(AnalyticsEvent.ProjectOpened)

        assertTrue(session.closed)
        assertFalse(AnalyticsEvent.ProjectOpened in session.events)
        assertTrue(preferences.resolve().isEnabled)
        assertFalse(preferences.resolve().needsChoice)
    }

    @Test
    fun `beginFinalSend goes to the running session`() = withPreferences { node ->
        val session = RecordingSession()
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, _ -> session }, FakeHooks())
        controller.beginFinalSend()
        controller.enable()
        controller.beginFinalSend()
        assertTrue(session.finalSendStarted)
    }

    @Test
    fun `each session start counts the session and the open flag gives unclean_exit`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val starts = mutableListOf<AnalyticsPreferences.SessionStart>()
        val hooks = FakeHooks()
        fun controller() = AnalyticsController(enabledConfig(), preferences, { _, start -> starts += start; RecordingSession() }, hooks)

        controller().apply { enable(); close() }
        controller().apply { startIfConsented(); close() }
        hooks.runAll()
        controller().apply { startIfConsented(); close() }

        assertEquals(
            listOf(
                AnalyticsPreferences.SessionStart(1, uncleanExit = false),
                AnalyticsPreferences.SessionStart(2, uncleanExit = true),
                AnalyticsPreferences.SessionStart(3, uncleanExit = false),
            ),
            starts,
        )
    }

    @Test
    fun `disable ends the session and removes the shutdown hook`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val hooks = FakeHooks()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _ -> RecordingSession() }, hooks)
        controller.enable()
        assertEquals(1, hooks.actions.size)

        controller.disable()

        assertTrue(hooks.actions.isEmpty())
        assertFalse(preferences.startSession().uncleanExit)
    }

    @Test
    fun `a new session gets the current tab and window state without a tab open`() = withPreferences { node ->
        val session = RecordingSession()
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, _ -> session }, FakeHooks())
        controller.record(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.STATS, byUser = true))
        controller.record(AnalyticsEvent.WindowActive(true))

        controller.enable()

        assertEquals(
            listOf(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.STATS, byUser = false), AnalyticsEvent.WindowActive(true)),
            session.events,
        )
    }

    @Test
    fun `a session that cannot start leaves no open flag and no consent`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val hooks = FakeHooks()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _ -> error("no session") }, hooks)

        controller.enable()
        controller.record(AnalyticsEvent.PointAdded)

        assertTrue(hooks.actions.isEmpty())
        assertTrue(preferences.resolve().needsChoice)
        assertFalse(preferences.startSession().uncleanExit)
    }

    @Test
    fun `a disabled build never starts a session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        var created = false
        val controller = AnalyticsController(AnalyticsBuildConfig.Disabled("test"), preferences, { _, _ -> created = true; RecordingSession() }, FakeHooks())
        controller.enable()
        assertFalse(created)
        assertNull(node.get("analytics.sessionCount", null))
    }

    private fun enabledConfig(): AnalyticsBuildConfig.Enabled = AnalyticsBuildConfig.fromProperties(Properties().apply {
        setProperty(AnalyticsBuildConfig.ENDPOINT_PROPERTY, "https://analytics.example.test/v1/session")
        setProperty(AnalyticsBuildConfig.PRIVACY_URL_PROPERTY, "https://tennis.example.test/privacy/analytics/")
        setProperty(AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY, "1")
    }) as AnalyticsBuildConfig.Enabled

    private fun withPreferences(block: (Preferences) -> Unit) {
        val node = Preferences.userRoot().node("/org/litvin/test/analytics/${UUID.randomUUID()}")
        try { block(node) } finally { node.removeNode() }
    }

    private class RecordingSession : ManagedAnalytics {
        val events = mutableListOf<AnalyticsEvent>()
        var closed = false
        var finalSendStarted = false
        override fun record(event: AnalyticsEvent) { events += event }
        override fun beginFinalSend() { finalSendStarted = true }
        override fun close() { closed = true }
    }

    private class FakeHooks : ShutdownHooks {
        val actions = mutableListOf<() -> Unit>()
        override fun add(action: () -> Unit): AutoCloseable {
            actions += action
            return AutoCloseable { actions -= action }
        }
        fun runAll() = actions.toList().forEach { it() }
    }
}
