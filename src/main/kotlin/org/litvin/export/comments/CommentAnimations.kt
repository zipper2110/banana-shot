package org.litvin.export.comments

import org.litvin.points.CommentAnimation
import org.litvin.points.CommentV1
import org.litvin.points.ReplayV1
import org.litvin.points.Replays
import java.util.Locale
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

    /** The time of the Pop growth and of its fade in. */
    const val POP_MS = 200L

    /** The time of the Slide up move and of its fade in. */
    const val SLIDE_MS = 350L

    /** The size of a Pop comment at its start, as a part of its size. */
    const val POP_START_SCALE = 0.5

    /** The distance of the Slide up move in a frame of 1080 lines at 100% size. It changes with the size of the comment. */
    const val SLIDE_PX = 48.0

    /**
     * The state of a comment at one moment. [opacity] (0 to 1) multiplies the opacity of all parts of the comment.
     * [scale] multiplies the size of each part around the rotation center of the part. [rise] is the part of the
     * Slide up distance that the comment is still below its place: 1 at the start, 0 in place.
     * The values are in small steps (the opacity in steps of 1/255, the step of an ASS alpha value), so a preview
     * sends a new overlay only for a visible change.
     */
    data class Frame(val opacity: Double = 1.0, val scale: Double = 1.0, val rise: Double = 0.0) {
        companion object {
            val REST = Frame()
        }
    }

    /**
     * The in time and the out time of [animation] for a comment that shows for [durationMs]. When the two times are
     * longer than the comment, both get shorter in the same ratio.
     */
    fun times(animation: CommentAnimation, durationMs: Long): Pair<Long, Long> {
        val (wantedIn, wantedOut) = when (animation) {
            CommentAnimation.NONE -> return 0L to 0L
            CommentAnimation.FADE -> FADE_MS to FADE_MS
            CommentAnimation.POP -> POP_MS to FADE_MS
            CommentAnimation.SLIDE_UP -> SLIDE_MS to FADE_MS
        }
        val duration = durationMs.coerceAtLeast(0L)
        val wanted = wantedIn + wantedOut
        if (wanted <= duration) return wantedIn to wantedOut
        return duration * wantedIn / wanted to duration * wantedOut / wanted
    }

    /**
     * The state of [animation] at [clock]. All values are linear in time between their start and end values, as
     * libass interpolates \fad, \move, and \t.
     */
    fun frame(animation: CommentAnimation, clock: CommentClock): Frame {
        if (animation == CommentAnimation.NONE) return Frame.REST
        val (inMs, outMs) = times(animation, clock.durationMs)
        val duration = clock.durationMs.coerceAtLeast(0L)
        val elapsed = clock.elapsedMs.coerceIn(0L, duration)
        val remaining = duration - elapsed
        // How far the comment came in: 0 at the start, 1 at the end of the in time.
        val entered = if (inMs > 0 && elapsed < inMs) elapsed.toDouble() / inMs else 1.0
        val opacity = when {
            entered < 1.0 -> entered
            outMs > 0 && remaining < outMs -> remaining.toDouble() / outMs
            else -> 1.0
        }
        val scale = if (animation == CommentAnimation.POP) POP_START_SCALE + (1 - POP_START_SCALE) * entered else 1.0
        val rise = if (animation == CommentAnimation.SLIDE_UP) 1.0 - entered else 0.0
        return Frame(alphaStep(opacity), fine(scale), fine(rise))
    }

    /** The state of [motion]. A still or timed motion is at rest: the tags of a timed motion animate it. */
    fun frame(motion: CommentMotion): Frame = when (motion) {
        CommentMotion.Still, is CommentMotion.Timed -> Frame.REST
        is CommentMotion.Frozen -> frame(motion.animation, motion.clock)
    }

    /**
     * [events] with the fade tags of [motion]. All events of one comment get the same tags, so the shadows, the
     * backdrops, and the texts fade together. \fad multiplies the opacity of each color, so the opacity and the
     * transparent parts stay correct.
     */
    fun timed(events: List<String>, motion: CommentMotion): List<String> {
        if (motion !is CommentMotion.Timed || motion.animation == CommentAnimation.NONE) return events
        val (inMs, outMs) = times(motion.animation, motion.durationMs)
        if (inMs <= 0 && outMs <= 0) return events
        val tags = "{\\fad($inMs,$outMs)}"
        return events.map { tags + it }
    }

    /**
     * The place tag of one part of a comment: \pos, or \move for a timed Pop or Slide up. ([x], [y]) is the place of
     * the part at rest. ([originX], [originY]) is the rotation center of the part. A Pop grows each part around its
     * rotation center, so the parts stay together and the rotation stays correct. [slidePx] is the Slide up distance.
     * The rotation center does not move, so a turned comment slides along its own up direction.
     */
    fun placeTag(motion: CommentMotion, x: Double, y: Double, originX: Double, originY: Double, slidePx: Double): String {
        fun at(scale: Double, rise: Double): String {
            if (scale == 1.0 && rise == 0.0) return "${fmt(x)},${fmt(y)}"
            return "${fmt(originX + scale * (x - originX))},${fmt(originY + scale * (y - originY) + rise * slidePx)}"
        }
        return when (motion) {
            CommentMotion.Still -> "\\pos(${at(1.0, 0.0)})"
            is CommentMotion.Frozen -> frame(motion).let { "\\pos(${at(it.scale, it.rise)})" }
            is CommentMotion.Timed -> {
                val inMs = times(motion.animation, motion.durationMs).first
                val start = when (motion.animation) {
                    CommentAnimation.POP -> at(POP_START_SCALE, 0.0)
                    CommentAnimation.SLIDE_UP -> at(1.0, 1.0)
                    else -> null
                }
                if (start == null || inMs <= 0) "\\pos(${at(1.0, 0.0)})" else "\\move($start,${at(1.0, 0.0)},0,$inMs)"
            }
        }
    }

    /** The size tags of one part of a comment: \fscx and \fscy, and for a timed Pop the \t that grows the part. */
    fun scaleTags(motion: CommentMotion): String {
        val rest = "\\fscx100\\fscy100"
        return when (motion) {
            CommentMotion.Still -> rest
            is CommentMotion.Frozen -> frame(motion).scale.let { if (it == 1.0) rest else "\\fscx${fmt(it * 100)}\\fscy${fmt(it * 100)}" }
            is CommentMotion.Timed -> {
                val inMs = times(motion.animation, motion.durationMs).first
                if (motion.animation != CommentAnimation.POP || inMs <= 0) {
                    rest
                } else {
                    val start = fmt(POP_START_SCALE * 100)
                    "\\fscx$start\\fscy$start\\t(0,$inMs,$rest)"
                }
            }
        }
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

    private fun alphaStep(opacity: Double): Double = (opacity.coerceIn(0.0, 1.0) * 255).roundToInt() / 255.0

    private fun fine(value: Double): Double = (value * 1000).roundToInt() / 1000.0

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
}
