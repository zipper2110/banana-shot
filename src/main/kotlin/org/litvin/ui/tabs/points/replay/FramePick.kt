package org.litvin.ui.tabs.points.replay

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The frame numbers and the frame steps of pick mode in the replay editor (decision 23). */
internal object FramePick {
    /** The frame rate when the player does not know it yet. */
    const val FALLBACK_FPS = 30.0

    /** The number of frames that Shift with Left or Right steps. */
    const val LONG_STEP_FRAMES = 5

    /** The time of one frame. */
    fun frameMs(fps: Double): Double = 1000.0 / fps

    /** The frames from [startMs] to [endMs], with the frame at each end. */
    fun count(startMs: Long, endMs: Long, fps: Double): Int =
        floor((endMs - startMs).coerceAtLeast(0L) * fps / 1000.0 + 1e-6).toInt() + 1

    /** The number of the frame at [timeMs] in the part from [startMs] to [endMs]. The first frame is 1. */
    fun index(timeMs: Long, startMs: Long, endMs: Long, fps: Double): Int =
        (((timeMs - startMs) * fps / 1000.0).roundToInt() + 1).coerceIn(1, count(startMs, endMs, fps))

    /** The time [frames] frames after [timeMs] (before it for a negative count), inside the part. */
    fun step(timeMs: Long, frames: Int, startMs: Long, endMs: Long, fps: Double): Long =
        (timeMs + (frames * frameMs(fps)).roundToLong()).coerceIn(startMs, endMs)
}
