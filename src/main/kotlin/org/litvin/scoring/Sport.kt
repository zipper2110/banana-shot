package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats
import org.litvin.i18n.I18n

/**
 * The sport of a project. Tennis and padel use the same point, game, and set counting.
 * The sport sets the match formats that the app shows, the default rules, and the names of the two sides.
 */
enum class Sport {
    @JsonEnumDefaultValue
    TENNIS,

    /** Padel is always doubles. Each side is a team of two players. */
    PADEL,
    ;

    val title: String
        get() = when (this) {
            TENNIS -> tr("sport.tennis")
            PADEL -> tr("sport.padel")
        }

    /** The rules of a new project. */
    fun defaultRules(): MatchRulesV1 = when (this) {
        TENNIS -> MatchRulesV1()
        // The FIP rules use the star point since 2026.
        PADEL -> MatchRulesV1(deuce = DeuceRule.STAR_POINT)
    }

    /**
     * The name of side 1 or 2 when the user did not type a name, for example "Team 1". It is one catalog text with
     * the number as a placeholder (B-11 plan, section 6).
     */
    fun defaultSideName(side: Int): String = when (this) {
        TENNIS -> tr("sport.tennis.side", "number" to side)
        PADEL -> tr("sport.padel.side", "number" to side)
    }

    /** [defaultSideName] in all app languages. A project keeps the default name of the language that made it. */
    fun defaultSideNames(side: Int): Set<String> = when (this) {
        TENNIS -> I18n.inAllLanguages("sport.tennis.side", "number" to side)
        PADEL -> I18n.inAllLanguages("sport.padel.side", "number" to side)
    }

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
        DeuceRule.ADVANTAGE -> tr("deuce.advantage")
        DeuceRule.NO_AD -> if (this == PADEL) tr("deuce.goldenPoint") else tr("deuce.noAd")
        DeuceRule.STAR_POINT -> tr("deuce.starPoint")
    }

    /** An explanation of [rule]. */
    fun deuceDescription(rule: DeuceRule): String = when (rule) {
        DeuceRule.ADVANTAGE -> tr("deuce.advantage.description")
        DeuceRule.NO_AD -> tr("deuce.noAd.description")
        DeuceRule.STAR_POINT -> tr("deuce.starPoint.description")
    }

    /**
     * The scoreboard badge of a deciding point under [rule], for example "STAR POINT".
     * Null for [DeuceRule.ADVANTAGE], which has no deciding point.
     */
    fun decidingPointBadge(rule: DeuceRule): String? = when (rule) {
        DeuceRule.ADVANTAGE -> null
        DeuceRule.NO_AD -> DisplayFormats.upper(if (this == PADEL) tr("video.badge.goldenPoint") else tr("video.badge.decidingPoint"))
        DeuceRule.STAR_POINT -> DisplayFormats.upper(tr("video.badge.starPoint"))
    }

    /** A shorter explanation of [rule], for a control with three rules side by side. */
    fun deuceShortDescription(rule: DeuceRule): String = when (rule) {
        DeuceRule.ADVANTAGE -> tr("deuce.advantage.description")
        DeuceRule.NO_AD -> tr("deuce.noAd.short")
        DeuceRule.STAR_POINT -> tr("deuce.starPoint.short")
    }
}
