package org.litvin.ui.tabs.points

import org.junit.jupiter.api.Test
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import java.util.prefs.AbstractPreferences
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreboardPreferencesTest {
    @Test
    fun preferencesKeepTheDefaultStyle() {
        val defaults = PreferencesScoreboardStyleDefaults(MemoryPreferences())
        assertNull(defaults.load())

        val style = ScoreboardSettingsV1(style = ScoreboardStyleId.LED_BOARD, showAppCredit = false, sizePercent = 120)
        defaults.save(style)

        assertEquals(style, defaults.load())
    }

    @Test
    fun theAppCreditRequestIsShownOnlyOnce() {
        val preferences = MemoryPreferences()
        val request = PreferencesAppCreditRequest(preferences)
        assertTrue(request.shouldAsk())

        request.markAsked()

        assertFalse(request.shouldAsk())
        // The flag stays after a restart of the app.
        assertFalse(PreferencesAppCreditRequest(preferences).shouldAsk())
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
