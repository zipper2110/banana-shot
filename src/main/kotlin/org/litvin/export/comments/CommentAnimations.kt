package org.litvin.export.comments

import org.litvin.points.CommentAnimation
import org.litvin.points.CommentV1
import org.litvin.points.ReplayV1
import org.litvin.points.Replays
import kotlin.math.roundToInt

/**
 * The time of a comment on screen: [elapsedMs] from the moment that it comes on screen, of [durationMs] in total.
 * The times are video times: a slow replay makes the duration of a point comment longer, as in the export.
 */
data class CommentClock(val elapsedMs: Long, val durationMs: Long)

/** How [CommentAss] draws the [CommentAnimation] of a comment. */
sealed interface CommentMotion {
    /** No animation: the comment shows in its rest state. The editing views and the dialog preview use it. */
    data object Still : CommentMotion

    /**
     * The export: each event animates with its own ASS tags. The tag times are relative to the event start, and the
     * event shows for [durationMs]. So the animation starts when the comment comes on screen in the output video.
     */
    data class Timed(val animation: CommentAnimation, val durationMs: Long) : CommentMotion

    /**
     * The preview: the events show the animation state at one moment, without timing tags. An mpv `osd-overlay` has no
     * event times, so the preview sends a new overlay at each new state.
     */
    data class Frozen(val animation: CommentAnimation, val clock: CommentClock) : CommentMotion
}

/**
 * The times and the states of the comment animations. The export ([CommentMotion.Timed]) and the preview
 * ([CommentMotion.Frozen]) use the same times, and the frozen state is the state that libass computes from the tags at
 * that time. So the preview shows the same animation as the export.
 */
object CommentAnimations {
    /** The time of a fade in and of a fade out. */
    const val FADE_MS = 300L

    /**
     * The state of a comment at one moment. [opacity] (0 to 1) multiplies the opacity of all parts of the comment.
     * The opacity is in steps of 1/255, the step of an ASS alpha value, so a preview sends a new overlay only for a
     * visible change.
     */
    data class Frame(val opacity: Double = 1.0) {
        companion object {
            val REST = Frame()
        }
    }

    /** The fade in time and the fade out time of a comment that shows for [durationMs]. A short comment gets shorter fades. */
    fun fadeMs(durationMs: Long): Pair<Long, Long> {
        val each = FADE_MS.coerceAtMost(durationMs.coerceAtLeast(0L) / 2)
        return each to each
    }

    /** The state of [animation] at [clock]. */
    fun frame(animation: CommentAnimation, clock: CommentClock): Frame = when (animation) {
        CommentAnimation.NONE -> Frame.REST
        CommentAnimation.FADE -> {
            val (fadeIn, fadeOut) = fadeMs(clock.durationMs)
            val elapsed = clock.elapsedMs.coerceIn(0L, clock.durationMs.coerceAtLeast(0L))
            val remaining = clock.durationMs - elapsed
            // The same steps as libass \fad: linear in time, from transparent to opaque and back.
            val opacity = when {
                fadeIn > 0 && elapsed < fadeIn -> elapsed.toDouble() / fadeIn
                fadeOut > 0 && remaining < fadeOut -> remaining.toDouble() / fadeOut
                else -> 1.0
            }
            Frame(step(opacity))
        }
    }

    /** The state of [motion]. A still or timed motion is at rest: the tags of a timed motion animate it. */
    fun frame(motion: CommentMotion): Frame = when (motion) {
        CommentMotion.Still, is CommentMotion.Timed -> Frame.REST
        is CommentMotion.Frozen -> frame(motion.animation, motion.clock)
    }

    /**
     * [events] with the timing tags of [motion]. All events of one comment get the same tags, so the shadows, the
     * backdrops, and the texts animate together. \fad multiplies the opacity of each color, so the opacity and the
     * transparent parts stay correct.
     */
    fun timed(events: List<String>, motion: CommentMotion): List<String> {
        if (motion !is CommentMotion.Timed) return events
        val tags = when (motion.animation) {
            CommentAnimation.NONE -> return events
            CommentAnimation.FADE -> {
                val (fadeIn, fadeOut) = fadeMs(motion.durationMs)
                if (fadeIn <= 0 && fadeOut <= 0) return events
                "{\\fad($fadeIn,$fadeOut)}"
            }
        }
        return events.map { tags + it }
    }

    /**
     * The motion of a preview comment at [clock]. A null clock or no animation is still. A clock outside the time of the
     * comment is also still: only a drag or the open comment dialog shows a comment outside its time.
     */
    fun preview(comment: CommentV1, clock: CommentClock?): CommentMotion = when {
        clock == null || comment.animation == CommentAnimation.NONE -> CommentMotion.Still
        clock.elapsedMs < 0 || clock.elapsedMs >= clock.durationMs -> CommentMotion.Still
        else -> CommentMotion.Frozen(comment.animation, clock)
    }

    /**
     * The animation states of preview [comments] with their [clocks] (in the same order, a missing clock is null).
     * A preview sends a new overlay when this list changes.
     */
    fun previewFrames(comments: List<CommentV1>, clocks: List<CommentClock?>): List<Frame> =
        comments.mapIndexed { index, comment -> frame(preview(comment, clocks.getOrNull(index))) }

    /** The clock of a point comment at [sourceMs] of the source video, outside a replay. */
    fun sourceClock(comment: CommentV1, sourceMs: Long): CommentClock =
        CommentClock(sourceMs - comment.startMs, comment.durationMs.toLong())

    /**
     * The clock of a point comment during a play of [replay], at [playOffsetMs] from the play start. The comment shows
     * from the part start or from its own start, and to the part end or to its own end. It is the span that the export
     * writes for the replay (`ExportReplays`).
     */
    fun replayPointClock(replay: ReplayV1, comment: CommentV1, playOffsetMs: Long): CommentClock {
        val start = Replays.playOffsetAt(replay, maxOf(comment.startMs.toLong(), replay.startMs.toLong()))
        val end = Replays.playOffsetAt(replay, minOf(comment.startMs.toLong() + comment.durationMs, replay.endMs.toLong()))
        return CommentClock(playOffsetMs - start, end - start)
    }

    /** The clock of a comment of [replay] at [playOffsetMs] from the play start. */
    fun replayCommentClock(replay: ReplayV1, comment: CommentV1, playOffsetMs: Long): CommentClock {
        val start = Replays.commentPlayStartMs(replay, comment)
        return CommentClock(playOffsetMs - start, Replays.commentPlayEndMs(replay, comment) - start)
    }

    private fun step(opacity: Double): Double = (opacity.coerceIn(0.0, 1.0) * 255).roundToInt() / 255.0
}
