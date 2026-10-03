package org.litvin.feedback

import java.util.prefs.Preferences

/** Keeps the reply address of the last report. An empty address deletes the saved one. */
class FeedbackPreferences(private val preferences: Preferences) {
    var email: String
        get() = preferences.get(EMAIL_KEY, "")
        set(value) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) preferences.remove(EMAIL_KEY) else preferences.put(EMAIL_KEY, trimmed)
        }

    private companion object {
        const val EMAIL_KEY = "feedback.email"
    }
}
