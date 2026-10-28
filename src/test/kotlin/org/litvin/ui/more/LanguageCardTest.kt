package org.litvin.ui.more

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import org.litvin.ui.commons.AppTheme
import org.litvin.ui.commons.SectionPage
import org.litvin.ui.commons.ThemeSettings
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** B-11 E4-T11: the "Language" row of More > Settings. */
class LanguageCardTest {
    private val saved = mutableListOf<AppLanguage?>()
    private var restarts = 0

    @BeforeTest
    fun setUp() {
        System.setProperty("java.awt.headless", "true")
    }

    @AfterTest
    fun tearDown() = I18n.install(Messages.english())

    @Test
    fun `the row has a globe icon, and a translated row also has the English word Language`() {
        assertEquals("Language", onEdt { heading(page()) }.let { assertNotNull(it.icon); it.text })
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        assertEquals("Idioma · Language", onEdt { heading(page()) }.let { assertNotNull(it.icon); it.text })
    }

    @Test
    fun `the list shows each language in its own language`() {
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        val names = onEdt { combo(page()).let { combo -> (0 until combo.itemCount).map { combo.getItemAt(it).toString() } } }
        assertEquals(listOf("Igual que Windows: English") + AppLanguage.entries.map { it.nativeName }, names)
    }

    @Test
    fun `on an English app, the user selects Spanish, and Restart now applies it`() {
        onEdt {
            val page = page()
            val restart = button(page, "more-settings-language-restart")
            assertFalse(restart.isVisible)
            combo(page).selectedIndex = AppLanguage.entries.indexOf(AppLanguage.SPANISH_SPAIN) + 1
            assertTrue(restart.isVisible)
            assertEquals(Messages.english().text("settings.language.afterRestart"), status(page))
            restart.doClick()
        }
        assertEquals(listOf<AppLanguage?>(AppLanguage.SPANISH_SPAIN), saved)
        assertEquals(1, restarts)
    }

    @Test
    fun `without a restart, the row tells the user to start the app again`() {
        onEdt {
            val page = page(canRestart = false)
            combo(page).selectedIndex = AppLanguage.entries.indexOf(AppLanguage.SPANISH_SPAIN) + 1
            assertFalse(button(page, "more-settings-language-restart").isVisible)
            assertEquals(Messages.english().text("settings.language.closeAndStart"), status(page))
        }
    }

    @Test
    fun `selecting the running language again needs no restart`() {
        onEdt {
            val page = page()
            combo(page).selectedIndex = AppLanguage.entries.indexOf(AppLanguage.SPANISH_SPAIN) + 1
            combo(page).selectedIndex = AppLanguage.entries.indexOf(AppLanguage.ENGLISH) + 1
            assertFalse(button(page, "more-settings-language-restart").isVisible)
            assertEquals("", status(page))
        }
    }

    private fun page(canRestart: Boolean = true) = SettingsPage(
        File("data"), NoThemeSettings, onShowHintsAgain = {}, onOpenFolder = { true },
        language = object : LanguageSetting {
            override val choice: AppLanguage? = null
            override val running: AppLanguage = AppLanguage.ENGLISH
            override val systemLanguage: AppLanguage = AppLanguage.ENGLISH
            override fun save(choice: AppLanguage?): Boolean = saved.add(choice)
            override val restart: (() -> Unit)? = if (canRestart) ({ restarts++ }) else null
        },
    )

    private fun heading(page: Component): JLabel = named(page, "more-settings-language-heading")

    @Suppress("UNCHECKED_CAST")
    private fun combo(page: Component): JComboBox<Any> = named(page, "more-settings-language")

    private fun button(page: Component, name: String): AbstractButton = named(page, name)

    private fun status(page: Component): String = named<SectionPage.StatusLine>(page, "more-settings-language-status").text

    private inline fun <reified T : Component> named(root: Component, name: String): T =
        all(root).filterIsInstance<T>().single { it.name == name }

    private fun all(root: Component): List<Component> = buildList {
        add(root)
        if (root is Container) root.components.forEach { addAll(all(it)) }
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private object NoThemeSettings : ThemeSettings {
        override val theme = AppTheme.DARK
        override val customAccent: Color? = null
        override fun selectTheme(theme: AppTheme) = Unit
        override fun selectAccent(accent: Color?) = Unit
        override fun previewAccent(accent: Color) = Unit
        override fun onChange(listener: () -> Unit) = Unit
    }
}
