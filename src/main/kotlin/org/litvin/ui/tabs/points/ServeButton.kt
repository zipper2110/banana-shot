package org.litvin.ui.tabs.points

import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.ui.OutcomeButton
import org.litvin.ui.tabs.points.ui.ScoringUi
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JButton

/**
 * The serve button: a strip on the left and a racket in the color of the side that serves, and the S key chip.
 * Gray when the server is not known. A dark side color stands on a light disc, so that the racket stays visible on the dark button.
 */
internal class ServeButton(private val buttonHeight: Int) : JButton() {
    var color: Color? = null
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }
    private val icons = HashMap<Color, Icon>()

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        // Space must stay the play hotkey, so the button never keeps the focus.
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() =
        Dimension(STRIP + PAD * 2 + BADGE + GAP + KeyChips.width(KEY, KeyChipStyle.SMALL), buttonHeight)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ScoringUi.DISABLED_ALPHA)
            val hover = model.isRollover && isEnabled
            val sideColor = color
            // The strip on the left has the color of the side that serves, as the strips of the outcome buttons.
            val shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 8.0, 8.0)
            g2.color = if (hover) Palette.RAISED_2 else Palette.RAISED
            g2.fill(shape)
            val clip = g2.clip
            g2.clip(shape)
            g2.color = sideColor ?: Palette.LINE_6
            g2.fillRect(0, 0, STRIP, height)
            g2.clip = clip
            UiKit.paintBox(g2, 0, 0, width, height, 4, null, if (hover) Palette.HOVER_LINE else Palette.LINE_2)
            val iconColor = sideColor ?: Palette.FG_2
            val y = (height - BADGE) / 2
            val badgeX = STRIP + PAD
            if (sideColor != null && ScoringUi.needsBadge(sideColor)) {
                g2.color = Palette.NEUTRAL_LIGHT
                g2.fillOval(badgeX, y, BADGE, BADGE)
            }
            val icon = icons.getOrPut(iconColor) { UiKit.icon(Material2MZ.SPORTS_TENNIS, ICON, iconColor) }
            icon.paintIcon(this, g2, badgeX + (BADGE - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
            val chipX = badgeX + BADGE + GAP
            KeyChips.paint(g2, KEY, chipX, (height - KeyChipStyle.SMALL.height) / 2, KeyChipStyle.SMALL)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val STRIP = OutcomeButton.STRIP
        const val PAD = 10
        const val GAP = 6
        const val ICON = 17
        const val BADGE = 22
        const val KEY = "S"
    }
}
