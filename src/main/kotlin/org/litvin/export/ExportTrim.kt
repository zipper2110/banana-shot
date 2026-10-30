package org.litvin.export

import org.litvin.CommentOverlaySpan
import org.litvin.OverlaySpan
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.TrimRange

/**
 * The trim of a "Full video" export (B-62). The export uses only the source between [startMs] and [endMs]. The
 * video timeline of the export starts at [startMs], so the scoreboard and the comments move back by [startMs].
 * The "Only points" and "Only favorites" exports do not use the trim.
 */
data class ExportTrim(val startMs: Long, val endMs: Long?) {
    /** The length of the trimmed video, or null when the trim has no end and the source length is not known. */
    fun durationMs(sourceDurationMs: Long?): Long? = (endMs ?: sourceDurationMs)?.let { (it - startMs).coerceAtLeast(0L) }

    /** True when a point that ends at [pointEndMs] is inside the trim. Only its replays play in the export. */
    fun keepsReplayAt(pointEndMs: Long): Boolean = pointEndMs > startMs && (endMs == null || pointEndMs <= endMs)

    /** The scoreboard spans on the trimmed video timeline. A span outside the trim goes away. */
    fun overlays(spans: List<OverlaySpan>): List<OverlaySpan> = spans.mapNotNull { span ->
        shift(span.startMs, span.endMs)?.let { (start, end) -> span.copy(startMs = start, endMs = end) }
    }

    /** The comment spans on the trimmed video timeline. A comment outside the trim goes away. */
    fun comments(spans: List<CommentOverlaySpan>): List<CommentOverlaySpan> = spans.mapNotNull { span ->
        shift(span.startMs, span.endMs)?.let { (start, end) -> span.copy(startMs = start, endMs = end) }
    }

    private fun shift(fromMs: Long, toMs: Long): Pair<Long, Long>? {
        val start = maxOf(fromMs, startMs) - startMs
        val end = minOf(toMs, endMs ?: Long.MAX_VALUE) - startMs
        return if (end > start) start to end else null
    }

    companion object {
        /**
         * The trim of [adjustments] for a full video export, or null without a trim. [sourceDurationMs] limits the
         * end when it is known. An end at or after the source end is the source end.
         */
        fun of(adjustments: AdjustmentsV1, sourceDurationMs: Long?): ExportTrim? {
            if (!TrimRange.isSet(adjustments)) return null
            if (sourceDurationMs != null && sourceDurationMs > 0) {
                val range = TrimRange.of(adjustments, sourceDurationMs)
                return ExportTrim(range.startMs, range.endMs.takeIf { it < sourceDurationMs })
            }
            return ExportTrim(adjustments.trimStartMs ?: 0L, adjustments.trimEndMs)
        }
    }
}
