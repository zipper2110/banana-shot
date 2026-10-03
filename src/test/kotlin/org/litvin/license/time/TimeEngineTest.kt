package org.litvin.license.time

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeEngineTest {
    private val buildMoment = Instant.parse("2026-10-01T00:00:00Z")
    private val day = Duration.ofDays(1)
    private val hour = Duration.ofHours(1)
    private val minute = Duration.ofMinutes(1)

    private class FakeCounter(var value: Long = 1_000_000L) : RunTimeCounter {
        override fun millis(): Long = value
        fun advance(duration: Duration) { value += duration.toMillis() }
    }

    private class FakeStore(var value: Instant? = null) : SavedTimeStore {
        val writes = mutableListOf<Instant>()
        override fun read(): Instant? = value
        override fun write(time: Instant) { value = time; writes += time }
    }

    /** The Java clock and the file time of one computer. Both move with [advance]. */
    private inner class FakeComputer(var javaTime: Instant) {
        var fileTime: Instant? = null
        val counter = FakeCounter()

        fun advance(duration: Duration) {
            javaTime += duration
            fileTime = fileTime?.plus(duration)
            counter.advance(duration)
        }

        /** A sleep: the counter and the clocks move, but no tick runs. */
        fun sleep(duration: Duration) = advance(duration)

        fun engine(store: SavedTimeStore) =
            TimeEngine(counter, SystemTime(probe = { fileTime }, javaClock = { javaTime }), store, buildMoment)
    }

    private fun Instant.days(count: Long) = plus(Duration.ofDays(count))

    // E3-S1 Run time counter

    @Test
    fun `a sleep moves the saved time forward also offline with the clock behind`() {
        val stored = buildMoment.days(30)
        val computer = FakeComputer(javaTime = stored.days(-10))
        val engine = computer.engine(FakeStore(stored))
        val atStart = engine.start()
        assertTrue(engine.clockBehind)

        computer.sleep(Duration.ofHours(8))

        assertEquals(atStart + Duration.ofHours(8), engine.tick())
    }

    @Test
    fun `a change of the system clock does not change the run time`() {
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(FakeStore())
        val atStart = engine.start()

        computer.counter.advance(hour)
        computer.javaTime = computer.javaTime.days(-3)

        assertEquals(atStart + hour, engine.tick())
    }

    // E3-S2 File time probe

    @Test
    fun `a file time later than the Java clock becomes the system time`() {
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        computer.fileTime = buildMoment.days(7)

        assertEquals(buildMoment.days(7), computer.engine(FakeStore()).start())
    }

    @Test
    fun `a Java clock later than the file time stays the system time`() {
        val computer = FakeComputer(javaTime = buildMoment.days(7))
        computer.fileTime = buildMoment.days(5)

        assertEquals(buildMoment.days(7), computer.engine(FakeStore()).start())
    }

    @Test
    fun `the tick does not use the probe and the expiry check does`() {
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(FakeStore())
        engine.start()
        computer.fileTime = buildMoment.days(9)

        assertEquals(buildMoment.days(5), engine.tick())
        assertEquals(buildMoment.days(9), engine.check())
    }

    // E3-S3 Saved time calculation

    @Test
    fun `no saved time becomes the build moment`() {
        val computer = FakeComputer(javaTime = buildMoment.minus(hour))

        assertEquals(buildMoment, computer.engine(FakeStore()).start())
    }

    @Test
    fun `a saved time earlier than the build moment becomes the build moment`() {
        val computer = FakeComputer(javaTime = buildMoment.minus(hour))

        assertEquals(buildMoment, computer.engine(FakeStore(buildMoment.days(-100))).start())
    }

    @Test
    fun `before a server time a later system time moves the saved time to the system time`() {
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(FakeStore(buildMoment.days(3)))
        assertEquals(buildMoment.days(5), engine.start())

        computer.javaTime = buildMoment.days(8)

        assertEquals(buildMoment.days(8), engine.tick())
    }

    @Test
    fun `a system time 10 days forward and then back keeps the saved time 10 days ahead`() {
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(FakeStore())
        engine.start()

        computer.javaTime = buildMoment.days(15)
        assertEquals(buildMoment.days(15), engine.tick())
        computer.advance(minute)
        computer.javaTime = buildMoment.days(5) + minute

        assertEquals(buildMoment.days(15) + minute, engine.tick())
        computer.advance(hour)
        assertEquals(buildMoment.days(15) + minute + hour, engine.tick())
    }

    @Test
    fun `a system clock that moves back does not stop the time`() {
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(FakeStore())
        val atStart = engine.start()

        computer.javaTime = buildMoment.days(1)
        computer.counter.advance(Duration.ofMinutes(30))

        assertEquals(atStart + Duration.ofMinutes(30), engine.tick())
    }

    // E3-S4 Clock behind

    @Test
    fun `a system time more than 24 hours before the saved time adds 12 hours at each start`() {
        val stored = buildMoment.days(30)
        val store = FakeStore(stored)
        val computer = FakeComputer(javaTime = stored - Duration.ofHours(25))

        val first = computer.engine(store)
        assertEquals(stored + Duration.ofHours(12), first.start())
        assertTrue(first.clockBehind)
        first.close()

        val second = computer.engine(store)
        assertEquals(stored + Duration.ofHours(24), second.start())
        assertTrue(second.clockBehind)
    }

    @Test
    fun `a start that ends with no close still uses the 12 hours`() {
        val stored = buildMoment.days(30)
        val store = FakeStore(stored)
        val computer = FakeComputer(javaTime = stored.days(-5))

        computer.engine(store).start()

        assertEquals(stored + Duration.ofHours(24), computer.engine(store).start())
    }

    @Test
    fun `a system time 23 hours before the saved time has no effect`() {
        val stored = buildMoment.days(30)
        val engine = FakeComputer(javaTime = stored - Duration.ofHours(23)).engine(FakeStore(stored))

        assertEquals(stored, engine.start())
        assertFalse(engine.clockBehind)
    }

    @Test
    fun `with no saved time and a system time 1 year before the build date the rule adds 12 hours`() {
        val engine = FakeComputer(javaTime = buildMoment.days(-365)).engine(FakeStore())

        assertEquals(buildMoment + Duration.ofHours(12), engine.start())
        assertTrue(engine.clockBehind)
    }

    @Test
    fun `a server time after clock behind sets the saved time and ends the clock behind state`() {
        val stored = buildMoment.days(30)
        val computer = FakeComputer(javaTime = stored.days(-5))
        val engine = computer.engine(FakeStore(stored))
        engine.start()

        engine.serverTime(stored.days(1))

        assertEquals(stored.days(1), engine.tick())
        assertFalse(engine.clockBehind)
    }

    // E3-S5 Server time

    @Test
    fun `a server time earlier than the saved time moves the saved time back`() {
        val computer = FakeComputer(javaTime = buildMoment.days(40))
        val engine = computer.engine(FakeStore())
        engine.start()

        engine.serverTime(buildMoment.days(10))

        assertEquals(buildMoment.days(10), engine.tick())
        assertTrue(engine.hasServerTime)
    }

    @Test
    fun `a server time later than the saved time moves the saved time forward`() {
        val computer = FakeComputer(javaTime = buildMoment.days(10))
        val engine = computer.engine(FakeStore())
        engine.start()

        engine.serverTime(buildMoment.days(12))

        assertEquals(buildMoment.days(12), engine.tick())
    }

    @Test
    fun `the anchor uses the counter value when the response arrived`() {
        val computer = FakeComputer(javaTime = buildMoment.days(10))
        val engine = computer.engine(FakeStore())
        engine.start()
        val counterAtResponse = computer.counter.value

        computer.counter.advance(Duration.ofSeconds(3))
        engine.serverTime(buildMoment.days(10), counterAtResponse)

        assertEquals(buildMoment.days(10) + Duration.ofSeconds(3), engine.tick())
    }

    @Test
    fun `after a server time the system time does not change the saved time`() {
        val computer = FakeComputer(javaTime = buildMoment.days(10))
        computer.fileTime = buildMoment.days(10)
        val engine = computer.engine(FakeStore())
        engine.start()
        engine.serverTime(buildMoment.days(10))

        computer.javaTime = buildMoment.days(2)
        assertEquals(buildMoment.days(10), engine.tick())

        computer.javaTime = buildMoment.days(100)
        computer.fileTime = buildMoment.days(100)
        computer.counter.advance(hour)
        assertEquals(buildMoment.days(10) + hour, engine.check())
    }

    @Test
    fun `the server time gives the clock error for the wrong clock notice`() {
        val computer = FakeComputer(javaTime = buildMoment.days(40))
        val engine = computer.engine(FakeStore())
        engine.start()

        assertEquals(Duration.ofDays(30), engine.serverTime(buildMoment.days(10)))
    }

    // E3-S6 Write the saved time

    @Test
    fun `the engine writes at start, at each expiry check, and when the app closes`() {
        val store = FakeStore()
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(store)

        engine.start()
        computer.advance(minute)
        engine.check()
        computer.advance(minute)
        engine.close()

        assertEquals(listOf(buildMoment.days(5), buildMoment.days(5) + minute, buildMoment.days(5) + minute.multipliedBy(2)), store.writes)
    }

    @Test
    fun `the tick writes after 5 minutes of run time`() {
        val store = FakeStore()
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(store)
        engine.start()

        repeat(4) {
            computer.advance(minute)
            engine.tick()
        }
        assertEquals(1, store.writes.size)

        computer.advance(minute)
        engine.tick()
        assertEquals(listOf(buildMoment.days(5), buildMoment.days(5) + minute.multipliedBy(5)), store.writes)
    }

    @Test
    fun `the first tick after a long sleep writes`() {
        val store = FakeStore()
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(store)
        engine.start()

        computer.sleep(Duration.ofHours(10))
        engine.tick()

        assertEquals(buildMoment.days(5) + Duration.ofHours(10), store.value)
    }

    @Test
    fun `a session that ends with no close loses at most 5 minutes of run time`() {
        val stored = buildMoment.days(30)
        val store = FakeStore(stored)
        val computer = FakeComputer(javaTime = stored.days(-5))
        val engine = computer.engine(store)
        val atStart = engine.start()

        repeat(127) {
            computer.advance(minute)
            engine.tick()
        }
        val trueTime = atStart + minute.multipliedBy(127)

        val stored127 = store.value!!
        assertTrue(Duration.between(stored127, trueTime) <= Duration.ofMinutes(5), "stored=$stored127, true=$trueTime")
    }

    @Test
    fun `a write with no new server time keeps the later stored value`() {
        val store = FakeStore()
        val computer = FakeComputer(javaTime = buildMoment.days(5))
        val engine = computer.engine(store)
        engine.start()

        store.value = buildMoment.days(20)
        engine.check()

        assertEquals(buildMoment.days(20), store.value)
    }

    @Test
    fun `the first write after a new server time replaces a later stored value`() {
        val store = FakeStore()
        val computer = FakeComputer(javaTime = buildMoment.days(40))
        val engine = computer.engine(store)
        engine.start()
        engine.serverTime(buildMoment.days(10))

        store.value = buildMoment.days(50)
        engine.check()
        assertEquals(buildMoment.days(10), store.value)

        store.value = buildMoment.days(50)
        engine.close()
        assertEquals(buildMoment.days(50), store.value)
    }

    @Test
    fun `after a server time correction a start with no network uses the corrected value`() {
        val store = FakeStore()
        val forward = FakeComputer(javaTime = buildMoment.days(40))
        val first = forward.engine(store)
        first.start()
        first.serverTime(buildMoment.days(10))
        first.close()

        val corrected = FakeComputer(javaTime = buildMoment.days(10) + hour)
        val second = corrected.engine(store)

        assertEquals(buildMoment.days(10) + hour, second.start())
        assertFalse(second.clockBehind)
    }

    @Test
    fun `the build moment is 00 00 UTC on the build date`() {
        assertEquals(
            Instant.parse("2026-10-02T00:00:00Z"),
            TimeEngine.buildMoment(java.time.LocalDate.of(2026, 10, 2)),
        )
    }
}
