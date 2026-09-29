package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.Ikon
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JButton
import kotlin.math.ceil
import org.litvin.ui.commons.UiKit

/**
 * A text button of the Points tab, the `.btn` element of the design.
 * [Kind.SECONDARY] is the dark default, [Kind.LIME] is the main action of a dialog, and [Kind.DANGER] deletes.
 */
internal class PointsButton(
    text: String,
    private val ikon: Ikon? = null,
    private val kind: Kind = Kind.SECONDARY,
    private val buttonHeight: Int = 34,
) : JButton(text) {
    enum class Kind { SECONDARY, LIME, DANGER }

    private val textFont get() = if (kind == Kind.LIME) UiKit.font(12.5f, UiKit.Weight.BOLD) else UiKit.font(12.5f)

    private val iconColor: Color
        get() = when (kind) {
            Kind.SECONDARY -> UiKit.FG_2
            Kind.LIME -> UiKit.ON_LIME
            Kind.DANGER -> UiKit.RED
        }

    private var cachedIcon: Pair<Color, Icon>? = null

    /** A color square in front of the text, for the comment color button. Null shows no square. */
    var swatch: Color? = null
        set(value) {
            field = value
            revalidate()
            repaint()
        }

    private fun iconFor(color: Color): Icon? {
        val source = ikon ?: return null
        cachedIcon?.let { (c, icon) -> if (c == color) return icon }
        return UiKit.icon(source, ICON_SIZE, color).also { cachedIcon = color to it }
    }

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private fun leadingWidth() = when {
        swatch != null -> SWATCH + SWATCH_GAP
        ikon != null -> ICON_SIZE + GAP
        else -> 0
    }

    override fun getPreferredSize(): Dimension =
        Dimension(ceil(UiKit.textWidth(text, textFont)).toInt() + leadingWidth() + PADDING * 2, buttonHeight)

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover && isEnabled
            val (fill, border, fg) = when (kind) {
                Kind.SECONDARY -> Triple(
                    if (hover) UiKit.RAISED_2 else UiKit.RAISED,
                    if (hover) Color(0x444444) else UiKit.LINE_2,
                    UiKit.FG,
                )
                Kind.LIME -> Triple(if (hover) UiKit.LIME_HOVER else UiKit.LIME, null, UiKit.ON_LIME)
                Kind.DANGER -> Triple(
                    if (hover) UiKit.RED_TINT else null,
                    if (hover) UiKit.RED else Color(255, 115, 81, 115),
                    UiKit.RED,
                )
            }
            UiKit.paintBox(g2, 0, 0, width, height, 4, fill, border)
            val contentWidth = leadingWidth() + UiKit.textWidth(text, textFont)
            var x = (width - contentWidth) / 2f
            val color = swatch
            val icon = iconFor(iconColor)
            if (color != null) {
                UiKit.paintBox(g2, x.toInt(), (height - SWATCH) / 2, SWATCH, SWATCH, 3, color, Color(255, 255, 255, 64))
                x += SWATCH + SWATCH_GAP
            } else if (icon != null) {
                icon.paintIcon(this, g2, x.toInt(), (height - icon.iconHeight) / 2)
                x += ICON_SIZE + GAP
            }
            UiKit.drawText(g2, text, textFont, if (isEnabled) fg else UiKit.FG_3, x, 0f, height.toFloat())
            if (isFocusOwner) UiKit.paintBox(g2, 0, 0, width, height, 4, null, UiKit.LIME_LINE)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PADDING = 12
        const val GAP = 6
        const val ICON_SIZE = 17
        const val SWATCH = 16
        const val SWATCH_GAP = 8
    }
}
