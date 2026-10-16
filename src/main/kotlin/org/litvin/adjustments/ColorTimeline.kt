package org.litvin.adjustments

/**
 * The colors of each time in the source video, with the color change points of B-47.
 *
 * Before the first color change point, the colors are the values of [AdjustmentsV1]. From a change point on, they
 * are the values of that change point. The change is a linear blend of [BLEND_MS] that ends at the change point.
 * The blend starts at the previous change point if that is nearer.
 *
 * The blend is linear because the export fades from the old colors to the new colors: FFmpeg cannot change the
 * tone curve at each frame. The color operations are almost linear in their values, so the preview, which mixes
 * the values, shows the same result.
 */
object ColorTimeline {
    const val BLEND_MS = GeometryTimeline.BLEND_MS

    /** One blend: from [startMs] to [endMs] the colors move from [from] to [to]. Both have no change points. */
    data class Blend(val startMs: Long, val endMs: Long, val from: AdjustmentsV1, val to: AdjustmentsV1) {
        fun fractionAt(ms: Long): Double = ((ms - startMs).toDouble() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0.0, 1.0)
        fun colorsAt(ms: Long): AdjustmentsV1 = lerp(from, to, fractionAt(ms))
    }

    /**
     * A part of a source span for the export. [Piece.Hold] has fixed colors. [Piece.Fade] fades from [Piece.Fade.from]
     * to [Piece.Fade.to] over the full piece.
     */
    sealed interface Piece {
        val startMs: Long
        val endMs: Long

        data class Hold(override val startMs: Long, override val endMs: Long, val colors: AdjustmentsV1) : Piece
        data class Fade(override val startMs: Long, override val endMs: Long, val from: AdjustmentsV1, val to: AdjustmentsV1) : Piece
    }

    fun hasKeys(adjustments: AdjustmentsV1): Boolean = adjustments.colorKeys.isNotEmpty()

    /** The index of the last color change point at or before [ms], or -1 before the first one. */
    fun activeIndex(adjustments: AdjustmentsV1, ms: Long): Int = adjustments.colorKeys.indexOfLast { it.atMs <= ms }

    /**
     * The colors of change point [index], without color change points. Index -1 gives the colors before the first
     * change point. The geometry and its change points do not change.
     */
    fun valuesOf(adjustments: AdjustmentsV1, index: Int): AdjustmentsV1 {
        val key = adjustments.colorKeys.getOrNull(index) ?: return adjustments.copy(colorKeys = emptyList())
        return adjustments.copy(
            brightness = key.brightness,
            contrast = key.contrast,
            saturation = key.saturation,
            shadows = key.shadows,
            highlights = key.highlights,
            whiteBalance = WhiteBalanceV1(key.temperature),
            colorKeys = emptyList(),
        )
    }

    /** The colors that the Colors sub-tab edits at [ms]: those of the active change point, without the blend. */
    fun editingAt(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 = valuesOf(adjustments, activeIndex(adjustments, ms))

    /** The colors at [ms], with the blend. The result has no color change points. */
    fun at(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 {
        if (!hasKeys(adjustments)) return adjustments
        blends(adjustments).firstOrNull { ms >= it.startMs && ms < it.endMs }?.let { blend ->
            return withColors(adjustments.copy(colorKeys = emptyList()), blend.colorsAt(ms))
        }
        return editingAt(adjustments, ms)
    }

    /** Writes the colors of [edited] into the active change point at [ms], or into the usual colors before the first one. */
    fun withEdited(adjustments: AdjustmentsV1, ms: Long, edited: AdjustmentsV1): AdjustmentsV1 {
        val index = activeIndex(adjustments, ms)
        if (index < 0) return withColors(adjustments, edited)
        val keys = adjustments.colorKeys.toMutableList()
        keys[index] = keyOf(keys[index].atMs, edited)
        return adjustments.copy(colorKeys = keys)
    }

    /** Adds a change point at [ms] with the active colors. Nothing changes if a change point is already at [ms]. */
    fun withKeyAdded(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 {
        val at = ms.coerceAtLeast(0L)
        if (adjustments.colorKeys.any { it.atMs == at }) return adjustments
        val key = keyOf(at, editingAt(adjustments, at))
        return adjustments.copy(colorKeys = (adjustments.colorKeys + key).sortedBy { it.atMs })
    }

    /** Removes the active color change point at [ms]. Nothing changes before the first one. */
    fun withKeyRemoved(adjustments: AdjustmentsV1, ms: Long): AdjustmentsV1 {
        val index = activeIndex(adjustments, ms)
        if (index < 0) return adjustments
        return adjustments.copy(colorKeys = adjustments.colorKeys.filterIndexed { i, _ -> i != index })
    }

    /** The blends in time order. A change point at 0 ms has no blend. */
    fun blends(adjustments: AdjustmentsV1): List<Blend> {
        val keys = adjustments.colorKeys
        return keys.mapIndexedNotNull { index, key ->
            val previousAt = keys.getOrNull(index - 1)?.atMs ?: 0L
            val start = maxOf(previousAt, key.atMs - BLEND_MS)
            if (start >= key.atMs) null else Blend(start, key.atMs, valuesOf(adjustments, index - 1), valuesOf(adjustments, index))
        }
    }

    /** All the color values that the video uses at some time: the usual colors and each change point. */
    fun allColors(adjustments: AdjustmentsV1): List<AdjustmentsV1> =
        (-1 until adjustments.colorKeys.size).map { valuesOf(adjustments, it) }

    /**
     * The pieces of the source span [startMs, endMs) for the export: a fade for each part of a blend in the span,
     * and holds between them. A fade that the span cuts starts and ends at the colors of the cut.
     */
    fun pieces(adjustments: AdjustmentsV1, startMs: Long, endMs: Long): List<Piece> {
        val result = mutableListOf<Piece>()
        var cursor = startMs
        for (blend in blends(adjustments).filter { it.startMs < endMs && it.endMs > startMs }) {
            val fadeStart = maxOf(cursor, blend.startMs)
            val fadeEnd = minOf(endMs, blend.endMs)
            if (fadeStart > cursor) result += Piece.Hold(cursor, fadeStart, at(adjustments, cursor))
            result += Piece.Fade(fadeStart, fadeEnd, blend.colorsAt(fadeStart), blend.colorsAt(fadeEnd))
            cursor = fadeEnd
        }
        if (cursor < endMs) result += Piece.Hold(cursor, endMs, at(adjustments, cursor))
        return result
    }

    /** The colors [amount] (0 to 1) of the way from [from] to [to]. The other values come from [from]. */
    fun lerp(from: AdjustmentsV1, to: AdjustmentsV1, amount: Double): AdjustmentsV1 {
        fun mix(a: Float, b: Float) = (a + (b - a) * amount).toFloat()
        return from.copy(
            brightness = mix(from.brightness, to.brightness),
            contrast = mix(from.contrast, to.contrast),
            saturation = mix(from.saturation, to.saturation),
            shadows = mix(from.shadows, to.shadows),
            highlights = mix(from.highlights, to.highlights),
            whiteBalance = WhiteBalanceV1(mix(temperatureOf(from), temperatureOf(to))),
        )
    }

    private fun temperatureOf(adjustments: AdjustmentsV1) = adjustments.whiteBalance?.temperature ?: 0.0f

    private fun withColors(target: AdjustmentsV1, colors: AdjustmentsV1) = target.copy(
        brightness = colors.brightness,
        contrast = colors.contrast,
        saturation = colors.saturation,
        shadows = colors.shadows,
        highlights = colors.highlights,
        whiteBalance = colors.whiteBalance,
    )

    private fun keyOf(atMs: Long, colors: AdjustmentsV1) = ColorKeyV1(
        atMs = atMs,
        brightness = colors.brightness,
        contrast = colors.contrast,
        saturation = colors.saturation,
        shadows = colors.shadows,
        highlights = colors.highlights,
        temperature = temperatureOf(colors),
    )
}
