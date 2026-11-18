package org.litvin.stats

import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStats
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.Ratio

/** The areas of play that get a performance rating (B-77). */
enum class RatingArea {
    SERVE,
    RETURN,
    /** The winners of the player and the forced errors of the opponent. Only advanced mode has the data. */
    ATTACK,
    /** Few unforced errors. Only advanced mode has the data. */
    CONSISTENCY,
    PRESSURE,
}

/**
 * The performance ratings of a match (B-77, experimental). Each rating is from 0 to 10 for each player. The ratings
 * use the statistics, not the score.
 *
 * The scale is fixed for amateur tennis. Each input has two anchors: the value that gives 0 and the value that gives 10.
 * A value between the anchors gives a value on a straight line. A value outside the anchors gives 0 or 10. A rating is
 * the average of its inputs. The two players get independent values.
 *
 * At club level, the serve gives a small advantage. Thus 50% of the points and the games won on serve or on return
 * gives 5. A real match with both players near 50% on serve showed this (2026-11-18).
 *
 * An input counts only when both players have sufficient data for it. Thus the two players always have the same
 * inputs. A rating without an input has no value. [overall] is the weighted average of the ratings, and it needs two
 * ratings or more.
 */
data class PerformanceRatings(
    val areas: Map<RatingArea, PerPlayer<Double>>,
    val overall: PerPlayer<Double>?,
) {
    companion object {
        /** The data of fewer points than this does not give an input that counts points. */
        const val MIN_POINTS = 10

        /** The data of fewer games or chances than this does not give an input that counts games or chances. */
        const val MIN_EVENTS = 3

        /** The 2nd serves are fewer than the 1st serves. Thus their minimum is smaller. */
        const val MIN_SECOND_SERVES = 5

        const val MAX = 10.0

        /** The weight of each area in [overall]. Pressure uses few points, thus it has a smaller weight. */
        val WEIGHTS: Map<RatingArea, Double> = mapOf(
            RatingArea.SERVE to 1.0,
            RatingArea.RETURN to 1.0,
            RatingArea.ATTACK to 1.0,
            RatingArea.CONSISTENCY to 1.0,
            RatingArea.PRESSURE to 0.5,
        )

        fun of(stats: MatchStats, rules: MatchRulesV1): PerformanceRatings {
            val areas = RatingArea.entries.mapNotNull { area ->
                val inputs = inputs(area, stats, rules)
                if (inputs.isEmpty()) null else area to PerPlayer(inputs.map { it.p1 }.average(), inputs.map { it.p2 }.average())
            }.toMap()
            val overall = if (areas.size < 2) {
                null
            } else {
                fun weighted(player: Int): Double {
                    val total = areas.keys.sumOf { WEIGHTS.getValue(it) }
                    return areas.entries.sumOf { (area, value) -> WEIGHTS.getValue(area) * value[player] } / total
                }
                PerPlayer(weighted(1), weighted(2))
            }
            return PerformanceRatings(areas, overall)
        }

        /** The value from 0 to 10 of [value] between the anchors [zero] (gives 0) and [ten] (gives 10). */
        fun scale(value: Double, zero: Double, ten: Double): Double =
            (MAX * (value - zero) / (ten - zero)).coerceIn(0.0, MAX)

        /** The inputs of [area] that have sufficient data, each with the values from 0 to 10 of the two players. */
        private fun inputs(area: RatingArea, stats: MatchStats, rules: MatchRulesV1): List<PerPlayer<Double>> {
            val structure = rules.structure
            val hasGames = structure == MatchStructure.SETS || structure == MatchStructure.GAMES_ONLY
            val hasEnd = structure == MatchStructure.SETS || structure == MatchStructure.SINGLE_TIEBREAK
            val manual = rules.manualScoring
            val serve = stats.serve
            val details = stats.details?.takeIf { it.detailedPoints > 0 }
            val inputs = mutableListOf<PerPlayer<Double>>()

            /** A percentage input. The ratio of each player must have [min] events or more. */
            fun share(ratios: PerPlayer<Ratio>?, min: Int, zero: Double, ten: Double) {
                if (ratios == null || ratios.p1.total < min || ratios.p2.total < min) return
                fun value(ratio: Ratio) = scale(100.0 * ratio.won / ratio.total, zero, ten)
                inputs += PerPlayer(value(ratios.p1), value(ratios.p2))
            }

            /** A rate input: [count] of each player divided by [base] of the same player. */
            fun rate(count: PerPlayer<Int>, base: PerPlayer<Int>, zero: Double, ten: Double) {
                if (base.p1 < MIN_POINTS || base.p2 < MIN_POINTS) return
                inputs += PerPlayer(
                    scale(count.p1.toDouble() / base.p1, zero, ten),
                    scale(count.p2.toDouble() / base.p2, zero, ten),
                )
            }

            when (area) {
                RatingArea.SERVE -> {
                    share(serve?.servicePointsWon, MIN_POINTS, 30.0, 70.0)
                    if (hasGames) share(serve?.serviceGamesWon, MIN_EVENTS, 10.0, 90.0)
                    if (details != null && serve != null) {
                        share(details.firstServeIn, MIN_POINTS, 30.0, 80.0)
                        share(details.firstServePointsWon, MIN_POINTS, 40.0, 80.0)
                        share(details.secondServePointsWon, MIN_SECOND_SERVES, 30.0, 65.0)
                        val served = PerPlayer(details.firstServeIn.p1.total, details.firstServeIn.p2.total)
                        rate(details.unreturnedServes, served, 0.0, 0.25)
                        rate(details.doubleFaults, served, 0.15, 0.0)
                    }
                }
                RatingArea.RETURN -> {
                    share(serve?.returnPointsWon, MIN_POINTS, 30.0, 70.0)
                    if (hasGames) share(serve?.returnGamesWon, MIN_EVENTS, 10.0, 90.0)
                    if (details != null && serve != null) {
                        share(details.firstServeReturnPointsWon, MIN_POINTS, 20.0, 60.0)
                        share(details.secondServeReturnPointsWon, MIN_SECOND_SERVES, 35.0, 70.0)
                    }
                }
                RatingArea.ATTACK -> if (details != null) {
                    val points = PerPlayer(details.detailedPoints, details.detailedPoints)
                    val made = PerPlayer(
                        details.winners.p1 + details.forcedErrors.p2,
                        details.winners.p2 + details.forcedErrors.p1,
                    )
                    rate(made, points, 0.0, 0.4)
                }
                RatingArea.CONSISTENCY -> if (details != null) {
                    rate(details.unforcedErrors, PerPlayer(details.detailedPoints, details.detailedPoints), 0.45, 0.05)
                }
                RatingArea.PRESSURE -> if (!manual) {
                    if (hasGames) {
                        share(serve?.breakPointsWon, MIN_EVENTS, 10.0, 70.0)
                        share(serve?.breakPointsSaved, MIN_EVENTS, 20.0, 80.0)
                        if (rules.deuce != DeuceRule.NO_AD) share(stats.deuceGamesWon, MIN_EVENTS, 20.0, 80.0)
                        if (rules.deuce != DeuceRule.ADVANTAGE) share(stats.decidingPointsWon, MIN_EVENTS, 20.0, 80.0)
                    }
                    if (hasEnd) share(stats.tiebreaksWon, 2, 20.0, 80.0)
                }
            }
            return inputs
        }
    }
}
