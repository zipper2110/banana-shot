package org.litvin.ui.more

import org.litvin.ui.commons.AppTheme
import org.litvin.ui.commons.ThemeSettings
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorePanelTest {
    @Test
    fun `selecting a section changes the selected title`() {
        System.setProperty("java.awt.headless", "true")

        SwingUtilities.invokeAndWait {
            val panel = MorePanel(listOf(MoreSection("Settings", JLabel()), MoreSection("About", JLabel())))
            assertEquals(listOf("Settings", "About"), panel.sectionTitles())
            assertEquals("Settings", panel.selectedTitle)

            panel.selectSection("About")
            assertEquals("About", panel.selectedTitle)

            panel.selectSection("Unknown")
            assertEquals("About", panel.selectedTitle)
        }
    }

    @Test
    fun `Show all hints again calls the reset callback`() {
        System.setProperty("java.awt.headless", "true")
        var resets = 0

        SwingUtilities.invokeAndWait {
            val page = SettingsPage(File("data"), RecordingThemeSettings(), onShowHintsAgain = { resets++ }, onOpenFolder = { true })
            checkNotNull(findButton(page, "more-settings-show-hints")).doClick()
        }

        assertEquals(1, resets)
    }

    @Test
    fun `selecting a theme calls the theme callback`() {
        System.setProperty("java.awt.headless", "true")
        val settings = RecordingThemeSettings()

        SwingUtilities.invokeAndWait {
            val page = SettingsPage(File("data"), settings, onShowHintsAgain = {}, onOpenFolder = { true })
            checkNotNull(findButton(page, "more-settings-theme-2")).doClick()
            // The selected theme does not call the callback again.
            checkNotNull(findButton(page, "more-settings-theme-2")).doClick()
        }

        assertEquals(listOf(AppTheme.LIGHT), settings.themes)
    }

    @Test
    fun `Default removes the custom accent and is disabled without one`() {
        System.setProperty("java.awt.headless", "true")
        val settings = RecordingThemeSettings(customAccent = Color(0x3366FF))

        SwingUtilities.invokeAndWait {
            val page = SettingsPage(File("data"), settings, onShowHintsAgain = {}, onOpenFolder = { true })
            val default = checkNotNull(findButton(page, "more-settings-accent-default"))
            assertTrue(default.isEnabled)
            default.doClick()
            assertFalse(default.isEnabled)
        }

        assertEquals(listOf<Color?>(null), settings.accents)
    }

    private class RecordingThemeSettings(
        override var theme: AppTheme = AppTheme.DARK,
        override var customAccent: Color? = null,
    ) : ThemeSettings {
        val themes = mutableListOf<AppTheme>()
        val accents = mutableListOf<Color?>()

        override fun selectTheme(theme: AppTheme) {
            themes += theme
            this.theme = theme
        }

        override fun selectAccent(accent: Color?) {
            accents += accent
            customAccent = accent
        }

        override fun previewAccent(accent: Color) = Unit

        override fun onChange(listener: () -> Unit) = Unit
    }

    @Test
    fun `About shows buttons only for the documents that exist`() {
        System.setProperty("java.awt.headless", "true")
        val existing = File.createTempFile("license", ".txt").apply { deleteOnExit() }

        SwingUtilities.invokeAndWait {
            val page = AboutPage(
                AboutInfo(
                    appName = "BananaShot",
                    version = "1.2.3",
                    documents = listOf(AboutDocument("License", existing), AboutDocument("Missing", File("no-such-file.txt"))),
                    components = listOf("Java" to "17"),
                ),
                onOpenFile = { true },
            )
            val texts = buttons(page).map { it.text }
            assertTrue("License" in texts)
            assertTrue("Missing" !in texts)
        }
    }

    private fun buttons(root: Component): List<AbstractButton> = buildList {
        if (root is AbstractButton) add(root)
        if (root is Container) root.components.forEach { addAll(buttons(it)) }
    }

    private fun findButton(root: Component, name: String): AbstractButton? = buttons(root).firstOrNull { it.name == name }

    @Test
    fun `Send feedback is the first action of the Contact page and opens the form`() {
        System.setProperty("java.awt.headless", "true")
        var opened = 0

        SwingUtilities.invokeAndWait {
            val page = ContactPage("someone@example.com", "1.2.3", File("logs"), onSendFeedback = { opened++ }, onOpenLink = { true }, onOpenFolder = { true })
            val all = buttons(page)
            assertEquals("more-contact-feedback", all.first().name)
            all.first().doClick()
        }

        assertEquals(1, opened)
    }

    @Test
    fun `Write an email opens a mailto link with the address`() {
        System.setProperty("java.awt.headless", "true")
        val opened = mutableListOf<java.net.URI>()

        SwingUtilities.invokeAndWait {
            val page = ContactPage("someone@example.com", "1.2.3", File("logs"), onSendFeedback = {}, onOpenLink = { opened += it; true }, onOpenFolder = { true })
            checkNotNull(findButton(page, "more-contact-write")).doClick()
        }

        assertEquals(listOf(java.net.URI("mailto:someone@example.com")), opened)
    }

    @Test
    fun `Open log folder makes the folder and opens it`() {
        System.setProperty("java.awt.headless", "true")
        val logFolder = kotlin.io.path.createTempDirectory("contact-logs").toFile().resolve("logs")
        val opened = mutableListOf<File>()

        try {
            SwingUtilities.invokeAndWait {
                val page = ContactPage("someone@example.com", "1.2.3", logFolder, onSendFeedback = {}, onOpenLink = { true }, onOpenFolder = { opened += it; true })
                checkNotNull(findButton(page, "more-contact-open-log-folder")).doClick()
            }

            assertEquals(listOf(logFolder), opened)
            assertTrue(logFolder.isDirectory)
        } finally {
            logFolder.parentFile.deleteRecursively()
        }
    }
}
