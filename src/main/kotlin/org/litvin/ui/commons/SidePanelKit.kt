package org.litvin.ui.commons

import com.formdev.flatlaf.ui.FlatSliderUI
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.feather.Feather
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JViewport
import javax.swing.Scrollable
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * The parts of the right panel of the Colors and Transform tabs: a header with a status and "Reset all",
 * a scrollable column of cards, group cards with slider rows, and the slider that fills from its default value.
 * The layout comes from design/colors-redesign/option-a.html and design/transform-redesign/option-a.html.
 */

/** The width of the right panel of a video tab. */
internal const val SIDE_PANEL_WIDTH = 400

/**
 * The quiet or normal "Reset all" button. It is quiet (no fill, gray text) when all values are at the default.
 * It stays enabled in both styles.
 */
internal class ResetAllButton(name: String, tooltip: String) : JButton("Reset all") {
    private val iconNormal = UiKit.icon(Feather.ROTATE_CCW, 14, Palette.FG)
    private val iconQuiet = UiKit.icon(Feather.ROTATE_CCW, 14, Palette.FG_3)

    var quiet = true
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    init {
        this.name = name
        toolTipText = tooltip
        font = UiKit.font(12f)
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        // Space must stay the play hotkey, so the button never keeps the focus.
        isFocusable = false
        border = BorderFactory.createEmptyBorder()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() =
        Dimension(PAD_X * 2 + ICON + GAP + ceil(UiKit.textWidth(text.orEmpty(), font)).toInt() + 1, BUTTON_HEIGHT)

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover
            val pressed = model.isArmed && model.isPressed
            val fill = when {
                quiet -> null
                pressed -> Palette.RAISED
                hover -> Palette.RAISED_2
                else -> Palette.RAISED
            }
            val line = when {
                quiet && hover -> Palette.LINE_2
                quiet -> Palette.LINE
                hover -> Palette.HOVER_LINE
                else -> Palette.LINE_2
            }
            UiKit.paintBox(g2, 0, 0, width, height, 4, fill, line)
            val bright = !quiet || hover
            val icon = if (bright) iconNormal else iconQuiet
            icon.paintIcon(this, g2, PAD_X + (ICON - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
            UiKit.drawText(
                g2, text.orEmpty(), font, if (bright) Palette.FG else Palette.FG_3,
                (PAD_X + ICON + GAP).toFloat(), 0f, height.toFloat(),
            )
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 10
        const val ICON = 16
        const val GAP = 6
        const val BUTTON_HEIGHT = 26
    }
}

/** The title of the panel, the status under it and "Reset all" on the right. */
internal class SidePanelHeader(title: String, private val status: JLabel, private val resetButton: JComponent) : JPanel(null) {
    private val titleLabel = JLabel(title).apply {
        font = UiKit.font(16f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
    }

    init {
        isOpaque = false
        status.font = UiKit.font(12f)
        add(titleLabel)
        add(status)
        add(resetButton)
    }

    /** Shows "All at default" or "N of M changed" in the status, and makes "Reset all" quiet when nothing changed. */
    fun showChanged(changed: Int, total: Int) {
        status.text = if (changed == 0) "All at default" else "$changed of $total changed"
        status.foreground = if (changed == 0) Palette.FG_3 else Palette.SAGE
        (resetButton as? ResetAllButton)?.quiet = changed == 0
    }

    override fun getPreferredSize() = Dimension(SIDE_PANEL_WIDTH, PAD_TOP + TITLE_LINE + STATUS_LINE + PAD_BOTTOM)

    override fun doLayout() {
        val button = resetButton.preferredSize
        val buttonX = width - PAD_X - button.width
        val textWidth = max(0, buttonX - GAP - PAD_X)
        titleLabel.setBounds(PAD_X, PAD_TOP, textWidth, TITLE_LINE)
        status.setBounds(PAD_X, PAD_TOP + TITLE_LINE, textWidth, STATUS_LINE)
        val centerY = PAD_TOP + (TITLE_LINE + STATUS_LINE) / 2
        resetButton.setBounds(buttonX, centerY - button.height / 2, button.width, button.height)
    }

    private companion object {
        const val PAD_X = 16
        const val PAD_TOP = 16
        const val PAD_BOTTOM = 12
        const val TITLE_LINE = 22
        const val STATUS_LINE = 17
        const val GAP = 10
    }
}

/** A component with a text that wraps, so its height depends on its width. */
internal interface HeightForWidth {
    fun heightForWidth(width: Int): Int
}

/**
 * The cards of the panel in a column with a gap between them. The column follows the width of the scroll pane,
 * so only a vertical scroll bar can show. Hidden children take no space.
 */
internal class CardColumn : JPanel(null), Scrollable {
    init {
        isOpaque = true
        background = Palette.BG
    }

    private val shown get() = components.filter { it.isVisible }

    private fun heightOf(child: Component, width: Int): Int =
        if (child is HeightForWidth) child.heightForWidth(width) else child.preferredSize.height

    private fun contentHeight(width: Int): Int {
        val inner = width - PAD_X * 2
        val children = shown
        return children.sumOf { heightOf(it, inner) } + GAP * (children.size - 1).coerceAtLeast(0) + PAD_BOTTOM
    }

    override fun getPreferredSize(): Dimension {
        val viewportWidth = (parent as? JViewport)?.width?.takeIf { it > 0 } ?: width.takeIf { it > 0 } ?: SIDE_PANEL_WIDTH
        return Dimension(viewportWidth, contentHeight(viewportWidth))
    }

    override fun doLayout() {
        val inner = width - PAD_X * 2
        var y = 0
        for (child in shown) {
            val h = heightOf(child, inner)
            child.setBounds(PAD_X, y, inner, h)
            y += h + GAP
        }
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
        max(16, visibleRect.height - 16)
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false

    /** A borderless scroll pane with a dark scroll bar and no horizontal scroll bar around this column. */
    fun inScrollPane(): JScrollPane = JScrollPane(this).apply {
        border = BorderFactory.createEmptyBorder()
        background = Palette.BG
        viewport.background = Palette.BG
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        try { applyDarkScrollbar(this, Palette.BG) } catch (_: Throwable) { }
    }

    private companion object {
        const val PAD_X = 12
        const val PAD_BOTTOM = 12
        const val GAP = 12
    }
}

/**
 * A card with a caption in capital letters and an icon, and rows of the same height under it.
 * A subclass can paint more on the right of the caption with [paintCaptionEnd].
 */
internal open class GroupCard(
    componentName: String,
    private val title: String,
    ikon: Ikon,
    rows: List<JComponent>,
    private val rowHeight: Int,
) : JPanel(null) {
    private val rowComponents = rows.toList()
    private val captionIcon = UiKit.icon(ikon, 14, Palette.FG_3)
    private val captionFont = UiKit.trackedFont(10.5f, 0.1, UiKit.Weight.BOLD)

    init {
        name = componentName
        isOpaque = false
        rowComponents.forEach { add(it) }
    }

    override fun getPreferredSize() = Dimension(MIN_WIDTH, ROWS_TOP + rowComponents.size * rowHeight + PAD_BOTTOM)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun doLayout() {
        rowComponents.forEachIndexed { index, row ->
            row.setBounds(BORDER, ROWS_TOP + index * rowHeight, width - BORDER * 2, rowHeight)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 8, Palette.CARD, Palette.LINE)
            val lineTop = CAPTION_TOP + CAPTION_PAD_TOP
            captionIcon.paintIcon(this, g2, CAPTION_X, lineTop + (CAPTION_LINE - captionIcon.iconHeight) / 2)
            val textX = (CAPTION_X + captionIcon.iconWidth + CAPTION_GAP).toFloat()
            UiKit.drawText(g2, title.uppercase(), captionFont, Palette.FG_3, textX, lineTop.toFloat(), CAPTION_LINE.toFloat())
            paintCaptionEnd(g2, (width - CAPTION_X).toFloat(), lineTop.toFloat(), CAPTION_LINE.toFloat())
        } finally {
            g2.dispose()
        }
    }

    /** Paints more text on the caption line. [right] is the right edge of the caption. */
    protected open fun paintCaptionEnd(g2: Graphics2D, right: Float, top: Float, height: Float) = Unit

    private companion object {
        const val MIN_WIDTH = 200
        const val BORDER = 1
        const val CAPTION_TOP = BORDER + 6
        const val CAPTION_PAD_TOP = 4
        const val CAPTION_LINE = 15
        const val CAPTION_X = BORDER + 14
        const val CAPTION_GAP = 6
        const val ROWS_TOP = CAPTION_TOP + CAPTION_PAD_TOP + CAPTION_LINE + 2
        const val PAD_BOTTOM = 4 + BORDER
    }
}

/** Painting that the slider rows of a group card share. */
internal object SliderRows {
    const val PAD_X = 14
    private const val MARK_INSET = 10
    private const val END_PAD = 2
    private const val END_LINE = 15f

    /** The small font of the labels under the ends of a slider. */
    val endFont = UiKit.font(10.5f)

    /** The line at the top of each row after the first, and the lime bar at the left edge of a changed row. */
    fun paintFrame(g2: Graphics2D, width: Int, height: Int, first: Boolean, changed: Boolean) {
        if (!first) {
            g2.color = Palette.CARD_ROW_LINE
            g2.fillRect(0, 0, width, 1)
        }
        if (changed) {
            g2.color = Palette.LIME
            g2.fill(RoundRectangle2D.Double(-2.0, MARK_INSET.toDouble(), 4.0, (height - MARK_INSET * 2).toDouble(), 4.0, 4.0))
        }
    }

    /**
     * The labels under the ends of the slider: [low] on the left, [high] on the right, and [mid] centered on [midX].
     * [top] is the top of the label line.
     */
    fun paintEnds(g2: Graphics2D, width: Int, top: Float, low: String, high: String, mid: String? = null, midX: Float = 0f) {
        UiKit.drawText(g2, low, endFont, Palette.FG_3, (PAD_X + END_PAD).toFloat(), top, END_LINE)
        val highX = width - PAD_X - END_PAD - UiKit.textWidth(high, endFont)
        UiKit.drawText(g2, high, endFont, Palette.FG_3, highX, top, END_LINE)
        if (mid != null) {
            UiKit.drawText(g2, mid, endFont, MID_LABEL, midX - UiKit.textWidth(mid, endFont) / 2f, top, END_LINE)
        }
    }

    /** The middle label ("Center", "0°") is darker than the end labels. */
    private val MID_LABEL = Palette.LINE_5
}

/**
 * The slider of a control with a default value. The lime fill starts at [default] and goes to the thumb.
 * A tick marks the default, and small ticks mark [minorTicks]. The thumb is gray at the default and lime
 * for a changed value. A click on the track moves the thumb to the click position.
 */
internal class DefaultFillSliderUI(private val default: Int, private val minorTicks: List<Int> = emptyList()) : FlatSliderUI() {
    override fun getThumbSize(): Dimension = Dimension(THUMB, THUMB)

    override fun paintFocus(g: Graphics) = Unit

    override fun paintTrack(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val centerY = trackRect.y + trackRect.height / 2.0
            val left = trackRect.x.toDouble()
            val right = (trackRect.x + trackRect.width).toDouble()
            g2.color = TRACK_BG
            g2.fill(RoundRectangle2D.Double(left, centerY - TRACK / 2, right - left, TRACK, TRACK, TRACK))
            val defaultX = xPositionForValue(default).toDouble()
            val thumbX = thumbRect.x + thumbRect.width / 2.0
            val from = min(defaultX, thumbX)
            val to = max(defaultX, thumbX)
            if (to - from >= 1) {
                g2.color = Palette.LIME
                g2.fill(RoundRectangle2D.Double(from, centerY - TRACK / 2, to - from, TRACK, TRACK, TRACK))
            }
            g2.color = MINOR_TICK
            minorTicks.forEach { value ->
                val x = xPositionForValue(value).toDouble()
                g2.fill(RoundRectangle2D.Double(x, centerY - MINOR_TICK_HEIGHT / 2 + 1, 1.0, MINOR_TICK_HEIGHT, 1.0, 1.0))
            }
            g2.color = TICK
            g2.fill(RoundRectangle2D.Double(defaultX - 1, centerY - TICK_HEIGHT / 2, 2.0, TICK_HEIGHT, 2.0, 2.0))
        } finally {
            g2.dispose()
        }
    }

    override fun paintThumb(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val color = if (slider.value != default) Palette.LIME else THUMB_IDLE
            val cx = thumbRect.x + thumbRect.width / 2.0
            val cy = thumbRect.y + thumbRect.height / 2.0
            fun circle(diameter: Double, fill: Color) {
                g2.color = fill
                g2.fill(Ellipse2D.Double(cx - diameter / 2, cy - diameter / 2, diameter, diameter))
            }
            if (slider.hasFocus()) circle(THUMB + 4.0, FOCUS_GLOW)
            circle(THUMB.toDouble(), color)
            circle(THUMB - 2.0, THUMB_RING)
            circle(THUMB - 8.0, color)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        /** The thumb diameter. The track runs between the thumb centers at the two ends of the slider. */
        const val THUMB = 18
        private const val TRACK = 4.0
        private const val TICK_HEIGHT = 12.0
        private const val MINOR_TICK_HEIGHT = 8.0
        private val TRACK_BG = Palette.LINE_2
        private val TICK = Palette.LINE_5
        private val MINOR_TICK = Palette.HOVER_LINE
        private val THUMB_IDLE = Palette.NEUTRAL_LIGHT
        private val THUMB_RING = Palette.BG
        private val FOCUS_GLOW = Palette.LIME_GLOW

        /** A slider with this UI, a hand cursor and the focus glow. */
        fun slider(min: Int, max: Int, default: Int, minorTicks: List<Int> = emptyList()): JSlider =
            JSlider(min, max, default).apply {
                isOpaque = false
                setUI(DefaultFillSliderUI(default, minorTicks))
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                // The focus glow shows only while the slider has the focus.
                addFocusListener(object : FocusAdapter() {
                    override fun focusGained(e: FocusEvent) = repaint()
                    override fun focusLost(e: FocusEvent) = repaint()
                })
            }

        /** The x position of [value] in slider pixels, computed from the range, so it does not need a layout of the UI. */
        fun xOf(slider: JSlider, value: Int): Float {
            val share = (value - slider.minimum).toFloat() / (slider.maximum - slider.minimum).coerceAtLeast(1)
            return THUMB / 2f + (slider.width - THUMB) * share
        }
    }
}
