package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
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
 * The frame step toggle: a check box, "Frame step", and the F key chip. It has a lime border when it is on.
 * A compact toggle has no label; the tooltip names the action.
 */
internal class FrameStepToggle : JButton(tr("points.frameStep")) {
    var compact = false
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }

    var on = false
        set(value) {
            if (field == value) return
            field = value
            model.isSelected = value
            repaint()
        }
    private val textFont get() = UiKit.font(12f)
    private val check by lazy { UiKit.icon(Material2AL.CHECK, 12, Palette.ON_LIME) }

    init {
        name = "points-frame-step"
        toolTipText = tr("points.frameStep.tooltip", "left" to tr("key.left"), "right" to tr("key.right"), "key" to "F")
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private fun labelWidth() = if (compact) 0 else ceil(UiKit.textWidth(text, textFont)).toInt() + GAP

    override fun getPreferredSize() = Dimension(PAD * 2 + BOX + GAP + labelWidth() + KeyChips.width(KEY), HEIGHT)

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover
            val border = when {
                on -> Palette.LIME_LINE
                hover -> Palette.HOVER_LINE
                else -> Palette.LINE_2
            }
            UiKit.paintBox(g2, 0, 0, width, height, 4, if (hover) Palette.RAISED_2 else Palette.RAISED, border)
            var x = PAD
            val boxY = (height - BOX) / 2
            if (on) {
                UiKit.paintBox(g2, x, boxY, BOX, BOX, 3, Palette.LIME_FILL, null)
                check.paintIcon(this, g2, x + (BOX - check.iconWidth) / 2, boxY + (BOX - check.iconHeight) / 2)
            } else {
                UiKit.paintBox(g2, x, boxY, BOX, BOX, 3, null, Palette.LINE_5)
            }
            x += BOX + GAP
            if (!compact) UiKit.drawText(g2, text, textFont, Palette.FG, x.toFloat(), 0f, height.toFloat())
            x += labelWidth()
            KeyChips.paint(g2, KEY, x, (height - KeyChips.HEIGHT) / 2)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 32
        private const val PAD = 12
        private const val BOX = 14
        private const val GAP = 7
        private const val KEY = "F"
    }
}
