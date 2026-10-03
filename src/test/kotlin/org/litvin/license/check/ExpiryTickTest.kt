package org.litvin.license.check

import org.litvin.license.check.ExpirySimulator.Network
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E6-S4 (minute tick and hourly check) and E6-S5 (expiry during a session). */
class ExpiryTickTest {
    private val expiryMoment = Instant.parse("2027-04-01T00:00:00Z")

    @Test
    fun `the tick finds the expiry within 1 minute, and with a server time expired mode comes with no online check`() =
        simulate(ExpirySimulator(startTime = expiryMoment - minutes(30))) {
            start()
            assertEquals(1, fetches.size)

            advance(minutes(29))
            assertEquals(ExpiryMode.NORMAL, state.mode)
            advance(minutes(1))

            assertEquals(ExpiryMode.EXPIRED, state.mode)
            assertEquals(1, fetches.size, "No online check")
            val beforeIndex = events.indexOf(ExpirySimulator.BEFORE_EXPIRED_MODE)
            val expiredIndex = events.indexOfFirst { it is ExpiryState && it.mode == ExpiryMode.EXPIRED }
            assertTrue(beforeIndex in 0 until expiredIndex, "The project is saved before expired mode")
        }

    @Test
    fun `the tick shows the warning within 1 minute when the warning starts`() =
        simulate(ExpirySimulator(startTime = expiryMoment - days(30) - minutes(30))) {
            start()
            assertFalse(state.expiryWarning)

            advance(minutes(30))

            assertTrue(state.expiryWarning)
        }

    @Test
    fun `the first tick after a long sleep does the check`() = simulate(ExpirySimulator(startTime = expiryMoment - days(2))) {
        start()

        sleep(days(3))

        assertEquals(ExpiryMode.EXPIRED, state.mode)
    }

    @Test
    fun `with no event the check runs when 1 hour of run time has passed since the last check`() = simulate {
        start()
        val reads = probeReads

        advance(minutes(59))
        assertEquals(reads, probeReads, "The tick does not use the file time probe")
        advance(minutes(1))

        assertEquals(reads + 1, probeReads)
    }

    @Test
    fun `the tick writes the saved time after 5 minutes of run time`() = simulate {
        start()
        val writes = savedTime.writes

        advance(minutes(4))
        assertEquals(writes, savedTime.writes)
        advance(minutes(1))

        assertEquals(writes + 1, savedTime.writes)
        assertEquals(realTime, savedTime.value)
    }

    @Test
    fun `with no server time an expiry in a session gets one online check with retries, and the app stays in normal mode during it`() =
        simulate(ExpirySimulator(startTime = expiryMoment - minutes(10))) {
            network = Network.OFFLINE
            start()
            val fetchesAtStart = fetches.size
            val modesDuringCheck = mutableListOf<ExpiryMode>()
            val allowedDuringCheck = mutableListOf<Boolean>()
            duringFetch = {
                modesDuringCheck += controller.state.mode
                allowedDuringCheck += controller.allowsNewWork()
            }

            advance(minutes(10))

            assertEquals(3, fetches.size - fetchesAtStart, "3 attempts")
            assertEquals(listOf(ExpiryMode.NORMAL), modesDuringCheck.distinct())
            assertEquals(listOf(true), allowedDuringCheck.distinct(), "The core functions do not refuse and do not wait")
            assertEquals(ExpiryMode.EXPIRED, state.mode)
            assertEquals(CheckState.NO_CONNECTION, state.check)
            assertTrue(ExpirySimulator.BEFORE_EXPIRED_MODE in events)
        }

    @Test
    fun `with no server time an online check that gives Not expired keeps normal mode`() = simulate {
        network = Network.OFFLINE
        start()

        // The clock moves forward by mistake. Then the network comes back.
        setClock("2028-03-03T10:00:00Z")
        network = Network.ONLINE
        advance(minutes(1))

        assertEquals(ExpiryMode.NORMAL, state.mode)
        assertFalse(ExpirySimulator.BEFORE_EXPIRED_MODE in events)
        assertEquals(realTime, state.currentTime, "The server time corrects the time")
        assertTrue(state.wrongClockDays!! > 400)
    }

    @Test
    fun `the core functions refuse in expired mode`() = simulate(ExpirySimulator(startTime = expiryMoment - minutes(1))) {
        start()
        assertTrue(controller.allowsNewWork())

        advance(minutes(1))

        assertFalse(controller.allowsNewWork())
        assertNull(state.updateNotice)
    }
}
