package org.litvin.ui.tabs.points

import org.litvin.i18n.tr
import org.litvin.scoring.ScoringMode
import java.util.prefs.Preferences

/**
 * The user's default scoring mode for new projects (B-68). More → Settings changes it. A new project gets it when the
 * Points tab opens the project for the first time, together with the default scoreboard style.
 */
interface ScoringModeDefaults {
    fun load(): ScoringMode

    fun save(mode: ScoringMode)

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

/** Keeps the default scoring mode as the name of the mode in the user preferences. An unknown name is basic mode. */
class PreferencesScoringModeDefaults(private val preferences: Preferences) : ScoringModeDefaults {
    override fun load(): ScoringMode {
        val name = preferences.get(KEY_MODE, null) ?: return ScoringMode.BASIC
        return ScoringMode.entries.firstOrNull { it.name == name } ?: ScoringMode.BASIC
    }

    override fun save(mode: ScoringMode) {
        preferences.put(KEY_MODE, mode.name)
    }

    private companion object {
        const val KEY_MODE = "default-scoring-mode"
    }
}
