package org.litvin.adjustments

/**
 * The change points of the Colors & Transform tab (B-47). A change point has the colors and the transform.
 *
 * The file keeps two lists, [AdjustmentsV1.colorKeys] and [AdjustmentsV1.geometryKeys]. The tab adds and removes a
 * change point in the two lists together, so the two lists have the same times. An older file can have a time in one
 * list only. Then the change point time is in [times], and the controls edit the active key of each list.
 */
object ChangePoints {
    /** The times of all change points, sorted and without duplicates. */
    fun times(adjustments: AdjustmentsV1): List<Long> =
        (adjustments.colorKeys.map { it.atMs } + adjustments.geometryKeys.map { it.atMs }).distinct().sorted()

    /** The values that the controls edit at [ms]: the colors and the transform of the active change point, without blends. */
    fun editingAt(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 =
        GeometryTimeline.editingAt(ColorTimeline.editingAt(adjustments, ms), ms)

    /**
     * Adds a change point at [ms] with the active colors and transform, so the video does not change until the user
     * edits the values. A list that has a key at [ms] already does not change.
     */
    fun withAdded(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 =
        GeometryTimeline.withKeyAdded(ColorTimeline.withKeyAdded(adjustments, ms), ms)

    /** Removes the active change point at [ms] from the two lists. Nothing changes before the first change point. */
    fun withRemoved(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 {
        val at = times(adjustments).lastOrNull { it <= ms } ?: return adjustments
        return adjustments.copy(
            colorKeys = adjustments.colorKeys.filter { it.atMs != at },
            geometryKeys = adjustments.geometryKeys.filter { it.atMs != at },
        )
    }
}
