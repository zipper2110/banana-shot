package org.litvin.ui.tabs.points.ui

import org.junit.jupiter.api.Test
import org.litvin.ui.tabs.points.PointDto
import org.litvin.ui.tabs.points.PointPatch
import org.litvin.ui.tabs.points.PointsActions
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditPointDialogTest {
    @Test
    fun anInvalidTimeShowsTheErrorLineAndKeepsTheDialogOpen() {
        val edits = mutableListOf<PointPatch>()
        SwingUtilities.invokeAndWait {
            val dialog = EditPointDialog.build(JPanel(), PointDto("p1", 1_000, 2_000, null), 1, RecordingActions(edits))
            try {
                (dialog.find("points-edit-start") as JTextField).text = "abc"
                (dialog.find("points-edit-save") as JButton).doClick()

                assertTrue(edits.isEmpty())
                assertTrue(dialog.isDisplayable, "the dialog stays open")
                assertTrue(dialog.descendants().filterIsInstance<JLabel>().any { it.text == "Invalid time format" })
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun saveSendsTheNewTimesAndLabelAndClosesTheDialog() {
        val edits = mutableListOf<PointPatch>()
        SwingUtilities.invokeAndWait {
            val dialog = EditPointDialog.build(JPanel(), PointDto("p1", 1_000, 2_000, null), 4, RecordingActions(edits))
            (dialog.find("points-edit-start") as JTextField).text = "00:00:01.5"
            (dialog.find("points-edit-label") as JTextField).text = "ace"
            (dialog.find("points-edit-save") as JButton).doClick()

            assertEquals(listOf(PointPatch(startMs = 1_500, endMs = 2_000, label = "ace")), edits)
            assertFalse(dialog.isDisplayable, "the dialog closes after a save")
            assertEquals("Edit point #4", dialog.title)
        }
    }

    private fun Component.find(name: String): Component = descendants().first { it.name == name }

    private fun Component.descendants(): List<Component> {
        val children = if (this is Container) components.flatMap { it.descendants() } else emptyList()
        return listOf(this) + children
    }

    private class RecordingActions(private val edits: MutableList<PointPatch>) : PointsActions {
        override fun togglePlayPause() = Unit
        override fun seekTo(ms: Long) = Unit
        override fun jumpToSelected() = Unit
        override fun setStartAtPlayhead() = Unit
        override fun setEndAtPlayhead() = Unit
        override fun createPointAt(ms: Long) = Unit
        override fun editPoint(id: String, patch: PointPatch) { edits += patch }
        override fun deletePoint(id: String) = Unit
        override fun toggleFavorite(id: String) = Unit
        override fun selectByVisualIndex(index: Int) = Unit
        override fun saveNow() = Unit
    }
}
