package org.litvin.export.preview

import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.ExportPassPlanner
import org.litvin.export.ExportReplays
import org.litvin.export.ExportTrim
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the items of the export preview (B-75 T1). */
class ExportPreviewTimelineTest {
    private val p1 = PointV1(id = "p1", startMs = 10_000, endMs = 14_000)
    private val p2 = PointV1(id = "p2", startMs = 20_000, endMs = 26_000)
    private val p3 = PointV1(id = "p3", startMs = 40_000, endMs = 43_000)

    /** A replay of the last 2 s of [p2] at half speed: one play is 4 s. */
    private val p2WithReplay = p2.copy(replays = listOf(ReplayV1(24_000, 26_000, speed = 0.5, effect = ReplayEffect.FADE)))

    private fun card(pages: Int) = StatsCardVideo(List(pages) { ScoreboardScene(100.0, 100.0, emptyList()) }, pageDurationMs = 1_000L)

    private fun job(
        keeps: List<PointV1>,
        statsCard: StatsCardVideo? = null,
        setSummaries: List<SetSummaryCard> = emptyList(),
        adjustments: AdjustmentsV1 = AdjustmentsV1(),
        replayPoints: List<PointV1> = keeps,
    ) = RenderJob(
        sourcePath = "source.mp4",
        edlSnapshot = keeps,
        presetId = "balanced",
        outWidth = 1920,
        outHeight = 1080,
        encoderLabel = "test",
        idleTrim = keeps.isNotEmpty(),
        outputPath = "out.mp4",
        statsCard = statsCard,
        setSummaries = setSummaries,
        adjustments = adjustments,
        replays = ExportReplays.of(replayPoints, emptyList()),
    )

    @Test
    fun onlyPointsHasOneVideoItemForEachPointInOutputOrder() {
        val timeline = ExportPreviewTimeline.of(job(listOf(p1, p2, p3)), sourceDurationMs = 60_000)

        val videos = timeline.items.map { assertIs<ExportPreviewTimeline.Video>(it) }
        assertEquals(listOf("p1", "p2", "p3"), videos.map { it.pointId })
        assertEquals(listOf(0L, 4_000L, 10_000L), videos.map { it.outStartMs })
        assertEquals(listOf(0L, 4_000L, 10_000L), videos.map { it.overlayStartMs })
        assertEquals(13_000L, timeline.durationMs)
        assertTrue(timeline.hasCuts)
    }

    @Test
    fun aReplayGoesAfterItsPointAndTheOverlayTimeSkipsIt() {
        val timeline = ExportPreviewTimeline.of(job(listOf(p1, p2WithReplay, p3)), sourceDurationMs = 60_000)

        assertEquals(4, timeline.items.size)
        val second = assertIs<ExportPreviewTimeline.Video>(timeline.items[1])
        val replay = assertIs<ExportPreviewTimeline.Replay>(timeline.items[2])
        val third = assertIs<ExportPreviewTimeline.Video>(timeline.items[3])
        assertEquals(10_000L, replay.outStartMs)
        assertEquals(4_000L, replay.durationMs)
        // The point before the replay fades out, and the point after it fades in.
        assertEquals(ExportReplays.cutFade(ReplayEffect.FADE), second.fadeOut)
        assertEquals(ExportReplays.cutFade(ReplayEffect.FADE), third.fadeIn)
        assertEquals(14_000L, third.outStartMs)
        // The scoreboard and the comments of the export do not count the replay.
        assertEquals(10_000L, third.overlayStartMs)
        assertEquals(17_000L, timeline.durationMs)
    }

    @Test
    fun theSetCardAndTheMatchCardFollowTheirPoints() {
        val setCard = card(pages = 2)
        val matchCard = card(pages = 3)
        val timeline = ExportPreviewTimeline.of(
            job(
                keeps = listOf(p1, p2, p3),
                statsCard = matchCard,
                setSummaries = listOf(SetSummaryCard(1, setOf("p1", "p2"), setCard)),
            ),
            sourceDurationMs = 60_000,
        )

        val kinds = timeline.items.map { it::class.simpleName }
        assertEquals(listOf("Video", "Video", "Card", "Video", "Card"), kinds)
        val set = assertIs<ExportPreviewTimeline.Card>(timeline.items[2])
        assertEquals(1, set.setNumber)
        assertEquals(26_000L, set.freezeAtMs)
        val match = assertIs<ExportPreviewTimeline.Card>(timeline.items[4])
        assertNull(match.setNumber)
        assertEquals(43_000L, match.freezeAtMs)
        assertEquals(13_000L + 2_000L + 3_000L, timeline.durationMs)
    }

    @Test
    fun theTotalIsTheDurationThatTheExportGivesForItsProgress() {
        val job = job(listOf(p1, p2WithReplay, p3), statsCard = card(pages = 2))
        val timeline = ExportPreviewTimeline.of(job, sourceDurationMs = 60_000)

        val passes = ExportPassPlanner.plan(job.edlSnapshot, job.setSummaries, job.statsCard, 60_000, replays = job.replays)
        val last = passes.last()
        assertIs<ExportPassPlanner.Card>(last)
        assertEquals(last.progressBaseMs + last.card.durationMs, timeline.durationMs)
    }

    @Test
    fun theFullVideoIsOneItemOfTheWholeSource() {
        val timeline = ExportPreviewTimeline.of(job(emptyList()), sourceDurationMs = 60_000)

        val video = assertIs<ExportPreviewTimeline.Video>(timeline.items.single())
        assertEquals(0L, video.sourceStartMs)
        assertEquals(60_000L, video.sourceEndMs)
        assertNull(video.pointId)
        assertFalse(timeline.hasCuts)
    }

    @Test
    fun theFullVideoWithATrimStartsItsOverlaysAtTheTrimStart() {
        val adjustments = AdjustmentsV1(trimStartMs = 5_000, trimEndMs = 50_000)
        val trim = ExportTrim.of(adjustments, 60_000)!!
        val timeline = ExportPreviewTimeline.of(job(emptyList(), adjustments = adjustments), sourceDurationMs = 60_000)

        val video = assertIs<ExportPreviewTimeline.Video>(timeline.items.single())
        assertEquals(trim.startMs, video.sourceStartMs)
        assertEquals(trim.endMs, video.sourceEndMs)
        assertEquals(0L, video.overlayStartMs)
        assertEquals(1_000L, video.overlayMsAt(6_000))
    }

    @Test
    fun theFullVideoWithAReplaySplitsAtThePointEnd() {
        val timeline = ExportPreviewTimeline.of(
            job(emptyList(), replayPoints = listOf(p1, p2WithReplay)),
            sourceDurationMs = 60_000,
        )

        val kinds = timeline.items.map { it::class.simpleName }
        assertEquals(listOf("Video", "Replay", "Video"), kinds)
        val before = assertIs<ExportPreviewTimeline.Video>(timeline.items[0])
        val after = assertIs<ExportPreviewTimeline.Video>(timeline.items[2])
        assertEquals(26_000L, before.sourceEndMs)
        assertEquals(26_000L, after.sourceStartMs)
        // The overlays of a full video use the source time.
        assertEquals(26_000L, after.overlayStartMs)
        assertEquals(30_000L, after.outStartMs)
    }

    @Test
    fun theItemJumpsGoToTheNeighbors() {
        val timeline = ExportPreviewTimeline.of(job(listOf(p1, p2, p3)), sourceDurationMs = 60_000)

        assertEquals(1, timeline.indexAt(4_000))
        assertEquals(2, timeline.indexAt(13_000))
        assertEquals(10_000L, timeline.nextStartMs(5_000))
        assertNull(timeline.nextStartMs(11_000))
        // Near the start of an item, previous goes to the item before. Later, it goes to the start of the same item.
        assertEquals(0L, timeline.previousStartMs(4_500))
        assertEquals(4_000L, timeline.previousStartMs(8_000))
        assertNull(timeline.previousStartMs(500))
    }
}
