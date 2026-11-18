package org.litvin.export.preview

import org.litvin.RenderJob
import org.litvin.export.ExportPassPlanner
import org.litvin.export.ExportReplay
import org.litvin.export.ExportTrim
import org.litvin.export.PassFade
import org.litvin.stats.StatsCardVideo

/**
 * The output of an export as a list of items in output order (B-75): the video parts, the replays, and the cards.
 *
 * The items come from [ExportPassPlanner], with the same input as the export ([RenderJob]). Thus the preview and the
 * export cannot have different rules for the content.
 *
 * "Output time" counts all items. "Overlay time" is the video timeline of the export: the video items without the
 * replays and the cards. The scoreboard and the comments of the export ([RenderJob.overlayTimeline],
 * [RenderJob.commentOverlayTimeline]) use the overlay time.
 */
class ExportPreviewTimeline(val items: List<Item>) {
    sealed interface Item {
        /** The output time where the item starts. */
        val outStartMs: Long
        val durationMs: Long
        val outEndMs: Long get() = outStartMs + durationMs
    }

    /**
     * A part of the source video: a kept point, or a part of the full video. [pointId] is null for a part of the full
     * video. [fadeIn] and [fadeOut] are the dips at a cut from a replay and into a replay.
     */
    data class Video(
        override val outStartMs: Long,
        val sourceStartMs: Long,
        val sourceEndMs: Long,
        val overlayStartMs: Long,
        val pointId: String?,
        val fadeIn: PassFade? = null,
        val fadeOut: PassFade? = null,
    ) : Item {
        override val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)

        /** The output time of the source time [sourceMs] of this item. */
        fun outMsAt(sourceMs: Long): Long = outStartMs + (sourceMs - sourceStartMs).coerceIn(0L, durationMs)

        /** The source time at [outMs] of the output time. */
        fun sourceMsAt(outMs: Long): Long = sourceStartMs + (outMs - outStartMs).coerceIn(0L, durationMs)

        /** The overlay time at the source time [sourceMs] of this item. */
        fun overlayMsAt(sourceMs: Long): Long = overlayStartMs + (sourceMs - sourceStartMs).coerceIn(0L, durationMs)
    }

    /** One play of a replay, after the end of its point. */
    data class Replay(override val outStartMs: Long, val replay: ExportReplay) : Item {
        override val durationMs: Long get() = replay.playMs
    }

    /**
     * A statistics card on a frozen frame. [freezeAtMs] is the source time just after the frozen frame, or null for the
     * end of the source. [setNumber] is the set of a set card, or null for the statistics card of the match.
     */
    data class Card(
        override val outStartMs: Long,
        val card: StatsCardVideo,
        val freezeAtMs: Long?,
        val setNumber: Int?,
    ) : Item {
        override val durationMs: Long get() = card.durationMs
    }

    /** The output time of all items. */
    val durationMs: Long get() = items.lastOrNull()?.outEndMs ?: 0L

    /** True when the output has a cut: a jump in the source between two items (decision 10 of B-75). */
    val hasCuts: Boolean get() = items.size > 1

    /** The index of the item at [outMs]. The end of the output is in the last item. Returns -1 without items. */
    fun indexAt(outMs: Long): Int {
        if (items.isEmpty()) return -1
        val index = items.indexOfFirst { outMs < it.outEndMs }
        return if (index < 0) items.lastIndex else index
    }

    /** The start of the item after the item at [outMs], or null at the last item. */
    fun nextStartMs(outMs: Long): Long? = items.getOrNull(indexAt(outMs) + 1)?.outStartMs

    /**
     * The start of the item before [outMs]. Inside an item, after its first [RESTART_MS], it is the start of the same
     * item, as the previous track of a music player.
     */
    fun previousStartMs(outMs: Long): Long? {
        val index = indexAt(outMs)
        val item = items.getOrNull(index) ?: return null
        if (outMs - item.outStartMs > RESTART_MS) return item.outStartMs
        return items.getOrNull(index - 1)?.outStartMs
    }

    companion object {
        /** A jump back inside the first part of an item goes to the item before it. */
        const val RESTART_MS = 1_500L

        /**
         * The items of [job]. [sourceDurationMs] is the duration of the source video. A full video export needs it for
         * the end of the video.
         */
        fun of(job: RenderJob, sourceDurationMs: Long?): ExportPreviewTimeline {
            val keeps = if (job.idleTrim) job.edlSnapshot else emptyList()
            val trim = if (keeps.isEmpty()) ExportTrim.of(job.adjustments, sourceDurationMs) else null
            val passes = ExportPassPlanner.plan(
                keeps = keeps,
                setSummaries = job.setSummaries,
                endCard = job.statsCard,
                fullVideoDurationMs = sourceDurationMs,
                replays = job.replays,
                trim = trim,
            )
            val items = mutableListOf<Item>()
            for (pass in passes) {
                when (pass) {
                    is ExportPassPlanner.Video -> items += videoItems(pass, keeps.isEmpty(), trim, sourceDurationMs)
                    is ExportPassPlanner.Replay -> items += Replay(pass.progressBaseMs, pass.replay)
                    is ExportPassPlanner.Card -> items += Card(
                        pass.progressBaseMs,
                        pass.card,
                        pass.freezeAtMs,
                        job.setSummaries.firstOrNull { it.card === pass.card }?.setNumber,
                    )
                }
            }
            return ExportPreviewTimeline(items.filter { it.durationMs > 0 })
        }

        /**
         * The video items of one pass: one item for each kept point. A pass of the full video without replays and
         * without a trim has no segments: it is the whole source.
         */
        private fun videoItems(
            pass: ExportPassPlanner.Video,
            fullVideo: Boolean,
            trim: ExportTrim?,
            sourceDurationMs: Long?,
        ): List<Video> {
            val chunk = pass.chunk
            if (chunk.keeps.isEmpty()) {
                val end = sourceDurationMs ?: 0L
                return listOf(Video(pass.progressBaseMs, 0L, end, 0L, null))
            }
            // The overlays of a full video export start at the trim start.
            val trimStart = trim?.startMs ?: 0L
            var outMs = pass.progressBaseMs
            var overlayMs = chunk.outputOffsetMs
            return chunk.keeps.mapIndexed { index, keep ->
                val start = keep.startMs.toLong()
                val end = if (fullVideo && sourceDurationMs != null) minOf(keep.endMs.toLong(), sourceDurationMs) else keep.endMs.toLong()
                Video(
                    outStartMs = outMs,
                    sourceStartMs = start,
                    sourceEndMs = end,
                    overlayStartMs = if (fullVideo) start - trimStart else overlayMs,
                    pointId = if (fullVideo) null else keep.id,
                    fadeIn = pass.fadeIn.takeIf { index == 0 },
                    fadeOut = pass.fadeOut.takeIf { index == chunk.keeps.lastIndex },
                ).also {
                    outMs += it.durationMs
                    overlayMs += it.durationMs
                }
            }
        }
    }
}
