package org.litvin.license.time

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

/** Keeps the saved time between sessions. */
interface SavedTimeStore {
    /** The stored value, or null when there is no value or the value is damaged. */
    fun read(): Instant?

    fun write(time: Instant)
}

/** Keeps the saved time in [node] as milliseconds from the epoch. */
class PreferencesSavedTimeStore(
    private val node: Preferences,
    private val key: String = KEY,
) : SavedTimeStore {
    override fun read(): Instant? = try {
        node.get(key, null)?.toLongOrNull()?.let(Instant::ofEpochMilli)
    } catch (failure: IllegalStateException) {
        logger.warn(failure) { "Cannot read the saved time" }
        null
    }

    override fun write(time: Instant) {
        try {
            node.putLong(key, time.toEpochMilli())
            node.flush()
        } catch (failure: BackingStoreException) {
            logger.warn(failure) { "Cannot write the saved time" }
        } catch (failure: IllegalStateException) {
            logger.warn(failure) { "Cannot write the saved time" }
        }
    }

    companion object {
        const val KEY = "savedTime"

        private val logger = KotlinLogging.logger {}
    }
}
