package org.litvin.ui.tabs.points.ui

import org.junit.jupiter.api.Test
import org.litvin.scoring.ClipChoice
import org.litvin.scoring.ClipOutcome
import org.litvin.scoring.Ending
import org.litvin.ui.tabs.points.PointDto
import org.litvin.ui.tabs.points.PointOutcomeForm
import org.litvin.ui.tabs.points.PointPatch
import org.litvin.ui.tabs.points.PointsActions
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun deleteClosesTheDialogAndLeavesTheConfirmationToThePanel() {
        val deletes = mutableListOf<String>()
        SwingUtilities.invokeAndWait {
            val dialog = EditPointDialog.build(JPanel(), PointDto("p1", 1_000, 2_000, null), 1, RecordingActions(mutableListOf(), deletes))
            (dialog.find("points-edit-delete") as JButton).doClick()

            // The panel asks for the confirmation (B-38 T1), so the dialog does not ask a second time.
            assertEquals(listOf("p1"), deletes)
            assertFalse(dialog.isDisplayable, "the dialog closes before the question")
        }
    }

    @Test
    fun advancedModeShowsTheOutcomeAndSendsOnlyAChangedOutcome() {
        val form = PointOutcomeForm(
            current = ClipOutcome(ClipChoice.POINT, 1, Ending.WINNER),
            serve = 2, doubleFault = false, server = 2, p1Name = "Ana", p2Name = "Ben", zeroLength = false,
        )
        val edits = mutableListOf<PointPatch>()
        SwingUtilities.invokeAndWait {
            val unchanged = EditPointDialog.build(JPanel(), PointDto("p1", 1_000, 2_000, null), 1, RecordingActions(edits, form = form))
            assertEquals("2nd serve", (unchanged.find("points-edit-serve") as JLabel).text)
            (unchanged.find("points-edit-save") as JButton).doClick()
            assertNull(edits.single().outcome)

            val dialog = EditPointDialog.build(JPanel(), PointDto("p1", 1_000, 2_000, null), 1, RecordingActions(edits, form = form))
            val ending = dialog.find("points-edit-ending") as JComboBox<*>
            // The labels name the player who made the ending: an error of Ben gives the point to Ana.
            assertEquals("Ben unforced error", ending.getItemAt(4).toString())
            assertFalse(dialog.find("points-edit-error-kind").isEnabled)
            ending.selectedIndex = 4
            assertTrue(dialog.find("points-edit-error-kind").isEnabled)
            // An ace goes to the server: Ben.
            ending.selectedIndex = 1
            (dialog.find("points-edit-save") as JButton).doClick()
            assertEquals(ClipOutcome(ClipChoice.POINT, 2, Ending.ACE), edits.last().outcome)
        }
    }

    @Test
    fun aClipOfZeroLengthCanOnlyBeAFault() {
        val form = PointOutcomeForm(
            current = ClipOutcome(ClipChoice.FAULT),
            serve = 1, doubleFault = false, server = 1, p1Name = "Ana", p2Name = "Ben", zeroLength = true,
        )
        val edits = mutableListOf<PointPatch>()
        SwingUtilities.invokeAndWait {
            val dialog = EditPointDialog.build(JPanel(), PointDto("z", 5_000, 5_000, null), 1, RecordingActions(edits, form = form))
            try {
                assertFalse(dialog.find("points-edit-winner").isEnabled)
                (dialog.find("points-edit-kind") as JComboBox<*>).selectedIndex = 2
                (dialog.find("points-edit-save") as JButton).doClick()
                assertTrue(edits.isEmpty())
                assertTrue(dialog.isDisplayable)
            } finally {
                dialog.dispose()
            }
        }
    }

    private fun Component.find(name: String): Component = descendants().first { it.name == name }

    private fun Component.descendants(): List<Component> {
        val children = if (this is Container) components.flatMap { it.descendants() } else emptyList()
        return listOf(this) + children
    }

    private class RecordingActions(
        private val edits: MutableList<PointPatch>,
        private val deletes: MutableList<String> = mutableListOf(),
        private val form: PointOutcomeForm? = null,
    ) : PointsActions {
        override fun outcomeForm(id: String): PointOutcomeForm? = form
        override fun togglePlayPause() = Unit
        override fun seekTo(ms: Long) = Unit
        override fun jumpToSelected() = Unit
        override fun setStartAtPlayhead() = Unit
        override fun createPointAt(ms: Long) = Unit
        override fun editPoint(id: String, patch: PointPatch) { edits += patch }
        override fun deletePoint(id: String) { deletes += id }
        override fun toggleFavorite(id: String) = Unit
        override fun selectByVisualIndex(index: Int) = Unit
        override fun saveNow() = Unit
    }
}
