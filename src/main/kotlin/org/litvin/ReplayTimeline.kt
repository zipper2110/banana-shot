package org.litvin.points

import kotlin.math.roundToLong

/**
 * The play order of a project with instant replays (B-50 T2): the source video, with the replays of each point
 * inserted after the end of the point, and each repeat of a replay after the other.
 *
 * "Play time" counts the source video and the replays. "Source time" is the time in the video file.
 * Before the first insert, the two times are the same. The source time of a point end is after its replays:
 * the video continues there when the replays end.
 */
class ReplayTimeline(points: List<PointV1>) {
    /** One play of a replay part: one repeat. [startMs] is in play time. */
    data class Play(
        val pointId: String,
        val replayIndex: Int,
        val repeat: Int,
        val replay: ReplayV1,
        val startMs: Long,
        val durationMs: Long,
    ) {
        val endMs: Long get() = startMs + durationMs
        val isFirst: Boolean get() = repeat == 0
        val isLast: Boolean get() = repeat == replay.repeats - 1

        /** The source time of the frame at [playMs] of this play. */
        fun sourceAt(playMs: Long): Long {
            val offset = (playMs - startMs).coerceIn(0L, durationMs)
            return (replay.startMs + offset * replay.speed).roundToLong().coerceAtMost(replay.endMs.toLong())
        }

        /** The play time of the frame at [sourceMs] of the part. */
        fun playAt(sourceMs: Long): Long {
            val offset = (sourceMs - replay.startMs).coerceIn(0L, (replay.endMs - replay.startMs).toLong())
            return startMs + (offset / replay.speed).roundToLong().coerceAtMost(durationMs)
        }
    }

    /** All replays of one point, inserted at the end of the point. [startMs] is in play time. */
    data class Insert(val point: PointV1, val startMs: Long, val plays: List<Play>) {
        val atSourceMs: Long get() = point.endMs.toLong()
        val durationMs: Long get() = plays.sumOf { it.durationMs }
        val endMs: Long get() = startMs + durationMs
    }

    /** A place in play time. */
    sealed interface Position {
        val sourceMs: Long

        data class Source(override val sourceMs: Long) : Position

        data class InReplay(val insert: Insert, val play: Play, val playMs: Long) : Position {
            override val sourceMs: Long get() = play.sourceAt(playMs)
        }
    }

    val inserts: List<Insert>

    init {
        var added = 0L
        inserts = points
            .filter { it.replays.isNotEmpty() && it.endMs > it.startMs }
            .sortedBy { it.endMs }
            .map { point ->
                val insertStart = point.endMs + added
                var playStart = insertStart
                val plays = point.replays.flatMapIndexed { index, replay ->
                    val one = Replays.playMs(replay)
                    (0 until replay.repeats).map { repeat ->
                        Play(point.id, index, repeat, replay, playStart, one).also { playStart += one }
                    }
                }
                Insert(point, insertStart, plays).also { added += it.durationMs }
            }
    }

    /** The time that all replays add to the video. */
    val addedMs: Long get() = inserts.sumOf { it.durationMs }

    val isEmpty: Boolean get() = inserts.isEmpty()

    /** The play time of a video of [sourceDurationMs] with the replays. */
    fun totalMs(sourceDurationMs: Long): Long = sourceDurationMs + addedMs

    /** The play time of the source frame at [sourceMs]. A point end is after its replays. */
    fun playMsAtSource(sourceMs: Long): Long =
        sourceMs + inserts.asSequence().takeWhile { it.atSourceMs <= sourceMs }.sumOf { it.durationMs }

    /** The place at [playMs] of the play time. */
    fun positionAt(playMs: Long): Position {
        var added = 0L
        for (insert in inserts) {
            if (playMs < insert.startMs) return Position.Source(playMs - added)
            if (playMs < insert.endMs) {
                val play = insert.plays.first { playMs < it.endMs }
                return Position.InReplay(insert, play, playMs)
            }
            added += insert.durationMs
        }
        return Position.Source((playMs - added).coerceAtLeast(0L))
    }

    /** The insert of the point that ends in (fromSourceMs, toSourceMs], or null. A player uses it while it plays. */
    fun insertCrossed(fromSourceMs: Long, toSourceMs: Long): Insert? =
        inserts.firstOrNull { it.atSourceMs > fromSourceMs && it.atSourceMs <= toSourceMs }

    /** The insert of the point with [pointId], or null. */
    fun insertOf(pointId: String): Insert? = inserts.firstOrNull { it.point.id == pointId }

    companion object {
        val EMPTY = ReplayTimeline(emptyList())
    }
}
