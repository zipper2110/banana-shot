package org.litvin.license

import org.litvin.AppInfo
import org.litvin.app.PreferencesProvider
import org.litvin.license.check.ExpiryController
import org.litvin.license.check.ExpiryScheduler
import org.litvin.license.check.PreferencesExpiredFlagStore
import org.litvin.license.check.ScheduledTask
import org.litvin.license.check.SystemRetryTiming
import org.litvin.license.online.RulesFetchResult
import org.litvin.license.online.RulesFetcher
import org.litvin.license.time.PreferencesSavedTimeStore
import org.litvin.license.time.RunTimeCounter
import org.litvin.license.time.SystemTime
import org.litvin.license.time.TimeEngine
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** A gate for the tests of the core functions that do not test the expiry. */
object AllowNewWork : NewWorkGate {
    override fun allowsNewWork(): Boolean = true
}

/**
 * Expiry controllers for the tests (E7-S3, E7-S4). They use fakes only: no read of GitHub, no rules URL, and no
 * preferences in `HKCU`.
 */
object TestExpiry {
    /** No network. The online check gives "no connection". */
    val OFFLINE = RulesFetcher { RulesFetchResult.NoConnection("test: no network") }

    /** A scheduler that never runs a task. Use it when the test does not start the controller. */
    val NO_TASKS = ExpiryScheduler { _, _ -> ScheduledTask { } }

    /** The run time counter of the test controllers. A fake fetcher uses it for `counterAtResponse`. */
    val COUNTER = RunTimeCounter { System.nanoTime() / 1_000_000 }

    /**
     * @param systemClock the system time that the controller reads. A UI-flow test can move it forward.
     * @param buildExpiry the build expiry. A date in the past gives an expired build.
     */
    fun controller(
        dataFolder: File,
        preferences: PreferencesProvider = InMemoryPreferencesProvider(),
        fetcher: RulesFetcher = OFFLINE,
        scheduler: ExpiryScheduler = NO_TASKS,
        systemClock: () -> Instant = Instant::now,
        buildExpiry: LocalDate = BuildExpiry.expiryDate(),
    ): ExpiryController {
        val node = preferences.node(PreferencesProvider.LICENSE)
        val counter = COUNTER
        return ExpiryController(
            appVersion = AppInfo.version,
            engine = TimeEngine(counter, SystemTime(probe = { null }, javaClock = systemClock), PreferencesSavedTimeStore(node)),
            counter = counter,
            savedRules = SavedVersionRules(dataFolder),
            fetcher = fetcher,
            flag = PreferencesExpiredFlagStore(node),
            scheduler = scheduler,
            retryTiming = SystemRetryTiming,
            buildExpiry = buildExpiry,
        )
    }
}
