package org.litvin.ui.tabs.points.ui

import org.litvin.ui.tabs.points.AutosaveState
import org.litvin.ui.tabs.points.PointDto
import org.litvin.ui.tabs.points.PointEventDto
import org.litvin.ui.tabs.points.PointPatch
import org.litvin.ui.tabs.points.PointsActions
import org.litvin.ui.tabs.points.PointsViewState
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-68 E3b: the packs in the points list of advanced mode. */
class PointsTablePacksTest {
    private val infos = mapOf(
        "F" to PointRowInfo(1, true, PackPosition.FIRST, RowLabel(RowServe.FIRST, RowEnding.FAULT), dimmed = true),
        "P" to PointRowInfo(1, false, PackPosition.LAST, RowLabel(RowServe.SECOND, RowEnding.ACE), dimmed = false),
        "W" to PointRowInfo(2, true, PackPosition.SINGLE, RowLabel(RowServe.NO_POINT), dimmed = false),
        "Z" to PointRowInfo(3, true, PackPosition.FIRST, RowLabel(RowServe.FIRST, RowEnding.FAULT), dimmed = true),
        "D" to PointRowInfo(3, false, PackPosition.LAST, RowLabel(RowServe.SECOND, RowEnding.DOUBLE_FAULT), dimmed = false),
    )

    @Test
    fun `the rows show the pack numbers and the labels, and a gap around each pack`() {
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(NoOpActions)
            view.pointRowInfo = { infos[it] }
            view.setState(
                PointsViewState(
                    isPlaying = false,
                    currentTimeMs = 0,
                    selectedVisualIndex = null,
                    pendingDraftStartMs = null,
                    events = listOf(
                        point("F", 1_000, 2_000),
                        point("P", 3_000, 6_000),
                        point("W", 7_000, 8_000),
                        // A fault clip of zero length.
                        point("Z", 9_000, 9_000),
                        point("D", 10_000, 11_000),
                    ),
                    autosave = AutosaveState(false, null),
                ),
            )
            view.setSize(360, 600)
            view.layoutRecursively()

            assertEquals(listOf("#1", "#1", "#2", "#3", "#3"), view.visibleTitles())
            assertEquals(
                listOf("1st serve · Fault", "2nd serve · Ace", "No point", "1st serve · Fault", "2nd serve · Double fault"),
                view.visibleLabels(),
            )
            val rows = view.descendants().filterIsInstance<TableRow>()
            val gap = TableRow.PACK_GAP
            val h = TableRow.HEIGHT
            assertEquals(listOf(0, h, 2 * h + gap, 3 * h + 2 * gap, 4 * h + 2 * gap), rows.map { it.y })

            // The rows paint with no error, also the row of zero length.
            val image = BufferedImage(360, 600, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try {
                rows.forEach { row ->
                    val child = g.create(0, row.y, row.width, row.height)
                    try {
                        row.paint(child)
                    } finally {
                        child.dispose()
                    }
                }
            } finally {
                g.dispose()
            }
        }
    }

    @Test
    fun `basic mode has no labels and numbers each clip`() {
        SwingUtilities.invokeAndWait {
            val view = PointsTableView(NoOpActions)
            view.setState(
                PointsViewState(
                    isPlaying = false,
                    currentTimeMs = 0,
                    selectedVisualIndex = null,
                    pendingDraftStartMs = null,
                    events = listOf(point("F", 1_000, 1_000), point("P", 3_000, 6_000)),
                    autosave = AutosaveState(false, null),
                ),
            )
            assertEquals(listOf("#1", "#2"), view.visibleTitles())
            assertEquals(listOf(null, null), view.visibleLabels())
        }
    }

    private fun point(id: String, start: Long, end: Long) = PointEventDto(PointDto(id, start, end, null))

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
        override fun createPointAt(ms: Long) = Unit
        override fun editPoint(id: String, patch: PointPatch) = Unit
        override fun deletePoint(id: String) = Unit
        override fun toggleFavorite(id: String) = Unit
        override fun selectByVisualIndex(index: Int) = Unit
        override fun saveNow() = Unit
    }
}
