package org.litvin.analytics

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * One analytics session (`docs/analytics/design.md`, "Session", "Levels", and "When the app sends").
 *
 * - The session counts only the keys of its [AnalyticsLevel]. A change to [AnalyticsLevel.ESSENTIAL] deletes the
 *   other counters. The next summary then replaces the extended counters on the server.
 * - The constructor sends the first summary. Then the timer sends a summary every [sendInterval], but only if a
 *   counter or `active_s` changed. Thus an idle app does not send.
 * - [beginFinalSend] starts the last summary (`final = true`). [close] waits for it until [finalSendLimit] after
 *   [beginFinalSend], and then stops. Without [beginFinalSend], [close] sends nothing.
 * - A 410 response stops all sends of this session. There is no retry: the next summary replaces a lost one.
 * - All work runs on one daemon thread. [record] never waits for the network.
 * - The log contains only the count of sends and the status classes, not the body or the session ID.
 */
internal class EnabledAnalytics(
    private val transport: AnalyticsTransport,
    private val appVersion: String,
    private val osFamily: String,
    start: AnalyticsPreferences.SessionStart,
    private var level: AnalyticsLevel,
    private val clock: AnalyticsClock = SystemAnalyticsClock,
    private val executor: ScheduledExecutorService = newDaemonExecutor(),
    sendInterval: Duration = SEND_INTERVAL,
    private val finalSendLimit: Duration = FINAL_SEND_LIMIT,
) : ManagedAnalytics {
    private val sessionId = UUID.randomUUID().toString()
    private val counters = SessionCounters()
    private val lock = Any()
    private val startedAt = clock.nanoTime()
    private var activeSince: Long? = null
    private var activeNanos = 0L
    private var currentTab: AnalyticsEvent.Tab? = null
    private var snapshot = 0
    private var lastSent: SentState? = null
    private var finalSend: Future<*>? = null
    private var finalSendStartedAt = 0L
    private var closed = false
    @Volatile private var stopped = false
    private val statusCounts = sortedMapOf<String, Int>()

    init {
        count(AnalyticsSchema.sessionBucket(start.number))
        if (start.uncleanExit) count(AnalyticsSchema.UNCLEAN_EXIT)
        sendSummary(final = false)
        val intervalMs = sendInterval.toMillis()
        executor.scheduleWithFixedDelay({ runCatching { sendIfChanged() } }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    }

    override fun record(event: AnalyticsEvent) {
        synchronized(lock) {
            if (!closed) apply(event)
        }
    }

    override fun setLevel(level: AnalyticsLevel) {
        synchronized(lock) {
            if (closed || level == this.level) return
            addActiveTime()
            this.level = level
            counters.retainOnly(level.counterKeys)
        }
    }

    /** Adds to the counter [key] only if the current level has the key. Call only while holding [lock]. */
    private fun count(key: String, value: Long = 1) {
        if (key in level.counterKeys) counters.add(key, value)
    }

    /** As [count], for a time in nanoseconds. Call only while holding [lock]. */
    private fun countNanos(key: String, value: Long) {
        if (key in level.counterKeys) counters.addNanos(key, value)
    }

    /** Call only while holding [lock]. */
    private fun apply(event: AnalyticsEvent) {
        AnalyticsSchema.simpleKey(event)?.let { count(it) }
        when (event) {
            is AnalyticsEvent.TabShown -> {
                addActiveTime()
                currentTab = event.tab
                if (event.byUser && event.tab != null) count(AnalyticsSchema.tabOpened(event.tab))
            }
            is AnalyticsEvent.WindowActive -> {
                addActiveTime()
                activeSince = if (event.active) clock.nanoTime() else null
            }
            is AnalyticsEvent.ExportStarted -> {
                count("export_started_${event.encoder.key}")
                event.options.forEach { count("export_opt_${it.key}") }
                count("export_res_${event.resolution.key}")
            }
            is AnalyticsEvent.ExportFinished -> recordExportResult(event.encoder.key, event.outcome)
            else -> Unit
        }
    }

    private fun recordExportResult(encoder: String, outcome: AnalyticsEvent.ExportOutcome) = when (outcome) {
        is AnalyticsEvent.ExportOutcome.Completed -> {
            count("export_completed_$encoder")
            countNanos("export_run_s_$encoder", TimeUnit.MILLISECONDS.toNanos(outcome.runMs.coerceAtLeast(0)))
            count("export_video_s_$encoder", outcome.videoS)
        }
        is AnalyticsEvent.ExportOutcome.Failed -> {
            count("export_failed_$encoder")
            count("export_fail_${outcome.reason.key}")
        }
        AnalyticsEvent.ExportOutcome.Cancelled -> count("export_cancelled_$encoder")
        AnalyticsEvent.ExportOutcome.Interrupted -> count("export_interrupted_$encoder")
    }

    override fun beginFinalSend() {
        synchronized(lock) {
            if (closed || finalSend != null) return
            finalSendStartedAt = System.nanoTime()
            finalSend = sendSummary(final = true)
        }
    }

    override fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            finalSend
        }
        if (pending != null) {
            val left = finalSendLimit.toNanos() - (System.nanoTime() - finalSendStartedAt)
            try {
                pending.get(left.coerceAtLeast(0), TimeUnit.NANOSECONDS)
            } catch (_: TimeoutException) {
                logger.info { "Analytics: the last summary did not complete in ${finalSendLimit.toMillis()} ms" }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                // The send logs its own failure.
            }
        }
        executor.shutdownNow()
        logger.info { "Analytics: session closed. Summaries sent: ${synchronized(statusCounts) { statusCounts.toString() }}" }
    }

    /** The timer task. Sends a summary only if the level, a counter, or `active_s` changed since the last send. */
    internal fun sendIfChanged() {
        synchronized(lock) {
            if (closed || stopped) return
            val (summary, state) = summaryNow(final = false)
            if (state == lastSent) return
            send(summary, state)
        }
    }

    private fun sendSummary(final: Boolean): Future<*>? {
        val (summary, state) = summaryNow(final)
        return send(summary, state)
    }

    /** Call only while holding [lock]. */
    private fun send(summary: SessionSummary, state: SentState): Future<*>? {
        if (stopped || snapshot > AnalyticsSchema.MAX_SNAPSHOT) return null
        snapshot++
        lastSent = state
        val body = summary.toJson()
        return runCatching { executor.submit { deliver(body) } }.getOrNull()
    }

    /** Call only while holding [lock]. */
    private fun summaryNow(final: Boolean): Pair<SessionSummary, SentState> {
        addActiveTime()
        val now = clock.nanoTime()
        val activeS = seconds(activeNanos)
        val values = counters.values()
        val summary = SessionSummary(
            sessionId = sessionId,
            level = level,
            appVersion = appVersion,
            osFamily = osFamily,
            snapshot = snapshot,
            final = final,
            durationS = seconds(now - startedAt),
            activeS = activeS,
            counters = values,
        )
        return summary to SentState(level, activeS, values)
    }

    /** Adds the active time since the last call to `active_s` and to the time of the current tab. */
    private fun addActiveTime() {
        val since = activeSince ?: return
        val now = clock.nanoTime()
        val delta = (now - since).coerceAtLeast(0)
        activeNanos += delta
        currentTab?.let { countNanos(AnalyticsSchema.tabSeconds(it), delta) }
        activeSince = now
    }

    private fun deliver(body: String) {
        if (stopped) return
        val statusClass = try {
            val status = transport.post(body)
            if (status == 410) {
                stopped = true
                logger.info { "Analytics: the server returned 410. No more summaries in this process." }
            }
            "${status / 100}xx"
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            "interrupted"
        } catch (failure: Exception) {
            "no response (${failure.javaClass.simpleName})"
        }
        val count = synchronized(statusCounts) { statusCounts.merge(statusClass, 1, Int::plus) }
        logger.debug { "Analytics: summary sent, $statusClass ($count with this result)" }
    }

    private fun seconds(nanos: Long): Int =
        (nanos.coerceAtLeast(0) / SessionCounters.NANOS_PER_SECOND).coerceAtMost(AnalyticsSchema.MAX_SECONDS.toLong()).toInt()

    /** The values that decide if the timer sends. `duration_s` is not one of them. */
    private data class SentState(val level: AnalyticsLevel, val activeS: Int, val counters: Map<String, Int>)

    companion object {
        private val logger = KotlinLogging.logger {}
        val SEND_INTERVAL: Duration = Duration.ofMinutes(5)
        val FINAL_SEND_LIMIT: Duration = Duration.ofMillis(500)

        fun newDaemonExecutor(): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "analytics").apply { isDaemon = true }
        }
    }
}
