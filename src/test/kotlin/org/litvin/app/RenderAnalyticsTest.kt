package org.litvin.app

import org.litvin.RenderJob
import org.litvin.RenderQueueManager
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.AnalyticsEvent.Encoder
import org.litvin.analytics.AnalyticsEvent.ExportOption
import org.litvin.analytics.AnalyticsEvent.ExportOutcome
import org.litvin.analytics.AnalyticsEvent.Resolution
import org.litvin.analytics.RecordingAnalytics
import org.litvin.CompletedRender
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.RenderFailReason
import org.litvin.export.RenderQueueRequest
import org.litvin.export.RenderRunFacts
import org.litvin.export.RenderRunResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderAnalyticsTest {
    private val analytics = RecordingAnalytics()
    private val listener = RenderAnalytics(analytics)

    private fun facts(
        encoderLabel: String = "H.264 (libx264)",
        width: Int = 1920,
        height: Int = 1080,
        scoreboard: Boolean = false,
        comments: Boolean = false,
        statsCard: Boolean = false,
        favoritesOnly: Boolean = false,
        idleTrim: Boolean = false,
    ) = RenderRunFacts(encoderLabel, width, height, scoreboard, comments, statsCard, favoritesOnly, idleTrim)

    @Test
    fun `a start gives the encoder, the options, and the resolution`() {
        listener.started(facts("H.264 (NVENC)", 1080, 1920, scoreboard = true, statsCard = true, idleTrim = true))
        listener.started(facts("HEVC (hevc_qsv)", 1280, 720, comments = true, favoritesOnly = true))
        listener.started(facts("H.264 (h264_amf)", 3000, 2000))

        assertEquals(
            listOf<AnalyticsEvent>(
                AnalyticsEvent.ExportStarted(
                    Encoder.NVENC,
                    setOf(ExportOption.SCOREBOARD, ExportOption.STATS_CARD, ExportOption.IDLE_TRIM),
                    Resolution.P1080,
                ),
                AnalyticsEvent.ExportStarted(Encoder.QSV, setOf(ExportOption.COMMENTS, ExportOption.FAVORITES_ONLY), Resolution.P720),
                AnalyticsEvent.ExportStarted(Encoder.AMF, emptySet(), Resolution.OTHER),
            ),
            analytics.events,
        )
    }

    @Test
    fun `an end gives the outcome, and the run time counts only with a known video length`() {
        val facts = facts()
        listener.finished(facts, RenderRunResult.Completed(runMs = 4_000, videoMs = 12_500))
        listener.finished(facts, RenderRunResult.Completed(runMs = 4_000, videoMs = null))
        listener.finished(facts, RenderRunResult.Failed(RenderFailReason.FFMPEG_EXIT))
        listener.finished(facts, RenderRunResult.Canceled)
        listener.interrupted(facts)

        assertEquals(
            listOf(
                ExportOutcome.Completed(runMs = 4_000, videoS = 12),
                ExportOutcome.Completed(runMs = 0, videoS = 0),
                ExportOutcome.Failed(AnalyticsEvent.FailReason.FFMPEG_EXIT),
                ExportOutcome.Cancelled,
                ExportOutcome.Interrupted,
            ),
            analytics.events.map { (it as AnalyticsEvent.ExportFinished).also { e -> assertEquals(Encoder.SOFTWARE, e.encoder) }.outcome },
        )
    }

    @Test
    fun `the render queue reports a start and a failure for a job with a missing source`() {
        val dir = createTempDirectory("render-analytics-").toFile()
        val ended = CountDownLatch(1)
        val previous = RenderQueueManager.runListener
        RenderQueueManager.runListener = listener
        try {
            val job = RenderJob(
                sourcePath = dir.resolve("missing.mp4").absolutePath,
                presetId = "balanced",
                outWidth = 1920,
                outHeight = 1080,
                encoderLabel = "H.264 (libx264)",
                idleTrim = false,
                includeScoreboard = true,
                outputPath = dir.resolve("out.mp4").absolutePath,
            )
            RenderQueueManager.enqueue(RenderQueueRequest("render-analytics-test", job, NoCompletedRenders) { ended.countDown() })

            assertTrue(ended.await(10, TimeUnit.SECONDS), "the job ends")
            assertEquals(
                listOf<AnalyticsEvent>(
                    AnalyticsEvent.ExportStarted(Encoder.SOFTWARE, setOf(ExportOption.SCOREBOARD), Resolution.P1080),
                    AnalyticsEvent.ExportFinished(Encoder.SOFTWARE, ExportOutcome.Failed(AnalyticsEvent.FailReason.SOURCE_MISSING)),
                ),
                analytics.events,
            )
            assertEquals(emptyList(), RenderQueueManager.runningExports())
        } finally {
            RenderQueueManager.runListener = previous
            dir.deleteRecursively()
        }
    }

    private object NoCompletedRenders : CompletedRendersRepository {
        override fun loadAll(): List<CompletedRender> = emptyList()
        override fun append(job: RenderJob) = Unit
        override fun clear() = Unit
    }
}
