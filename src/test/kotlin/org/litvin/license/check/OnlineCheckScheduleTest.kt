package org.litvin.license.check

import org.litvin.license.check.ExpirySimulator.Network
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** E6-S6. Each test finds the time of the next online check after the first check. */
class OnlineCheckScheduleTest {
    /** Asserts that the next check comes exactly [interval] after the last check. */
    private fun ExpirySimulator.assertNextCheckAfter(interval: Duration) {
        val count = fetches.size
        advance(interval - minutes(1))
        assertEquals(count, fetches.size, "No check before $interval")
        advance(minutes(1))
        assertEquals(count + 1, fetches.size, "A check after $interval")
    }

    private fun ExpirySimulator.expiredWithFlag() {
        wait(days(200))
        flag.value = true
    }

    @Test
    fun `No connection in expired mode gives the next check after 1 minute`() = simulate {
        expiredWithFlag()
        network = Network.OFFLINE
        start()

        assertNextCheckAfter(minutes(1))
        assertNextCheckAfter(minutes(1))
    }

    @Test
    fun `No connection in Clock behind with no server time gives the next check after 1 minute`() = simulate {
        setClock("2025-01-01T00:00:00Z")
        network = Network.OFFLINE
        start()

        assertNextCheckAfter(minutes(1))
    }

    @Test
    fun `Still expired gives 15 minutes`() = simulate {
        expiredWithFlag()
        start()
        assertEquals(CheckState.STILL_EXPIRED, state.check)

        assertNextCheckAfter(minutes(15))
    }

    @Test
    fun `No connection in normal mode gives 15 minutes`() = simulate {
        network = Network.OFFLINE
        start()

        assertNextCheckAfter(minutes(15))
    }

    @Test
    fun `in normal mode a 404, a server error, a redirect, and a file that is not valid give 15 minutes`() {
        for (status in listOf(404, 500, 302)) {
            simulate {
                serverStatus = status
                start()
                assertNextCheckAfter(minutes(15))
            }
        }
        simulate {
            serverFile = "not json"
            start()
            assertNextCheckAfter(minutes(15))
        }
    }

    @Test
    fun `a valid file or a file with an unknown schema gives 24 hours`() {
        simulate {
            start()
            assertNextCheckAfter(hours(24))
        }
        simulate {
            serverFile = ExpirySimulator.rulesFile(schema = 2)
            start()
            assertNextCheckAfter(hours(24))
        }
    }

    @Test
    fun `Check now runs a check at once and starts the schedule again`() = simulate {
        start()
        advance(hours(1))

        checkNow()
        assertEquals(2, fetches.size)

        advance(hours(23))
        assertEquals(2, fetches.size, "The old schedule is gone")
        assertNextCheckAfter(hours(1))
    }

    @Test
    fun `only one check runs at a time, and a second Check now during a check has no effect`() = simulate {
        expiredWithFlag()
        network = Network.OFFLINE
        start()
        val checkStates = mutableListOf<CheckState>()
        duringFetch = {
            checkStates += controller.state.check
            controller.checkNow()
        }

        checkNow()

        assertEquals(2, fetches.size)
        assertEquals(listOf(CheckState.CHECKING), checkStates)
    }

    @Test
    fun `after each check the expiry check runs again`() = simulate {
        start()
        serverFile = ExpirySimulator.rulesFile(rules = arrayOf(ExpirySimulator.rule("stop", "1.2.0", buildDate)))

        advance(hours(24))

        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals("stop", state.expiry.rule?.id)
    }

    @Test
    fun `a scheduled check that fires late after a sleep runs at the first tick`() = simulate {
        network = Network.OFFLINE
        start()
        val count = fetches.size

        sleep(hours(2))

        assertEquals(count + 1, fetches.size)
    }
}
