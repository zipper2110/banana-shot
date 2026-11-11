package org.litvin.shared.util

/** An activity of the app that can be the cause of a crash (B-67). The key is a code, not a text for the user. */
enum class AppActivity(val key: String) {
    EXPORT("export"),
    PLAYBACK("playback"),
    SLEEP_WAKE("sleep_wake"),
}

/**
 * What the app does now (B-67). The export queue, the video players, and the sleep listener tell it. The crash marker
 * listens and keeps the activities in a file, so that the next start knows what the app did at a crash.
 *
 * Each activity has a set of owners (a job ID, a player). The activity is on while one owner or more has it.
 */
object AppActivities {
    private val owners = mutableMapOf<AppActivity, MutableSet<Any>>()
    private var listener: ((Set<AppActivity>) -> Unit)? = null

    /** Turns [activity] on or off for [owner]. The listener gets the new set only when the set changes. */
    fun set(activity: AppActivity, owner: Any, active: Boolean) {
        synchronized(this) {
            val before = currentLocked()
            val set = owners.getOrPut(activity) { mutableSetOf() }
            if (active) set += owner else set -= owner
            val after = currentLocked()
            // The listener runs in the lock, so that the file always gets the newest set last.
            if (after != before) runCatching { listener?.invoke(after) }
        }
    }

    fun current(): Set<AppActivity> = synchronized(this) { currentLocked() }

    /** Sets the one listener. Null removes it. */
    fun listen(listener: ((Set<AppActivity>) -> Unit)?) {
        synchronized(this) { this.listener = listener }
    }

    /** For tests: removes all owners and the listener. */
    fun reset() {
        synchronized(this) {
            owners.clear()
            listener = null
        }
    }

    private fun currentLocked(): Set<AppActivity> =
        owners.filterValues { it.isNotEmpty() }.keys.toSortedSet()
}
