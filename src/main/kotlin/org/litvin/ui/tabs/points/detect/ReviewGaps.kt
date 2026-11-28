package org.litvin.ui.tabs.points.detect

import org.litvin.points.PointV1

/**
 * The time gaps between the points of the review (A57 step 1). A long gap is a clue to a missed point.
 *
 * The gap of a point is the time from the end of the point before it to its start. The rule of the audit
 * (`docs/auto-analysis/review-ui-audit.md`): a gap is long when it is more than [FACTOR] times the median of the gaps
 * of the [NEIGHBORS] points around it, and more than [MIN_LONG_MS]. A gap after a game end is a changeover: it gets
 * no mark, and it is not in the median of the other gaps.
 */
internal object ReviewGaps {
    const val FACTOR = 2.0
    const val NEIGHBORS = 10
    const val MIN_LONG_MS = 25_000L

    /** The gap of each point of [points] (in time order) to the point before it. The first point has no gap (null). */
    fun gaps(points: List<PointV1>): List<Long?> =
        points.mapIndexed { index, point -> points.getOrNull(index - 1)?.let { (point.startMs - it.endMs).toLong().coerceAtLeast(0) } }

    /**
     * The ids of the points of [points] (in time order) that have a long gap before them. [gameEnds] are the ids of
     * the points that end a game.
     */
    fun longGaps(points: List<PointV1>, gameEnds: Set<String>): Set<String> {
        val gaps = gaps(points)
        // A gap after a game end is not a gap of a game.
        val inGame = gaps.mapIndexed { index, gap -> gap?.takeIf { points[index - 1].id !in gameEnds } }
        val result = LinkedHashSet<String>()
        inGame.forEachIndexed { index, gap ->
            if (gap == null || gap <= MIN_LONG_MS) return@forEachIndexed
            val median = median(neighbors(inGame, index)) ?: return@forEachIndexed
            if (gap > FACTOR * median) result += points[index].id
        }
        return result
    }

    /** The gaps of the [NEIGHBORS] points nearest to [index] (half before, half after; more on one side at an end). */
    private fun neighbors(gaps: List<Long?>, index: Int): List<Long> {
        val known = gaps.withIndex().filter { it.index != index && it.value != null }
        return known.sortedWith(compareBy({ kotlin.math.abs(it.index - index) }, { it.index }))
            .take(NEIGHBORS)
            .map { it.value!! }
    }

    private fun median(values: List<Long>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    /** The text of a gap in the list, for example "+38 s". */
    fun text(gapMs: Long): String = "+${(gapMs + 500) / 1000} s"
}
