package org.litvin.ui.commons

import java.awt.Color
import javax.swing.AbstractButton
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeSwitchTest {
    @Test
    fun aSelectionInOneControlShowsInTheOther() {
        System.setProperty("java.awt.headless", "true")
        val settings = FakeThemeSettings()

        SwingUtilities.invokeAndWait {
            val projects = ThemeSwitch("projects-theme", settings)
            val more = ThemeSwitch("more-settings-theme", settings)

            projects.components.first { it.name == "projects-theme-1" }.let { (it as AbstractButton).doClick() }

            assertEquals(AppTheme.MID, settings.theme)
            assertEquals(AppTheme.MID, projects.selected)
            assertEquals(AppTheme.MID, more.selected)
        }
    }

    private class FakeThemeSettings : ThemeSettings {
        private val listeners = mutableListOf<() -> Unit>()
        override var theme = AppTheme.DARK
        override val customAccent: Color? = null

        override fun selectTheme(theme: AppTheme) {
            this.theme = theme
            listeners.forEach { it() }
        }

        override fun selectAccent(accent: Color?) = Unit
        override fun previewAccent(accent: Color) = Unit
        override fun onChange(listener: () -> Unit) {
            listeners += listener
        }
    }
}
