package org.litvin.analytics

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsPreferencesTest {
    @Test
    fun `current enabled decision is effective and old decisions require a new choice`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        assertTrue(preferences.resolve().needsChoice)
        assertFalse(preferences.resolve().isEnabled)

        preferences.record(AnalyticsPreferences.Choice.ENABLED)
        assertTrue(preferences.resolve().isEnabled)
        assertFalse(preferences.resolve().needsChoice)

        node.putInt("analytics.noticeVersion", 0)
        assertFalse(preferences.resolve().isEnabled)
        assertTrue(preferences.resolve().needsChoice)
    }

    @Test
    fun `future notice decision fails closed and dismissal records disabled`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        preferences.dismiss()
        assertEquals(AnalyticsPreferences.Choice.DISABLED, preferences.resolve().choice)
        assertFalse(preferences.resolve().needsChoice)

        node.putInt("analytics.noticeVersion", 99)
        assertEquals(AnalyticsPreferences.Choice.DISABLED, preferences.resolve().choice)
        assertTrue(preferences.resolve().needsChoice)
    }

    @Test
    fun `each session start increases the count, and a session that did not end gives an unclean exit`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        assertEquals(AnalyticsPreferences.SessionStart(1, uncleanExit = false), preferences.startSession())
        assertEquals(AnalyticsPreferences.SessionStart(2, uncleanExit = true), preferences.startSession())
        preferences.endSession()
        assertEquals(AnalyticsPreferences.SessionStart(3, uncleanExit = false), preferences.startSession())
    }

    @Test
    fun `stores only the choice, the count, and the flag`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        preferences.record(AnalyticsPreferences.Choice.ENABLED)
        preferences.startSession()
        assertEquals(
            setOf("analytics.choice", "analytics.noticeVersion", "analytics.decidedAt", "analytics.sessionCount", "analytics.sessionOpen"),
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
