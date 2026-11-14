package org.litvin.ui.tabs.points.ui

import org.litvin.points.PointV1
import org.litvin.scoring.EndingChoice
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringMode
import org.litvin.ui.tabs.points.PointsScoring
import org.litvin.ui.tabs.points.ScoreRequest
import java.awt.Dimension
import java.nio.file.Files
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-72: the slot of the error kind strip keeps its height, so that the video does not move. */
class ErrorKindSlotTest {
    private val stripSize = Dimension(300, 41)

    private fun strip() = JPanel().apply {
        preferredSize = stripSize
        isVisible = false
    }

    @Test
    fun `a reserved slot keeps the height of the strip when the strip shows or hides`() {
        val strip = strip()
        val slot = ErrorKindSlot(strip).apply { reserved = true }
        assertEquals(41, slot.preferredSize.height)
        strip.isVisible = true
        assertEquals(41, slot.preferredSize.height)
        strip.isVisible = false
        assertEquals(41, slot.preferredSize.height)
    }

    @Test
    fun `a slot that is not reserved has a height only while the strip shows`() {
        val strip = strip()
        val slot = ErrorKindSlot(strip)
        assertEquals(0, slot.preferredSize.height)
        strip.isVisible = true
        assertEquals(41, slot.preferredSize.height)
    }

    @Test
    fun `with Ask error kind on, the slot keeps its height when an error ending shows the strip`() {
        val asking = ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("A" to Outcome.P1), askErrorKind = true)
        withScoring(asking) { scoring ->
            assertTrue(scoring.errorStripSlot.reserved)
            assertFalse(scoring.errorStrip.isVisible)
            val before = scoring.errorStripSlot.preferredSize.height
            assertTrue(before > 0)

            scoring.score(ScoreRequest.WithEnding(EndingChoice.P2_UNFORCED_ERROR), "A")
            assertTrue(scoring.errorStrip.isVisible)
            assertEquals(before, scoring.errorStripSlot.preferredSize.height)
        }
    }

    @Test
    fun `with Ask error kind off, the slot is not reserved and has no height`() {
        val notAsking = ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("A" to Outcome.P1), askErrorKind = false)
        withScoring(notAsking) { scoring ->
            assertFalse(scoring.errorStripSlot.reserved)
            assertEquals(0, scoring.errorStripSlot.preferredSize.height)
        }
    }

    @Test
    fun `in basic mode, the slot is not reserved`() {
        withScoring(ScoreV1(askErrorKind = true)) { scoring ->
            assertFalse(scoring.errorStripSlot.reserved)
        }
    }

    private fun withScoring(initial: ScoreV1, block: (PointsScoring) -> Unit) {
        val dir = Files.createTempDirectory("b72-error-kind-slot").toFile()
        try {
            ScoreIO.writeForProjectDir(dir.path, initial.copy(scoreSettingsReviewed = true))
            SwingUtilities.invokeAndWait {
                val scoring = PointsScoring(
                    onStep = {}, onToggleFavorite = {}, onScoreChanged = {}, onError = {}, onOverlayChanged = {},
                    onScoreRequest = {}, loadFrame = {},
                )
                scoring.load(dir.path)
                scoring.setPoints(listOf(PointV1(id = "A", startMs = 10_000, endMs = 15_000)))
                scoring.select("A")
                block(scoring)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
