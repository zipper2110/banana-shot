package org.litvin.scoring

import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreOverrideTest {
    private fun points(count: Int) = (1..count).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }

    private fun timeline(
        points: List<PointV1>,
        outcomes: Map<String, Outcome>,
        overrides: Map<String, ScoreOverrideV1>,
        rules: MatchRulesV1 = MatchRulesV1(),
    ) = ScoringEngine.timeline(points, outcomes, rules, ManualScoreMarks(overrides = overrides))

    @Test
    fun theNextPointsCountFromTheOverride() {
        val points = points(3)
        val outcomes = mapOf("p1" to Outcome.P1, "p2" to Outcome.P1, "p3" to Outcome.P1)
        val line = timeline(points, outcomes, mapOf("p1" to ScoreOverrideV1(games = ScorePairV1(3, 2), points = ScorePairV1(2, 0))))

        assertEquals(3, line.statesAfterPoint[0].gamesP1)
        assertEquals(2, line.statesAfterPoint[0].gamesP2)
        assertEquals(2, line.statesAfterPoint[0].p1Pts)
        // 30–0, then 40–0, then the game: 4–2.
        assertEquals(3, line.statesAfterPoint[1].p1Pts)
        assertEquals(4, line.statesAfterPoint[2].gamesP1)
        assertEquals(1, line.statesAfterPoint[2].lastGameWonBy)
    }

    @Test
    fun aPartWithoutAnOverrideStaysComputed() {
        val overrides = mapOf("p3" to ScoreOverrideV1(games = ScorePairV1(4, 4)))
        val outcomes = mapOf("p1" to Outcome.P1, "p2" to Outcome.P1, "p3" to Outcome.P2)
        val before = timeline(points(3), outcomes, overrides).statesAfterPoint[2]
        assertEquals(4, before.gamesP1)
        assertEquals(2, before.p1Pts)
        assertEquals(1, before.p2Pts)

        // Without the first point, the games stay 4–4, but the points change.
        val after = timeline(points(3).drop(1), outcomes, overrides).statesAfterPoint[1]
        assertEquals(4, after.gamesP1)
        assertEquals(4, after.gamesP2)
        assertEquals(1, after.p1Pts)
        assertEquals(1, after.p2Pts)
    }

    @Test
    fun theSetsGiveTheSetCountAndTheCompletedSets() {
        val override = ScoreOverrideV1(sets = listOf(SetGamesV1(6, 4), SetGamesV1(6, 7, tiebreakP1 = 5, tiebreakP2 = 7)))
        val line = timeline(points(1), emptyMap(), mapOf("p1" to override), MatchRulesV1(bestOfSets = 5))

        val state = line.statesAfterPoint[0]
        assertEquals(1, state.setsP1)
        assertEquals(1, state.setsP2)
        assertEquals(
            listOf(ScoringEngine.SetScore(6, 4, false), ScoringEngine.SetScore(6, 7, true, 5, 7)),
            line.setsAfterPoint[0],
        )
    }

    @Test
    fun gamesAtTheTiebreakScoreStartATiebreak() {
        val outcomes = mapOf("p2" to Outcome.P1)
        val line = timeline(points(2), outcomes, mapOf("p1" to ScoreOverrideV1(games = ScorePairV1(6, 6))))

        assertTrue(line.statesAfterPoint[0].isTiebreak)
        assertEquals(1, line.statesAfterPoint[1].p1Pts)
        assertTrue(line.statesAfterPoint[1].isTiebreak)
    }

    @Test
    fun setsThatWinTheMatchEndIt() {
        val override = ScoreOverrideV1(sets = listOf(SetGamesV1(6, 1), SetGamesV1(6, 2)))
        val line = timeline(points(2), mapOf("p2" to Outcome.P1), mapOf("p1" to override))

        // The match is over, so the next point does not count.
        assertEquals(line.statesAfterPoint[0], line.statesAfterPoint[1].copy(lastGameWonBy = null))
        assertFalse(line.statesAfterPoint[1].isTiebreak)
    }

    @Test
    fun theScoreFileKeepsTheOverrides() {
        val override = ScoreOverrideV1(sets = listOf(SetGamesV1(7, 6, 7, 3)), points = ScorePairV1(3, 4))
        val score = ScoreV1(scoreOverrides = mapOf("p1" to override))
        assertEquals(score.scoreOverrides, score.manualMarks().overrides)

        val file = kotlin.io.path.createTempFile("score", ".json").toFile()
        try {
            ScoreIO.write(file.path, score)
            assertEquals(score.scoreOverrides, ScoreIO.read(file.path).scoreOverrides)
        } finally {
            file.delete()
        }
    }
}
