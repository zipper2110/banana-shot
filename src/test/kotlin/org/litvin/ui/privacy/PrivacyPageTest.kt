package org.litvin.ui.privacy

import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.AnalyticsPreferences
import org.litvin.analytics.ManagedAnalytics
import org.litvin.ui.commons.WrapText
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.net.URI
import java.util.Properties
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertTrue

/** E10-S3 (build-expiry-spec.md, "Privacy"): each build tells about the requests to GitHub, apart from the analytics. */
class PrivacyPageTest {
    @Test
    fun `a build with no analytics tells about the version check and the update download`() {
        assertVersionCheckSection(onEdt { PrivacyPage.withoutAnalytics() })
    }

    @Test
    fun `a build with analytics has the same section after the analytics`() {
        val config = AnalyticsBuildConfig.fromProperties(Properties().apply {
            setProperty(AnalyticsBuildConfig.ENDPOINT_PROPERTY, "https://analytics.example.test/v1/events/batch")
            setProperty(AnalyticsBuildConfig.PRIVACY_URL_PROPERTY, "https://tennis.example.test/privacy/analytics/")
            setProperty(AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY, "1")
        }) as AnalyticsBuildConfig.Enabled
        val preferences = AnalyticsPreferences(InMemoryPreferencesProvider().node("analytics"))
        val controller = AnalyticsController(config, preferences, enabledFactory = { NoAnalytics })

        val page = onEdt {
            PrivacyPage.withAnalytics(controller, preferences, URI("https://example.test/privacy"), URI("mailto:test@example.test")) { true }
        }

        val texts = assertVersionCheckSection(page)
        assertTrue(texts.indexOf("Usage analytics") < texts.indexOf(PrivacyPage.VERSION_CHECK_TITLE))
    }

    private fun assertVersionCheckSection(page: PrivacyPage): List<String> {
        val texts = onEdt { texts(page) }
        val start = texts.indexOf(PrivacyPage.VERSION_CHECK_TITLE)
        assertTrue(start >= 0, "The page has the section '${PrivacyPage.VERSION_CHECK_TITLE}': $texts")
        val section = texts.drop(start + 1).joinToString(" ")
        assertTrue(section.contains("GitHub"), "The section names GitHub as the receiver")
        assertTrue(section.contains("no user ID"))
        assertTrue(section.contains("Update and restart"), "The section tells about the download of the setup file")
        assertTrue(section.contains("cannot turn it off"))
        assertTrue(section.contains(AppInfo.NAME))
        return texts
    }

    private fun texts(component: Component): List<String> = buildList {
        when (component) {
            is JLabel -> add(component.text)
            is WrapText -> add(component.text)
        }
        if (component is Container) component.components.forEach { addAll(texts(it)) }
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        EventQueue.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private object NoAnalytics : ManagedAnalytics {
        override fun record(event: AnalyticsEvent) = Unit
        override fun close() = Unit
    }
}
