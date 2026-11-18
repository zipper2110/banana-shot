package org.litvin.ui.tabs.points

import org.junit.jupiter.api.extension.ExtendWith
import org.litvin.shared.util.PaidEdition
import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.EndingChoice
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
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-68 E2: the ending keys, the automatic winners, "Who serves?", and the confirmation of a server change. */
@ExtendWith(PaidEdition::class)
class PointsScoringEndingsTest {
    private val point = PointV1(id = "A", startMs = 10_000, endMs = 15_000)

    @Test
    fun `the top-row digit, the numpad digit, and the Shift form send the ending of the server's group`() = withScoring(
        ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("A" to Outcome.P1)),
    ) { _, scoring, requests, keys ->
        keys.press("1")
        keys.press("NUMPAD4")
        keys.press("shift 4")
        keys.press("7")
        keys.press("0")
        assertEquals(
            listOf(EndingChoice.P1_WINNER, EndingChoice.P1_ACE, EndingChoice.P1_ACE, EndingChoice.P2_WINNER),
            requests.map { (it as ScoreRequest.WithEnding).choice },
        )
        assertTrue(scoring.controls.button("points-ending-p1-ace").isVisible)
        assertTrue(!scoring.controls.button("points-ending-p2-ace").isVisible)
    }

    @Test
    fun `Z sends the unreturned serve of the server, and only the group of the server shows its button`() {
        for (server in listOf(Outcome.P1, Outcome.P2)) {
            withScoring(ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("A" to server))) { _, scoring, requests, keys ->
                keys.press("Z")
                val expected = if (server == Outcome.P1) EndingChoice.P1_UNRETURNED_SERVE else EndingChoice.P2_UNRETURNED_SERVE
                assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(expected)), requests)
                assertEquals(server == Outcome.P1, scoring.controls.button("points-ending-p1-unreturned-serve").isVisible)
                assertEquals(server == Outcome.P2, scoring.controls.button("points-ending-p2-unreturned-serve").isVisible)
            }
        }
    }

    @Test
    fun `an unreturned serve gets the winner from the server`() = withScoring(
        ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("A" to Outcome.P2)),
    ) { dir, scoring, _, _ ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P1_UNRETURNED_SERVE), "A")
        val scored = ScoreIO.readForProjectDir(dir.path)
        assertEquals(Outcome.P2, scored.outcomeOf("A"))
        assertEquals(PointDetailV1(Ending.UNRETURNED_SERVE), scored.detailOf("A"))
    }

    @Test
    fun `basic mode ignores the ending keys`() = withScoring(ScoreV1(serverMarks = mapOf("A" to Outcome.P1))) { _, _, requests, keys ->
        keys.press("1")
        keys.press("NUMPAD8")
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `an ending key writes the winner and the ending, and Q removes the ending in advanced mode`() = withScoring(
        ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("A" to Outcome.P1)),
    ) { dir, scoring, _, _ ->
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
        val scored = ScoreIO.readForProjectDir(dir.path)
        assertEquals(Outcome.P1, scored.outcomeOf("A"))
        assertEquals(PointDetailV1(Ending.UNFORCED_ERROR), scored.detailOf("A"))

        scoring.score(ScoreRequest.Winner(Outcome.P1), "A")
        val winnerOnly = ScoreIO.readForProjectDir(dir.path)
        assertEquals(Outcome.P1, winnerOnly.outcomeOf("A"))
        assertTrue(winnerOnly.details.isEmpty())
    }

    @Test
    fun `an ace gets the winner from the server`() = withScoring(
        ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("A" to Outcome.P2)),
    ) { dir, scoring, _, _ ->
        // A stored ace of the wrong group: the automatic winner gives the point to the server.
        scoring.score(ScoreRequest.WithEnding(EndingChoice.P1_ACE), "A")
        assertEquals(Outcome.P2, ScoreIO.readForProjectDir(dir.path).outcomeOf("A"))
    }

    @Test
    fun `a server change that moves the winner of an ace asks first`() {
        var answer = false
        val asked = mutableListOf<String>()
        withScoring(
            ScoreV1(
                scoringMode = ScoringMode.ADVANCED,
                outcomes = mapOf("A" to Outcome.P1),
                details = mapOf("A" to PointDetailV1(Ending.ACE)),
                serverMarks = mapOf("A" to Outcome.P1),
            ),
            confirm = { _, message -> asked += message; answer },
        ) { dir, _, _, keys ->
            keys.press("S")
            assertEquals(1, asked.size)
            assertEquals(Outcome.P1, ScoreIO.readForProjectDir(dir.path).outcomeOf("A"))

            answer = true
            keys.press("S")
            val changed = ScoreIO.readForProjectDir(dir.path)
            assertEquals(Outcome.P2, changed.serverMarks["A"])
            assertEquals(Outcome.P2, changed.outcomeOf("A"))
        }
    }

    @Test
    fun `with no serve mark, advanced mode asks who serves before the ending keys work`() = withScoring(
        ScoreV1(),
    ) { dir, scoring, requests, keys ->
        // Scoring rules → Advanced (B-73).
        scoring.saveScoringRules()
        keys.press("1")
        assertEquals(emptyList(), requests)

        scoring.controls.button("points-serve-answer-2").doClick()
        assertEquals(Outcome.P2, ScoreIO.readForProjectDir(dir.path).serverMarks["A"])
        keys.press("0")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.WithEnding(EndingChoice.P2_WINNER)), requests)
    }

    @Test
    fun `with no serve mark, advanced mode starts no point until the user answers who serves`() = withScoring(
        ScoreV1(scoringMode = ScoringMode.ADVANCED),
    ) { dir, scoring, _, _ ->
        assertFalse(scoring.controls.button("points-serve-answer-1").isVisible)
        assertFalse(scoring.allowsPointStart())
        assertTrue(scoring.controls.button("points-serve-answer-1").isVisible, "The point bar asks \"Who serves?\"")

        scoring.controls.button("points-serve-answer-1").doClick()
        assertEquals(Outcome.P1, ScoreIO.readForProjectDir(dir.path).serverMarks["A"])
        assertTrue(scoring.allowsPointStart())
    }

    @Test
    fun `basic mode starts a point with no serve mark`() = withScoring(ScoreV1()) { _, scoring, _, _ ->
        assertTrue(scoring.allowsPointStart())
    }

    private fun withScoring(
        initial: ScoreV1,
        confirm: (String, String) -> Boolean = { _, _ -> true },
        block: (File, PointsScoring, MutableList<ScoreRequest>, Keys) -> Unit,
    ) {
        val dir = Files.createTempDirectory("b68-endings").toFile()
        try {
            ScoreIO.writeForProjectDir(dir.path, initial.copy(scoreSettingsReviewed = true))
            SwingUtilities.invokeAndWait {
                val requests = mutableListOf<ScoreRequest>()
                val scoring = PointsScoring(
                    onStep = {}, onToggleFavorite = {}, onScoreChanged = {}, onError = {}, onOverlayChanged = {},
                    onScoreRequest = { requests += it }, loadFrame = {}, confirmServerChange = confirm,
                    scoreSettingsEditor = ScoreSettingsEditor { _, current -> current.copy(scoringMode = ScoringMode.ADVANCED) },
                )
                scoring.load(dir.path)
                scoring.setPoints(listOf(point))
                scoring.select("A")
                val target = JPanel()
                // The mode bar is a part of the test tree, so that its buttons can be found by name.
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

    // The ending buttons and the question "Who serves?" are in the second row of the point bar.
    private fun Container.button(name: String): JButton =
        (find(this, name) ?: (this as? PointsScoringControls)?.endingsRow?.let { find(it, name) }) as JButton

    private fun find(root: Component, name: String): Component? {
        if (root.name == name) return root
        if (root is Container) root.components.forEach { child -> find(child, name)?.let { return it } }
        return null
    }
}
