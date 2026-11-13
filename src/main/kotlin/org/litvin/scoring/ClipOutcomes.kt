package org.litvin.scoring

import org.litvin.points.PointV1

/** The kind of a clip that the Edit dialog of advanced mode sets (B-68, "Corrections"). */
enum class ClipChoice {
    /** A clip that gives the point: an ending, or the winner only. */
    POINT,

    /** A serve fault. The second fault of a pack is the double fault. */
    FAULT,

    /** A service let. */
    LET,

    /** A W clip: no point. */
    NO_POINT,
}

/**
 * The full outcome of one clip in the Edit dialog of advanced mode (B-68, "Corrections").
 * - [winner] (1 or 2) is only for a [ClipChoice.POINT] clip.
 * - [ending] is only for a point clip: [Ending.ACE], [Ending.WINNER], [Ending.FORCED_ERROR], [Ending.UNFORCED_ERROR], or
 *   null for the winner only (as Q and E).
 * - [errorKind] is only for an error ending.
 */
data class ClipOutcome(
    val kind: ClipChoice,
    val winner: Int? = null,
    val ending: Ending? = null,
    val errorKind: ErrorKind? = null,
)

/** The rules of the Edit dialog of advanced mode (B-68, "Corrections"). */
object ClipOutcomes {
    /** The outcome of the clip with [pointId] as the dialog shows it. A double fault is a [ClipChoice.FAULT] clip. */
    fun current(score: ScoreV1, pointId: String): ClipOutcome {
        val detail = score.detailOf(pointId)
        return when (detail.ending) {
            Ending.SERVE_FAULT -> ClipOutcome(ClipChoice.FAULT)
            Ending.LET -> ClipOutcome(ClipChoice.LET)
            else -> when (score.outcomeOf(pointId)) {
                Outcome.P1 -> ClipOutcome(ClipChoice.POINT, 1, detail.ending, detail.errorKind)
                Outcome.P2 -> ClipOutcome(ClipChoice.POINT, 2, detail.ending, detail.errorKind)
                Outcome.NONE -> ClipOutcome(ClipChoice.NO_POINT)
            }
        }.let(::normalized)
    }

    /**
     * [outcome] with only the fields that its kind uses. A point clip with no winner gets player 1. An ace goes to the
     * [server] when the server is known, as for the ace keys. Only an error ending keeps an error kind.
     */
    fun normalized(outcome: ClipOutcome, server: Int? = null): ClipOutcome {
        if (outcome.kind != ClipChoice.POINT) return ClipOutcome(outcome.kind)
        val ending = outcome.ending?.takeIf { it.endsRally }
        val winner = if (ending == Ending.ACE && server != null) server else outcome.winner?.takeIf { it == 1 || it == 2 } ?: 1
        return ClipOutcome(ClipChoice.POINT, winner, ending, outcome.errorKind?.takeIf { ending?.isError == true })
    }

    /**
     * True when [point] can have [outcome] after the edit. Only a fault clip can have zero length (see
     * [PointEndings.isZeroLength]).
     */
    fun allows(point: PointV1, outcome: ClipOutcome): Boolean =
        !PointEndings.isZeroLength(point) || outcome.kind == ClipChoice.FAULT

    /**
     * [score] with [outcome] on the clip with [pointId]. The app then calls [PointDetails.resolve], which gives the
     * receiver a double fault and the server an ace.
     */
    fun apply(score: ScoreV1, pointId: String, outcome: ClipOutcome): ScoreV1 {
        val edit = normalized(outcome)
        return when (edit.kind) {
            ClipChoice.FAULT -> PointEndings.withServeEnding(score, pointId, Ending.SERVE_FAULT)
            ClipChoice.LET -> PointEndings.withServeEnding(score, pointId, Ending.LET)
            ClipChoice.NO_POINT -> score.copy(outcomes = score.outcomes + (pointId to Outcome.NONE)).withDetail(pointId, PointDetailV1())
            ClipChoice.POINT -> {
                val winner = if (edit.winner == 2) Outcome.P2 else Outcome.P1
                score.copy(outcomes = score.outcomes + (pointId to winner))
                    .withDetail(pointId, PointDetailV1(edit.ending, edit.errorKind))
            }
        }
    }
}
