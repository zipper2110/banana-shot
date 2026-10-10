package org.litvin.scoring

import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.stats.MatchStat
import org.litvin.stats.StatRows
import org.litvin.stats.StatsReport
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-14: the padel rules. The winners of the points are a string, for example "1122" (player 1, 1, 2, 2). */
class PadelScoringTest {
    private val starPoint = MatchRulesV1(deuce = DeuceRule.STAR_POINT)

    @Test
    fun starPointPlaysTwoAdvantagesThenOneDecidingPoint() {
        // 40–40, Ad 1, deuce, Ad 2, deuce (the third deuce, 5–5): the next point wins the game.
        val states = timeline("111222" + "12" + "21", starPoint).statesAfterPoint

        assertEquals(0, states[9].gamesP1 + states[9].gamesP2, "No game at the third deuce")
        assertEquals(5, states[9].p1Pts)
        assertEquals(5, states[9].p2Pts)
        val afterStarPoint = timeline("111222" + "12" + "21" + "2", starPoint).statesAfterPoint.last()
        assertEquals(1, afterStarPoint.gamesP2)
        assertEquals(2, afterStarPoint.lastGameWonBy)
    }

    @Test
    fun starPointStillNeedsTwoPointsAtTheFirstAndTheSecondAdvantage() {
        // 40–40, then Ad 1 is won: 5–3.
        assertEquals(1, timeline("111222" + "11", starPoint).statesAfterPoint.last().lastGameWonBy)
        // 40–40, Ad 2, deuce, Ad 2 is won: 4–6 in points.
        val second = timeline("111222" + "21" + "22", starPoint).statesAfterPoint
        assertNull(second[7].lastGameWonBy)
        assertEquals(2, second[9].lastGameWonBy)
    }

    @Test
    fun theDecidingPointIsTheGoldenPointOrTheStarPoint() {
        val deuce = ScoringEngine.MatchState(3, 3, 0, 0, 0, 0, null, null, false)
        val thirdDeuce = deuce.copy(p1Pts = 5, p2Pts = 5)
        val noAd = MatchRulesV1(deuce = DeuceRule.NO_AD)

        assertTrue(ScoringEngine.isDecidingPoint(deuce, noAd))
        assertFalse(ScoringEngine.isDecidingPoint(deuce, starPoint))
        assertTrue(ScoringEngine.isDecidingPoint(thirdDeuce, starPoint))
        assertFalse(ScoringEngine.isDecidingPoint(thirdDeuce, MatchRulesV1()))
        assertFalse(ScoringEngine.isDecidingPoint(deuce.copy(isTiebreak = true), noAd))
        assertFalse(ScoringEngine.isDecidingPoint(deuce, noAd.copy(manualScoring = true)))
    }

    @Test
    fun anAmericanoMatchEndsAfterTheTotalPointsAndIgnoresTheRest() {
        val rules = MatchRulesV1(structure = MatchStructure.TOTAL_POINTS, totalPoints = 6)
        val timeline = timeline("112121" + "22", rules)

        val last = timeline.statesAfterPoint.last()
        assertEquals(4, last.p1Pts)
        assertEquals(2, last.p2Pts)
        assertTrue(last.isTiebreak, "The scoreboard shows the points as numbers")
        assertEquals(0, last.setsP1 + last.setsP2)
        assertEquals(5, MatchStats.matchEndIndex(timeline, rules))
        assertEquals(ScoringEngine.Stake.MATCH, timeline.stakesOfPoint[5].p1)
    }

    @Test
    fun anAmericanoMatchCanEndInADraw() {
        val rules = MatchRulesV1(structure = MatchStructure.TOTAL_POINTS, totalPoints = 4)
        val timeline = timeline("1122" + "1", rules)

        assertEquals(2, timeline.statesAfterPoint.last().p1Pts)
        assertEquals(2, timeline.statesAfterPoint.last().p2Pts)
        assertEquals(3, MatchStats.matchEndIndex(timeline, rules))
        // The last point gives the match to player 1 or a draw for player 2.
        assertEquals(ScoringEngine.Stake.MATCH, timeline.stakesOfPoint[3].p1)
        assertEquals(ScoringEngine.Stake.NONE, timeline.stakesOfPoint[3].p2)
    }

    @Test
    fun inAnAmericanoMatchEachSideServesItsTurnOfPoints() {
        val rules = MatchRulesV1(structure = MatchStructure.TOTAL_POINTS, totalPoints = 16, serveTurnPoints = 4)
        val timeline = timeline("1".repeat(10), rules, serverMarks = mapOf("p1" to Outcome.P2))

        assertEquals(listOf(2, 2, 2, 2, 1, 1, 1, 1, 2, 2), timeline.serverOfPoint)
    }

    @Test
    fun aMarkInAnAmericanoMatchContinuesTheTurn() {
        val rules = MatchRulesV1(structure = MatchStructure.TOTAL_POINTS, totalPoints = 16, serveTurnPoints = 2)
        // The mark on the third point says that player 1 serves the second turn.
        val timeline = timeline("1".repeat(6), rules, serverMarks = mapOf("p3" to Outcome.P1))

        assertEquals(listOf(2, 2, 1, 1, 2, 2), timeline.serverOfPoint)
    }

    @Test
    fun padelShowsItsOwnFormatsAndNames() {
        val padel = MatchFormatPreset.forSport(Sport.PADEL)
        val tennis = MatchFormatPreset.forSport(Sport.TENNIS)

        assertTrue(MatchFormatPreset.AMERICANO in padel)
        assertFalse(MatchFormatPreset.AMERICANO in tennis)
        assertFalse(MatchFormatPreset.BEST_OF_5 in padel)
        assertEquals(MatchFormatPreset.CUSTOM, padel.last())
        assertEquals("Super tiebreak (10 points)", MatchFormatPreset.MATCH_TIEBREAK.title(Sport.PADEL))
        assertEquals("Match tiebreak (10 points)", MatchFormatPreset.MATCH_TIEBREAK.title(Sport.TENNIS))
        assertEquals("Team 2", Sport.PADEL.defaultSideName(2))
        assertEquals("Golden point", Sport.PADEL.deuceTitle(DeuceRule.NO_AD))
        assertEquals("No-ad", Sport.TENNIS.deuceTitle(DeuceRule.NO_AD))
        assertEquals(listOf(DeuceRule.ADVANTAGE, DeuceRule.NO_AD), Sport.TENNIS.deuceRules(DeuceRule.ADVANTAGE))
        assertEquals(DeuceRule.STAR_POINT, Sport.TENNIS.deuceRules(DeuceRule.STAR_POINT).last())
    }

    @Test
    fun theAmericanoPresetKeepsThePointsAndAFiveSetMatchIsCustomInPadel() {
        val americano = MatchRulesV1(structure = MatchStructure.TOTAL_POINTS, totalPoints = 32, serveTurnPoints = 2)

        assertEquals(MatchFormatPreset.AMERICANO, MatchFormatPreset.of(americano, Sport.PADEL))
        assertEquals(americano, MatchFormatPreset.AMERICANO.applyTo(americano.copy(structure = MatchStructure.SETS)))
        assertEquals(MatchFormatPreset.CUSTOM, MatchFormatPreset.of(MatchRulesV1(bestOfSets = 5), Sport.PADEL))
        assertEquals(MatchFormatPreset.BEST_OF_3, MatchFormatPreset.of(starPoint, Sport.PADEL))
        // The total points do not change the canonical form of a set match.
        assertEquals(MatchRulesV1().canonical(), MatchRulesV1(totalPoints = 32).canonical())
    }

    @Test
    fun theDecidingPointsRowCountsTheGoldenPoints() {
        // Two games that reach 40–40 with the golden point: player 1 wins the first, player 2 the second.
        val rules = MatchRulesV1(deuce = DeuceRule.NO_AD)
        val report = report("111222" + "1" + "111222" + "2", rules)
        val row = StatRows.build(report.match, rules).single { it.stat == MatchStat.DECIDING_POINTS_WON }

        assertEquals("Golden points won", row.label)
        assertEquals(PerPlayer(Ratio(1, 2), Ratio(1, 2)), report.match.decidingPointsWon)
        assertEquals("1/2", row.values?.p1?.text)
    }

    @Test
    fun theDecidingPointsRowNeedsAGoldenOrAStarPoint() {
        val report = report("1111", MatchRulesV1())
        val row = StatRows.build(report.match, MatchRulesV1()).single { it.stat == MatchStat.DECIDING_POINTS_WON }

        assertFalse(row.available)
        assertEquals(
            "Star points won",
            StatRows.build(report.match, starPoint).single { it.stat == MatchStat.DECIDING_POINTS_WON }.label,
        )
    }

    @Test
    fun anAmericanoReportShowsThePointsAndHasMatchPoints() {
        val rules = MatchRulesV1(structure = MatchStructure.TOTAL_POINTS, totalPoints = 6)
        val report = report("112121" + "22", rules)
        val rows = StatRows.build(report.match, rules)

        assertEquals(listOf("4-2"), report.setScores())
        assertEquals(2, report.pointsAfterMatchEnd)
        assertTrue(rows.single { it.stat == MatchStat.MATCH_POINTS_WON }.available)
        assertFalse(rows.single { it.stat == MatchStat.SET_POINTS_WON }.available)
        assertFalse(rows.single { it.stat == MatchStat.GAMES_WON }.available)
    }

    @Test
    fun theSportAndTheRulesRoundTripInTheScoreFile() {
        val dir = Files.createTempDirectory("padel-score").toFile()
        try {
            val americano = MatchRulesV1(structure = MatchStructure.TOTAL_POINTS, totalPoints = 21, serveTurnPoints = 2)
            ScoreIO.writeForProjectDir(dir.absolutePath, ScoreV1.newProject(Sport.PADEL, americano))

            val read = ScoreIO.readForProjectDir(dir.absolutePath)
            assertEquals(Sport.PADEL, read.sport)
            assertEquals(americano, read.rules)
            assertEquals("Team 1", read.player1Name)
            assertTrue(read.useDefaultScoreboard)
            assertEquals(americano, ScoreIO.rulesFromJson(ScoreIO.rulesToJson(americano)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aScoreFileWithoutASportOrWithAnUnknownSportIsTennis() {
        val dir = Files.createTempDirectory("padel-score").toFile()
        try {
            val file = dir.resolve("score.json")
            file.writeText("""{"version":1,"sport":"PICKLEBALL","rules":{"deuce":"SOMETHING_NEW"}}""")
            val read = ScoreIO.read(file.absolutePath)
            assertEquals(Sport.TENNIS, read.sport)
            assertEquals(DeuceRule.ADVANTAGE, read.rules.deuce)
            assertFalse(read.useDefaultScoreboard)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun proSetToNineGamesPlaysTheTiebreakAtEightAll() {
        val rules = MatchFormatPreset.PRO_SET_9.applyTo(MatchRulesV1())
        val game = { player: Int -> player.toString().repeat(4) }
        val toEightAll = (1..8).joinToString("") { game(1) + game(2) }

        // 8–6 is not a win: the set goes to 9 games.
        val eightSix = timeline(game(1).repeat(8) + game(2).repeat(6), rules).statesAfterPoint.last()
        assertNull(eightSix.lastSetWonBy)
        assertEquals(ScoringEngine.SetScore(9, 7, false), timeline(toEightAll.dropLast(8) + game(1) + game(1), rules)
            .setsAfterPoint.last().single())

        val atEightAll = timeline(toEightAll, rules).statesAfterPoint.last()
        assertTrue(atEightAll.isTiebreak)
        val tiebreak = timeline(toEightAll + "2".repeat(7), rules)
        assertEquals(ScoringEngine.SetScore(8, 9, true), tiebreak.setsAfterPoint.last().single())
        assertEquals(1, tiebreak.statesAfterPoint.last().setsP2)
    }

    @Test
    fun proSetToNineGamesIsAPadelPresetAndTheEarlyTiebreakNeedsATiebreak() {
        val proSet = MatchRulesV1(bestOfSets = 1, gamesPerSet = 9, earlyTiebreak = true)

        assertEquals(MatchFormatPreset.PRO_SET_9, MatchFormatPreset.of(proSet, Sport.PADEL))
        assertEquals(MatchFormatPreset.CUSTOM, MatchFormatPreset.of(proSet.copy(earlyTiebreak = false), Sport.PADEL))
        assertFalse(MatchFormatPreset.PRO_SET_9 in MatchFormatPreset.forSport(Sport.TENNIS))
        assertEquals(8, proSet.setTiebreakGames())
        assertFalse(proSet.copy(setTiebreak = false).normalized().earlyTiebreak)
        assertFalse(MatchRulesV1(gamesPerSet = 1, earlyTiebreak = true).normalized().earlyTiebreak)
        assertEquals(proSet, ScoreIO.rulesFromJson(ScoreIO.rulesToJson(proSet)))
    }

    private fun timeline(
        winners: String,
        rules: MatchRulesV1,
        serverMarks: Map<String, Outcome> = emptyMap(),
    ): ScoringEngine.Timeline {
        val points = points(winners.length)
        return ScoringEngine.timeline(points, outcomes(points, winners), rules, serverMarks = serverMarks)
    }

    private fun report(winners: String, rules: MatchRulesV1): StatsReport {
        val points = points(winners.length)
        return StatsReport.build(EdlV1(points = points), ScoreV1(outcomes = outcomes(points, winners), rules = rules))
    }

    private fun points(count: Int) = (1..count).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }

    private fun outcomes(points: List<PointV1>, winners: String) =
        points.zip(winners.toList()).associate { (point, c) -> point.id to if (c == '1') Outcome.P1 else Outcome.P2 }
}
