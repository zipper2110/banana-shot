package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonIgnore
import org.litvin.points.PointV1

/**
 * The scoring mode of a project (B-68). The mode sets which controls the point bar shows. The modes are nested: each
 * mode gives all the data of the modes below it. A mode change deletes no data. A lower mode hides the extra data.
 */
enum class ScoringMode {
    /** The user records the winner of each point. */
    @JsonEnumDefaultValue
    BASIC,

    /** The user records the serve faults, the lets, and how each point ended. */
    ADVANCED,
}

/** How a clip ended (B-68). The details keep no DOUBLE_FAULT ending: the packs give it (see [ServePacks]). */
enum class Ending {
    /** A serve fault. The second fault of a pack is the double fault, and the receiver wins it. */
    SERVE_FAULT,

    /** A service let. The serve number does not change. */
    LET,

    /** The serve lands in, and the receiver does not touch it. The server wins. */
    ACE,

    /** A shot lands in, and the opponent does not touch it. The player who hit it wins. */
    WINNER,

    /** The ball goes into the net or out, and the player was under pressure. The opponent wins. */
    FORCED_ERROR,

    /** The ball goes into the net or out, and the player was not under pressure. The opponent wins. */
    UNFORCED_ERROR,
    ;

    /** True for a forced or an unforced error. Only these endings have an [ErrorKind]. */
    val isError: Boolean get() = this == FORCED_ERROR || this == UNFORCED_ERROR

    /** True for an ending that gives the point: an ace, a winner, or an error. */
    val endsRally: Boolean get() = this == ACE || this == WINNER || isError
}

/** Where an error went (B-68, the follow-up question). */
enum class ErrorKind { NET, LONG, WIDE }

/**
 * The details of one clip (B-68). The winner stays in [ScoreV1.outcomes]. The player who made a winner or an ace is the
 * point winner. The player who made an error is the other player. Thus the details need no player field.
 */
data class PointDetailV1(
    val ending: Ending? = null,
    /** Only for an error ending. */
    val errorKind: ErrorKind? = null,
) {
    /** True when the detail keeps nothing. The score file does not keep an empty detail. */
    @JsonIgnore
    fun isEmpty(): Boolean = ending == null && errorKind == null
}

/** The kind of a clip in the packs (B-68). */
enum class ClipKind {
    /** A clip that gives the point: an ending, or a winner with no ending (basic mode, or Q and E). */
    POINT,

    /** A serve fault that is not the double fault of its pack. It has no winner. */
    FAULT,

    /** The second serve fault of a pack. The receiver wins it, and it ends the pack. */
    DOUBLE_FAULT,

    /** A service let. It has no winner. */
    LET,

    /** A clip with no winner and no serve ending: a W clip ("No point"), or a clip with no score yet. It is in no pack. */
    NO_POINT,
}

/**
 * One clip in the packs.
 * - [serve] is 1 or 2 for a clip of a pack, and null for a [ClipKind.NO_POINT] clip.
 * - [pack] is the index of the pack in [ServePacks.packs], or null for a [ClipKind.NO_POINT] clip.
 * - [number] is the number of the pack or of the W clip, from 1. The numbers go over the packs and the W clips in time
 *   order. All clips of a pack have the number of the pack.
 */
data class PackedClip(
    val pointId: String,
    val kind: ClipKind,
    val serve: Int?,
    val pack: Int?,
    val number: Int,
    val firstOfPack: Boolean,
    val lastOfPack: Boolean,
)

/**
 * One pack: all the clips of one real point (B-68). The fault clips and the let clips of its serves come first, then
 * the clip that gives the point. [complete] is false for a pack at the end of the list that has no point clip yet.
 */
data class ServePack(val clipIds: List<String>, val complete: Boolean)

/**
 * The packs of a project (B-68, "Packs"). A fault clip or a let clip belongs to the next point clip in time order.
 * A pack ends with the clip that gives the point: an ending, a double fault, or a winner from basic mode. A W clip is
 * not in a pack. The serve numbers come from the packs, so no stored value can disagree with the clips.
 */
class ServePacks private constructor(
    /** The clips in time order. */
    val clips: List<PackedClip>,
    val packs: List<ServePack>,
    private val times: List<PointV1>,
) {
    private val byId: Map<String, PackedClip> = clips.associateBy { it.pointId }

    /** The clip with [pointId], or null when the project has no such clip. */
    fun of(pointId: String): PackedClip? = byId[pointId]

    /** The pack of the clip with [pointId], or null for a W clip or an unknown id. */
    fun packOf(pointId: String): ServePack? = byId[pointId]?.pack?.let(packs::get)

    /** The number of the packs and the W clips. */
    val count: Int get() = clips.lastOrNull()?.number ?: 0

    /**
     * The serve number of a new clip that starts at [timeMs]: 2 when the open pack before [timeMs] has one fault
     * clip, else 1. The clips that end at or before [timeMs] are before it. A clip of zero length at [timeMs] counts.
     */
    fun serveAt(timeMs: Long): Int {
        val before = openPackBefore(timeMs)
        return if (before.any { it.kind == ClipKind.FAULT }) 2 else 1
    }

    /**
     * True when the pack that a new clip at [timeMs] joins already has a fault clip or a let clip. The pack has the
     * open clips before [timeMs] and the clips after [timeMs] up to the next clip that ends a pack.
     */
    fun packAtHasServeClip(timeMs: Long): Boolean {
        val before = openPackBefore(timeMs)
        val after = mutableListOf<PackedClip>()
        for ((clip, time) in clips.zip(times)) {
            if (time.endMs <= timeMs) continue
            if (clip.kind == ClipKind.NO_POINT) continue
            after += clip
            if (clip.lastOfPack) break
        }
        return (before + after).any { it.kind == ClipKind.FAULT || it.kind == ClipKind.LET }
    }

    /** The clips of the last pack before [timeMs] when that pack has no clip that ends it yet. W clips are skipped. */
    private fun openPackBefore(timeMs: Long): List<PackedClip> {
        val open = mutableListOf<PackedClip>()
        for ((clip, time) in clips.zip(times)) {
            if (time.endMs > timeMs) break
            if (clip.kind == ClipKind.NO_POINT) continue
            if (clip.lastOfPack) open.clear() else open += clip
        }
        return open
    }

    companion object {
        /** An empty result, for a project with no clips. */
        val EMPTY = ServePacks(emptyList(), emptyList(), emptyList())

        /**
         * Computes the packs of [points]. The order is the time order: the start, then the end, so that a clip of zero
         * length comes before a clip that starts at the same time.
         */
        fun of(points: List<PointV1>, outcomes: Map<String, Outcome>, details: Map<String, PointDetailV1>): ServePacks {
            val sorted = points.sortedWith(compareBy({ it.startMs }, { it.endMs }))
            val kinds = ArrayList<ClipKind>(sorted.size)
            var faults = 0
            for (point in sorted) {
                val kind = when (details[point.id]?.ending) {
                    Ending.SERVE_FAULT -> if (faults >= 1) ClipKind.DOUBLE_FAULT else ClipKind.FAULT
                    Ending.LET -> ClipKind.LET
                    else -> when (outcomes[point.id]) {
                        Outcome.P1, Outcome.P2 -> ClipKind.POINT
                        Outcome.NONE, null -> ClipKind.NO_POINT
                    }
                }
                kinds += kind
                when (kind) {
                    ClipKind.FAULT -> faults++
                    ClipKind.POINT, ClipKind.DOUBLE_FAULT -> faults = 0
                    ClipKind.LET, ClipKind.NO_POINT -> Unit
                }
            }
            val clips = ArrayList<PackedClip>(sorted.size)
            val packs = ArrayList<ServePack>()
            var current = mutableListOf<String>()
            var serve = 1
            var number = 0
            for ((point, kind) in sorted.zip(kinds)) {
                if (kind == ClipKind.NO_POINT) {
                    number++
                    clips += PackedClip(point.id, kind, serve = null, pack = null, number = number, firstOfPack = true, lastOfPack = true)
                    continue
                }
                val first = current.isEmpty()
                if (first) number++
                current += point.id
                val ends = kind == ClipKind.POINT || kind == ClipKind.DOUBLE_FAULT
                clips += PackedClip(point.id, kind, serve, packs.size, number, first, ends)
                if (kind == ClipKind.FAULT) serve = 2
                if (ends) {
                    packs += ServePack(current, complete = true)
                    current = mutableListOf()
                    serve = 1
                }
            }
            if (current.isNotEmpty()) packs += ServePack(current, complete = false)
            // A clip keeps the number of its pack, also when a W clip comes between the clips of the pack.
            val packNumbers = packs.map { pack -> clips.first { it.pointId == pack.clipIds.first() }.number }
            val numbered = clips.map { clip -> clip.pack?.let { clip.copy(number = packNumbers[it]) } ?: clip }
            return ServePacks(numbered, packs, sorted)
        }
    }
}

/** The rules that give the winners from the details (B-68, "The server" and "Packs"). */
object PointDetails {
    /**
     * The outcomes with the winners that the details give:
     * - a fault clip and a let clip have no winner,
     * - a double fault goes to the receiver,
     * - an ace goes to the server.
     *
     * When the server of a clip is not known, an ace and a double fault keep their stored outcome. The server of a point
     * depends only on the points before it, so the loop ends after at most one round for each derived winner.
     */
    fun resolveOutcomes(points: List<PointV1>, score: ScoreV1): Map<String, Outcome> {
        if (score.details.isEmpty()) return score.outcomes
        val sorted = points.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        var outcomes = score.outcomes
        repeat(sorted.size + 1) {
            val packs = ServePacks.of(sorted, outcomes, score.details)
            val servers = ScoringEngine.timeline(
                sorted, outcomes, score.rules.normalized(), score.manualMarks(), score.serverMarks,
            ).serverOfPoint
            val next = outcomes.toMutableMap()
            sorted.forEachIndexed { index, point ->
                val server = servers.getOrNull(index)
                val derived = when (packs.of(point.id)?.kind) {
                    ClipKind.FAULT, ClipKind.LET -> Outcome.NONE
                    ClipKind.DOUBLE_FAULT -> server?.let { playerOutcome(3 - it) }
                    ClipKind.POINT -> if (score.details[point.id]?.ending == Ending.ACE) server?.let(::playerOutcome) else null
                    ClipKind.NO_POINT, null -> null
                }
                if (derived != null) next[point.id] = derived
            }
            if (next == outcomes) return outcomes
            outcomes = next
        }
        return outcomes
    }

    /** [score] with the outcomes of [resolveOutcomes]. */
    fun resolve(points: List<PointV1>, score: ScoreV1): ScoreV1 {
        val outcomes = resolveOutcomes(points, score)
        return if (outcomes == score.outcomes) score else score.copy(outcomes = outcomes)
    }

    /** The ids of the clips whose outcome is different in [after]. A missing outcome is [Outcome.NONE]. */
    fun changedWinners(before: Map<String, Outcome>, after: Map<String, Outcome>): Set<String> =
        (before.keys + after.keys).filterTo(LinkedHashSet()) { (before[it] ?: Outcome.NONE) != (after[it] ?: Outcome.NONE) }

    /** The player (1 or 2) who made the ending of a point clip that [winner] won, or null for a fault, a let, or no ending. */
    fun playerOfEnding(ending: Ending?, winner: Outcome): Int? {
        val won = when (winner) {
            Outcome.P1 -> 1
            Outcome.P2 -> 2
            Outcome.NONE -> return null
        }
        return when {
            ending == Ending.ACE || ending == Ending.WINNER -> won
            ending?.isError == true -> 3 - won
            else -> null
        }
    }

    private fun playerOutcome(player: Int): Outcome = if (player == 1) Outcome.P1 else Outcome.P2
}
