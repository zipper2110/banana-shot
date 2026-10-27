package org.litvin.ui.tabs.points

import org.litvin.points.CommentV1
import org.litvin.points.ReplayV1
import org.litvin.points.Replays

/**
 * The comments of the replay that plays now (decision 24). The player gives the source time. During a still frame, the
 * source time stops, so this clock counts the time from the start of the still frame. The Points tab and the preview of
 * the replay editor use it.
 */
internal class ReplayCommentClock(private val nanoTime: () -> Long = System::nanoTime) {
    private var holdStartNanos: Long? = null

    /** The player stopped on the still frame now. */
    fun holdStarted() {
        holdStartNanos = nanoTime()
    }

    fun holdEnded() {
        holdStartNanos = null
    }

    /** The time from the play start of [replay] at [sourceMs]. [holding] is true during the still frame. */
    fun playOffsetMs(replay: ReplayV1, sourceMs: Long, holding: Boolean): Long {
        val freezeAt = Replays.freezeOffsetMs(replay)
        val start = holdStartNanos
        if (holding && freezeAt != null && start != null) {
            val elapsed = ((nanoTime() - start) / NANOS_PER_MS).coerceIn(0L, Replays.freezeMs(replay))
            return freezeAt + elapsed
        }
        return Replays.playOffsetAt(replay, sourceMs)
    }

    /** The comments of [replay] on screen at [sourceMs]. */
    fun comments(replay: ReplayV1, sourceMs: Long, holding: Boolean): List<CommentV1> =
        if (replay.comments.isEmpty()) emptyList() else Replays.commentsAt(replay, playOffsetMs(replay, sourceMs, holding))

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
