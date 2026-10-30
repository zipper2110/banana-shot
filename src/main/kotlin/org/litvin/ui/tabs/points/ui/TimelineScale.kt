package org.litvin.ui.tabs.points.ui

import org.litvin.adjustments.TrimRange
import org.litvin.points.ReplayTimeline
import kotlin.math.max

/**
 * The horizontal scale of the Points timeline with replays (B-50 T5). The timeline shows play time: the source
 * video, and the replays of each point as blocks after the end of the point. The ruler and the marks use source
 * time, so this scale converts between the two times.
 *
 * With a [trim] (B-62), the timeline shows only the source between the trim start and the trim end, and the
 * replays of the points that end in it. The play times stay the play times of the full source; only the x
 * positions use the window.
 */
internal class TimelineScale(val replays: ReplayTimeline, sourceTotalMs: Long, val trim: TrimRange? = null) {
    /** One replay as a block in play time. */
    data class Block(val pointId: String, val replayIndex: Int, val startMs: Long, val endMs: Long)

    val sourceTotalMs: Long = max(1L, sourceTotalMs)

    /** The first and the last source time that the timeline shows. */
    val sourceStartMs: Long = trim?.startMs?.coerceIn(0L, this.sourceTotalMs - 1) ?: 0L
    val sourceEndMs: Long = trim?.endMs?.coerceIn(sourceStartMs + 1, this.sourceTotalMs) ?: this.sourceTotalMs

    /** The replays that the timeline shows: the replays of the points that end inside the window. */
    val inserts: List<ReplayTimeline.Insert> =
        replays.inserts.filter { it.atSourceMs > sourceStartMs && it.atSourceMs <= sourceEndMs }

    /** The play time at the left edge. The replays of a point that ends at the trim start are not in the window. */
    private val playStartMs: Long =
        sourceStartMs + replays.inserts.filter { it.atSourceMs <= sourceStartMs }.sumOf { it.durationMs }

    /** The play time at the right edge: the replays of a point that ends at the trim end are still in the window. */
    private val playEndMs: Long = sourceEndMs + replays.inserts.filter { it.atSourceMs <= sourceEndMs }.sumOf { it.durationMs }

    /** The length of the window in play time. Without a trim, it is the source with all replays. */
    val playTotalMs: Long = max(1L, playEndMs - playStartMs)

    val blocks: List<Block> = inserts.flatMap { insert ->
        insert.plays.map { play ->
            Block(insert.point.id, play.replayIndex, play.startMs, play.endMs)
        }
    }

    /**
     * The play time of the source frame at [sourceMs]. A point end maps to the start of its replay block, so that
     * the block comes right after the mark of the point.
     */
    fun playAt(sourceMs: Long): Long =
        sourceMs + replays.inserts.asSequence().takeWhile { it.atSourceMs < sourceMs }.sumOf { it.durationMs }

    /** The source time at [playMs]. A time inside a replay block maps to the end of its point. */
    fun sourceAt(playMs: Long): Long = when (val position = replays.positionAt(playMs.coerceIn(playStartMs, playEndMs))) {
        is ReplayTimeline.Position.InReplay -> position.insert.atSourceMs
        is ReplayTimeline.Position.Source -> position.sourceMs
    }.coerceIn(sourceStartMs, sourceEndMs)

    /** The x of [playMs] on a track of [width] pixels that starts at [left]. */
    fun xAtPlay(playMs: Long, left: Int, width: Int): Int =
        left + ((playMs - playStartMs).toDouble() * width / playTotalMs).toInt()

    fun xAtSource(sourceMs: Long, left: Int, width: Int): Int = xAtPlay(playAt(sourceMs), left, width)

    /** The play time at [x] on a track of [width] pixels that starts at [left]. */
    fun playAtX(x: Int, left: Int, width: Int): Long =
        (playStartMs + (x - left).toDouble() / max(1, width) * playTotalMs).toLong().coerceIn(playStartMs, playEndMs)

    /** The play time of the left edge, for the first part of the video track. */
    val firstPlayMs: Long get() = playStartMs
}
