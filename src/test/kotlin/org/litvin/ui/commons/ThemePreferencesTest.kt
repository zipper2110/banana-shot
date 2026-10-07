package org.litvin.ui.commons

import java.awt.Color
import java.util.prefs.AbstractPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemePreferencesTest {
    @Test
    fun savedThemeAndAccent_loadAgain() {
        val preferences = ThemePreferences(MemoryPreferences())
        assertEquals(AppTheme.DARK, preferences.load())
        assertNull(preferences.loadAccent())
        assertEquals(AppTheme.LIGHT.accent, preferences.accentFor(AppTheme.LIGHT))

        preferences.save(AppTheme.MID)
        preferences.saveAccent(Color(0x3366FF))

        assertEquals(AppTheme.MID, preferences.load())
        assertEquals(0x3366FF, preferences.accentFor(AppTheme.LIGHT).rgb and 0xFFFFFF)
    }

    @Test
    fun noAccent_removesTheCustomAccent() {
        val preferences = ThemePreferences(MemoryPreferences())
        preferences.saveAccent(Color(0x3366FF))

        preferences.saveAccent(null)

        assertNull(preferences.loadAccent())
        assertEquals(AppTheme.DARK.accent, preferences.accentFor(AppTheme.DARK))
    }

    private class MemoryPreferences : AbstractPreferences(null, "") {
        private val values = mutableMapOf<String, String>()
        override fun putSpi(key: String, value: String) { values[key] = value }
        override fun getSpi(key: String): String? = values[key]
        override fun removeSpi(key: String) { values.remove(key) }
        override fun removeNodeSpi() = Unit
        override fun keysSpi(): Array<String> = values.keys.toTypedArray()
        override fun childrenNamesSpi(): Array<String> = emptyArray()
        override fun childSpi(name: String) = MemoryPreferences()
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }
}
