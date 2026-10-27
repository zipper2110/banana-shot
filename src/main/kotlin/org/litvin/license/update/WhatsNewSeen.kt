package org.litvin.license.update

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.license.Version
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

/**
 * The newest version whose "What's new" popup showed (B-54). The popup shows only one time for each new version.
 * Tests give a fake.
 */
interface WhatsNewSeen {
    /** The version of the last popup that showed, or null when no popup showed. */
    fun lastShown(): String?

    fun markShown(version: String)

    /** True when the popup of [version] did not show. An older or the same version gives false. */
    fun isNew(version: String): Boolean = lastShown()?.let { Version.parse(version) > Version.parse(it) } ?: true
}

/** Keeps the version in [node]. A failure goes to the log only: then the popup can show one more time. */
class PreferencesWhatsNewSeen(
    private val node: Preferences,
    private val key: String = KEY,
) : WhatsNewSeen {
    override fun lastShown(): String? = try {
        node.get(key, null)
    } catch (failure: IllegalStateException) {
        logger.warn(failure) { "Cannot read the version of the last What's new popup" }
        null
    }

    override fun markShown(version: String) {
        try {
            node.put(key, version)
            node.flush()
        } catch (failure: BackingStoreException) {
            logger.warn(failure) { "Cannot write the version of the last What's new popup" }
        } catch (failure: IllegalStateException) {
            logger.warn(failure) { "Cannot write the version of the last What's new popup" }
        }
    }

    companion object {
        const val KEY = "whatsNewShownVersion"

        private val logger = KotlinLogging.logger {}
    }
}
