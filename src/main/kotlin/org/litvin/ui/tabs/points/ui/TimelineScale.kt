package org.litvin.ui.tabs.points.ui

import org.litvin.points.ReplayTimeline
import kotlin.math.max

/**
 * The horizontal scale of the Points timeline with replays (B-50 T5). The timeline shows play time: the source
 * video, and the replays of each point as blocks after the end of the point. The ruler and the marks use source
 * time, so this scale converts between the two times.
 */
internal class TimelineScale(val replays: ReplayTimeline, sourceTotalMs: Long) {
    /** One replay as a block in play time: all its repeats. */
    data class Block(val pointId: String, val replayIndex: Int, val startMs: Long, val endMs: Long)

    val sourceTotalMs: Long = max(1L, sourceTotalMs)
    val playTotalMs: Long = this.sourceTotalMs + replays.addedMs

    val blocks: List<Block> = replays.inserts.flatMap { insert ->
        insert.plays.groupBy { it.replayIndex }.map { (index, plays) ->
            Block(insert.point.id, index, plays.minOf { it.startMs }, plays.maxOf { it.endMs })
        }
    }

    /**
     * The play time of the source frame at [sourceMs]. A point end maps to the start of its replay block, so that
     * the block comes right after the mark of the point.
     */
    fun playAt(sourceMs: Long): Long =
        sourceMs + replays.inserts.asSequence().takeWhile { it.atSourceMs < sourceMs }.sumOf { it.durationMs }

    /** The source time at [playMs]. A time inside a replay block maps to the end of its point. */
    fun sourceAt(playMs: Long): Long = when (val position = replays.positionAt(playMs.coerceIn(0L, playTotalMs))) {
        is ReplayTimeline.Position.InReplay -> position.insert.atSourceMs
        is ReplayTimeline.Position.Source -> position.sourceMs
    }.coerceIn(0L, sourceTotalMs)

    /** The x of [playMs] on a track of [width] pixels that starts at [left]. */
    fun xAtPlay(playMs: Long, left: Int, width: Int): Int = left + (playMs.toDouble() * width / playTotalMs).toInt()

    fun xAtSource(sourceMs: Long, left: Int, width: Int): Int = xAtPlay(playAt(sourceMs), left, width)

    /** The play time at [x] on a track of [width] pixels that starts at [left]. */
    fun playAtX(x: Int, left: Int, width: Int): Long =
        ((x - left).toDouble() / max(1, width) * playTotalMs).toLong().coerceIn(0L, playTotalMs)
}
