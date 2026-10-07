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
    fun lightBackground_makesTheTextDarkAndInvertsTheSurfaceSteps() {
        Palette.setSeeds(Palette.DEFAULT_ACCENT, Color(0xF0F0F0))

        assertTrue(Palette.LINE.red < Palette.BG.red)
        assertEquals(0x1B1B1B, Palette.FG.rgb and 0xFFFFFF)
        assertEquals(0x000000, Palette.FG_STRONG.rgb and 0xFFFFFF)
        // The pale status text gets a dark variant.
        assertTrue(Palette.RED_TEXT.red < 0xC0)
    }

    @Test
    fun midBackground_keepsTheOrderOfTheTextColors() {
        Palette.setSeeds(Palette.DEFAULT_ACCENT, Palette.MID_BACKGROUND)

        assertEquals(0xFFFFFF, Palette.FG_STRONG.rgb and 0xFFFFFF)
        assertTrue(Palette.FG.red < Palette.FG_STRONG.red)
        assertTrue(Palette.FG_3.red < Palette.FG_2.red && Palette.FG_2.red < Palette.FG.red)
        assertTrue(Palette.FG_3.red > Palette.BG.red)
    }

    @Test
    fun keptToken_followsASeedChange() {
        val kept = Palette.BG
        val keptTint = Palette.withAlpha(Palette.LIME, 30)

        Palette.setSeeds(Palette.LIGHT_ACCENT, Palette.LIGHT_BACKGROUND)

        assertEquals(Palette.LIGHT_BACKGROUND.rgb, kept.rgb)
        assertEquals(Palette.LIME.rgb and 0xFFFFFF, keptTint.rgb and 0xFFFFFF)
        assertEquals(30, keptTint.alpha)
        assertEquals(kept.rgb, kept.hashCode())
    }

    @Test
    fun lightTheme_hasDarkerLimeMarksThanTheLimeFill() {
        Palette.setSeeds(Palette.DEFAULT_ACCENT, Palette.DEFAULT_BACKGROUND)
        assertEquals(Palette.LIME_FILL.rgb, Palette.LIME.rgb)

        Palette.setSeeds(Palette.LIGHT_ACCENT, Palette.LIGHT_BACKGROUND)
        assertEquals(Palette.LIGHT_ACCENT.rgb, Palette.LIME_FILL.rgb)
        assertTrue(Palette.LIME.green < Palette.LIME_FILL.green)
    }

    @Test
    fun readableOnSurface_followsTheBackground() {
        val yellow = Palette.readableOnSurface(Color(0xFFFF00))
        val navy = Palette.readableOnSurface(Color(0x0D1B6E))

        // On the dark theme a bright color does not change, and a dark color gets lighter.
        assertEquals(0xFFFF00, yellow.rgb and 0xFFFFFF)
        assertTrue(navy.blue > 0x6E && navy.blue > navy.red, Integer.toHexString(navy.rgb))

        Palette.setSeeds(Palette.LIGHT_ACCENT, Palette.LIGHT_BACKGROUND)

        // On the Light theme the same object is darker and keeps its hue.
        assertTrue(yellow.red < 0xA0 && yellow.red == yellow.green && yellow.blue == 0, Integer.toHexString(yellow.rgb))
        assertEquals(0x0D1B6E, navy.rgb and 0xFFFFFF)
    }

    @Test
    fun darkAccent_getsLightInkAndVisibleMarks() {
        assertTrue(Palette.ON_LIME.red < 0x40, "a light accent keeps the dark ink")

        Palette.setSeeds(Color(0x464646), Palette.DEFAULT_BACKGROUND)

        assertEquals(0xFFFFFF, Palette.ON_LIME.rgb and 0xFFFFFF)
        assertEquals(0x464646, Palette.LIME_FILL.rgb and 0xFFFFFF)
        // The marks get lighter than the accent, so that they show on the dark background.
        assertTrue(Palette.LIME.red > 0x46, Integer.toHexString(Palette.LIME.rgb))
    }

    @Test
    fun themes_setTheirSeeds() {
        AppTheme.entries.forEach { theme ->
            Palette.setSeeds(theme.accent, theme.background)
            assertEquals(theme.background.rgb, Palette.BG.rgb)
        }
        assertEquals(AppTheme.DARK, AppTheme.fromId("unknown"))
        assertEquals(AppTheme.MID, AppTheme.fromId("mid"))
    }

    @Test
    fun resetSeeds_restoresTheBaseValues() {
        Palette.setSeeds(Color(0xFF00AA), Color(0x202020))
        Palette.setSeeds(Palette.DEFAULT_ACCENT, Palette.DEFAULT_BACKGROUND)

        assertEquals(0x0E0E0E, Palette.BG.rgb and 0xFFFFFF)
        assertEquals(0xC8EC46, Palette.LIME.rgb and 0xFFFFFF)
    }
}
