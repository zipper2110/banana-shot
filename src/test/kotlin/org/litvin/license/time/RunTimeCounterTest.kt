package org.litvin.license.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunTimeCounterTest {
    @Test
    fun `the counter uses the primary counter while it works`() {
        var primary = 5_000L
        val logs = mutableListOf<Throwable>()
        val counter = FallbackRunTimeCounter(primary = { primary }, fallback = { 99L }, log = { logs += it })

        assertEquals(5_000L, counter.millis())
        primary = 6_000L
        assertEquals(6_000L, counter.millis())
        assertEquals(emptyList(), logs)
    }

    @Test
    fun `a counter that does not load uses the fallback and writes one log line`() {
        var fallback = 100L
        val logs = mutableListOf<Throwable>()
        val counter = FallbackRunTimeCounter(
            primary = { throw UnsatisfiedLinkError("no kernel32") },
            fallback = { fallback },
            log = { logs += it },
        )

        val first = counter.millis()
        fallback = 350L
        val second = counter.millis()
        counter.millis()

        assertEquals(250L, second - first)
        assertEquals(1, logs.size)
    }

    @Test
    fun `a failure after some calls continues from the last value`() {
        var failing = false
        var fallback = 10L
        val counter = FallbackRunTimeCounter(
            primary = { if (failing) throw IllegalStateException("failed") else 8_000L },
            fallback = { fallback },
            log = {},
        )

        assertEquals(8_000L, counter.millis())
        failing = true
        assertEquals(8_000L, counter.millis())
        fallback = 70L
        assertEquals(8_060L, counter.millis())
    }

    @Test
    fun `the production counter counts forward`() {
        val counter = RunTimeCounter.production()

        val first = counter.millis()
        val second = counter.millis()

        assertTrue(second >= first)
    }
}
