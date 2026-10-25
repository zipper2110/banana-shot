package org.litvin.ui.more

import org.litvin.ui.commons.AppTheme
import org.litvin.ui.commons.Theme
import org.litvin.ui.commons.ThemeSettings
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.io.File
import java.net.URI
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
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

    /** The data folder path is in a text area. Its caret must not scroll the page to the data card when it opens. */
    @Test
    fun `the Settings page opens at the top`() {
        System.setProperty("java.awt.headless", "true")
        lateinit var scroll: JScrollPane
        val previous = UIManager.getLookAndFeel()
        // The app uses FlatLaf, and its caret differs from the caret of the default look.
        Theme.start(AppTheme.DEFAULT)

        try {
            SwingUtilities.invokeAndWait {
                val page = SettingsPage(File("C:\\Users\\player\\AppData\\Local\\BananaShot"), RecordingThemeSettings(), onShowHintsAgain = {}, onOpenFolder = { true })
                scroll = page.inScrollPane().apply { setSize(600, 300) }
                // Without a window, validate() does not lay out, so lay out each container as the window does.
                layOut(scroll)
            }
            // The caret scrolls in a later event, so wait for the events that the page made.
            repeat(3) { SwingUtilities.invokeAndWait {} }

            SwingUtilities.invokeAndWait {
                assertTrue(scroll.viewport.viewSize.height > 300, "The page must be taller than the view")
                assertEquals(0, scroll.viewport.viewPosition.y)
            }
        } finally {
            UIManager.setLookAndFeel(previous)
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

    @Test
    fun `About shows the author and opens the website`() {
        System.setProperty("java.awt.headless", "true")
        val opened = mutableListOf<URI>()

        SwingUtilities.invokeAndWait {
            val page = AboutPage(
                AboutInfo(
                    appName = "BananaShot",
                    version = "1.2.3",
                    documents = emptyList(),
                    components = emptyList(),
                    author = "Dmitrii Litvin",
                    website = URI("https://example.test/"),
                ),
                onOpenFile = { true },
                onOpenLink = { opened += it; true },
            )
            assertEquals("Dmitrii Litvin", (find(page, "more-about-author") as JTextArea).text)
            assertEquals("https://example.test/", (find(page, "more-about-website") as JTextArea).text)
            findButton(page, "more-about-open-website")!!.doClick()
        }
        assertEquals(listOf(URI("https://example.test/")), opened)
    }

    private fun layOut(component: Component) {
        if (component !is Container) return
        component.doLayout()
        component.components.forEach(::layOut)
    }

    private fun find(root: Component, name: String): Component? =
        if (root.name == name) root else (root as? Container)?.components?.firstNotNullOfOrNull { find(it, name) }

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
