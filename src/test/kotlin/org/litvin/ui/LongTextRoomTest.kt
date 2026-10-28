package org.litvin.ui

import org.litvin.ui.commons.ChangePointsCard
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.ui.SwingTimelineComponent
import org.litvin.ui.tabs.stats.captionLines
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-11 E3-T22: a longer translation fits. A column or a card grows, or a caption breaks into 2 lines. The English
 * layout does not change.
 */
class LongTextRoomTest {
    @Test
    fun `a long In video caption of the statistics breaks into 2 lines`() {
        val font = UiKit.trackedFont(11f, 0.05, UiKit.Weight.SEMIBOLD)
        val room = 60f
        assertEquals(listOf("IN VIDEO"), captionLines("IN VIDEO", room, font))
        val french = captionLines("DANS LA VIDÉO", room, font)
        assertEquals(listOf("DANS LA", "VIDÉO"), french)
        french.forEach { assertTrue(UiKit.textWidth(it, font) <= room, "\"$it\" is wider than $room px") }
        assertEquals(listOf("WORD"), captionLines("WORD", 1f, font))
    }

    @Test
    fun `the label column of the timeline fits a longer track name and its count`() {
        assertEquals(124, SwingTimelineComponent.gutterWidth(listOf("VIDEO", "MARKS", "COMMENTS")))
        assertTrue(SwingTimelineComponent.gutterWidth(listOf("VIDEO", "MARKEN", "KOMMENTARE")) > 124)
    }

    @Test
    fun `the change points card grows when its hint needs a third line`() {
        var short = 0
        var long = 0
        SwingUtilities.invokeAndWait {
            val card = ChangePointsCard("test", "Did the phone move?\nAdd a change point there.", {}, {}, {}, {})
            short = card.heightForWidth(WIDTH)
            card.setHint("Le téléphone a bougé ?\n" + "Ajoutez un repère de changement à cet endroit. La transition dure 1 seconde. ".repeat(2))
            long = card.heightForWidth(WIDTH)
        }
        assertTrue(long > short, "the card has $long px with a long hint and $short px with a short hint")
    }

    private companion object {
        const val WIDTH = 440
    }
}
