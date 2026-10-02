package org.litvin.media

/**
 * Extra time for a back seek while the video plays.
 * Playback continues during the seek delay, so a back seek of 1 second moves back only about 0.5 seconds.
 */
const val PLAYING_BACK_SEEK_EXTRA_MS = 500L

/**
 * Returns the delta to send for a relative seek of [deltaMs].
 * A back seek while [playing] gets [PLAYING_BACK_SEEK_EXTRA_MS] more. Other seeks stay the same.
 */
fun relativeSeekDeltaMs(deltaMs: Long, playing: Boolean): Long =
    if (playing && deltaMs < 0L) deltaMs - PLAYING_BACK_SEEK_EXTRA_MS else deltaMs
