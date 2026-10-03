package org.litvin.license

import com.fasterxml.jackson.databind.ObjectMapper
import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.AnalyticsPreferences
import org.litvin.analytics.AnalyticsTransport
import org.litvin.app.productionAnalyticsController
import java.net.URI
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionSourceTest {
    @Test
    fun `app and analytics version are BuildInfo VERSION also with a different system property`() =
        withVersionProperty("99.0.0") {
            assertEquals(BuildInfo.VERSION, AppInfo.version)
            assertEquals(BuildInfo.VERSION, firstAnalyticsSummaryVersion())
        }

    @Test
    fun `display name always contains the version`() {
        assertEquals("${AppInfo.NAME} ${BuildInfo.VERSION}", AppInfo.displayName)
        assertTrue(AppInfo.displayName.contains(BuildInfo.VERSION))
    }

    /** Starts the production analytics controller with a recording transport and reads the first summary. */
    private fun firstAnalyticsSummaryVersion(): String {
        val node = Preferences.userRoot().node("/org/litvin/test/analytics/${UUID.randomUUID()}")
        val bodies = LinkedBlockingQueue<String>()
        val transport = AnalyticsTransport { body -> bodies.put(body); 204 }
        val controller = productionAnalyticsController(enabledConfig(), AnalyticsPreferences(node)) { transport }
        try {
            controller.enable()
            val body = bodies.poll(5, TimeUnit.SECONDS) ?: error("no summary")
            return ObjectMapper().readTree(body)["app_version"].textValue()
        } finally {
            controller.disable()
            node.removeNode()
        }
    }

    private fun enabledConfig() = AnalyticsBuildConfig.Enabled(
        URI("https://analytics.example.test/v1/session"),
        URI("https://tennis.example.test/privacy/analytics/"),
        noticeVersion = 1,
        osFamily = "windows",
    )

    private fun withVersionProperty(value: String, block: () -> Unit) {
        val previous = System.getProperty("bananashot.version")
        System.setProperty("bananashot.version", value)
        try {
            block()
        } finally {
            if (previous == null) System.clearProperty("bananashot.version") else System.setProperty("bananashot.version", previous)
        }
    }
}
