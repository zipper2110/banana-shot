package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JButton

/**
 * A small square icon button, the `.btn-icon` element of the design. It has no fill until the pointer is over it.
 * A [danger] button turns red on hover. [color] is the icon color at rest.
 */
internal class RowIconButton(
    private val ikon: Ikon,
    tooltip: String,
    private val danger: Boolean = false,
    private val color: Color = Palette.FG_2,
    private val side: Int = 26,
) : JButton() {

    private val icons = HashMap<Pair<Ikon, Color>, Icon>()

    init {
        toolTipText = tooltip
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() = Dimension(side, side)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover
            val iconColor = when {
                hover && danger -> Palette.RED
                hover && color == Palette.FG_2 -> Palette.FG
                else -> color
            }
            if (hover) {
                if (danger) UiKit.paintBox(g2, 0, 0, width, height, 4, Palette.RED_TINT, Palette.RED_LINE)
                else UiKit.paintBox(g2, 0, 0, width, height, 4, Palette.RAISED_2, Palette.LINE_2)
            }
            val icon = icons.getOrPut(ikon to iconColor) { UiKit.icon(ikon, ICON_SIZE, iconColor) }
            icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val ICON_SIZE = 17
    }
}
