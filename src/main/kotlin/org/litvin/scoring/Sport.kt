package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

/**
 * The sport of a project. Tennis and padel use the same point, game, and set counting.
 * The sport sets the match formats that the app shows, the default rules, and the names of the two sides.
 */
enum class Sport(val title: String, /** The word for one side: "Player" or "Team". */ val sideNoun: String) {
    @JsonEnumDefaultValue
    TENNIS("Tennis", "Player"),

    /** Padel is always doubles. Each side is a team of two players. */
    PADEL("Padel", "Team"),
    ;

    /** The rules of a new project. */
    fun defaultRules(): MatchRulesV1 = when (this) {
        TENNIS -> MatchRulesV1()
        // The FIP rules use the star point since 2026.
        PADEL -> MatchRulesV1(deuce = DeuceRule.STAR_POINT)
    }

    /** The name of side 1 or 2 when the user did not type a name, for example "Team 1". */
    fun defaultSideName(side: Int): String = "$sideNoun $side"

    /** The deuce rules that the score settings offer. A [current] rule that the sport does not use stays in the list. */
    fun deuceRules(current: DeuceRule): List<DeuceRule> {
        val rules = when (this) {
            TENNIS -> listOf(DeuceRule.ADVANTAGE, DeuceRule.NO_AD)
            PADEL -> listOf(DeuceRule.ADVANTAGE, DeuceRule.NO_AD, DeuceRule.STAR_POINT)
        }
        return if (current in rules) rules else rules + current
    }

    /** The name of [rule] in this sport. Padel calls the no-ad deciding point the "golden point". */
    fun deuceTitle(rule: DeuceRule): String = when (rule) {
        DeuceRule.ADVANTAGE -> "Advantage"
        DeuceRule.NO_AD -> if (this == PADEL) "Golden point" else "No-ad"
        DeuceRule.STAR_POINT -> "Star point"
    }

    /** An explanation of [rule]. */
    fun deuceDescription(rule: DeuceRule): String = when (rule) {
        DeuceRule.ADVANTAGE -> "win by 2 points"
        DeuceRule.NO_AD -> "deciding point at 40–40"
        DeuceRule.STAR_POINT -> "deciding point at the third deuce"
    }

    /**
     * The scoreboard badge of a deciding point under [rule], for example "STAR POINT".
     * Null for [DeuceRule.ADVANTAGE], which has no deciding point.
     */
    fun decidingPointBadge(rule: DeuceRule): String? = when (rule) {
        DeuceRule.ADVANTAGE -> null
        DeuceRule.NO_AD -> if (this == PADEL) "GOLDEN POINT" else "DECIDING POINT"
        DeuceRule.STAR_POINT -> "STAR POINT"
    }

    /** A shorter explanation of [rule], for a control with three rules side by side. */
    fun deuceShortDescription(rule: DeuceRule): String = when (rule) {
        DeuceRule.ADVANTAGE -> "win by 2 points"
        DeuceRule.NO_AD -> "at 40–40"
        DeuceRule.STAR_POINT -> "at the 3rd deuce"
    }
}
