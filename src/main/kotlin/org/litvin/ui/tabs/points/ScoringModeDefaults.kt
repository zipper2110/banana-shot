package org.litvin.ui.tabs.points

import org.litvin.i18n.tr
import org.litvin.scoring.ScoringMode
import java.util.prefs.Preferences

/**
 * The user's default scoring mode for new projects (B-68). More → Settings changes it. A new project gets it when the
 * Points tab opens the project for the first time, together with the default scoreboard style. The default of the
 * option "Ask error kind" of advanced mode comes in the same way. It is off when the user did not set it.
 */
interface ScoringModeDefaults {
    fun load(): ScoringMode

    fun save(mode: ScoringMode)

    /** The default of the option "Ask error kind" (B-68, "Follow-up questions"). */
    fun loadAskErrorKind(): Boolean = false

    fun saveAskErrorKind(ask: Boolean) = Unit

    companion object {
        /** Keeps no default: new projects use basic mode. Tests and the standalone panel use it. */
        val NONE: ScoringModeDefaults = object : ScoringModeDefaults {
            override fun load(): ScoringMode = ScoringMode.BASIC
            override fun save(mode: ScoringMode) = Unit
        }

        /** The label of [mode] in the mode switch and in Settings. */
        fun label(mode: ScoringMode): String = when (mode) {
            ScoringMode.BASIC -> tr("points.mode.basic")
            ScoringMode.ADVANCED -> tr("points.mode.advanced")
        }

        /** The tooltip of [mode] in the mode switch and in Settings. */
        fun tooltip(mode: ScoringMode): String = when (mode) {
            ScoringMode.BASIC -> tr("points.mode.basic.tooltip")
            ScoringMode.ADVANCED -> tr("points.mode.advanced.tooltip")
        }
    }
}

/**
 * Keeps the default scoring mode as the name of the mode in the user preferences. An unknown name is basic mode. It
 * also keeps the default of the option "Ask error kind".
 */
class PreferencesScoringModeDefaults(private val preferences: Preferences) : ScoringModeDefaults {
    override fun load(): ScoringMode {
        val name = preferences.get(KEY_MODE, null) ?: return ScoringMode.BASIC
        return ScoringMode.entries.firstOrNull { it.name == name } ?: ScoringMode.BASIC
    }

    override fun save(mode: ScoringMode) {
        preferences.put(KEY_MODE, mode.name)
    }

    override fun loadAskErrorKind(): Boolean = preferences.getBoolean(KEY_ASK_ERROR_KIND, false)

    override fun saveAskErrorKind(ask: Boolean) {
        preferences.putBoolean(KEY_ASK_ERROR_KIND, ask)
    }

    private companion object {
        const val KEY_MODE = "default-scoring-mode"
        const val KEY_ASK_ERROR_KIND = "default-ask-error-kind"
    }
}
