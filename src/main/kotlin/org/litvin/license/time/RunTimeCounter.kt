package org.litvin.license.time

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A counter in milliseconds that ignores changes of the system clock and counts the time while the computer sleeps.
 * The run time is the change of this counter. The value starts again at each start of the system, so do not save it.
 */
fun interface RunTimeCounter {
    fun millis(): Long

    companion object {
        /**
         * `GetTickCount64` on Windows and `CLOCK_MONOTONIC` on macOS. Both count the time in sleep and hibernation.
         * The counter does not read `os.name` to select the system clock: it uses the first clock that works
         * (the expiry reads no property). `os.name` only sets the order of the tries. `System.nanoTime` is the fallback.
         */
        fun production(): RunTimeCounter {
            val windows = { Kernel32.INSTANCE.GetTickCount64() }
            val macos = { MacClock.monotonicMillis() }
            val clocks = if (Platform.isMac()) listOf(macos, windows) else listOf(windows, macos)
            return FallbackRunTimeCounter(primary = FirstWorkingCounter(clocks)::millis)
        }
    }
}

/**
 * Uses the first of [candidates] that works at the first call, for the rest of the session. If no candidate works,
 * the call throws the failure of the first candidate. A later failure of the selected candidate goes to the caller.
 */
internal class FirstWorkingCounter(private val candidates: List<() -> Long>) : RunTimeCounter {
    private var selected: (() -> Long)? = null

    @Synchronized
    override fun millis(): Long {
        selected?.let { return it() }
        var firstFailure: Throwable? = null
        for (candidate in candidates) {
            try {
                return candidate().also { selected = candidate }
            } catch (failure: LinkageError) {
                firstFailure = firstFailure?.apply { addSuppressed(failure) } ?: failure
            } catch (failure: RuntimeException) {
                firstFailure = firstFailure?.apply { addSuppressed(failure) } ?: failure
            }
        }
        throw firstFailure ?: IllegalStateException("No run time counter is available")
    }
}

/**
 * The macOS clock `CLOCK_MONOTONIC`. On macOS this clock also counts the time while the Mac sleeps
 * (`man clock_gettime`). `System.nanoTime` does not count it on macOS.
 */
internal object MacClock {
    /** The value of `CLOCK_MONOTONIC` in `<time.h>` of the macOS SDK. */
    private const val CLOCK_MONOTONIC = 6

    private interface LibC : Library {
        fun clock_gettime_nsec_np(clockId: Int): Long
    }

    private val libc: LibC by lazy { Native.load("c", LibC::class.java) }

    fun monotonicMillis(): Long {
        val nanos = libc.clock_gettime_nsec_np(CLOCK_MONOTONIC)
        // The function gives 0 when it fails.
        check(nanos > 0) { "clock_gettime_nsec_np(CLOCK_MONOTONIC) failed" }
        return nanos / 1_000_000
    }
}

/**
 * Uses [primary] until a call fails. Then it uses [fallback] for the rest of the session and writes one log line.
 * The value continues from the last value of [primary], so the change to the fallback does not move the run time.
 */
class FallbackRunTimeCounter(
    private val primary: () -> Long,
    private val fallback: () -> Long = { System.nanoTime() / 1_000_000 },
    private val log: (Throwable) -> Unit = { failure ->
        logger.warn(failure) { "The run time counter is not available. The run time uses System.nanoTime, so a sleep can be lost." }
    },
) : RunTimeCounter {
    private var failed = false
    private var lastPrimary = 0L
    private var fallbackOffset = 0L

    @Synchronized
    override fun millis(): Long {
        if (!failed) {
            try {
                return primary().also { lastPrimary = it }
            } catch (failure: LinkageError) {
                useFallback(failure)
            } catch (failure: RuntimeException) {
                useFallback(failure)
            }
        }
        return fallback() + fallbackOffset
    }

    private fun useFallback(failure: Throwable) {
        failed = true
        fallbackOffset = lastPrimary - fallback()
        log(failure)
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
