package org.litvin.ui.commons

import java.util.prefs.Preferences

/** The one-time hints of the app. [key] is the part of the preferences key, so do not change it. */
enum class HintId(val key: String) {
    /** The Help button, after the user closes the Overview help at the first start. */
    HELP_BUTTON("help-button"),

    /** The Scoring button in the sidebar, after 4 marked points. */
    GO_TO_SCORING("go-to-scoring"),

    /** The first point row of the Points tab: the hover actions of a row. */
    POINT_ROW("point-row"),

    /** The Scoring settings button, after the automatic Scoring settings dialog. */
    SCORE_SETTINGS("score-settings"),

    /** The Next button of the Scoring tab: the R and Shift+R keys. */
    SCORING_KEYS("scoring-keys"),

    /** The serve button of player 1: one serve mark gives the serve statistics. */
    SERVE_MARK("serve-mark"),

    /** The active export: the user can continue to edit, and more exports go into a queue. */
    EXPORT_QUEUE("export-queue"),
}

/** Keeps which hints the user closed. A closed hint never shows again until [resetAll]. */
interface HintRegistry {
    fun isDismissed(hint: HintId): Boolean

    fun dismiss(hint: HintId)

    /** Makes all hints show again. */
    fun resetAll()

    companion object {
        /** Never shows a hint. Tests and the standalone panels use it. */
        val NONE: HintRegistry = object : HintRegistry {
            override fun isDismissed(hint: HintId) = true
            override fun dismiss(hint: HintId) = Unit
            override fun resetAll() = Unit
        }
    }
}

/** Keeps one flag for each hint in the user preferences, so that a hint shows one time for each user, not for each project. */
class PreferencesHintRegistry(private val preferences: Preferences) : HintRegistry {
    override fun isDismissed(hint: HintId): Boolean = preferences.getBoolean(key(hint), false)

    override fun dismiss(hint: HintId) {
        preferences.putBoolean(key(hint), true)
    }

    override fun resetAll() {
        HintId.entries.forEach { preferences.remove(key(it)) }
    }

    companion object {
        /** The preferences key of the closed flag of [hint]. */
        fun key(hint: HintId): String = "hint.${hint.key}.dismissed"
    }
}
