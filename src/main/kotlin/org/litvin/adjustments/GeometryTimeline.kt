package org.litvin.adjustments

/**
 * The geometry of each time in the source video, with the change points of B-47.
 *
 * Before the first change point, the geometry uses the values of [AdjustmentsV1]. From a change point on, it uses
 * the values of that change point. The change is a blend of [BLEND_MS] that ends at the change point, so the frame
 * at the change point gets the exact values. The blend starts at the previous change point if that is nearer.
 *
 * A blend moves between the two [GeometryPlan]s: the angle takes the shorter direction, and the crop rectangle
 * moves linearly. The preview shader and the FFmpeg export use the same [blends] and [ease].
 */
object GeometryTimeline {
    const val BLEND_MS = 1000L

    /** One blend: from [startMs] to [endMs] the geometry moves from [from] to [to]. */
    data class Blend(val startMs: Long, val endMs: Long, val from: GeometryPlan, val to: GeometryPlan) {
        /** The geometry at [ms]. Before [startMs] it is [from], after [endMs] it is [to]. */
        fun planAt(ms: Long): GeometryPlan {
            val fraction = (ms - startMs).toDouble() / (endMs - startMs).coerceAtLeast(1L)
            return lerp(from, to, ease(fraction))
        }
    }

    fun hasKeys(adjustments: AdjustmentsV1): Boolean = adjustments.geometryKeys.isNotEmpty()

    /** The index of the last change point at or before [ms], or -1 before the first change point. */
    fun activeIndex(adjustments: AdjustmentsV1, ms: Long): Int =
        adjustments.geometryKeys.indexOfLast { it.atMs <= ms }

    /**
     * The values of change point [index] as adjustments without change points. Index -1 gives the values that
     * apply before the first change point. The color values do not change.
     */
    fun valuesOf(adjustments: AdjustmentsV1, index: Int): AdjustmentsV1 {
        val key = adjustments.geometryKeys.getOrNull(index)
            ?: return adjustments.copy(geometryKeys = emptyList())
        return adjustments.copy(
            zoom = key.zoom,
            panX = key.panX,
            panY = key.panY,
            rotationDeg = key.rotationDeg,
            geometryKeys = emptyList(),
        )
    }

    /** The values that the Transform controls edit at [ms]: those of the active change point, without the blend. */
    fun editingAt(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 = valuesOf(adjustments, activeIndex(adjustments, ms))

    /** Writes the geometry of [edited] into the active change point at [ms], or into the usual values before the first one. */
    fun withEdited(adjustments: AdjustmentsV1, ms: Long, edited: AdjustmentsV1): AdjustmentsV1 {
        val index = activeIndex(adjustments, ms)
        if (index < 0) {
            return adjustments.copy(zoom = edited.zoom, panX = edited.panX, panY = edited.panY, rotationDeg = edited.rotationDeg)
        }
        val keys = adjustments.geometryKeys.toMutableList()
        keys[index] = keys[index].copy(
            zoom = edited.zoom,
            panX = edited.panX,
            panY = edited.panY,
            rotationDeg = edited.rotationDeg,
        )
        return adjustments.copy(geometryKeys = keys)
    }

    /**
     * Adds a change point at [ms] with the values of the active change point, so the video does not change until
     * the user edits the values. Nothing changes if a change point is already at [ms].
     */
    fun withKeyAdded(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 {
        val at = ms.coerceAtLeast(0L)
        if (adjustments.geometryKeys.any { it.atMs == at }) return adjustments
        val values = editingAt(adjustments, at)
        val key = GeometryKeyV1(at, values.zoom, values.panX, values.panY, values.rotationDeg)
        return adjustments.copy(geometryKeys = (adjustments.geometryKeys + key).sortedBy { it.atMs })
    }

    /** Removes the active change point at [ms]. Nothing changes before the first change point. */
    fun withKeyRemoved(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 {
        val index = activeIndex(adjustments, ms)
        if (index < 0) return adjustments
        return adjustments.copy(geometryKeys = adjustments.geometryKeys.filterIndexed { i, _ -> i != index })
    }

    /** The blends of a [width] x [height] source, in time order. A change point at 0 ms has no blend. */
    fun blends(adjustments: AdjustmentsV1, width: Int, height: Int): List<Blend> {
        val keys = adjustments.geometryKeys
        if (keys.isEmpty()) return emptyList()
        val plans = (-1 until keys.size).map { GeometryPlan.of(valuesOf(adjustments, it), width, height) }
        return keys.mapIndexedNotNull { index, key ->
            val previousAt = keys.getOrNull(index - 1)?.atMs ?: 0L
            val start = maxOf(previousAt, key.atMs - BLEND_MS)
            if (start >= key.atMs) null else Blend(start, key.atMs, plans[index], plans[index + 1])
        }
    }

    /** The geometry at [ms] of a [width] x [height] source. In a blend, the crop is not snapped to even pixels. */
    fun planAt(adjustments: AdjustmentsV1, ms: Long, width: Int, height: Int): GeometryPlan {
        if (!hasKeys(adjustments)) return GeometryPlan.of(adjustments, width, height)
        blends(adjustments, width, height).firstOrNull { ms >= it.startMs && ms < it.endMs }?.let { return it.planAt(ms) }
        return GeometryPlan.of(valuesOf(adjustments, activeIndex(adjustments, ms)), width, height)
    }

    /** True when the geometry is not the identity at some time. */
    fun hasGeometry(adjustments: AdjustmentsV1, width: Int, height: Int): Boolean =
        (-1 until adjustments.geometryKeys.size).any { !GeometryPlan.of(valuesOf(adjustments, it), width, height).isIdentity }

    /** The blends that overlap the source span [startMs, endMs). */
    fun blendsIn(adjustments: AdjustmentsV1, width: Int, height: Int, startMs: Long, endMs: Long): List<Blend> =
        blends(adjustments, width, height).filter { it.startMs < endMs && it.endMs > startMs }

    /** Smoothstep: slow at the start and at the end of the blend. The FFmpeg expression uses the same curve. */
    fun ease(fraction: Double): Double {
        val s = fraction.coerceIn(0.0, 1.0)
        return s * s * (3 - 2 * s)
    }

    /** The geometry at [amount] (0 to 1) of the way from [from] to [to]. The angle takes the shorter direction. */
    fun lerp(from: GeometryPlan, to: GeometryPlan, amount: Double): GeometryPlan {
        fun mix(a: Double, b: Double) = a + (b - a) * amount
        val rotation = from.rotationDeg + rotationDelta(from, to) * amount
        val crop = CropRect(
            mix(from.crop.x, to.crop.x),
            mix(from.crop.y, to.crop.y),
            mix(from.crop.width, to.crop.width),
            mix(from.crop.height, to.crop.height),
        )
        return GeometryPlan(rotation, crop)
    }

    /** The change of the angle from [from] to [to] in the shorter direction, in degrees. */
    fun rotationDelta(from: GeometryPlan, to: GeometryPlan): Double {
        var delta = (to.rotationDeg - from.rotationDeg) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }
}
