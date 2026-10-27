package org.litvin.license.update

import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-54: the popup shows only one time for each new version. */
class PreferencesWhatsNewSeenTest {
    private val node = InMemoryPreferencesProvider().node("license")
    private val seen = PreferencesWhatsNewSeen(node)

    @Test
    fun `with no value, each version is new`() {
        assertNull(seen.lastShown())
        assertTrue(seen.isNew("1.4.0"))
    }

    @Test
    fun `after a popup, the same and older versions are not new, and a newer version is new`() {
        seen.markShown("1.4.0")

        assertEquals("1.4.0", PreferencesWhatsNewSeen(node).lastShown())
        assertFalse(seen.isNew("1.4.0"))
        assertFalse(seen.isNew("1.3.2"))
        assertTrue(seen.isNew("1.4.1"))
    }
}
