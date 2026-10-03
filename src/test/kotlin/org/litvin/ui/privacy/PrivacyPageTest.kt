package org.litvin.ui.privacy

import org.litvin.ui.commons.WrapText
import java.awt.Component
import java.awt.Container
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

/** T4 of B-8: the Privacy page tells about the feedback reports, also in a build without analytics. */
class PrivacyPageTest {
    private fun texts(root: Component): String = buildList {
        fun walk(component: Component) {
            if (component is WrapText) add(component.text)
            if (component is Container) component.components.forEach(::walk)
        }
        walk(root)
    }.joinToString("\n")

    @Test
    fun `the page tells what a report contains, where it goes, how long it stays, and how to delete it`() {
        System.setProperty("java.awt.headless", "true")
        lateinit var text: String
        SwingUtilities.invokeAndWait { text = texts(PrivacyPage.withoutAnalytics("author@example.test")) }

        listOf(
            "only when you click Send",
            "usage analytics choice does not change this",
            "your email address if you give it",
            "the end of the log files",
            "Cloudflare",
            "Telegram",
            "90 days",
            "does not keep the log files or your IP address",
            "write to author@example.test and give the report ID",
        ).forEach { assertTrue(it in text, it) }
    }
}
