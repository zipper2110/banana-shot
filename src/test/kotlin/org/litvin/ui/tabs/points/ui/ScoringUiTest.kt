package org.litvin.ui.tabs.points.ui

import org.junit.jupiter.api.Test
import org.litvin.scoring.ScoringEngine.MatchState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.litvin.ui.commons.Palette

class ScoringUiTest {
    @Test
    fun pointsTextFollowsTheScoreboard() {
        fun state(p1: Int, p2: Int, tiebreak: Boolean = false) = MatchState(p1, p2, 0, 0, 0, 0, null, null, tiebreak)
        assertEquals("40", ScoringUi.pointsText(state(3, 3), 1))
        assertEquals("Ad", ScoringUi.pointsText(state(5, 4), 1))
        assertEquals("40", ScoringUi.pointsText(state(5, 4), 2))
        assertEquals("7", ScoringUi.pointsText(state(7, 5, tiebreak = true), 1))
        assertTrue(ScoringUi.leads(state(5, 4), 1))
        assertFalse(ScoringUi.leads(state(4, 4), 1))
        assertFalse(ScoringUi.leads(state(0, 0), 2))
    }

    @Test
    fun svgPathReadsTheScoreboardGlyph() {
        // A square with relative moves, lines and a smooth quadratic curve.
        val square = ScoringUi.svgPath("M10 10h20v20H10Zm40 0q5 0 10 5T70 20z").bounds2D
        assertEquals(10.0, square.minX)
        assertEquals(70.0, square.maxX)
        assertEquals(10.0, square.minY)
        assertEquals(30.0, square.maxY)

        val icon = ScoringUi.scoreboardIcon(18, Palette.FG)
        assertEquals(18, icon.iconWidth)
        val image = java.awt.image.BufferedImage(18, 18, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        icon.paintIcon(null, g, 0, 0)
        g.dispose()
        // The frame of the board is at 80..880 of 960 horizontally: the edge columns stay empty.
        assertTrue((0 until 18).none { y -> image.getRGB(0, y) ushr 24 != 0 })
        assertTrue((0 until 18).any { y -> image.getRGB(9, y) ushr 24 != 0 })
    }

    @Test
    fun textOnAPlayerColorKeepsItsContrast() {
        // Dark player colors get white text, light player colors get the dark text of the design.
        assertEquals(java.awt.Color.WHITE, ScoringUi.onPlayer(java.awt.Color.BLACK))
        assertEquals(java.awt.Color.WHITE, ScoringUi.onPlayer(java.awt.Color(0x00, 0x5F, 0x5F)))
        assertEquals(java.awt.Color.WHITE, ScoringUi.onPlayer(java.awt.Color(0x00, 0x00, 0xFF)))
        assertEquals(Palette.ON_LIGHT, ScoringUi.onPlayer(java.awt.Color(0xFF, 0xD5, 0x4A)))
        assertEquals(Palette.ON_LIGHT, ScoringUi.onPlayer(java.awt.Color(0x00, 0xFF, 0x00)))
        assertEquals(Palette.ON_LIGHT, ScoringUi.onPlayer(Palette.LIME))
        assertEquals(java.awt.Color.WHITE, ScoringUi.onPlayerKeyStyle(java.awt.Color.BLACK).text)
        assertEquals(Palette.ON_LIGHT, ScoringUi.onPlayerKeyStyle(Palette.LIME).text)
    }

    @Test
    fun aRacketInADarkPlayerColorGetsALightBadge() {
        assertTrue(ScoringUi.needsBadge(java.awt.Color.BLACK))
        assertTrue(ScoringUi.needsBadge(java.awt.Color(0x00, 0x5F, 0x5F)))
        assertFalse(ScoringUi.needsBadge(Palette.LIME))
        assertFalse(ScoringUi.needsBadge(ScorePlayers().p1Color))
        assertFalse(ScoringUi.needsBadge(ScorePlayers().p2Color))
        assertEquals(21.0, ScoringUi.contrast(java.awt.Color.BLACK, java.awt.Color.WHITE), 0.01)
    }
}
