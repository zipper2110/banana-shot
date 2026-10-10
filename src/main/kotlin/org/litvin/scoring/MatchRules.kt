package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

/** The unit that the match is played in. */
enum class MatchStructure {
    /** Games make sets, and sets make the match. */
    @JsonEnumDefaultValue
    SETS,

    /** Games are counted without sets. The match has no end. Use it for practice games. */
    GAMES_ONLY,

    /** One tiebreak is the full match. */
    SINGLE_TIEBREAK,

    /** Points are counted 1, 2, 3 like in a tiebreak, without a points limit. The match has no end. */
    PLAIN_POINTS,

    /**
     * Points are counted 1, 2, 3 until the sum of both scores is [MatchRulesV1.totalPoints]. The side with more points wins.
     * Equal scores are a draw. Each side serves [MatchRulesV1.serveTurnPoints] points in a row. Padel Americano uses it.
     */
    TOTAL_POINTS,
}

/** What happens when the game score is 40–40. */
enum class DeuceRule {
    /** A player must win two points in a row after deuce. */
    @JsonEnumDefaultValue
    ADVANTAGE,

    /** At 40–40, the next point wins the game (deciding point). Padel calls it the golden point. */
    NO_AD,

    /**
     * Padel (FIP rules since 2026): the first two deuces use advantage. At the third deuce, the next point wins the game.
     * In points, that deciding point is played at 5–5.
     */
    STAR_POINT,
}

/** How the deciding set is played when the sets are equal. */
enum class FinalSetRule {
    @JsonEnumDefaultValue
    FULL_SET,

    /** A tiebreak to [MatchRulesV1.MATCH_TIEBREAK_POINTS] points is played in place of the deciding set. */
    MATCH_TIEBREAK,
}

/**
 * Point counting rules of a match. They are stored in score.json.
 *
 * When [manualScoring] is true, the engine counts points only. The user marks each game and set win.
 */
data class MatchRulesV1(
    val manualScoring: Boolean = false,
    val structure: MatchStructure = MatchStructure.SETS,
    /** 1, 3 or 5. */
    val bestOfSets: Int = 3,
    /** The number of games that wins a set (with a two-game lead). */
    val gamesPerSet: Int = 6,
    /** True: a tiebreak is played when the games are equal at [gamesPerSet]. False: the set continues until a two-game lead. */
    val setTiebreak: Boolean = true,
    /**
     * True: the set tiebreak is at one game less, for example at 8–8 in a pro set to 9 games. Thus the set ends at
     * [gamesPerSet] games, for example 9–7 or 9–8 after the tiebreak. Only with [setTiebreak].
     */
    val earlyTiebreak: Boolean = false,
    /** The points that win a set tiebreak or a [MatchStructure.SINGLE_TIEBREAK] (with a two-point lead). */
    val tiebreakPoints: Int = 7,
    val finalSet: FinalSetRule = FinalSetRule.FULL_SET,
    val deuce: DeuceRule = DeuceRule.ADVANTAGE,
    /** [MatchStructure.TOTAL_POINTS]: the points that both sides play together. */
    val totalPoints: Int = 24,
    /** [MatchStructure.TOTAL_POINTS]: the points that one side serves in a row. */
    val serveTurnPoints: Int = 4,
) {
    /** The number of sets that wins the match. */
    fun setsToWin(): Int = bestOfSets / 2 + 1

    /** The games of each side at which the set tiebreak starts. */
    fun setTiebreakGames(): Int = if (earlyTiebreak) gamesPerSet - 1 else gamesPerSet

    /** True when the deciding set is a match tiebreak. A one-set match has no deciding set. */
    fun hasMatchTiebreakDecider(): Boolean = finalSet == FinalSetRule.MATCH_TIEBREAK && bestOfSets > 1

    /** Returns a copy with all values in their allowed ranges. */
    fun normalized(): MatchRulesV1 = copy(
        bestOfSets = bestOfSets.takeIf { it in BEST_OF_OPTIONS } ?: 3,
        gamesPerSet = gamesPerSet.coerceIn(MIN_GAMES_PER_SET, MAX_GAMES_PER_SET),
        // Only with a set tiebreak. A tiebreak at 0–0 is not possible.
        earlyTiebreak = earlyTiebreak && setTiebreak && gamesPerSet.coerceIn(MIN_GAMES_PER_SET, MAX_GAMES_PER_SET) > 1,
        tiebreakPoints = tiebreakPoints.coerceIn(MIN_TIEBREAK_POINTS, MAX_TIEBREAK_POINTS),
        totalPoints = totalPoints.coerceIn(MIN_TOTAL_POINTS, MAX_TOTAL_POINTS),
        serveTurnPoints = serveTurnPoints.coerceIn(MIN_SERVE_TURN_POINTS, MAX_SERVE_TURN_POINTS),
    )

    /**
     * Returns a copy where the values that [structure] does not use have their defaults.
     * Two rules with the same scoring behavior have the same canonical form.
     */
    fun canonical(): MatchRulesV1 {
        val rules = normalized()
        val defaults = MatchRulesV1()
        return when (rules.structure) {
            MatchStructure.SETS -> rules.copy(
                tiebreakPoints = if (rules.setTiebreak) rules.tiebreakPoints else defaults.tiebreakPoints,
                earlyTiebreak = rules.setTiebreak && rules.earlyTiebreak,
                finalSet = if (rules.bestOfSets > 1) rules.finalSet else defaults.finalSet,
                totalPoints = defaults.totalPoints,
                serveTurnPoints = defaults.serveTurnPoints,
            )
            MatchStructure.GAMES_ONLY -> defaults.copy(
                manualScoring = rules.manualScoring,
                structure = rules.structure,
                deuce = rules.deuce,
            )
            MatchStructure.SINGLE_TIEBREAK -> defaults.copy(
                manualScoring = rules.manualScoring,
                structure = rules.structure,
                tiebreakPoints = rules.tiebreakPoints,
            )
            MatchStructure.PLAIN_POINTS -> defaults.copy(
                manualScoring = rules.manualScoring,
                structure = rules.structure,
            )
            MatchStructure.TOTAL_POINTS -> defaults.copy(
                manualScoring = rules.manualScoring,
                structure = rules.structure,
                totalPoints = rules.totalPoints,
                serveTurnPoints = rules.serveTurnPoints,
            )
        }
    }

    companion object {
        const val MATCH_TIEBREAK_POINTS = 10
        val BEST_OF_OPTIONS = listOf(1, 3, 5)
        val GAMES_PER_SET_OPTIONS = listOf(4, 6, 8, 9)
        /** The games in a set that offer [earlyTiebreak]: the pro set to 9 games with a tiebreak at 8–8 (see padel.md). */
        val EARLY_TIEBREAK_GAMES = setOf(9)
        val TIEBREAK_POINTS_OPTIONS = listOf(7, 10)
        const val MIN_GAMES_PER_SET = 1
        const val MAX_GAMES_PER_SET = 12
        const val MIN_TIEBREAK_POINTS = 3
        const val MAX_TIEBREAK_POINTS = 21
        val TOTAL_POINTS_OPTIONS = listOf(16, 21, 24, 32)
        val SERVE_TURN_POINTS_OPTIONS = listOf(2, 4)
        const val MIN_TOTAL_POINTS = 2
        const val MAX_TOTAL_POINTS = 99
        const val MIN_SERVE_TURN_POINTS = 1
        const val MAX_SERVE_TURN_POINTS = 8
    }
}

/**
 * Popular match formats. Each preset sets the match structure. The deuce rule, manual scoring, and the points of a
 * [MatchStructure.TOTAL_POINTS] match are separate choices, so a preset keeps them.
 *
 * [sports] are the sports that show the preset. [padelTitle] and [padelDescription] replace the tennis words in padel,
 * for example "super tiebreak" for "match tiebreak".
 */
enum class MatchFormatPreset(
    val title: String,
    val description: String,
    private val rules: MatchRulesV1?,
    private val sports: Set<Sport> = Sport.entries.toSet(),
    private val padelTitle: String = title,
    private val padelDescription: String = description,
) {
    BEST_OF_3(
        "Best of 3 sets",
        "Standard match. Sets to 6 games, tiebreak at 6–6.",
        MatchRulesV1(),
    ),
    BEST_OF_3_MATCH_TIEBREAK(
        "Best of 3 sets, match tiebreak",
        "Sets to 6 games. At one set all, a 10-point match tiebreak decides the match.",
        MatchRulesV1(finalSet = FinalSetRule.MATCH_TIEBREAK),
        padelTitle = "Best of 3 sets, super tiebreak",
        padelDescription = "Sets to 6 games. At one set all, a 10-point super tiebreak decides the match.",
    ),
    BEST_OF_5(
        "Best of 5 sets",
        "Grand Slam format. Sets to 6 games, tiebreak at 6–6.",
        MatchRulesV1(bestOfSets = 5),
        sports = setOf(Sport.TENNIS),
    ),
    ONE_SET(
        "One set",
        "One set to 6 games, tiebreak at 6–6.",
        MatchRulesV1(bestOfSets = 1),
    ),
    PRO_SET(
        "Pro set (8 games)",
        "One set to 8 games, tiebreak at 8–8.",
        MatchRulesV1(bestOfSets = 1, gamesPerSet = 8),
    ),
    PRO_SET_9(
        "Pro set (9 games)",
        "One set to 9 games, tiebreak at 8–8.",
        MatchRulesV1(bestOfSets = 1, gamesPerSet = 9, earlyTiebreak = true),
        sports = setOf(Sport.PADEL),
    ),
    SHORT_SETS(
        "Short sets (4 games)",
        "Best of 3 sets to 4 games, tiebreak at 4–4.",
        MatchRulesV1(gamesPerSet = 4),
    ),
    GAMES_ONLY(
        "Games only",
        "Count games without sets. Use it for practice games.",
        MatchRulesV1(structure = MatchStructure.GAMES_ONLY),
    ),
    AMERICANO(
        "Americano (total points)",
        "Count points 1, 2, 3 until both teams together played the total, for example 24 points. " +
            "The team with more points wins. Equal points are a draw.",
        MatchRulesV1(structure = MatchStructure.TOTAL_POINTS),
        sports = setOf(Sport.PADEL),
    ),
    TIEBREAK(
        "Tiebreak (7 points)",
        "One tiebreak to 7 points with a two-point lead.",
        MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK, tiebreakPoints = 7),
    ),
    MATCH_TIEBREAK(
        "Match tiebreak (10 points)",
        "One tiebreak to 10 points with a two-point lead.",
        MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK, tiebreakPoints = 10),
        padelTitle = "Super tiebreak (10 points)",
    ),
    PLAIN_POINTS(
        "Plain points (endless tiebreak)",
        "Count points 1, 2, 3 like in a tiebreak, without a points limit. The match has no end.",
        MatchRulesV1(structure = MatchStructure.PLAIN_POINTS),
    ),
    CUSTOM(
        "Custom",
        "Your own combination of the rules below.",
        null,
    ),
    ;

    fun title(sport: Sport): String = if (sport == Sport.PADEL) padelTitle else title

    fun description(sport: Sport): String = if (sport == Sport.PADEL) padelDescription else description

    /**
     * Applies this preset to [current]. The deuce rule, manual scoring, and the total points settings of [current]
     * stay. [CUSTOM] returns [current].
     */
    fun applyTo(current: MatchRulesV1): MatchRulesV1 =
        rules?.copy(
            manualScoring = current.manualScoring,
            deuce = current.deuce,
            totalPoints = current.totalPoints,
            serveTurnPoints = current.serveTurnPoints,
        ) ?: current

    companion object {
        /** The presets that [sport] shows, in display order. [CUSTOM] is the last one. */
        fun forSport(sport: Sport): List<MatchFormatPreset> = entries.filter { sport in it.sports }

        /** Returns the preset of [sport] that has the same structure as [rules], or [CUSTOM]. */
        fun of(rules: MatchRulesV1, sport: Sport = Sport.TENNIS): MatchFormatPreset {
            val target = rules.canonical()
            return forSport(sport).firstOrNull { preset ->
                preset.rules != null && preset.applyTo(target).canonical() == target
            } ?: CUSTOM
        }
    }
}

/** Game and set wins that the user marked by hand in manual scoring. The maps use point ids as keys. */
data class ManualScoreMarks(
    val gameWins: Map<String, Outcome> = emptyMap(),
    val setWins: Map<String, Outcome> = emptyMap(),
)
