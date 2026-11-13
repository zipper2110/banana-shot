package org.litvin.scoring

import org.litvin.points.PointV1
import org.litvin.scoring.ScoringEngine.Stake
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One value for each player. */
data class PerPlayer<T>(val p1: T, val p2: T) {
    operator fun get(player: Int): T = if (player == 1) p1 else p2
}

/** [won] of [total], for example 3 break points won of 7. */
data class Ratio(val won: Int, val total: Int) {
    /** The rounded percentage, or null when [total] is 0. */
    val percent: Int? get() = if (total == 0) null else (won * 100.0 / total).roundToInt()
}

/** Statistics that need the server of each point. */
data class ServeStats(
    /** The points that the player won on the player's own serve, of the points that the player served. */
    val servicePointsWon: PerPlayer<Ratio>,
    /** The points that the player won on the opponent's serve, of the points that the player received. */
    val returnPointsWon: PerPlayer<Ratio>,
    /** The service games that the player won (held), of the service games that the player completed. Tiebreaks are not service games. */
    val serviceGamesWon: PerPlayer<Ratio>,
    /** The break points that the player won, of the break points that the player had. */
    val breakPointsWon: PerPlayer<Ratio>,
) {
    /** The break points that the player saved, of the break points that the player faced. */
    val breakPointsSaved: PerPlayer<Ratio>
        get() = PerPlayer(lostBy(breakPointsWon.p2), lostBy(breakPointsWon.p1))

    /** The service games of the opponent that the player won (broke), of the service games that the opponent completed. */
    val returnGamesWon: PerPlayer<Ratio>
        get() = PerPlayer(lostBy(serviceGamesWon.p2), lostBy(serviceGamesWon.p1))

    /** The chances of the opponent that the opponent did not win. */
    private fun lostBy(opponent: Ratio) = Ratio(opponent.total - opponent.won, opponent.total)
}

/**
 * Statistics from the start and end times of the points.
 *
 * The point lengths (the average point, the average point won, the longest point, and the short and long points) use
 * only the point clips and the W clips. They skip the fault clips, the double faults, and the let clips (B-68). The
 * playing time includes all clips.
 */
data class TimeStats(
    /**
     * The match duration: from the start of the first point with a winner to the end of the last point with a winner.
     * It is not the length of the video. It is 0 when no point has a winner.
     */
    val durationMs: Long,
    /** The sum of the point durations. */
    val playingMs: Long,
    /** The average point duration, or null when there are no points. */
    val averagePointMs: Long?,
    /** The point with the longest duration, or null when there are no points. */
    val longestPoint: PointV1?,
    /** The average duration of the points that each player won, or null when the player won no points. */
    val averagePointMsWon: PerPlayer<Long?>,
    /** The duration and the winner of each point that has a winner. */
    val scoredDurations: List<ScoredDuration> = emptyList(),
)

/**
 * Statistics from the point details (B-68). Only a project in advanced mode has them.
 *
 * A pack counts in the range when its last clip (the point clip or the double fault) is in the range. A pack at the end
 * of the list with no point clip yet does not count. Each player is the player who made the ending (see
 * [PointDetails.playerOfEnding]): the double faults are the faults of the server.
 */
data class DetailStats(
    /**
     * The points with a known ending: an ace, a winner, an error, or a double fault. A point with a winner and no ending
     * (from basic mode, or from Q and E) is not a detailed point.
     */
    val detailedPoints: Int,
    val aces: PerPlayer<Int>,
    val doubleFaults: PerPlayer<Int>,
    /** The packs where the 1st serve was in, of the packs that the player served. Only packs with a known server count. */
    val firstServeIn: PerPlayer<Ratio>,
    /** The points that the server won on the 1st serve, of the points that the player played on the 1st serve. */
    val firstServePointsWon: PerPlayer<Ratio>,
    /** The points that the server won on the 2nd serve, of the points that the player played on the 2nd serve. */
    val secondServePointsWon: PerPlayer<Ratio>,
    val winners: PerPlayer<Int>,
    val forcedErrors: PerPlayer<Int>,
    val unforcedErrors: PerPlayer<Int>,
    /** The forced and unforced errors. */
    val errors: Int,
    /** The errors of each player with each error kind. A kind with no error has the value 0 for both players. */
    val errorKinds: Map<ErrorKind, PerPlayer<Int>>,
) {
    /** The errors that have an error kind. */
    val errorsWithKind: Int get() = errorKinds.values.sumOf { it.p1 + it.p2 }
}

/** The duration of a point that has a winner. [winner] is 1 or 2. */
data class ScoredDuration(val durationMs: Long, val winner: Int)

/** The points that some statistics come from. The Stats tab opens them in the Points tab. */
data class KeyPoints(
    /** The first point of the longest point run of each player. Null when the player won no point. */
    val longestPointRunStart: PerPlayer<String?> = PerPlayer(null, null),
    /** The point where the player first has the largest lead. Null when the player never leads. */
    val largestPointLeadAt: PerPlayer<String?> = PerPlayer(null, null),
)

/**
 * Match statistics for a range of points: the full match or one set.
 *
 * Only the points that have a winner count. An unscored point or a point with no outcome does not stop a run of points.
 */
data class MatchStats(
    /** The points in the range. */
    val points: Int,
    /** The points in the range that have a winner. */
    val scoredPoints: Int,
    val pointsWon: PerPlayer<Int>,
    /**
     * The games that the player won in the range. They come from the score, not from the points: the statistics are
     * for the match, so a game that the user sets with a score override counts, also when no marked point wins it.
     */
    val gamesWon: PerPlayer<Int>,
    /** The sets that the player won in the range. They come from the score, as [gamesWon]. */
    val setsWon: PerPlayer<Int>,
    /** Null when the user did not mark a server. */
    val serve: ServeStats?,
    /** The points that win a set (match points too) that the player won, of the set points that the player had. */
    val setPointsWon: PerPlayer<Ratio>,
    /** The match points that the player won, of the match points that the player had. */
    val matchPointsWon: PerPlayer<Ratio>,
    /** The points played at deuce (40–40 or later) that the player won. Tiebreaks and manual scoring have no deuce points. */
    val deucePointsWon: PerPlayer<Int>,
    /**
     * The deciding points (golden points or star points, see [ScoringEngine.isDecidingPoint]) that the player won,
     * of the deciding points in the range. Both players have the same total.
     */
    val decidingPointsWon: PerPlayer<Ratio> = PerPlayer(Ratio(0, 0), Ratio(0, 0)),
    /** The most points in a row that the player won. */
    val longestPointRun: PerPlayer<Int>,
    /** The most games in a row that the player won. */
    val longestGameRun: PerPlayer<Int>,
    /** The largest lead in points won that the player had. */
    val largestPointLead: PerPlayer<Int>,
    val time: TimeStats,
    val keyPoints: KeyPoints = KeyPoints(),
    /** The statistics from the point details, or null for a project in basic mode (B-68). */
    val details: DetailStats? = null,
) {
    companion object {
        /**
         * Calculates the statistics for the points in [range].
         * [timeline] must come from [ScoringEngine.timeline] with the same [points] and [outcomes].
         *
         * [details] are the point details of the project (B-68). The time statistics use them in all modes to find the
         * fault clips and the let clips. [advanced] is true for a project in advanced mode: only then the result has
         * [MatchStats.details].
         */
        fun compute(
            points: List<PointV1>,
            outcomes: Map<String, Outcome>,
            timeline: ScoringEngine.Timeline,
            rules: MatchRulesV1,
            range: IntRange = points.indices,
            details: Map<String, PointDetailV1> = emptyMap(),
            advanced: Boolean = false,
        ): MatchStats {
            val indices = range.filter { it in points.indices }
            val winners = points.map { winner(outcomes[it.id]) }
            val scored = indices.filter { winners[it] != null }
            val packs = ServePacks.of(points, outcomes, details)
            val kinds = points.map { packs.of(it.id)?.kind }

            return MatchStats(
                points = indices.size,
                scoredPoints = scored.size,
                pointsWon = count(scored) { winners[it] },
                gamesWon = scoreChange(indices, timeline, ::gamesInScore),
                setsWon = scoreChange(indices, timeline) { state, _ -> PerPlayer(state.setsP1, state.setsP2) },
                serve = serveStats(indices, scored, winners, timeline),
                setPointsWon = chances(scored, winners, timeline, Stake.SET),
                matchPointsWon = chances(scored, winners, timeline, Stake.MATCH),
                deucePointsWon = count(scored.filter { isDeucePoint(timeline.stateBefore(it), rules) }) { winners[it] },
                decidingPointsWon = scored.filter { ScoringEngine.isDecidingPoint(timeline.stateBefore(it), rules) }
                    .let { deciding ->
                        fun wonBy(player: Int) = Ratio(deciding.count { winners[it] == player }, deciding.size)
                        PerPlayer(wonBy(1), wonBy(2))
                    },
                longestPointRun = longestRun(scored.map { winners[it]!! }),
                longestGameRun = longestRun(gameWinners(indices, timeline)),
                largestPointLead = largestLead(scored.map { winners[it]!! }),
                time = timeStats(indices.map { points[it] }, indices.map { winners[it] }, indices.map { kinds[it] }),
                keyPoints = scored.map { winners[it]!! }.let { players ->
                    fun idOf(position: Int?) = position?.let { points[scored[it]].id }
                    val runs = longestRunStarts(players)
                    val leads = largestLeadPoints(players)
                    KeyPoints(
                        longestPointRunStart = PerPlayer(idOf(runs.p1), idOf(runs.p2)),
                        largestPointLeadAt = PerPlayer(idOf(leads.p1), idOf(leads.p2)),
                    )
                },
                details = if (advanced) detailStats(points, outcomes, details, packs, indices, timeline) else null,
            )
        }

        /**
         * The index of the point that wins the match, or null when nobody has won the match yet.
         * The engine ignores the points after this point. Formats without an end and manual scoring return null.
         */
        fun matchEndIndex(timeline: ScoringEngine.Timeline, rules: MatchRulesV1): Int? {
            if (rules.manualScoring) return null
            val normalized = rules.normalized()
            val setsToWin = when (rules.structure) {
                MatchStructure.SETS -> normalized.setsToWin()
                MatchStructure.SINGLE_TIEBREAK -> 1
                MatchStructure.TOTAL_POINTS -> return timeline.statesAfterPoint
                    .indexOfFirst { it.p1Pts + it.p2Pts >= normalized.totalPoints }
                    .takeIf { it >= 0 }
                MatchStructure.GAMES_ONLY, MatchStructure.PLAIN_POINTS -> return null
            }
            return timeline.statesAfterPoint.indexOfFirst { maxOf(it.setsP1, it.setsP2) >= setsToWin }.takeIf { it >= 0 }
        }

        /**
         * The point ranges of the sets in [points]. A set ends at the point that wins it.
         * The points after the last completed set make one more range. Without completed sets, all points are one range.
         */
        fun setRanges(
            timeline: ScoringEngine.Timeline,
            points: IntRange = timeline.statesAfterPoint.indices,
        ): List<IntRange> {
            val ranges = mutableListOf<IntRange>()
            var start = points.first
            for (index in points) {
                if (timeline.statesAfterPoint[index].lastSetWonBy != null) {
                    ranges += start..index
                    start = index + 1
                }
            }
            if (start <= points.last) ranges += start..points.last
            return ranges
        }

        private fun winner(outcome: Outcome?): Int? = when (outcome) {
            Outcome.P1 -> 1
            Outcome.P2 -> 2
            Outcome.NONE, null -> null
        }

        /**
         * The games of each player in the score: the games of the completed sets and of the current set.
         * A match tiebreak (a 1–0 tiebreak set) is not a game.
         */
        private fun gamesInScore(state: ScoringEngine.MatchState, sets: List<ScoringEngine.SetScore>): PerPlayer<Int> {
            val gameSets = sets.filterNot { it.tiebreak && it.p1 + it.p2 == 1 }
            return PerPlayer(gameSets.sumOf { it.p1 } + state.gamesP1, gameSets.sumOf { it.p2 } + state.gamesP2)
        }

        private fun setsBefore(timeline: ScoringEngine.Timeline, index: Int): List<ScoringEngine.SetScore> =
            if (index == 0) emptyList() else timeline.setsAfterPoint[index - 1]

        /**
         * How much [value] grows from the score before the first point of [indices] to the score after the last point.
         * The score includes the score overrides, so a game that the user sets in the score counts,
         * also when no marked point wins it.
         */
        private fun scoreChange(
            indices: List<Int>,
            timeline: ScoringEngine.Timeline,
            value: (ScoringEngine.MatchState, List<ScoringEngine.SetScore>) -> PerPlayer<Int>,
        ): PerPlayer<Int> {
            if (indices.isEmpty()) return PerPlayer(0, 0)
            val before = value(timeline.stateBefore(indices.first()), setsBefore(timeline, indices.first()))
            val after = value(timeline.statesAfterPoint[indices.last()], timeline.setsAfterPoint[indices.last()])
            return PerPlayer((after.p1 - before.p1).coerceAtLeast(0), (after.p2 - before.p2).coerceAtLeast(0))
        }

        /** The winner of each game in order. A score override that adds games adds them for the player that gets them. */
        private fun gameWinners(indices: List<Int>, timeline: ScoringEngine.Timeline): List<Int> = indices.flatMap { i ->
            val before = gamesInScore(timeline.stateBefore(i), setsBefore(timeline, i))
            val after = gamesInScore(timeline.statesAfterPoint[i], timeline.setsAfterPoint[i])
            List((after.p1 - before.p1).coerceAtLeast(0)) { 1 } + List((after.p2 - before.p2).coerceAtLeast(0)) { 2 }
        }

        private fun count(indices: List<Int>, playerOf: (Int) -> Int?): PerPlayer<Int> {
            val players = indices.mapNotNull(playerOf)
            return PerPlayer(players.count { it == 1 }, players.count { it == 2 })
        }

        private fun serveStats(
            indices: List<Int>,
            scored: List<Int>,
            winners: List<Int?>,
            timeline: ScoringEngine.Timeline,
        ): ServeStats? {
            if (indices.none { timeline.serverOfPoint[it] != null }) return null
            val served = IntArray(3)
            val servedWon = IntArray(3)
            val breakChances = IntArray(3)
            val breaksWon = IntArray(3)
            for (i in scored) {
                val server = timeline.serverOfPoint[i] ?: continue
                val receiver = 3 - server
                val winner = winners[i]!!
                served[server]++
                if (winner == server) servedWon[server]++
                val isBreakPoint = !timeline.stateBefore(i).isTiebreak && timeline.stakesOfPoint[i].of(receiver) >= Stake.GAME
                if (isBreakPoint) {
                    breakChances[receiver]++
                    if (winner == receiver) breaksWon[receiver]++
                }
            }
            val serviceGames = IntArray(3)
            val holds = IntArray(3)
            for (i in indices) {
                val gameWinner = timeline.statesAfterPoint[i].lastGameWonBy ?: continue
                val server = timeline.serverOfPoint[i] ?: continue
                if (timeline.stateBefore(i).isTiebreak) continue
                serviceGames[server]++
                if (gameWinner == server) holds[server]++
            }
            return ServeStats(
                servicePointsWon = PerPlayer(Ratio(servedWon[1], served[1]), Ratio(servedWon[2], served[2])),
                returnPointsWon = PerPlayer(
                    Ratio(served[2] - servedWon[2], served[2]),
                    Ratio(served[1] - servedWon[1], served[1]),
                ),
                serviceGamesWon = PerPlayer(Ratio(holds[1], serviceGames[1]), Ratio(holds[2], serviceGames[2])),
                breakPointsWon = PerPlayer(Ratio(breaksWon[1], breakChances[1]), Ratio(breaksWon[2], breakChances[2])),
            )
        }

        /** The points where a player could win at least [stake], and how many of them the player won. */
        private fun chances(
            scored: List<Int>,
            winners: List<Int?>,
            timeline: ScoringEngine.Timeline,
            stake: Stake,
        ): PerPlayer<Ratio> {
            fun forPlayer(player: Int): Ratio {
                val chances = scored.filter { timeline.stakesOfPoint[it].of(player) >= stake }
                return Ratio(chances.count { winners[it] == player }, chances.size)
            }
            return PerPlayer(forPlayer(1), forPlayer(2))
        }

        private fun isDeucePoint(before: ScoringEngine.MatchState, rules: MatchRulesV1): Boolean =
            !rules.manualScoring && !before.isTiebreak && before.p1Pts == before.p2Pts && before.p1Pts >= 3

        private fun longestRun(players: List<Int>): PerPlayer<Int> {
            val best = IntArray(3)
            var current = 0
            players.forEachIndexed { index, player ->
                current = if (index > 0 && players[index - 1] == player) current + 1 else 1
                if (current > best[player]) best[player] = current
            }
            return PerPlayer(best[1], best[2])
        }

        /** The position of the first point of the first longest run of each player. */
        private fun longestRunStarts(players: List<Int>): PerPlayer<Int?> {
            val best = IntArray(3)
            val bestStart = arrayOfNulls<Int>(3)
            var current = 0
            players.forEachIndexed { index, player ->
                current = if (index > 0 && players[index - 1] == player) current + 1 else 1
                if (current > best[player]) {
                    best[player] = current
                    bestStart[player] = index - current + 1
                }
            }
            return PerPlayer(bestStart[1], bestStart[2])
        }

        /** The position of the point where each player first has the largest lead. */
        private fun largestLeadPoints(players: List<Int>): PerPlayer<Int?> {
            var difference = 0
            var p1Lead = 0
            var p2Lead = 0
            var p1At: Int? = null
            var p2At: Int? = null
            players.forEachIndexed { index, player ->
                difference += if (player == 1) 1 else -1
                if (difference > p1Lead) {
                    p1Lead = difference
                    p1At = index
                }
                if (-difference > p2Lead) {
                    p2Lead = -difference
                    p2At = index
                }
            }
            return PerPlayer(p1At, p2At)
        }

        private fun largestLead(players: List<Int>): PerPlayer<Int> {
            var difference = 0
            var p1Lead = 0
            var p2Lead = 0
            for (player in players) {
                difference += if (player == 1) 1 else -1
                p1Lead = maxOf(p1Lead, difference)
                p2Lead = maxOf(p2Lead, -difference)
            }
            return PerPlayer(p1Lead, p2Lead)
        }

        /**
         * The time statistics of [points]. [kinds] gives the kind of each clip in the packs. A fault clip, a double
         * fault, or a let clip is not a point for the point lengths, but it is playing time. Without details, all clips
         * are point clips or W clips, as before B-68.
         */
        private fun timeStats(points: List<PointV1>, winners: List<Int?>, kinds: List<ClipKind?>): TimeStats {
            val durations = points.map { (it.endMs - it.startMs).coerceAtLeast(0).toLong() }
            val isPoint = kinds.map { it != ClipKind.FAULT && it != ClipKind.DOUBLE_FAULT && it != ClipKind.LET }
            val pointIndices = points.indices.filter { isPoint[it] }
            // The match starts with the first clip of the first pack, also when it is a fault clip.
            val inPack = points.indices.filter { winners[it] != null || !isPoint[it] }
            val first = inPack.firstOrNull()?.let(points::get)
            val last = inPack.lastOrNull()?.let(points::get)
            fun averageWonBy(player: Int): Long? = pointIndices.filter { winners[it] == player }.map { durations[it] }.averageOrNull()
            return TimeStats(
                durationMs = if (first == null || last == null) 0 else (last.endMs - first.startMs).coerceAtLeast(0).toLong(),
                playingMs = durations.sum(),
                averagePointMs = pointIndices.map { durations[it] }.averageOrNull(),
                longestPoint = pointIndices.maxByOrNull { durations[it] }?.let(points::get),
                averagePointMsWon = PerPlayer(averageWonBy(1), averageWonBy(2)),
                scoredDurations = pointIndices.mapNotNull { index ->
                    winners[index]?.let { ScoredDuration(durations[index], it) }
                },
            )
        }

        /** The statistics from the point details of the packs that end in [indices]. See [DetailStats]. */
        private fun detailStats(
            points: List<PointV1>,
            outcomes: Map<String, Outcome>,
            details: Map<String, PointDetailV1>,
            packs: ServePacks,
            indices: List<Int>,
            timeline: ScoringEngine.Timeline,
        ): DetailStats {
            val indexOf = points.withIndex().associate { (index, point) -> point.id to index }
            val inRange = indices.toSet()
            var detailed = 0
            var errors = 0
            val aces = IntArray(3)
            val doubleFaults = IntArray(3)
            val winners = IntArray(3)
            val forced = IntArray(3)
            val unforced = IntArray(3)
            val kinds = ErrorKind.entries.associateWith { IntArray(3) }
            val served = IntArray(3)
            val firstIn = IntArray(3)
            val firstPlayed = IntArray(3)
            val firstWon = IntArray(3)
            val secondPlayed = IntArray(3)
            val secondWon = IntArray(3)
            for (pack in packs.packs) {
                if (!pack.complete) continue
                val id = pack.clipIds.last()
                val index = indexOf[id]?.takeIf { it in inRange } ?: continue
                val clip = packs.of(id) ?: continue
                val outcome = outcomes[id] ?: Outcome.NONE
                val winner = winner(outcome)
                val server = timeline.serverOfPoint.getOrNull(index)
                val detail = details[id]
                if (clip.kind == ClipKind.DOUBLE_FAULT) {
                    detailed++
                    (server ?: winner?.let { 3 - it })?.let { doubleFaults[it]++ }
                } else if (detail?.ending?.endsRally == true) {
                    detailed++
                    val player = PointDetails.playerOfEnding(detail.ending, outcome)
                    if (player != null) {
                        when (detail.ending) {
                            Ending.ACE -> aces[player]++
                            Ending.WINNER -> winners[player]++
                            Ending.FORCED_ERROR -> forced[player]++
                            Ending.UNFORCED_ERROR -> unforced[player]++
                            else -> Unit
                        }
                        if (detail.ending.isError) {
                            errors++
                            detail.errorKind?.let { kinds.getValue(it)[player]++ }
                        }
                    }
                }
                if (server == null || winner == null) continue
                served[server]++
                if (clip.serve == 1) {
                    firstIn[server]++
                    firstPlayed[server]++
                    if (winner == server) firstWon[server]++
                } else {
                    secondPlayed[server]++
                    if (winner == server) secondWon[server]++
                }
            }
            fun perPlayer(values: IntArray) = PerPlayer(values[1], values[2])
            fun ratios(won: IntArray, total: IntArray) = PerPlayer(Ratio(won[1], total[1]), Ratio(won[2], total[2]))
            return DetailStats(
                detailedPoints = detailed,
                aces = perPlayer(aces),
                doubleFaults = perPlayer(doubleFaults),
                firstServeIn = ratios(firstIn, served),
                firstServePointsWon = ratios(firstWon, firstPlayed),
                secondServePointsWon = ratios(secondWon, secondPlayed),
                winners = perPlayer(winners),
                forcedErrors = perPlayer(forced),
                unforcedErrors = perPlayer(unforced),
                errors = errors,
                errorKinds = kinds.mapValues { perPlayer(it.value) },
            )
        }

        private fun List<Long>.averageOrNull(): Long? = if (isEmpty()) null else average().roundToLong()
    }
}
