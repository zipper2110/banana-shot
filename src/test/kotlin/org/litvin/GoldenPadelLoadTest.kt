package org.litvin

import org.litvin.points.EdlIO
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.FinalSetRule
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoringEngine
import org.litvin.scoring.Sport
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-14: a padel project as the app writes it. The file must load with the same sport, rules, names, and score. */
class GoldenPadelLoadTest {
    private val base = File(this::class.java.classLoader.getResource("golden/padel/score.json")!!.toURI()).parentFile

    @Test
    fun goldenPadelProjectLoadsWithItsSportRulesAndTeamNames() {
        val score = ScoreIO.read(File(base, "score.json").absolutePath)

        assertEquals(Sport.PADEL, score.sport)
        assertEquals(DeuceRule.STAR_POINT, score.rules.deuce)
        assertEquals(FinalSetRule.MATCH_TIEBREAK, score.rules.finalSet)
        assertEquals("Lucía / Begoña", score.player1Name)
        assertEquals("Carla / Inés", score.player2Name)
    }

    @Test
    fun goldenPadelProjectScoresTheStarPointGame() {
        val edl = EdlIO.read(File(base, "edl.json").absolutePath)
        val score = ScoreIO.read(File(base, "score.json").absolutePath)
        val points = edl.points.sortedBy { it.startMs }

        val timeline = ScoringEngine.timeline(points, score.outcomes, score.rules, score.manualMarks(), score.serverMarks)

        // Game 1 goes to the third deuce. Team 2 wins the star point (the 11th point).
        val starPoint = points.indexOfFirst { it.id == "g1p11" }
        assertTrue(ScoringEngine.isDecidingPoint(timeline.stateBefore(starPoint), score.rules))
        assertEquals(2, timeline.statesAfterPoint[starPoint].lastGameWonBy)
        // Game 3 goes to deuce one time. Team 1 wins it at the first advantage, without a star point.
        assertFalse(points.indices.filter { points[it].id.startsWith("g3") }.any { ScoringEngine.isDecidingPoint(timeline.stateBefore(it), score.rules) })
        val last = timeline.statesAfterPoint.last()
        assertEquals(2, last.gamesP1)
        assertEquals(1, last.gamesP2)
    }

    @Test
    fun goldenPadelProjectKeepsItsDataAfterSaveAndLoad() {
        val score = ScoreIO.read(File(base, "score.json").absolutePath)
        val copy = Files.createTempFile("padel-score-", ".json").toFile()
        try {
            ScoreIO.write(copy.absolutePath, score)
            assertEquals(score, ScoreIO.read(copy.absolutePath))
        } finally {
            copy.delete()
        }
    }
}
