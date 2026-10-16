package org.litvin.scoring

/** Two numbers of a score: one for side 1 and one for side 2. */
data class ScorePairV1(val p1: Int = 0, val p2: Int = 0)

/**
 * The games of one completed set in a [ScoreOverrideV1]. [tiebreakP1] and [tiebreakP2] are the tiebreak points of a
 * set that ended in a tiebreak, for example 8–6 for a 7–6 set. They are null for a set without tiebreak points.
 */
data class SetGamesV1(
    val p1: Int = 0,
    val p2: Int = 0,
    val tiebreakP1: Int? = null,
    val tiebreakP2: Int? = null,
)

/**
 * A score that the user typed for the end of a point. The engine uses this score after the point and counts the next
 * points from it. Each part is optional: a null part stays computed from the points, so it changes when the points
 * before it change.
 * - [sets]: the games of each completed set, in order. The set count comes from these sets.
 * - [games]: the games of the current set.
 * - [points]: the points of the current game, as the engine counts them (0, 1, 2, 3 for 0, 15, 30, 40; one more for
 *   advantage), or the points of a tiebreak.
 */
data class ScoreOverrideV1(
    val sets: List<SetGamesV1>? = null,
    val games: ScorePairV1? = null,
    val points: ScorePairV1? = null,
) {
    /** True when no part is set. Such an override changes nothing. */
    fun isEmpty(): Boolean = sets == null && games == null && points == null
}
