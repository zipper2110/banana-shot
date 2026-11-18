package org.litvin.stats

import org.litvin.i18n.DisplayFormats
import org.litvin.scoring.DetailStats
import org.litvin.scoring.ErrorKind
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStats
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.Ratio
import org.litvin.scoring.ServeStats
import org.litvin.scoring.TimeStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceRatingsTest {
    @Test
    fun theScaleIsAStraightLineBetweenTheAnchors() {
        assertEquals(0.0, PerformanceRatings.scale(30.0, 30.0, 70.0))
        assertEquals(5.0, PerformanceRatings.scale(50.0, 30.0, 70.0))
        assertEquals(10.0, PerformanceRatings.scale(70.0, 30.0, 70.0))
        // A value outside the anchors gives 0 or 10.
        assertEquals(0.0, PerformanceRatings.scale(20.0, 30.0, 70.0))
        assertEquals(10.0, PerformanceRatings.scale(90.0, 30.0, 70.0))
        // When the anchor for 10 is smaller, a smaller value is better, for example for the errors.
        assertEquals(7.5, PerformanceRatings.scale(0.15, 0.45, 0.05), 1e-9)
    }

    @Test
    fun basicModeRatesServeReturnAndPressureFromTheBasicStatistics() {
        val ratings = PerformanceRatings.of(basicStats(), MatchRulesV1())

        // Serve: points won on serve 55% and 50%, service games won 60% and 40%.
        assertNear(PerPlayer(avg(6.25, 6.25), avg(5.0, 3.75)), ratings.areas.getValue(RatingArea.SERVE))
        // Return: points won on return 50% and 45%, return games won 60% and 40%.
        assertNear(PerPlayer(avg(5.0, 6.25), avg(3.75, 3.75)), ratings.areas.getValue(RatingArea.RETURN))
        // Pressure: break points won 6/10 and 4/12, break points saved 8/12 and 4/10.
        assertNear(
            PerPlayer(avg(50.0 / 6, (200.0 / 3 - 20) / 6), avg((100.0 / 3 - 10) / 6, (40.0 - 20) / 6)),
            ratings.areas.getValue(RatingArea.PRESSURE),
        )
        // Basic mode has no data for Attack and Consistency.
        assertEquals(setOf(RatingArea.SERVE, RatingArea.RETURN, RatingArea.PRESSURE), ratings.areas.keys)
    }

    @Test
    fun overallIsTheWeightedAverageAndPressureCountsHalf() {
        val ratings = PerformanceRatings.of(basicStats(), MatchRulesV1())
        val areas = ratings.areas

        fun expected(player: Int) =
            (areas.getValue(RatingArea.SERVE)[player] + areas.getValue(RatingArea.RETURN)[player] +
                0.5 * areas.getValue(RatingArea.PRESSURE)[player]) / 2.5
        assertNear(PerPlayer(expected(1), expected(2)), ratings.overall!!)
        assertTrue(ratings.overall.p1 > ratings.overall.p2)
    }

    @Test
    fun anInputCountsOnlyWhenBothPlayersHaveSufficientData() {
        // Player 2 had only 2 break points. Thus no break point input counts, also not for player 1.
        val stats = basicStats().let { it.copy(serve = it.serve!!.copy(breakPointsWon = PerPlayer(Ratio(6, 10), Ratio(1, 2)))) }

        val ratings = PerformanceRatings.of(stats, MatchRulesV1())

        assertNull(ratings.areas[RatingArea.PRESSURE])
    }

    @Test
    fun withoutAServerOnlyPressureHasDataAndOverallNeedsTwoRatings() {
        val stats = basicStats().copy(serve = null, deuceGamesWon = PerPlayer(Ratio(3, 4), Ratio(1, 4)))

        val ratings = PerformanceRatings.of(stats, MatchRulesV1())

        assertEquals(setOf(RatingArea.PRESSURE), ratings.areas.keys)
        assertNull(ratings.overall)
    }

    @Test
    fun manualScoringHasNoPressureRating() {
        val ratings = PerformanceRatings.of(basicStats(), MatchRulesV1(manualScoring = true))

        assertNull(ratings.areas[RatingArea.PRESSURE])
    }

    @Test
    fun aFormatWithoutGamesUsesOnlyThePointInputs() {
        val ratings = PerformanceRatings.of(basicStats(), MatchRulesV1(structure = MatchStructure.TOTAL_POINTS))

        assertNear(PerPlayer(6.25, 5.0), ratings.areas.getValue(RatingArea.SERVE))
        assertNear(PerPlayer(5.0, 3.75), ratings.areas.getValue(RatingArea.RETURN))
        assertNull(ratings.areas[RatingArea.PRESSURE])
    }

    @Test
    fun advancedModeRatesAttackAndConsistencyFromThePointDetails() {
        // 100 points with details. Player 1: 20 winners, 10 unforced errors. Player 2: 10 winners, 30 unforced
        // errors. Player 1 made 4 forced errors, player 2 made 10 forced errors.
        val stats = basicStats().copy(details = details(winners = PerPlayer(20, 10), forced = PerPlayer(4, 10), unforced = PerPlayer(10, 30)))

        val ratings = PerformanceRatings.of(stats, MatchRulesV1())

        // Attack: (winners + forced errors of the opponent) per point, 0.30 and 0.14, on the scale 0 to 0.4.
        assertNear(PerPlayer(7.5, 3.5), ratings.areas.getValue(RatingArea.ATTACK))
        // Consistency: unforced errors per point, 0.10 and 0.30, on the scale 0.45 to 0.05.
        assertNear(PerPlayer(8.75, 3.75), ratings.areas.getValue(RatingArea.CONSISTENCY))
        assertEquals(RatingArea.entries.toSet(), ratings.areas.keys)
    }

    @Test
    fun advancedModeAddsTheServeDetailsToTheServeRating() {
        val stats = basicStats().copy(details = details())

        val serve = PerformanceRatings.of(stats, MatchRulesV1()).areas.getValue(RatingArea.SERVE)

        // Points won on serve, service games won, 1st serve in (60% and 50%), 1st serve points won (70% and 60%), 2nd
        // serve points won (40% and 35%), unreturned serves (6/60 and 3/60), double faults (3/60 and 6/60).
        assertNear(
            PerPlayer(
                avg(6.25, 6.25, 6.0, 7.5, 10.0 / 3.5, 4.0, 2.0 / 3 * 10),
                avg(5.0, 3.75, 4.0, 5.0, 5.0 / 3.5, 2.0, 1.0 / 3 * 10),
            ),
            serve,
        )
    }

    @Test
    fun theTableShowsARatingWithOneDecimalAndABarOfTen() {
        val rows = StatRows.build(basicStats(), MatchRulesV1())
        val serve = rows.single { it.stat == MatchStat.RATING_SERVE }

        assertEquals(PerPlayer(DisplayFormats.decimal(6.3, 1), DisplayFormats.decimal(4.4, 1)), serve.values!!.let { PerPlayer(it.p1.text, it.p2.text) })
        assertEquals(PerPlayer(6.3, 4.4), serve.bar)
        assertEquals(10.0, serve.barMax)
        // Basic mode has no Attack and Consistency rows.
        assertTrue(rows.none { it.stat == MatchStat.RATING_ATTACK || it.stat == MatchStat.RATING_CONSISTENCY })
    }

    @Test
    fun aRatingWithoutDataHasNoValueInTheTable() {
        val noServer = StatRows.build(basicStats().copy(serve = null), MatchRulesV1())
        assertEquals(StatRows.NEEDS_SERVER, noServer.single { it.stat == MatchStat.RATING_SERVE }.unavailableReason)
        assertEquals(StatRows.NEEDS_SERVER, noServer.single { it.stat == MatchStat.RATING_OVERALL }.unavailableReason)
        assertEquals(StatRows.NO_VALUE, noServer.single { it.stat == MatchStat.RATING_PRESSURE }.unavailableReason)

        // With a server but too few points, the reason is the data.
        val fewPoints = basicStats().let { stats ->
            stats.copy(serve = stats.serve!!.copy(servicePointsWon = PerPlayer(Ratio(3, 5), Ratio(2, 5)), serviceGamesWon = PerPlayer(Ratio(1, 1), Ratio(0, 1))))
        }
        assertEquals(StatRows.NO_VALUE, StatRows.build(fewPoints, MatchRulesV1()).single { it.stat == MatchStat.RATING_SERVE }.unavailableReason)
    }

    private fun avg(vararg values: Double) = values.average()

    private fun assertNear(expected: PerPlayer<Double>, actual: PerPlayer<Double>) {
        assertEquals(expected.p1, actual.p1, 1e-9, "player 1")
        assertEquals(expected.p2, actual.p2, 1e-9, "player 2")
    }

    /** 120 points with a server. Each player served 60 points and 10 service games. */
    private fun basicStats() = MatchStats(
        points = 120,
        scoredPoints = 120,
        pointsWon = PerPlayer(63, 57),
        gamesWon = PerPlayer(12, 8),
        setsWon = PerPlayer(2, 0),
        serve = ServeStats(
            servicePointsWon = PerPlayer(Ratio(33, 60), Ratio(30, 60)),
            returnPointsWon = PerPlayer(Ratio(30, 60), Ratio(27, 60)),
            serviceGamesWon = PerPlayer(Ratio(6, 10), Ratio(4, 10)),
            breakPointsWon = PerPlayer(Ratio(6, 10), Ratio(4, 12)),
        ),
        setPointsWon = PerPlayer(Ratio(2, 3), Ratio(0, 0)),
        matchPointsWon = PerPlayer(Ratio(1, 1), Ratio(0, 0)),
        deuceGamesWon = PerPlayer(Ratio(0, 0), Ratio(0, 0)),
        time = TimeStats(0, 0, null, null, PerPlayer(null, null)),
    )

    private fun details(
        winners: PerPlayer<Int> = PerPlayer(15, 15),
        forced: PerPlayer<Int> = PerPlayer(5, 5),
        unforced: PerPlayer<Int> = PerPlayer(10, 10),
    ) = DetailStats(
        detailedPoints = 100,
        aces = PerPlayer(4, 2),
        unreturnedServes = PerPlayer(6, 3),
        doubleFaults = PerPlayer(3, 6),
        firstServeIn = PerPlayer(Ratio(36, 60), Ratio(30, 60)),
        firstServePointsWon = PerPlayer(Ratio(21, 30), Ratio(18, 30)),
        secondServePointsWon = PerPlayer(Ratio(8, 20), Ratio(7, 20)),
        firstServeReturnPointsWon = PerPlayer(Ratio(12, 30), Ratio(9, 30)),
        secondServeReturnPointsWon = PerPlayer(Ratio(13, 20), Ratio(12, 20)),
        winners = winners,
        forcedErrors = forced,
        unforcedErrors = unforced,
        errors = forced.p1 + forced.p2 + unforced.p1 + unforced.p2,
        errorKinds = ErrorKind.entries.associateWith { PerPlayer(0, 0) },
    )
}
