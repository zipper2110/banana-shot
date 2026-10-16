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
            "The point started at $value. Press Q, W, or E at the end of the point to give the winner."
        } else {
            "Set point start at the playhead [Enter]"
        }
        repaint()
    }

    private val titleFont get() = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
    private val valueFont get() = UiKit.font(13f, UiKit.Weight.SEMIBOLD)

    override fun getPreferredSize(): Dimension {
        // The width keeps space for the time, so the bar does not move when a point starts.
        val time = ceil(UiKit.textWidth(TIME_SAMPLE, valueFont)).toInt()
        val title = ceil(UiKit.textWidth(TITLE, titleFont)).toInt()
        return Dimension(PAD_X * 2 + title + GAP + time + GAP + KeyChips.width(KEY), HEIGHT)
    }

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
            UiKit.drawText(g2, TITLE, titleFont, Palette.FG, x, 0f, height.toFloat())
            if (pending) {
                val valueX = x + UiKit.textWidth(TITLE, titleFont) + GAP
                UiKit.drawText(g2, value, valueFont, Palette.LIME, valueX, 0f, height.toFloat())
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
        private const val TITLE = "Point start"
        private const val KEY = "Enter"
        private const val TIME_SAMPLE = "00:00:00.0"
    }
}
