package org.litvin.license.check

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

/**
 * The flag "expired mode in the last session" ("Start of a build that looks expired"). The flag selects only the
 * start path. It never changes the result of the expiry check, so it needs no protection.
 */
interface ExpiredFlagStore {
    fun isSet(): Boolean

    fun set(value: Boolean)
}

/** Keeps the flag in [node]. A failure goes to the log only: then the next start uses path A. */
class PreferencesExpiredFlagStore(
    private val node: Preferences,
    private val key: String = KEY,
) : ExpiredFlagStore {
    override fun isSet(): Boolean = try {
        node.getBoolean(key, false)
    } catch (failure: IllegalStateException) {
        logger.warn(failure) { "Cannot read the expired flag" }
        false
    }

    override fun set(value: Boolean) {
        try {
            if (value) node.putBoolean(key, true) else node.remove(key)
            node.flush()
        } catch (failure: BackingStoreException) {
            logger.warn(failure) { "Cannot write the expired flag" }
        } catch (failure: IllegalStateException) {
            logger.warn(failure) { "Cannot write the expired flag" }
        }
    }

    companion object {
        const val KEY = "expiredLastSession"

        private val logger = KotlinLogging.logger {}
    }
}
