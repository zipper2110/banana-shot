package org.litvin.ui.tabs.scoring.ui

import org.junit.jupiter.api.Test
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoringEngine
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScoringPointsListTest {
    private val players = ScorePlayers("Alex", "Sam")

    private fun data(points: List<PointV1>, outcomes: Map<String, Outcome>, serverMarks: Map<String, Outcome> = emptyMap()) =
        PointsListData(
            points = points,
            outcomes = outcomes,
            states = ScoringEngine.timeline(points, outcomes).statesAfterPoint,
            serverMarks = serverMarks,
            players = players,
        )

    @Test
    fun headCountsTheScoredAndFavoritePoints() {
        SwingUtilities.invokeAndWait {
            val points = (1..4).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000, favorite = it == 2) }
            val list = ScoringPointsList({}, {}, {})
            list.setData(data(points, mapOf("p1" to Outcome.P1, "p2" to Outcome.NONE)))

            assertEquals(4, list.rowCount())
            assertEquals(2, list.head.scored)
            assertEquals(4, list.head.total)
            assertEquals(1, list.head.favorites)
        }
    }

    @Test
    fun rowButtonsToggleTheFavoriteAndGoToThePointWithoutTakingTheFocus() {
        SwingUtilities.invokeAndWait {
            val favorites = mutableListOf<Int>()
            val goTo = mutableListOf<Int>()
            val points = (1..2).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000, favorite = it == 1) }
            val list = ScoringPointsList({}, { favorites += it }, { goTo += it })
            list.setData(data(points, emptyMap()))

            val first = assertNotNull(list.row(0))
            val second = assertNotNull(list.row(1))
            assertTrue(first.favoriteButton.isVisible, "The star of a favorite point stays visible")
            assertFalse(second.favoriteButton.isVisible, "An empty star shows only on hover")
            assertFalse(second.goToButton.isVisible, "Go to point shows only on hover")
            second.hovered = true
            assertTrue(second.favoriteButton.isVisible)
            assertTrue(second.goToButton.isVisible)
            assertEquals("scoring-go-to-point-p2", second.goToButton.name)

            second.favoriteButton.doClick()
            second.goToButton.doClick()
            assertEquals(listOf(1), favorites)
            assertEquals(listOf(1), goTo)
            assertFalse(second.favoriteButton.isFocusable)
            assertFalse(second.goToButton.isFocusable)
        }
    }

    @Test
    fun rowsNameTheGameSetAndServeMarksInTooltips() {
        SwingUtilities.invokeAndWait {
            // 24 straight points for Alex: a game every 4th point, and the set on the 24th.
            val points = (1..24).map { PointV1(id = "p$it", startMs = it * 1_000, endMs = it * 1_000 + 500) }
            val list = ScoringPointsList({}, {}, {})
            list.setData(data(points, points.associate { it.id to Outcome.P1 }, serverMarks = mapOf("p1" to Outcome.P2)))

            fun tooltipAtMarks(index: Int, offset: Int): String? {
                val row = list.row(index)!!
                row.setSize(380, ScoringPointRow.HEIGHT)
                val x = ListColumns(380).marksRight - offset
                return row.getToolTipText(MouseEvent(row, MouseEvent.MOUSE_MOVED, 0, 0, x, 20, 0, false))
            }
            assertEquals("Game won by Alex", tooltipAtMarks(3, 5))
            assertEquals("Set won by Alex", tooltipAtMarks(23, 5), "A point that wins the set shows SET only")
            assertEquals(null, tooltipAtMarks(4, 5))
            assertEquals("Serve marked: Sam serves", tooltipAtMarks(0, 5))
        }
    }

    @Test
    fun clickOnARowSelectsItsPoint() {
        SwingUtilities.invokeAndWait {
            val selected = mutableListOf<Int>()
            val points = (1..3).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
            val list = ScoringPointsList({ selected += it }, {}, {})
            list.setData(data(points, emptyMap()))
            val row = list.row(2)!!
            row.mouseListeners.forEach {
                it.mouseClicked(MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 40, 20, 1, false, MouseEvent.BUTTON1))
            }
            assertEquals(listOf(2), selected)

            list.setSelectedIndex(2, scroll = false)
            assertTrue(list.row(2)!!.selected)
            assertFalse(list.row(0)!!.selected)
        }
    }
}
