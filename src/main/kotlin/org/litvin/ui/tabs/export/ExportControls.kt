package org.litvin.ui.tabs.export

import com.formdev.flatlaf.ui.FlatSliderUI
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.Palette
import org.litvin.ui.tabs.export.ExportUi.Weight
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JToggleButton
import kotlin.math.ceil
import kotlin.math.max

/** Makes a button paint only what its own paintComponent paints, with a hand cursor and a hover state. */
private fun AbstractButton.plain() {
    layout = null
    isContentAreaFilled = false
    isBorderPainted = false
    isFocusPainted = false
    isOpaque = false
    isRolloverEnabled = true
    border = BorderFactory.createEmptyBorder()
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addPropertyChangeListener("enabled") {
        cursor = Cursor.getPredefinedCursor(if (isEnabled) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
        repaint()
    }
}

/**
 * The caption of a step in the left column: a number in a lime ring, a title and an optional control on the right.
 */
internal class StepHeader(number: Int, title: String, private val trailing: JComponent? = null) : JPanel(null), HeightForWidth {
    private val numberBadge = object : JComponent() {
        override fun paintComponent(g: Graphics) {
            val g2 = ExportUi.smooth(g)
            try {
                g2.color = Palette.LIME_LINE
                g2.stroke = BasicStroke(1f)
                g2.draw(Ellipse2D.Double(0.5, 0.5, width - 1.0, height - 1.0))
                val font = ExportUi.font(11f, Weight.BOLD)
                val text = number.toString()
                val metrics = font.getLineMetrics(text, ExportUi.frc)
                g2.font = font
                g2.color = Palette.LIME
                g2.drawString(
                    text,
                    (width - ExportUi.textWidth(text, font)) / 2f,
                    (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent,
                )
            } finally {
                g2.dispose()
            }
        }
    }
    private val titleText = WrapText(title, ExportUi.font(14f, Weight.SEMIBOLD), Palette.FG)

    init {
        isOpaque = false
        add(numberBadge)
        add(titleText)
        trailing?.let(::add)
    }

    override fun heightForWidth(width: Int): Int =
        max(MIN_HEIGHT, max(titleText.heightForWidth(titleWidth(width)), trailing?.heightAt(TRAILING_WIDTH) ?: 0))

    override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 380))

    override fun doLayout() {
        numberBadge.setBounds(0, (height - BADGE) / 2, BADGE, BADGE)
        val titleHeight = titleText.heightForWidth(titleWidth(width))
        titleText.setBounds(BADGE + GAP, (height - titleHeight) / 2, titleWidth(width), titleHeight)
        trailing?.let {
            val h = it.heightAt(TRAILING_WIDTH)
            it.setBounds(width - TRAILING_WIDTH, (height - h) / 2, TRAILING_WIDTH, h)
        }
    }

    private fun titleWidth(width: Int) = (width - BADGE - GAP - (if (trailing != null) TRAILING_WIDTH + GAP else 0)).coerceAtLeast(0)

    private companion object {
        const val BADGE = 22
        const val GAP = 10
        const val MIN_HEIGHT = 30
        const val TRAILING_WIDTH = 190
    }
}

/**
 * A content choice: a radio dot, a title with a grey line under it, and a value with a unit on the right.
 * The card is a toggle button, so the keyboard, accessibility and the UI tests use it as one.
 */
internal class ChoiceCard(componentName: String) : JToggleButton(), HeightForWidth {
    val titleText = WrapText("", ExportUi.font(13.5f, Weight.SEMIBOLD), Palette.FG)
    val subText = WrapText("", ExportUi.font(12f), Palette.FG_2)
    val metaValue = WrapText("", ExportUi.font(12.5f, Weight.SEMIBOLD), Palette.FG, align = WrapText.Align.RIGHT)
    val metaUnit = WrapText("", ExportUi.font(12f), Palette.FG_2, align = WrapText.Align.RIGHT)

    init {
        name = componentName
        plain()
        listOf(titleText, subText, metaValue, metaUnit).forEach(::add)
        addItemListener { repaint() }
    }

    private fun metaWidth() = max(metaValue.naturalWidth(), metaUnit.naturalWidth())
    private fun textWidth(width: Int) = (width - TEXT_X - GAP - metaWidth() - PAD_X).coerceAtLeast(0)
    private fun textHeight(textWidth: Int) = titleText.heightForWidth(textWidth) + 1 + subText.heightForWidth(textWidth)
    private fun metaHeight() = metaValue.heightForWidth(metaWidth()) + metaUnit.heightForWidth(metaWidth())

    override fun heightForWidth(width: Int): Int = PAD_Y * 2 + maxOf(textHeight(textWidth(width)), metaHeight(), RADIO)

    override fun getPreferredSize() = Dimension(380, heightForWidth(if (width > 0) width else 380))
    override fun getMinimumSize() = Dimension(0, preferredSize.height)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun doLayout() {
        val tw = textWidth(width)
        val th = textHeight(tw)
        var y = (height - th) / 2
        val titleHeight = titleText.heightForWidth(tw)
        titleText.setBounds(TEXT_X, y, tw, titleHeight)
        y += titleHeight + 1
        subText.setBounds(TEXT_X, y, tw, subText.heightForWidth(tw))
        val mw = metaWidth()
        val mx = width - PAD_X - mw
        val valueHeight = metaValue.heightForWidth(mw)
        val my = (height - metaHeight()) / 2
        metaValue.setBounds(mx, my, mw, valueHeight)
        metaUnit.setBounds(mx, my + valueHeight, mw, metaUnit.heightForWidth(mw))
    }

    override fun paint(g: Graphics) = ExportUi.paintFaded(g, isEnabled) { super.paint(it) }

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            ExportUi.paintBox(g2, 0, 0, width, height, 6, Palette.RAISED, null)
            val border = when {
                isSelected -> {
                    ExportUi.paintBox(g2, 0, 0, width, height, 6, Palette.LIME_TINT, null)
                    Palette.LIME_LINE
                }
                isEnabled && model.isRollover -> Palette.HOVER_LINE
                else -> Palette.LINE
            }
            ExportUi.paintBox(g2, 0, 0, width, height, 6, null, border)
            ExportUi.paintRadio(g2, PAD_X, (height - RADIO) / 2, RADIO, isSelected)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        // The CSS padding plus the 1 px border.
        const val PAD_X = 13
        const val PAD_Y = 11
        const val RADIO = 16
        const val GAP = 12
        const val TEXT_X = PAD_X + RADIO + GAP
    }
}

/**
 * A tile with a check box, a label and a short note under the label.
 * The tile is a check box, so the keyboard, accessibility and the UI tests use it as one.
 */
internal class IncludeTile(componentName: String, label: String) : JCheckBox(), HeightForWidth {
    private val labelText = WrapText(label, ExportUi.font(12.5f, Weight.SEMIBOLD), Palette.FG)
    private val noteText = WrapText(" ", ExportUi.font(11f), Palette.FG_2, lineHeight = 1.3f)

    init {
        name = componentName
        plain()
        add(labelText)
        add(noteText)
        getAccessibleContext().accessibleName = label
        addItemListener { repaint() }
    }

    /** The note under the label. [ok] shows it in green, for example when all points are scored. */
    fun setNote(text: String, ok: Boolean = false) {
        noteText.setText(text.ifEmpty { " " }, ExportUi.font(11f), if (ok) Palette.LIME else Palette.FG_2)
        getAccessibleContext().accessibleDescription = text.ifBlank { null }
    }

    val note: String get() = noteText.text.trim()

    private fun textWidth(width: Int) = (width - TEXT_X - PAD_X).coerceAtLeast(0)
    private fun labelRowHeight(width: Int) = max(CHECK, labelText.heightForWidth(textWidth(width)))
    private fun contentHeight(width: Int) = labelRowHeight(width) + 1 + noteText.heightForWidth(textWidth(width))

    override fun heightForWidth(width: Int): Int = PAD_Y * 2 + contentHeight(width)

    override fun getPreferredSize() = Dimension(180, heightForWidth(if (width > 0) width else 180))
    override fun getMinimumSize() = Dimension(0, preferredSize.height)

    override fun doLayout() {
        val tw = textWidth(width)
        val rowHeight = labelRowHeight(width)
        val top = (height - contentHeight(width)) / 2
        val labelHeight = labelText.heightForWidth(tw)
        labelText.setBounds(TEXT_X, top + (rowHeight - labelHeight) / 2, tw, labelHeight)
        noteText.setBounds(TEXT_X, top + rowHeight + 1, tw, noteText.heightForWidth(tw))
    }

    override fun paint(g: Graphics) = ExportUi.paintFaded(g, isEnabled) { super.paint(it) }

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val border = when {
                isSelected -> CHECKED_BORDER
                isEnabled && model.isRollover -> Palette.HOVER_LINE
                else -> Palette.LINE
            }
            ExportUi.paintBox(g2, 0, 0, width, height, 6, Palette.RAISED, border)
            val top = (height - contentHeight(width)) / 2
            ExportUi.paintCheck(g2, PAD_X, top + (labelRowHeight(width) - CHECK) / 2, isSelected)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 11
        const val PAD_Y = 10
        const val CHECK = 16
        const val TEXT_X = PAD_X + CHECK + 9
        val CHECKED_BORDER get() = Palette.LIME_DIM_LINE
    }
}

/** A quality choice of the Simple mode: a title, a grey line, the file size at the bottom and a radio dot at the top right. */
internal class QualityTile(componentName: String) : JToggleButton(), HeightForWidth {
    private val titleText = WrapText("", ExportUi.font(13f, Weight.SEMIBOLD), Palette.FG)
    private val subText = WrapText("", ExportUi.font(11f), Palette.FG_2, lineHeight = 1.3f)
    private val valueText = WrapText("", ExportUi.font(12.5f, Weight.SEMIBOLD), Palette.FG)

    init {
        name = componentName
        plain()
        listOf(titleText, subText, valueText).forEach(::add)
        addItemListener { repaint() }
    }

    fun setTexts(title: String, sub: String) {
        titleText.setText(title, ExportUi.font(13f, Weight.SEMIBOLD), Palette.FG)
        subText.setText(sub, ExportUi.font(11f), Palette.FG_2)
        updateAccessibleName()
    }

    fun setValue(value: String) {
        valueText.setText(value, ExportUi.font(12.5f, Weight.SEMIBOLD), Palette.FG)
        updateAccessibleName()
    }

    private fun updateAccessibleName() {
        getAccessibleContext().accessibleName = listOf(titleText.text, subText.text, valueText.text).filter { it.isNotBlank() }.joinToString(", ")
    }

    private fun innerWidth(width: Int) = (width - PAD_X * 2).coerceAtLeast(0)

    override fun heightForWidth(width: Int): Int {
        val inner = innerWidth(width)
        val natural = PAD_TOP + titleText.heightForWidth((inner - RADIO_ROOM).coerceAtLeast(0)) + 2 +
            subText.heightForWidth(inner) + 2 + 6 + valueText.heightForWidth(inner) + PAD_BOTTOM
        return max(MIN_HEIGHT, natural)
    }

    override fun getPreferredSize() = Dimension(120, heightForWidth(if (width > 0) width else 120))
    override fun getMinimumSize() = Dimension(0, preferredSize.height)

    override fun doLayout() {
        val inner = innerWidth(width)
        val titleWidth = (inner - RADIO_ROOM).coerceAtLeast(0)
        val titleHeight = titleText.heightForWidth(titleWidth)
        titleText.setBounds(PAD_X, PAD_TOP, titleWidth, titleHeight)
        subText.setBounds(PAD_X, PAD_TOP + titleHeight + 2, inner, subText.heightForWidth(inner))
        val valueHeight = valueText.heightForWidth(inner)
        valueText.setBounds(PAD_X, height - PAD_BOTTOM - valueHeight, inner, valueHeight)
    }

    override fun paint(g: Graphics) = ExportUi.paintFaded(g, isEnabled) { super.paint(it) }

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            ExportUi.paintBox(g2, 0, 0, width, height, 6, Palette.RAISED, null)
            val border = when {
                isSelected -> {
                    ExportUi.paintBox(g2, 0, 0, width, height, 6, Palette.LIME_TINT, null)
                    Palette.LIME_LINE
                }
                isEnabled && model.isRollover -> Palette.HOVER_LINE
                else -> Palette.LINE
            }
            ExportUi.paintBox(g2, 0, 0, width, height, 6, null, border)
            ExportUi.paintRadio(g2, width - PAD_X - RADIO, PAD_TOP, RADIO, isSelected)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 11
        const val PAD_TOP = 11
        const val PAD_BOTTOM = 10
        const val RADIO = 14
        const val RADIO_ROOM = 18
        const val MIN_HEIGHT = 72
    }
}

/**
 * One button of a [SegmentedControl]: a bold title and an optional small line under it.
 * Parts of the small line can be green, for example "original".
 */
internal class SegmentButton(componentName: String, title: String, private val compact: Boolean = false) : JToggleButton(), HeightForWidth {
    private val titleText = WrapText(title, ExportUi.font(12.5f, Weight.SEMIBOLD), Palette.FG_2, align = WrapText.Align.CENTER)
    private val smallText = WrapText(emptyList(), lineHeight = 1.3f, align = WrapText.Align.CENTER).apply { isVisible = false }
    private var smallParts: List<Pair<String, Boolean>> = emptyList()

    init {
        name = componentName
        plain()
        add(titleText)
        add(smallText)
        model.addChangeListener { updateColors() }
        getAccessibleContext().accessibleName = title
    }

    /**
     * The small line. Each part is a text and true when the part is green.
     * A blank part keeps an empty line, so that the titles of all buttons in a row align.
     */
    fun setSmall(parts: List<Pair<String, Boolean>>) {
        smallParts = parts
        smallText.isVisible = parts.isNotEmpty()
        updateColors()
        getAccessibleContext().accessibleName = (listOf(titleText.text) + parts.joinToString("") { it.first }).filter { it.isNotBlank() }.joinToString(", ")
        revalidate()
    }

    private fun updateColors() {
        val active = isSelected || isEnabled && model.isRollover
        titleText.recolor { if (active) Palette.FG else Palette.FG_2 }
        smallText.runs = smallParts.map { (text, ok) ->
            when {
                ok -> TextRun(text, ExportUi.font(10.5f, Weight.SEMIBOLD), Palette.LIME)
                else -> TextRun(text, ExportUi.font(10.5f), if (isSelected) Palette.FG_2 else Palette.FG_3)
            }
        }
        repaint()
    }

    private val padY get() = if (compact) 4 else 5

    private fun innerWidth(width: Int) = (width - PAD_X * 2).coerceAtLeast(0)

    override fun heightForWidth(width: Int): Int {
        val inner = innerWidth(width)
        val small = if (smallText.isVisible) 1 + smallText.heightForWidth(inner) else 0
        return padY * 2 + titleText.heightForWidth(inner) + small
    }

    override fun getPreferredSize() = Dimension(
        max(titleText.naturalWidth(), if (smallText.isVisible) smallText.naturalWidth() else 0) + PAD_X * 2,
        heightForWidth(if (width > 0) width else 120),
    )

    override fun getMinimumSize() = Dimension(0, preferredSize.height)

    override fun doLayout() {
        val inner = innerWidth(width)
        val titleHeight = titleText.heightForWidth(inner)
        val small = if (smallText.isVisible) 1 + smallText.heightForWidth(inner) else 0
        val top = (height - titleHeight - small) / 2
        titleText.setBounds(PAD_X, top, inner, titleHeight)
        smallText.setBounds(PAD_X, top + titleHeight + 1, inner, small - 1)
    }

    override fun paint(g: Graphics) = ExportUi.paintFaded(g, isEnabled, alpha = 0.35f) { super.paint(it) }

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            when {
                isSelected -> ExportUi.paintBox(g2, 0, 0, width, height, 4, Palette.RAISED_2, Palette.LIME_LINE)
                isEnabled && model.isRollover -> ExportUi.paintBox(g2, 0, 0, width, height, 4, HOVER_BG, null)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 10
        val HOVER_BG get() = Palette.OVERLAY
    }
}

/** Buttons of equal width in a dark rounded frame. Only one button can be selected. */
internal class SegmentedControl : JPanel(null), HeightForWidth {
    private var group = ButtonGroup()
    val segments: List<SegmentButton> get() = components.filterIsInstance<SegmentButton>()

    init {
        isOpaque = false
    }

    fun addSegment(button: SegmentButton) {
        group.add(button)
        add(button)
    }

    fun removeSegments() {
        removeAll()
        group = ButtonGroup()
    }

    private fun segmentWidth(width: Int): Int {
        val count = segments.size.coerceAtLeast(1)
        return ((width - INSET * 2 - GAP * (count - 1)) / count).coerceAtLeast(0)
    }

    override fun heightForWidth(width: Int): Int {
        val w = segmentWidth(width)
        return INSET * 2 + (segments.maxOfOrNull { it.heightForWidth(w) } ?: 0)
    }

    override fun getPreferredSize(): Dimension {
        val natural = segments.sumOf { it.preferredSize.width } + GAP * (segments.size - 1).coerceAtLeast(0) + INSET * 2
        return Dimension(natural, heightForWidth(if (width > 0) width else natural))
    }

    override fun getMinimumSize() = Dimension(0, preferredSize.height)

    override fun doLayout() {
        val w = segmentWidth(width)
        val h = height - INSET * 2
        segments.forEachIndexed { index, button ->
            val x = INSET + index * (w + GAP)
            val right = if (index == segments.lastIndex) width - INSET else x + w
            button.setBounds(x, INSET, right - x, h)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            ExportUi.paintBox(g2, 0, 0, width, height, 6, Palette.BG, Palette.LINE_2)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        // The 1 px border and the 2 px padding.
        const val INSET = 3
        const val GAP = 2
    }
}

/**
 * A table of the quality details in a thin rounded frame: a grey name on the left, and a value
 * with an optional tag and an optional note on the right.
 */
internal class SpecTable : Stack(0) {
    init {
        border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
    }

    fun row(name: String): SpecRow = SpecRow(name).also(::add)

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            ExportUi.paintBox(g2, 0, 0, width, height, 6, null, Palette.LINE)
            g2.color = Palette.LINE
            components.filter { it.isVisible }.drop(1).forEach { g2.fillRect(1, it.y, width - 2, 1) }
        } finally {
            g2.dispose()
        }
    }
}

/** One row of a [SpecTable]. The value and the tag are on one line. The note is under them. */
internal class SpecRow(name: String) : JPanel(null), HeightForWidth {
    private val nameText = WrapText(name, ExportUi.font(12f), Palette.FG_2)
    private val valueText = WrapText("", ExportUi.font(12f), Palette.FG)
    private val tag = Tag("").apply { isVisible = false }
    private val noteText = WrapText("", ExportUi.font(11f), Palette.FG_3).apply { isVisible = false }

    init {
        isOpaque = false
        listOf(nameText, valueText, tag, noteText).forEach(::add)
    }

    fun set(value: String, tag: String? = null, mutedTag: Boolean = false, note: String? = null) {
        valueText.setText(value, ExportUi.font(12f), Palette.FG)
        this.tag.isVisible = !tag.isNullOrEmpty()
        this.tag.text = tag.orEmpty()
        this.tag.muted = mutedTag
        noteText.isVisible = !note.isNullOrEmpty()
        noteText.setText(note.orEmpty(), ExportUi.font(11f), Palette.FG_3)
        revalidate()
        repaint()
    }

    val value: String get() = valueText.text

    private fun valueWidth(width: Int) = (width - VALUE_X - PAD_X).coerceAtLeast(0)

    /** True when the tag does not fit next to the value and goes to the next line. */
    private fun tagWraps(width: Int) = tag.isVisible && valueText.naturalWidth() + GAP + tag.preferredSize.width > valueWidth(width)

    private fun inlineHeight(width: Int): Int {
        val w = valueWidth(width)
        val valueHeight = valueText.heightForWidth(if (tagWraps(width)) w else (w - (if (tag.isVisible) GAP + tag.preferredSize.width else 0)).coerceAtLeast(0))
        return when {
            !tag.isVisible -> valueHeight
            tagWraps(width) -> valueHeight + GAP + tag.preferredSize.height
            else -> max(valueHeight, tag.preferredSize.height)
        }
    }

    private fun valueBlockHeight(width: Int) =
        inlineHeight(width) + if (noteText.isVisible) GAP + noteText.heightForWidth(valueWidth(width)) else 0

    override fun heightForWidth(width: Int): Int =
        PAD_Y * 2 + max(nameText.heightForWidth(NAME_WIDTH), valueBlockHeight(width))

    override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 380))

    override fun doLayout() {
        val nameHeight = nameText.heightForWidth(NAME_WIDTH)
        nameText.setBounds(PAD_X, (height - nameHeight) / 2, NAME_WIDTH, nameHeight)
        val w = valueWidth(width)
        var y = (height - valueBlockHeight(width)) / 2
        val inline = inlineHeight(width)
        if (tagWraps(width) || !tag.isVisible) {
            val valueHeight = valueText.heightForWidth(w)
            valueText.setBounds(VALUE_X, y, w, valueHeight)
            if (tag.isVisible) tag.setBounds(VALUE_X, y + valueHeight + GAP, tag.preferredSize.width, tag.preferredSize.height)
        } else {
            val valueWidth = valueText.naturalWidth()
            val valueHeight = valueText.heightForWidth(valueWidth)
            valueText.setBounds(VALUE_X, y + (inline - valueHeight) / 2, valueWidth, valueHeight)
            val tagSize = tag.preferredSize
            tag.setBounds(VALUE_X + valueWidth - 1 + GAP, y + (inline - tagSize.height) / 2, tagSize.width, tagSize.height)
        }
        y += inline + GAP
        if (noteText.isVisible) noteText.setBounds(VALUE_X, y, w, noteText.heightForWidth(w))
    }

    private companion object {
        const val PAD_X = 12
        const val PAD_Y = 6
        const val NAME_WIDTH = 96
        const val GAP = 6
        const val VALUE_X = PAD_X + NAME_WIDTH + 10
    }
}

/**
 * The bitrate slider: a thin track that is lime up to the thumb, and a lime thumb with a dark ring.
 * A click on the track moves the thumb to the click position.
 */
internal class ExportSliderUI : FlatSliderUI() {
    override fun getThumbSize(): Dimension = Dimension(THUMB, THUMB)

    override fun paintFocus(g: Graphics) = Unit

    override fun paintTrack(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val y = trackRect.y + trackRect.height / 2.0 - TRACK / 2.0
            val fill = thumbRect.x + thumbRect.width / 2.0
            val width = slider.width.toDouble()
            g2.color = TRACK_BG
            g2.fill(RoundRectangle2D.Double(0.0, y, width, TRACK, TRACK, TRACK))
            g2.color = Palette.LIME
            g2.fill(RoundRectangle2D.Double(0.0, y, fill, TRACK, TRACK, TRACK))
        } finally {
            g2.dispose()
        }
    }

    override fun paintThumb(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val x = thumbRect.x.toDouble()
            val y = thumbRect.y + (thumbRect.height - THUMB) / 2.0
            g2.color = Palette.LIME
            g2.fill(Ellipse2D.Double(x, y, THUMB.toDouble(), THUMB.toDouble()))
            g2.color = THUMB_RING
            g2.fill(Ellipse2D.Double(x + 1, y + 1, THUMB - 2.0, THUMB - 2.0))
            g2.color = Palette.LIME
            g2.fill(Ellipse2D.Double(x + 4, y + 4, THUMB - 8.0, THUMB - 8.0))
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private const val THUMB = 18
        private const val TRACK = 4.0
        private val TRACK_BG get() = Palette.LINE_2
        private val THUMB_RING get() = Palette.BG

        fun slider(componentName: String): JSlider = JSlider().apply {
            name = componentName
            isOpaque = false
            isFocusable = true
            setUI(ExportSliderUI())
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
    }
}

/** The lime button at the bottom of the left column. It has a play icon and bold dark text. */
internal class StartExportButton(text: String) : JButton(text) {
    private val playIcon = ExportUi.icon(Material2OutlinedMZ.PLAY_CIRCLE_OUTLINE, 20, Palette.ON_LIME)
    private val blockedIcon = ExportUi.icon(Material2OutlinedMZ.PLAY_CIRCLE_OUTLINE, 20, Palette.FG_3)

    /** Expired mode (build-expiry-spec.md, "Expired mode"): a gray button with no glow. A click shows the dialog. */
    var blocked = false
        set(value) {
            field = value
            foreground = if (value) Palette.FG_3 else Palette.ON_LIME
            parent?.repaint()
        }

    init {
        plain()
        font = ExportUi.font(14f, Weight.BOLD)
        foreground = Palette.ON_LIME
    }

    override fun getPreferredSize() = Dimension(210, 46)
    override fun getMinimumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val fill = when {
                blocked -> if (model.isRollover) Palette.RAISED_2 else Palette.RAISED
                model.isArmed && model.isPressed -> Palette.LIME_PRESSED
                model.isRollover -> Palette.LIME_HOVER
                else -> Palette.LIME_FILL
            }
            ExportUi.paintBox(g2, 0, 0, width, height, 6, fill, null)
            val label = text.orEmpty()
            val textWidth = ExportUi.textWidth(label, font)
            val icon = if (blocked) blockedIcon else playIcon
            val total = icon.iconWidth + ICON_GAP + textWidth
            val x = (width - total) / 2f
            icon.paintIcon(this, g2, x.toInt(), (height - icon.iconHeight) / 2)
            val metrics = font.getLineMetrics(label, ExportUi.frc)
            g2.font = font
            g2.color = foreground
            g2.drawString(label, x + playIcon.iconWidth + ICON_GAP, (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private const val ICON_GAP = 6

        /** The glow under the button. The parent paints it, because a component cannot paint outside its bounds. */
        fun paintGlow(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
            for (step in 1..10) {
                val spread = step * 2.0
                g2.color = Palette.withAlpha(Palette.LIME, (13 - step).coerceAtLeast(1))
                g2.fill(RoundRectangle2D.Double(x - spread / 2, y + 6 - spread / 2, w + spread, h + spread, 12 + spread, 12 + spread))
            }
            g2.color = Palette.LIME_EDGE
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Double(x - 0.5, y - 0.5, w + 1.0, h + 1.0, 13.0, 13.0))
        }
    }
}

/** A small square button with an icon, for example "Open folder". A danger button has a red icon. */
internal class IconButton(ikon: Ikon, tooltip: String, private val danger: Boolean = false) : JButton() {
    init {
        plain()
        icon = ExportUi.icon(ikon, 16, if (danger) Palette.RED else Palette.FG)
        toolTipText = tooltip
        getAccessibleContext().accessibleName = tooltip
    }

    override fun getPreferredSize() = Dimension(SIZE, SIZE)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paint(g: Graphics) = ExportUi.paintFaded(g, isEnabled, alpha = 0.35f) { super.paint(it) }

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val hover = isEnabled && model.isRollover
            when {
                hover && danger -> ExportUi.paintBox(g2, 0, 0, width, height, 4, Palette.RED_TINT, Palette.RED_LINE)
                hover -> ExportUi.paintBox(g2, 0, 0, width, height, 4, Palette.RAISED_2, Palette.HOVER_LINE)
                else -> ExportUi.paintBox(g2, 0, 0, width, height, 4, Palette.RAISED, Palette.LINE_2)
            }
            icon?.paintIcon(this, g2, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val SIZE = 26
    }
}

/** A small button without a frame. A hover shows its frame and makes the text bright. */
internal class GhostButton(text: String, ikon: Ikon) : JButton(text) {
    private val normalIcon = ExportUi.icon(ikon, 16, Palette.FG_2)
    private val hoverIcon = ExportUi.icon(ikon, 16, Palette.FG)

    init {
        plain()
        font = ExportUi.font(12f)
    }

    override fun getPreferredSize() =
        Dimension(PAD_X * 2 + normalIcon.iconWidth + GAP + ceil(ExportUi.textWidth(text.orEmpty(), font)).toInt() + 1, HEIGHT)

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val hover = isEnabled && model.isRollover
            if (hover) ExportUi.paintBox(g2, 0, 0, width, height, 4, Palette.RAISED, Palette.LINE_2)
            val icon = if (hover) hoverIcon else normalIcon
            icon.paintIcon(this, g2, PAD_X, (height - icon.iconHeight) / 2)
            val metrics = font.getLineMetrics(text, ExportUi.frc)
            g2.font = font
            g2.color = if (hover) Palette.FG else Palette.FG_2
            g2.drawString(text, (PAD_X + icon.iconWidth + GAP).toFloat(), (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 10
        const val GAP = 6
        const val HEIGHT = 26
    }
}
