package org.litvin.analytics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnabledAnalyticsTest {
    private val mapper = ObjectMapper()
    private val clock = FakeClock()
    private val executor = EnabledAnalytics.newDaemonExecutor()
    private val transport = RecordingTransport()
    private val sessions = mutableListOf<EnabledAnalytics>()

    @AfterTest
    fun tearDown() {
        sessions.forEach { it.close() }
        executor.shutdownNow()
    }

    @Test
    fun `sends the first summary at session start with the session bucket`() {
        start()
        val first = transport.next()
        assertEquals(contractFieldNames(), first.fieldNames().asSequence().toSet())
        assertEquals(1, first["schema_version"].intValue())
        assertEquals(1, first["notice_version"].intValue())
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(first["session_id"].textValue()))
        assertEquals("1.2.3", first["app_version"].textValue())
        assertEquals("windows", first["os_family"].textValue())
        assertEquals(0, first["snapshot"].intValue())
        assertFalse(first["final"].booleanValue())
        assertEquals(0, first["duration_s"].intValue())
        assertEquals(0, first["active_s"].intValue())
        assertEquals(mapOf("session_n_1" to 1), counters(first))
    }

    @Test
    fun `counts an unclean exit of the previous session and uses the session bucket`() {
        start(AnalyticsPreferences.SessionStart(number = 7, uncleanExit = true))
        assertEquals(mapOf("session_n_6_20" to 1, "unclean_exit" to 1), counters(transport.next()))
    }

    @Test
    fun `an idle app does not send, also when duration_s changes`() {
        val session = start()
        transport.next()
        clock.advance(Duration.ofMinutes(10))
        session.sendIfChanged()
        assertNoSend()
    }

    @Test
    fun `sends after a counter change with the next snapshot and only once`() {
        val session = start()
        transport.next()
        clock.advance(Duration.ofSeconds(42))
        session.record(AnalyticsEvent.PointAdded)
        session.record(AnalyticsEvent.PointAdded)
        session.sendIfChanged()
        val second = transport.next()
        assertEquals(1, second["snapshot"].intValue())
        assertEquals(42, second["duration_s"].intValue())
        assertEquals(2, counters(second)["point_added"])
        session.sendIfChanged()
        assertNoSend()
    }

    @Test
    fun `active time and tab time count only while the window is active`() {
        val session = start()
        transport.next()
        session.record(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.POINTS, byUser = false))
        session.record(AnalyticsEvent.WindowActive(true))
        clock.advance(Duration.ofSeconds(90))
        session.record(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.STATS, byUser = true))
        clock.advance(Duration.ofSeconds(30))
        session.record(AnalyticsEvent.WindowActive(false))
        clock.advance(Duration.ofSeconds(600))
        session.sendIfChanged()
        val summary = transport.next()
        assertEquals(120, summary["active_s"].intValue())
        assertEquals(720, summary["duration_s"].intValue())
        assertEquals(mapOf("session_n_1" to 1, "tab_s_points" to 90, "tab_s_stats" to 30, "tab_stats" to 1), counters(summary))

        clock.advance(Duration.ofSeconds(600))
        session.sendIfChanged()
        assertNoSend()
    }

    @Test
    fun `adds the time of the current tab before each send`() {
        val session = start()
        transport.next()
        session.record(AnalyticsEvent.WindowActive(true))
        session.record(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.EXPORT, byUser = false))
        clock.advance(Duration.ofSeconds(5))
        session.sendIfChanged()
        assertEquals(5, counters(transport.next())["tab_s_export"])
        clock.advance(Duration.ofSeconds(5))
        session.sendIfChanged()
        assertEquals(10, counters(transport.next())["tab_s_export"])
    }

    @Test
    fun `counts only sidebar clicks as tab opens and never the Test tab`() {
        val session = start()
        transport.next()
        session.record(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.PROJECTS, byUser = false))
        session.record(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.POINTS, byUser = true))
        session.record(AnalyticsEvent.TabShown(AnalyticsEvent.Tab.POINTS, byUser = true))
        session.record(AnalyticsEvent.TabShown(null, byUser = true))
        session.record(AnalyticsEvent.WindowActive(true))
        clock.advance(Duration.ofSeconds(3))
        session.sendIfChanged()
        assertEquals(mapOf("session_n_1" to 1, "tab_points" to 2), counters(transport.next()))
    }

    @Test
    fun `changes each feature event into its counter`() {
        val session = start()
        transport.next()
        val events = mapOf(
            AnalyticsEvent.UncaughtError to "uncaught_error",
            AnalyticsEvent.HelpOpened to "help_opened",
            AnalyticsEvent.ProjectCreated to "project_created",
            AnalyticsEvent.ProjectOpened to "project_opened",
            AnalyticsEvent.VideoOpenFailed to "video_open_failed",
            AnalyticsEvent.PointAdded to "point_added",
            AnalyticsEvent.PointDeleted to "point_deleted",
            AnalyticsEvent.PointFavorited to "point_favorited",
            AnalyticsEvent.CommentAdded to "comment_added",
            AnalyticsEvent.ScoreRecorded to "score_recorded",
            AnalyticsEvent.ColorChanged to "color_changed",
            AnalyticsEvent.CropRotateChanged to "crop_rotate_changed",
        )
        events.keys.forEach(session::record)
        session.sendIfChanged()
        assertEquals(events.values.associateWith { 1 } + ("session_n_1" to 1), counters(transport.next()))
    }

    @Test
    fun `changes export events into counters by encoder`() {
        val session = start()
        transport.next()
        val options = setOf(AnalyticsEvent.ExportOption.SCOREBOARD, AnalyticsEvent.ExportOption.IDLE_TRIM)
        session.record(AnalyticsEvent.ExportStarted(AnalyticsEvent.Encoder.NVENC, options, AnalyticsEvent.Resolution.P1080))
        session.record(AnalyticsEvent.ExportStarted(AnalyticsEvent.Encoder.NVENC, emptySet(), AnalyticsEvent.Resolution.OTHER))
        val nvenc = AnalyticsEvent.Encoder.NVENC
        session.record(AnalyticsEvent.ExportFinished(nvenc, AnalyticsEvent.ExportOutcome.Completed(runMs = 1_500, videoS = 60)))
        session.record(AnalyticsEvent.ExportFinished(nvenc, AnalyticsEvent.ExportOutcome.Completed(runMs = 1_600, videoS = 30)))
        session.record(AnalyticsEvent.ExportFinished(AnalyticsEvent.Encoder.QSV, AnalyticsEvent.ExportOutcome.Failed(AnalyticsEvent.FailReason.FFMPEG_EXIT)))
        session.record(AnalyticsEvent.ExportFinished(AnalyticsEvent.Encoder.AMF, AnalyticsEvent.ExportOutcome.Cancelled))
        session.record(AnalyticsEvent.ExportFinished(AnalyticsEvent.Encoder.SOFTWARE, AnalyticsEvent.ExportOutcome.Interrupted))
        session.sendIfChanged()
        assertEquals(
            mapOf(
                "session_n_1" to 1,
                "export_started_nvenc" to 2, "export_opt_scoreboard" to 1, "export_opt_idle_trim" to 1,
                "export_res_1080" to 1, "export_res_other" to 1,
                "export_completed_nvenc" to 2, "export_run_s_nvenc" to 3, "export_video_s_nvenc" to 90,
                "export_failed_qsv" to 1, "export_fail_ffmpeg_exit" to 1,
                "export_cancelled_amf" to 1, "export_interrupted_software" to 1,
            ),
            counters(transport.next()),
        )
    }

    @Test
    fun `clamps duration_s and active_s to 7 days`() {
        val session = start()
        transport.next()
        session.record(AnalyticsEvent.WindowActive(true))
        clock.advance(Duration.ofDays(8))
        session.sendIfChanged()
        val summary = transport.next()
        assertEquals(604_800, summary["duration_s"].intValue())
        assertEquals(604_800, summary["active_s"].intValue())
    }

    @Test
    fun `the final summary has final true and close waits for it`() {
        val session = start()
        transport.next()
        session.record(AnalyticsEvent.HelpOpened)
        session.beginFinalSend()
        session.close()
        val last = transport.next()
        assertTrue(last["final"].booleanValue())
        assertEquals(1, last["snapshot"].intValue())
        assertEquals(1, counters(last)["help_opened"])
    }

    @Test
    fun `close waits for the final send only until the limit`() {
        val release = CountDownLatch(1)
        val session = start(finalSendLimit = Duration.ofMillis(200))
        transport.next()
        transport.block = release
        session.beginFinalSend()
        val started = System.nanoTime()
        session.close()
        val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        release.countDown()
        assertTrue(waitedMs in 150..2_000, "close waited $waitedMs ms")
    }

    @Test
    fun `close without the final send sends nothing and later events are ignored`() {
        val session = start()
        transport.next()
        session.record(AnalyticsEvent.PointAdded)
        session.close()
        session.beginFinalSend()
        session.record(AnalyticsEvent.PointAdded)
        session.sendIfChanged()
        assertNull(transport.bodies.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a 410 response stops all sends of the session`() {
        transport.status = 410
        val session = start()
        transport.next()
        session.record(AnalyticsEvent.PointAdded)
        session.sendIfChanged()
        session.beginFinalSend()
        assertNoSend()
    }

    @Test
    fun `a failed send does not stop the next send`() {
        transport.failure = IOException("no network")
        val session = start()
        executor.submit {}.get(2, TimeUnit.SECONDS)
        assertNull(transport.bodies.poll())
        transport.failure = null
        session.record(AnalyticsEvent.PointAdded)
        session.sendIfChanged()
        assertEquals(1, transport.next()["snapshot"].intValue())
    }

    @Test
    fun `sends on a daemon thread`() {
        start()
        transport.next()
        assertEquals(true, transport.daemon)
    }

    @Test
    fun `the production executor runs the timer on a daemon thread`() {
        val daemon = LinkedBlockingQueue<Boolean>()
        val production = EnabledAnalytics.newDaemonExecutor()
        try {
            production.submit { daemon.put(Thread.currentThread().isDaemon) }
            assertEquals(true, daemon.poll(2, TimeUnit.SECONDS))
        } finally {
            production.shutdownNow()
        }
    }

    private fun start(
        sessionStart: AnalyticsPreferences.SessionStart = AnalyticsPreferences.SessionStart(1, uncleanExit = false),
        finalSendLimit: Duration = Duration.ofSeconds(2),
    ): EnabledAnalytics = EnabledAnalytics(
        transport, "1.2.3", "windows", sessionStart, clock, executor,
        sendInterval = Duration.ofDays(1), finalSendLimit = finalSendLimit,
    ).also { sessions += it }

    /** Waits until the analytics thread has done all queued work, then checks that nothing was sent. */
    private fun assertNoSend() {
        executor.submit {}.get(2, TimeUnit.SECONDS)
        assertNull(transport.bodies.poll(), "no send expected")
    }

    private fun RecordingTransport.next(): JsonNode =
        mapper.readTree(bodies.poll(2, TimeUnit.SECONDS) ?: error("no send"))

    private fun counters(summary: JsonNode): Map<String, Int> =
        summary["counters"].fields().asSequence().associate { (key, value) -> key to value.intValue() }

    private fun contractFieldNames(): Set<String> =
        mapper.readTree(Path("analytics-contract/v1/smoke-summary.json").readText()).fieldNames().asSequence().toSet()

    private class FakeClock : AnalyticsClock {
        private var now = 1_000_000_000L
        @Synchronized override fun nanoTime(): Long = now
        @Synchronized fun advance(duration: Duration) { now += duration.toNanos() }
    }

    private class RecordingTransport : AnalyticsTransport {
        val bodies = LinkedBlockingQueue<String>()
        @Volatile var status = 204
        @Volatile var failure: IOException? = null
        @Volatile var block: CountDownLatch? = null
        @Volatile var daemon: Boolean? = null

        override fun post(body: String): Int {
            daemon = Thread.currentThread().isDaemon
            block?.await()
            failure?.let { throw it }
            bodies.put(body)
            return status
        }
    }
}
