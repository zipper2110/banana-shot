package org.litvin.ui.tabs.points

import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStart
import java.awt.geom.Rectangle2D
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-63: the Points tab preview removes the board at once when a start setting change hides it. */
class PointsScoringPreviewTest {
    private val area = Rectangle2D.Double(0.0, 0.0, 1920.0, 1080.0)

    @Test
    fun `a change from the video start to the first point removes the board before the first point`() {
        val dir = Files.createTempDirectory("b63-preview").toFile()
        try {
            val point = PointV1(id = "A", startMs = 10_000, endMs = 15_000)
            fun save(start: ScoreboardStart) = ScoreIO.writeForProjectDir(
                dir.path, ScoreV1(outcomes = mapOf("A" to Outcome.P1), scoreboard = ScoreboardSettingsV1(start = start)),
            )
            var overlayChanges = 0
            SwingUtilities.invokeAndWait {
                val scoring = PointsScoring(
                    onStep = {}, onToggleFavorite = {}, onScoreChanged = {}, onError = {},
                    onOverlayChanged = { overlayChanges++ }, onOutcomeRequest = {}, loadFrame = {},
                )
                save(ScoreboardStart.VIDEO_START)
                scoring.load(dir.path)
                scoring.setPoints(listOf(point))
                scoring.moveTo(0L)
                assertTrue(scoring.scoreboardEvents(area).isNotEmpty(), "The video start shows the board at 0 s")

                save(ScoreboardStart.FIRST_POINT)
                val before = overlayChanges
                scoring.load(dir.path)
                assertTrue(scoring.scoreboardEvents(area).isEmpty(), "The first point hides the board at 0 s")
                assertTrue(overlayChanges > before, "The video must draw the overlay again")

                // A seek that does not change the board does not draw the overlay again.
                val afterChange = overlayChanges
                scoring.moveTo(1_000L)
                assertEquals(afterChange, overlayChanges)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
