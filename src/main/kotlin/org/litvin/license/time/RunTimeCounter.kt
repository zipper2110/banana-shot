package org.litvin.license.time

import com.sun.jna.platform.win32.Kernel32
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A counter in milliseconds that ignores changes of the system clock and counts the time while the computer sleeps.
 * The run time is the change of this counter. The value starts again at each start of Windows, so do not save it.
 */
fun interface RunTimeCounter {
    fun millis(): Long

    companion object {
        /** `GetTickCount64` counts the time in sleep and hibernation. `System.nanoTime` is the fallback. */
        fun production(): RunTimeCounter = FallbackRunTimeCounter(primary = { Kernel32.INSTANCE.GetTickCount64() })
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
