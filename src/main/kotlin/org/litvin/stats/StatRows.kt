package org.litvin.stats

import org.litvin.scoring.DetailStats
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.ErrorKind
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStats
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.Ratio
import org.litvin.scoring.ScoredDuration
import org.litvin.scoring.Sport
import java.util.Locale
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/** The groups of the statistics table, in display order. */
enum class StatGroup {
    /** The performance ratings from 0 to 10 (B-77, experimental). */
    RATINGS,
    OVERVIEW,
    SERVE,
    /** How the points ended (B-68). Only a project in advanced mode has these rows. */
    ENDINGS,
    PRESSURE,
    POINT_LENGTH,
    TIME,
    ;

    val title: String
        get() = when (this) {
            RATINGS -> tr("stats.group.ratings")
            OVERVIEW -> tr("stats.group.overview")
            SERVE -> tr("stats.group.serve")
            ENDINGS -> tr("stats.group.endings")
            PRESSURE -> tr("stats.group.pressure")
            POINT_LENGTH -> tr("stats.group.pointLength")
            TIME -> tr("stats.group.time")
        }
}

/**
 * The statistics that the app can show, in display order.
 * [key] is stored in stats.json. Do not change a key after a release.
 * [advanced] is true for a row from the point details (B-68). Only a project in advanced mode shows these rows.
 */
enum class MatchStat(
    val key: String,
    val group: StatGroup,
    val inVideoByDefault: Boolean = false,
    val advanced: Boolean = false,
) {
    /** The performance ratings (B-77). [PerformanceRatings] has the formulas. Attack and Consistency need advanced mode. */
    RATING_OVERALL("rating_overall", StatGroup.RATINGS),
    RATING_SERVE("rating_serve", StatGroup.RATINGS),
    RATING_RETURN("rating_return", StatGroup.RATINGS),
    RATING_ATTACK("rating_attack", StatGroup.RATINGS, advanced = true),
    RATING_CONSISTENCY("rating_consistency", StatGroup.RATINGS, advanced = true),
    RATING_PRESSURE("rating_pressure", StatGroup.RATINGS),
    POINTS_WON("points_won", StatGroup.OVERVIEW, inVideoByDefault = true),
    GAMES_WON("games_won", StatGroup.OVERVIEW),
    SETS_WON("sets_won", StatGroup.OVERVIEW),
    /** The set tiebreaks, the match tiebreaks, and the single tiebreak that the player won (B-76). */
    TIEBREAKS_WON("tiebreaks_won", StatGroup.OVERVIEW),
    ACES("aces", StatGroup.SERVE, inVideoByDefault = true, advanced = true),
    /** The aces and the unreturned serves (B-76). */
    UNRETURNED_SERVES("unreturned_serves", StatGroup.SERVE, advanced = true),
    DOUBLE_FAULTS("double_faults", StatGroup.SERVE, inVideoByDefault = true, advanced = true),
    FIRST_SERVE_IN("first_serve_in", StatGroup.SERVE, advanced = true),
    FIRST_SERVE_POINTS_WON("first_serve_points_won", StatGroup.SERVE, advanced = true),
    SECOND_SERVE_POINTS_WON("second_serve_points_won", StatGroup.SERVE, advanced = true),
    SERVICE_POINTS_WON("service_points_won", StatGroup.SERVE, inVideoByDefault = true),
    RETURN_POINTS_WON("return_points_won", StatGroup.SERVE),
    FIRST_SERVE_RETURN_POINTS_WON("first_serve_return_points_won", StatGroup.SERVE, advanced = true),
    SECOND_SERVE_RETURN_POINTS_WON("second_serve_return_points_won", StatGroup.SERVE, advanced = true),
    SERVICE_GAMES_WON("service_games_won", StatGroup.SERVE, inVideoByDefault = true),
    RETURN_GAMES_WON("return_games_won", StatGroup.SERVE),
    WINNERS("winners", StatGroup.ENDINGS, inVideoByDefault = true, advanced = true),
    FORCED_ERRORS("forced_errors", StatGroup.ENDINGS, advanced = true),
    UNFORCED_ERRORS("unforced_errors", StatGroup.ENDINGS, inVideoByDefault = true, advanced = true),
    /** The winners of a player divided by the unforced errors of the same player. */
    WINNERS_TO_UNFORCED_ERRORS("winners_to_unforced_errors", StatGroup.ENDINGS, advanced = true),
    /** The errors with the error kind [ErrorKind.NET]. */
    ERRORS_NET("errors_net", StatGroup.ENDINGS, advanced = true),
    ERRORS_LONG("errors_long", StatGroup.ENDINGS, advanced = true),
    ERRORS_WIDE("errors_wide", StatGroup.ENDINGS, advanced = true),
    BREAK_POINTS_WON("break_points_won", StatGroup.PRESSURE, inVideoByDefault = true),
    BREAK_POINTS_SAVED("break_points_saved", StatGroup.PRESSURE),
    SET_POINTS_WON("set_points_won", StatGroup.PRESSURE),
    MATCH_POINTS_WON("match_points_won", StatGroup.PRESSURE),
    /**
     * The games that reached deuce. With no-ad scoring, the table does not show this row, because it has the same
     * values as [DECIDING_POINTS_WON].
     */
    DEUCE_GAMES_WON("deuce_games_won", StatGroup.PRESSURE),
    /**
     * The golden points, the deciding points of no-ad tennis, or the star points. [StatRows.label] gives the name.
     * With advantage scoring, the table does not show this row.
     */
    DECIDING_POINTS_WON("deciding_points_won", StatGroup.PRESSURE),
    AVERAGE_POINT_WON("average_point_won", StatGroup.POINT_LENGTH),
    SHORT_POINTS_WON("short_points_won", StatGroup.POINT_LENGTH, inVideoByDefault = true),
    LONG_POINTS_WON("long_points_won", StatGroup.POINT_LENGTH, inVideoByDefault = true),
    DURATION("duration", StatGroup.TIME, inVideoByDefault = true),
    PLAYING_TIME("playing_time", StatGroup.TIME),
    AVERAGE_POINT("average_point", StatGroup.TIME),
    LONGEST_POINT("longest_point", StatGroup.TIME),
    ;

    /** The name of the statistic in the app language. */
    val label: String
        get() = when (this) {
            RATING_OVERALL -> tr("stats.row.ratingOverall")
            RATING_SERVE -> tr("stats.row.ratingServe")
            RATING_RETURN -> tr("stats.row.ratingReturn")
            RATING_ATTACK -> tr("stats.row.ratingAttack")
            RATING_CONSISTENCY -> tr("stats.row.ratingConsistency")
            RATING_PRESSURE -> tr("stats.row.ratingPressure")
            POINTS_WON -> tr("stats.row.pointsWon")
            GAMES_WON -> tr("stats.row.gamesWon")
            SETS_WON -> tr("stats.row.setsWon")
            TIEBREAKS_WON -> tr("stats.row.tiebreaksWon")
            ACES -> tr("stats.row.aces")
            UNRETURNED_SERVES -> tr("stats.row.unreturnedServes")
            DOUBLE_FAULTS -> tr("stats.row.doubleFaults")
            FIRST_SERVE_IN -> tr("stats.row.firstServeIn")
            FIRST_SERVE_POINTS_WON -> tr("stats.row.firstServePointsWon")
            SECOND_SERVE_POINTS_WON -> tr("stats.row.secondServePointsWon")
            SERVICE_POINTS_WON -> tr("stats.row.servicePointsWon")
            RETURN_POINTS_WON -> tr("stats.row.returnPointsWon")
            FIRST_SERVE_RETURN_POINTS_WON -> tr("stats.row.firstServeReturnPointsWon")
            SECOND_SERVE_RETURN_POINTS_WON -> tr("stats.row.secondServeReturnPointsWon")
            SERVICE_GAMES_WON -> tr("stats.row.serviceGamesWon")
            RETURN_GAMES_WON -> tr("stats.row.returnGamesWon")
            WINNERS -> tr("stats.row.winners")
            FORCED_ERRORS -> tr("stats.row.forcedErrors")
            UNFORCED_ERRORS -> tr("stats.row.unforcedErrors")
            WINNERS_TO_UNFORCED_ERRORS -> tr("stats.row.winnersToUnforcedErrors")
            ERRORS_NET -> tr("stats.row.errorsNet")
            ERRORS_LONG -> tr("stats.row.errorsLong")
            ERRORS_WIDE -> tr("stats.row.errorsWide")
            BREAK_POINTS_WON -> tr("stats.row.breakPointsWon")
            BREAK_POINTS_SAVED -> tr("stats.row.breakPointsSaved")
            SET_POINTS_WON -> tr("stats.row.setPointsWon")
            MATCH_POINTS_WON -> tr("stats.row.matchPointsWon")
            DEUCE_GAMES_WON -> tr("stats.row.deuceGamesWon")
            DECIDING_POINTS_WON -> tr("stats.row.decidingPointsWon")
            AVERAGE_POINT_WON -> tr("stats.row.averagePointWon")
            SHORT_POINTS_WON -> tr("stats.row.shortPointsWon")
            LONG_POINTS_WON -> tr("stats.row.longPointsWon")
            DURATION -> tr("stats.row.duration")
            PLAYING_TIME -> tr("stats.row.playingTime")
            AVERAGE_POINT -> tr("stats.row.averagePoint")
            LONGEST_POINT -> tr("stats.row.longestPoint")
        }

    companion object {
        /**
     * The old keys in stats.json and the keys that replace them. The app ignores the keys of removed rows, for example
     * "longest_point_run", "longest_game_run", and "largest_point_lead".
     */
        val REPLACED_KEYS: Map<String, String> = mapOf("deuce_points_won" to "deuce_games_won")

        fun ofKey(key: String): MatchStat? = entries.firstOrNull { it.key == key }

        val defaultVideoKeys: List<String> = entries.filter { it.inVideoByDefault }.map { it.key }
    }
}

/** What the [StatCoverage.count] of a row counts (B-68). */
enum class CoverageKind {
    /** The points with a known ending. See [DetailStats.detailedPoints]. */
    DETAILED_POINTS,

    /** The errors with an error kind. See [DetailStats.errorsWithKind]. */
    ERRORS_WITH_KIND,
}

/**
 * The part of the points that a row of advanced mode counts, when the row does not count all of them (B-68). For
 * example, a row of the endings counts only the points with a known ending. The UI makes the sentence.
 */
data class StatCoverage(val kind: CoverageKind, val count: Int)

/** A value in the table: the main [text], and an optional [detail], for example "64%" and "32/50". */
data class StatValue(val text: String, val detail: String? = null)

/**
 * One row of the statistics table.
 * A row has one value for each player ([values]) or one value for both players ([shared]).
 * [bar] holds the numbers that the video compares in a bar, or null when the row has no bar.
 * [barMax] is the largest possible bar number, for example 100 for a percentage. It is null when only the two numbers
 * give the scale, for example for a count.
 * When the row has no value, [unavailableReason] tells the user why.
 */
data class StatRow(
    val stat: MatchStat,
    val values: PerPlayer<StatValue>? = null,
    val shared: StatValue? = null,
    val unavailableReason: String? = null,
    val bar: PerPlayer<Double>? = null,
    val barMax: Double? = null,
    /** The point that the shared value comes from. */
    val sharedPointId: String? = null,
    /** The text of the row. Some rows add their settings to [MatchStat.label]. */
    val label: String = stat.label,
    /** The part of the points that the row counts, or null when the row counts all points. */
    val coverage: StatCoverage? = null,
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
    val NO_DETAILS: String get() = tr("stats.reason.noDetails")
    val NO_ERROR_KIND: String get() = tr("stats.reason.noErrorKind")
    val NO_TIEBREAKS: String get() = tr("stats.reason.noTiebreaks")

    /**
     * The rows of [stats]. The rows of advanced mode ([MatchStat.advanced]) are only in a result with details.
     * [sport] gives the name of the deciding points row.
     */
    fun build(
        stats: MatchStats,
        rules: MatchRulesV1,
        settings: StatsSettingsV1 = StatsSettingsV1(),
        sport: Sport = Sport.TENNIS,
    ): List<StatRow> {
        val normalized = settings.normalized()
        val ratings by lazy { PerformanceRatings.of(stats, rules) }
        return shownStats(stats, rules).map { stat ->
            if (stat.group == StatGroup.RATINGS) ratingRow(stat, ratings, stats.serve != null).copy(label = label(stat, normalized, rules, sport))
            else row(stat, stats, rules, normalized, sport)
        }
    }

    /**
     * The statistics that a table of [stats] shows. In basic mode, the rows of advanced mode are hidden. The deuce rule
     * shows one row for the 40–40 games: the deuce games with advantage scoring, the deciding points with no-ad
     * scoring. Star point scoring shows the two rows.
     */
    fun shownStats(stats: MatchStats, rules: MatchRulesV1): List<MatchStat> {
        val hidden = when (rules.deuce) {
            DeuceRule.ADVANTAGE -> MatchStat.DECIDING_POINTS_WON
            DeuceRule.NO_AD -> MatchStat.DEUCE_GAMES_WON
            DeuceRule.STAR_POINT -> null
        }
        return MatchStat.entries.filter { it != hidden && (stats.details != null || !it.advanced) }
    }

    /**
     * The text of a row with its setting, for example "Short points won (≤ 7 s)".
     * With [rules], the deciding points row gets the name of the deuce rule in [sport], for example "Golden points won".
     */
    fun label(
        stat: MatchStat,
        settings: StatsSettingsV1,
        rules: MatchRulesV1? = null,
        sport: Sport = Sport.TENNIS,
    ): String = when (stat) {
        MatchStat.SHORT_POINTS_WON -> tr("stats.row.shortPointsWon.limit", "seconds" to settings.normalized().shortPointMaxSeconds)
        MatchStat.LONG_POINTS_WON -> tr("stats.row.longPointsWon.limit", "seconds" to settings.normalized().longPointMinSeconds)
        MatchStat.DECIDING_POINTS_WON -> when (rules?.deuce) {
            DeuceRule.NO_AD -> if (sport == Sport.PADEL) tr("stats.row.goldenPointsWon") else stat.label
            DeuceRule.STAR_POINT -> tr("stats.row.starPointsWon")
            DeuceRule.ADVANTAGE, null -> stat.label
        }
        else -> stat.label
    }

    private fun row(stat: MatchStat, stats: MatchStats, rules: MatchRulesV1, settings: StatsSettingsV1, sport: Sport): StatRow {
        val structure = rules.structure
        val hasGames = structure == MatchStructure.SETS || structure == MatchStructure.GAMES_ONLY
        val hasSets = structure == MatchStructure.SETS
        val hasEnd = structure == MatchStructure.SETS || structure == MatchStructure.SINGLE_TIEBREAK
        // A total points match has an end, but no sets.
        val hasMatchEnd = hasEnd || structure == MatchStructure.TOTAL_POINTS
        val manual = rules.manualScoring
        val serve = stats.serve
        val time = stats.time
        val details = stats.details

        fun unavailable(reason: String) = StatRow(stat, unavailableReason = reason)
        fun perPlayer(values: PerPlayer<StatValue>, bar: PerPlayer<Double>? = null) = StatRow(stat, values = values, bar = bar)
        fun counts(values: PerPlayer<Int>) = perPlayer(values.map(::count), values.map { it.toDouble() })
        fun shares(values: PerPlayer<Ratio>) =
            perPlayer(values.map(::share), values.map { (it.percent ?: 0).toDouble() }).copy(barMax = PERCENT_MAX)
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
            MatchStat.TIEBREAKS_WON -> when {
                !hasEnd -> unavailable(NO_SETS)
                manual -> unavailable(MANUAL_SCORING)
                stats.tiebreaksWon.p1.total == 0 -> unavailable(NO_TIEBREAKS)
                else -> fractions(stats.tiebreaksWon)
            }
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
            MatchStat.DEUCE_GAMES_WON -> when {
                !hasGames -> unavailable(NO_GAMES)
                manual -> unavailable(MANUAL_SCORING)
                else -> fractions(stats.deuceGamesWon)
            }
            MatchStat.DECIDING_POINTS_WON -> when {
                !hasGames -> unavailable(NO_GAMES)
                manual -> unavailable(MANUAL_SCORING)
                else -> fractions(stats.decidingPointsWon)
            }
            MatchStat.DURATION -> shared(time.durationMs.takeIf { stats.scoredPoints > 0 }?.let { StatValue(clock(it)) })
            MatchStat.PLAYING_TIME -> shared(time.playingMs.takeIf { stats.points > 0 }?.let { StatValue(clock(it)) })
            MatchStat.AVERAGE_POINT -> shared(time.averagePointMs?.let { StatValue(seconds(it)) })
            MatchStat.LONGEST_POINT -> shared(time.longestPoint?.let { StatValue(seconds((it.endMs - it.startMs).toLong())) })
                .copy(sharedPointId = time.longestPoint?.id)
            MatchStat.AVERAGE_POINT_WON -> perPlayer(time.averagePointMsWon.map { ms -> StatValue(ms?.let(::seconds) ?: "—") })
            MatchStat.ACES, MatchStat.UNRETURNED_SERVES, MatchStat.DOUBLE_FAULTS, MatchStat.FIRST_SERVE_IN,
            MatchStat.FIRST_SERVE_POINTS_WON, MatchStat.SECOND_SERVE_POINTS_WON, MatchStat.FIRST_SERVE_RETURN_POINTS_WON,
            MatchStat.SECOND_SERVE_RETURN_POINTS_WON, MatchStat.WINNERS, MatchStat.FORCED_ERRORS, MatchStat.UNFORCED_ERRORS,
            MatchStat.WINNERS_TO_UNFORCED_ERRORS, MatchStat.ERRORS_NET, MatchStat.ERRORS_LONG, MatchStat.ERRORS_WIDE ->
                if (details == null || details.detailedPoints == 0) unavailable(NO_DETAILS)
                else detailRow(stat, details, stats.scoredPoints, serve != null)
            MatchStat.RATING_OVERALL, MatchStat.RATING_SERVE, MatchStat.RATING_RETURN, MatchStat.RATING_ATTACK,
            MatchStat.RATING_CONSISTENCY, MatchStat.RATING_PRESSURE -> error("build() makes the rating rows: $stat")
            MatchStat.SHORT_POINTS_WON ->
                lengthShares(time.scoredDurations.filter { it.durationMs <= settings.shortPointMaxSeconds * 1000L })
            MatchStat.LONG_POINTS_WON ->
                lengthShares(time.scoredDurations.filter { it.durationMs >= settings.longPointMinSeconds * 1000L })
        }.copy(label = label(stat, settings, rules, sport))
    }

    /**
     * A row of the performance ratings (B-77): a value from 0 to 10 with one decimal for each player. Without a server
     * mark, the Serve, Return, and Overall rows ask for the server, because these ratings need it.
     */
    private fun ratingRow(stat: MatchStat, ratings: PerformanceRatings, hasServer: Boolean): StatRow {
        val values = when (stat) {
            MatchStat.RATING_OVERALL -> ratings.overall
            MatchStat.RATING_SERVE -> ratings.areas[RatingArea.SERVE]
            MatchStat.RATING_RETURN -> ratings.areas[RatingArea.RETURN]
            MatchStat.RATING_ATTACK -> ratings.areas[RatingArea.ATTACK]
            MatchStat.RATING_CONSISTENCY -> ratings.areas[RatingArea.CONSISTENCY]
            MatchStat.RATING_PRESSURE -> ratings.areas[RatingArea.PRESSURE]
            else -> error("Not a rating: $stat")
        } ?: return StatRow(stat, unavailableReason = if (!hasServer && stat in NEEDS_SERVER_RATINGS) NEEDS_SERVER else NO_VALUE)
        val rounded = values.map { Math.round(it * 10) / 10.0 }
        return StatRow(
            stat,
            values = rounded.map { StatValue(DisplayFormats.decimal(it, 1)) },
            bar = rounded,
            barMax = PerformanceRatings.MAX,
        )
    }

    /**
     * A row of advanced mode (B-68). The serve rows need the server. The rows of the error kinds need an error with a
     * kind. When the row does not count all points with a winner, it has a [StatCoverage].
     */
    private fun detailRow(stat: MatchStat, details: DetailStats, scoredPoints: Int, hasServer: Boolean): StatRow {
        fun counts(values: PerPlayer<Int>) = StatRow(stat, values = values.map(::count), bar = values.map { it.toDouble() })
        fun shares(values: PerPlayer<Ratio>): StatRow = if (!hasServer) {
            StatRow(stat, unavailableReason = NEEDS_SERVER)
        } else {
            StatRow(stat, values = values.map(::share), bar = values.map { (it.percent ?: 0).toDouble() }, barMax = PERCENT_MAX)
        }
        fun errorKind(kind: ErrorKind): StatRow = if (details.errorsWithKind == 0) {
            StatRow(stat, unavailableReason = NO_ERROR_KIND)
        } else {
            counts(details.errorKinds.getValue(kind))
        }
        val row = when (stat) {
            MatchStat.ACES -> counts(details.aces)
            MatchStat.UNRETURNED_SERVES -> counts(details.unreturnedServes)
            MatchStat.DOUBLE_FAULTS -> counts(details.doubleFaults)
            MatchStat.FIRST_SERVE_IN -> shares(details.firstServeIn)
            MatchStat.FIRST_SERVE_POINTS_WON -> shares(details.firstServePointsWon)
            MatchStat.SECOND_SERVE_POINTS_WON -> shares(details.secondServePointsWon)
            MatchStat.FIRST_SERVE_RETURN_POINTS_WON -> shares(details.firstServeReturnPointsWon)
            MatchStat.SECOND_SERVE_RETURN_POINTS_WON -> shares(details.secondServeReturnPointsWon)
            MatchStat.WINNERS -> counts(details.winners)
            MatchStat.FORCED_ERRORS -> counts(details.forcedErrors)
            MatchStat.UNFORCED_ERRORS -> counts(details.unforcedErrors)
            MatchStat.WINNERS_TO_UNFORCED_ERRORS -> StatRow(
                stat,
                values = PerPlayer(1, 2).map { player ->
                    val winners = details.winners[player]
                    val errors = details.unforcedErrors[player]
                    val ratio = if (errors == 0) "—" else DisplayFormats.decimal(winners.toDouble() / errors, 2)
                    StatValue(ratio, "$winners/$errors")
                },
            )
            MatchStat.ERRORS_NET -> errorKind(ErrorKind.NET)
            MatchStat.ERRORS_LONG -> errorKind(ErrorKind.LONG)
            MatchStat.ERRORS_WIDE -> errorKind(ErrorKind.WIDE)
            else -> error("Not a row of advanced mode: $stat")
        }
        if (!row.available) return row
        val coverage = when (stat) {
            MatchStat.ERRORS_NET, MatchStat.ERRORS_LONG, MatchStat.ERRORS_WIDE ->
                StatCoverage(CoverageKind.ERRORS_WITH_KIND, details.errorsWithKind).takeIf { it.count < details.errors }
            else -> StatCoverage(CoverageKind.DETAILED_POINTS, details.detailedPoints).takeIf { it.count < scoredPoints }
        }
        return row.copy(coverage = coverage)
    }

    private fun <T, R> PerPlayer<T>.map(transform: (T) -> R): PerPlayer<R> = PerPlayer(transform(p1), transform(p2))

    private const val PERCENT_MAX = 100.0

    private val NEEDS_SERVER_RATINGS = setOf(MatchStat.RATING_OVERALL, MatchStat.RATING_SERVE, MatchStat.RATING_RETURN)

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
