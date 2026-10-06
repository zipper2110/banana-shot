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
    fun `start begins an essential session for each user without a choice`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val levels = mutableListOf<AnalyticsLevel>()
        val session = RecordingSession()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, level -> levels += level; session }, FakeHooks())

        controller.record(AnalyticsEvent.ProjectCreated)
        assertTrue(session.events.isEmpty())

        controller.start()
        controller.record(AnalyticsEvent.ProjectCreated)

        assertEquals(listOf(AnalyticsLevel.ESSENTIAL), levels)
        assertEquals(AnalyticsEvent.ProjectCreated, session.events.last())
        assertTrue(preferences.resolve().needsChoice)
    }

    @Test
    fun `start uses the saved extended choice`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        preferences.record(AnalyticsPreferences.Choice.EXTENDED)
        val levels = mutableListOf<AnalyticsLevel>()
        AnalyticsController(enabledConfig(), preferences, { _, _, level -> levels += level; RecordingSession() }, FakeHooks()).start()
        assertEquals(listOf(AnalyticsLevel.EXTENDED), levels)
    }

    @Test
    fun `choose saves the choice and changes the level of the running session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val session = RecordingSession()
        var created = 0
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, _ -> created++; session }, FakeHooks())
        controller.start()

        controller.choose(AnalyticsPreferences.Choice.EXTENDED)
        assertEquals(AnalyticsLevel.EXTENDED, preferences.resolve().level)
        assertFalse(preferences.resolve().needsChoice)

        controller.choose(AnalyticsPreferences.Choice.ESSENTIAL)
        assertEquals(AnalyticsLevel.ESSENTIAL, preferences.resolve().level)
        assertFalse(preferences.resolve().needsChoice)

        assertEquals(listOf(AnalyticsLevel.EXTENDED, AnalyticsLevel.ESSENTIAL), session.levels)
        assertEquals(1, created)
        assertFalse(session.closed)
    }

    @Test
    fun `off stops the session without a send, ends it, and removes the shutdown hook`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val session = RecordingSession()
        val hooks = FakeHooks()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, _ -> session }, hooks)
        controller.start()
        assertEquals(1, hooks.actions.size)

        controller.choose(AnalyticsPreferences.Choice.OFF)
        controller.record(AnalyticsEvent.PointAdded)

        assertTrue(session.closed)
        assertFalse(session.finalSendStarted)
        assertFalse(AnalyticsEvent.PointAdded in session.events)
        assertTrue(hooks.actions.isEmpty())
        assertNull(preferences.resolve().level)
        assertFalse(preferences.startSession().uncleanExit)
    }

    @Test
    fun `start sends nothing after the choice off, and a later choice starts a new session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        preferences.record(AnalyticsPreferences.Choice.OFF)
        val levels = mutableListOf<AnalyticsLevel>()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, level -> levels += level; RecordingSession() }, FakeHooks())

        controller.start()
        assertTrue(levels.isEmpty())
        assertNull(node.get("analytics.sessionCount", null))

        controller.choose(AnalyticsPreferences.Choice.ESSENTIAL)
        assertEquals(listOf(AnalyticsLevel.ESSENTIAL), levels)
    }

    @Test
    fun `close at app shutdown stops delivery and keeps the choice`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val session = RecordingSession()
        var created = 0
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, _ -> created++; session }, FakeHooks())
        controller.start()
        controller.choose(AnalyticsPreferences.Choice.EXTENDED)

        controller.close()
        controller.record(AnalyticsEvent.ProjectOpened)
        controller.choose(AnalyticsPreferences.Choice.EXTENDED)

        assertTrue(session.closed)
        assertFalse(AnalyticsEvent.ProjectOpened in session.events)
        assertEquals(1, created, "no new session after close")
        assertEquals(AnalyticsLevel.EXTENDED, preferences.resolve().level)
    }

    @Test
    fun `beginFinalSend goes to the running session`() = withPreferences { node ->
        val session = RecordingSession()
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, _, _ -> session }, FakeHooks())
        controller.beginFinalSend()
        assertFalse(session.finalSendStarted)
        controller.start()
        controller.beginFinalSend()
        assertTrue(session.finalSendStarted)
    }

    @Test
    fun `each session start counts the session and the open flag gives unclean_exit`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val starts = mutableListOf<AnalyticsPreferences.SessionStart>()
        val hooks = FakeHooks()
        fun controller() = AnalyticsController(enabledConfig(), preferences, { _, start, _ -> starts += start; RecordingSession() }, hooks)

        controller().apply { start(); close() }
        controller().apply { start(); close() }
        hooks.runAll()
        controller().apply { start(); close() }

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
    fun `a session that starts late gets the current tab and window state without a tab open`() = withPreferences { node ->
        val session = RecordingSession()
        var fail = true
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, _, _ ->
            if (fail) error("no session") else session
        }, FakeHooks())
        controller.start()
        controller.record(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.STATS, byUser = true))
        controller.record(AnalyticsEvent.WindowActive(true))

        fail = false
        controller.choose(AnalyticsPreferences.Choice.EXTENDED)

        assertEquals(
            listOf(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.STATS, byUser = false), AnalyticsEvent.WindowActive(true)),
            session.events,
        )
    }

    @Test
    fun `a session that cannot start leaves no open flag and no shutdown hook`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val hooks = FakeHooks()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, _ -> error("no session") }, hooks)

        controller.start()
        controller.record(AnalyticsEvent.PointAdded)

        assertTrue(hooks.actions.isEmpty())
        assertFalse(preferences.startSession().uncleanExit)
    }

    @Test
    fun `a disabled build never starts a session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        var created = false
        val controller = AnalyticsController(AnalyticsBuildConfig.Disabled("test"), preferences, { _, _, _ -> created = true; RecordingSession() }, FakeHooks())
        controller.start()
        controller.choose(AnalyticsPreferences.Choice.EXTENDED)
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
        val levels = mutableListOf<AnalyticsLevel>()
        var closed = false
        var finalSendStarted = false
        override fun record(event: AnalyticsEvent) { events += event }
        override fun beginFinalSend() { finalSendStarted = true }
        override fun setLevel(level: AnalyticsLevel) { levels += level }
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
