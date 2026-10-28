package org.litvin.export

import org.litvin.i18n.DisplayFormats

data class ExportFrameRate(
    val ffmpegArgument: String,
    val framesPerSecond: Double,
) {
    /** The frame rate that the user reads, for example "30" or "29.97" ("29,97" with a decimal comma). */
    val displayFps: String
        get() = if (kotlin.math.abs(framesPerSecond - framesPerSecond.toInt()) < 0.005) {
            framesPerSecond.toInt().toString()
        } else {
            DisplayFormats.decimal(framesPerSecond, 2, 2, grouping = false)
        }
}

object ExportFrameRates {
    fun parse(rawRate: String): ExportFrameRate? {
        val normalized = rawRate.trim()
        if (normalized.isEmpty()) return null
        val value = if ('/' in normalized) {
            val parts = normalized.split('/', limit = 2)
            if (parts.size != 2) return null
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull() ?: return null
            if (denominator == 0.0) return null
            numerator / denominator
        } else {
            normalized.toDoubleOrNull() ?: return null
        }
        return ExportFrameRate(normalized, value.takeIf { it.isFinite() && it > 0 } ?: return null)
    }
}
