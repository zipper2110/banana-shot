package org.litvin.ui.tabs.points.ui

import org.litvin.ui.tabs.points.AutosaveState
import org.litvin.ui.tabs.points.CommentDto
import org.litvin.ui.tabs.points.PointDto
import org.litvin.ui.tabs.points.PointEventDto
import org.litvin.ui.tabs.points.PointPatch
import org.litvin.ui.tabs.points.PointsActions
import org.litvin.ui.tabs.points.PointsViewState
import org.litvin.ui.tabs.points.TimelineEventDto
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PointsTableViewTest {
    @Test
    fun eventsAreOrderedBySourceTimeAndCommentRowShowsStableNumberAndText() {
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(NoOpActions)
            view.setState(
                state(
                    PointEventDto(PointDto("pt-a", 1_000, 2_000, null)),
                    CommentDto(4, 500, 2_000, "Call was in", "#FFFFFF"),
                ),
            )

            assertEquals(listOf("Comment #4", "#1"), view.visibleTitles())
            assertTrue(view.visibleText().contains("Call was in"))
        }
    }

    @Test
    fun everyRowIs45PixelsHigh() {
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(NoOpActions)
            view.setState(
                state(
                    PointEventDto(PointDto("pt-a", 1_000, 2_000, null)),
                    CommentDto(4, 3_000, 2_000, "Call was in", "#FFFFFF"),
                ),
            )
            view.setSize(360, 400)
            view.layoutRecursively()

            val rows = view.descendants().filterIsInstance<TableRow>()
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.height == 45 }, "row heights: ${rows.map { it.height }}")
        }
    }

    @Test
    fun theSelectionShowsOnPointRowsOnly() {
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(NoOpActions)
            view.setState(
                state(
                    PointEventDto(PointDto("pt-a", 1_000, 2_000, null)),
                    CommentDto(4, 3_000, 2_000, "Call was in", "#FFFFFF"),
                ),
            )

            view.updateSelection(0)
            assertEquals("#1", view.selectedTitle())
            view.updateSelection(1)
            assertNull(view.selectedTitle(), "a comment row has no selected state")
            // The pending point has no row, so its index selects nothing.
            view.updateSelection(2)
            assertNull(view.selectedTitle())
        }
    }

    @Test
    fun favoriteStarIsVisibleWithoutHoveringTheRow() {
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(NoOpActions)
            view.setState(
                state(
                    PointEventDto(PointDto("pt-fav", 1_000, 2_000, null, favorite = true)),
                    PointEventDto(PointDto("pt-plain", 3_000, 4_000, null)),
                ),
            )

            assertTrue(findByName(view, "favorite-point-pt-fav")!!.isVisible, "favorite star should show at rest")
            assertFalse(findByName(view, "favorite-point-pt-plain")!!.isVisible, "plain point shows its star on hover only")
            assertFalse(findByName(view, "edit-point-pt-fav")!!.isVisible, "edit shows on hover only")
        }
    }

    @Test
    fun hoverButtonsMoveToTheRowUnderThePointer() {
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(NoOpActions)
            view.setState(
                state(
                    PointEventDto(PointDto("pt-a", 1_000, 2_000, null)),
                    PointEventDto(PointDto("pt-b", 3_000, 4_000, null)),
                ),
            )
            val rows = view.descendants().filterIsInstance<PointRow>()
            val starA = findByName(view, "favorite-point-pt-a")!!
            val starB = findByName(view, "favorite-point-pt-b")!!

            dispatch(rows[0], MouseEvent.MOUSE_ENTERED)
            assertTrue(starA.isVisible, "row A shows its buttons on hover")
            assertTrue(findByName(view, "delete-point-pt-a")!!.isVisible)

            // The pointer goes from row A into row B. Row A gets no exit event of its own.
            dispatch(rows[1], MouseEvent.MOUSE_ENTERED)
            assertTrue(starB.isVisible, "row B shows its buttons on hover")
            assertFalse(starA.isVisible, "row A hides its buttons when row B gets the hover")
        }
    }

    @Test
    fun aClickOnARowSelectsItAndSeeksToItsStart() {
        val calls = mutableListOf<String>()
        val actions = object : PointsActions by NoOpActions {
            override fun selectByVisualIndex(index: Int) { calls += "select $index" }
            override fun seekTo(ms: Long) { calls += "seek $ms" }
        }
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(actions)
            view.setState(state(PointEventDto(PointDto("pt-a", 1_000, 2_000, null)), CommentDto(4, 3_000, 2_000, "In", "#FFFFFF")))
            val rows = view.descendants().filterIsInstance<TableRow>()

            dispatch(rows[1], MouseEvent.MOUSE_CLICKED)

            assertEquals(listOf("select 1", "seek 3000"), calls)
        }
    }

    @Test
    fun aScrollAfterARebuildKeepsTheListAtTheEditedRow() {
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(NoOpActions)
            val points = (0 until 20).map { PointDto("pt-$it", it * 10_000L, it * 10_000L + 5_000, null) }
            view.setState(state(*points.map(::PointEventDto).toTypedArray()))
            view.setSize(360, 200)
            view.layoutRecursively()
            val viewport = view.descendants().filterIsInstance<JViewport>().single()
            viewport.viewPosition = Point(0, 15 * 45)

            // Save of an edited point rebuilds the rows, then scrolls to the point before the next layout.
            val edited = points.mapIndexed { i, p -> if (i == 16) p.copy(label = "Edited") else p }
            view.setState(state(*edited.map(::PointEventDto).toTypedArray()))
            view.scrollToVisualIndex(16)

            assertEquals(15 * 45, viewport.viewPosition.y, "the list stays at the edited row")
        }
    }

    private fun state(vararg events: TimelineEventDto) = PointsViewState(
        isPlaying = false,
        currentTimeMs = 0,
        selectedVisualIndex = null,
        pendingDraftStartMs = null,
        events = events.toList(),
        autosave = AutosaveState(false, null),
    )

    private fun dispatch(target: Component, id: Int) {
        val modifiers = if (id == MouseEvent.MOUSE_CLICKED) MouseEvent.BUTTON1_DOWN_MASK else 0
        target.dispatchEvent(MouseEvent(target, id, System.currentTimeMillis(), modifiers, 1, 1, 1, false, MouseEvent.BUTTON1))
    }

    private fun findByName(root: Component, name: String): Component? =
        root.descendants().firstOrNull { it.name == name }

    private fun Component.descendants(): List<Component> {
        val children = if (this is Container) components.flatMap { it.descendants() } else emptyList()
        return listOf(this) + children
    }

    private fun Component.layoutRecursively() {
        if (this is Container) {
            doLayout()
            components.forEach { it.layoutRecursively() }
        }
    }

    private object NoOpActions : PointsActions {
        override fun togglePlayPause() = Unit
        override fun seekTo(ms: Long) = Unit
        override fun jumpToSelected() = Unit
        override fun setStartAtPlayhead() = Unit
        override fun setEndAtPlayhead() = Unit
        override fun createPointAt(ms: Long) = Unit
        override fun editPoint(id: String, patch: PointPatch) = Unit
        override fun deletePoint(id: String) = Unit
        override fun toggleFavorite(id: String) = Unit
        override fun selectByVisualIndex(index: Int) = Unit
        override fun saveNow() = Unit
    }
}
