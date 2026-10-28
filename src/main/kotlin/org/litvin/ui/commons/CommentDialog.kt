package org.litvin.ui.commons

import org.litvin.i18n.DisplayFormats
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import org.litvin.shared.util.Timecode
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import org.litvin.i18n.MachineFormats
import org.litvin.i18n.tr

/** The values of a comment in [CommentDialog]. The start is in source time; the color uses the #RRGGBB format. */
data class CommentInput(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
    val colorHex: String,
    val style: CommentStyle = CommentStyle.OUTLINE,
    val sizePercent: Int = CommentV1.DEFAULT_SIZE_PERCENT,
    val opacityPercent: Int = 100,
    val rotationDeg: Int = 0,
)

/**
 * Modal editor for creating or changing a source-time-pinned comment. The Points tab uses it.
 * [namePrefix] starts the names of the fields, for example "points" gives "points-comment-text".
 *
 * The `onPreview` callback gets the comment after each change, so the video can show the change at once.
 * When the dialog closes, it gets null. Only Save keeps the change. Cancel, Escape, and the close button discard it.
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
        defaultSizePercent: Int = CommentV1.DEFAULT_SIZE_PERCENT,
        defaultOpacityPercent: Int = 100,
        /** The first time of the new comment. */
        initialDurationMs: Long = DEFAULT_DURATION_MS,
        onPreview: (CommentInput?) -> Unit = {},
        onSave: (CommentInput) -> Unit,
    ) {
        buildCreate(
            parent, namePrefix, initialStartMs, defaultColor, defaultStyle, defaultSizePercent, defaultOpacityPercent,
            initialDurationMs, onPreview, onSave,
        ).showOver(parent)
    }

    fun showEdit(
        parent: Component,
        namePrefix: String,
        id: Int,
        comment: CommentInput,
        onPreview: (CommentInput?) -> Unit = {},
        onSave: (CommentInput) -> Unit,
    ) {
        buildEdit(parent, namePrefix, id, comment, onPreview, onSave).showOver(parent)
    }

    /** Builds the packed "Add comment" dialog without showing it. */
    internal fun buildCreate(
        parent: Component,
        namePrefix: String,
        initialStartMs: Long,
        defaultColor: String,
        defaultStyle: CommentStyle,
        defaultSizePercent: Int = CommentV1.DEFAULT_SIZE_PERCENT,
        defaultOpacityPercent: Int = 100,
        initialDurationMs: Long = DEFAULT_DURATION_MS,
        onPreview: (CommentInput?) -> Unit = {},
        onSave: (CommentInput) -> Unit,
    ): JDialog = build(
        parent,
        namePrefix,
        tr("comment.add.title"),
        CommentInput(initialStartMs, initialDurationMs, "", defaultColor, defaultStyle, defaultSizePercent, defaultOpacityPercent),
        onPreview,
        onSave,
    )

    /** Builds the packed "Edit comment" dialog without showing it. */
    internal fun buildEdit(
        parent: Component,
        namePrefix: String,
        id: Int,
        comment: CommentInput,
        onPreview: (CommentInput?) -> Unit = {},
        onSave: (CommentInput) -> Unit,
    ): JDialog = build(parent, namePrefix, tr("comment.edit.title", "number" to id.toString()), comment, onPreview, onSave)

    private fun JDialog.showOver(parent: Component) {
        setLocationRelativeTo(parent)
        isVisible = true
    }

    private fun build(
        parent: Component,
        namePrefix: String,
        title: String,
        initial: CommentInput,
        preview: (CommentInput?) -> Unit,
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
        val size = SliderValueRow(
            "$namePrefix-comment-size", tr("comment.size"), tr("comment.size.tooltip"),
            CommentV1.MIN_SIZE_PERCENT, CommentV1.MAX_SIZE_PERCENT, CommentV1.DEFAULT_SIZE_PERCENT,
            emptyList(), percent(CommentV1.MIN_SIZE_PERCENT), null, percent(CommentV1.MAX_SIZE_PERCENT),
            PERCENT_FORMAT, first = true,
        ).apply { slider.value = initial.sizePercent }
        val opacity = SliderValueRow(
            "$namePrefix-comment-opacity", tr("comment.opacity"), tr("comment.opacity.tooltip"),
            CommentV1.MIN_OPACITY_PERCENT, 100, 100,
            emptyList(), percent(CommentV1.MIN_OPACITY_PERCENT), null, percent(100),
            PERCENT_FORMAT, first = false,
        ).apply { slider.value = initial.opacityPercent }
        val rotation = SliderValueRow(
            "$namePrefix-comment-rotation", tr("comment.rotation"), tr("comment.rotation.tooltip"),
            CommentV1.MIN_ROTATION_DEG, CommentV1.MAX_ROTATION_DEG, 0,
            listOf(-90, 90), degrees(CommentV1.MIN_ROTATION_DEG), degrees(0), degrees(CommentV1.MAX_ROTATION_DEG),
            DEGREE_FORMAT, first = false,
        ).apply { slider.value = initial.rotationDeg }
        val error = DialogKit.errorLine()
        val saveButton = UiButton(tr("common.save"), kind = UiButton.Kind.LIME).apply { name = "$namePrefix-comment-save" }
        val cancelButton = UiButton(tr("common.cancel")).apply { name = "$namePrefix-comment-cancel" }

        // The comment with the values in the fields now. A field that is not valid gives its first value.
        fun current() = CommentInput(
            startMs = runCatching { Timecode.parse(start.text) }.getOrNull()?.takeIf { it >= 0 } ?: initial.startMs,
            durationMs = durationMs(duration.text)?.takeIf { it > 0 } ?: initial.durationMs,
            text = text.text.trim(),
            colorHex = colorHex,
            style = style.selected ?: initial.style,
            sizePercent = size.slider.value,
            opacityPercent = opacity.slider.value,
            rotationDeg = rotation.slider.value,
        )
        val showChange = { preview(current()) }
        val fieldListener = object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent?) = showChange()
            override fun removeUpdate(event: DocumentEvent?) = showChange()
            override fun changedUpdate(event: DocumentEvent?) = showChange()
        }
        listOf(text, start, duration).forEach { it.document.addDocumentListener(fieldListener) }
        style.onChange { showChange() }
        size.slider.addChangeListener { showChange() }
        opacity.slider.addChangeListener { showChange() }
        rotation.slider.addChangeListener { showChange() }

        val setColor = { chosen: Color ->
            colorHex = MachineFormats.format("#%06X", chosen.rgb and 0xFFFFFF)
            color.swatch = chosen
            color.toolTipText = colorHex
        }
        color.addActionListener {
            // The picker sends each color before OK. After Cancel, it sends the first color again.
            val chosen = ColorPickerDialog.pick(dialog, tr("comment.color.pick"), colorFor(colorHex)) { live ->
                setColor(live)
                showChange()
            }
            chosen?.let(setColor)
        }

        val doSave = {
            val problem = firstProblem(start.text, duration.text, text.text)
            if (problem != null) {
                error.text = problem
            } else {
                val parsed = parseFields(start.text, duration.text, text.text, colorHex, style.selected ?: initial.style)
                    ?.copy(sizePercent = size.slider.value, opacityPercent = opacity.slider.value, rotationDeg = rotation.slider.value)
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
                JPanel(GridLayout(3, 1, 0, 0)).apply {
                    isOpaque = false
                    add(size)
                    add(opacity)
                    add(rotation)
                },
                error,
            ),
            DialogKit.footer(left = emptyList(), right = listOf(cancelButton, saveButton)),
        )
        // Enter saves from the single-line fields; the text area keeps Enter for new lines.
        dialog.rootPane.defaultButton = saveButton
        var closed = false
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent?) {
                text.requestFocusInWindow()
                text.caretPosition = text.document.length
            }

            // Save, Cancel, Escape, and the close button all dispose the dialog. The preview stops in all cases.
            override fun windowClosed(event: WindowEvent?) {
                if (closed) return
                closed = true
                preview(null)
            }
        })
        dialog.pack()
        dialog.isResizable = false
        return dialog
    }

    /** The format of the Size and Opacity fields: a whole number of percent. */
    private val PERCENT_FORMAT = SliderValueFormat(
        unit = "%",
        step = 1,
        toText = { it.toString() },
        fromText = { SliderValueFormat.parse(it, "%")?.roundToInt() },
    )

    private fun percent(value: Int): String = DisplayFormats.percent(value)

    /** The format of the Rotation field: a whole number of degrees. */
    private val DEGREE_FORMAT = SliderValueFormat(
        unit = "°",
        step = 1,
        toText = { it.toString() },
        fromText = { SliderValueFormat.parse(it, "°")?.roundToInt() },
    )

    private fun degrees(value: Int): String = "$value°"

    /** Returns a message for the first invalid field, or null when every field is usable. */
    private fun firstProblem(start: String, durationSeconds: String, text: String): String? {
        if (text.isBlank()) return tr("comment.error.text")
        val startMs = runCatching { Timecode.parse(start) }.getOrNull()
            ?: return tr("comment.error.start")
        if (startMs < 0) return tr("comment.error.startNegative")
        val durationMs = durationMs(durationSeconds) ?: return tr("comment.error.duration")
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

    /** The duration field in milliseconds, or null when it is not a number. */
    private fun durationMs(durationSeconds: String): Long? = runCatching {
        BigDecimal(typedSeconds(durationSeconds)).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()

    /** A number of seconds that the user typed. "1,5" and "1.5" are the same (B-11, "Typed decimal separator"). */
    private fun typedSeconds(text: String): String = text.trim().replace(',', '.')

    private fun colorFor(hex: String): Color = runCatching { Color.decode(hex) }.getOrDefault(Palette.PURE_WHITE)
    /** The duration in seconds with the decimal separator of the regional format, for example "1.5" or "1,5". */
    private fun formatDurationField(durationMs: Long): String = DisplayFormats.decimal(durationMs / 1000.0, 0, 3, grouping = false)
    private fun formatTimestamp(totalMs: Long): String {
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val seconds = (totalMs % 60_000L) / 1_000L
        val milliseconds = totalMs % 1_000L
        // A machine format: the field reads it again with Timecode.parse, so the digits must be 0-9 in all locales.
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds)
    }
}
