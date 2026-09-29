package org.litvin

import org.litvin.adjustments.AdjustmentsV1

/**
 * The luma codes of black and white in the source video. The shadows/highlights curve uses them to find
 * mid gray, so that the tone controls do not change mid gray.
 */
enum class ToneRange(val black: Int, val white: Int) {
    /** Limited ("tv") range, the range of most camera and phone video. */
    LIMITED(16, 235),

    /** Full ("pc") range. */
    FULL(0, 255);

    val span: Int get() = white - black
}

data class FfmpegColorAdjustments(
    /** The luma change at every tone, in 8-bit code values. */
    val brightnessLift: Double,
    val contrast: Double,
    val saturation: Double,
    /** The Cb (blue-difference) change, in 8-bit code values. */
    val cbShift: Double,
    /** The Cr (red-difference) change, in 8-bit code values. */
    val crShift: Double,
    /** The luma change at black, in 8-bit code values. */
    val shadowsLift: Double,
    /** The luma change at white, in 8-bit code values. */
    val highlightsLift: Double,
    /** The black and white codes that the brightness and shadows/highlights changes use. */
    val toneRange: ToneRange = ToneRange.LIMITED,
) {
    val hasEqualizerAdjustments: Boolean
        get() =
            kotlin.math.abs(contrast - 1.0) >= EPSILON ||
                kotlin.math.abs(saturation - 1.0) >= EPSILON

    /** True when the luma lookup table changes a code: brightness, shadows or highlights. These need [toneRange]. */
    val hasToneAdjustments: Boolean
        get() =
            kotlin.math.abs(brightnessLift) >= EPSILON ||
                kotlin.math.abs(shadowsLift) >= EPSILON ||
                kotlin.math.abs(highlightsLift) >= EPSILON

    /** True when the chroma lookup tables change a code (temperature). */
    val hasTemperatureAdjustments: Boolean
        get() = kotlin.math.abs(cbShift) >= EPSILON || kotlin.math.abs(crShift) >= EPSILON

    private companion object {
        const val EPSILON = 1e-6
    }
}

/**
 * Maps the stored color model to the FFmpeg `eq` and `lutyuv` values. The export command
 * (FFmpegCommandBuilder) and the mpv preview shader (MpvShaderParams) both use this, so the
 * preview and the rendered file get the same numbers.
 *
 * Every Color tab slider runs [-100..+100] with 0 as the identity (AdjustmentsUiConverter), which
 * gives model values of [0.0..2.0] for brightness/contrast/saturation and [-1.0..+1.0] for
 * shadows/highlights/white balance. The mapping keeps that symmetry:
 *
 * - brightness [0..2] -> a luma change of up to [BRIGHTNESS_SPAN] of the black-to-white range, the same at every tone.
 * - contrast   [0..2] -> eq contrast   [0..2],   as `model`;     -100 is flat grey, +100 is double.
 * - saturation [0..2] -> eq saturation [0..2],   as `model`;     -100 is greyscale, +100 is double.
 * - shadows    [-1..+1] -> a luma change of up to [TONE_SPAN] of the black-to-white range at black.
 *   The change falls smoothly to 0 at [TONE_ZONE] of the range (35%). Brighter tones do not change.
 * - highlights [-1..+1] -> the same change at white. It falls smoothly to 0 at 65% of the range.
 *   Darker tones do not change.
 * - white balance temperature [-1..+1] -> a chroma shift toward orange (warmer) or blue (cooler):
 *   Cb moves by up to [TEMPERATURE_CB_CODES] and Cr by up to [TEMPERATURE_CR_CODES], in the opposite direction.
 *   White and gray get the color cast too, as a white balance change gives.
 *
 * [toneCurve] is the brightness and shadows/highlights curve on the luma plane, and [chromaCurve] is the
 * temperature shift on the chroma planes. The export writes them as one `lutyuv` filter after `eq`, and the
 * preview shader has a copy. The weight is smoothstep over the zone, so its slope is at most 1.5 / [TONE_ZONE].
 * The shadows/highlights change is at most [TONE_SPAN], so the slope of the curve never drops below
 * 1 - 1.5 * 0.20 / 0.35 = 0.14. The curve always increases, so the tone controls cannot invert the image.
 * A larger [TONE_SPAN] or a smaller [TONE_ZONE] can make the curve decrease; a unit test checks this.
 *
 * The preview shader must give the same codes as the export, so the controls use only eq and lookup tables.
 * A lookup table on a chroma plane cannot read the luma, so the temperature shift is the same at every tone.
 */
object FfmpegColorAdjustmentStrategy {
    /** The Cb change, in 8-bit codes, at white balance temperature -1.0 / +1.0. Warmer lowers Cb (less blue). */
    private const val TEMPERATURE_CB_CODES = 14.0

    /** The Cr change, in 8-bit codes, at white balance temperature -1.0 / +1.0. Warmer raises Cr (more red). */
    private const val TEMPERATURE_CR_CODES = 7.0

    /** The brightness luma change, as a fraction of the black-to-white range, at -1.0 / +1.0. */
    private const val BRIGHTNESS_SPAN = 0.25

    /** The shadows/highlights luma change at black or white, as a fraction of the black-to-white range, at -1.0 / +1.0. */
    private const val TONE_SPAN = 0.20

    /** The part of the black-to-white range that shadows (from black) or highlights (from white) changes. */
    private const val TONE_ZONE = 0.35

    // Ranges accepted by ffmpeg's eq filter, mirrored by the tr-adjust.hook parameters.
    private const val EQ_CONTRAST_MIN = 0.0
    private const val EQ_CONTRAST_MAX = 3.0
    private const val EQ_SATURATION_MIN = 0.0
    private const val EQ_SATURATION_MAX = 3.0

    fun map(adjustments: AdjustmentsV1, toneRange: ToneRange = ToneRange.LIMITED): FfmpegColorAdjustments {
        val whiteBalance = adjustments.whiteBalance
        val temperature = (whiteBalance?.temperature ?: 0.0f).coerceIn(-1.0f, 1.0f).toDouble()
        val brightness = (adjustments.brightness.toDouble() - 1.0).coerceIn(-1.0, 1.0)
        val shadows = adjustments.shadows.coerceIn(-1.0f, 1.0f).toDouble()
        val highlights = adjustments.highlights.coerceIn(-1.0f, 1.0f).toDouble()

        // Rounded as the export command writes them, so the command, the shader and the curves use the same number.
        return FfmpegColorAdjustments(
            brightnessLift = round4(brightness * BRIGHTNESS_SPAN * toneRange.span),
            contrast = adjustments.contrast.toDouble().coerceIn(EQ_CONTRAST_MIN, EQ_CONTRAST_MAX),
            saturation = adjustments.saturation.toDouble().coerceIn(EQ_SATURATION_MIN, EQ_SATURATION_MAX),
            cbShift = round4(-temperature * TEMPERATURE_CB_CODES),
            crShift = round4(temperature * TEMPERATURE_CR_CODES),
            shadowsLift = round4(shadows * TONE_SPAN * toneRange.span),
            highlightsLift = round4(highlights * TONE_SPAN * toneRange.span),
            toneRange = toneRange,
        )
    }

    /** The width of the shadows and of the highlights zone, in codes. Rounded as the export command writes it. */
    fun toneZone(range: ToneRange): Double = round4(TONE_ZONE * range.span)

    /** The last code that shadows changes. */
    fun shadowsEnd(range: ToneRange): Double = round4(range.black + toneZone(range))

    /** The first code that highlights changes. */
    fun highlightsStart(range: ToneRange): Double = round4(range.white - toneZone(range))

    /**
     * The weight of the highlights change at 8-bit luma [code]: 0 up to [highlightsStart], then a smooth rise to 1 at white.
     * The rise is `s*s*(3-2*s)` (smoothstep), so it starts and ends flat. The export expression uses the same operations.
     */
    fun highlightsWeight(code: Double, range: ToneRange): Double = smooth((code - highlightsStart(range)) / toneZone(range))

    /** The weight of the shadows change: 1 at black, a smooth fall to 0 at [shadowsEnd], and 0 above it. */
    fun shadowsWeight(code: Double, range: ToneRange): Double = smooth((shadowsEnd(range) - code) / toneZone(range))

    /**
     * The brightness and shadows/highlights curve for one 8-bit luma [code], as `lutyuv` computes it for the export:
     * the result is rounded to the nearest integer and clipped to 0..255. Rounding (not truncation) keeps
     * a very small change from moving a code one full step.
     */
    fun toneCurve(values: FfmpegColorAdjustments, code: Int): Int {
        val range = values.toneRange
        val v = code + values.brightnessLift +
            values.shadowsLift * shadowsWeight(code.toDouble(), range) +
            values.highlightsLift * highlightsWeight(code.toDouble(), range)
        return kotlin.math.floor(v + 0.5).toInt().coerceIn(0, 255)
    }

    /** The temperature shift for one 8-bit chroma [code] ([FfmpegColorAdjustments.cbShift] or crShift), rounded and clipped as in [toneCurve]. */
    fun chromaCurve(shift: Double, code: Int): Int = kotlin.math.floor(code + shift + 0.5).toInt().coerceIn(0, 255)

    private fun round4(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0

    private fun smooth(x: Double): Double {
        val s = x.coerceIn(0.0, 1.0)
        return s * s * (3.0 - 2.0 * s)
    }
}
