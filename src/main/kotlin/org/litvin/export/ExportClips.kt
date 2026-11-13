package org.litvin.export

import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.ClipKind
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
 */
class ExportClips private constructor(
    private val kinds: Map<String, ClipKind>,
    private val packFavorite: Map<String, Boolean>,
    private val includeServeClips: Boolean,
) {
    /** True when the export keeps [point]. [favoriteOnly] is "Only favorites". */
    fun keeps(point: PointV1, favoriteOnly: Boolean): Boolean {
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
         * lets". The packs use all clips of [edl], also the clips of zero length.
         */
        fun of(edl: EdlV1?, score: ScoreV1, includeServeClips: Boolean): ExportClips {
            if (!score.isAdvanced || edl == null) return ALL
            val points = edl.points
            val packs = ServePacks.of(points, score.outcomes, score.details)
            val byId = points.associateBy { it.id }
            val favorite = HashMap<String, Boolean>()
            packs.packs.filter { it.complete }.forEach { pack ->
                val flag = byId[pack.clipIds.last()]?.favorite ?: false
                pack.clipIds.forEach { favorite[it] = flag }
            }
            return ExportClips(packs.clips.associate { it.pointId to it.kind }, favorite, includeServeClips)
        }
    }
}
