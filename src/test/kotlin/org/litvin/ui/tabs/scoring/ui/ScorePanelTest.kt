package org.litvin.ui.tabs.scoring.ui

import org.junit.jupiter.api.Test
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoringEngine.MatchState
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.litvin.ui.commons.UiKit

class ScorePanelTest {
    private class Calls {
        val list = mutableListOf<String>()
    }

    private fun panel(calls: Calls = Calls()) = ScorePanel(
        onPrevious = { calls.list += "previous" },
        onNext = { calls.list += "next" },
        onToggleFavorite = { calls.list += "favorite" },
        onOutcome = { calls.list += "outcome:$it" },
        onServe = { calls.list += "serve:$it" },
        onManualGame = { calls.list += "game:$it" },
        onManualSet = { calls.list += "set:$it" },
    ).apply {
        setPlayers(ScorePlayers("Alex", "Sam"))
        setSize(356, preferredSize.height)
        doLayout()
        player1.doLayout()
        player2.doLayout()
    }

    private val selected = ScorePanelState(
        index = 44,
        total = 128,
        hasPrevious = true,
        hasNext = true,
        canScore = true,
        after = MatchState(p1Pts = 1, p2Pts = 0, gamesP1 = 5, gamesP2 = 1, setsP1 = 0, setsP2 = 0, lastGameWonBy = null, lastSetWonBy = null, isTiebreak = false),
    )

    @Test
    fun withoutSelectionTheControlsAreDisabled() {
        SwingUtilities.invokeAndWait {
            val panel = panel()
            panel.render(ScorePanelState(total = 3))

            assertEquals("No point selected", panel.pointLabel.text)
            assertFalse(panel.previousButton.isEnabled)
            assertFalse(panel.nextButton.isEnabled)
            assertFalse(panel.favoriteButton.isVisible)
            assertFalse(panel.player1Button.isEnabled)
            assertFalse(panel.noPointButton.isEnabled)
            assertFalse(panel.player2Button.isEnabled)
            assertFalse(panel.player1.serveButton.isEnabled)
        }
    }

    @Test
    fun showsThePointNumberAndTheScoreAfterThePoint() {
        SwingUtilities.invokeAndWait {
            val panel = panel()
            panel.render(selected)

            assertEquals("Point 45 / 128", panel.pointLabel.text)
            assertEquals("15", panel.player1.pointsLabel.text)
            assertEquals("scoring-score-summary", panel.player1.pointsLabel.name)
            assertEquals("0", panel.player2.pointsLabel.text)
            assertEquals("5", panel.player1.gamesLabel.text)
            assertEquals("1", panel.player2.gamesLabel.text)
            // The player who leads the game has the lime value.
            assertEquals(UiKit.LIME, panel.player1.pointsLabel.foreground)
            assertEquals(UiKit.FG, panel.player2.pointsLabel.foreground)
        }
    }

    @Test
    fun theOutcomeButtonsShowTheNextStepUntilThePointHasAnOutcome() {
        SwingUtilities.invokeAndWait {
            val calls = Calls()
            val panel = panel(calls)
            panel.render(selected)
            assertEquals(ScoringButton.Kind.SECONDARY, panel.nextButton.kind)
            assertFalse(panel.player1Button.isOn)

            panel.player1Button.doClick()
            panel.noPointButton.doClick()
            panel.player2Button.doClick()
            assertEquals(listOf("outcome:P1", "outcome:NONE", "outcome:P2"), calls.list)

            panel.render(selected.copy(outcome = Outcome.P2))
            assertTrue(panel.player2Button.isOn)
            assertFalse(panel.player1Button.isOn)
            assertEquals(ScoringButton.Kind.LIME, panel.nextButton.kind, "Next is lime when the point has a score")
            assertEquals("E — Point for Sam", panel.player2Button.toolTipText)
            assertEquals("W — No point", panel.noPointButton.toolTipText)
        }
    }

    @Test
    fun previousNextAndTheStarCallTheirActions() {
        SwingUtilities.invokeAndWait {
            val calls = Calls()
            val panel = panel(calls)
            panel.render(selected.copy(hasNext = false, favorite = true))

            assertTrue(panel.previousButton.isEnabled)
            assertFalse(panel.nextButton.isEnabled)
            assertTrue(panel.favoriteButton.favorite)
            panel.previousButton.doClick()
            panel.favoriteButton.doClick()
            assertEquals(listOf("previous", "favorite"), calls.list)
            assertFalse(panel.previousButton.isFocusable, "Space must stay the play hotkey")
        }
    }

    @Test
    fun automaticScoringShowsWonTagsAndManualScoringShowsPlusButtons() {
        SwingUtilities.invokeAndWait {
            val calls = Calls()
            val panel = panel(calls)
            val gameWon = selected.copy(after = selected.after.copy(p1Pts = 0, gamesP1 = 6, lastGameWonBy = 1))
            panel.render(gameWon)
            assertTrue(panel.player1.gameTag.isVisible)
            assertTrue(panel.player1.gameTag.on)
            assertFalse(panel.player2.gameTag.on)
            assertFalse(panel.player1.gamePlus.isVisible)

            panel.render(gameWon.copy(manual = true, manualGame = Outcome.P1, outcome = Outcome.P1))
            assertFalse(panel.player1.gameTag.isVisible)
            assertTrue(panel.player1.gamePlus.isVisible)
            assertTrue(panel.player1.gamePlus.on)
            assertTrue(panel.player1.pointPlus.on, "The point \"+\" shows the outcome of the point")
            assertFalse(panel.player2.setPlus.on)

            panel.player2.setPlus.doClick()
            panel.player1.gamePlus.doClick()
            panel.player2.pointPlus.doClick()
            assertEquals(listOf("set:P2", "game:P1", "outcome:P2"), calls.list)
            assertEquals("Point for Sam [E]", panel.player2.pointPlus.toolTipText)
        }
    }

    @Test
    fun serveButtonsShowTheServerAndTheMark() {
        SwingUtilities.invokeAndWait {
            val calls = Calls()
            val panel = panel(calls)
            panel.render(selected)
            assertFalse(panel.player1.serveButton.serving)
            assertEquals("Click to mark Alex as the server of this point. S — switch the server", panel.player1.serveButton.toolTipText)

            panel.render(selected.copy(server = 2))
            assertTrue(panel.player2.serveButton.serving)
            assertFalse(panel.player2.serveButton.marked)
            assertEquals("Sam serves (computed from your serve marks). S — switch the server", panel.player2.serveButton.toolTipText)

            panel.render(selected.copy(server = 2, serverMarked = true))
            assertTrue(panel.player2.serveButton.marked)
            assertTrue(
                panel.player2.serveButton.preferredSize.width > panel.player1.serveButton.preferredSize.width,
                "A marked serve button also shows the pin",
            )

            panel.player1.serveButton.doClick()
            assertEquals(listOf("serve:P1"), calls.list)
        }
    }

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

        val icon = ScoringUi.scoreboardIcon(18, UiKit.FG)
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
        assertEquals(ScoringUi.ON_PLAYER, ScoringUi.onPlayer(java.awt.Color(0xFF, 0xD5, 0x4A)))
        assertEquals(ScoringUi.ON_PLAYER, ScoringUi.onPlayer(java.awt.Color(0x00, 0xFF, 0x00)))
        assertEquals(ScoringUi.ON_PLAYER, ScoringUi.onPlayer(UiKit.LIME))
        assertEquals(java.awt.Color.WHITE, ScoringUi.onPlayerKeyStyle(java.awt.Color.BLACK).text)
        assertEquals(ScoringUi.ON_PLAYER, ScoringUi.onPlayerKeyStyle(UiKit.LIME).text)
    }

    @Test
    fun aRacketInADarkPlayerColorGetsALightBadge() {
        assertTrue(ScoringUi.needsBadge(java.awt.Color.BLACK))
        assertTrue(ScoringUi.needsBadge(java.awt.Color(0x00, 0x5F, 0x5F)))
        assertFalse(ScoringUi.needsBadge(UiKit.LIME))
        assertFalse(ScoringUi.needsBadge(ScorePlayers().p1Color))
        assertFalse(ScoringUi.needsBadge(ScorePlayers().p2Color))
        assertEquals(21.0, ScoringUi.contrast(java.awt.Color.BLACK, java.awt.Color.WHITE), 0.01)
    }
}
