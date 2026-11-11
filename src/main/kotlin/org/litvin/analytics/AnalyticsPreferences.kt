package org.litvin.analytics

import java.time.Instant
import java.util.prefs.Preferences

/**
 * Stores the level choice and the session count. It stores no counters and no session ID. The crash marker of the app
 * (B-67), not this class, finds an unclean exit.
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

    /**
     * [number] is the number of this analytics session on this install, from 1. [crash] is the crash of the previous
     * app session, or null after a normal exit. It gives `unclean_exit` and the crash keys (B-67).
     */
    data class SessionStart(val number: Int, val crash: AnalyticsCrash? = null) {
        val uncleanExit: Boolean get() = crash != null
    }

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

    /** Starts an analytics session: increases the session count. [crash] is the crash of the previous app session. */
    fun startSession(crash: AnalyticsCrash? = null): SessionStart {
        val number = (preferences.getInt(SESSION_COUNT_KEY, 0).coerceAtLeast(0) + 1).coerceAtMost(MAX_SESSION_COUNT)
        preferences.putInt(SESSION_COUNT_KEY, number)
        // Versions before B-67 kept an open-session flag here. The crash marker replaces it.
        preferences.remove(LEGACY_OPEN_SESSION_KEY)
        flush()
        return SessionStart(number, crash)
    }

    private fun flush() {
        runCatching { preferences.flush() }
    }

    private companion object {
        const val CHOICE_KEY = "analytics.choice"
        const val NOTICE_VERSION_KEY = "analytics.noticeVersion"
        const val DECIDED_AT_KEY = "analytics.decidedAt"
        const val SESSION_COUNT_KEY = "analytics.sessionCount"
        const val LEGACY_OPEN_SESSION_KEY = "analytics.sessionOpen"
        const val MAX_SESSION_COUNT = 1_000_000
    }
}
