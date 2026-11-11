package org.litvin.crash

import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Finds a frozen UI thread (B-67). A user often stops a frozen app with a force quit. Without this watchdog, the log
 * then has no data about the freeze.
 *
 * Each [checkMillis], the watchdog asks the UI thread to run a small task ([postToUi]). When the UI thread does not
 * run it for [freezeMillis], the watchdog writes a dump of all threads to the log one time, and calls [onFreeze] with
 * true. When the UI thread runs the task again, it calls [onFreeze] with false.
 *
 * A computer sleep stops the watchdog thread too. A long gap between two checks resets the time, so that a sleep is not
 * a freeze.
 */
class FreezeWatchdog(
    private val postToUi: (Runnable) -> Unit,
    private val onFreeze: (Boolean) -> Unit,
    private val freezeMillis: Long = FREEZE_MILLIS,
    private val checkMillis: Long = CHECK_MILLIS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val threadDump: () -> String = ::allThreads,
) : AutoCloseable {
    @Volatile private var lastAnswer = clock()
    @Volatile private var waiting = false
    private var lastCheck = clock()
    private var frozen = false
    private var executor: ScheduledExecutorService? = null

    fun start() {
        if (executor != null) return
        executor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "freeze-watchdog").apply { isDaemon = true }
        }.also { it.scheduleWithFixedDelay({ runCatching { check() } }, checkMillis, checkMillis, TimeUnit.MILLISECONDS) }
    }

    /** One check. The scheduled task calls it. Tests call it directly. */
    @Synchronized
    fun check() {
        val now = clock()
        if (now - lastCheck > SLEEP_GAP_FACTOR * checkMillis) {
            // The watchdog did not run for a long time: a sleep or a stopped process. Start again from now.
            lastAnswer = now
        }
        lastCheck = now
        if (!waiting) {
            waiting = true
            postToUi(Runnable { answered() })
        }
        val silence = now - lastAnswer
        if (!frozen && waiting && silence >= freezeMillis) {
            frozen = true
            logger.warn { "The UI thread did not answer for ${silence / 1000} s. All threads follow.\n${threadDump()}" }
            onFreeze(true)
        }
    }

    private fun answered() {
        val now = clock()
        waiting = false
        lastAnswer = now
        synchronized(this) {
            if (frozen) {
                frozen = false
                logger.info { "The UI thread answers again." }
                onFreeze(false)
            }
        }
    }

    override fun close() {
        executor?.shutdownNow()
        executor = null
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        const val FREEZE_MILLIS = 10_000L
        const val CHECK_MILLIS = 1_000L
        private const val SLEEP_GAP_FACTOR = 5

        /** The stacks of all threads, with the locks. The UI thread is first. */
        fun allThreads(): String {
            val threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)
            return threads.sortedByDescending { it.threadName.startsWith("AWT-EventQueue") }
                .joinToString("") { info -> fullStack(info) }
        }

        /** `ThreadInfo.toString` shows only 8 frames. This text has all frames. */
        private fun fullStack(info: java.lang.management.ThreadInfo): String = buildString {
            append('"').append(info.threadName).append("\" ").append(info.threadState)
            info.lockName?.let { append(" on ").append(it) }
            info.lockOwnerName?.let { append(" owned by \"").append(it).append('"') }
            append('\n')
            info.stackTrace.forEach { frame -> append("\tat ").append(frame).append('\n') }
            append('\n')
        }
    }
}
