package org.litvin.license.time

import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
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

    @Test
    fun `the first counter that works is used for the rest of the session`() {
        var firstCalls = 0
        var secondCalls = 0
        val counter = FirstWorkingCounter(
            listOf(
                { firstCalls++; throw UnsatisfiedLinkError("no kernel32") },
                { secondCalls++; 42L },
            ),
        )

        assertEquals(42L, counter.millis())
        assertEquals(42L, counter.millis())
        assertEquals(1, firstCalls)
        assertEquals(2, secondCalls)
    }

    @Test
    fun `with no counter that works the first failure goes to the caller`() {
        val first = UnsatisfiedLinkError("no kernel32")
        val second = UnsatisfiedLinkError("no libc")
        val counter = FirstWorkingCounter(listOf({ throw first }, { throw second }))

        val failure = assertFailsWith<UnsatisfiedLinkError> { counter.millis() }
        assertSame(first, failure)
        assertEquals(listOf<Throwable>(second), failure.suppressed.toList())
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `the macOS clock counts forward`() {
        val first = MacClock.monotonicMillis()
        Thread.sleep(20)
        val second = MacClock.monotonicMillis()

        assertTrue(first > 0)
        assertTrue(second - first >= 20, "change ${second - first} ms")
    }
}
