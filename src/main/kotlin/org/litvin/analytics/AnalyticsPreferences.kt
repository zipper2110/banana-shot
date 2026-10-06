package org.litvin.analytics

import java.time.Instant
import java.util.prefs.Preferences

/**
 * Stores the level choice, the session count, and the open-session flag. It stores no counters and no session ID.
 */
class AnalyticsPreferences(private val preferences: Preferences) {
    /**
     * The choice of the user. [level] is the level that the app sends, or null when the app sends nothing.
     * [UNDECIDED] sends the essential level, and the app asks at the next start.
     */
    enum class Choice(val level: AnalyticsLevel?) {
        UNDECIDED(AnalyticsLevel.ESSENTIAL),
        OFF(null),
        ESSENTIAL(AnalyticsLevel.ESSENTIAL),
        EXTENDED(AnalyticsLevel.EXTENDED),
    }

    data class ResolvedChoice(val choice: Choice, val needsChoice: Boolean) {
        val level: AnalyticsLevel? get() = choice.level
    }

    /** [number] is the number of this analytics session on this install, from 1. */
    data class SessionStart(val number: Int, val uncleanExit: Boolean)

    /** A choice for another notice version, or an unknown value, is [Choice.UNDECIDED]: the level is essential. */
    fun resolve(): ResolvedChoice {
        val storedChoice = runCatching { Choice.valueOf(preferences.get(CHOICE_KEY, Choice.UNDECIDED.name)) }
            .getOrDefault(Choice.UNDECIDED)
        val current = preferences.getInt(NOTICE_VERSION_KEY, 0) == AnalyticsSchema.NOTICE_VERSION
        val choice = if (current) storedChoice else Choice.UNDECIDED
        return ResolvedChoice(choice, needsChoice = choice == Choice.UNDECIDED)
    }

    fun record(choice: Choice) {
        require(choice != Choice.UNDECIDED)
        preferences.put(CHOICE_KEY, choice.name)
        preferences.putInt(NOTICE_VERSION_KEY, AnalyticsSchema.NOTICE_VERSION)
        preferences.put(DECIDED_AT_KEY, Instant.now().toString())
    }

    /**
     * Starts an analytics session: increases the session count and sets the open-session flag. A flag that is still
     * set from the previous session means that the previous session did not close normally (`unclean_exit`).
     */
    fun startSession(): SessionStart {
        val uncleanExit = preferences.getBoolean(OPEN_SESSION_KEY, false)
        val number = (preferences.getInt(SESSION_COUNT_KEY, 0).coerceAtLeast(0) + 1).coerceAtMost(MAX_SESSION_COUNT)
        preferences.putInt(SESSION_COUNT_KEY, number)
        preferences.putBoolean(OPEN_SESSION_KEY, true)
        flush()
        return SessionStart(number, uncleanExit)
    }

    /** Clears the open-session flag: at a normal exit (a shutdown hook) and when the user turns off the statistics. */
    fun endSession() {
        preferences.remove(OPEN_SESSION_KEY)
        flush()
    }

    private fun flush() {
        runCatching { preferences.flush() }
    }

    private companion object {
        const val CHOICE_KEY = "analytics.choice"
        const val NOTICE_VERSION_KEY = "analytics.noticeVersion"
        const val DECIDED_AT_KEY = "analytics.decidedAt"
        const val SESSION_COUNT_KEY = "analytics.sessionCount"
        const val OPEN_SESSION_KEY = "analytics.sessionOpen"
        const val MAX_SESSION_COUNT = 1_000_000
    }
}
