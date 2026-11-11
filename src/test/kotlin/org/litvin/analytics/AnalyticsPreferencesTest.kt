package org.litvin.analytics

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsPreferencesTest {
    @Test
    fun `no choice is essential, and a choice for another notice version needs a new choice`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        assertTrue(preferences.resolve().needsChoice)
        assertEquals(AnalyticsLevel.ESSENTIAL, preferences.resolve().level)

        preferences.record(AnalyticsPreferences.Choice.EXTENDED)
        assertEquals(AnalyticsPreferences.Choice.EXTENDED, preferences.resolve().choice)
        assertEquals(AnalyticsLevel.EXTENDED, preferences.resolve().level)
        assertFalse(preferences.resolve().needsChoice)

        listOf(0, 99).forEach { version ->
            node.putInt("analytics.noticeVersion", version)
            assertEquals(AnalyticsPreferences.Choice.UNDECIDED, preferences.resolve().choice)
            assertEquals(AnalyticsLevel.ESSENTIAL, preferences.resolve().level)
            assertTrue(preferences.resolve().needsChoice)
        }
    }

    @Test
    fun `the essential and the off choices need no new question, and an unknown stored value is undecided`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        preferences.record(AnalyticsPreferences.Choice.ESSENTIAL)
        assertEquals(AnalyticsPreferences.Choice.ESSENTIAL, preferences.resolve().choice)
        assertFalse(preferences.resolve().needsChoice)

        preferences.record(AnalyticsPreferences.Choice.OFF)
        assertEquals(AnalyticsPreferences.Choice.OFF, preferences.resolve().choice)
        assertEquals(null, preferences.resolve().level)
        assertFalse(preferences.resolve().needsChoice)

        node.put("analytics.choice", "ENABLED")
        assertEquals(AnalyticsPreferences.Choice.UNDECIDED, preferences.resolve().choice)
        assertEquals(AnalyticsLevel.ESSENTIAL, preferences.resolve().level)
        assertTrue(preferences.resolve().needsChoice)
    }

    @Test
    fun `each session start increases the count and keeps the crash of the previous app session`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val crash = AnalyticsCrash(AnalyticsCrash.Kind.NATIVE, AnalyticsCrash.Library.MPV, emptySet())
        assertEquals(AnalyticsPreferences.SessionStart(1), preferences.startSession())
        assertEquals(AnalyticsPreferences.SessionStart(2, crash), preferences.startSession(crash))
        assertEquals(AnalyticsPreferences.SessionStart(3), preferences.startSession())
    }

    @Test
    fun `stores only the choice and the count, and removes the old open-session flag`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        node.putBoolean("analytics.sessionOpen", true)
        preferences.record(AnalyticsPreferences.Choice.EXTENDED)
        preferences.startSession()
        assertEquals(
            setOf("analytics.choice", "analytics.noticeVersion", "analytics.decidedAt", "analytics.sessionCount"),
            node.keys().toSet(),
        )
    }

    private fun withPreferences(block: (Preferences) -> Unit) {
        val node = Preferences.userRoot().node("/org/litvin/test/analytics/${UUID.randomUUID()}")
        try {
            block(node)
        } finally {
            node.removeNode()
        }
    }
}
