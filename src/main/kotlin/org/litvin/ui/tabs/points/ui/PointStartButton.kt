package org.litvin.ui.tabs.points.ui

import org.litvin.shared.util.Timecode
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JButton
import kotlin.math.ceil
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.i18n.tr

/**
 * The Point start button at the left end of the point bar under the video: a title, the start time of the
 * pending point, and the Enter key chip. C also starts a point.
 * Without a pending point, the button has the lime outline: it is the next step.
 * While a point is pending, the button has a lime tint and shows the start of the point.
 * The winner keys (Q, W, E) end the pending point.
 */
internal class PointStartButton(onStart: () -> Unit) : JButton() {

    /** True while a point waits for its end. */
    var pending: Boolean = false
        private set

    /** The start time of the pending point, or "" without a pending point. */
    var value: String = ""
        private set

    init {
        name = "points-point-start"
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        // Space must stay the play hotkey, so the button never keeps the focus.
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { onStart() }
        setState(null)
    }

    fun setState(pendingStartMs: Long?) {
        val nextPending = pendingStartMs != null
        val nextValue = pendingStartMs?.let(Timecode::format).orEmpty()
        if (pending == nextPending && value == nextValue && toolTipText != null) return
        pending = nextPending
        value = nextValue
        toolTipText = if (pending) {
            tr("points.pointStart.pending.tooltip", "time" to value)
        } else {
            tr("points.pointStart.tooltip", "key" to tr("key.enter"))
        }
        repaint()
    }

    private val titleFont get() = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
    private val valueFont get() = UiKit.font(13f, UiKit.Weight.SEMIBOLD)

    /**
     * The form for a narrow point bar: the time of a pending point is on a second line under the title, so the button
     * does not keep space for the time next to the title (B-11 E4-T8). The point bar sets it.
     */
    var compact: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }

    /** The width of the button with the time next to the title. */
    fun fullWidth(): Int = widthFor(compact = false)

    private fun widthFor(compact: Boolean): Int {
        // The width keeps space for the time, so the bar does not move when a point starts.
        val time = ceil(UiKit.textWidth(TIME_SAMPLE, valueFont)).toInt()
        val title = ceil(UiKit.textWidth(TITLE, titleFont)).toInt()
        val text = if (compact) maxOf(title, time) else title + GAP + time
        return PAD_X * 2 + text + GAP + KeyChips.width(KEY)
    }

    override fun getPreferredSize() = Dimension(widthFor(compact), HEIGHT)

    override fun getMinimumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover
            UiKit.paintBox(g2, 0, 0, width, height, 8, if (hover) Palette.RAISED_2 else Palette.RAISED, null)
            if (pending) UiKit.paintBox(g2, 0, 0, width, height, 8, Palette.LIME_TINT, null)
            UiKit.paintBox(g2, 0, 0, width, height, 8, null, Palette.LIME_LINE)
            if (!pending) UiKit.paintBox(g2, 1, 1, width - 2, height - 2, 7, null, Palette.LIME_EDGE)

            val x = PAD_X.toFloat()
            if (compact && pending) {
                val half = height / 2f
                UiKit.drawText(g2, TITLE, titleFont, Palette.FG, x, 2f, half - 1f)
                UiKit.drawText(g2, value, valueFont, Palette.LIME, x, half - 1f, half - 1f)
            } else {
                UiKit.drawText(g2, TITLE, titleFont, Palette.FG, x, 0f, height.toFloat())
                if (pending) {
                    val valueX = x + UiKit.textWidth(TITLE, titleFont) + GAP
                    UiKit.drawText(g2, value, valueFont, Palette.LIME, valueX, 0f, height.toFloat())
                }
            }
            KeyChips.paint(g2, KEY, width - PAD_X - KeyChips.width(KEY), (height - KeyChips.HEIGHT) / 2)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 40
        private const val PAD_X = 12
        private const val GAP = 10
        private val TITLE: String get() = tr("points.pointStart")
        private val KEY: String get() = tr("key.enter")
        private const val TIME_SAMPLE = "00:00:00.0"
    }
}
