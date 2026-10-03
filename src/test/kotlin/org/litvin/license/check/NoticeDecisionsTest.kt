package org.litvin.license.check

import org.litvin.license.check.ExpirySimulator.Companion.rule
import org.litvin.license.check.ExpirySimulator.Companion.rulesFile
import org.litvin.license.check.ExpirySimulator.Network
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E6-S7. The build expires on 2027-04-01, so the warning starts on 2027-03-02 at 00:00 UTC. */
class NoticeDecisionsTest {
    private val warningStart = Instant.parse("2027-03-02T00:00:00Z")

    @Test
    fun `the update notice shows only when latest_version is newer than the app version`() {
        for ((latest, shows) in listOf("1.3.0" to true, "1.2.0" to false, "1.1.9" to false)) {
            simulate {
                serverFile = rulesFile(latest = latest, notes = "Faster export")
                start()
                assertEquals(shows, state.updateNotice != null, latest)
            }
        }
    }

    @Test
    fun `a development build 1_4_0-SNAPSHOT shows no update notice for 1_4_0`() = simulate(ExpirySimulator(appVersion = "1.4.0-SNAPSHOT")) {
        serverFile = rulesFile(latest = "1.4.0")
        start()

        assertNull(state.updateNotice)
    }

    @Test
    fun `Later hides the notice until the app closes, and a newer version shows it again in the same session`() = simulate {
        serverFile = rulesFile(latest = "1.3.0")
        start()

        controller.laterUpdateNotice()
        assertNull(state.updateNotice)
        advance(hours(24))
        assertNull(state.updateNotice, "Later is kept in memory for this version")

        serverFile = rulesFile(latest = "1.4.0")
        advance(hours(24))
        assertEquals("1.4.0", state.updateNotice?.version)

        controller.laterUpdateNotice()
        close()
        start()
        assertEquals("1.4.0", state.updateNotice?.version, "The next start shows it again")
    }

    @Test
    fun `at a start with no connection the update notice shows from the saved file`() = simulate {
        serverFile = rulesFile(latest = "1.3.0")
        start()
        close()
        network = Network.OFFLINE

        start()

        assertEquals("1.3.0", state.updateNotice?.version)
    }

    @Test
    fun `the warning starts 30 days before the expiry and shows at start`() {
        simulate(ExpirySimulator(startTime = warningStart - minutes(1))) {
            start()
            assertFalse(state.expiryWarning)
        }
        simulate(ExpirySimulator(startTime = warningStart)) {
            start()
            assertTrue(state.expiryWarning)
        }
    }

    @Test
    fun `a session from 23_00 to 02_00 local time shows the warning at start and again before 01_00`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 23:00 in Berlin (UTC+1) on 10 March 2027.
        simulate(ExpirySimulator(startTime = Instant.parse("2027-03-10T22:00:00Z"), zone = berlin)) {
            start()
            assertTrue(state.expiryWarning)
            controller.closeExpiryWarning()

            advance(minutes(59))
            assertFalse(state.expiryWarning, "The same calendar day")
            advance(minutes(1))

            assertTrue(state.expiryWarning)
            assertTrue(realTime.atZone(berlin).hour < 1, "Shown at ${realTime.atZone(berlin)}")
        }
    }

    @Test
    fun `a closed warning stays hidden for the rest of the calendar day and shows again on the next one`() =
        simulate(ExpirySimulator(startTime = Instant.parse("2027-03-10T10:00:00Z"))) {
            start()
            controller.closeExpiryWarning()

            advance(hours(13) + minutes(59))
            assertFalse(state.expiryWarning)
            advance(minutes(1))

            assertTrue(state.expiryWarning)
        }

    @Test
    fun `a new rule that makes the expiry earlier than 30 days from now shows the warning at once, also after a close`() =
        simulate(ExpirySimulator(startTime = Instant.parse("2027-03-10T10:00:00Z"))) {
            start()
            controller.closeExpiryWarning()

            serverFile = rulesFile(rules = arrayOf(rule("early", "1.2.0", LocalDate.of(2027, 3, 20), "Security problem")))
            checkNow()

            assertTrue(state.expiryWarning)
            assertEquals("Security problem", state.expiry.message)
        }

    @Test
    fun `a new rule with a stopsOn 90 days from now shows no warning`() = simulate {
        start()

        serverFile = rulesFile(rules = arrayOf(rule("later", "1.2.0", LocalDate.of(2027, 1, 31))))
        checkNow()

        assertEquals(LocalDate.of(2027, 1, 31), state.expiry.date)
        assertFalse(state.expiryWarning)
    }

    @Test
    fun `a rule that the app already saw in this session does not show the warning again`() = simulate {
        serverFile = rulesFile(rules = arrayOf(rule("soon", "1.2.0", LocalDate.of(2026, 11, 20))))
        start()
        assertTrue(state.expiryWarning)
        controller.closeExpiryWarning()

        checkNow()

        assertFalse(state.expiryWarning)
    }

    @Test
    fun `the Clock behind notice shows at start in normal mode, Check now shows the result, and a server time closes it`() = simulate {
        setClock("2025-01-01T00:00:00Z")
        network = Network.OFFLINE
        start()
        assertTrue(state.clockBehindNotice)
        assertEquals(CheckState.NO_CONNECTION, state.check)

        network = Network.ONLINE
        checkNow()

        assertFalse(state.clockBehindNotice)
        assertTrue(state.wrongClockDays!! > 600)
    }

    @Test
    fun `the user can close the Clock behind notice for the session`() = simulate {
        setClock("2025-01-01T00:00:00Z")
        network = Network.OFFLINE
        start()

        controller.closeClockBehindNotice()
        advance(hours(2))

        assertFalse(state.clockBehindNotice)
    }

    @Test
    fun `in expired mode the Clock behind notice does not show, but the 12 hours still apply`() = simulate {
        val saved = Instant.parse("2027-04-02T00:00:00Z")
        savedTime.value = saved
        flag.value = true
        setClock("2025-01-01T00:00:00Z")
        network = Network.OFFLINE

        start()

        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertFalse(state.clockBehindNotice)
        assertEquals(saved + hours(12), state.currentTime)
    }

    @Test
    fun `a system time 25 hours from the server time shows the wrong clock notice one time in each session`() = simulate {
        clockOffset = hours(25)
        start()
        assertEquals(1L, state.wrongClockDays)

        controller.closeWrongClockNotice()
        checkNow()

        assertNull(state.wrongClock)
    }

    @Test
    fun `a system time 23 hours from the server time shows no wrong clock notice`() = simulate {
        clockOffset = hours(23)
        start()

        assertNull(state.wrongClock)
    }
}
