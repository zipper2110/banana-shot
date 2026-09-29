package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.formatSeconds
import org.litvin.ui.tabs.points.PointDto
import org.litvin.ui.tabs.points.PointPatch
import org.litvin.ui.tabs.points.PointsActions
import java.awt.Component
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.JTextField

/**
 * EditPointDialog — a small modal editor for a single point.
 *
 * Responsibilities:
 * - Show/edit fields: start time, end time, label
 * - Save the changes, or delete the point after a confirmation; Cancel just closes the dialog
 * - Communicate changes strictly via PointsActions
 * - Keep work lightweight on EDT (no long-running operations)
 */
object EditPointDialog {

    /**
     * Shows the dialog for the point with the ordinal [number]. Save calls actions.editPoint().
     * Delete asks for confirmation and calls actions.deletePoint(). A time that does not parse shows
     * an error line and keeps the dialog open.
     */
    fun show(parent: Component, point: PointDto, number: Int, actions: PointsActions) {
        build(parent, point, number, actions).apply {
            setLocationRelativeTo(parent)
            isVisible = true
        }
    }

    /** Builds the packed dialog without showing it. */
    internal fun build(parent: Component, point: PointDto, number: Int, actions: PointsActions): JDialog {
        val dialog = PointsDialogKit.dialog(parent, "Edit point #$number")
        val start = JTextField(Timecode.format(point.startMs)).apply { name = "points-edit-start" }
        val end = JTextField(point.endMs?.let { Timecode.format(it) } ?: "").apply { name = "points-edit-end" }
        val label = JTextField(point.label ?: "").apply { name = "points-edit-label" }
        val error = PointsDialogKit.errorLine()

        val save = PointsButton("Save", kind = PointsButton.Kind.LIME).apply { name = "points-edit-save" }
        val cancel = PointsButton("Cancel")
        val delete = PointsButton("Delete", Material2AL.DELETE, PointsButton.Kind.DANGER).apply { name = "points-edit-delete" }

        save.addActionListener {
            try {
                val newStart = Timecode.parse(start.text)
                val newEnd = Timecode.parse(end.text)
                dialog.dispose()
                actions.editPoint(point.id, PointPatch(startMs = newStart, endMs = newEnd, label = label.text))
            } catch (t: IllegalArgumentException) {
                error.text = if (t is NumberFormatException) "Invalid time format" else t.message ?: "Invalid time format"
            }
        }
        cancel.addActionListener { dialog.dispose() }
        delete.addActionListener {
            dialog.dispose()
            val name = Timecode.format(point.startMs) + " - " + (point.endMs?.let { Timecode.format(it) } ?: "?")
            val confirm = JOptionPane.showConfirmDialog(
                parent,
                "Delete marked point $name?",
                "Confirm delete",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
            if (confirm == JOptionPane.OK_OPTION) actions.deletePoint(point.id)
        }

        val length = point.endMs?.let { formatSeconds(it - point.startMs) }
        dialog.contentPane = PointsDialogKit.content(
            PointsDialogKit.head("Edit point #$number", length),
            PointsDialogKit.form(
                PointsDialogKit.pair(
                    PointsDialogKit.field("Start", PointsDialogKit.inputBox(start)),
                    PointsDialogKit.field("End", PointsDialogKit.inputBox(end)),
                ),
                PointsDialogKit.field("Label", PointsDialogKit.inputBox(label), note = "optional"),
                error,
            ),
            PointsDialogKit.footer(left = listOf(delete), right = listOf(cancel, save)),
        )
        // Enter in any field saves.
        dialog.rootPane.defaultButton = save
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent?) {
                start.requestFocusInWindow()
                start.selectAll()
            }
        })
        dialog.pack()
        dialog.isResizable = false
        return dialog
    }
}
