package org.litvin.ui.commons

import java.awt.Color
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteSeedsTest {
    @AfterTest
    fun resetSeeds() = Palette.setSeeds(Palette.DEFAULT_ACCENT, Palette.DEFAULT_BACKGROUND)

    @Test
    fun defaultSeeds_useTheDefaultAccentAndTheBaseSurfaces() {
        assertEquals(0xC8EC46, Palette.LIME.rgb and 0xFFFFFF)
        assertEquals(0x0E0E0E, Palette.BG.rgb and 0xFFFFFF)
        assertEquals(0x262626, Palette.LINE.rgb and 0xFFFFFF)
        assertEquals(0xE4E4E4, Palette.FG.rgb and 0xFFFFFF)
    }

    @Test
    fun baseAccent_keepsTheBaseValuesOfTheLimeTokens() {
        Palette.setSeeds(Palette.BASE_ACCENT, Palette.DEFAULT_BACKGROUND)

        assertEquals(0xA1FE00, Palette.LIME.rgb and 0xFFFFFF)
        assertEquals(0xB4FF33, Palette.LIME_HOVER.rgb and 0xFFFFFF)
        assertEquals(0x1D2116, Palette.LIME_ROW.rgb and 0xFFFFFF)
    }

    @Test
    fun newSeeds_changeTheSeededTokens() {
        val accent = Color(0x4B8CFF)
        val background = Color(0x101820)
        Palette.setSeeds(accent, background)

        assertEquals(background.rgb, Palette.BG.rgb)
        assertEquals(accent.rgb, Palette.LIME.rgb)
        assertEquals(Palette.LIME.rgb and 0xFFFFFF, Palette.LIME_GLOW.rgb and 0xFFFFFF)
        // A surface keeps its distance from the background.
        assertEquals(0x18, Palette.LINE.red - Palette.BG.red)
        // The fixed tokens do not change.
        assertEquals(0xFF7351, Palette.RED.rgb and 0xFFFFFF)
    }

    @Test
    fun lightBackground_invertsTheTextAndTheSurfaceSteps() {
        Palette.setSeeds(Palette.DEFAULT_ACCENT, Color(0xF0F0F0))

        assertTrue(Palette.LINE.red < Palette.BG.red)
        assertEquals(0x1B1B1B, Palette.FG.rgb and 0xFFFFFF)
    }

    @Test
    fun resetSeeds_restoresTheBaseValues() {
        Palette.setSeeds(Color(0xFF00AA), Color(0x202020))
        Palette.setSeeds(Palette.DEFAULT_ACCENT, Palette.DEFAULT_BACKGROUND)

        assertEquals(0x0E0E0E, Palette.BG.rgb and 0xFFFFFF)
        assertEquals(0xC8EC46, Palette.LIME.rgb and 0xFFFFFF)
    }
}
