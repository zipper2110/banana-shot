package org.litvin.export

import com.fasterxml.jackson.annotation.JsonIgnore
import kotlin.math.roundToInt

/**
 * The sample aspect ratio (SAR) of a video: the width of one pixel divided by its height (B-59).
 * Phone and action camera videos have square pixels (1:1). DV and broadcast videos can have other values,
 * for example 720x486 with 40:33. A player then shows the frame at about 873x486.
 */
data class SampleAspect(val num: Int, val den: Int) {
    /** True when the pixels are square. Then the display size is the stored size. */
    @get:JsonIgnore
    val square: Boolean get() = num <= 0 || den <= 0 || num == den

    /** The width that a player shows for a frame with [width] stored pixels. */
    fun displayWidth(width: Int): Int =
        if (square) width else (width.toDouble() * num / den).roundToInt().coerceAtLeast(1)

    /** The value for the FFmpeg "setsar" filter. */
    fun ffmpegArgument(): String = if (square) "1" else "$num/$den"

    companion object {
        val SQUARE = SampleAspect(1, 1)

        /**
         * Parses the ffprobe value, for example "40:33". FFmpeg writes "0:1" or "N/A" when the file has no value.
         * Then the pixels are square.
         */
        fun parse(text: String?): SampleAspect {
            val parts = text?.trim()?.split(':', '/')?.takeIf { it.size == 2 } ?: return SQUARE
            val num = parts[0].trim().toIntOrNull() ?: return SQUARE
            val den = parts[1].trim().toIntOrNull() ?: return SQUARE
            if (num <= 0 || den <= 0) return SQUARE
            val divisor = gcd(num, den)
            return SampleAspect(num / divisor, den / divisor).let { if (it.square) SQUARE else it }
        }

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
