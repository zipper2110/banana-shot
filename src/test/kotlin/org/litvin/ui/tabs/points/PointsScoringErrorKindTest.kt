package org.litvin.ui.tabs.points

import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.EndingChoice
import org.litvin.scoring.ErrorKind
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetailV1
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringMode
import org.litvin.ui.tabs.points.ui.ScoreSettingsEditor
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.io.File
import java.nio.file.Files
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-68 E5: the error kind question, its strip under the point bar, and the project option. */
class PointsScoringErrorKindTest {
    private val point = PointV1(id = "A", startMs = 10_000, endMs = 15_000)
    private val asking = ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("A" to Outcome.P1), askErrorKind = true)

    @Test
    fun `with the option on, an error ending opens the question and a digit answers it`() = withScoring(asking) { dir, scoring, requests, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        assertTrue(scoring.strip().isVisible)

        keys.press("3")
        assertEquals(PointDetailV1(Ending.UNFORCED_ERROR, ErrorKind.WIDE), ScoreIO.readForProjectDir(dir.path).detailOf("A"))
        // The digit answered the question and selected no ending.
        assertEquals(emptyList(), requests)

        // The question is closed: the next digit selects an ending.
        keys.press("2")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(EndingChoice.P2_FORCED_ERROR)), requests)
    }

    @Test
    fun `the numpad digit also answers the question`() = withScoring(asking) { dir, scoring, requests, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P1_FORCED_ERROR), "A")
        keys.press("NUMPAD1")
        assertEquals(ErrorKind.NET, ScoreIO.readForProjectDir(dir.path).detailOf("A").errorKind)
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `with the option off, an error ending does not open the question and shows no strip`() = withScoring(
        asking.copy(askErrorKind = false),
    ) { dir, scoring, requests, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        assertFalse(scoring.strip().isVisible)
        keys.press("1")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(EndingChoice.P1_WINNER)), requests)
        assertNull(ScoreIO.readForProjectDir(dir.path).detailOf("A").errorKind)
    }

    @Test
    fun `a winner does not open the question`() = withScoring(asking) { _, scoring, requests, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P1_WINNER), "A")
        assertFalse(scoring.strip().isVisible)
        keys.press("2")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(EndingChoice.P2_FORCED_ERROR)), requests)
    }

    @Test
    fun `R closes the question with no answer`() = withScoring(asking) { dir, scoring, requests, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        keys.press("R")
        keys.press("1")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(EndingChoice.P1_WINNER)), requests)
        assertNull(ScoreIO.readForProjectDir(dir.path).detailOf("A").errorKind)
    }

    @Test
    fun `C closes the question with no answer`() = withScoring(asking) { _, scoring, requests, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        scoring.setPending(20_000, "B")
        scoring.onPointStart()
        keys.press("1")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(EndingChoice.P1_WINNER)), requests)
    }

    @Test
    fun `Esc closes the open question, and only then can Esc cancel the pending point`() = withScoring(asking) { _, scoring, _, _ ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        // True: Esc closed the question, so the panel does not cancel the pending point.
        assertTrue(scoring.onEscape())
        assertFalse(scoring.onEscape())
    }

    @Test
    fun `the strip shows for the active error clip after the question closes, and a click changes the answer`() = withScoring(
        asking,
    ) { dir, scoring, requests, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        assertTrue(scoring.onEscape())
        assertTrue(scoring.strip().isVisible)

        scoring.strip().button("points-error-kind-net").doClick()
        assertEquals(ErrorKind.NET, ScoreIO.readForProjectDir(dir.path).detailOf("A").errorKind)
        scoring.strip().button("points-error-kind-long").doClick()
        assertEquals(ErrorKind.LONG, ScoreIO.readForProjectDir(dir.path).detailOf("A").errorKind)
        assertTrue(scoring.strip().isVisible)

        // No active clip hides the strip.
        scoring.select(null)
        assertFalse(scoring.strip().isVisible)
        scoring.select("A")
        assertTrue(scoring.strip().isVisible)
        // The question is closed, so the digit selects an ending.
        keys.press("1")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(EndingChoice.P1_WINNER)), requests)
    }

    @Test
    fun `a click on the strip opens the question again`() = withScoring(
        asking.copy(outcomes = mapOf("A" to Outcome.P1), details = mapOf("A" to PointDetailV1(Ending.FORCED_ERROR))),
    ) { dir, scoring, requests, keys ->
        assertTrue(scoring.strip().isVisible)
        scoring.strip().clickOnStrip()
        keys.press("2")
        assertEquals(ErrorKind.LONG, ScoreIO.readForProjectDir(dir.path).detailOf("A").errorKind)
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `a change of the ending to a non-error removes the strip`() = withScoring(asking) { dir, scoring, _, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        keys.press("1")
        assertTrue(scoring.strip().isVisible)

        scoring.score(ScoreRequest.WithEnding(EndingChoice.P1_WINNER), "A")
        assertFalse(scoring.strip().isVisible)
        assertEquals(PointDetailV1(Ending.WINNER), ScoreIO.readForProjectDir(dir.path).detailOf("A"))
    }

    @Test
    fun `Q on the clip of the open question closes it`() = withScoring(asking) { _, scoring, requests, keys ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        scoring.score(ScoreRequest.Winner(Outcome.P1), "A")
        assertFalse(scoring.strip().isVisible)
        keys.press("1")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(EndingChoice.P1_WINNER)), requests)
    }

    @Test
    fun `the option in Scoring rules turns the question on and off for the project`() = withScoring(
        asking.copy(askErrorKind = false),
    ) { dir, scoring, _, _ ->
        // The fake Scoring rules dialog turns the option over at each save (B-73).
        scoring.saveScoringRules()
        assertTrue(ScoreIO.readForProjectDir(dir.path).askErrorKind)
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        assertTrue(scoring.strip().isVisible)
        scoring.saveScoringRules()
        assertFalse(ScoreIO.readForProjectDir(dir.path).askErrorKind)
        assertFalse(scoring.strip().isVisible)
    }

    @Test
    fun `a new project gets the default of the option from Settings`() {
        val dir = Files.createTempDirectory("b68-error-kind-default").toFile()
        try {
            val defaults = object : ScoringModeDefaults {
                override fun load() = ScoringMode.ADVANCED
                override fun save(mode: ScoringMode) = Unit
                override fun loadAskErrorKind() = true
            }
            SwingUtilities.invokeAndWait {
                val scoring = PointsScoring(
                    onStep = {}, onToggleFavorite = {}, onScoreChanged = {}, onError = {}, onOverlayChanged = {},
                    onScoreRequest = {}, loadFrame = {}, modeDefaults = defaults,
                )
                scoring.load(dir.path)
            }
            val score = ScoreIO.readForProjectDir(dir.path)
            assertEquals(ScoringMode.ADVANCED, score.scoringMode)
            assertTrue(score.askErrorKind)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun PointsScoring.strip(): JComponent = errorStrip

    private fun JComponent.clickOnStrip() {
        mouseListeners.forEach {
            it.mouseClicked(java.awt.event.MouseEvent(this, java.awt.event.MouseEvent.MOUSE_CLICKED, 0L, 0, 5, 5, 1, false))
        }
    }

    private fun withScoring(
        initial: ScoreV1,
        block: (File, PointsScoring, MutableList<ScoreRequest>, Keys) -> Unit,
    ) {
        val dir = Files.createTempDirectory("b68-error-kind").toFile()
        try {
            ScoreIO.writeForProjectDir(dir.path, initial.copy(scoreSettingsReviewed = true))
            SwingUtilities.invokeAndWait {
                val requests = mutableListOf<ScoreRequest>()
                val scoring = PointsScoring(
                    onStep = {}, onToggleFavorite = {}, onScoreChanged = {}, onError = {}, onOverlayChanged = {},
                    onScoreRequest = { requests += it }, loadFrame = {},
                    scoreSettingsEditor = ScoreSettingsEditor { _, current -> current.copy(askErrorKind = !current.askErrorKind) },
                )
                scoring.load(dir.path)
                scoring.setPoints(listOf(point))
                scoring.select("A")
                val target = JPanel()
                scoring.installKeys(target) { false }
                block(dir, scoring, requests, Keys(target))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    class Keys(private val target: JComponent) {
        fun press(stroke: String) {
            val name = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(stroke))
            target.actionMap.get(name).actionPerformed(ActionEvent(target, ActionEvent.ACTION_PERFORMED, stroke))
        }
    }

    private fun Container.button(name: String): AbstractButton = find(name) as AbstractButton

    private fun Component.find(name: String): Component? {
        if (this.name == name) return this
        if (this is Container) components.forEach { child -> child.find(name)?.let { return it } }
        return null
    }
}
