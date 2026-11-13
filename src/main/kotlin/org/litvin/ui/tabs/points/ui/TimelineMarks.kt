package org.litvin.ui.tabs.points.ui

import org.litvin.points.PointV1
import kotlin.math.max

/**
 * The geometry of the clip marks on the MARKS track of the timeline (B-68 E3b, "Timeline").
 *
 * A clip of zero length (a fault clip with `startMs == endMs`) has no video. It shows as a thin mark of [THIN] px over
 * the full height of the track, with its center at the time of the clip. A click in [HIT_SLOP] px of the mark selects
 * it. A clip with a length has at least [MIN_WIDTH] px.
 */
internal object TimelineMarks {
    const val THIN = 2
    const val MIN_WIDTH = 2
    const val HIT_SLOP = 3

    /** True for a clip of zero length. A clip that ends before its start also has no video. */
    fun isZeroLength(point: PointV1): Boolean = point.endMs <= point.startMs

    /** The left x and the width of the mark of a clip from [x1] (the start) to [x2] (the end). */
    fun span(x1: Int, x2: Int, zeroLength: Boolean): Pair<Int, Int> =
        if (zeroLength) (x1 - THIN / 2) to THIN else x1 to max(MIN_WIDTH, x2 - x1)

    /** True when [x] is on the mark of a clip from [x1] to [x2]. A thin mark has a wider hit area. */
    fun hits(x: Int, x1: Int, x2: Int, zeroLength: Boolean): Boolean =
        if (zeroLength) x in (x1 - HIT_SLOP)..(x1 + HIT_SLOP) else x in x1..max(x1 + 1, x2)

    /**
     * The clip at [timeMs] after a click on the timeline: the clip that contains [timeMs] (half-open interval
     * [start, end)), else a clip of zero length at [timeMs].
     */
    fun clipAt(points: List<PointV1>, timeMs: Long): PointV1? =
        points.firstOrNull { it.startMs <= timeMs && timeMs < it.endMs }
            ?: points.firstOrNull { isZeroLength(it) && it.startMs.toLong() == timeMs }
}
