package org.litvin.license.time

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.license.BuildExpiry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Calculates the current time of the app: the saved time ("Time and the clock" in `build-expiry-spec.md`).
 *
 * The engine keeps an anchor time `A` and the counter value `C_A` at that time.
 * The saved time at each moment is `A + (C(now) - C_A)`.
 *
 * Call [start] one time, after the instance lock of B-19. Then call [tick] each minute, [check] at each expiry
 * check, [serverTime] for each server response, and [close] when the app closes.
 */
class TimeEngine(
    private val counter: RunTimeCounter,
    private val systemTime: SystemTime,
    private val store: SavedTimeStore,
    private val buildMoment: Instant = buildMoment(),
) {
    private var started = false
    private var anchor: Instant = buildMoment
    private var anchorCounter = 0L
    private var startCounter = 0L
    private var lastWriteCounter = 0L
    private var clockBehindAtStart = false
    private var unwrittenServerTime = false

    /** True after the first server time in this session. Then the system time does not change the saved time. */
    @get:Synchronized
    var hasServerTime = false
        private set

    /** True when the clock was behind at start and no server time arrived after that. */
    val clockBehind: Boolean
        @Synchronized get() = clockBehindAtStart && !hasServerTime

    /** Reads the saved time, applies the build-date floor and "Clock behind", and writes the result. */
    @Synchronized
    fun start(): Instant {
        check(!started) { "The time engine is already started" }
        started = true
        val counterNow = counter.millis()
        val system = systemTime.withProbe()
        val stored = store.read()

        var startTime = if (stored == null || stored < buildMoment) buildMoment else stored
        clockBehindAtStart = Duration.between(system, startTime) > CLOCK_BEHIND_LIMIT
        if (clockBehindAtStart) startTime += CLOCK_BEHIND_PENALTY

        anchor = startTime
        anchorCounter = counterNow
        startCounter = counterNow
        logger.info {
            "Saved time at start: stored=$stored, system=$system, saved=$startTime, clockBehind=$clockBehindAtStart"
        }
        return write(currentTime(system, counterNow), counterNow)
    }

    /** The minute tick: uses the Java clock only, and writes the saved time after 5 minutes of run time. */
    @Synchronized
    fun tick(): Instant {
        requireStarted()
        val counterNow = counter.millis()
        val now = currentTime(systemTime.withoutProbe(), counterNow)
        return if (counterNow - lastWriteCounter >= WRITE_INTERVAL.toMillis()) write(now, counterNow) else now
    }

    /** The expiry check: uses the file time probe, and writes the saved time. */
    @Synchronized
    fun check(): Instant {
        requireStarted()
        val counterNow = counter.millis()
        val now = currentTime(systemTime.withProbe(), counterNow)
        logger.info { "Expiry check: saved=$now, run time=${Duration.ofMillis(counterNow - startCounter)}" }
        return write(now, counterNow)
    }

    /**
     * Sets the anchor to [time], with [counterAtResponse], the counter value when the response arrived.
     * Returns the system time minus the server time, for the wrong clock notice.
     */
    @Synchronized
    fun serverTime(time: Instant, counterAtResponse: Long = counter.millis()): Duration {
        requireStarted()
        anchor = time
        anchorCounter = counterAtResponse
        hasServerTime = true
        unwrittenServerTime = true
        val counterNow = counter.millis()
        val clockError = Duration.between(savedAt(counterNow), systemTime.withoutProbe())
        logger.info { "Server time: $time, clock error=$clockError" }
        return clockError
    }

    /** Writes the saved time when the app closes. */
    @Synchronized
    fun close(): Instant {
        requireStarted()
        val counterNow = counter.millis()
        return write(currentTime(systemTime.withoutProbe(), counterNow), counterNow)
    }

    private fun currentTime(system: Instant, counterNow: Long): Instant {
        if (!hasServerTime && system > savedAt(counterNow)) {
            anchor = system
            anchorCounter = counterNow
        }
        return savedAt(counterNow)
    }

    private fun savedAt(counterNow: Long): Instant = anchor.plusMillis(maxOf(0L, counterNow - anchorCounter))

    /**
     * The first write after a new server time replaces the stored value. All other writes keep the later of the
     * stored value and [now], so an other instance with no new server time cannot move the stored time back.
     */
    private fun write(now: Instant, counterNow: Long): Instant {
        val value = if (unwrittenServerTime) {
            now
        } else {
            val stored = store.read()
            if (stored != null && stored > now) stored else now
        }
        store.write(value)
        unwrittenServerTime = false
        lastWriteCounter = counterNow
        return now
    }

    private fun requireStarted() = check(started) { "The time engine is not started" }

    companion object {
        val CLOCK_BEHIND_LIMIT: Duration = Duration.ofHours(24)
        val CLOCK_BEHIND_PENALTY: Duration = Duration.ofHours(12)
        val WRITE_INTERVAL: Duration = Duration.ofMinutes(5)

        private val logger = KotlinLogging.logger {}

        /** 00:00 UTC on the build date. The real time is never earlier. */
        fun buildMoment(buildDate: LocalDate = BuildExpiry.buildDate): Instant =
            buildDate.atStartOfDay(ZoneOffset.UTC).toInstant()
    }
}
