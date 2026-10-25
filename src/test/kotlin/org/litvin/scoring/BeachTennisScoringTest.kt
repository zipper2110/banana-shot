package org.litvin.scoring

import org.litvin.points.PointV1
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Beach tennis uses the ITF rules by default. The winners of the points are a string, for example "1122". */
class BeachTennisScoringTest {
    private val rules = Sport.BEACH_TENNIS.defaultRules()

    @Test
    fun theDefaultRulesAreTwoTiebreakSetsAMatchTiebreakAndNoAd() {
        assertEquals(DeuceRule.NO_AD, rules.deuce)
        assertEquals(3, rules.bestOfSets)
        assertEquals(6, rules.gamesPerSet)
        assertTrue(rules.setTiebreak)
        assertEquals(7, rules.tiebreakPoints)
        assertTrue(rules.hasMatchTiebreakDecider())
        assertEquals(MatchFormatPreset.BEST_OF_3_MATCH_TIEBREAK, MatchFormatPreset.of(rules, Sport.BEACH_TENNIS))
    }

    @Test
    fun thePointAtFortyAllWinsTheGame() {
        val states = timeline("111222" + "2").statesAfterPoint

        assertNull(states[5].lastGameWonBy)
        assertEquals(2, states.last().lastGameWonBy)
        assertEquals(1, states.last().gamesP2)
    }

    @Test
    fun oneSetAllGoesToATenPointMatchTiebreak() {
        val oneSetAll = "1".repeat(24) + "2".repeat(24)
        val states = timeline(oneSetAll + "1".repeat(10)).statesAfterPoint

        val inTiebreak = states[oneSetAll.length]
        assertEquals(1, inTiebreak.setsP1)
        assertEquals(1, inTiebreak.setsP2)
        assertTrue(inTiebreak.isTiebreak)
        assertEquals(1, inTiebreak.p1Pts)
        assertNull(states[oneSetAll.length + 8].lastSetWonBy, "9–0 does not win the match tiebreak")
        assertEquals(2, states.last().setsP1)
    }

    @Test
    fun beachTennisHasTeamsAndTheTennisWords() {
        assertEquals("Beach tennis", Sport.BEACH_TENNIS.title)
        assertEquals("Team 1", Sport.BEACH_TENNIS.defaultSideName(1))
        assertEquals(listOf(DeuceRule.ADVANTAGE, DeuceRule.NO_AD), Sport.BEACH_TENNIS.deuceRules(DeuceRule.NO_AD))
        assertEquals("No-ad", Sport.BEACH_TENNIS.deuceTitle(DeuceRule.NO_AD))
        assertEquals("DECIDING POINT", Sport.BEACH_TENNIS.decidingPointBadge(DeuceRule.NO_AD))
        assertEquals("Best of 3 sets, match tiebreak", MatchFormatPreset.BEST_OF_3_MATCH_TIEBREAK.title(Sport.BEACH_TENNIS))

        val formats = MatchFormatPreset.forSport(Sport.BEACH_TENNIS)
        assertFalse(MatchFormatPreset.BEST_OF_5 in formats)
        assertFalse(MatchFormatPreset.PRO_SET_9 in formats)
        assertFalse(MatchFormatPreset.AMERICANO in formats)
        assertEquals(MatchFormatPreset.CUSTOM, formats.last())
    }

    @Test
    fun beachTennisPointsAreShortAtSevenSecondsAndLongAtTen() {
        val defaults = org.litvin.stats.StatsSettingsV1.defaults(Sport.BEACH_TENNIS)
        assertEquals(7, defaults.shortPointMaxSeconds)
        assertEquals(10, defaults.longPointMinSeconds)
        assertEquals(defaults, defaults.normalized())
    }

    @Test
    fun theSportRoundTripsInTheScoreFile() {
        val dir = Files.createTempDirectory("beach-tennis").toFile()
        try {
            ScoreIO.writeForProjectDir(dir.absolutePath, ScoreV1.newProject(Sport.BEACH_TENNIS, rules))
            assertTrue("\"BEACH_TENNIS\"" in java.io.File(ScoreIO.scoreFilePath(dir.absolutePath)).readText())

            val read = ScoreIO.readForProjectDir(dir.absolutePath)
            assertEquals(Sport.BEACH_TENNIS, read.sport)
            assertEquals(rules, read.rules)
            assertEquals("Team 2", read.player2Name)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun timeline(winners: String): ScoringEngine.Timeline {
        val points = (1..winners.length).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
        val outcomes = points.zip(winners.toList()).associate { (point, c) -> point.id to if (c == '1') Outcome.P1 else Outcome.P2 }
        return ScoringEngine.timeline(points, outcomes, rules)
    }
}
