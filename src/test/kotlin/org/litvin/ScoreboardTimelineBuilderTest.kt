package org.litvin

import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreboardTimelineBuilderTest {
    private fun pts(id: String, s: Int, e: Int) = PointV1(id = id, startMs = s, endMs = e)

    @Test
    fun timeline_idleTrim_ON_aligns_to_concatenated_output_and_carry_forward() {
        val points = listOf(
            pts("A", 0, 1_000),
            pts("B", 2_000, 3_000),
            pts("C", 4_000, 5_000),
        )
        val outcomes = mapOf(
            "A" to Outcome.P1,
            // B is intentionally missing → carry-forward state (no change)
            "C" to Outcome.P2,
        )
        val spans = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true, player1Name = "Alice", player2Name = "Bob")
        // Expect three output intervals back-to-back with total 3 seconds
        assertEquals(3, spans.size)
        assertEquals(0L, spans[0].startMs); assertEquals(1_000L, spans[0].endMs)
        assertEquals(1_000L, spans[1].startMs); assertEquals(2_000L, spans[1].endMs)
        assertEquals(2_000L, spans[2].startMs); assertEquals(3_000L, spans[2].endMs)
        // Names should be threaded through
        assertEquals("Alice", spans[0].p1Name); assertEquals("Bob", spans[0].p2Name)
        // Text progression: before first point completes → 0; before second → P1 has 15; before third → still carry-forward 15
        assertTrue(spans[0].text.contains("Player 1: "))
        assertTrue(spans[0].text.contains("pts 0"))
        assertTrue(spans[1].text.contains("pts 15"), "Expected P1 points to be 15 before point B")
        assertTrue(spans[2].text.contains("pts 15"), "Carry-forward expected before point C outcome is applied at its end")
    }

    @Test
    fun aBlankNameGetsTheDefaultSideNameOfTheSport() {
        val points = listOf(pts("A", 0, 1_000))
        val padel = ScoreboardTimelineBuilder.build(points, emptyMap(), idleTrim = false, player1Name = " ", player2Name = "", sport = Sport.PADEL)
        val tennis = ScoreboardTimelineBuilder.build(points, emptyMap(), idleTrim = false, player1Name = "", player2Name = "Bob")

        assertEquals("Team 1" to "Team 2", padel[0].p1Name to padel[0].p2Name)
        assertEquals("Player 1" to "Bob", tennis[0].p1Name to tennis[0].p2Name)
    }

    @Test
    fun timeline_idleTrim_OFF_uses_source_time_domain_boundaries() {
        val points = listOf(
            pts("A", 0, 1_000),
            pts("B", 2_000, 3_000),
            pts("C", 4_000, 5_000),
        )
        val spans = ScoreboardTimelineBuilder.build(points, outcomes = emptyMap(), idleTrim = false)
        // Expect spans to fill from 0 to last end with boundaries at original end times
        assertEquals(3, spans.size)
        assertEquals(0L, spans[0].startMs); assertEquals(1_000L, spans[0].endMs)
        assertEquals(1_000L, spans[1].startMs); assertEquals(3_000L, spans[1].endMs)
        assertEquals(3_000L, spans[2].startMs); assertEquals(5_000L, spans[2].endMs)
    }

    @Test
    fun sourcePointSpans_coverActualPointStartForPausedPreviewSeeking() {
        val points = listOf(
            pts("A", 500, 1_000),
            pts("B", 2_000, 3_000),
            pts("C", 4_000, 5_000),
        )
        val spans = ScoreboardTimelineBuilder.buildSourcePointSpans(
            points = points,
            outcomes = mapOf("A" to Outcome.P1),
        )

        assertEquals(3, spans.size)
        assertEquals(250L, spans[0].startMs); assertEquals(1_000L, spans[0].endMs)
        assertEquals(1_750L, spans[1].startMs); assertEquals(3_000L, spans[1].endMs)
        assertEquals(3_750L, spans[2].startMs); assertEquals(5_000L, spans[2].endMs)
        assertTrue(spans[1].text.contains("Player 1: pts 15"), "Preview span should keep score-before-point semantics: ${spans[1].text}")
        assertTrue(spans[1].text.contains("Player 2: pts 0"), "Preview span should not include point B outcome: ${spans[1].text}")
    }

    @Test
    fun timeline_idleTrim_OFF_currentPointOutcome_doesNotChangeCurrentPointSpan() {
        val points = listOf(
            pts("A", 0, 1_000),
            pts("B", 2_000, 3_000),
            pts("C", 4_000, 5_000),
        )
        val spans = ScoreboardTimelineBuilder.build(
            points = points,
            outcomes = mapOf(
                "A" to Outcome.P1,
                "B" to Outcome.P2,
            ),
            idleTrim = false,
        )

        assertEquals(3, spans.size)
        assertTrue(spans[1].text.contains("Player 1: pts 15"), "Point B should show score before B outcome: ${spans[1].text}")
        assertTrue(spans[1].text.contains("Player 2: pts 0"), "Point B should not include B outcome yet: ${spans[1].text}")
        assertTrue(spans[2].text.contains("Player 1: pts 15"), "Point C should include previous P1 outcome: ${spans[2].text}")
        assertTrue(spans[2].text.contains("Player 2: pts 15"), "Point C should include previous P2 outcome: ${spans[2].text}")
    }

    @Test
    fun timeline_ignoresOutcomeForDeletedOrMissingPoint() {
        val points = listOf(
            pts("A", 0, 1_000),
            pts("C", 4_000, 5_000),
        )
        val spans = ScoreboardTimelineBuilder.build(
            points = points,
            outcomes = mapOf(
                "A" to Outcome.P1,
                "B-deleted" to Outcome.P1,
            ),
            idleTrim = false,
        )

        assertEquals(2, spans.size)
        assertTrue(spans[1].text.contains("Player 1: pts 15"), "Deleted point outcome should not be counted: ${spans[1].text}")
        assertTrue(spans[1].text.contains("Player 2: pts 0"), "Deleted point outcome should not affect player 2 either: ${spans[1].text}")
    }

    @Test
    fun timeline_advances_games_when_enough_points_to_win_game() {
        // Four consecutive P1 point wins → one game up, then displays 0 points at next interval
        val points = listOf(
            pts("P1", 0, 1_000),
            pts("P2", 1_100, 2_100),
            pts("P3", 2_200, 3_200),
            pts("P4", 3_300, 4_300),
            pts("P5", 4_400, 5_400),
        )
        val outcomes = mapOf(
            "P1" to Outcome.P1,
            "P2" to Outcome.P1,
            "P3" to Outcome.P1,
            "P4" to Outcome.P1, // game should increment after this completes
        )
        val spans = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true)
        // After four points are applied, the fifth span should show gamesP1 == 1 and points reset
        assertEquals(5, spans.size)
        // The FIFTH span text is computed before P5 outcome → after P4 applied → new game started
        val t5 = spans[4].text
        assertTrue(t5.contains("games 1"), "Expected gamesP1 to be 1 in span 5 text: $t5")
        assertTrue(t5.contains("Player 1: "))
        // And points should be reset to 0/0, so avoid showing 15/30/40/Ad; check for "pts 0"
        assertTrue(t5.contains("pts 0"), "Expected points reset in new game: $t5")
    }

    @Test
    fun timeline_exportFilter_emitsOnlyFavoritesButScoresFromAllPoints() {
        val points = listOf(
            pts("A", 0, 1_000),
            pts("B", 2_000, 3_000),
            pts("C", 4_000, 5_000),
        )
        val outcomes = mapOf(
            "A" to Outcome.P1,
            "B" to Outcome.P1,
        )

        val spans = ScoreboardTimelineBuilder.build(
            points = points,
            outcomes = outcomes,
            idleTrim = true,
            exportedPointIds = setOf("C"),
        )

        assertEquals(1, spans.size)
        assertEquals(0L, spans[0].startMs)
        assertEquals(1_000L, spans[0].endMs)
        assertTrue(spans[0].text.contains("pts 30"), "Expected score before C to include A and B outcomes: ${spans[0].text}")
    }

    @Test
    fun spansAfterTheMatchPointAreMarkedAsMatchEnded() {
        // Player 1 wins 6–0, 6–0 in a best of 3 match: 48 points. One more point follows the match point.
        val points = (0..48).map { pts("P$it", it * 1_000, it * 1_000 + 500) }
        val outcomes = points.associate { it.id to Outcome.P1 }

        val spans = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = false)

        assertFalse(spans[47].matchEnded, "The match point itself is still part of the match")
        assertTrue(spans[48].matchEnded)
        assertEquals(listOf(6 to 0, 6 to 0), spans[48].completedSets)
    }

    @Test
    fun fullVideoStartsAtTheFirstScoredPointAndEndsTwentySecondsAfterTheLastPoint() {
        // A is a warm-up point without a winner. B and C have winners.
        val points = listOf(pts("A", 2_000, 5_000), pts("B", 10_000, 15_000), pts("C", 20_000, 26_000))
        val outcomes = mapOf("B" to Outcome.P1, "C" to Outcome.P1)

        val spans = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = false, fullVideo = true)

        assertEquals(listOf(10_000L to 15_000L, 15_000L to 26_000L, 26_000L to 46_000L), spans.map { it.startMs to it.endMs })
        assertEquals(listOf(500L, 0L, 0L), spans.map { it.fadeInMs })
        assertEquals(listOf(0L, 0L, 500L), spans.map { it.fadeOutMs })
        // The last span shows the score after C: 30-0.
        assertEquals(2 to 0, spans.last().p1Pts to spans.last().p2Pts)
    }

    @Test
    fun fullVideoWithoutAScoredPointHasNoBoard() {
        val points = listOf(pts("A", 0, 1_000), pts("B", 2_000, 3_000))

        assertTrue(ScoreboardTimelineBuilder.build(points, emptyMap(), idleTrim = false, fullVideo = true).isEmpty())
    }

    @Test
    fun highlightsHaveNoFade() {
        val points = listOf(pts("A", 0, 1_000), pts("B", 2_000, 3_000))
        val outcomes = mapOf("A" to Outcome.P1, "B" to Outcome.P2)

        val spans = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true)

        assertTrue(spans.all { it.fadeInMs == 0L && it.fadeOutMs == 0L })
        assertEquals(2, spans.size)
    }
}
