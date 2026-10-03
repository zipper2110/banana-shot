package org.litvin.app

import org.litvin.analytics.Analytics
import org.litvin.analytics.AnalyticsEvent
import org.litvin.export.RenderFailReason
import org.litvin.export.RenderRunFacts
import org.litvin.export.RenderRunListener
import org.litvin.export.RenderRunResult

/** Changes the facts of the render queue into analytics events (B-9, "Export" counters). */
internal class RenderAnalytics(private val analytics: Analytics) : RenderRunListener {
    override fun started(facts: RenderRunFacts) {
        val options = buildSet {
            if (facts.scoreboard) add(AnalyticsEvent.ExportOption.SCOREBOARD)
            if (facts.comments) add(AnalyticsEvent.ExportOption.COMMENTS)
            if (facts.statsCard) add(AnalyticsEvent.ExportOption.STATS_CARD)
            if (facts.favoritesOnly) add(AnalyticsEvent.ExportOption.FAVORITES_ONLY)
            if (facts.idleTrim) add(AnalyticsEvent.ExportOption.IDLE_TRIM)
        }
        analytics.record(
            AnalyticsEvent.ExportStarted(encoder(facts), options, AnalyticsEvent.Resolution.ofFrame(facts.outWidth, facts.outHeight)),
        )
    }

    override fun finished(facts: RenderRunFacts, result: RenderRunResult) {
        val outcome = when (result) {
            // The encode speed is video seconds for each run second. Without the video length, the run time also
            // does not count, so that the speed stays correct.
            is RenderRunResult.Completed -> AnalyticsEvent.ExportOutcome.Completed(
                runMs = if (result.videoMs != null) result.runMs else 0,
                videoS = (result.videoMs ?: 0) / 1000,
            )
            is RenderRunResult.Failed -> AnalyticsEvent.ExportOutcome.Failed(failReason(result.reason))
            RenderRunResult.Canceled -> AnalyticsEvent.ExportOutcome.Cancelled
        }
        analytics.record(AnalyticsEvent.ExportFinished(encoder(facts), outcome))
    }

    /** The export was running when the app closed. */
    fun interrupted(facts: RenderRunFacts) {
        analytics.record(AnalyticsEvent.ExportFinished(encoder(facts), AnalyticsEvent.ExportOutcome.Interrupted))
    }

    companion object {
        /** The same rule as `FFmpegCommandBuilder`: the job label names the encoder. */
        fun encoder(facts: RenderRunFacts): AnalyticsEvent.Encoder = when {
            facts.encoderLabel.contains("NVENC", ignoreCase = true) -> AnalyticsEvent.Encoder.NVENC
            facts.encoderLabel.contains("QSV", ignoreCase = true) -> AnalyticsEvent.Encoder.QSV
            facts.encoderLabel.contains("AMF", ignoreCase = true) -> AnalyticsEvent.Encoder.AMF
            else -> AnalyticsEvent.Encoder.SOFTWARE
        }

        private fun failReason(reason: RenderFailReason): AnalyticsEvent.FailReason = when (reason) {
            RenderFailReason.SOURCE_MISSING -> AnalyticsEvent.FailReason.SOURCE_MISSING
            RenderFailReason.OUTPUT_WRITE -> AnalyticsEvent.FailReason.OUTPUT_WRITE
            RenderFailReason.PROCESS_START -> AnalyticsEvent.FailReason.PROCESS_START
            RenderFailReason.FFMPEG_EXIT -> AnalyticsEvent.FailReason.FFMPEG_EXIT
            RenderFailReason.OTHER -> AnalyticsEvent.FailReason.OTHER
        }
    }
}
