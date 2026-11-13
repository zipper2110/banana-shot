package org.litvin.scoring

import org.litvin.points.PointV1
import org.litvin.scoring.ScoringEngine.Stake
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchStatsTest {
    @Test
    fun countsPointsGamesAndSets() {
        val stats = stats("1111" + "2222" + "1112" + "1")

        assertEquals(13, stats.points)
        assertEquals(PerPlayer(8, 5), stats.pointsWon)
        assertEquals(PerPlayer(2, 1), stats.gamesWon)
        assertEquals(PerPlayer(0, 0), stats.setsWon)
    }

    @Test
    fun unscoredPointsDoNotCount() {
        val stats = stats("11-.2")

        assertEquals(5, stats.points)
        assertEquals(3, stats.scoredPoints)
        assertEquals(PerPlayer(2, 1), stats.pointsWon)
    }

    @Test
    fun withoutServerMarksTheServeStatisticsAreNull() {
        assertNull(stats("11112222").serve)
    }

    @Test
    fun serveReturnAndBreakPoints() {
        // Player 1 holds to love. Then player 1 breaks from 40–15: two break points, the second one wins.
        val serve = stats("1111" + "112121", serverMarks = mapOf("p1" to Outcome.P1)).serve!!

        assertEquals(PerPlayer(Ratio(4, 4), Ratio(2, 6)), serve.servicePointsWon)
        assertEquals(PerPlayer(Ratio(4, 6), Ratio(0, 4)), serve.returnPointsWon)
        assertEquals(PerPlayer(Ratio(1, 1), Ratio(0, 1)), serve.serviceGamesWon)
        assertEquals(PerPlayer(Ratio(1, 2), Ratio(0, 0)), serve.breakPointsWon)
        assertEquals(PerPlayer(Ratio(0, 0), Ratio(1, 2)), serve.breakPointsSaved)
    }

    @Test
    fun aTiebreakIsNotAServiceGameAndHasNoBreakPoints() {
        // Sets to 1 game: at 1–1 the players play a tiebreak.
        val rules = MatchRulesV1(gamesPerSet = 1)
        val stats = stats("1111" + "2222" + "1111111", rules, serverMarks = mapOf("p1" to Outcome.P1))

        assertEquals(PerPlayer(2, 1), stats.gamesWon)
        assertEquals(PerPlayer(1, 0), stats.setsWon)
        assertEquals(PerPlayer(Ratio(1, 1), Ratio(1, 1)), stats.serve!!.serviceGamesWon)
        assertEquals(PerPlayer(Ratio(0, 0), Ratio(0, 0)), stats.serve!!.breakPointsWon)
    }

    @Test
    fun setPointsAndMatchPoints() {
        // One tiebreak to 3 points: at 2–0 and at 2–1 player 1 has a match point.
        val rules = MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK, tiebreakPoints = 3)
        val stats = stats("1121", rules)

        assertEquals(PerPlayer(Ratio(1, 2), Ratio(0, 0)), stats.matchPointsWon)
        assertEquals(PerPlayer(Ratio(1, 2), Ratio(0, 0)), stats.setPointsWon)
    }

    @Test
    fun aSetPointThatDoesNotEndTheMatchIsNotAMatchPoint() {
        val stats = stats("1".repeat(24))

        assertEquals(PerPlayer(Ratio(1, 1), Ratio(0, 0)), stats.setPointsWon)
        assertEquals(PerPlayer(Ratio(0, 0), Ratio(0, 0)), stats.matchPointsWon)
    }

    @Test
    fun deucePointsAreThePointsAtEqualScoreFrom40All() {
        // 40–40, advantage player 1, deuce, advantage player 1, game.
        assertEquals(PerPlayer(2, 0), stats("111222" + "1211").deucePointsWon)
        // With no-ad scoring, the deciding point is the only deuce point.
        assertEquals(PerPlayer(1, 0), stats("111222" + "1", MatchRulesV1(deuce = DeuceRule.NO_AD)).deucePointsWon)
    }

    @Test
    fun manualScoringHasNoStakesAndNoDeucePoints() {
        val stats = stats("111222" + "1211", MatchRulesV1(manualScoring = true))

        assertEquals(PerPlayer(0, 0), stats.deucePointsWon)
        assertEquals(PerPlayer(Ratio(0, 0), Ratio(0, 0)), stats.setPointsWon)
    }

    @Test
    fun runsAndLargestLead() {
        val stats = stats("11122111")

        assertEquals(PerPlayer(3, 2), stats.longestPointRun)
        assertEquals(PerPlayer(4, 0), stats.largestPointLead)
    }

    @Test
    fun anUnscoredPointDoesNotStopARun() {
        assertEquals(PerPlayer(3, 0), stats("11-1").longestPointRun)
    }

    @Test
    fun longestGameRun() {
        assertEquals(PerPlayer(2, 1), stats("1111" + "2222" + "1111" + "1111").longestGameRun)
    }

    @Test
    fun gamesFromAScoreOverrideCount() {
        // Player 1 wins a game. Then the user sets the games to 3–0 on a point without a winner.
        val stats = stats("1111" + ".", overrides = mapOf("p5" to ScoreOverrideV1(games = ScorePairV1(3, 0))))

        assertEquals(PerPlayer(4, 0), stats.pointsWon)
        assertEquals(PerPlayer(3, 0), stats.gamesWon)
        assertEquals(PerPlayer(3, 0), stats.longestGameRun)
    }

    @Test
    fun setsFromAScoreOverrideCount() {
        val stats = stats(".", overrides = mapOf("p1" to ScoreOverrideV1(sets = listOf(SetGamesV1(6, 4)))))

        assertEquals(PerPlayer(0, 0), stats.pointsWon)
        assertEquals(PerPlayer(6, 4), stats.gamesWon)
        assertEquals(PerPlayer(1, 0), stats.setsWon)
    }

    @Test
    fun aScoreOverrideBeforeTheRangeDoesNotCountInTheRange() {
        val points = points(5)
        val outcomes = outcomes(points, "." + "1111")
        val marks = ManualScoreMarks(overrides = mapOf("p1" to ScoreOverrideV1(games = ScorePairV1(2, 3))))
        val timeline = ScoringEngine.timeline(points, outcomes, MatchRulesV1(), marks)

        val stats = MatchStats.compute(points, outcomes, timeline, MatchRulesV1(), 1..4)

        assertEquals(PerPlayer(1, 0), stats.gamesWon)
    }

    @Test
    fun aMatchTiebreakIsASetButNotAGame() {
        val rules = MatchRulesV1(finalSet = FinalSetRule.MATCH_TIEBREAK)
        val sets = listOf(SetGamesV1(6, 0), SetGamesV1(0, 6))
        val stats = stats("." + "1".repeat(10), rules, overrides = mapOf("p1" to ScoreOverrideV1(sets = sets)))

        assertEquals(PerPlayer(6, 6), stats.gamesWon)
        assertEquals(PerPlayer(2, 1), stats.setsWon)
    }

    @Test
    fun theStakesShowABreakPoint() {
        val points = points(6)
        val timeline = ScoringEngine.timeline(points, outcomes(points, "222"), serverMarks = mapOf("p1" to Outcome.P1))

        // At 0–40 the receiver (player 2) wins the game with the next point.
        assertEquals(ScoringEngine.PointStakes(Stake.NONE, Stake.GAME), timeline.stakesOfPoint[3])
    }

    @Test
    fun setRangesEndAtTheSetWinningPoint() {
        val rules = MatchRulesV1(gamesPerSet = 4)
        val points = points(19)
        val outcomes = outcomes(points, "1".repeat(16) + "221")
        val timeline = ScoringEngine.timeline(points, outcomes, rules)

        val ranges = MatchStats.setRanges(timeline)

        assertEquals(listOf(0..15, 16..18), ranges)
        assertEquals(PerPlayer(1, 2), MatchStats.compute(points, outcomes, timeline, rules, ranges[1]).pointsWon)
    }

    @Test
    fun theMatchEndsAtThePointThatWinsTheLastSet() {
        // Best of 1 set to 1 game without a tiebreak: player 1 wins 2–0 at point 8.
        val rules = MatchRulesV1(bestOfSets = 1, gamesPerSet = 1, setTiebreak = false)
        val points = points(10)
        val timeline = ScoringEngine.timeline(points, outcomes(points, "11111111" + "22"), rules)

        assertEquals(7, MatchStats.matchEndIndex(timeline, rules))
        assertEquals(listOf(0..7), MatchStats.setRanges(timeline, 0..7))
    }

    @Test
    fun manualScoringAndOpenFormatsHaveNoMatchEnd() {
        val points = points(8)
        val outcomes = outcomes(points, "11111111")
        for (rules in listOf(MatchRulesV1(manualScoring = true), MatchRulesV1(structure = MatchStructure.GAMES_ONLY))) {
            assertNull(MatchStats.matchEndIndex(ScoringEngine.timeline(points, outcomes, rules), rules))
        }
    }

    @Test
    fun withoutCompletedSetsAllPointsAreOneRange() {
        val points = points(5)
        val timeline = ScoringEngine.timeline(points, outcomes(points, "11221"))

        assertEquals(listOf(0..4), MatchStats.setRanges(timeline))
    }

    @Test
    fun timeStatistics() {
        val points = listOf(
            PointV1(id = "p1", startMs = 0, endMs = 5_000),
            PointV1(id = "p2", startMs = 7_000, endMs = 10_000),
            PointV1(id = "p3", startMs = 12_000, endMs = 20_000),
        )
        val outcomes = outcomes(points, "121")
        val timeline = ScoringEngine.timeline(points, outcomes)

        val time = MatchStats.compute(points, outcomes, timeline, MatchRulesV1()).time

        assertEquals(20_000L, time.durationMs)
        assertEquals(16_000L, time.playingMs)
        assertEquals(5_333L, time.averagePointMs)
        assertEquals("p3", time.longestPoint?.id)
        assertEquals(PerPlayer<Long?>(6_500, 3_000), time.averagePointMsWon)
    }

    @Test
    fun thePointLengthsSkipTheServeClipsAndThePlayingTimeIncludesThem() {
        val (points, outcomes, details) = advancedMatch()
        val timeline = ScoringEngine.timeline(points, outcomes)

        val time = MatchStats.compute(points, outcomes, timeline, MatchRulesV1(), details = details).time

        // The match starts with the first fault clip.
        assertEquals(40_000L, time.durationMs)
        // The clip of zero length adds nothing.
        assertEquals(29_000L, time.playingMs)
        // Only p1, p2, p3, and p4 are point clips.
        assertEquals(6_000L, time.averagePointMs)
        assertEquals("p4", time.longestPoint?.id)
        // The double fault is not a point length of player 2.
        assertEquals(PerPlayer<Long?>(5_333, 8_000), time.averagePointMsWon)
        assertEquals(listOf(7_000L, 2_000L, 7_000L, 8_000L), time.scoredDurations.map { it.durationMs })
    }

    @Test
    fun basicModeHasNoDetailStatistics() {
        val (points, outcomes, details) = advancedMatch()
        val timeline = ScoringEngine.timeline(points, outcomes)

        assertNull(MatchStats.compute(points, outcomes, timeline, MatchRulesV1(), details = details).details)
    }

    @Test
    fun theDetailStatisticsCountTheEndingsAndTheServesOfThePacks() {
        val (points, outcomes, details) = advancedMatch()
        val timeline = ScoringEngine.timeline(points, outcomes, serverMarks = mapOf("p1" to Outcome.P1))

        val stats = MatchStats.compute(points, outcomes, timeline, MatchRulesV1(), details = details, advanced = true).details!!

        // p4 has a winner but no ending.
        assertEquals(4, stats.detailedPoints)
        assertEquals(PerPlayer(1, 0), stats.aces)
        assertEquals(PerPlayer(1, 0), stats.doubleFaults)
        assertEquals(PerPlayer(1, 0), stats.winners)
        assertEquals(PerPlayer(0, 0), stats.forcedErrors)
        // Player 1 won p3 with an unforced error of player 2.
        assertEquals(PerPlayer(0, 1), stats.unforcedErrors)
        assertEquals(1, stats.errors)
        assertEquals(1, stats.errorsWithKind)
        assertEquals(PerPlayer(0, 1), stats.errorKinds.getValue(ErrorKind.NET))
        // Player 1 served 5 packs. The 1st serve was in for p2, p3, and p4.
        assertEquals(Ratio(3, 5), stats.firstServeIn.p1)
        assertEquals(Ratio(2, 3), stats.firstServePointsWon.p1)
        // The 2nd serves: p1 (won) and the double fault (lost).
        assertEquals(Ratio(1, 2), stats.secondServePointsWon.p1)
        assertEquals(Ratio(0, 0), stats.firstServeIn.p2)
    }

    @Test
    fun aFaultClipOfZeroLengthAndAFaultClipWithVideoCountTheSame() {
        val (points, outcomes, details) = advancedMatch()
        // The fault before the double fault gets video.
        val withVideo = points.map { if (it.id == "z") it.copy(endMs = 16_500) else it }
        fun detailStats(clips: List<PointV1>) = MatchStats.compute(
            clips, outcomes, ScoringEngine.timeline(clips, outcomes, serverMarks = mapOf("p1" to Outcome.P1)), MatchRulesV1(),
            details = details, advanced = true,
        ).details

        assertEquals(detailStats(points), detailStats(withVideo))
    }

    /**
     * Five packs of one game. Player 1 serves:
     * - a fault, then a winner of player 1 on the 2nd serve,
     * - an ace,
     * - a fault of zero length, then a double fault,
     * - a let, then an unforced error of player 2 into the net,
     * - a point of player 2 with no ending (Q or E).
     */
    private fun advancedMatch(): Triple<List<PointV1>, Map<String, Outcome>, Map<String, PointDetailV1>> {
        val points = listOf(
            PointV1(id = "f1", startMs = 0, endMs = 2_000),
            PointV1(id = "p1", startMs = 3_000, endMs = 10_000),
            PointV1(id = "p2", startMs = 12_000, endMs = 14_000),
            PointV1(id = "z", startMs = 16_000, endMs = 16_000),
            PointV1(id = "d", startMs = 17_000, endMs = 19_000),
            PointV1(id = "l", startMs = 21_000, endMs = 22_000),
            PointV1(id = "p3", startMs = 23_000, endMs = 30_000),
            PointV1(id = "p4", startMs = 32_000, endMs = 40_000),
        )
        val outcomes = mapOf(
            "f1" to Outcome.NONE, "p1" to Outcome.P1, "p2" to Outcome.P1, "z" to Outcome.NONE,
            "d" to Outcome.P2, "l" to Outcome.NONE, "p3" to Outcome.P1, "p4" to Outcome.P2,
        )
        val details = mapOf(
            "f1" to PointDetailV1(Ending.SERVE_FAULT),
            "p1" to PointDetailV1(Ending.WINNER),
            "p2" to PointDetailV1(Ending.ACE),
            "z" to PointDetailV1(Ending.SERVE_FAULT),
            "d" to PointDetailV1(Ending.SERVE_FAULT),
            "l" to PointDetailV1(Ending.LET),
            "p3" to PointDetailV1(Ending.UNFORCED_ERROR, ErrorKind.NET),
        )
        return Triple(points, outcomes, details)
    }

    @Test
    fun theMatchDurationUsesOnlyThePointsWithAWinnerAtTheStartAndAtTheEnd() {
        val points = listOf(
            PointV1(id = "p1", startMs = 0, endMs = 5_000),
            PointV1(id = "p2", startMs = 7_000, endMs = 10_000),
            PointV1(id = "p3", startMs = 12_000, endMs = 20_000),
            PointV1(id = "p4", startMs = 30_000, endMs = 35_000),
        )
        // The first point and the last point have no winner.
        val outcomes = outcomes(points, ".12.")
        val timeline = ScoringEngine.timeline(points, outcomes)

        val time = MatchStats.compute(points, outcomes, timeline, MatchRulesV1()).time

        assertEquals(13_000L, time.durationMs)
    }

    @Test
    fun noPointsGiveEmptyStatistics() {
        val stats = stats("")

        assertEquals(0, stats.points)
        assertEquals(0L, stats.time.durationMs)
        assertNull(stats.time.averagePointMs)
        assertNull(stats.time.longestPoint)
        assertEquals(PerPlayer(0, 0), stats.longestPointRun)
    }

    @Test
    fun theKeyPointsAreTheStartOfTheLongestRunAndThePointOfTheLargestLead() {
        // An unscored point (p6) does not stop the run of player 1: p5, p7 and p8.
        val stats = stats("12221.11")

        assertEquals(PerPlayer(3, 3), stats.longestPointRun)
        assertEquals(PerPlayer<String?>("p5", "p2"), stats.keyPoints.longestPointRunStart)
        assertEquals(PerPlayer(1, 2), stats.largestPointLead)
        assertEquals(PerPlayer<String?>("p1", "p4"), stats.keyPoints.largestPointLeadAt)
    }

    @Test
    fun aPlayerWithoutPointsHasNoKeyPoints() {
        val stats = stats("111")

        assertNull(stats.keyPoints.longestPointRunStart.p2)
        assertNull(stats.keyPoints.largestPointLeadAt.p2)
        assertEquals("p3", stats.keyPoints.largestPointLeadAt.p1)
    }

    private fun stats(
        winners: String,
        rules: MatchRulesV1 = MatchRulesV1(),
        serverMarks: Map<String, Outcome> = emptyMap(),
        overrides: Map<String, ScoreOverrideV1> = emptyMap(),
    ): MatchStats {
        val points = points(winners.length)
        val outcomes = outcomes(points, winners)
        val timeline = ScoringEngine.timeline(points, outcomes, rules, ManualScoreMarks(overrides = overrides), serverMarks)
        return MatchStats.compute(points, outcomes, timeline, rules)
    }

    /** '1' and '2' are the point winners, '-' is no point, and any other character leaves the point unscored. */
    private fun outcomes(points: List<PointV1>, winners: String): Map<String, Outcome> =
        points.zip(winners.toList()).mapNotNull { (point, c) ->
            when (c) {
                '1' -> point.id to Outcome.P1
                '2' -> point.id to Outcome.P2
                '-' -> point.id to Outcome.NONE
                else -> null
            }
        }.toMap()

    private fun points(count: Int): List<PointV1> =
        (1..count).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
}
