package org.litvin.export

import org.litvin.points.PointV1
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo

/**
 * Puts the encode passes of one export in output order: the video chunks, the replays after their points (B-50),
 * the set cards after the last exported point of each set, and the match card at the end.
 *
 * A card pass shows a frozen frame of the source. A replay pass shows one play of a replay part. The scoreboard
 * and the comments stay on the video timeline, so [ExportChunkPlanner.Chunk.outputOffsetMs] does not include the
 * cards and the replays.
 */
object ExportPassPlanner {
    sealed interface Pass {
        /** Output time where this pass starts, with the cards and the replays before it. The progress bar uses it. */
        val progressBaseMs: Long
    }

    /** A video chunk. A chunk before a replay fades out, and a chunk after a replay fades in. */
    data class Video(
        val chunk: ExportChunkPlanner.Chunk,
        override val progressBaseMs: Long,
        val fadeIn: PassFade? = null,
        val fadeOut: PassFade? = null,
    ) : Pass

    data class Card(
        val card: StatsCardVideo,
        /** Source time just after the frozen frame, or null for the end of the source. */
        val freezeAtMs: Long?,
        override val progressBaseMs: Long,
    ) : Pass

    /** The play of a replay. */
    data class Replay(
        val replay: ExportReplay,
        override val progressBaseMs: Long,
        val fadeIn: PassFade?,
        val fadeOut: PassFade?,
    ) : Pass {
        val durationMs: Long get() = replay.playMs
    }

    /**
     * Plans the passes. Without [keeps] the export is the full video: one video pass, and no set cards. With
     * [replays], a full video export splits the video at the end of each point with replays.
     * [fullVideoDurationMs] is the source duration for a full video export, when it is known.
     */
    fun plan(
        keeps: List<PointV1>,
        setSummaries: List<SetSummaryCard> = emptyList(),
        endCard: StatsCardVideo? = null,
        fullVideoDurationMs: Long? = null,
        maxSegmentsPerChunk: Int = ExportChunkPlanner.maxSegmentsPerChunk(),
        replays: List<ExportReplay> = emptyList(),
    ): List<Pass> {
        if (keeps.isEmpty()) return planFullVideo(endCard, fullVideoDurationMs, replays)
        val builder = Builder(maxSegmentsPerChunk)

        // The set cards after each kept point. A set without a kept point has no card.
        val cardsAfter = setSummaries
            .sortedBy { it.setNumber }
            .mapNotNull { summary ->
                val last = keeps.indexOfLast { it.id in summary.pointIds }
                if (last < 0) null else last to summary.card
            }
            .groupBy({ it.first }, { it.second })
        val replaysAfter = replays
            .mapNotNull { replay -> keeps.indexOfFirst { it.id == replay.pointId }.takeIf { it >= 0 }?.let { it to replay } }
            .groupBy({ it.first }, { it.second })

        var groupStart = 0
        for (last in (cardsAfter.keys + replaysAfter.keys).toSortedSet()) {
            val pointReplays = replaysAfter[last].orEmpty()
            builder.addVideo(keeps.subList(groupStart, last + 1), pointReplays.firstOrNull())
            groupStart = last + 1
            pointReplays.forEach(builder::addReplay)
            // The cards come after the replays of the last point of the set (decision 11).
            cardsAfter[last]?.forEach { builder.addCard(it, keeps[last].endMs.toLong()) }
        }
        builder.addVideo(keeps.subList(groupStart, keeps.size), null)
        endCard?.let { builder.addCard(it, keeps.last().endMs.toLong()) }
        return builder.passes
    }

    /**
     * The full video. Without replays it is one pass. With replays, each part of the source between two point ends
     * with replays is one chunk, so the replays can go between the chunks.
     */
    private fun planFullVideo(endCard: StatsCardVideo?, durationMs: Long?, replays: List<ExportReplay>): List<Pass> {
        val passes = mutableListOf<Pass>()
        if (replays.isEmpty()) {
            passes += Video(ExportChunkPlanner.Chunk(emptyList(), 0L), 0L)
            endCard?.let { passes += Card(it, freezeAtMs = null, progressBaseMs = durationMs ?: 0L) }
            return passes
        }
        val builder = Builder(maxSegmentsPerChunk = 1)
        val sourceEnd = durationMs?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: UNKNOWN_END_MS
        var rangeStart = 0
        for ((pointEnd, pointReplays) in replays.groupBy { it.pointEndMs }.toSortedMap()) {
            if (pointEnd > sourceEnd) continue
            builder.addVideo(listOf(range(rangeStart, pointEnd)), pointReplays.first())
            rangeStart = pointEnd
            pointReplays.forEach(builder::addReplay)
        }
        if (rangeStart < sourceEnd) builder.addVideo(listOf(range(rangeStart, sourceEnd)), null)
        endCard?.let { builder.addCard(it, freezeAtMs = null) }
        return builder.passes
    }

    /** A part of the source as a kept segment, so that the pass seeks to it. */
    private fun range(startMs: Int, endMs: Int) = PointV1(id = "range-$startMs", startMs = startMs, endMs = endMs)

    /** The end of the source when its duration is not known. ffmpeg stops at the end of the file. */
    private const val UNKNOWN_END_MS = Int.MAX_VALUE / 2

    private class Builder(private val maxSegmentsPerChunk: Int) {
        val passes = mutableListOf<Pass>()
        private var videoMs = 0L
        private var extraMs = 0L

        /** The fade in of the next video chunk: the last pass was a replay. */
        private var pendingFadeIn: PassFade? = null

        /** Adds [keeps] as chunks. With [nextReplay], the last chunk fades out into that replay. */
        fun addVideo(keeps: List<PointV1>, nextReplay: ExportReplay?) {
            if (keeps.isEmpty()) return
            val groups = keeps.chunked(maxSegmentsPerChunk.coerceAtLeast(1))
            groups.forEachIndexed { index, group ->
                val fadeIn = if (index == 0) pendingFadeIn else null
                val fadeOut = if (index == groups.lastIndex) nextReplay?.let { ExportReplays.cutFade(it.replay.effect) } else null
                passes += Video(ExportChunkPlanner.Chunk(group, videoMs), videoMs + extraMs, fadeIn, fadeOut)
                videoMs += ExportChunkPlanner.keptDurationMs(group)
            }
            pendingFadeIn = null
        }

        /** Adds the play of [replay]. The effect of the replay is at its start and its end. */
        fun addReplay(replay: ExportReplay) {
            val cut = ExportReplays.cutFade(replay.replay.effect)
            passes += Replay(replay, videoMs + extraMs, cut, cut)
            extraMs += replay.playMs
            pendingFadeIn = cut
        }

        fun addCard(card: StatsCardVideo, freezeAtMs: Long?) {
            passes += Card(card, freezeAtMs, videoMs + extraMs)
            extraMs += card.durationMs
            pendingFadeIn = null
        }
    }

    /** The duration of all cards in [passes]. */
    fun cardDurationMs(passes: List<Pass>): Long = passes.filterIsInstance<Card>().sumOf { it.card.durationMs }

    /** The duration of all cards and replays in [passes]: the output time that is not on the video timeline. */
    fun extraDurationMs(passes: List<Pass>): Long =
        cardDurationMs(passes) + passes.filterIsInstance<Replay>().sumOf { it.durationMs }
}
