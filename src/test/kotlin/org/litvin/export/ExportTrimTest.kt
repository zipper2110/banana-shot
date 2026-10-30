package org.litvin.export

import org.litvin.CommentOverlaySpan
import org.litvin.OverlaySpan
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.TrimRange
import org.litvin.export.ExportPassPlanner.Card
import org.litvin.export.ExportPassPlanner.Video
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.points.PointV1
import org.litvin.points.ReplayV1
import org.litvin.stats.StatsCardVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The trim of the video (B-60) in the full video export. */
class ExportTrimTest {
    private val trimmed = AdjustmentsV1(trimStartMs = 10_000, trimEndMs = 50_000)

    @Test
    fun `without a trim the export has no trim`() {
        assertNull(ExportTrim.of(AdjustmentsV1(), 60_000))
    }

    @Test
    fun `an end at the source end is the source end`() {
        assertEquals(ExportTrim(10_000, null), ExportTrim.of(AdjustmentsV1(trimStartMs = 10_000, trimEndMs = 90_000), 60_000))
        assertEquals(40_000L, ExportTrim.of(trimmed, 60_000)!!.durationMs(60_000))
        assertEquals(ExportTrim(10_000, 50_000), ExportTrim.of(trimmed, null))
    }

    @Test
    fun `the overlays move back by the trim start and stop at the trim end`() {
        val trim = ExportTrim(10_000, 50_000)
        val spans = listOf(span(0, 5_000), span(8_000, 12_000), span(20_000, 30_000), span(45_000, 70_000), span(55_000, 60_000))
        assertEquals(listOf(0L to 2_000L, 10_000L to 20_000L, 35_000L to 40_000L), trim.overlays(spans).map { it.startMs to it.endMs })
        val comments = listOf(CommentOverlaySpan(1, 9_000, 11_000, "c", "#FFFFFF"))
        assertEquals(listOf(0L to 1_000L), trim.comments(comments).map { it.startMs to it.endMs })
    }

    @Test
    fun `a trimmed full video is one range of the source and the end card shows the trim end`() {
        val card = StatsCardVideo(listOf(ScoreboardScene(1920.0, 1080.0, emptyList())))
        val passes = ExportPassPlanner.plan(keeps = emptyList(), endCard = card, fullVideoDurationMs = 60_000, trim = ExportTrim(10_000, 50_000))
        val video = assertIs<Video>(passes[0])
        assertEquals(listOf(10_000 to 50_000), video.chunk.keeps.map { it.startMs to it.endMs })
        assertEquals(50_000L, assertIs<Card>(passes[1]).freezeAtMs)
    }

    @Test
    fun `the replays split the trimmed range`() {
        val point = PointV1("p1", 20_000, 30_000, replays = listOf(ReplayV1(25_000, 30_000)))
        val replays = ExportReplays.of(listOf(point), emptyList())
        val passes = ExportPassPlanner.plan(keeps = emptyList(), fullVideoDurationMs = 60_000, replays = replays, trim = ExportTrim(10_000, null))
        val ranges = passes.filterIsInstance<Video>().map { pass -> pass.chunk.keeps.single().let { it.startMs to it.endMs } }
        assertEquals(listOf(10_000 to 30_000, 30_000 to 60_000), ranges)
        assertEquals(listOf(0L, 20_000L), passes.filterIsInstance<Video>().map { it.chunk.outputOffsetMs })
    }

    @Test
    fun `a replay of a point outside the trim does not play`() {
        val trim = ExportTrim(10_000, 50_000)
        assertEquals(listOf(false, true, true, false), listOf(10_000L, 10_001L, 50_000L, 50_001L).map(trim::keepsReplayAt))
    }

    @Test
    fun `the points outside the trim are the points that the trim cuts off fully or in part`() {
        val points = listOf(PointV1("a", 5_000, 9_000), PointV1("b", 9_000, 11_000), PointV1("c", 20_000, 30_000), PointV1("d", 49_000, 51_000))
        val outside = TrimRange.outside(trimmed, points, { it.startMs.toLong() }, { it.endMs.toLong() })
        assertEquals(listOf("a", "b", "d"), outside.map { it.id })
    }

    @Test
    fun `the trim range keeps the start before the end`() {
        assertEquals(TrimRange(0, 60_000), TrimRange.of(AdjustmentsV1(), 60_000))
        assertEquals(TrimRange(10_000, 50_000), TrimRange.of(trimmed, 60_000))
        assertEquals(TrimRange(10_000, 30_000), TrimRange.of(trimmed, 30_000))
        assertNull(TrimRange.cleanEnd(10_000, 5_000))
        assertNull(TrimRange.cleanStart(0))
    }

    private fun span(start: Long, end: Long) = OverlaySpan(start, end, "0-0")
}
