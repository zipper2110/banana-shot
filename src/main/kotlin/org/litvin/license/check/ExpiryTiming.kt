package org.litvin.license.check

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A task that [ExpiryScheduler] runs later. */
fun interface ScheduledTask {
    fun cancel()
}

/**
 * Runs the tasks of [ExpiryController]: the minute tick, the online checks, and "Check now". Production uses one
 * background thread, so the tasks run one after the other. The scenario simulator uses virtual time.
 */
fun interface ExpiryScheduler {
    fun schedule(delay: Duration, task: () -> Unit): ScheduledTask
}

/** The waits of the online check with retries (path A). The scenario simulator uses virtual time. */
interface RetryTiming {
    /** A counter in milliseconds for the total limit. */
    fun millis(): Long

    fun sleep(duration: Duration)

    /** Runs [block] and waits a maximum of [limit]. Null when the limit passes first. */
    fun <T : Any> withLimit(limit: Duration, block: () -> T): T?
}

/** One daemon thread. A task that fails goes to the log, and the next tasks still run. */
class ExecutorExpiryScheduler(
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "expiry-check").apply { isDaemon = true }
    },
) : ExpiryScheduler {
    override fun schedule(delay: Duration, task: () -> Unit): ScheduledTask {
        val future = executor.schedule({
            try {
                task()
            } catch (failure: Exception) {
                logger.error(failure) { "An expiry task failed" }
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS)
        return ScheduledTask { future.cancel(false) }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}

/** `System.nanoTime`, `Thread.sleep`, and a separate daemon thread for each limited call. */
object SystemRetryTiming : RetryTiming {
    private val executor = Executors.newCachedThreadPool { task -> Thread(task, "expiry-fetch").apply { isDaemon = true } }

    override fun millis(): Long = System.nanoTime() / 1_000_000

    override fun sleep(duration: Duration) = Thread.sleep(duration.toMillis())

    override fun <T : Any> withLimit(limit: Duration, block: () -> T): T? {
        val future = executor.submit<T>(block)
        return try {
            future.get(limit.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }
}
