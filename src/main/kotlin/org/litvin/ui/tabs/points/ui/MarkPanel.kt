package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.shared.util.Timecode
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GridLayout
import java.awt.geom.Ellipse2D
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.ceil
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit

/**
 * The "Mark a point" panel at the top of the side column: two numbered step tiles for Point start (C)
 * and Point end (V), and a hint line with the next key to press.
 * While a point is pending, the start tile shows its start and the end tile follows the playhead.
 */
internal class MarkPanel(
    onStart: () -> Unit,
    onEnd: () -> Unit,
) : JPanel(null) {

    val startTile = StepTile(1, "Point start", "C").apply {
        name = "points-point-start"
        toolTipText = "Set point start at the playhead [C]"
    }
    val endTile = StepTile(2, "Point end", "V").apply {
        name = "points-point-end"
        toolTipText = "Set point end at the playhead [V]"
    }
    private val steps = JPanel(GridLayout(1, 2, 8, 0)).apply {
        isOpaque = false
        add(startTile)
        add(endTile)
    }
    private val hint = HintLine()

    /** The text of the head on the right side: "Pending point" while a point waits for its end. */
    internal var stepOfText: String = ""
        private set

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING)
        add(steps)
        add(hint)
        startTile.addActionListener { onStart() }
        endTile.addActionListener { onEnd() }
        setState(null, 0)
    }

    fun setState(pendingStartMs: Long?, currentMs: Long) {
        val pending = pendingStartMs != null
        startTile.update(if (pending) StepTile.State.DONE else StepTile.State.NEXT, pendingStartMs?.let(Timecode::format) ?: "—")
        endTile.update(if (pending) StepTile.State.LIVE else StepTile.State.IDLE, if (pending) Timecode.format(currentMs) else "—")
        val nextStep = if (pending) "Pending point" else ""
        if (stepOfText != nextStep) {
            stepOfText = nextStep
            repaint()
        }
        if (pending) hint.set("Press", "V", "at the end of the point to set End.")
        else hint.set("Press", "C", "at the start of a point.")
    }

    private val titleFont get() = UiKit.font(14f, UiKit.Weight.SEMIBOLD)

    override fun getPreferredSize(): Dimension {
        val insets = insets
        val h = insets.top + HEAD_HEIGHT + HEAD_GAP + steps.preferredSize.height + HINT_GAP + hint.preferredSize.height + insets.bottom
        return Dimension(insets.left + insets.right + steps.preferredSize.width, h)
    }

    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun doLayout() {
        val insets = insets
        val w = width - insets.left - insets.right
        var y = insets.top + HEAD_HEIGHT + HEAD_GAP
        steps.setBounds(insets.left, y, w, steps.preferredSize.height)
        y += steps.height + HINT_GAP
        hint.setBounds(insets.left + 2, y, w - 2, hint.preferredSize.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 8, Palette.CARD, Palette.LINE)
            UiKit.drawText(g2, "Mark a point", titleFont, Palette.FG, insets.left.toFloat(), insets.top.toFloat(), HEAD_HEIGHT.toFloat())
            if (stepOfText.isNotEmpty()) {
                val font = UiKit.font(11.5f)
                val x = width - insets.right - UiKit.textWidth(stepOfText, font)
                UiKit.drawText(g2, stepOfText, font, Palette.FG_3, x, insets.top.toFloat(), HEAD_HEIGHT.toFloat())
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PADDING = 12
        const val HEAD_HEIGHT = 20
        const val HEAD_GAP = 10
        const val HINT_GAP = 10
    }
}

/**
 * One step of the mark flow: a number in a circle, a title, a key chip and a time value.
 * [State.NEXT] has the lime outline, [State.DONE] is filled with a lime tint, and [State.LIVE] is the next
 * step while its value follows the playhead.
 */
internal class StepTile(private val number: Int, private val title: String, private val key: String) : JButton() {
    enum class State { IDLE, NEXT, DONE, LIVE }

    var state: State = State.IDLE
        private set

    /** The time in the tile, or "—" when the step has no time yet. */
    var value: String = "—"
        private set

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    fun update(state: State, value: String) {
        if (this.state == state && this.value == value) return
        this.state = state
        this.value = value
        repaint()
    }

    private val titleFont get() = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
    private val valueFont get() = UiKit.font(15f, UiKit.Weight.SEMIBOLD)

    override fun getPreferredSize(): Dimension {
        val top = CIRCLE + 7 + ceil(UiKit.textWidth(title, titleFont)).toInt() + 8 + KeyChips.width(key)
        return Dimension(PAD_X * 2 + top, PAD_TOP + TOP_ROW + ROW_GAP + VALUE_ROW + PAD_BOTTOM)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val next = state == State.NEXT || state == State.LIVE
            val done = state == State.DONE
            val border = when {
                next -> Palette.LIME_LINE
                model.isRollover -> Palette.HOVER_LINE
                else -> Palette.LINE_2
            }
            UiKit.paintBox(g2, 0, 0, width, height, 6, Palette.RAISED, null)
            if (done) UiKit.paintBox(g2, 0, 0, width, height, 6, Palette.LIME_TINT, null)
            UiKit.paintBox(g2, 0, 0, width, height, 6, null, border)
            if (next) UiKit.paintBox(g2, 1, 1, width - 2, height - 2, 5, null, Palette.LIME_EDGE)

            // Top row: number, title and key chip.
            val rowTop = PAD_TOP
            val circleY = rowTop + (TOP_ROW - CIRCLE) / 2.0
            val circle = Ellipse2D.Double(PAD_X + 0.5, circleY + 0.5, CIRCLE - 1.0, CIRCLE - 1.0)
            val numberColor = when {
                done -> {
                    g2.color = Palette.LIME_FILL
                    g2.fill(Ellipse2D.Double(PAD_X.toDouble(), circleY, CIRCLE.toDouble(), CIRCLE.toDouble()))
                    Palette.ON_LIME
                }
                next -> {
                    g2.color = Palette.LIME
                    g2.draw(circle)
                    Palette.LIME
                }
                else -> {
                    g2.color = Palette.LINE_2
                    g2.draw(circle)
                    Palette.FG_2
                }
            }
            val numberFont = UiKit.font(10.5f, UiKit.Weight.BOLD)
            val numberText = number.toString()
            UiKit.drawText(
                g2, numberText, numberFont, numberColor,
                PAD_X + (CIRCLE - UiKit.textWidth(numberText, numberFont)) / 2f, circleY.toFloat(), CIRCLE.toFloat(),
            )
            UiKit.drawText(g2, title, titleFont, Palette.FG, (PAD_X + CIRCLE + 7).toFloat(), rowTop.toFloat(), TOP_ROW.toFloat())
            KeyChips.paint(g2, key, width - PAD_X - KeyChips.width(key), rowTop + (TOP_ROW - KeyChips.HEIGHT) / 2)

            val valueColor = when (state) {
                State.DONE -> Palette.LIME
                State.LIVE -> Palette.FG_2
                else -> Palette.FG_3
            }
            UiKit.drawText(g2, value, valueFont, valueColor, PAD_X.toFloat(), (rowTop + TOP_ROW + ROW_GAP).toFloat(), VALUE_ROW.toFloat())
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 10
        const val PAD_TOP = 9
        const val PAD_BOTTOM = 10
        const val TOP_ROW = 18
        const val ROW_GAP = 6
        const val VALUE_ROW = 21
        const val CIRCLE = 18
    }
}

/** The hint line of the mark panel: an info icon and a sentence with one key chip. */
internal class HintLine : JComponent() {
    private val icon = UiKit.icon(Material2AL.INFO, 16, Palette.FG_3)
    private var before = ""
    private var key = ""
    private var after = ""
    private val textFont get() = UiKit.font(12f)

    /** The full sentence, for tests and tooltips. */
    val text: String get() = "$before $key $after"

    fun set(before: String, key: String, after: String) {
        if (this.before == before && this.key == key && this.after == after) return
        this.before = before
        this.key = key
        this.after = after
        repaint()
    }

    override fun getPreferredSize() = Dimension(0, 20)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            icon.paintIcon(this, g2, 0, (height - icon.iconHeight) / 2)
            var x = icon.iconWidth + 6f
            val space = UiKit.textWidth(" ", textFont)
            UiKit.drawText(g2, before, textFont, Palette.FG_2, x, 0f, height.toFloat())
            x += UiKit.textWidth(before, textFont) + space
            KeyChips.paint(g2, key, x.toInt(), (height - KeyChips.HEIGHT) / 2)
            x += KeyChips.width(key) + space
            UiKit.drawText(g2, after, textFont, Palette.FG_2, x, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }
}
