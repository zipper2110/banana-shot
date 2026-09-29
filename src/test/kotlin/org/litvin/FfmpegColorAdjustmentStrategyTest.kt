package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FfmpegColorAdjustmentStrategyTest {

    private fun fromSliders(
        brightness: Int = 0,
        contrast: Int = 0,
        saturation: Int = 0,
        shadows: Int = 0,
        highlights: Int = 0,
        temperature: Int = 0,
    ): FfmpegColorAdjustments = FfmpegColorAdjustmentStrategy.map(
        AdjustmentsUiConverter.slidersToModel(brightness, contrast, saturation, shadows, highlights, temperature)
    )

    @Test
    fun `identity sliders produce identity filter values`() {
        val values = fromSliders()

        assertEquals(0.0, values.brightnessLift, 1e-6)
        assertEquals(1.0, values.contrast, 1e-6)
        assertEquals(1.0, values.saturation, 1e-6)
        assertEquals(0.0, values.cbShift, 1e-6)
        assertEquals(0.0, values.crShift, 1e-6)
        assertEquals(0.0, values.shadowsLift, 1e-6)
        assertEquals(0.0, values.highlightsLift, 1e-6)
        assertTrue(!values.hasEqualizerAdjustments && !values.hasTemperatureAdjustments && !values.hasToneAdjustments)
    }

    @Test
    fun `brightness moves every tone by up to a quarter of the black-to-white range`() {
        assertEquals(-54.75, fromSliders(brightness = -100).brightnessLift, 1e-6)
        assertEquals(-27.375, fromSliders(brightness = -50).brightnessLift, 1e-6)
        assertEquals(13.6875, fromSliders(brightness = 25).brightnessLift, 1e-6)
        assertEquals(54.75, fromSliders(brightness = 100).brightnessLift, 1e-6)
        val full = FfmpegColorAdjustmentStrategy.map(AdjustmentsUiConverter.slidersToModel(100, 0, 0, 0, 0, 0), ToneRange.FULL)
        assertEquals(63.75, full.brightnessLift, 1e-6)

        val brighter = fromSliders(brightness = 100)
        assertTrue(brighter.hasToneAdjustments)
        assertTrue(!brighter.hasEqualizerAdjustments, "brightness must not use the eq filter")
        // The same change at black, mid gray and white (before the clip at 255).
        for (code in listOf(16, 126, 200)) {
            assertEquals(code + 55, FfmpegColorAdjustmentStrategy.toneCurve(brighter, code), "code $code")
        }
    }

    @Test
    fun `contrast slider maps linearly onto eq contrast`() {
        assertEquals(0.0, fromSliders(contrast = -100).contrast, 1e-6)
        assertEquals(0.5, fromSliders(contrast = -50).contrast, 1e-6)
        assertEquals(1.5, fromSliders(contrast = 50).contrast, 1e-6)
        assertEquals(2.0, fromSliders(contrast = 100).contrast, 1e-6)
    }

    @Test
    fun `saturation slider maps linearly onto eq saturation`() {
        assertEquals(0.0, fromSliders(saturation = -100).saturation, 1e-6)
        assertEquals(0.5, fromSliders(saturation = -50).saturation, 1e-6)
        assertEquals(1.5, fromSliders(saturation = 50).saturation, 1e-6)
        assertEquals(2.0, fromSliders(saturation = 100).saturation, 1e-6)
    }

    @Test
    fun `shadows and highlights set the change at black and at white`() {
        val lifted = fromSliders(shadows = 100)
        assertEquals(43.8, lifted.shadowsLift, 1e-6)
        assertEquals(0.0, lifted.highlightsLift, 1e-6)
        assertTrue(lifted.hasToneAdjustments)
        assertTrue(!lifted.hasEqualizerAdjustments, "tone controls must not touch the eq filter")

        val recovered = fromSliders(highlights = -100)
        assertEquals(0.0, recovered.shadowsLift, 1e-6)
        assertEquals(-43.8, recovered.highlightsLift, 1e-6)

        assertEquals(21.9, fromSliders(shadows = 50).shadowsLift, 1e-6)
        assertEquals(-10.95, fromSliders(highlights = -25).highlightsLift, 1e-6)

        // A full-range source has a wider black-to-white range, so the same slider moves more codes.
        val full = FfmpegColorAdjustmentStrategy.map(AdjustmentsUiConverter.slidersToModel(0, 0, 0, 100, -100, 0), ToneRange.FULL)
        assertEquals(51.0, full.shadowsLift, 1e-6)
        assertEquals(-51.0, full.highlightsLift, 1e-6)
    }

    @Test
    fun `highlights change only the top 35 percent and shadows only the bottom 35 percent`() {
        assertEquals(92.65, FfmpegColorAdjustmentStrategy.shadowsEnd(ToneRange.LIMITED), 1e-9)
        assertEquals(158.35, FfmpegColorAdjustmentStrategy.highlightsStart(ToneRange.LIMITED), 1e-9)
        for (range in ToneRange.entries) {
            val shadowsEnd = FfmpegColorAdjustmentStrategy.shadowsEnd(range)
            val highlightsStart = FfmpegColorAdjustmentStrategy.highlightsStart(range)
            for (slider in -100..100) {
                val highlights = FfmpegColorAdjustmentStrategy.map(AdjustmentsUiConverter.slidersToModel(0, 0, 0, 0, slider, 0), range)
                val shadows = FfmpegColorAdjustmentStrategy.map(AdjustmentsUiConverter.slidersToModel(0, 0, 0, slider, 0, 0), range)
                for (code in 0..255) {
                    if (code <= highlightsStart) {
                        assertEquals(code, FfmpegColorAdjustmentStrategy.toneCurve(highlights, code), "highlights=$slider changed code $code ($range)")
                    }
                    if (code >= shadowsEnd) {
                        assertEquals(code, FfmpegColorAdjustmentStrategy.toneCurve(shadows, code), "shadows=$slider changed code $code ($range)")
                    }
                }
            }
        }
    }

    @Test
    fun `the tone curve gives the planned changes for limited range video`() {
        val darker = fromSliders(highlights = -100)
        val lifted = fromSliders(shadows = 100)
        fun change(values: FfmpegColorAdjustments, code: Int) = FfmpegColorAdjustmentStrategy.toneCurve(values, code) - code

        val codes = listOf(16, 40, 60, 80, 92, 126, 159, 170, 180, 200, 213, 235)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, -3, -9, -25, -35, -44), codes.map { change(darker, it) })
        assertEquals(listOf(44, 34, 17, 3, 0, 0, 0, 0, 0, 0, 0, 0), codes.map { change(lifted, it) })
    }

    @Test
    fun `the tone curve never decreases for any slider values`() {
        for (range in ToneRange.entries) {
            for (shadows in -100..100 step 5) {
                for (highlights in -100..100 step 5) {
                    val values = FfmpegColorAdjustmentStrategy.map(
                        AdjustmentsUiConverter.slidersToModel(0, 0, 0, shadows, highlights, 0), range
                    )
                    var previous = FfmpegColorAdjustmentStrategy.toneCurve(values, 0)
                    for (code in 1..255) {
                        val current = FfmpegColorAdjustmentStrategy.toneCurve(values, code)
                        assertTrue(
                            current >= previous,
                            "tone curve decreases at code $code for shadows=$shadows highlights=$highlights ($range)"
                        )
                        previous = current
                    }
                }
            }
        }
    }

    @Test
    fun `warmer lowers Cb and raises Cr, cooler does the opposite`() {
        val warm = fromSliders(temperature = 100)
        assertEquals(-14.0, warm.cbShift, 1e-6)
        assertEquals(7.0, warm.crShift, 1e-6)
        assertTrue(warm.hasTemperatureAdjustments)
        assertEquals(1.0, warm.saturation, 1e-6)
        assertTrue(!warm.hasToneAdjustments && !warm.hasEqualizerAdjustments)

        val cool = fromSliders(temperature = -50)
        assertEquals(7.0, cool.cbShift, 1e-6)
        assertEquals(-3.5, cool.crShift, 1e-6)
    }

    @Test
    fun `temperature gives gray a color cast`() {
        // Mid gray in BT.709 limited range: Y 126, Cb 128, Cr 128. Convert the shifted codes back to R'G'B'.
        fun rgb(values: FfmpegColorAdjustments): Triple<Double, Double, Double> {
            val y = (126 - 16) / 219.0
            val pb = (FfmpegColorAdjustmentStrategy.chromaCurve(values.cbShift, 128) - 128) / 224.0
            val pr = (FfmpegColorAdjustmentStrategy.chromaCurve(values.crShift, 128) - 128) / 224.0
            val r = y + 1.5748 * pr
            val b = y + 1.8556 * pb
            val g = (y - 0.2126 * r - 0.0722 * b) / 0.7152
            return Triple(r, g, b)
        }
        val (wr, _, wb) = rgb(fromSliders(temperature = 100))
        assertTrue(wr > wb + 0.1, "warm gray must be red-orange: r=$wr b=$wb")
        val (cr, _, cb) = rgb(fromSliders(temperature = -100))
        assertTrue(cb > cr + 0.1, "cool gray must be blue: r=$cr b=$cb")
    }

    @Test
    fun `every slider combination stays inside the ffmpeg eq, hue and lut ranges`() {
        val extremes = listOf(-100, -50, 0, 50, 100)
        for (brightness in extremes) {
            for (contrast in extremes) {
                for (saturation in extremes) {
                    for (shadows in extremes) {
                        for (highlights in extremes) {
                            for (temperature in extremes) {
                                val values =
                                    fromSliders(brightness, contrast, saturation, shadows, highlights, temperature)
                                val at = "b=$brightness c=$contrast s=$saturation sh=$shadows hl=$highlights " +
                                    "t=$temperature"
                                assertTrue(values.brightnessLift in -255.0..255.0, "brightness out of range at $at")
                                assertTrue(values.contrast in 0.0..3.0, "contrast out of range at $at")
                                assertTrue(values.saturation in 0.0..3.0, "saturation out of range at $at")
                                assertTrue(values.cbShift in -255.0..255.0, "Cb shift out of range at $at")
                                assertTrue(values.crShift in -255.0..255.0, "Cr shift out of range at $at")
                                assertTrue(values.shadowsLift in -255.0..255.0, "shadows out of range at $at")
                                assertTrue(values.highlightsLift in -255.0..255.0, "highlights out of range at $at")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `out of range model values are clamped instead of leaking into the command`() {
        val values = FfmpegColorAdjustmentStrategy.map(
            AdjustmentsV1(
                brightness = 9.0f,
                contrast = -4.0f,
                saturation = 12.0f,
                shadows = 6.0f,
                highlights = -6.0f,
                whiteBalance = WhiteBalanceV1(temperature = 7.0f),
            )
        )

        assertEquals(54.75, values.brightnessLift, 1e-6)
        assertEquals(0.0, values.contrast, 1e-6)
        assertEquals(3.0, values.saturation, 1e-6)
        assertEquals(43.8, values.shadowsLift, 1e-6)
        assertEquals(-43.8, values.highlightsLift, 1e-6)
        assertEquals(-14.0, values.cbShift, 1e-6)
        assertEquals(7.0, values.crShift, 1e-6)
    }
}
