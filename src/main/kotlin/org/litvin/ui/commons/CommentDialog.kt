package org.litvin.ui.commons

import org.litvin.points.CommentStyle
import org.litvin.shared.util.Timecode
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import org.litvin.i18n.tr

/** The values of a comment in [CommentDialog]. The start is in source time; the color uses the #RRGGBB format. */
data class CommentInput(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
    val colorHex: String,
    val style: CommentStyle = CommentStyle.OUTLINE,
)

/**
 * Modal editor for creating or changing a source-time-pinned comment. The Points tab uses it.
 * [namePrefix] starts the names of the fields, for example "points" gives "points-comment-text".
 */
object CommentDialog {
    /** How long a new comment stays on screen until the user changes it. */
    private const val DEFAULT_DURATION_MS = 5_000L

    /** The height of the text box, about five lines. */
    private const val TEXT_HEIGHT = 96

    private val COLOR_HEX = Regex("^#?([0-9A-Fa-f]{6})$")

    /** The choices of the Style switch. The order is the order of the buttons "<prefix>-comment-style-<index>". */
    private val STYLE_OPTIONS = listOf(
        SegmentedChoice.Option(
            CommentStyle.OUTLINE,
            tr("comment.style.outline"),
            sub = tr("comment.style.outline.sub"),
            tooltip = tr("comment.style.outline.tooltip"),
        ),
        SegmentedChoice.Option(CommentStyle.CARD, tr("comment.style.card"), sub = tr("comment.style.card.sub"), tooltip = tr("comment.style.card.tooltip")),
        SegmentedChoice.Option(CommentStyle.PILL, tr("comment.style.pill"), sub = tr("comment.style.pill.sub"), tooltip = tr("comment.style.pill.tooltip")),
    )

    fun showCreate(
        parent: Component,
        namePrefix: String,
        initialStartMs: Long,
        defaultColor: String,
        defaultStyle: CommentStyle,
        onSave: (CommentInput) -> Unit,
    ) {
        buildCreate(parent, namePrefix, initialStartMs, defaultColor, defaultStyle, onSave).showOver(parent)
    }

    fun showEdit(parent: Component, namePrefix: String, id: Int, comment: CommentInput, onSave: (CommentInput) -> Unit) {
        buildEdit(parent, namePrefix, id, comment, onSave).showOver(parent)
    }

    /** Builds the packed "Add comment" dialog without showing it. */
    internal fun buildCreate(
        parent: Component,
        namePrefix: String,
        initialStartMs: Long,
        defaultColor: String,
        defaultStyle: CommentStyle,
        onSave: (CommentInput) -> Unit,
    ): JDialog = build(
        parent,
        namePrefix,
        tr("comment.add.title"),
        CommentInput(initialStartMs, DEFAULT_DURATION_MS, "", defaultColor, defaultStyle),
        onSave,
    )

    /** Builds the packed "Edit comment" dialog without showing it. */
    internal fun buildEdit(parent: Component, namePrefix: String, id: Int, comment: CommentInput, onSave: (CommentInput) -> Unit): JDialog =
        build(parent, namePrefix, tr("comment.edit.title", "number" to id.toString()), comment, onSave)

    private fun JDialog.showOver(parent: Component) {
        setLocationRelativeTo(parent)
        isVisible = true
    }

    private fun build(
        parent: Component,
        namePrefix: String,
        title: String,
        initial: CommentInput,
        save: (CommentInput) -> Unit,
    ): JDialog {
        val dialog = DialogKit.modal(parent, title)
        val text = JTextArea(initial.text).apply {
            name = "$namePrefix-comment-text"
            lineWrap = true
            wrapStyleWord = true
        }
        val start = JTextField(formatTimestamp(initial.startMs)).apply {
            name = "$namePrefix-comment-start"
            toolTipText = tr("comment.start.tooltip")
        }
        val duration = JTextField(formatDurationField(initial.durationMs)).apply {
            name = "$namePrefix-comment-duration"
            toolTipText = tr("comment.duration.tooltip")
        }
        var colorHex = initial.colorHex
        val color = UiButton(tr("comment.color.change")).apply {
            name = "$namePrefix-comment-color"
            swatch = colorFor(colorHex)
            toolTipText = colorHex
        }
        val style = SegmentedChoice("$namePrefix-comment-style", STYLE_OPTIONS).apply { selected = initial.style }
        val error = DialogKit.errorLine()
        val saveButton = UiButton(tr("common.save"), kind = UiButton.Kind.LIME).apply { name = "$namePrefix-comment-save" }
        val cancelButton = UiButton(tr("common.cancel"))

        color.addActionListener {
            ColorPickerDialog.pick(dialog, tr("comment.color.pick"), colorFor(colorHex))?.let { chosen ->
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
                val parsed = parseFields(start.text, duration.text, text.text, colorHex, style.selected ?: initial.style)
                if (parsed == null) {
                    error.text = tr("comment.error.color")
                } else {
                    save(parsed)
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
                DialogKit.field(tr("comment.text"), textBox),
                DialogKit.pair(
                    DialogKit.field(tr("comment.start"), DialogKit.inputBox(start)),
                    DialogKit.field(tr("comment.duration"), DialogKit.inputBox(duration), note = tr("comment.duration.note")),
                ),
                DialogKit.field(tr("comment.color"), colorRow),
                DialogKit.field(tr("comment.style"), style),
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
        if (text.isBlank()) return tr("comment.error.text")
        val startMs = runCatching { Timecode.parse(start) }.getOrNull()
            ?: return tr("comment.error.start")
        if (startMs < 0) return tr("comment.error.startNegative")
        val durationMs = runCatching {
            BigDecimal(typedSeconds(durationSeconds)).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull() ?: return tr("comment.error.duration")
        if (durationMs <= 0) return tr("comment.error.durationZero")
        return null
    }

    private fun parseFields(start: String, durationSeconds: String, text: String, color: String, style: CommentStyle): CommentInput? = try {
        val startMs = Timecode.parse(start)
        val durationMs = BigDecimal(typedSeconds(durationSeconds))
            .movePointRight(3)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        val normalizedColor = normalizeColorHex(color) ?: return null
        val trimmedText = text.trim()
        if (startMs < 0 || durationMs <= 0 || trimmedText.isBlank()) null
        else CommentInput(startMs, durationMs, trimmedText, normalizedColor, style)
    } catch (_: Throwable) {
        null
    }

    /** The same rule as the EDL: six hex digits, with or without "#". The result is "#" and upper case. */
    private fun normalizeColorHex(color: String): String? =
        COLOR_HEX.matchEntire(color.trim())?.let { "#${it.groupValues[1].uppercase(Locale.ROOT)}" }

    /** A number of seconds that the user typed. "1,5" and "1.5" are the same (B-11, "Typed decimal separator"). */
    private fun typedSeconds(text: String): String = text.trim().replace(',', '.')

    private fun colorFor(hex: String): Color = runCatching { Color.decode(hex) }.getOrDefault(Palette.PURE_WHITE)
    private fun formatDurationField(durationMs: Long): String = BigDecimal(durationMs).movePointLeft(3).stripTrailingZeros().toPlainString()
    private fun formatTimestamp(totalMs: Long): String {
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val seconds = (totalMs % 60_000L) / 1_000L
        val milliseconds = totalMs % 1_000L
        // A machine format: the field reads it again with Timecode.parse, so the digits must be 0-9 in all locales.
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds)
    }
}
