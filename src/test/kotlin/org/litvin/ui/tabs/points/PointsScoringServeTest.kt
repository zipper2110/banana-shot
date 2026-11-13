package org.litvin.ui.tabs.points

import org.litvin.points.PointV1
import org.litvin.scoring.ClipChoice
import org.litvin.scoring.ClipOutcome
import org.litvin.scoring.Ending
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetailV1
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringMode
import java.awt.Component
import java.awt.Container
import java.io.File
import java.nio.file.Files
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-68 E3: X, L, the serve labels, the clips of zero length, and the outcome of the Edit dialog. */
class PointsScoringServeTest {
    private val fault = PointV1(id = "F", startMs = 10_000, endMs = 12_000)
    private val zero = PointV1(id = "Z", startMs = 14_000, endMs = 14_000)
    private val advanced = ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("F" to Outcome.P1))

    @Test
    fun `X and L send their requests only in advanced mode`() {
        withScoring(advanced) { _, _, requests, keys, _ ->
            keys.press("X")
            keys.press("L")
            assertEquals(listOf(ScoreRequest.Fault, ScoreRequest.Let), requests)
        }
        withScoring(ScoreV1(serverMarks = mapOf("F" to Outcome.P1))) { _, _, requests, keys, _ ->
            keys.press("X")
            keys.press("L")
            assertEquals(emptyList(), requests)
        }
    }

    @Test
    fun `X is not active while the point bar asks who serves`() = withScoring(ScoreV1()) { _, scoring, requests, keys, _ ->
        scoring.controls.button("points-scoring-mode-1").doClick()
        keys.press("X")
        assertEquals(emptyList(), requests)
        keys.press("L")
        assertEquals(listOf<ScoreRequest>(ScoreRequest.Let), requests)
    }

    @Test
    fun `a fault on the 2nd serve is a double fault, and the labels show the serve number`() =
        withScoring(advanced) { dir, scoring, _, _, serves ->
            assertEquals("Serve fault", scoring.controls.button("points-serve-fault").text)
            scoring.score(ScoreRequest.Fault, "F")
            scoring.score(ScoreRequest.Fault, "Z")
            val scored = ScoreIO.readForProjectDir(dir.path)
            assertEquals(Outcome.NONE, scored.outcomeOf("F"))
            assertEquals(Outcome.P2, scored.outcomeOf("Z"), "the double fault goes to the receiver")

            // After the fault at 12 s, the next serve is a 2nd serve. After the double fault, a 1st serve.
            scoring.select(null)
            scoring.moveTo(13_000)
            assertEquals(2, serves.last())
            assertEquals("Double fault", scoring.controls.button("points-serve-fault").text)
            scoring.moveTo(20_000)
            assertEquals(1, serves.last())
        }

    @Test
    fun `basic mode shows Point start`() = withScoring(ScoreV1()) { _, scoring, _, _, serves ->
        scoring.moveTo(13_000)
        assertNull(serves.last())
        assertFalse(scoring.controls.button("points-serve-fault").isVisible)
    }

    @Test
    fun `a clip of zero length takes only a fault`() = withScoring(advanced) { dir, scoring, _, _, _ ->
        scoring.score(ScoreRequest.Let, "Z")
        scoring.score(ScoreRequest.Winner(Outcome.P1), "Z")
        assertTrue(ScoreIO.readForProjectDir(dir.path).details.isEmpty())

        scoring.score(ScoreRequest.Fault, "Z")
        assertEquals(PointDetailV1(Ending.SERVE_FAULT), ScoreIO.readForProjectDir(dir.path).detailOf("Z"))
        // The ending buttons and L do not work on it. X and the serve button do.
        scoring.select("Z")
        assertFalse(scoring.controls.button("points-ending-p1-winner").isEnabled)
        assertFalse(scoring.controls.button("points-let").isEnabled)
        assertTrue(scoring.controls.button("points-serve-fault").isEnabled)
        assertTrue(scoring.controls.serveButton.isEnabled)
    }

    @Test
    fun `the edit dialog form and its outcome change`() = withScoring(advanced) { dir, scoring, _, _, _ ->
        scoring.score(ScoreRequest.Fault, "F")
        val form = scoring.outcomeForm("F")!!
        assertEquals(ClipOutcome(ClipChoice.FAULT), form.current)
        assertEquals(1, form.serve)
        assertEquals(1, form.server)
        assertFalse(form.zeroLength)
        assertTrue(scoring.outcomeForm("Z")!!.zeroLength)

        scoring.editOutcome("F", ClipOutcome(ClipChoice.POINT, 2, Ending.UNFORCED_ERROR))
        val edited = ScoreIO.readForProjectDir(dir.path)
        assertEquals(Outcome.P2, edited.outcomeOf("F"))
        assertEquals(PointDetailV1(Ending.UNFORCED_ERROR), edited.detailOf("F"))

        // A clip of zero length can only be a fault.
        scoring.editOutcome("Z", ClipOutcome(ClipChoice.LET))
        assertTrue(ScoreIO.readForProjectDir(dir.path).detailOf("Z").isEmpty())
    }

    @Test
    fun `basic mode has no outcome in the edit dialog`() = withScoring(ScoreV1()) { _, scoring, _, _, _ ->
        assertNull(scoring.outcomeForm("F"))
    }

    @Test
    fun `the pack check finds a fault clip at the playhead`() = withScoring(advanced) { _, scoring, _, _, _ ->
        assertFalse(scoring.packAtHasServeClip(13_000))
        scoring.score(ScoreRequest.Fault, "F")
        assertTrue(scoring.packAtHasServeClip(13_000))
    }

    private fun withScoring(
        initial: ScoreV1,
        block: (File, PointsScoring, MutableList<ScoreRequest>, PointsScoringEndingsTest.Keys, MutableList<Int?>) -> Unit,
    ) {
        val dir = Files.createTempDirectory("b68-serve").toFile()
        try {
            ScoreIO.writeForProjectDir(dir.path, initial.copy(scoreSettingsReviewed = true))
            SwingUtilities.invokeAndWait {
                val requests = mutableListOf<ScoreRequest>()
                val serves = mutableListOf<Int?>()
                val scoring = PointsScoring(
                    onStep = {}, onToggleFavorite = {}, onScoreChanged = {}, onError = {}, onOverlayChanged = {},
                    onScoreRequest = { requests += it }, loadFrame = {}, onServeNumber = { serves += it },
                )
                scoring.load(dir.path)
                scoring.setPoints(listOf(fault, zero))
                scoring.select("F")
                val target = JPanel()
                scoring.controls.add(scoring.modeBar)
                scoring.installKeys(target) { false }
                block(dir, scoring, requests, PointsScoringEndingsTest.Keys(target), serves)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun Container.button(name: String): JButton = find(this, name) as JButton

    private fun find(root: Component, name: String): Component? {
        if (root.name == name) return root
        if (root is Container) root.components.forEach { child -> find(child, name)?.let { return it } }
        return null
    }
}
