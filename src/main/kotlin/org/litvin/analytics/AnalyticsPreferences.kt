package org.litvin.analytics

import java.time.Instant
import java.util.prefs.Preferences

/**
 * Stores the consent choice, the session count, and the open-session flag. It stores no counters and no session ID.
 */
class AnalyticsPreferences(private val preferences: Preferences) {
    enum class Choice { UNDECIDED, ENABLED, DISABLED }

    data class ResolvedChoice(val choice: Choice, val isEnabled: Boolean, val needsChoice: Boolean)

    /** [number] is the number of this analytics session on this install, from 1. */
    data class SessionStart(val number: Int, val uncleanExit: Boolean)

    fun resolve(): ResolvedChoice {
        val storedChoice = runCatching { Choice.valueOf(preferences.get(CHOICE_KEY, Choice.UNDECIDED.name)) }
            .getOrDefault(Choice.UNDECIDED)
        val storedVersion = preferences.getInt(NOTICE_VERSION_KEY, 0)
        val current = storedVersion == AnalyticsSchema.NOTICE_VERSION
        if (!current) return ResolvedChoice(Choice.DISABLED, isEnabled = false, needsChoice = true)
        return ResolvedChoice(storedChoice, storedChoice == Choice.ENABLED, storedChoice == Choice.UNDECIDED)
    }

    fun record(choice: Choice) {
        require(choice != Choice.UNDECIDED)
        preferences.put(CHOICE_KEY, choice.name)
        preferences.putInt(NOTICE_VERSION_KEY, AnalyticsSchema.NOTICE_VERSION)
        preferences.put(DECIDED_AT_KEY, Instant.now().toString())
    }

    fun dismiss() = record(Choice.DISABLED)

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

    /** Clears the open-session flag: at a normal exit (a shutdown hook) and when the user turns off analytics. */
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
