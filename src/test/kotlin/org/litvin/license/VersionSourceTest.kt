package org.litvin.license

import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.EnabledAnalytics
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionSourceTest {
    @Test
    fun `app and analytics version are BuildInfo VERSION also with a different system property`() =
        withVersionProperty("99.0.0") {
            assertEquals(BuildInfo.VERSION, AppInfo.version)
            assertEquals(BuildInfo.VERSION, EnabledAnalytics(enabledConfig()).appVersion)
        }

    @Test
    fun `display name always contains the version`() {
        assertEquals("${AppInfo.NAME} ${BuildInfo.VERSION}", AppInfo.displayName)
        assertTrue(AppInfo.displayName.contains(BuildInfo.VERSION))
    }

    private fun enabledConfig() = AnalyticsBuildConfig.Enabled(
        URI("https://analytics.example.test/v1/events/batch"),
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
