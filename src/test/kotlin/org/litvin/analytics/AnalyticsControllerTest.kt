package org.litvin.analytics

import java.util.Properties
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsControllerTest {
    @Test
    fun `does not create delivery until enabled and clears it immediately when disabled`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val captured = mutableListOf<AnalyticsEvent>()
        val controller = AnalyticsController(
            enabledConfig(),
            preferences,
            enabledFactory = { RecordingEnabledAnalytics(captured) }
        )

        controller.record(AnalyticsEvent.ProjectCreated)
        assertTrue(captured.isEmpty())

        controller.enable()
        controller.record(AnalyticsEvent.ProjectCreated)
        assertEquals(listOf(AnalyticsEvent.SessionStarted, AnalyticsEvent.ProjectCreated), captured)
        assertTrue(preferences.resolve().isEnabled)

        controller.disable()
        controller.record(AnalyticsEvent.ProjectOpened)
        assertFalse(preferences.resolve().isEnabled)
        assertEquals(listOf(AnalyticsEvent.SessionStarted, AnalyticsEvent.ProjectCreated), captured)
    }

    @Test
    fun `close at app shutdown stops delivery and keeps the consent choice`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val captured = mutableListOf<AnalyticsEvent>()
        var closed = false
        val controller = AnalyticsController(
            enabledConfig(),
            preferences,
            enabledFactory = { RecordingEnabledAnalytics(captured) { closed = true } }
        )
        controller.enable()

        controller.close()
        controller.record(AnalyticsEvent.ProjectOpened)

        assertTrue(closed)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.SessionStarted), captured)
        assertTrue(preferences.resolve().isEnabled)
        assertFalse(preferences.resolve().needsChoice)
    }

    private fun enabledConfig(): AnalyticsBuildConfig.Enabled = AnalyticsBuildConfig.fromProperties(Properties().apply {
        setProperty(AnalyticsBuildConfig.ENDPOINT_PROPERTY, "https://analytics.example.test/v1/events/batch")
        setProperty(AnalyticsBuildConfig.PRIVACY_URL_PROPERTY, "https://tennis.example.test/privacy/analytics/")
        setProperty(AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY, "1")
    }) as AnalyticsBuildConfig.Enabled

    private fun withPreferences(block: (Preferences) -> Unit) {
        val node = Preferences.userRoot().node("/org/litvin/test/analytics/${UUID.randomUUID()}")
        try { block(node) } finally { node.removeNode() }
    }

    private class RecordingEnabledAnalytics(
        private val events: MutableList<AnalyticsEvent>,
        private val onClose: () -> Unit = {},
    ) : ManagedAnalytics {
        override fun record(event: AnalyticsEvent) { events += event }
        override fun close() = onClose()
    }
}
