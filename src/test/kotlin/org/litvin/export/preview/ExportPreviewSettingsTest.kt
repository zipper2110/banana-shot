package org.litvin.export.preview

import org.litvin.ExportPreset
import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.ExportPlanner
import org.litvin.export.ExportRenderPlanRequest
import org.litvin.export.ExportResolution
import org.litvin.i18n.AppLanguage
import org.litvin.points.CommentV1
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.points.ReplayV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreV1
import java.awt.geom.Rectangle2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Covers the export preview with the settings of the Export tab (B-75). Each combination of the settings goes through
 * [ExportPlanner.buildRenderPlan], as the Preview button does. The test then checks the preview against the rules of
 * the export: the content, the order of the parts, the total time, the cards, the scoreboard, and the comments.
 *
 * The project has two sets. Set 1 has 24 points (6-0) and is complete. Set 2 has 4 points. Point 2 is a favorite and
 * has a replay with a replay comment. Point 26 is not a favorite and has a replay. A point comment is in point 2, and
 * a comment is before the first point.
 */
class ExportPreviewSettingsTest {
    private enum class Content { FULL, POINTS, FAVORITES }

    private data class Settings(
        val content: Content,
        val scoreboard: Boolean,
        val comments: Boolean,
        val statsCard: Boolean,
        val setCards: Boolean,
        val replays: Boolean,
        val trim: Boolean,
    )

    private val sourceMs = 300_000L
    private val pointCount = 28
    private val favorites = setOf(2, 10, 23, 27)
    private fun startOf(index: Int) = 10_000 + index * 10_000
    private fun endOf(index: Int) = startOf(index) + POINT_MS.toInt()

    /** The trim of the full video. It removes the comment before the first point, and the replay of point 26. */
    private val trim = AdjustmentsV1(trimStartMs = 5_000, trimEndMs = 272_000)

    private val area = Rectangle2D.Double(0.0, 0.0, 1920.0, 1080.0)

    private fun edl(replays: Boolean) = EdlV1(
        points = List(pointCount) { index ->
            PointV1(
                id = "p$index",
                startMs = startOf(index),
                endMs = endOf(index),
                favorite = index in favorites,
                replays = when {
                    !replays -> emptyList()
                    index == 2 -> listOf(
                        ReplayV1(
                            startOf(2) + 2_000,
                            endOf(2),
                            speed = 0.5,
                            comments = listOf(CommentV1(1, startOf(2) + 2_200, 1_000, REPLAY_COMMENT)),
                        ),
                    )
                    index == 26 -> listOf(ReplayV1(startOf(26) + 2_000, endOf(26), speed = 0.5))
                    else -> emptyList()
                },
            )
        },
        comments = listOf(
            CommentV1(1, 3_000, 1_500, EARLY_COMMENT),
            CommentV1(2, startOf(2) + 1_000, 1_500, POINT_COMMENT),
        ),
        nextCommentId = 3,
    )

    /** Player 1 wins all points: set 1 is 6-0 after point 23, and set 2 is 1-0 after point 27. */
    private val score = ScoreV1(outcomes = List(pointCount) { "p$it" to Outcome.P1 }.toMap(), player1Name = "Anna", player2Name = "Bea")

    private fun plan(settings: Settings) = ExportPlanner.buildRenderPlan(
        ExportRenderPlanRequest(
            manifest = null,
            sourcePath = "source.mp4",
            edl = edl(settings.replays),
            score = score,
            preset = ExportPreset(id = "balanced", label = "Balanced"),
            resolution = ExportResolution("1080p", 1920, 1080),
            encoderLabel = "test",
            idleTrim = settings.content != Content.FULL,
            favoriteOnly = settings.content == Content.FAVORITES,
            includeScoreboard = settings.scoreboard,
            outputPath = "",
            includeComments = settings.comments,
            sourceDurationMs = sourceMs,
            includeStatsCard = settings.statsCard,
            includeSetSummaries = settings.setCards,
            adjustments = if (settings.trim) trim else AdjustmentsV1(),
            videoLanguage = AppLanguage.ENGLISH,
        ),
    )

    private val allSettings: List<Settings> = buildList {
        for (content in Content.entries) for (scoreboard in BOTH) for (comments in BOTH) for (statsCard in BOTH)
            for (setCards in BOTH) for (replays in BOTH) for (trim in BOTH) {
                add(Settings(content, scoreboard, comments, statsCard, setCards, replays, trim))
            }
    }

    @Test
    fun eachCombinationOfSettingsGivesThePartsOfTheExport() {
        for (settings in allSettings) {
            val job = plan(settings).job
            val timeline = ExportPreviewTimeline.of(job, sourceMs)
            checkParts(settings, job, timeline)
        }
    }

    @Test
    fun eachCombinationOfSettingsShowsTheOverlaysOfTheExport() {
        // The board of a full video without a trim is the reference: the board of a point is the same in each content.
        val reference = Settings(Content.FULL, true, false, false, false, false, false)
        val referenceJob = plan(reference).job
        val referenceTimeline = ExportPreviewTimeline.of(referenceJob, sourceMs)
        val boards = (0 until pointCount).associateWith { index ->
            boardAt(referenceJob, referenceTimeline, middleOf(index)) ?: fail("The full video has no frame of point $index")
        }
        assertTrue(boards.values.all { it.isNotEmpty() })
        // The board changes with the score.
        assertTrue(boards[0] != boards[5])

        for (settings in allSettings) {
            val job = plan(settings).job
            val timeline = ExportPreviewTimeline.of(job, sourceMs)
            checkOverlays(settings, job, timeline, boards)
        }
    }

    private fun checkParts(settings: Settings, job: RenderJob, timeline: ExportPreviewTimeline) {
        val label = settings.toString()
        val items = timeline.items
        val videos = items.filterIsInstance<ExportPreviewTimeline.Video>()
        val replays = items.filterIsInstance<ExportPreviewTimeline.Replay>()
        val cards = items.filterIsInstance<ExportPreviewTimeline.Card>()
        val full = settings.content == Content.FULL
        val trimmed = full && settings.trim

        // The parts follow each other with no gap.
        items.forEachIndexed { index, item ->
            val expectedStart = if (index == 0) 0L else items[index - 1].outEndMs
            assertEquals(expectedStart, item.outStartMs, "$label: the start of part $index")
        }

        // The kept points, in source order. A full video is the whole source, or the part between the trim ends.
        val kept = when (settings.content) {
            Content.FULL -> emptyList()
            Content.POINTS -> (0 until pointCount).toList()
            Content.FAVORITES -> favorites.sorted()
        }
        if (full) {
            assertTrue(videos.all { it.pointId == null }, label)
            assertEquals(if (trimmed) 5_000L else 0L, videos.first().sourceStartMs, label)
            assertEquals(if (trimmed) 272_000L else sourceMs, videos.last().sourceEndMs, label)
            videos.zipWithNext().forEach { (a, b) -> assertEquals(a.sourceEndMs, b.sourceStartMs, "$label: a hole in the full video") }
        } else {
            assertEquals(kept.map { "p$it" }, videos.map { it.pointId }, label)
            assertEquals(kept.map { startOf(it).toLong() to endOf(it).toLong() }, videos.map { it.sourceStartMs to it.sourceEndMs }, label)
        }

        // The replays: all replays for a full video and for "Only points", the replays of the favorites for "Only
        // favorites". A trim removes the replays of the points after its end.
        val expectedReplays = when {
            !settings.replays -> emptyList()
            settings.content == Content.FAVORITES -> listOf("p2")
            trimmed -> listOf("p2")
            else -> listOf("p2", "p26")
        }
        assertEquals(expectedReplays, replays.map { it.replay.pointId }, label)
        for (replay in replays) {
            val before = items.subList(0, items.indexOf(replay)).last { it !is ExportPreviewTimeline.Replay }
            val video = assertIs<ExportPreviewTimeline.Video>(before, "$label: a replay goes after its point")
            if (full) {
                assertEquals(replay.replay.pointEndMs.toLong(), video.sourceEndMs, "$label: the full video cuts at the point end")
            } else {
                assertEquals(replay.replay.pointId, video.pointId, label)
            }
            assertEquals(4_000L, replay.durationMs, label)
        }

        // The cards: a set card only for an export of points, after the last point of set 1 in the content. The match
        // card is the last part. Each card shows the last frame of the video before it.
        val setCards = if (settings.setCards && !full) 1 else 0
        val matchCards = if (settings.statsCard) 1 else 0
        assertEquals(setCards, cards.count { it.setNumber != null }, label)
        assertEquals(matchCards, cards.count { it.setNumber == null }, label)
        if (matchCards == 1) assertEquals(null, assertIs<ExportPreviewTimeline.Card>(items.last(), label).setNumber, label)
        for (card in cards) {
            val video = items.subList(0, items.indexOf(card)).filterIsInstance<ExportPreviewTimeline.Video>().last()
            assertEquals(video.sourceEndMs, card.freezeAtMs ?: sourceMs, "$label: the frame of the card")
            if (card.setNumber != null) assertEquals("p23", video.pointId, "$label: the set card follows the last point of set 1")
        }

        // The total time is the time of the export: the video, the replays, and the cards.
        val videoMs = when {
            trimmed -> 267_000L
            full -> sourceMs
            else -> kept.size * POINT_MS
        }
        val cardsMs = (job.statsCard?.durationMs ?: 0L) + job.setSummaries.sumOf { it.card.durationMs }
        assertEquals(videoMs + expectedReplays.size * 4_000L + cardsMs, timeline.durationMs, label)

        // The note about the stops shows when the output has a cut.
        assertEquals(!full || replays.isNotEmpty() || cards.isNotEmpty(), timeline.hasCuts, label)
    }

    private fun checkOverlays(settings: Settings, job: RenderJob, timeline: ExportPreviewTimeline, boards: Map<Int, List<String>>) {
        val label = settings.toString()
        val full = settings.content == Content.FULL
        val shownPoints = when (settings.content) {
            Content.FULL -> (0 until pointCount).filter { !settings.trim || endOf(it) <= 272_000 }
            Content.POINTS -> (0 until pointCount).toList()
            Content.FAVORITES -> favorites.sorted()
        }

        // The board shows only with the scoreboard setting. It shows the same score as the full video at that frame.
        for (index in shownPoints) {
            val board = boardAt(job, timeline, middleOf(index)) ?: fail("$label: no frame of point $index")
            if (settings.scoreboard) {
                assertEquals(boards.getValue(index), board, "$label: the board of point $index")
            } else {
                assertTrue(board.isEmpty(), "$label: a board without the scoreboard setting")
            }
        }

        // The point comment shows in its point. The comment before the first point shows only in a full video without
        // a trim.
        val inComment = textsAt(job, timeline, startOf(2) + 1_700L).orEmpty()
        val afterComment = textsAt(job, timeline, startOf(2) + 3_000L).orEmpty()
        assertEquals(settings.comments, inComment.any { POINT_COMMENT in it }, "$label: the point comment")
        assertTrue(afterComment.none { POINT_COMMENT in it }, "$label: the point comment after its end")
        val early = textsAt(job, timeline, 3_500L).orEmpty()
        assertEquals(settings.comments && full && !settings.trim, early.any { EARLY_COMMENT in it }, "$label: the comment before the first point")

        // A replay shows its badge, its replay comment, and the point comment of its part. It does not show the board.
        timeline.items.filterIsInstance<ExportPreviewTimeline.Replay>().filter { it.replay.pointId == "p2" }.forEach { replay ->
            val events = ExportPreviewOverlay.events(job, PreviewOverlay.Replay(replay, 800), area)
            val badge = assertNotNull(replay.replay.badgeText, label)
            assertTrue(events.any { badge in it }, "$label: the replay badge")
            assertEquals(settings.comments, events.any { REPLAY_COMMENT in it }, "$label: the replay comment")
            assertEquals(settings.comments, events.any { POINT_COMMENT in it }, "$label: the point comment in the replay")
        }

        // Each card shows its pages.
        timeline.items.filterIsInstance<ExportPreviewTimeline.Card>().forEach { card ->
            assertTrue(ExportPreviewOverlay.events(job, PreviewOverlay.Card(card, 0), area).isNotEmpty(), "$label: the card")
        }
    }

    /** The board events at [sourceMs], or null when the output has no frame at that source time. */
    private fun boardAt(job: RenderJob, timeline: ExportPreviewTimeline, sourceMs: Long): List<String>? {
        val video = videoAt(timeline, sourceMs) ?: return null
        return ExportPreviewOverlay.events(job.copy(includeComments = false), PreviewOverlay.Video(video.overlayMsAt(sourceMs)), area)
    }

    /** All overlay events at [sourceMs], or null when the output has no frame at that source time. */
    private fun textsAt(job: RenderJob, timeline: ExportPreviewTimeline, sourceMs: Long): List<String>? {
        val video = videoAt(timeline, sourceMs) ?: return null
        return ExportPreviewOverlay.events(job, PreviewOverlay.Video(video.overlayMsAt(sourceMs)), area)
    }

    private fun videoAt(timeline: ExportPreviewTimeline, sourceMs: Long): ExportPreviewTimeline.Video? =
        timeline.items.filterIsInstance<ExportPreviewTimeline.Video>().firstOrNull { sourceMs >= it.sourceStartMs && sourceMs < it.sourceEndMs }

    private fun middleOf(index: Int) = startOf(index) + POINT_MS / 2

    private companion object {
        const val POINT_MS = 4_000L
        const val EARLY_COMMENT = "Before the first point"
        const val POINT_COMMENT = "Point comment"
        const val REPLAY_COMMENT = "Replay comment"
        val BOTH = listOf(false, true)
    }
}
