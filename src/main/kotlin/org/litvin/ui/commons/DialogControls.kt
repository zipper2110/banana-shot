package org.litvin.ui.commons

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.max

/**
 * An on/off switch with a text on the right: the `.switch-row` element of the designs.
 * It is a [JCheckBox], so the callers and the tests use [isSelected] and the action listeners as before.
 */
internal class SwitchBox(text: String, private val fontSize: Float = 13f) : JCheckBox(text) {
    init {
        isOpaque = false
        isFocusPainted = false
        isBorderPainted = false
        isRolloverEnabled = true
        border = BorderFactory.createEmptyBorder()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        font = UiKit.font(fontSize)
    }

    override fun getPreferredSize(): Dimension {
        val textWidth = if (text.isNullOrEmpty()) 0 else GAP + ceil(UiKit.textWidth(text, font)).toInt() + 2
        return Dimension(TRACK_W + 4 + textWidth, max(TRACK_H + 4, 20))
    }

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)
            paintSwitch(g2, 2, (height - TRACK_H) / 2, isSelected, isFocusOwner)
            if (!text.isNullOrEmpty()) {
                UiKit.drawText(g2, text, font, UiKit.FG, (TRACK_W + 4 + GAP).toFloat(), 0f, height.toFloat())
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val TRACK_W = 30
        const val TRACK_H = 17
        private const val GAP = 10
        private val TRACK_OFF = Color(0x3A3A3A)
        private val KNOB_OFF = Color(0x9A9A9A)

        /** Paints the switch track and the knob with the top-left corner at [x], [y]. */
        fun paintSwitch(g2: Graphics2D, x: Int, y: Int, on: Boolean, focused: Boolean) {
            if (focused) {
                g2.color = UiKit.LIME_LINE
                g2.stroke = BasicStroke(1.5f)
                g2.draw(RoundRectangle2D.Double(x - 2.5, y - 2.5, TRACK_W + 5.0, TRACK_H + 5.0, TRACK_H + 5.0, TRACK_H + 5.0))
            }
            g2.color = if (on) UiKit.LIME else TRACK_OFF
            g2.fill(RoundRectangle2D.Double(x.toDouble(), y.toDouble(), TRACK_W.toDouble(), TRACK_H.toDouble(), TRACK_H.toDouble(), TRACK_H.toDouble()))
            val knob = TRACK_H - 4.0
            val knobX = if (on) x + TRACK_W - 2.0 - knob else x + 2.0
            g2.color = if (on) UiKit.ON_LIME else KNOB_OFF
            g2.fill(Ellipse2D.Double(knobX, y + 2.0, knob, knob))
        }
    }
}

/**
 * Two or three choices side by side, of which one is selected: the `.seg` element of the designs.
 * An option can have a second, smaller line, for example "No-ad" and "deciding point at 40–40".
 * An option with a [Glyph] and no label shows only the glyph, for example the corner of the scoreboard position.
 * Each option is a button with the name "<componentName>-<index>", so the tests can click it.
 */
internal class SegmentedChoice<T>(private val componentName: String, options: List<Option<T>>) : JPanel(null) {
    data class Option<T>(
        val value: T,
        val label: String,
        val sub: String? = null,
        val tooltip: String? = null,
        val glyph: Glyph? = null,
    )

    /** A small picture in a segment. [color] is brighter for the selected segment and under the pointer. */
    fun interface Glyph {
        fun paint(g2: Graphics2D, area: Rectangle, color: Color, selected: Boolean)
    }

    private val listeners = mutableListOf<(T) -> Unit>()
    private var segments: List<Segment> = emptyList()

    /** The selected value, or null when no option is selected. A change from the code does not call the listeners. */
    var selected: T? = null
        set(value) {
            field = value
            repaint()
        }

    init {
        name = componentName
        isOpaque = false
        setOptions(options)
    }

    /** Replaces the options. The selected value stays when an option has it. */
    fun setOptions(options: List<Option<T>>) {
        if (segments.map { it.option } == options) return
        segments.forEach { remove(it) }
        segments = options.mapIndexed { index, option -> Segment(option, index) }
        segments.forEach { add(it) }
        revalidate()
        repaint()
    }

    /** Calls [listener] when the user selects an option. */
    fun onChange(listener: (T) -> Unit) {
        listeners += listener
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        segments.forEach { it.isEnabled = enabled }
        repaint()
    }

    private val twoLines get() = segments.any { it.option.sub != null }
    private val segmentHeight get() = if (twoLines) 36 else 26

    override fun getPreferredSize(): Dimension {
        val widest = segments.maxOfOrNull { it.preferredSize.width } ?: 60
        return Dimension(PAD * 2 + widest * segments.size + GAP * (segments.size - 1), segmentHeight + PAD * 2)
    }

    override fun getMinimumSize() = Dimension(0, preferredSize.height)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun doLayout() {
        val count = segments.size.coerceAtLeast(1)
        val inner = width - PAD * 2 - GAP * (count - 1)
        segments.forEachIndexed { index, segment ->
            val left = PAD + index * inner / count + index * GAP
            val right = PAD + (index + 1) * inner / count + index * GAP
            segment.setBounds(left, PAD, right - left, height - PAD * 2)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)
            UiKit.paintBox(g2, 0, 0, width, height, 6, DialogKit.INPUT_BG, UiKit.LINE_2)
        } finally {
            g2.dispose()
        }
    }

    private inner class Segment(val option: Option<T>, index: Int) : JButton(option.label) {
        init {
            name = "$componentName-$index"
            toolTipText = option.tooltip
            if (option.label.isEmpty()) getAccessibleContext().accessibleName = option.tooltip
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            isRolloverEnabled = true
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                if (selected == option.value) return@addActionListener
                selected = option.value
                listeners.toList().forEach { it(option.value) }
            }
        }

        private val labelFont get() = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
        private val subFont get() = UiKit.font(11f)

        override fun getPreferredSize(): Dimension {
            val labelWidth = UiKit.textWidth(option.label, labelFont)
            val subWidth = option.sub?.let { UiKit.textWidth(it, subFont) } ?: 0f
            val glyphWidth = if (option.glyph != null) GLYPH_WIDTH.toFloat() else 0f
            return Dimension(ceil(max(max(labelWidth, subWidth), glyphWidth)).toInt() + 16, segmentHeight)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                if (!this@SegmentedChoice.isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)
                val isSelected = selected == option.value
                val hover = model.isRollover && isEnabled && !isSelected
                when {
                    isSelected -> UiKit.paintBox(g2, 0, 0, width, height, 4, UiKit.RAISED_2, if (isEnabled) UiKit.LIME_LINE else Color(0x555555))
                    hover -> UiKit.paintBox(g2, 0, 0, width, height, 4, DialogKit.DIALOG_BG, null)
                }
                if (isFocusOwner) UiKit.paintBox(g2, 0, 0, width, height, 4, null, UiKit.LIME)
                val fg = if (isSelected || hover) UiKit.FG else UiKit.FG_2
                val sub = option.sub
                val glyph = option.glyph
                if (glyph != null && option.label.isEmpty()) {
                    val glyphHeight = height - 10
                    val glyphWidth = minOf(width - 8, glyphHeight * 16 / 9)
                    glyph.paint(g2, Rectangle((width - glyphWidth) / 2, (height - glyphHeight) / 2, glyphWidth, glyphHeight), fg, isSelected)
                    return
                }
                val label = UiKit.ellipsize(option.label, labelFont, width - 8f)
                val labelX = (width - UiKit.textWidth(label, labelFont)) / 2f
                if (sub == null) {
                    UiKit.drawText(g2, label, labelFont, fg, labelX, 0f, height.toFloat())
                } else {
                    val lineH = 15f
                    val top = (height - lineH * 2) / 2f
                    UiKit.drawText(g2, label, labelFont, fg, labelX, top, lineH)
                    val subText = UiKit.ellipsize(sub, subFont, width - 8f)
                    val subX = (width - UiKit.textWidth(subText, subFont)) / 2f
                    UiKit.drawText(g2, subText, subFont, if (isSelected) UiKit.FG_2 else UiKit.FG_3, subX, top + lineH, lineH)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val PAD = 3
        const val GAP = 2
        const val GLYPH_WIDTH = 28
    }
}

/** A square button with a round color swatch, for a player color: the `.color-btn` element of the design. */
internal class SwatchButton(color: Color, private val size: Dimension = Dimension(34, DialogKit.INPUT_HEIGHT)) : JButton() {
    var color: Color = color
        set(value) {
            field = value
            repaint()
        }

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() = size
    override fun getMinimumSize() = size
    override fun getMaximumSize() = size

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover && isEnabled
            UiKit.paintBox(
                g2, 0, 0, width, height, 4, if (hover) UiKit.RAISED_2 else UiKit.RAISED,
                if (isFocusOwner) UiKit.LIME_LINE else if (hover) UiKit.HOVER_LINE else UiKit.LINE_2,
            )
            val d = 18.0
            val x = (width - d) / 2
            val y = (height - d) / 2
            g2.color = color
            g2.fill(Ellipse2D.Double(x, y, d, d))
            g2.color = Color(255, 255, 255, 64)
            g2.draw(Ellipse2D.Double(x + 0.5, y + 0.5, d - 1, d - 1))
        } finally {
            g2.dispose()
        }
    }
}
