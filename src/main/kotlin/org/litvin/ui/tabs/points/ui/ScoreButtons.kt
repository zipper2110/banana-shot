package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ScoreboardComponent
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JButton
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit

/** The names and colors of the two players. */
internal data class ScorePlayers(
    val p1Name: String = "Player 1",
    val p2Name: String = "Player 2",
    val p1Color: Color = Color(ScoreboardComponent.DEFAULT_PLAYER1_RGB),
    val p2Color: Color = Color(ScoreboardComponent.DEFAULT_PLAYER2_RGB),
) {
    fun name(player: Int) = if (player == 1) p1Name else p2Name
    fun color(player: Int) = if (player == 1) p1Color else p2Color
}

/**
 * An outcome button: a color strip, the player name (or "No point") and the key chip.
 * The selected outcome fills the button with the player color. When the point has no outcome yet,
 * the buttons have a lime outline: choosing the winner is the next step.
 */
internal class OutcomeButton(private val player: Int, private val key: String) : JButton() {
    private var color: Color = Palette.LINE_6
    private var on = false
    private var next = false
    private val labelFont get() = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        // Space must stay the play hotkey, so the buttons never keep the focus.
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    fun setPlayer(name: String, color: Color) {
        text = name
        this.color = color
        toolTipText = if (player == 0) "$key — No point" else "$key — Point for $name"
        repaint()
    }

    fun update(enabled: Boolean, on: Boolean, next: Boolean) {
        isEnabled = enabled
        this.on = on
        this.next = next && !on
        model.isSelected = on
        repaint()
    }

    /** True when this outcome is the outcome of the selected point. */
    val isOn: Boolean get() = on

    override fun getPreferredSize() = Dimension(0, HEIGHT)
    override fun getMinimumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ScoringUi.DISABLED_ALPHA)
            val hover = model.isRollover && isEnabled
            val noPoint = player == 0
            val (fill, border) = when {
                on && noPoint -> Palette.LINE_5 to Palette.FG_3
                on -> color to color
                next -> (if (hover) Palette.RAISED_2 else Palette.RAISED) to Palette.LIME_LINE
                hover -> Palette.RAISED_2 to Palette.HOVER_LINE
                else -> Palette.RAISED to Palette.LINE_2
            }
            val shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 8.0, 8.0)
            g2.color = fill
            g2.fill(shape)
            val clip = g2.clip
            g2.clip(shape)
            g2.color = if (on) Palette.SHADE else color
            g2.fillRect(0, 0, STRIP, height)
            g2.clip = clip
            UiKit.paintBox(g2, 0, 0, width, height, 4, null, border)
            if (next) {
                // The inset glow of the next step.
                UiKit.paintBox(g2, 1, 1, width - 2, height - 2, 3, null, Palette.LIME_EDGE)
            }

            val textColor = when {
                on && noPoint -> Palette.FG_STRONG
                on -> ScoringUi.onPlayer(color)
                else -> Palette.FG
            }
            val chipStyle = when {
                on && noPoint -> ON_NO_POINT_KEY
                on -> ScoringUi.onPlayerKeyStyle(color)
                else -> KeyChipStyle.DEFAULT
            }
            val chipWidth = KeyChips.width(key, chipStyle)
            val chipX = width - PAD_RIGHT - chipWidth
            val x = STRIP + GAP
            val label = UiKit.ellipsize(text.orEmpty(), labelFont, (chipX - LABEL_GAP - x).toFloat())
            UiKit.drawText(g2, label, labelFont, textColor, x.toFloat(), 0f, height.toFloat())
            KeyChips.paint(g2, key, chipX, (height - chipStyle.height) / 2, chipStyle)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 40
        const val STRIP = 6
        const val GAP = 9
        const val LABEL_GAP = 6
        const val PAD_RIGHT = 10

        val ON_NO_POINT_KEY = KeyChipStyle(fill = Palette.SHADE, border = Palette.SHADE_2, text = Palette.FG_STRONG)
    }
}

/** A star button: yellow and filled for a favorite point, a gray outline otherwise. */
internal class FavoriteButton(private val side: Int) : JButton() {
    var favorite = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }
    private val icons = HashMap<Pair<Boolean, Color>, Icon>()

    init {
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
            val hover = model.isRollover && isEnabled
            if (hover) UiKit.paintBox(g2, 0, 0, width, height, 4, Palette.RAISED_2, Palette.LINE_2)
            val color = when {
                favorite -> Palette.YELLOW
                hover -> Palette.FG
                else -> Palette.FG_3
            }
            val icon = icons.getOrPut(favorite to color) {
                UiKit.icon(if (favorite) Material2MZ.STAR else Material2MZ.STAR_BORDER, 17, color)
            }
            icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }
}
