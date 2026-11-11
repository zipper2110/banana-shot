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
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, level -> levels += level; session })

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
        AnalyticsController(enabledConfig(), preferences, { _, _, level -> levels += level; RecordingSession() }).start()
        assertEquals(listOf(AnalyticsLevel.EXTENDED), levels)
    }

    @Test
    fun `choose saves the choice and changes the level of the running session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val session = RecordingSession()
        var created = 0
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, _ -> created++; session })
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
    fun `off stops the session without a send`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val session = RecordingSession()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, _ -> session })
        controller.start()

        controller.choose(AnalyticsPreferences.Choice.OFF)
        controller.record(AnalyticsEvent.PointAdded)

        assertTrue(session.closed)
        assertFalse(session.finalSendStarted)
        assertFalse(AnalyticsEvent.PointAdded in session.events)
        assertNull(preferences.resolve().level)
    }

    @Test
    fun `start sends nothing after the choice off, and a later choice starts a new session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        preferences.record(AnalyticsPreferences.Choice.OFF)
        val levels = mutableListOf<AnalyticsLevel>()
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, level -> levels += level; RecordingSession() })

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
        val controller = AnalyticsController(enabledConfig(), preferences, { _, _, _ -> created++; session })
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
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, _, _ -> session })
        controller.beginFinalSend()
        assertFalse(session.finalSendStarted)
        controller.start()
        controller.beginFinalSend()
        assertTrue(session.finalSendStarted)
    }

    @Test
    fun `each session start counts the session and gets the crash of the previous app session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val starts = mutableListOf<AnalyticsPreferences.SessionStart>()
        fun controller() = AnalyticsController(enabledConfig(), preferences, { _, start, _ -> starts += start; RecordingSession() })
        val crash = AnalyticsCrash(AnalyticsCrash.Kind.NATIVE, AnalyticsCrash.Library.COREVIDEO, emptySet())

        controller().apply { start(); close() }
        controller().apply { start(crash); close() }
        controller().apply { start(); close() }

        assertEquals(
            listOf(
                AnalyticsPreferences.SessionStart(1),
                AnalyticsPreferences.SessionStart(2, crash),
                AnalyticsPreferences.SessionStart(3),
            ),
            starts,
        )
        assertEquals(listOf(false, true, false), starts.map { it.uncleanExit })
    }

    @Test
    fun `only the first session of the process counts the crash`() = withPreferences { node ->
        val starts = mutableListOf<AnalyticsPreferences.SessionStart>()
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, start, _ -> starts += start; RecordingSession() })
        val crash = AnalyticsCrash(AnalyticsCrash.Kind.FREEZE, null, setOf(AnalyticsCrash.Activity.EXPORT))

        controller.start(crash)
        controller.choose(AnalyticsPreferences.Choice.OFF)
        controller.choose(AnalyticsPreferences.Choice.ESSENTIAL)

        assertEquals(listOf(crash, null), starts.map { it.crash })
    }

    @Test
    fun `a crash stays for a later session when the first start fails`() = withPreferences { node ->
        val starts = mutableListOf<AnalyticsPreferences.SessionStart>()
        var fail = true
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, start, _ ->
            if (fail) error("no session")
            starts += start
            RecordingSession()
        })
        val crash = AnalyticsCrash(AnalyticsCrash.Kind.UNKNOWN, null, emptySet())

        controller.start(crash)
        fail = false
        controller.choose(AnalyticsPreferences.Choice.ESSENTIAL)

        assertEquals(listOf(crash), starts.map { it.crash })
    }

    @Test
    fun `a session that starts late gets the current tab and window state without a tab open`() = withPreferences { node ->
        val session = RecordingSession()
        var fail = true
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, _, _ ->
            if (fail) error("no session") else session
        })
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
    fun `a new session after off gets the last settings, but not the sports`() = withPreferences { node ->
        val sessions = mutableListOf<RecordingSession>()
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, _, _ ->
            RecordingSession().also { sessions += it }
        })
        val settings = AnalyticsEvent.Settings(AnalyticsEvent.Theme.LIGHT, AnalyticsEvent.Accent.CUSTOM, AnalyticsEvent.Language.EN)
        controller.start()
        controller.record(settings)
        controller.record(AnalyticsEvent.SportUsed(AnalyticsEvent.Sport.PADEL))

        controller.choose(AnalyticsPreferences.Choice.OFF)
        controller.choose(AnalyticsPreferences.Choice.EXTENDED)

        assertEquals(2, sessions.size)
        assertTrue(settings in sessions[1].events)
        assertFalse(sessions[1].events.any { it is AnalyticsEvent.SportUsed })
    }

    @Test
    fun `a session that cannot start records nothing`() = withPreferences { node ->
        val controller = AnalyticsController(enabledConfig(), AnalyticsPreferences(node), { _, _, _ -> error("no session") })

        controller.start()
        controller.record(AnalyticsEvent.PointAdded)
    }

    @Test
    fun `a disabled build never starts a session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        var created = false
        val controller = AnalyticsController(AnalyticsBuildConfig.Disabled("test"), preferences, { _, _, _ -> created = true; RecordingSession() })
        controller.start()
        controller.choose(AnalyticsPreferences.Choice.EXTENDED)
        assertFalse(created)
        assertNull(node.get("analytics.sessionCount", null))
    }

    private fun enabledConfig(): AnalyticsBuildConfig.Enabled = AnalyticsBuildConfig.fromProperties(Properties().apply {
        setProperty(AnalyticsBuildConfig.ENDPOINT_PROPERTY, "https://analytics.example.test/v1/session")
        setProperty(AnalyticsBuildConfig.PRIVACY_URL_PROPERTY, "https://tennis.example.test/privacy/analytics/")
        setProperty(AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY, AnalyticsSchema.NOTICE_VERSION.toString())
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
}
