package org.litvin.ui.tabs.stats

import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.scoring.Sport
import org.litvin.ui.commons.Palette
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsCardPreviewTest {
    /** Paints the preview without a video frame and with an empty card page, so only the drawn court shows. */
    private fun paint(sport: Sport): BufferedImage {
        lateinit var image: BufferedImage
        SwingUtilities.invokeAndWait {
            val preview = StatsCardPreview().apply {
                this.sport = sport
                pages = listOf(ScoreboardScene(1920.0, 1080.0, emptyList()))
                setSize(640, 360)
            }
            image = BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            preview.paint(g)
            g.dispose()
        }
        return image
    }

    @Test
    fun withoutVideoFrameThePreviewDrawsTheCourtOfTheSport() {
        // A point on the court surface, away from the lines.
        val x = (640 * 0.38).toInt()
        val y = (360 * 0.82).toInt()

        assertEquals(Palette.COURT_HARD.rgb, paint(Sport.TENNIS).getRGB(x, y))
        assertEquals(Palette.COURT_PADEL.rgb, paint(Sport.PADEL).getRGB(x, y))
    }
}
