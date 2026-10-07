package org.litvin.ui.tabs.scoring

import java.util.prefs.Preferences

/**
 * The request from the app author to keep the app credit line on the scoreboard.
 * The app asks only once: the first time the user turns the line off.
 */
interface AppCreditRequest {
    /** Returns true when the app did not ask yet. */
    fun shouldAsk(): Boolean

    fun markAsked()

    companion object {
        /** Never asks. Tests and the standalone panel use it. */
        val NONE: AppCreditRequest = object : AppCreditRequest {
            override fun shouldAsk() = false
            override fun markAsked() = Unit
        }
    }
}

/** Keeps the "asked" flag in the user preferences. */
class PreferencesAppCreditRequest(private val preferences: Preferences) : AppCreditRequest {
    override fun shouldAsk(): Boolean = !preferences.getBoolean(KEY_ASKED, false)

    override fun markAsked() {
        preferences.putBoolean(KEY_ASKED, true)
    }

    private companion object {
        const val KEY_ASKED = "app-credit-request-shown"
    }
}
