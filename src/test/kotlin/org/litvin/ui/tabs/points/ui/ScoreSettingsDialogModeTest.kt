package org.litvin.ui.tabs.points.ui

import org.litvin.scoring.ScoringMode
import org.litvin.scoring.Sport
import java.awt.Component
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-73: the scoring mode and "Ask error kind" are in the Scoring rules dialog of the project. */
class ScoreSettingsDialogModeTest {
    private fun settings(sport: Sport = Sport.TENNIS, mode: ScoringMode = ScoringMode.BASIC, ask: Boolean = false) =
        ScoreSettings(
            player1Name = "Vika",
            player2Name = "Dima",
            player1ColorHex = "#FF0000",
            player2ColorHex = "#0000FF",
            rules = sport.defaultRules(),
            sport = sport,
            scoringMode = mode,
            askErrorKind = ask,
        )

    private fun onDialog(initial: ScoreSettings, block: (ScoreSettingsDialog) -> Unit) {
        SwingUtilities.invokeAndWait {
            val dialog = ScoreSettingsDialog(null, initial)
            try {
                block(dialog)
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun `Save gives the selected mode and the option`() = onDialog(settings()) { dialog ->
        val ask = dialog.button("score-settings-ask-error-kind")
        // In basic mode, the option is dim.
        assertFalse(ask.isEnabled)
        dialog.button("score-settings-mode-1").doClick()
        assertTrue(ask.isEnabled)
        ask.doClick()
        dialog.button("score-settings-save").doClick()
        assertEquals(ScoringMode.ADVANCED, dialog.result?.scoringMode)
        assertEquals(true, dialog.result?.askErrorKind)
    }

    @Test
    fun `the dialog keeps the mode and the option of the project`() =
        onDialog(settings(mode = ScoringMode.ADVANCED, ask = true)) { dialog ->
            dialog.button("score-settings-save").doClick()
            assertEquals(ScoringMode.ADVANCED, dialog.result?.scoringMode)
            assertEquals(true, dialog.result?.askErrorKind)
        }

    @Test
    fun `only a tennis project shows the scoring mode`() = onDialog(settings()) { dialog ->
        val group = dialog.find("score-settings-mode-group")!!
        assertTrue(group.isVisible)
        dialog.button("score-settings-sport-1").doClick()
        assertFalse(group.isVisible)
        dialog.button("score-settings-sport-0").doClick()
        assertTrue(group.isVisible)
    }

    @Test
    fun `a padel project keeps its mode value`() = onDialog(settings(sport = Sport.PADEL, mode = ScoringMode.ADVANCED)) { dialog ->
        assertFalse(dialog.find("score-settings-mode-group")!!.isVisible)
        dialog.button("score-settings-save").doClick()
        assertEquals(ScoringMode.ADVANCED, dialog.result?.scoringMode)
    }

    private fun Container.button(name: String): AbstractButton = find(name) as AbstractButton

    private fun Component.find(name: String): Component? {
        if (this.name == name) return this
        if (this is Container) components.forEach { child -> child.find(name)?.let { return it } }
        return null
    }
}
