package org.litvin.license.check

import org.litvin.license.ExpiryMomentFormat
import org.litvin.license.check.ExpiryController.StartPath
import org.litvin.license.check.ExpirySimulator.Companion.rule
import org.litvin.license.check.ExpirySimulator.Companion.rulesFile
import org.litvin.license.check.ExpirySimulator.Network
import org.litvin.license.update.UpdateOptions
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E6-S8: one simulator test for each scenario of `expiry-scenarios.md`. Each test checks the "Expected result" for
 * the parts that the controller decides. The parts that only the user interface can show are in E8-S9.
 *
 * The default build date is 2026-10-01, so the build expires on 2027-04-01 at 00:00 UTC. The app version is 1.2.0.
 */
class ExpiryScenariosTest {
    private val expiryMoment = Instant.parse("2027-04-01T00:00:00Z")

    @Test
    fun `S-01 warning before the expiry, then an update`() =
        simulate(ExpirySimulator(startTime = expiryMoment - days(30) - minutes(1))) {
            start()
            assertFalse(state.expiryWarning)

            advance(minutes(1))

            // The warning starts exactly 30 x 24 hours before the expiry moment, with the local time and the hour.
            assertTrue(state.expiryWarning)
            assertEquals(expiryMoment - days(30), state.expiry.warningStart)
            assertEquals("31 March 2027, 17:00", ExpiryMomentFormat.format(state.expiry.moment, ZoneId.of("America/Los_Angeles")))
            assertTrue(UpdateOptions.of(appVersion, state.rules).showsUpdateAndRestart)
        }

    @Test
    fun `S-02 update notice for a new release`() = simulate {
        serverFile = rulesFile(latest = "1.3.0", notes = "Faster export")

        start()

        assertEquals("1.3.0", state.updateNotice?.version)
        assertEquals("Faster export", state.updateNotice?.notes)
        controller.laterUpdateNotice()
        assertNull(state.updateNotice)
        close()
        network = Network.OFFLINE
        start()
        assertEquals("1.3.0", state.updateNotice?.version, "The next start shows it from the saved file")

        // After "Update and restart", version 1.3.0 runs and shows no notice.
        close()
        val updated = install(buildDate = LocalDate.of(2026, 12, 1), appVersion = "1.3.0")
        updated.start()
        assertNull(updated.state.updateNotice)
    }

    @Test
    fun `S-03 expiry during a session, with exports in the queue`() =
        simulate(ExpirySimulator(startTime = expiryMoment - minutes(10))) {
            start()
            assertTrue(controller.allowsNewWork())

            advance(minutes(10))

            assertEquals(ExpiryMode.EXPIRED, state.mode)
            assertTrue(events.indexOf(ExpirySimulator.BEFORE_EXPIRED_MODE) >= 0, "The project is saved")
            assertTrue(state.expiredDialog)
            assertFalse(controller.allowsNewWork(), "No new export and no project")
            controller.closeExpiredDialog()
            controller.showExpiredDialog()
            assertTrue(state.expiredDialog, "A click on the start button shows the dialog again")

            close()
            assertEquals(StartPath.EXPIRED, start(), "Path B at the next start")
        }

    @Test
    fun `S-04 expiry while the app is closed`() = simulate {
        start()
        close()
        wait(days(200))
        val checksBefore = fetches.size

        assertEquals(StartPath.DATE_CHECK, start())

        assertEquals(1, fetches.size - checksBefore, "One online check")
        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals(CheckState.STILL_EXPIRED, state.check)
        assertTrue(state.expiredDialog)
        assertTrue(flag.value)
        assertFalse(controller.allowsNewWork(), "No project opens")
    }

    @Test
    fun `S-05 update from expired mode`() = simulate {
        wait(days(200))
        network = Network.OFFLINE
        start()
        assertEquals(ExpiryMode.EXPIRED, state.mode)
        val options = UpdateOptions.of(appVersion, state.rules)
        assertEquals(UpdateOptions.STABLE_INSTALLER_URL, options.installerUrl, "No saved file: the stable URL")
        assertEquals(UpdateOptions.RELEASES_PAGE_URL, options.downloadPageUrl)
        close()

        val updated = install(buildDate = realTime.atZone(ZoneOffset.UTC).toLocalDate(), appVersion = "1.3.0")
        assertEquals(StartPath.NORMAL, updated.start())

        assertEquals(ExpiryMode.NORMAL, updated.state.mode)
        assertFalse(flag.value, "The new build clears the flag")
    }

    @Test
    fun `S-06 clock too far forward, offline`() = simulate {
        start()
        close()
        network = Network.OFFLINE
        setClock("2028-03-03T10:00:00Z")

        start()

        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals(CheckState.NO_CONNECTION, state.check)
        assertEquals(systemTime, state.currentTime, "The dialog shows the date used: the clock of the computer")

        // Connect, but do not correct the clock: recovery within 1 minute.
        network = Network.ONLINE
        advance(minutes(1))
        assertEquals(ExpiryMode.NORMAL, state.mode)
        assertNotNull(state.wrongClock)
        assertEquals(realTime, savedTime.value, "The registry gets the corrected value")

        // Correct the clock: a later start with no network uses the corrected saved time.
        close()
        clockOffset = hours(0)
        network = Network.OFFLINE
        assertEquals(StartPath.NORMAL, start())
    }

    @Test
    fun `S-07 clock too far forward, online`() = simulate {
        setClock("2028-03-03T10:00:00Z")

        assertEquals(StartPath.DATE_CHECK, start())

        assertFalse(ExpiryMode.EXPIRED in modes(), "No flash of expired mode")
        assertEquals(ExpiryMode.NORMAL, state.mode)
        assertEquals(487L, state.wrongClockDays)
    }

    @Test
    fun `S-08 clock behind, offline (flat clock battery)`() = simulate {
        start()
        close()
        val saved = savedTime.value!!
        network = Network.OFFLINE
        setClock("2020-01-01T00:00:00Z")

        start()

        assertEquals(saved + hours(12), state.currentTime)
        assertTrue(state.clockBehindNotice)
        assertTrue(controller.allowsNewWork(), "The user can work")

        network = Network.ONLINE
        advance(minutes(1))
        assertFalse(state.clockBehindNotice, "Recovery within 1 minute")
        assertNotNull(state.wrongClock)
    }

    @Test
    fun `S-09 new device with a clock earlier than the build date`() = simulate {
        network = Network.OFFLINE
        setClock("2026-09-01T00:00:00Z")

        start()

        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), state.currentTime, "The build moment plus 12 hours")
        assertTrue(state.clockBehindNotice)
    }

    @Test
    fun `S-10 always offline, with a correct clock`() = simulate {
        network = Network.OFFLINE
        start()
        close()
        wait(Duration.between(realTime, expiryMoment - days(1)))

        start()
        assertEquals(ExpiryMode.NORMAL, state.mode, "No maximum offline period")
        assertTrue(state.expiryWarning)
        assertNull(state.updateNotice)

        advance(days(1))
        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals(CheckState.NO_CONNECTION, state.check)
    }

    @Test
    fun `S-11 firewall or blocked region`() = simulate {
        network = Network.BLACKHOLE
        val modesDuringFetch = mutableListOf<ExpiryMode>()
        duringFetch = { modesDuringFetch += controller.state.mode }

        assertEquals(StartPath.NORMAL, start())
        assertEquals(listOf(ExpiryMode.NORMAL), modesDuringFetch, "A normal start does not wait for the check")

        close()
        wait(days(200))
        val before = counter
        start()
        assertEquals(15_000L, counter - before, "The start waits a maximum of 15 seconds")
        assertEquals(CheckState.NO_CONNECTION, state.check)
    }

    /** The trust of the Windows store is in `UpdateTrustTest`. Here the inspected network is a normal network. */
    @Test
    fun `S-12 office with TLS inspection`() = simulate {
        serverFile = rulesFile(latest = "1.3.0")
        clockOffset = days(3)

        start()

        assertEquals("1.3.0", state.updateNotice?.version, "The rules and the update notice")
        assertEquals(realTime, state.currentTime, "The correction of the clock")
    }

    @Test
    fun `S-13 rule with a future stop date`() = simulate {
        start()
        val stopsOn = LocalDate.of(2026, 11, 22)
        serverFile = rulesFile(rules = arrayOf(rule("2026-11-bug", "1.2.0", stopsOn, "Update to fix a bug.")))

        advance(hours(24))

        assertEquals(stopsOn, state.expiry.date)
        assertTrue(state.expiryWarning)
        assertEquals("Update to fix a bug.", state.expiry.message)
        assertTrue(logLines.any { "2026-11-bug" in it && stopsOn.toString() in it }, "Log: $logLines")
    }

    @Test
    fun `S-14 rule that stops the version at once`() = simulate {
        start()
        serverFile = rulesFile(rules = arrayOf(rule("damage", "1.2.0", LocalDate.of(2026, 10, 15), "Damages projects.")))

        advance(hours(24))

        assertEquals(ExpiryMode.EXPIRED, state.mode, "During a session: at once")
        assertFalse(state.expiryWarning, "No warning")
        assertEquals("Damages projects.", state.expiry.message)
        assertTrue(ExpirySimulator.BEFORE_EXPIRED_MODE in events, "The project is saved")

        close()
        flag.value = false
        assertEquals(StartPath.DATE_CHECK, start())
        assertEquals(CheckState.STILL_EXPIRED, state.check, "At start: path A gives Still expired")
    }

    @Test
    fun `S-15 wrong rule, then the fix of the author`() = simulate {
        val wrongFile = rulesFile(rules = arrayOf(rule("wrong", "1.2.0", LocalDate.of(2026, 10, 15))))
        start()
        serverFile = wrongFile
        checkNow()
        assertEquals(ExpiryMode.EXPIRED, state.mode)

        // An open app: the fix arrives, and the next check after 15 minutes leaves expired mode.
        serverFile = rulesFile()
        advance(minutes(15))
        assertEquals(ExpiryMode.NORMAL, state.mode)

        // An app that read the wrong rule and closed: path B gives Not expired at once.
        serverFile = wrongFile
        checkNow()
        close()
        serverFile = rulesFile()
        assertEquals(StartPath.EXPIRED, start())
        assertEquals(ExpiryMode.NORMAL, state.mode)

        // An app that saved the wrong file and is offline: recovery within 1 minute after it connects.
        serverFile = wrongFile
        checkNow()
        close()
        flag.value = false
        network = Network.OFFLINE
        start()
        assertEquals(ExpiryMode.EXPIRED, state.mode)
        serverFile = rulesFile()
        network = Network.ONLINE
        advance(minutes(1))
        assertEquals(ExpiryMode.NORMAL, state.mode)
    }

    @Test
    fun `S-16 sleep with the app open`() {
        simulate(ExpirySimulator(startTime = expiryMoment - days(2))) {
            start()
            sleep(days(3))
            assertEquals(ExpiryMode.EXPIRED, state.mode, "With a server time: at once after the wake")
        }
        simulate(ExpirySimulator(startTime = expiryMoment - days(2))) {
            network = Network.OFFLINE
            start()
            val checks = fetches.size
            sleep(days(3))
            assertEquals(ExpiryMode.EXPIRED, state.mode, "With no server time: after one online check")
            assertTrue(fetches.size > checks)
        }
    }

    /**
     * The instance lock is in `InstanceLockTest`, and the order in `main` is in `StartOrderSourceTest`. A second
     * process that does not get the lock never calls [ExpiryController.start]. Only `start` adds the 12 hours.
     */
    @Test
    fun `S-17 second start while the app runs`() = simulate {
        network = Network.OFFLINE
        setClock("2025-01-01T00:00:00Z")
        start()
        val savedByFirst = savedTime.value
        val writes = savedTime.writes

        // The second process quits before the time steps: it makes no controller.

        assertEquals(savedByFirst, savedTime.value)
        assertEquals(writes, savedTime.writes)
        assertEquals(ExpiryMode.NORMAL, state.mode, "The first instance does not change")
    }

    @Test
    fun `S-18 reinstall, or install an old installer`() = simulate {
        start()
        advance(hours(2))
        close()
        wait(days(200))
        val saved = savedTime.value

        // The same build again: the saved time stays.
        val sameBuild = install(buildDate, appVersion)
        assertEquals(saved, sameBuild.savedTime.value)

        // An old installer: its expiry is earlier, so it opens in expired mode.
        val old = install(buildDate = LocalDate.of(2026, 6, 1), appVersion = "1.1.0")
        old.network = Network.OFFLINE
        old.start()
        assertEquals(ExpiryMode.EXPIRED, old.state.mode)
    }

    @Test
    fun `S-19 app closed with End task, or a crash`() = simulate {
        start()
        advance(minutes(9))
        val expiryBefore = state.expiry

        endTask()

        assertEquals(realTime - minutes(4), savedTime.value, "The last write was 5 minutes into the session")
        start()
        assertEquals(expiryBefore, state.expiry, "The expiry does not change")
        assertEquals(realTime, state.currentTime)
    }

    @Test
    fun `C-01 clock back, offline`() = simulate(ExpirySimulator(startTime = expiryMoment - days(20))) {
        start()
        close()
        network = Network.OFFLINE
        clockOffset = days(-365)

        // Two starts each day, 1.5 hours of work each.
        var day = 0
        while (day < 30) {
            day++
            var expired = false
            repeat(2) {
                start()
                expired = expired || state.mode == ExpiryMode.EXPIRED
                if (state.mode == ExpiryMode.NORMAL) assertTrue(state.clockBehindNotice)
                advance(minutes(90))
                close()
                wait(hours(10) + minutes(30))
            }
            if (expired) break
        }

        assertTrue(day <= 20, "The build expired after $day calendar days")
    }

    @Test
    fun `C-02 clock back at each start, with End task`() = simulate {
        network = Network.OFFLINE
        val falseDate = "2026-11-02T10:00:00Z"
        start()
        endTask()

        repeat(6) {
            setClock(falseDate)
            val before = savedTime.value!!
            start()
            advance(minutes(60))
            endTask()
            val counted = Duration.between(before, savedTime.value)
            assertTrue(counted >= minutes(55), "The session lost ${minutes(60) - counted}, at most 5 minutes")
        }
    }

    @Test
    fun `C-03 time tool for one program`() = simulate {
        wait(days(200))
        network = Network.OFFLINE
        javaClockOffset = days(-200)

        start()

        assertEquals(ExpiryMode.EXPIRED, state.mode)
        assertEquals(realTime, state.currentTime, "The file time shows the real time")
    }

    @Test
    fun `C-04 new device with an old installer, offline`() =
        simulate(ExpirySimulator(startTime = Instant.parse("2026-10-02T00:00:00Z"))) {
            network = Network.OFFLINE

            start()
            assertEquals(ExpiryMode.NORMAL, state.mode)
            assertFalse(state.clockBehindNotice)
            close()

            wait(Duration.between(realTime, expiryMoment - hours(1)))
            assertEquals(StartPath.NORMAL, start(), "The build works until the clock gets to its expiry")
            close()
            wait(hours(2))
            start()
            assertEquals(ExpiryMode.EXPIRED, state.mode, "A maximum of one build life")
        }

    @Test
    fun `C-05 new Windows user account with an old installer`() {
        val firstAccount = ExpirySimulator(startTime = expiryMoment + days(1))
        simulate(firstAccount) {
            network = Network.OFFLINE
            start()
            assertEquals(ExpiryMode.EXPIRED, state.mode)
        }

        // A new account has no saved time. With the clock one day after the build date, the build works again.
        simulate(ExpirySimulator(startTime = expiryMoment + days(1))) {
            network = Network.OFFLINE
            setClock("2026-10-02T00:00:00Z")
            start()
            assertEquals(ExpiryMode.NORMAL, state.mode, "Accepted limit: one build life for each new account")
        }
    }
}
