package org.litvin.license.time

import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferencesSavedTimeStoreTest {
    private val node = InMemoryPreferencesProvider().node("license")
    private val store = PreferencesSavedTimeStore(node)

    @Test
    fun `no value gives no saved time`() {
        assertNull(store.read())
    }

    @Test
    fun `a written time is read back`() {
        val time = Instant.parse("2026-11-01T10:15:30.123Z")

        store.write(time)

        assertEquals(time, store.read())
        assertEquals(time.toEpochMilli(), node.getLong(PreferencesSavedTimeStore.KEY, 0))
    }

    @Test
    fun `a damaged value gives no saved time`() {
        node.put(PreferencesSavedTimeStore.KEY, "not a number")

        assertNull(store.read())
    }
}
