package org.litvin.adjustments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorTimelineTest {
    private val oneKey = AdjustmentsV1(
        brightness = 1.0f,
        rotationDeg = 2.0f,
        colorKeys = listOf(ColorKeyV1(atMs = 10_000, brightness = 1.4f, temperature = 0.5f)),
    )

    @Test
    fun `the colors blend linearly over one second before the change point`() {
        assertEquals(1.0f, ColorTimeline.at(oneKey, 9_000).brightness)
        assertEquals(1.2f, ColorTimeline.at(oneKey, 9_500).brightness, 1e-6f)
        assertEquals(0.25f, ColorTimeline.at(oneKey, 9_500).whiteBalance!!.temperature, 1e-6f)
        assertEquals(1.4f, ColorTimeline.at(oneKey, 10_000).brightness)
    }

    @Test
    fun `the colors keep the geometry and drop only the color change points`() {
        val colors = ColorTimeline.at(oneKey, 12_000)

        assertEquals(2.0f, colors.rotationDeg)
        assertTrue(colors.colorKeys.isEmpty())
    }

    @Test
    fun `an edit changes the active color change point`() {
        val edited = ColorTimeline.withEdited(oneKey, 11_000, AdjustmentsV1(contrast = 1.3f))

        assertEquals(1.3f, edited.colorKeys.single().contrast)
        assertEquals(1.0f, edited.contrast)
    }

    @Test
    fun `add copies the active colors and remove deletes the active change point`() {
        val added = ColorTimeline.withKeyAdded(oneKey, 20_000)
        assertEquals(1.4f, added.colorKeys[1].brightness)

        val removed = ColorTimeline.withKeyRemoved(added, 15_000)
        assertEquals(listOf(20_000L), removed.colorKeys.map { it.atMs })
    }

    @Test
    fun `a span that cuts a blend gets a fade from the colors at the cut`() {
        val pieces = ColorTimeline.pieces(oneKey, 8_000, 9_500)

        assertEquals(2, pieces.size)
        val hold = pieces[0] as ColorTimeline.Piece.Hold
        val fade = pieces[1] as ColorTimeline.Piece.Fade
        assertEquals(8_000L to 9_000L, hold.startMs to hold.endMs)
        assertEquals(9_000L to 9_500L, fade.startMs to fade.endMs)
        assertEquals(1.0f, fade.from.brightness)
        assertEquals(1.2f, fade.to.brightness, 1e-6f)
    }

    @Test
    fun `a span without a blend is one hold`() {
        val pieces = ColorTimeline.pieces(oneKey, 11_000, 15_000)

        assertEquals(listOf<ColorTimeline.Piece>(ColorTimeline.Piece.Hold(11_000, 15_000, ColorTimeline.at(oneKey, 11_000))), pieces)
    }
}
