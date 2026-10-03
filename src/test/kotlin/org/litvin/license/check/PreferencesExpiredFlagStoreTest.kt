package org.litvin.license.check

import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreferencesExpiredFlagStoreTest {
    private val node = InMemoryPreferencesProvider().node("license")
    private val store = PreferencesExpiredFlagStore(node)

    @Test
    fun `no value is a flag that is not set`() {
        assertFalse(store.isSet())
    }

    @Test
    fun `a set flag is read back, and a clear removes the value`() {
        store.set(true)
        assertTrue(store.isSet())

        store.set(false)

        assertFalse(store.isSet())
        assertNull(node.get(PreferencesExpiredFlagStore.KEY, null))
    }

    @Test
    fun `a damaged value is a flag that is not set`() {
        node.put(PreferencesExpiredFlagStore.KEY, "maybe")

        assertFalse(store.isSet())
    }
}
