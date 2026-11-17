package org.litvin.export

import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.ClipKind
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetails
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ServePacks

/**
 * Which clips the "Only points" and "Only favorites" exports keep (B-68, "Export").
 * - A project in basic mode keeps all clips, as before B-68. The fault clips and the let clips are then normal points
 *   with no winner.
 * - A project in advanced mode keeps the fault clips and the let clips only with the option "Include serve faults and
 *   lets". A double fault is the serve that counts, so it stays also without the option.
 * - "Only favorites" uses the favorite flag of the clip that ends the pack. Thus a fault clip of a favorite point goes
 *   in with the option. A pack at the end of the list with no point clip uses the flags of its own clips.
 * - The clips of zero length have no video. [ExportPlanner.validateEdl] already removes them.
 * - A [PointFilter] with a selection keeps only the packs whose point clip matches (B-69). The fault clips and the let
 *   clips of a pack follow its point clip.
 */
class ExportClips private constructor(
    private val kinds: Map<String, ClipKind>,
    private val packFavorite: Map<String, Boolean>,
    private val includeServeClips: Boolean,
    /** The ids of the clips that match the point filter, or null when no filter is active. */
    private val matching: Set<String>? = null,
) {
    /** True when a point filter with a selection is active (B-69). */
    val filtered: Boolean get() = matching != null

    /** True when the export keeps [point]. [favoriteOnly] is "Only favorites". */
    fun keeps(point: PointV1, favoriteOnly: Boolean): Boolean {
        if (matching != null && point.id !in matching) return false
        val kind = kinds[point.id]
        val serveClip = kind == ClipKind.FAULT || kind == ClipKind.LET
        if (serveClip && !includeServeClips) return false
        if (!favoriteOnly) return true
        return packFavorite[point.id] ?: point.favorite
    }

    /** The clips of [validPoints] that the export keeps, in the same order. */
    fun keep(validPoints: List<PointV1>, favoriteOnly: Boolean): List<PointV1> = validPoints.filter { keeps(it, favoriteOnly) }

    companion object {
        /** Keeps all clips: basic mode, and the exports before B-68. */
        val ALL = ExportClips(emptyMap(), emptyMap(), includeServeClips = true)

        /**
         * The rule for a project with [edl] and [score]. [includeServeClips] is the option "Include serve faults and
         * lets". The packs use all clips of [edl], also the clips of zero length. Only a project in advanced mode uses
         * [filter], so the free version, which has no advanced mode, ignores it.
         */
        fun of(edl: EdlV1?, score: ScoreV1, includeServeClips: Boolean, filter: PointFilter = PointFilter.NONE): ExportClips {
            if (!score.isAdvanced || edl == null) return ALL
            val points = edl.points
            val packs = ServePacks.of(points, score.outcomes, score.details)
            val byId = points.associateBy { it.id }
            val favorite = HashMap<String, Boolean>()
            packs.packs.filter { it.complete }.forEach { pack ->
                val flag = byId[pack.clipIds.last()]?.favorite ?: false
                pack.clipIds.forEach { favorite[it] = flag }
            }
            val matching = if (filter.isActive) {
                packs.packs.filter { it.complete && packMatches(it.clipIds.last(), packs, score, filter) }
                    .flatMapTo(HashSet()) { it.clipIds }
            } else {
                null
            }
            return ExportClips(packs.clips.associate { it.pointId to it.kind }, favorite, includeServeClips, matching)
        }

        /** True when the clip that ends a pack matches [filter]. A double fault is an ending of the server. */
        private fun packMatches(lastId: String, packs: ServePacks, score: ScoreV1, filter: PointFilter): Boolean {
            val winner = score.outcomeOf(lastId)
            val detail = score.detailOf(lastId)
            return when (packs.of(lastId)?.kind) {
                ClipKind.DOUBLE_FAULT -> {
                    val server = when (winner) {
                        Outcome.P1 -> 2
                        Outcome.P2 -> 1
                        Outcome.NONE -> null
                    }
                    filter.matches(FilterEnding.DOUBLE_FAULT, server, errorKind = null)
                }
                ClipKind.POINT -> filter.matches(
                    FilterEnding.of(detail.ending),
                    PointDetails.playerOfEnding(detail.ending, winner),
                    detail.errorKind,
                )
                else -> false
            }
        }
    }
}
