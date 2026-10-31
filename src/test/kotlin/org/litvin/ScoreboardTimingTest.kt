package org.litvin

import org.litvin.export.ExportTrim
import org.litvin.points.PointV1
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardEnd
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStart
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-63: the start and the end settings of the scoreboard in a full video and in the Points tab preview. */
class ScoreboardTimingTest {
    private fun pt(id: String, s: Int, e: Int) = PointV1(id = id, startMs = s, endMs = e)

    // Three points of a match that has not ended. The first scored point starts at 10 s, the last point ends at 40 s.
    private val points = listOf(pt("A", 10_000, 15_000), pt("B", 20_000, 25_000), pt("C", 35_000, 40_000))
    private val outcomes = points.associate { it.id to Outcome.P1 }

    // A best of 1 set match that player 1 wins 6–0: 24 points. The match point ends at 23.5 s.
    private val matchPoints = (0..23).map { pt("M$it", it * 1_000, it * 1_000 + 500) }
    private val matchOutcomes = matchPoints.associate { it.id to Outcome.P1 }
    private val oneSet = MatchRulesV1(bestOfSets = 1)

    private fun full(
        timing: ScoreboardTiming,
        preview: Boolean = false,
        videoEndMs: Long? = null,
        list: List<PointV1> = points,
        wins: Map<String, Outcome> = outcomes,
        rules: MatchRulesV1 = MatchRulesV1(),
    ) = ScoreboardTimelineBuilder.build(
        list, wins, idleTrim = false, rules = rules, fullVideo = true, timing = timing, preview = preview, videoEndMs = videoEndMs,
    )

    @Test
    fun `the default board starts at the first point and ends 20 s after the last point, with fades`() {
        val spans = full(ScoreboardTiming())
        assertEquals(10_000L, spans.first().startMs)
        assertEquals(60_000L, spans.last().endMs)
        assertEquals(ScoreboardTimelineBuilder.FADE_MS, spans.first().fadeInMs)
        assertEquals(ScoreboardTimelineBuilder.FADE_MS, spans.last().fadeOutMs)
    }

    @Test
    fun `the video start shows the board from 0 with no fade`() {
        val spans = full(ScoreboardTiming(start = ScoreboardStart.VIDEO_START))
        assertEquals(0L, spans.first().startMs)
        assertEquals(0L, spans.first().fadeInMs)
    }

    @Test
    fun `a fixed start shows the board from that time with a fade, also after the first point`() {
        val early = full(ScoreboardTiming(start = ScoreboardStart.FIXED_TIME, startMs = 4_000))
        assertEquals(4_000L, early.first().startMs)
        assertEquals(ScoreboardTimelineBuilder.FADE_MS, early.first().fadeInMs)

        val late = full(ScoreboardTiming(start = ScoreboardStart.FIXED_TIME, startMs = 22_000))
        assertEquals(22_000L, late.first().startMs)
        assertTrue(late.none { it.startMs < 22_000 })
    }

    @Test
    fun `each end after the last point ends the board at its time`() {
        mapOf(
            ScoreboardEnd.AT_ONCE to 40_000L,
            ScoreboardEnd.AFTER_5_S to 45_000L,
            ScoreboardEnd.AFTER_10_S to 50_000L,
            ScoreboardEnd.AFTER_20_S to 60_000L,
        ).forEach { (end, endMs) ->
            val spans = full(ScoreboardTiming(end = end))
            assertEquals(endMs, spans.last().endMs, end.name)
            assertTrue(spans.last().fadeOutMs > 0, end.name)
        }
    }

    @Test
    fun `the video end shows the board until the end of the video with no fade`() {
        val spans = full(ScoreboardTiming(end = ScoreboardEnd.VIDEO_END), videoEndMs = 90_000)
        assertEquals(90_000L, spans.last().endMs)
        assertEquals(0L, spans.last().fadeOutMs)

        // Without the video length, the board has no end.
        assertEquals(Int.MAX_VALUE.toLong(), full(ScoreboardTiming(end = ScoreboardEnd.VIDEO_END)).last().endMs)
    }

    @Test
    fun `a fixed end before the last point cuts the board, and a fixed end after the video end is the video end`() {
        val early = full(ScoreboardTiming(end = ScoreboardEnd.FIXED_TIME, endMs = 30_000), videoEndMs = 90_000)
        assertEquals(30_000L, early.last().endMs)
        assertEquals(ScoreboardTimelineBuilder.FADE_MS, early.last().fadeOutMs)

        val late = full(ScoreboardTiming(end = ScoreboardEnd.FIXED_TIME, endMs = 120_000), videoEndMs = 90_000)
        assertEquals(90_000L, late.last().endMs)
        assertEquals(0L, late.last().fadeOutMs)
    }

    @Test
    fun `a fixed start after the fixed end gives no board`() {
        val timing = ScoreboardTiming(ScoreboardStart.FIXED_TIME, 30_000, ScoreboardEnd.FIXED_TIME, 20_000)
        assertTrue(full(timing).isEmpty())
    }

    @Test
    fun `the preview keeps the board until the video ends while the match has not ended`() {
        assertEquals(Int.MAX_VALUE.toLong(), full(ScoreboardTiming(), preview = true).last().endMs)
        assertEquals(Int.MAX_VALUE.toLong(), full(ScoreboardTiming(end = ScoreboardEnd.AT_ONCE), preview = true).last().endMs)
        // Sets with no match end: the match never ends by the score.
        val noEnd = full(ScoreboardTiming(), preview = true, rules = MatchRulesV1(bestOfSets = MatchRulesV1.NO_MATCH_END))
        assertEquals(Int.MAX_VALUE.toLong(), noEnd.last().endMs)
    }

    @Test
    fun `the export of an incomplete markup hides the board 20 s after the last point`() {
        assertEquals(60_000L, full(ScoreboardTiming(), videoEndMs = 90_000).last().endMs)
    }

    @Test
    fun `the preview of an ended match ends the board as the export`() {
        val spans = full(ScoreboardTiming(), preview = true, list = matchPoints, wins = matchOutcomes, rules = oneSet)
        assertEquals(43_500L, spans.last().endMs)
    }

    @Test
    fun `the preview with points after the match end ends the board as the export`() {
        val list = matchPoints + pt("X", 30_000, 31_000)
        val spans = full(ScoreboardTiming(end = ScoreboardEnd.AFTER_5_S), preview = true, list = list, wins = matchOutcomes + ("X" to Outcome.P2), rules = oneSet)
        assertEquals(36_000L, spans.last().endMs)
    }

    @Test
    fun `the preview follows a fixed time and the video end exactly`() {
        val fixed = full(ScoreboardTiming(end = ScoreboardEnd.FIXED_TIME, endMs = 30_000), preview = true)
        assertEquals(30_000L, fixed.last().endMs)
        val start = full(ScoreboardTiming(start = ScoreboardStart.FIXED_TIME, startMs = 22_000), preview = true)
        assertEquals(22_000L, start.first().startMs)
    }

    @Test
    fun `the Only points export ignores the start and the end settings`() {
        val timing = ScoreboardTiming(ScoreboardStart.VIDEO_START, null, ScoreboardEnd.VIDEO_END, null)
        val plain = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true)
        val withTiming = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true, timing = timing)
        assertEquals(plain, withTiming)
    }

    @Test
    fun `the trim removes the fade at the cut`() {
        val spans = full(ScoreboardTiming(start = ScoreboardStart.FIXED_TIME, startMs = 4_000), videoEndMs = 90_000)
        val trimmed = ExportTrim(startMs = 12_000, endMs = 50_000).overlays(spans)
        assertEquals(0L, trimmed.first().startMs)
        assertEquals(0L, trimmed.first().fadeInMs)
        assertEquals(38_000L, trimmed.last().endMs)
        assertEquals(0L, trimmed.last().fadeOutMs)
    }

    @Test
    fun `the default drops a fixed time`() {
        val settings = ScoreboardSettingsV1(start = ScoreboardStart.FIXED_TIME, startMs = 5_000, end = ScoreboardEnd.FIXED_TIME, endMs = 60_000)
        val default = settings.forDefault()
        assertEquals(ScoreboardStart.FIRST_POINT, default.start)
        assertNull(default.startMs)
        assertEquals(ScoreboardEnd.AFTER_20_S, default.end)
        assertNull(default.endMs)

        val other = ScoreboardSettingsV1(start = ScoreboardStart.VIDEO_START, end = ScoreboardEnd.AFTER_5_S).forDefault()
        assertEquals(ScoreboardStart.VIDEO_START, other.start)
        assertEquals(ScoreboardEnd.AFTER_5_S, other.end)
    }

    @Test
    fun `the fixed time check finds a start after the end`() {
        val fixed = ScoreboardSettingsV1(start = ScoreboardStart.FIXED_TIME, startMs = 30_000, end = ScoreboardEnd.FIXED_TIME, endMs = 20_000)
        assertTrue(fixed.hasFixedStartAfterEnd())
        assertFalse(fixed.copy(endMs = 40_000).hasFixedStartAfterEnd())
        assertFalse(fixed.copy(end = ScoreboardEnd.AFTER_20_S).hasFixedStartAfterEnd())
    }

    @Test
    fun `a fixed time without a time becomes the default`() {
        val settings = ScoreboardSettingsV1(start = ScoreboardStart.FIXED_TIME, end = ScoreboardEnd.FIXED_TIME).normalized()
        assertEquals(ScoreboardStart.FIRST_POINT, settings.start)
        assertEquals(ScoreboardEnd.AFTER_20_S, settings.end)
        // A time of another option goes away.
        assertNull(ScoreboardSettingsV1(startMs = 5_000).normalized().startMs)
    }

    @Test
    fun `score json keeps the start and the end settings, and an old file gets the defaults`() {
        val dir = Files.createTempDirectory("b63").toFile()
        try {
            val board = ScoreboardSettingsV1(start = ScoreboardStart.FIXED_TIME, startMs = 5_000, end = ScoreboardEnd.VIDEO_END)
            ScoreIO.writeForProjectDir(dir.path, ScoreV1(scoreboard = board))
            val read = ScoreIO.readForProjectDir(dir.path).scoreboard
            assertEquals(ScoreboardStart.FIXED_TIME, read.start)
            assertEquals(5_000L, read.startMs)
            assertEquals(ScoreboardEnd.VIDEO_END, read.end)

            ScoreIO.scoreFilePath(dir.path).let { java.io.File(it).writeText("""{"scoreboard":{"style":"MINIMAL"}}""") }
            val old = ScoreIO.readForProjectDir(dir.path).scoreboard
            assertEquals(ScoreboardStart.FIRST_POINT, old.start)
            assertEquals(ScoreboardEnd.AFTER_20_S, old.end)
        } finally {
            dir.deleteRecursively()
        }
    }
}
