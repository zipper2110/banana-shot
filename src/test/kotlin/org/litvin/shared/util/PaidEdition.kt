package org.litvin.shared.util

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Runs each test of a class in the paid version (docs/paid-features.md). Use it with
 * `@ExtendWith(PaidEdition::class)` on a test of a paid feature, for example advanced scoring.
 * After each test, the switch gets its old value again.
 */
class PaidEdition : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        context.getStore(NAMESPACE).put(KEY, System.getProperty(Edition.PROPERTY) ?: NONE)
        System.setProperty(Edition.PROPERTY, "true")
    }

    override fun afterEach(context: ExtensionContext) {
        restore(context.getStore(NAMESPACE).remove(KEY, String::class.java) ?: NONE)
    }

    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(PaidEdition::class.java)
        private const val KEY = "old"
        private const val NONE = "\u0000"

        /** Runs [block] in the free version or the paid version, and then gives the switch its old value again. */
        fun <T> with(paid: Boolean, block: () -> T): T {
            val old = System.getProperty(Edition.PROPERTY) ?: NONE
            System.setProperty(Edition.PROPERTY, paid.toString())
            try {
                return block()
            } finally {
                restore(old)
            }
        }

        private fun restore(old: String) {
            if (old == NONE) System.clearProperty(Edition.PROPERTY) else System.setProperty(Edition.PROPERTY, old)
        }
    }
}
