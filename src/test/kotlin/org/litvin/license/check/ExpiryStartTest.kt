package org.litvin.license.check

import org.litvin.license.check.ExpiryController.StartPath
import org.litvin.license.check.ExpirySimulator.Network
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E6-S2 (start sequence) and E6-S3 (start of a build that looks expired). */
class ExpiryStartTest {
    @Test
    fun `a start in normal mode starts an online check in the background and clears the flag`() = simulate {
        flag.value = true

        assertEquals(StartPath.NORMAL, start())

        assertEquals(ExpiryMode.NORMAL, state.mode)
        assertFalse(flag.value)
        assertEquals(1, fetches.size)
        assertEquals(realTime, savedTime.value, "The start writes the saved time")
    }

    @Test
    fun `the start does the expiry check with the saved rules file before the online check`() = simulate {
        serverFile = ExpirySimulator.rulesFile(rules = arrayOf(ExpirySimulator.rule("stop", "1.2.0", realTime.toDate())))
        start()
        close()
        // The next start has no network and no flag: the saved file stops the version, and the start uses path A.
        network = Network.OFFLINE
        flag.value = false

        assertEquals(StartPath.DATE_CHECK, start())

        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals("stop", state.expiry.rule?.id)
    }

    @Test
    fun `path A shows the date check before the main window, and Still expired gives expired mode`() = simulate {
        wait(days(200))
        val modesDuringFetch = mutableListOf<ExpiryMode>()
        duringFetch = { modesDuringFetch += controller.state.mode }

        assertEquals(StartPath.DATE_CHECK, start())

        assertEquals(listOf(ExpiryMode.CHECKING_DATE), modesDuringFetch)
        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals(CheckState.STILL_EXPIRED, state.check)
        assertTrue(state.expiredDialog)
        assertTrue(flag.value, "Going to expired mode sets the flag")
    }

    @Test
    fun `path A with no connection makes 3 attempts 2 seconds apart and gives expired mode with that result`() = simulate {
        wait(days(200))
        network = Network.OFFLINE
        val startCounter = counter

        start()

        assertEquals(listOf(0L, 2000L, 4000L), fetches.take(3).map { it - startCounter })
        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals(CheckState.NO_CONNECTION, state.check)
    }

    @Test
    fun `path A stops after 15 seconds in total, also when an attempt still waits`() = simulate {
        wait(days(200))
        network = Network.BLACKHOLE
        val startCounter = counter

        start()

        // Attempt 1 waits 10 seconds, the pause is 2 seconds, and attempt 2 stops at the 15-second limit.
        assertEquals(listOf(0L, 12_000L), fetches.take(2).map { it - startCounter })
        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals(CheckState.NO_CONNECTION, state.check)
        assertEquals(ExpiryMode.EXPIRED, modes().last())
        assertTrue(fetches.size == 2 || fetches[2] - startCounter >= 15_000L, "No third attempt in the 15 seconds")
    }

    @Test
    fun `path A with Not expired opens the normal main window`() = simulate {
        setClock("2028-03-03T10:00:00Z")

        assertEquals(StartPath.DATE_CHECK, start())

        assertEquals(listOf(ExpiryMode.CHECKING_DATE, ExpiryMode.NORMAL), modes(), "No expired mode shows")
        assertFalse(flag.value)
        assertEquals(487L, state.wrongClockDays, "The wrong clock notice shows")
    }

    @Test
    fun `path B opens expired mode at once with Checking, and Not expired then leaves expired mode`() = simulate {
        flag.value = true
        setClock("2028-03-03T10:00:00Z")
        val firstStates = mutableListOf<ExpiryState>()
        duringFetch = { firstStates += controller.state }

        assertEquals(StartPath.EXPIRED, start())

        val beforeCheck = firstStates.single()
        assertEquals(ExpiryMode.EXPIRED, beforeCheck.mode)
        assertEquals(CheckState.CHECKING, beforeCheck.check)
        assertTrue(beforeCheck.expiredDialog)
        assertEquals(ExpiryMode.NORMAL, state.mode)
        assertFalse(state.expiredDialog)
        assertFalse(flag.value, "Leaving expired mode clears the flag")
    }

    @Test
    fun `path B with no connection keeps expired mode with the connection text`() = simulate {
        wait(days(200))
        flag.value = true
        network = Network.OFFLINE

        assertEquals(StartPath.EXPIRED, start())

        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals(CheckState.NO_CONNECTION, state.check)
        assertEquals(1, fetches.size)
    }

    @Test
    fun `a deleted flag gives path A and the same final result`() {
        val results = listOf(true, false).map { flagSet ->
            val simulator = ExpirySimulator()
            simulate(simulator) {
                wait(days(200))
                flag.value = flagSet
                start()
            }
            simulator.state.copy(check = CheckState.NONE)
        }
        assertEquals(results[0], results[1])
    }

    @Test
    fun `a start in path A or path B shows no project work before the check`() = simulate {
        wait(days(200))
        var allowedDuringCheck: Boolean? = null
        duringFetch = { allowedDuringCheck = controller.allowsNewWork() }

        start()

        assertEquals(false, allowedDuringCheck)
        assertFalse(controller.allowsNewWork())
        assertNull(state.updateNotice)
    }

    @Test
    fun `the saved time is read with the build-date floor and Clock behind before the expiry check`() = simulate {
        setClock("2025-01-01T00:00:00Z")
        network = Network.OFFLINE

        start()

        val floor = Instant.parse("2026-10-01T00:00:00Z")
        assertEquals(floor + hours(12), state.currentTime)
        assertTrue(state.clockBehindNotice)
    }

    private fun Instant.toDate() = atZone(java.time.ZoneOffset.UTC).toLocalDate()
}
