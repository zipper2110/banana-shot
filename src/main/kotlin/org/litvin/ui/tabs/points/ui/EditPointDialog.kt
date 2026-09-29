package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.formatSeconds
import org.litvin.ui.tabs.points.PointDto
import org.litvin.ui.tabs.points.PointPatch
import org.litvin.ui.tabs.points.PointsActions
import java.awt.Component
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
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
        val dialog = DialogKit.modal(parent, "Edit point #$number")
        val start = JTextField(Timecode.format(point.startMs)).apply { name = "points-edit-start" }
        val end = JTextField(point.endMs?.let { Timecode.format(it) } ?: "").apply { name = "points-edit-end" }
        val label = JTextField(point.label ?: "").apply { name = "points-edit-label" }
        val error = DialogKit.errorLine()

        val save = UiButton("Save", kind = UiButton.Kind.LIME).apply { name = "points-edit-save" }
        val cancel = UiButton("Cancel")
        val delete = UiButton("Delete", Material2AL.DELETE, UiButton.Kind.DANGER).apply { name = "points-edit-delete" }

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
            val confirmed = MessageDialog.confirm(
                parent,
                "Delete point",
                "Delete marked point **$name**?",
                confirmLabel = "Delete",
                destructive = true,
            )
            if (confirmed) actions.deletePoint(point.id)
        }

        val length = point.endMs?.let { formatSeconds(it - point.startMs) }
        dialog.contentPane = DialogKit.content(
            DialogKit.SMALL,
            DialogKit.head("Edit point #$number", length),
            DialogKit.form(
                DialogKit.pair(
                    DialogKit.field("Start", DialogKit.inputBox(start)),
                    DialogKit.field("End", DialogKit.inputBox(end)),
                ),
                DialogKit.field("Label", DialogKit.inputBox(label), note = "optional"),
                error,
            ),
            DialogKit.footer(left = listOf(delete), right = listOf(cancel, save)),
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
