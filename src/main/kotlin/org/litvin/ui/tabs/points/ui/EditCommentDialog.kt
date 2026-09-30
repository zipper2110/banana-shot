package org.litvin.ui.tabs.points.ui

import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.ColorPickerDialog
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.formatSeconds
import org.litvin.ui.tabs.points.CommentDto
import org.litvin.ui.tabs.points.CommentPatch
import org.litvin.ui.tabs.points.PointsActions
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.math.BigDecimal
import java.math.RoundingMode
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField

/** Modal editor for creating or changing a source-time-pinned point comment. */
object EditCommentDialog {
    /** How long a new comment stays on screen until the user changes it. */
    private const val DEFAULT_DURATION_MS = 5_000L

    /** The height of the text box, about five lines. */
    private const val TEXT_HEIGHT = 96

    fun showCreate(parent: Component, initialStartMs: Long, defaultColor: String, actions: PointsActions) {
        buildCreate(parent, initialStartMs, defaultColor, actions).showOver(parent)
    }

    fun showEdit(parent: Component, comment: CommentDto, actions: PointsActions) {
        buildEdit(parent, comment, actions).showOver(parent)
    }

    /** Builds the packed "Add comment" dialog without showing it. */
    internal fun buildCreate(parent: Component, initialStartMs: Long, defaultColor: String, actions: PointsActions): JDialog =
        build(parent, "Add comment", initialStartMs, DEFAULT_DURATION_MS, "", defaultColor) { startMs, durationMs, text, color ->
            actions.createComment(startMs, durationMs, text, color)
        }

    /** Builds the packed "Edit comment" dialog without showing it. */
    internal fun buildEdit(parent: Component, comment: CommentDto, actions: PointsActions): JDialog =
        build(parent, "Edit comment #${comment.id}", comment.startMs, comment.durationMs, comment.text, comment.colorHex) { startMs, durationMs, text, color ->
            actions.editComment(
                comment.id,
                CommentPatch(startMs = startMs, durationMs = durationMs, text = text, colorHex = color),
            )
        }

    private fun JDialog.showOver(parent: Component) {
        setLocationRelativeTo(parent)
        isVisible = true
    }

    private fun build(
        parent: Component,
        title: String,
        initialStartMs: Long,
        initialDurationMs: Long,
        initialText: String,
        initialColor: String,
        save: (Long, Long, String, String) -> Unit,
    ): JDialog {
        val dialog = DialogKit.modal(parent, title)
        val text = JTextArea(initialText).apply {
            name = "points-comment-text"
            lineWrap = true
            wrapStyleWord = true
        }
        val start = JTextField(formatTimestamp(initialStartMs)).apply {
            name = "points-comment-start"
            toolTipText = "hh:mm:ss.mmm, mm:ss.mmm, or seconds"
        }
        val duration = JTextField(formatSeconds(initialDurationMs)).apply {
            name = "points-comment-duration"
            toolTipText = "How long the comment stays on screen, in seconds"
        }
        var colorHex = initialColor
        val color = UiButton("Change…").apply {
            name = "points-comment-color"
            swatch = colorFor(colorHex)
            toolTipText = colorHex
        }
        val error = DialogKit.errorLine()
        val saveButton = UiButton("Save", kind = UiButton.Kind.LIME).apply { name = "points-comment-save" }
        val cancelButton = UiButton("Cancel")

        color.addActionListener {
            ColorPickerDialog.pick(dialog, "Choose comment color", colorFor(colorHex))?.let { chosen ->
                colorHex = "#%06X".format(chosen.rgb and 0xFFFFFF)
                color.swatch = chosen
                color.toolTipText = colorHex
            }
        }

        val doSave = {
            val problem = firstProblem(start.text, duration.text, text.text)
            if (problem != null) {
                error.text = problem
            } else {
                val parsed = parseFields(start.text, duration.text, text.text, colorHex)
                if (parsed == null) {
                    error.text = "Text color must use the #RRGGBB format."
                } else {
                    save(parsed.startMs, parsed.durationMs, parsed.text, parsed.colorHex)
                    dialog.dispose()
                }
            }
        }
        saveButton.addActionListener { doSave() }
        cancelButton.addActionListener { dialog.dispose() }

        val textBox = DialogKit.inputBox(text, boxHeight = null, padding = 8).apply {
            preferredSize = Dimension(preferredSize.width, TEXT_HEIGHT)
        }
        val colorRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(color)
        }
        dialog.contentPane = DialogKit.content(
            DialogKit.SMALL,
            DialogKit.head(title),
            DialogKit.form(
                DialogKit.field("Text", textBox),
                DialogKit.pair(
                    DialogKit.field("Start", DialogKit.inputBox(start)),
                    DialogKit.field("Duration", DialogKit.inputBox(duration), note = "seconds"),
                ),
                DialogKit.field("Text color", colorRow),
                error,
            ),
            DialogKit.footer(left = emptyList(), right = listOf(cancelButton, saveButton)),
        )
        // Enter saves from the single-line fields; the text area keeps Enter for new lines.
        dialog.rootPane.defaultButton = saveButton
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent?) {
                text.requestFocusInWindow()
                text.caretPosition = text.document.length
            }
        })
        dialog.pack()
        dialog.isResizable = false
        return dialog
    }

    /** Returns a message for the first invalid field, or null when every field is usable. */
    private fun firstProblem(start: String, durationSeconds: String, text: String): String? {
        if (text.isBlank()) return "Enter the comment text."
        val startMs = runCatching { Timecode.parse(start) }.getOrNull()
            ?: return "Start must use the hh:mm:ss.mmm format."
        if (startMs < 0) return "Start cannot be negative."
        val durationMs = runCatching {
            BigDecimal(durationSeconds.trim()).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull() ?: return "Duration must be a number of seconds."
        if (durationMs <= 0) return "Duration must be greater than zero."
        return null
    }

    private data class ParsedComment(val startMs: Long, val durationMs: Long, val text: String, val colorHex: String)

    private fun parseFields(start: String, durationSeconds: String, text: String, color: String): ParsedComment? = try {
        val startMs = Timecode.parse(start)
        val durationMs = BigDecimal(durationSeconds.trim())
            .movePointRight(3)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        val normalizedColor = org.litvin.points.EdlIO.normalizeColorHex(color) ?: return null
        val trimmedText = text.trim()
        if (startMs < 0 || durationMs <= 0 || trimmedText.isBlank()) null
        else ParsedComment(startMs, durationMs, trimmedText, normalizedColor)
    } catch (_: Throwable) {
        null
    }

    private fun colorFor(hex: String): Color = runCatching { Color.decode(hex) }.getOrDefault(Palette.PURE_WHITE)
    private fun formatSeconds(durationMs: Long): String = BigDecimal(durationMs).movePointLeft(3).stripTrailingZeros().toPlainString()
    private fun formatTimestamp(totalMs: Long): String {
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val seconds = (totalMs % 60_000L) / 1_000L
        val milliseconds = totalMs % 1_000L
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, milliseconds)
    }
}
