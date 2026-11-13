package org.litvin.ui.tabs.points

import org.litvin.points.PointV1
import org.litvin.scoring.ClipKind
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ServePacks
import org.litvin.ui.tabs.points.ui.PackPosition
import org.litvin.ui.tabs.points.ui.PointRowInfo
import org.litvin.ui.tabs.points.ui.RowLabel

/** The number of the current point and the count of the points, for "Point x / y" in the navigator. */
internal data class PointNumber(val number: Int, val total: Int)

/**
 * The packs of the points list, the navigator, and the timeline (B-68 E3b, "Packs", "Labels", and "Timeline").
 *
 * In advanced mode:
 * - the list shows each pack as a group, with the number of the pack on its first row and a label on each row,
 * - the navigator shows the number of the pack and the count of the packs and the W clips,
 * - Ctrl+R and Ctrl+Shift+R (Cmd on macOS) go to the first clip of the next and the previous pack,
 * - the timeline shows the fault clips and the let clips dimmed.
 *
 * Basic mode has no packs: [row], [number], and [packTarget] give null, and [isDimmed] gives false. The list and the
 * navigator then number each clip, and Ctrl+R does the same as R.
 */
internal class PointPacks(points: List<PointV1>, private val score: ScoreV1) {
    val advanced: Boolean = score.isAdvanced

    private val packs: ServePacks = if (advanced) ServePacks.of(points, score.outcomes, score.details) else ServePacks.EMPTY
    private val starts: Map<String, Int> = points.associate { it.id to it.startMs }

    /** The first clip of each number (a pack or a W clip), in time order. Index 0 has the number 1. */
    private val firstClips: List<String> = packs.clips.distinctBy { it.number }.sortedBy { it.number }.map { it.pointId }

    /** What the row of the clip with [pointId] shows about its pack, or null in basic mode and for an unknown clip. */
    fun row(pointId: String): PointRowInfo? {
        val clip = packs.of(pointId) ?: return null
        val pack = packs.packOf(pointId)
        val position = when {
            pack == null || pack.clipIds.size < 2 -> PackPosition.SINGLE
            pack.clipIds.first() == pointId -> PackPosition.FIRST
            pack.clipIds.last() == pointId -> PackPosition.LAST
            else -> PackPosition.MIDDLE
        }
        return PointRowInfo(
            number = clip.number,
            showNumber = clip.firstOfPack,
            position = position,
            label = RowLabel.of(clip, score.details[pointId]),
            dimmed = clip.kind == ClipKind.FAULT || clip.kind == ClipKind.LET,
        )
    }

    /** "Point x / y" for the clip with [pointId]: the number of its pack and the count, or null in basic mode. */
    fun number(pointId: String): PointNumber? = packs.of(pointId)?.let { PointNumber(it.number, packs.count) }

    /**
     * True for a fault clip and a let clip in advanced mode. The timeline shows them dimmed. A double fault gives the
     * point to the receiver, so it is not dimmed.
     */
    fun isDimmed(pointId: String): Boolean {
        val kind = packs.of(pointId)?.kind
        return kind == ClipKind.FAULT || kind == ClipKind.LET
    }

    /**
     * The first clip of the next pack ([delta] = 1) or of the previous pack ([delta] = -1), or null when there is no
     * such pack. A W clip counts as a pack. With a selected clip, the target is the pack with the next or the previous
     * number. With no selected clip, the target is the first pack that starts after [playheadMs], or the last pack that
     * starts before it. Basic mode gives null: the caller then goes to the next or the previous clip.
     */
    fun packTarget(selectedId: String?, playheadMs: Long, delta: Int): String? {
        if (!advanced || firstClips.isEmpty()) return null
        val current = selectedId?.let(packs::of)
        if (current != null) return firstClips.getOrNull(current.number - 1 + delta)
        val start = { id: String -> starts[id]?.toLong() ?: 0L }
        return if (delta > 0) {
            firstClips.firstOrNull { start(it) > playheadMs }
        } else {
            firstClips.lastOrNull { start(it) < playheadMs }
        }
    }
}

/** Keeps the last [PointPacks], so that the paint of each row does not compute the packs again. */
internal class PointPacksCache {
    private var points: List<PointV1>? = null
    private var score: ScoreV1? = null
    private var value: PointPacks? = null

    /** The packs of [points] and [score]. The result is the same object while both stay the same objects. */
    fun of(points: List<PointV1>, score: ScoreV1): PointPacks {
        val cached = value
        if (cached != null && points === this.points && score === this.score) return cached
        return PointPacks(points, score).also {
            this.points = points
            this.score = score
            value = it
        }
    }
}
