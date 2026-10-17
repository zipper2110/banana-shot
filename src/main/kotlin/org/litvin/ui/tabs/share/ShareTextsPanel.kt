package org.litvin.ui.tabs.share

import org.litvin.share.ThumbnailField
import org.litvin.share.ThumbnailVideoType
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.Stack
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

/** What the Texts tab shows for one field. [source]: where the value comes from, or "Edited". */
internal data class FieldState(val value: String, val source: String, val edited: Boolean, val used: Boolean)

/** The state of the Texts tab. [finalScore] is null when the project has no scored points. */
internal data class TextsState(
    val type: ThumbnailVideoType,
    val fields: Map<ThumbnailField, FieldState>,
    val typeWords: String,
    val finalScore: String?,
    val headIsScore: Boolean,
    val anyEdited: Boolean,
)

/**
 * The Texts tab of the side column: the video type and the text fields with their presets.
 * [onText] gets each change of a field. [onHead] gets a preset choice of the large words: false is the words of the
 * video type, true is the final score.
 */
internal class ShareTextsPanel(
    private val onType: (ThumbnailVideoType) -> Unit,
    private val onText: (ThumbnailField, String) -> Unit,
    private val onHead: (Boolean) -> Unit,
    private val onUsePresets: () -> Unit,
) : Stack(pad = Insets(14, 16, 16, 16), gap = 12) {
    private val typeChoice = SegmentedChoice(
        "share-type",
        ThumbnailVideoType.entries.map { SegmentedChoice.Option(it, it.title) },
    )
    private val inputs = ThumbnailField.entries.associateWith { field -> input(field) }
    private val rows = HashMap<ThumbnailField, JComponent>()
    private val sources = HashMap<ThumbnailField, JLabel>()
    private val typeChip = ChipButton("share-head-type", "")
    private val scoreChip = ChipButton("share-head-score", "Final score").apply {
        toolTipText = "Shows the final score of the match on the thumbnail"
    }
    private val usePresets = LinkButton("share-use-presets", "Use the presets")
    private var updating = false

    init {
        name = "share-texts"
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            preferredSize = Dimension(0, 18)
            add(usePresets, BorderLayout.EAST)
        }, gapAfter = 4)
        add(captioned("Video type", null, typeChoice))
        typeChoice.onChange { onType(it) }
        usePresets.addActionListener { onUsePresets() }

        add(textField(ThumbnailField.HEAD, JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(box(ThumbnailField.HEAD), BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(typeChip)
                add(scoreChip)
            }, BorderLayout.CENTER)
        }))
        typeChip.addActionListener { onHead(false) }
        scoreChip.addActionListener { onHead(true) }
        add(textField(ThumbnailField.KIND, box(ThumbnailField.KIND)))
        add(textField(ThumbnailField.TEAM1, JPanel(GridLayout(2, 1, 0, 6)).apply {
            isOpaque = false
            add(box(ThumbnailField.TEAM1))
            add(box(ThumbnailField.TEAM2))
        }, caption = "Players"))
        add(textField(ThumbnailField.EVENT, box(ThumbnailField.EVENT)))
        add(textField(ThumbnailField.COURT, box(ThumbnailField.COURT)))
        add(textField(ThumbnailField.DATE, box(ThumbnailField.DATE)))
        add(ShareUi.hint("An empty field does not show on the thumbnail."))
        inputs.getValue(ThumbnailField.EVENT).toolTipText = "For example: Club league, round 3"
    }

    private fun input(field: ThumbnailField) = JTextField().apply {
        name = "share-field-${field.key}"
        getAccessibleContext().accessibleName = field.label
        (document as AbstractDocument).documentFilter = MaxLength(field.maxLength)
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = changed()
            override fun removeUpdate(e: DocumentEvent) = changed()
            override fun changedUpdate(e: DocumentEvent) = changed()
            private fun changed() {
                if (!updating) onText(field, text)
            }
        })
    }

    private fun box(field: ThumbnailField): JComponent = DialogKit.inputBox(inputs.getValue(field), boxHeight = 32)

    /** A caption with the source of the value on the right, and the input under it. */
    private fun textField(field: ThumbnailField, input: JComponent, caption: String = field.label): JComponent {
        val source = ShareUi.label("", 11f, Palette.FG_3)
        sources[field] = source
        return captioned(caption, source, input).also { rows[field] = it }
    }

    private fun captioned(caption: String, source: JLabel?, input: JComponent): JComponent = JPanel(BorderLayout(0, 5)).apply {
        isOpaque = false
        val label = ShareUi.label(caption, 12f, Palette.FG_2)
        add(JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            add(label, BorderLayout.WEST)
            if (source != null) add(source.apply { horizontalAlignment = SwingConstants.RIGHT }, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(input, BorderLayout.CENTER)
    }

    fun show(state: TextsState) {
        updating = true
        try {
            typeChoice.selected = state.type
            for ((field, input) in inputs) {
                val value = state.fields[field]?.value.orEmpty()
                // The field with the text cursor keeps its text, so that the cursor does not move while the user types.
                if (!input.isFocusOwner && input.text != value) input.text = value
            }
        } finally {
            updating = false
        }
        for ((field, source) in sources) {
            val keys = if (field == ThumbnailField.TEAM1) listOf(ThumbnailField.TEAM1, ThumbnailField.TEAM2) else listOf(field)
            val states = keys.mapNotNull { state.fields[it] }
            val edited = states.any { it.edited }
            val used = states.any { it.used }
            val empty = states.all { it.value.isBlank() }
            val text = when {
                edited -> "Edited"
                empty -> "No preset"
                field == ThumbnailField.HEAD && state.headIsScore -> "The final score"
                else -> field.source
            }
            source.text = text
            source.foreground = if (edited) Palette.YELLOW else Palette.FG_3
            // The selected style shows only some fields. The other fields keep their values for the other styles.
            rows[field]?.isVisible = used
        }
        val head = state.fields[ThumbnailField.HEAD]?.value.orEmpty()
        typeChip.text = state.typeWords
        typeChip.chosen = !state.headIsScore && head == state.typeWords
        scoreChip.isEnabled = state.finalScore != null
        scoreChip.chosen = state.headIsScore
        scoreChip.toolTipText = if (state.finalScore == null) "The project has no scored points" else "Shows the final score of the match on the thumbnail: ${state.finalScore}"
        usePresets.isVisible = state.anyEdited
        revalidate()
        repaint()
    }

    /** Stops a field at its maximum length. */
    private class MaxLength(private val max: Int) : DocumentFilter() {
        override fun insertString(fb: FilterBypass, offset: Int, text: String?, attr: AttributeSet?) =
            replace(fb, offset, 0, text, attr)

        override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
            val room = max - (fb.document.length - length)
            val fitted = text.orEmpty().replace('\n', ' ').take(room.coerceAtLeast(0))
            super.replace(fb, offset, length, fitted, attrs)
        }
    }
}
