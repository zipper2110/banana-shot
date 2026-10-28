package org.litvin.stats

import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStats
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.Ratio
import org.litvin.scoring.ScoredDuration
import java.util.Locale
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/** The groups of the statistics table, in display order. */
enum class StatGroup {
    OVERVIEW,
    SERVE,
    PRESSURE,
    POINT_LENGTH,
    MOMENTUM,
    TIME,
    ;

    val title: String
        get() = when (this) {
            OVERVIEW -> tr("stats.group.overview")
            SERVE -> tr("stats.group.serve")
            PRESSURE -> tr("stats.group.pressure")
            POINT_LENGTH -> tr("stats.group.pointLength")
            MOMENTUM -> tr("stats.group.momentum")
            TIME -> tr("stats.group.time")
        }
}

/**
 * The statistics that the app can show, in display order.
 * [key] is stored in stats.json. Do not change a key after a release.
 */
enum class MatchStat(val key: String, val group: StatGroup, val inVideoByDefault: Boolean = false) {
    POINTS_WON("points_won", StatGroup.OVERVIEW, inVideoByDefault = true),
    GAMES_WON("games_won", StatGroup.OVERVIEW),
    SETS_WON("sets_won", StatGroup.OVERVIEW),
    SERVICE_POINTS_WON("service_points_won", StatGroup.SERVE, inVideoByDefault = true),
    RETURN_POINTS_WON("return_points_won", StatGroup.SERVE),
    SERVICE_GAMES_WON("service_games_won", StatGroup.SERVE, inVideoByDefault = true),
    RETURN_GAMES_WON("return_games_won", StatGroup.SERVE),
    BREAK_POINTS_WON("break_points_won", StatGroup.PRESSURE, inVideoByDefault = true),
    BREAK_POINTS_SAVED("break_points_saved", StatGroup.PRESSURE),
    SET_POINTS_WON("set_points_won", StatGroup.PRESSURE),
    MATCH_POINTS_WON("match_points_won", StatGroup.PRESSURE),
    DEUCE_POINTS_WON("deuce_points_won", StatGroup.PRESSURE),
    /** The golden points or star points. [StatRows.label] gives the name of the deuce rule. */
    DECIDING_POINTS_WON("deciding_points_won", StatGroup.PRESSURE),
    AVERAGE_POINT_WON("average_point_won", StatGroup.POINT_LENGTH),
    SHORT_POINTS_WON("short_points_won", StatGroup.POINT_LENGTH, inVideoByDefault = true),
    LONG_POINTS_WON("long_points_won", StatGroup.POINT_LENGTH, inVideoByDefault = true),
    LONGEST_POINT_RUN("longest_point_run", StatGroup.MOMENTUM),
    LONGEST_GAME_RUN("longest_game_run", StatGroup.MOMENTUM),
    LARGEST_POINT_LEAD("largest_point_lead", StatGroup.MOMENTUM),
    DURATION("duration", StatGroup.TIME, inVideoByDefault = true),
    PLAYING_TIME("playing_time", StatGroup.TIME),
    AVERAGE_POINT("average_point", StatGroup.TIME),
    LONGEST_POINT("longest_point", StatGroup.TIME),
    AVERAGE_GAP("average_gap", StatGroup.TIME),
    ;

    /** The name of the statistic in the app language. */
    val label: String
        get() = when (this) {
            POINTS_WON -> tr("stats.row.pointsWon")
            GAMES_WON -> tr("stats.row.gamesWon")
            SETS_WON -> tr("stats.row.setsWon")
            SERVICE_POINTS_WON -> tr("stats.row.servicePointsWon")
            RETURN_POINTS_WON -> tr("stats.row.returnPointsWon")
            SERVICE_GAMES_WON -> tr("stats.row.serviceGamesWon")
            RETURN_GAMES_WON -> tr("stats.row.returnGamesWon")
            BREAK_POINTS_WON -> tr("stats.row.breakPointsWon")
            BREAK_POINTS_SAVED -> tr("stats.row.breakPointsSaved")
            SET_POINTS_WON -> tr("stats.row.setPointsWon")
            MATCH_POINTS_WON -> tr("stats.row.matchPointsWon")
            DEUCE_POINTS_WON -> tr("stats.row.deucePointsWon")
            DECIDING_POINTS_WON -> tr("stats.row.decidingPointsWon")
            AVERAGE_POINT_WON -> tr("stats.row.averagePointWon")
            SHORT_POINTS_WON -> tr("stats.row.shortPointsWon")
            LONG_POINTS_WON -> tr("stats.row.longPointsWon")
            LONGEST_POINT_RUN -> tr("stats.row.longestPointRun")
            LONGEST_GAME_RUN -> tr("stats.row.longestGameRun")
            LARGEST_POINT_LEAD -> tr("stats.row.largestPointLead")
            DURATION -> tr("stats.row.duration")
            PLAYING_TIME -> tr("stats.row.playingTime")
            AVERAGE_POINT -> tr("stats.row.averagePoint")
            LONGEST_POINT -> tr("stats.row.longestPoint")
            AVERAGE_GAP -> tr("stats.row.averageGap")
        }

    companion object {
        fun ofKey(key: String): MatchStat? = entries.firstOrNull { it.key == key }

        val defaultVideoKeys: List<String> = entries.filter { it.inVideoByDefault }.map { it.key }
    }
}

/** A value in the table: the main [text], and an optional [detail], for example "64%" and "32/50". */
data class StatValue(val text: String, val detail: String? = null)

/**
 * One row of the statistics table.
 * A row has one value for each player ([values]) or one value for both players ([shared]).
 * [bar] holds the numbers that the video compares in a bar, or null when the row has no bar.
 * When the row has no value, [unavailableReason] tells the user why.
 */
data class StatRow(
    val stat: MatchStat,
    val values: PerPlayer<StatValue>? = null,
    val shared: StatValue? = null,
    val unavailableReason: String? = null,
    val bar: PerPlayer<Double>? = null,
    /** The point that each value comes from, when the value comes from one point. */
    val pointIds: PerPlayer<String?>? = null,
    /** The point that the shared value comes from. */
    val sharedPointId: String? = null,
    /** The text of the row. Some rows add their settings to [MatchStat.label]. */
    val label: String = stat.label,
) {
    val available: Boolean get() = unavailableReason == null
}

/** Makes the table rows from [MatchStats]. The rows for the video are a subset of these rows. */
object StatRows {
    val NEEDS_SERVER: String get() = tr("stats.reason.needsServer")
    val MANUAL_SCORING: String get() = tr("stats.reason.manualScoring")
    val NO_SETS: String get() = tr("stats.reason.noSets")
    val NO_GAMES: String get() = tr("stats.reason.noGames")
    val NO_MATCH_END: String get() = tr("stats.reason.noMatchEnd")
    val NO_VALUE: String get() = tr("stats.reason.noValue")
    val NO_POINTS_OF_LENGTH: String get() = tr("stats.reason.noPointsOfLength")
    val NO_DECIDING_POINT: String get() = tr("stats.reason.noDecidingPoint")

    fun build(stats: MatchStats, rules: MatchRulesV1, settings: StatsSettingsV1 = StatsSettingsV1()): List<StatRow> {
        val normalized = settings.normalized()
        return MatchStat.entries.map { row(it, stats, rules, normalized) }
    }

    /**
     * The text of a row with its setting, for example "Short points won (≤ 7 s)".
     * With [rules], the deciding points row gets the name of the deuce rule, for example "Golden points won".
     */
    fun label(stat: MatchStat, settings: StatsSettingsV1, rules: MatchRulesV1? = null): String = when (stat) {
        MatchStat.SHORT_POINTS_WON -> "${stat.label} (≤ ${settings.normalized().shortPointMaxSeconds} s)"
        MatchStat.LONG_POINTS_WON -> "${stat.label} (≥ ${settings.normalized().longPointMinSeconds} s)"
        MatchStat.DECIDING_POINTS_WON -> when (rules?.deuce) {
            DeuceRule.NO_AD -> tr("stats.row.goldenPointsWon")
            DeuceRule.STAR_POINT -> tr("stats.row.starPointsWon")
            DeuceRule.ADVANTAGE, null -> stat.label
        }
        else -> stat.label
    }

    private fun row(stat: MatchStat, stats: MatchStats, rules: MatchRulesV1, settings: StatsSettingsV1): StatRow {
        val structure = rules.structure
        val hasGames = structure == MatchStructure.SETS || structure == MatchStructure.GAMES_ONLY
        val hasSets = structure == MatchStructure.SETS
        val hasEnd = structure == MatchStructure.SETS || structure == MatchStructure.SINGLE_TIEBREAK
        // A total points match has an end, but no sets.
        val hasMatchEnd = hasEnd || structure == MatchStructure.TOTAL_POINTS
        val manual = rules.manualScoring
        val serve = stats.serve
        val time = stats.time

        fun unavailable(reason: String) = StatRow(stat, unavailableReason = reason)
        fun perPlayer(values: PerPlayer<StatValue>, bar: PerPlayer<Double>? = null) = StatRow(stat, values = values, bar = bar)
        fun counts(values: PerPlayer<Int>) = perPlayer(values.map(::count), values.map { it.toDouble() })
        fun shares(values: PerPlayer<Ratio>) = perPlayer(values.map(::share), values.map { (it.percent ?: 0).toDouble() })
        fun fractions(values: PerPlayer<Ratio>) = perPlayer(values.map(::fraction), values.map { it.won.toDouble() })
        fun shared(value: StatValue?) = if (value == null) unavailable(NO_VALUE) else StatRow(stat, shared = value)
        /** The share of the points of one length that each player won. */
        fun lengthShares(points: List<ScoredDuration>): StatRow {
            if (points.isEmpty()) return unavailable(NO_POINTS_OF_LENGTH)
            fun wonBy(player: Int) = Ratio(points.count { it.winner == player }, points.size)
            return shares(PerPlayer(wonBy(1), wonBy(2)))
        }

        return when (stat) {
            MatchStat.POINTS_WON -> perPlayer(
                stats.pointsWon.map { won -> StatValue(won.toString(), percent(Ratio(won, stats.scoredPoints))) },
                stats.pointsWon.map { it.toDouble() },
            )
            MatchStat.GAMES_WON -> if (!hasGames) {
                unavailable(NO_GAMES)
            } else {
                val games = stats.gamesWon.p1 + stats.gamesWon.p2
                perPlayer(
                    stats.gamesWon.map { won -> StatValue(won.toString(), percent(Ratio(won, games))) },
                    stats.gamesWon.map { it.toDouble() },
                )
            }
            MatchStat.SETS_WON -> if (!hasSets) unavailable(NO_SETS) else counts(stats.setsWon)
            MatchStat.SERVICE_POINTS_WON -> if (serve == null) unavailable(NEEDS_SERVER) else shares(serve.servicePointsWon)
            MatchStat.RETURN_POINTS_WON -> if (serve == null) unavailable(NEEDS_SERVER) else shares(serve.returnPointsWon)
            MatchStat.SERVICE_GAMES_WON -> when {
                !hasGames -> unavailable(NO_GAMES)
                serve == null -> unavailable(NEEDS_SERVER)
                else -> shares(serve.serviceGamesWon)
            }
            MatchStat.RETURN_GAMES_WON -> when {
                !hasGames -> unavailable(NO_GAMES)
                serve == null -> unavailable(NEEDS_SERVER)
                else -> shares(serve.returnGamesWon)
            }
            MatchStat.BREAK_POINTS_WON -> when {
                !hasGames -> unavailable(NO_GAMES)
                manual -> unavailable(MANUAL_SCORING)
                serve == null -> unavailable(NEEDS_SERVER)
                else -> fractions(serve.breakPointsWon)
            }
            MatchStat.BREAK_POINTS_SAVED -> when {
                !hasGames -> unavailable(NO_GAMES)
                manual -> unavailable(MANUAL_SCORING)
                serve == null -> unavailable(NEEDS_SERVER)
                else -> fractions(serve.breakPointsSaved)
            }
            MatchStat.SET_POINTS_WON -> when {
                !hasEnd -> unavailable(NO_SETS)
                manual -> unavailable(MANUAL_SCORING)
                else -> fractions(stats.setPointsWon)
            }
            MatchStat.MATCH_POINTS_WON -> when {
                !hasMatchEnd -> unavailable(NO_MATCH_END)
                manual -> unavailable(MANUAL_SCORING)
                else -> fractions(stats.matchPointsWon)
            }
            MatchStat.DEUCE_POINTS_WON -> when {
                !hasGames -> unavailable(NO_GAMES)
                manual -> unavailable(MANUAL_SCORING)
                else -> counts(stats.deucePointsWon)
            }
            MatchStat.DECIDING_POINTS_WON -> when {
                !hasGames -> unavailable(NO_GAMES)
                manual -> unavailable(MANUAL_SCORING)
                rules.deuce == DeuceRule.ADVANTAGE -> unavailable(NO_DECIDING_POINT)
                else -> fractions(stats.decidingPointsWon)
            }
            MatchStat.LONGEST_POINT_RUN -> counts(stats.longestPointRun).copy(pointIds = stats.keyPoints.longestPointRunStart)
            MatchStat.LONGEST_GAME_RUN -> if (!hasGames) unavailable(NO_GAMES) else counts(stats.longestGameRun)
            MatchStat.LARGEST_POINT_LEAD -> counts(stats.largestPointLead).copy(pointIds = stats.keyPoints.largestPointLeadAt)
            MatchStat.DURATION -> shared(time.durationMs.takeIf { stats.scoredPoints > 0 }?.let { StatValue(clock(it)) })
            MatchStat.PLAYING_TIME -> shared(time.playingMs.takeIf { stats.points > 0 }?.let { StatValue(clock(it)) })
            MatchStat.AVERAGE_POINT -> shared(time.averagePointMs?.let { StatValue(seconds(it)) })
            MatchStat.LONGEST_POINT -> shared(time.longestPoint?.let { StatValue(seconds((it.endMs - it.startMs).toLong())) })
                .copy(sharedPointId = time.longestPoint?.id)
            MatchStat.AVERAGE_POINT_WON -> perPlayer(time.averagePointMsWon.map { ms -> StatValue(ms?.let(::seconds) ?: "—") })
            MatchStat.AVERAGE_GAP -> shared(time.averageGapMs?.let { StatValue(seconds(it)) })
            MatchStat.SHORT_POINTS_WON ->
                lengthShares(time.scoredDurations.filter { it.durationMs <= settings.shortPointMaxSeconds * 1000L })
            MatchStat.LONG_POINTS_WON ->
                lengthShares(time.scoredDurations.filter { it.durationMs >= settings.longPointMinSeconds * 1000L })
        }.copy(label = label(stat, settings, rules))
    }

    private fun <T, R> PerPlayer<T>.map(transform: (T) -> R): PerPlayer<R> = PerPlayer(transform(p1), transform(p2))

    private fun count(value: Int) = StatValue(value.toString())

    /** A percentage first, for example "64%" with the detail "32/50". */
    private fun share(ratio: Ratio) = StatValue(ratio.percent?.let(DisplayFormats::percent) ?: "—", "${ratio.won}/${ratio.total}")

    /** A fraction first, for example "3/7" with the detail "43%". */
    private fun fraction(ratio: Ratio) = StatValue("${ratio.won}/${ratio.total}", percent(ratio))

    private fun percent(ratio: Ratio): String? = ratio.percent?.let(DisplayFormats::percent)

    /** "12.3 s", with the decimal separator of the format locale. */
    fun seconds(ms: Long): String = tr("unit.seconds", "value" to DisplayFormats.decimal(ms / 1000.0, 1))

    /** "42:07" or "1:42:07" */
    fun clock(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }
}
