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
 * In advanced mode (B-68), the title is the serve number of the next clip: "1st serve" or "2nd serve" (see [serve]).
 */
internal class PointStartButton(onStart: () -> Unit) : JButton() {

    /** True while a point waits for its end. */
    var pending: Boolean = false
        private set

    /** The start time of the pending point, or "" without a pending point. */
    var value: String = ""
        private set

    /**
     * The serve number (1 or 2) in advanced mode, or null in basic mode (B-68). The title is then "1st serve" or
     * "2nd serve" in place of "Point start". The scoring sets it from the pack at the pending start or at the playhead.
     */
    var serve: Int? = null
        set(value) {
            if (field == value) return
            val modeChanged = (field == null) != (value == null)
            field = value
            updateTooltip()
            if (modeChanged) revalidate()
            repaint()
        }

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
        updateTooltip()
        repaint()
    }

    private fun updateTooltip() {
        toolTipText = when {
            !pending -> tr("points.pointStart.tooltip", "key" to tr("key.enter"))
            serve != null -> tr("points.pointStart.serve.pending.tooltip", "time" to value)
            else -> tr("points.pointStart.pending.tooltip", "time" to value)
        }
    }

    /** The title: "Point start" in basic mode, the serve number in advanced mode. */
    val title: String
        get() = when (serve) {
            null -> TITLE
            2 -> tr("points.serve.second")
            else -> tr("points.serve.first")
        }

    /** The longest title of the mode, so that the button keeps its width when the serve number changes. */
    private fun widestTitleWidth(): Int {
        val titles = if (serve == null) listOf(TITLE) else listOf(tr("points.serve.first"), tr("points.serve.second"))
        return titles.maxOf { ceil(UiKit.textWidth(it, titleFont)).toInt() }
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
        val title = widestTitleWidth()
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
            val title = title
            if (compact && pending) {
                val half = height / 2f
                UiKit.drawText(g2, title, titleFont, Palette.FG, x, 2f, half - 1f)
                UiKit.drawText(g2, value, valueFont, Palette.LIME, x, half - 1f, half - 1f)
            } else {
                UiKit.drawText(g2, title, titleFont, Palette.FG, x, 0f, height.toFloat())
                if (pending) {
                    val valueX = x + UiKit.textWidth(title, titleFont) + GAP
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
