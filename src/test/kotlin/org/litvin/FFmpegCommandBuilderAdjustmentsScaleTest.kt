package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FFmpegCommandBuilderAdjustmentsScaleTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private fun buildVf(adj: AdjustmentsV1?): String {
        val res = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "in.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1280,
                outHeight = 720,
                encoderLabel = "H.264 (libx264)",
                idleTrim = false,
                adjustments = adj,
            )
        )
        val vfIdx = res.args.indexOf("-vf")
        assertTrue(vfIdx >= 0, "-vf expected in simple path")
        return res.args[vfIdx + 1]
    }

    private fun parseEqMap(vf: String): Map<String, String>? {
        val eqStart = vf.indexOf("eq=")
        if (eqStart < 0) return null
        val after = vf.substring(eqStart + 3)
        val endIdx = after.indexOfAny(charArrayOf(',', ';'))
        val eqBody = if (endIdx >= 0) after.substring(0, endIdx) else after
        val parts = eqBody.split(":")
        return parts.mapNotNull {
            val kv = it.split("=")
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap()
    }

    @Test
    fun identity_model_values_emit_no_color_filter() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))

        assertNull(parseEqMap(vf), "Identity values must not add an eq filter, vf=$vf")
        assertFalse(vf.contains("lutyuv"), "Identity values must not add a lutyuv filter, vf=$vf")
    }

    @Test
    fun brightness_adds_a_constant_luma_change_and_no_eq_filter() {
        // Slider -100 -> model 0.0 -> a quarter of the 16..235 range darker.
        var vf = buildVf(AdjustmentsV1(brightness = 0.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        assertTrue(vf.contains("lutyuv=y='round(val+-54.7500)':u=val:v=val"), "Expected -54.75 codes, was: $vf")
        assertNull(parseEqMap(vf), "Brightness must not add an eq filter, vf=$vf")

        // Slider +100 -> model 2.0 -> the same change, brighter.
        vf = buildVf(AdjustmentsV1(brightness = 2.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        assertTrue(vf.contains("lutyuv=y='round(val+54.7500)':u=val:v=val"), "Expected +54.75 codes, was: $vf")
    }

    @Test
    fun boosted_saturation_passes_through() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 2.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)!!
        val saturation = eq["saturation"]!!.toDouble()

        assertTrue(kotlin.math.abs(saturation - 2.0) < 1e-3, "Expected +100 saturation to pass through as 2.0, was $saturation (vf=$vf)")
    }

    @Test
    fun full_desaturation_stays_grayscale() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 0.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)!!
        val saturation = eq["saturation"]!!.toDouble()

        assertTrue(kotlin.math.abs(saturation) < 1e-3, "Expected saturation 0.0 to stay grayscale, was $saturation (vf=$vf)")
    }

    @Test
    fun negative_contrast_export_supports_current_ui_floor() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 0.5f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)!!
        val contrast = eq["contrast"]!!.toDouble()
        assertTrue(kotlin.math.abs(contrast - 0.5) < 1e-3, "Expected -50 contrast to pass through as 0.5, was $contrast (vf=$vf)")
    }

    @Test
    fun positive_contrast_still_passes_through() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 2.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)!!
        val contrast = eq["contrast"]!!.toDouble()

        assertTrue(kotlin.math.abs(contrast - 2.0) < 1e-3, "Expected +100 contrast to pass through as 2.0, was $contrast (vf=$vf)")
    }

    @Test
    fun white_balance_temperature_shifts_the_chroma_and_leaves_the_luma_alone() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0.5f)))

        // Warmer: less blue (Cb down) and more red (Cr up). The luma plane passes through.
        assertTrue(vf.contains("lutyuv=y=val:u='round(val+-7.0000)':v='round(val+3.5000)'"), "Expected a chroma shift, was: $vf")
        assertNull(parseEqMap(vf), "WB temperature must not add an eq filter, vf=$vf")
        assertFalse(vf.contains("hue="), "WB temperature must not rotate the hue, vf=$vf")
    }

    @Test
    fun shadows_and_highlights_add_a_luma_lut_and_leave_the_eq_values_alone() {
        val vf = buildVf(
            AdjustmentsV1(
                brightness = 1.0f,
                contrast = 1.0f,
                saturation = 1.0f,
                shadows = 0.5f,
                highlights = -0.5f,
                whiteBalance = WhiteBalanceV1(0f),
            )
        )

        // The tone curve is a luma lookup table only, so the identity eq filter is left out.
        assertNull(parseEqMap(vf), "Shadows/highlights must not add an eq filter, vf=$vf")
        assertTrue(vf.contains("lutyuv=y='round(val+21.9000*clip((92.6500-val)/76.6500,0,1)"), "Expected the shadows lift, was: $vf")
        assertTrue(vf.contains("+-21.9000*clip((val-158.3500)/76.6500,0,1)"), "Expected the highlights lift, was: $vf")
        // lutyuv clips chroma to the legal range unless u and v pass the input through unchanged.
        assertTrue(vf.contains(":u=val:v=val"), "Expected chroma passthrough, was: $vf")
    }

    @Test
    fun identity_tone_sliders_add_no_lut_filter() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.2f, saturation = 1.0f))

        assertTrue(!vf.contains("lutyuv"), "Expected no lutyuv filter, was: $vf")
    }
}
