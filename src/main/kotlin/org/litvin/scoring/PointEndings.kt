package org.litvin.scoring

import org.litvin.points.PointV1

/**
 * One ending button of advanced mode (B-68, "Ending buttons"). The [winner] (1 or 2) gets the point. The group of a
 * button is its winner, not the player who hit the last shot. Thus the left group gives the point to player 1, and the
 * right group gives it to player 2.
 */
enum class EndingChoice(val winner: Int, val ending: Ending) {
    P1_WINNER(1, Ending.WINNER),
    P2_FORCED_ERROR(1, Ending.FORCED_ERROR),
    P2_UNFORCED_ERROR(1, Ending.UNFORCED_ERROR),
    P1_ACE(1, Ending.ACE),
    P2_WINNER(2, Ending.WINNER),
    P1_FORCED_ERROR(2, Ending.FORCED_ERROR),
    P1_UNFORCED_ERROR(2, Ending.UNFORCED_ERROR),
    P2_ACE(2, Ending.ACE),
    ;

    /** The outcome that the button gives. */
    val outcome: Outcome get() = if (winner == 1) Outcome.P1 else Outcome.P2

    /** The player (1 or 2) who made the ending: the winner for an ace and a winner, the other player for an error. */
    val player: Int get() = if (ending.isError) 3 - winner else winner

    companion object {
        /** The button of a point clip that [winner] won with [ending], or null for no ending or no winner. */
        fun of(winner: Outcome, ending: Ending?): EndingChoice? {
            if (ending == null || !ending.endsRally) return null
            return entries.firstOrNull { it.outcome == winner && it.ending == ending }
        }
    }
}

/** The score edits of the scoring keys (B-68, "Ending buttons" and "Implementation"). */
object PointEndings {
    /**
     * True when the user can use [choice] on a point that [server] serves. Only the group of the server has the ace
     * button. When the server is not known, no group has it.
     */
    fun isAvailable(choice: EndingChoice, server: Int?): Boolean = choice.ending != Ending.ACE || server == choice.winner

    /**
     * [score] after an ending key on the clip with [pointId]: the new winner and the new ending. On a fault clip or a
     * let clip, the ending makes a point clip. The error kind stays only when the ending stays an error of the same
     * player, for example a forced error that becomes an unforced error. A different player or a new kind of ending
     * can make the old error kind wrong.
     */
    fun withEnding(score: ScoreV1, pointId: String, choice: EndingChoice): ScoreV1 {
        val old = score.detailOf(pointId)
        val keepKind = old.ending?.isError == true && choice.ending.isError &&
            PointDetails.playerOfEnding(old.ending, score.outcomeOf(pointId)) == choice.player
        val detail = PointDetailV1(ending = choice.ending, errorKind = old.errorKind.takeIf { keepKind })
        return score.copy(outcomes = score.outcomes + (pointId to choice.outcome)).withDetail(pointId, detail)
    }

    /**
     * [score] after Q, W, or E on the clip with [pointId]:
     * - In advanced mode, the key always removes the ending and the error kind. Q and E give the winner only, and W
     *   gives no point.
     * - In basic mode, the key removes the ending and the error kind only when it changes the winner. A key that gives
     *   the same winner keeps them, because basic mode does not show them.
     */
    fun withWinnerOnly(score: ScoreV1, pointId: String, outcome: Outcome): ScoreV1 {
        val changesWinner = score.outcomeOf(pointId) != outcome
        val updated = score.copy(outcomes = score.outcomes + (pointId to outcome))
        return if (score.isAdvanced || changesWinner) updated.withDetail(pointId, PointDetailV1()) else updated
    }

    /**
     * [score] after X or L on the clip with [pointId] (B-68, "Serve faults and lets"). [ending] is [Ending.SERVE_FAULT]
     * or [Ending.LET]. The clip gets no winner and no error kind. [PointDetails.resolve] then gives the receiver the
     * point when the fault is the second fault of its pack (the double fault).
     */
    fun withServeEnding(score: ScoreV1, pointId: String, ending: Ending): ScoreV1 {
        require(ending == Ending.SERVE_FAULT || ending == Ending.LET) { "Not a serve ending: $ending" }
        return score.copy(outcomes = score.outcomes + (pointId to Outcome.NONE)).withDetail(pointId, PointDetailV1(ending))
    }

    /**
     * True when [point] is a clip of zero length (B-68). Only a fault clip can have zero length: X with no open point
     * and no active clip adds it at the playhead. It has no video, so the exports skip it.
     */
    fun isZeroLength(point: PointV1): Boolean = point.endMs <= point.startMs

    /**
     * True when the scoring keys can change [point]: a clip with a length, or a fault clip of zero length. On a fault
     * clip of zero length, only X and the server change work (see [allowsZeroLength]).
     */
    fun canScore(point: PointV1, score: ScoreV1): Boolean =
        !isZeroLength(point) || score.detailOf(point.id).ending == Ending.SERVE_FAULT

    /** True when a clip of zero length can have [ending]. Only a serve fault can. */
    fun allowsZeroLength(ending: Ending?): Boolean = ending == Ending.SERVE_FAULT
}

/** A point that gets a new winner from a server change (B-68, "The server"). [number] is the number in the list. */
data class WinnerChange(
    val pointId: String,
    val number: Int,
    /** True for a double fault, false for an ace. */
    val doubleFault: Boolean,
    val from: Outcome,
    val to: Outcome,
)

/** A game or a set that goes to another player after a server change. [game] is null for a set. [from] and [to] are 1, 2, or null. */
data class ScoreChange(val set: Int, val game: Int?, val from: Int?, val to: Int?)

/** What a server change does to the winners and to the score. */
data class ServerChangePreview(val winners: List<WinnerChange>, val scoreChanges: List<ScoreChange>)

/** Finds the effects of a server change before the app writes it (B-68, "The server"). */
object ServerChanges {
    /**
     * The points that get a new winner when [before] becomes [after], and the games and sets that then go to the other
     * player. Returns null when no winner changes: then the change needs no confirmation. [points] are in time order.
     * In advanced mode, the number of a point is the number of its pack. In basic mode, it is the position in the list.
     */
    fun preview(points: List<PointV1>, before: ScoreV1, after: ScoreV1): ServerChangePreview? {
        val sorted = points.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        val oldOutcomes = PointDetails.resolveOutcomes(sorted, before)
        val newOutcomes = PointDetails.resolveOutcomes(sorted, after)
        val changed = PointDetails.changedWinners(oldOutcomes, newOutcomes)
        if (changed.isEmpty()) return null
        val packs = ServePacks.of(sorted, oldOutcomes, before.details)
        val winners = sorted.withIndex().filter { it.value.id in changed }.map { (index, point) ->
            val number = if (before.isAdvanced) packs.of(point.id)?.number ?: (index + 1) else index + 1
            WinnerChange(
                pointId = point.id,
                number = number,
                doubleFault = packs.of(point.id)?.kind == ClipKind.DOUBLE_FAULT,
                from = oldOutcomes[point.id] ?: Outcome.NONE,
                to = newOutcomes[point.id] ?: Outcome.NONE,
            )
        }
        val oldResults = results(sorted, oldOutcomes, before)
        val newResults = results(sorted, newOutcomes, after)
        val scoreChanges = (0 until maxOf(oldResults.size, newResults.size)).mapNotNull { i ->
            val old = oldResults.getOrNull(i)
            val new = newResults.getOrNull(i)
            if (old?.winner == new?.winner) return@mapNotNull null
            val at = old ?: new!!
            ScoreChange(at.set, at.game, old?.winner, new?.winner)
        }
        return ServerChangePreview(winners, scoreChanges)
    }

    /** A game or a set that a player won. [game] is null for a set. */
    private data class Result(val set: Int, val game: Int?, val winner: Int)

    /**
     * The games and the sets in the order that the players won them. A game and a set have separate places in the
     * list, so that a set that changes its winner shows as one change.
     */
    private fun results(points: List<PointV1>, outcomes: Map<String, Outcome>, score: ScoreV1): List<Result> {
        val timeline = ScoringEngine.timeline(points, outcomes, score.rules.normalized(), score.manualMarks(), score.serverMarks)
        val results = mutableListOf<Result>()
        timeline.statesAfterPoint.forEachIndexed { index, state ->
            val before = timeline.stateBefore(index)
            val set = before.setsP1 + before.setsP2 + 1
            state.lastGameWonBy?.let { results += Result(set, before.gamesP1 + before.gamesP2 + 1, it) }
            state.lastSetWonBy?.let { results += Result(set, null, it) }
        }
        return results
    }
}
