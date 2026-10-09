package org.litvin.ui.privacy

import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.AnalyticsLevel
import org.litvin.analytics.AnalyticsPreferences
import org.litvin.analytics.ManagedAnalytics
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.WrapText
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.net.URI
import java.util.Properties
import javax.swing.AbstractButton
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E10-S3 (build-expiry-spec.md, "Privacy"): each build tells about the requests to GitHub, apart from the analytics.
 * T4 of B-8: each build also tells about the feedback reports.
 * T5 of B-9: the analytics texts agree with the privacy notice.
 */
class PrivacyPageTest {
    @Test
    fun `a build with no analytics tells about the version check and the update download`() {
        assertVersionCheckSection(onEdt { PrivacyPage.withoutAnalytics(CONTACT) })
    }

    @Test
    fun `a build with analytics has the same section after the analytics`() {
        val texts = assertVersionCheckSection(analyticsPage())
        assertTrue(texts.indexOf(PrivacyPage.ANALYTICS_TITLE) < texts.indexOf(PrivacyPage.VERSION_CHECK_TITLE))
    }

    @Test
    fun `the page tells what a report contains, where it goes, how long it stays, and how to delete it`() {
        listOf(onEdt { PrivacyPage.withoutAnalytics(CONTACT) }, analyticsPage()).forEach { page ->
            val text = onEdt { texts(page) }.joinToString("\n")
            listOf(
                "Feedback reports",
                "only when you click Send",
                "usage statistics choice does not change this",
                "your email address if you give it",
                "the end of the log files",
                "Cloudflare",
                "Telegram",
                "90 days",
                "does not keep the log files or your IP address",
                "write to $CONTACT. Give the report ID, or the date and the text of your message",
            ).forEach { assertTrue(it in text, it) }
        }
    }

    @Test
    fun `a build with analytics tells what each level collects and what the app excludes`() {
        val page = analyticsPage()
        val text = onEdt { texts(page) }.joinToString("\n")
        listOf(
            "Essential statistics are on by default: app version, OS family, session length, the number of errors",
            "Turn them off to send no statistics at all",
            "Extended statistics also send counts of the tabs and features you use, the export results",
            "the theme and language settings, and the sports of your projects",
            "user or device IDs",
            "text that you type",
        ).forEach { assertTrue(it in text, it) }
        assertTrue("optional usage analytics" !in text, "the old text is removed")
    }

    @Test
    fun `the switches of the Privacy page select the level or turn off all statistics`() {
        val preferences = AnalyticsPreferences(InMemoryPreferencesProvider().node("analytics"))
        val controller = AnalyticsController(analyticsConfig(), preferences, sessionFactory = { _, _, _ -> NoAnalytics })
        val page = onEdt { PrivacyPage.withAnalytics(controller, preferences, URI("https://example.test/privacy"), CONTACT) { true } }
        val (essential, extended) = onEdt { find<SwitchBox>(page) }
        assertEquals(PrivacyPage.ESSENTIAL_SWITCH, essential.text)
        assertEquals(PrivacyPage.EXTENDED_SWITCH, extended.text)
        onEdt {
            assertTrue(essential.isSelected, "essential is on by default")
            assertFalse(extended.isSelected)
            assertTrue(extended.isEnabled)
        }

        onEdt { extended.doClick() }
        assertEquals(AnalyticsLevel.EXTENDED, preferences.resolve().level)

        onEdt { essential.doClick() }
        assertEquals(AnalyticsPreferences.Choice.OFF, preferences.resolve().choice)
        onEdt {
            assertFalse(extended.isSelected, "off also clears the extended switch")
            assertFalse(extended.isEnabled)
        }

        onEdt { essential.doClick() }
        assertEquals(AnalyticsPreferences.Choice.ESSENTIAL, preferences.resolve().choice)
        assertTrue(onEdt { extended.isEnabled })
    }

    @Test
    fun `the Privacy page shows the saved choice off`() {
        val preferences = AnalyticsPreferences(InMemoryPreferencesProvider().node("analytics"))
        preferences.record(AnalyticsPreferences.Choice.OFF)
        val controller = AnalyticsController(analyticsConfig(), preferences, sessionFactory = { _, _, _ -> NoAnalytics })
        val page = onEdt { PrivacyPage.withAnalytics(controller, preferences, URI("https://example.test/privacy"), CONTACT) { true } }
        val (essential, extended) = onEdt { find<SwitchBox>(page) }
        onEdt {
            assertFalse(essential.isSelected)
            assertFalse(extended.isSelected)
            assertFalse(extended.isEnabled)
        }
    }

    @Test
    fun `the consent dialog offers the essential and the extended statistics`() {
        val preferences = AnalyticsPreferences(InMemoryPreferencesProvider().node("analytics"))
        val controller = AnalyticsController(analyticsConfig(), preferences, sessionFactory = { _, _, _ -> NoAnalytics })
        val text = onEdt {
            val dialog = AnalyticsConsentDialog.build(null, controller, URI("https://example.test/privacy")) { true }
            try { texts(dialog).joinToString("\n") } finally { dialog.dispose() }
        }
        listOf("essential, anonymous usage statistics", "extended statistics", "how many people use the app", "turn off all statistics").forEach {
            assertTrue(it in text, text)
        }
        assertTrue("Enable analytics" !in text, text)
    }

    @Test
    fun `each answer of the consent dialog saves a level`() {
        listOf(
            AnalyticsConsentDialog.EXTENDED_BUTTON to AnalyticsPreferences.Choice.EXTENDED,
            AnalyticsConsentDialog.ESSENTIAL_BUTTON to AnalyticsPreferences.Choice.ESSENTIAL,
        ).forEach { (button, choice) ->
            val preferences = AnalyticsPreferences(InMemoryPreferencesProvider().node("analytics"))
            val controller = AnalyticsController(analyticsConfig(), preferences, sessionFactory = { _, _, _ -> NoAnalytics })
            onEdt {
                val dialog = AnalyticsConsentDialog.build(null, controller, URI("https://example.test/privacy")) { true }
                try { find<AbstractButton>(dialog).single { it.text == button }.doClick() } finally { dialog.dispose() }
            }
            assertEquals(choice, preferences.resolve().choice, button)
        }
    }

    private fun analyticsConfig() = AnalyticsBuildConfig.fromProperties(Properties().apply {
        setProperty(AnalyticsBuildConfig.ENDPOINT_PROPERTY, "https://analytics.example.test/v1/session")
        setProperty(AnalyticsBuildConfig.PRIVACY_URL_PROPERTY, "https://tennis.example.test/privacy/analytics/")
        setProperty(AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY, org.litvin.analytics.AnalyticsSchema.NOTICE_VERSION.toString())
    }) as AnalyticsBuildConfig.Enabled

    private fun analyticsPage(): PrivacyPage {
        val config = analyticsConfig()
        val preferences = AnalyticsPreferences(InMemoryPreferencesProvider().node("analytics"))
        val controller = AnalyticsController(config, preferences, sessionFactory = { _, _, _ -> NoAnalytics })
        return onEdt {
            PrivacyPage.withAnalytics(controller, preferences, URI("https://example.test/privacy"), CONTACT) { true }
        }
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
            is JLabel -> component.text?.let(::add)
            is WrapText -> add(component.text)
        }
        if (component is Container) component.components.forEach { addAll(texts(it)) }
    }

    private inline fun <reified T : Component> find(root: Component): List<T> = all(root).filterIsInstance<T>()

    private fun all(root: Component): List<Component> = buildList {
        add(root)
        if (root is Container) root.components.forEach { addAll(all(it)) }
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

    private companion object {
        const val CONTACT = "author@example.test"
    }
}
