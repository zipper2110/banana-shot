package org.litvin.analytics

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnalyticsBuildConfigTest {
    @Test
    fun `the build scripts use the property names and the notice version of the app`() {
        listOf("distribution/windows/Build-AppImage.ps1", "distribution/windows/Validate-AppImage.ps1").forEach { path ->
            val script = File(path).readText()
            assertTrue("\$expectedNoticeVersion = \"${AnalyticsSchema.NOTICE_VERSION}\"" in script, "$path: notice version")
            listOf(
                AnalyticsBuildConfig.ENDPOINT_PROPERTY,
                AnalyticsBuildConfig.PRIVACY_URL_PROPERTY,
                AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY,
            ).forEach { assertTrue(it in script, "$path: $it") }
            assertTrue("${AnalyticsBuildConfig.ENDPOINT_PATH}$'" in script, "$path: endpoint path")
        }
    }

    @Test
    fun `accepts only a complete HTTPS release configuration`() {
        val config = AnalyticsBuildConfig.fromProperties(properties(
            endpoint = "https://analytics.example.test/v1/session",
            privacyUrl = "https://tennis.example.test/privacy/analytics/",
            noticeVersion = "2"
        ))

        val enabled = assertIs<AnalyticsBuildConfig.Enabled>(config)
        assertEquals("https://analytics.example.test/v1/session", enabled.endpoint.toString())
        assertEquals("https://tennis.example.test/privacy/analytics/", enabled.privacyUrl.toString())
        assertEquals(2, enabled.noticeVersion)
    }

    @Test
    fun `fails closed for partial or unsafe configuration`() {
        listOf(
            properties(endpoint = "https://analytics.example.test/v1/session", privacyUrl = null, noticeVersion = "2"),
            properties(endpoint = "http://analytics.example.test/v1/session", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "2"),
            properties(endpoint = "https://analytics.example.test/v1/session?x=1", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "2"),
            properties(endpoint = "https://analytics.example.test/v1/sessions", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "2"),
            properties(endpoint = "https://analytics.example.test/v1/events/batch", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "2"),
            properties(endpoint = "https://user:password@analytics.example.test/v1/session", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "2"),
            properties(endpoint = "https://analytics.example.test/v1/session", privacyUrl = "https://tennis.example.test/privacy/analytics/#notice", noticeVersion = "2"),
            properties(endpoint = "https://analytics.example.test/v1/session", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "1")
        ).forEach { properties ->
            assertIs<AnalyticsBuildConfig.Disabled>(AnalyticsBuildConfig.fromProperties(properties))
        }
    }

    private fun properties(endpoint: String?, privacyUrl: String?, noticeVersion: String?): Properties = Properties().apply {
        endpoint?.let { setProperty(AnalyticsBuildConfig.ENDPOINT_PROPERTY, it) }
        privacyUrl?.let { setProperty(AnalyticsBuildConfig.PRIVACY_URL_PROPERTY, it) }
        noticeVersion?.let { setProperty(AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY, it) }
    }
}
