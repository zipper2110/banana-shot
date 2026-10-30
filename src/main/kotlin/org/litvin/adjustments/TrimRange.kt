package org.litvin.adjustments

/**
 * The part of the source between the trim start and the trim end (B-60), in source milliseconds.
 * [endMs] is after [startMs].
 */
data class TrimRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs

    operator fun contains(ms: Long): Boolean = ms in startMs..endMs

    companion object {
        /** The shortest trimmed video. */
        const val MIN_LENGTH_MS = 1_000L

        /** True when [adjustments] cut off the start or the end of the source. */
        fun isSet(adjustments: AdjustmentsV1): Boolean = adjustments.trimStartMs != null || adjustments.trimEndMs != null

        /**
         * The trim of [adjustments] on a source of [sourceDurationMs]. Without a trim, the range is the full source.
         * A source of 0 ms or less gives a range of 1 ms, so that a timeline can still divide by the length.
         */
        fun of(adjustments: AdjustmentsV1, sourceDurationMs: Long): TrimRange {
            val total = sourceDurationMs.coerceAtLeast(1L)
            val start = (adjustments.trimStartMs ?: 0L).coerceIn(0L, total - 1)
            val end = (adjustments.trimEndMs ?: total).coerceIn(start + 1, total)
            return TrimRange(start, end)
        }

        /**
         * The points that the trim of [adjustments] cuts off fully or in part. The app keeps them (B-60).
         * The end of the source is not needed: without a trim end, no point is after the end.
         */
        fun <T> outside(adjustments: AdjustmentsV1, points: List<T>, startOf: (T) -> Long, endOf: (T) -> Long): List<T> {
            val start = adjustments.trimStartMs ?: 0L
            val end = adjustments.trimEndMs ?: Long.MAX_VALUE
            return points.filter { startOf(it) < start || endOf(it) > end }
        }

        /** The stored start: null for the start of the source. */
        fun cleanStart(startMs: Long?): Long? = startMs?.takeIf { it > 0L }

        /** The stored end: null when it is not after the start. The source length is not known here. */
        fun cleanEnd(startMs: Long?, endMs: Long?): Long? = endMs?.takeIf { it > (cleanStart(startMs) ?: 0L) }
    }
}
