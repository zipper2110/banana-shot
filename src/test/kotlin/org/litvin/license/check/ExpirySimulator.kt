package org.litvin.license.check

import org.litvin.license.ExpiryLog
import org.litvin.license.SavedVersionRules
import org.litvin.license.online.RulesFetchResult
import org.litvin.license.online.RulesFetcher
import org.litvin.license.time.RunTimeCounter
import org.litvin.license.time.SavedTimeStore
import org.litvin.license.time.SystemTime
import org.litvin.license.time.TimeEngine
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * The scenario simulator (E6-S1). It drives [ExpiryController] in virtual time: a fake wall clock, a fake run time
 * counter, a fake server, an in-memory store for the saved time and the flag, and a temporary data folder. Restarts
 * of the app and sleeps of the computer are steps. A test reads like a scenario of `expiry-scenarios.md`:
 *
 * ```
 * simulate {
 *     network = Network.OFFLINE        // starting state
 *     setClock("2028-03-03T10:00:00Z")
 *     start()                          // steps
 *     assertEquals(ExpiryMode.EXPIRED, state.mode)   // expected result
 * }
 * ```
 *
 * The world has a real time (the time of the server) and a system clock (the clock of the computer). Each part of
 * the app gets its time from the world. No part reads the real clock, the network, or the registry.
 */
class ExpirySimulator(
    val buildDate: LocalDate = LocalDate.of(2026, 10, 1),
    val appVersion: String = "1.2.0",
    startTime: Instant = Instant.parse("2026-11-02T10:00:00Z"),
    val zone: ZoneId = ZoneOffset.UTC,
    // The storage that stays between sessions and installs: the preferences and the data folder.
    val savedTime: MemorySavedTimeStore = MemorySavedTimeStore(),
    val flag: MemoryFlagStore = MemoryFlagStore(),
    val dataFolder: File = Files.createTempDirectory("expiry-simulator").toFile().apply { deleteOnExit() },
) {
    enum class Network {
        ONLINE,

        /** No network: each attempt fails at once. */
        OFFLINE,

        /** A firewall that drops the connection: each attempt waits for the 10-second limit. */
        BLACKHOLE,
    }

    val buildExpiry: LocalDate = buildDate.plusMonths(6)

    /** The moment when the build expires: 00:00 UTC on [buildExpiry]. */
    val buildExpiryMoment: Instant = buildExpiry.atStartOfDay(ZoneOffset.UTC).toInstant()

    /** The real time. The server gives it. */
    var realTime: Instant = startTime
        private set

    /** The system clock of Windows minus the real time. */
    var clockOffset: Duration = Duration.ZERO

    /** A tool that changes the time only for the app (C-03): the Java clock minus the system clock. */
    var javaClockOffset: Duration = Duration.ZERO

    /** The run time counter (`GetTickCount64`). It counts the sleep. */
    var counter: Long = Duration.ofHours(1).toMillis()
        private set

    var network = Network.ONLINE
    var serverStatus = 200
    var serverFile: String = rulesFile()

    /** The `Age` header of the server, in seconds. */
    var serverAge: Long = 0

    /** The lines of [ExpiryLog]. */
    val logLines = mutableListOf<String>()

    /** The counter values of the reads of the rules file. */
    val fetches = mutableListOf<Long>()

    /** The number of expiry checks with the file time probe (each start and each expiry check). */
    var probeReads = 0
        private set

    /** Runs in each read of the rules file, before the response. A test can act during the check. */
    var duringFetch: (ExpirySimulator) -> Unit = {}

    /** The events of the listener in order: `before expired mode` and the states. */
    val events = mutableListOf<Any>()

    private val scheduler = VirtualScheduler()
    private var fetchLimit: Duration? = null
    private var session: ExpiryController? = null

    val controller: ExpiryController get() = checkNotNull(session) { "The app is not running" }
    val state: ExpiryState get() = controller.state
    val systemTime: Instant get() = realTime + clockOffset

    /** Sets the system clock. */
    fun setClock(time: String) {
        clockOffset = Duration.between(realTime, Instant.parse(time))
    }

    /** Starts the app. Path A runs its check at once, as on the start thread. Returns the start path. */
    fun start(): ExpiryController.StartPath {
        check(session == null) { "The app is already running" }
        scheduler.clear()
        val engine = TimeEngine(
            counter = { counter },
            systemTime = SystemTime(probe = { probeReads++; systemTime }, javaClock = { systemTime + javaClockOffset }),
            store = savedTime,
            buildMoment = TimeEngine.buildMoment(buildDate),
        )
        val controller = ExpiryController(
            appVersion = appVersion,
            engine = engine,
            counter = RunTimeCounter { counter },
            savedRules = SavedVersionRules(dataFolder),
            fetcher = fetcher,
            flag = flag,
            scheduler = scheduler,
            retryTiming = retryTiming,
            buildExpiry = buildExpiry,
            zone = { zone },
            expiryLog = ExpiryLog { logLines += it },
        )
        controller.addListener(object : ExpiryListener {
            override fun beforeExpiredMode() {
                events += BEFORE_EXPIRED_MODE
            }

            override fun stateChanged(state: ExpiryState) {
                events += state
            }
        })
        session = controller
        val path = controller.start()
        if (path == ExpiryController.StartPath.DATE_CHECK) controller.runDateCheck()
        runDue()
        return path
    }

    /** The app is open, and [duration] passes. The scheduled tasks run at their time. */
    fun advance(duration: Duration) {
        runDue(counter + duration.toMillis())
    }

    /** The computer sleeps for [duration]. No task runs during the sleep. The late tasks run after the wake. */
    fun sleep(duration: Duration) {
        moveTime(duration)
        runDue()
    }

    /** The user clicks "Check now". The check runs at once. */
    fun checkNow() {
        controller.checkNow()
        runDue()
    }

    /** The time passes while the app is closed. */
    fun wait(duration: Duration) {
        check(session == null) { "Close the app first" }
        moveTime(duration)
    }

    /** A normal close. */
    fun close() {
        controller.close()
        scheduler.clear()
        session = null
    }

    /** End task, a crash, or a power failure: no close. */
    fun endTask() {
        scheduler.clear()
        session = null
    }

    /**
     * Another build on the same computer and Windows account (an update, a reinstall, or an old installer). It
     * shares the preferences, the data folder, and the clock. Close the app first.
     */
    fun install(buildDate: LocalDate, appVersion: String): ExpirySimulator {
        check(session == null) { "Close the app first" }
        return ExpirySimulator(buildDate, appVersion, realTime, zone, savedTime, flag, dataFolder).also {
            it.clockOffset = clockOffset
            it.network = network
            it.serverFile = serverFile
        }
    }

    /** The modes in the order of the state events. */
    fun modes(): List<ExpiryMode> = events.filterIsInstance<ExpiryState>().map { it.mode }.distinctConsecutive()

    private fun runDue(until: Long = counter) {
        while (true) {
            val task = scheduler.next(until) ?: break
            if (task.due > counter) moveTime(Duration.ofMillis(task.due - counter))
            task.run()
        }
        if (until > counter) moveTime(Duration.ofMillis(until - counter))
    }

    /** The time moves with no task. The world uses it in a read and in a pause of the retries. */
    private fun moveTime(duration: Duration) {
        realTime += duration
        counter += duration.toMillis()
    }

    private val fetcher = RulesFetcher {
        fetches += counter
        duringFetch(this)
        when (network) {
            Network.OFFLINE -> RulesFetchResult.NoConnection("offline")
            Network.BLACKHOLE -> {
                moveTime(minOf(Duration.ofSeconds(10), fetchLimit ?: Duration.ofSeconds(10)))
                RulesFetchResult.NoConnection("timeout")
            }
            Network.ONLINE -> RulesFetchResult.ServerResponse(
                statusCode = serverStatus,
                // The Date header has seconds only.
                serverTime = realTime.truncatedTo(ChronoUnit.SECONDS).plusSeconds(serverAge),
                counterAtResponse = counter,
                body = serverFile.toByteArray().takeIf { serverStatus == 200 },
            )
        }
    }

    private val retryTiming = object : RetryTiming {
        override fun millis(): Long = counter

        override fun sleep(duration: Duration) = moveTime(duration)

        override fun <T : Any> withLimit(limit: Duration, block: () -> T): T? {
            val before = counter
            fetchLimit = limit
            val result = try {
                block()
            } finally {
                fetchLimit = null
            }
            return result.takeIf { counter - before < limit.toMillis() }
        }
    }

    /** Tasks in virtual time, by the run time counter. */
    private inner class VirtualScheduler : ExpiryScheduler {
        inner class Entry(val due: Long, val sequence: Long, val run: () -> Unit) {
            var cancelled = false
        }

        private val entries = mutableListOf<Entry>()
        private var sequence = 0L

        override fun schedule(delay: Duration, task: () -> Unit): ScheduledTask {
            val entry = Entry(counter + delay.toMillis(), sequence++, task)
            entries += entry
            return ScheduledTask { entry.cancelled = true }
        }

        fun next(until: Long): Entry? {
            entries.removeAll { it.cancelled }
            val entry = entries.filter { it.due <= until }.minWithOrNull(compareBy({ it.due }, { it.sequence })) ?: return null
            entries -= entry
            return entry
        }

        fun clear() = entries.clear()
    }

    class MemorySavedTimeStore : SavedTimeStore {
        var value: Instant? = null
        var writes = 0
            private set

        override fun read(): Instant? = value

        override fun write(time: Instant) {
            value = time
            writes++
        }
    }

    class MemoryFlagStore : ExpiredFlagStore {
        var value = false

        override fun isSet() = value

        override fun set(value: Boolean) {
            this.value = value
        }
    }

    companion object {
        const val BEFORE_EXPIRED_MODE = "before expired mode"

        /** A rules file. Each rule is a JSON object. */
        fun rulesFile(latest: String? = null, notes: String? = null, schema: Int = 1, vararg rules: String): String {
            val latestJson = latest?.let {
                """"latest":{"version":"$it","downloadUrl":"https://example.invalid/releases"""" +
                    (notes?.let { text -> ""","notes":"$text"""" } ?: "") + "},"
            } ?: ""
            return """{"schema":$schema,$latestJson"rules":[${rules.joinToString(",")}]}"""
        }

        fun rule(id: String, toVersion: String, stopsOn: LocalDate, message: String? = null): String =
            """{"id":"$id","toVersion":"$toVersion","stopsOn":"$stopsOn"""" +
                (message?.let { ""","message":"$it"""" } ?: "") + "}"

        private fun <T> List<T>.distinctConsecutive(): List<T> = filterIndexed { index, item -> index == 0 || this[index - 1] != item }
    }
}

/** Runs a scenario in a new simulator. */
fun simulate(simulator: ExpirySimulator = ExpirySimulator(), scenario: ExpirySimulator.() -> Unit) {
    simulator.scenario()
}

fun minutes(count: Long): Duration = Duration.ofMinutes(count)

fun hours(count: Long): Duration = Duration.ofHours(count)

fun days(count: Long): Duration = Duration.ofDays(count)
