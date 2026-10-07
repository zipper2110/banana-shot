package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2RoundMZ
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.font.TextAttribute
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.Timer
import kotlin.math.ceil
import kotlin.math.max

/**
 * A seek button: a lime direction icon, the step ("−5s") and a key chip for each key of its hotkey.
 * It is one step brighter than a secondary button.
 */
internal class SeekButton(
    private val label: String,
    val deltaMs: Long,
    private val keys: List<String>,
    direction: Ikon,
    tooltip: String,
    componentName: String,
) : JButton() {

    private val directionIcon: Icon = UiKit.icon(direction, 18, Palette.LIME)
    private val iconFirst = deltaMs < 0
    private var flashing = false
    private val flashTimer = Timer(FLASH_MS) { flashing = false; repaint() }.apply { isRepeats = false }

    /** False hides the direction icon, for a narrow bar. */
    var showDirection = true
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }

    private val labelFont get() = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)

    init {
        name = componentName
        toolTipText = tooltip
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    fun flash() {
        flashing = true
        flashTimer.restart()
        repaint()
    }

    private fun keysWidth() = keys.sumOf { KeyChips.width(it, KeyChipStyle.SEEK) } + KEY_GAP * (keys.size - 1)

    /** The icon is 18 px wide with -2 px margins on both sides, as in the design. */
    private fun iconWidth() = if (showDirection) 14 + GAP else 0

    private fun contentWidth(): Float = iconWidth() + UiKit.textWidth(label, labelFont) + GAP + KEYS_MARGIN + keysWidth()

    override fun getPreferredSize() = Dimension(max(MIN_WIDTH, ceil(contentWidth()).toInt() + PADDING * 2), HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover
            val border = when {
                flashing -> Palette.LIME
                hover -> Palette.LINE_5
                else -> Palette.HOVER_LINE
            }
            UiKit.paintBox(g2, 0, 0, width, height, 4, if (hover) Palette.CONTROL else Palette.RAISED_2, border)
            // The inset highlight at the top edge.
            g2.color = Palette.HIGHLIGHT
            g2.fillRect(3, 1, width - 6, 1)

            var x = (width - contentWidth()) / 2f
            val paintIcon = {
                directionIcon.paintIcon(this, g2, (x - 2).toInt(), (height - directionIcon.iconHeight) / 2)
                x += iconWidth()
            }
            if (showDirection && iconFirst) paintIcon()
            UiKit.drawText(g2, label, labelFont, Palette.FG, x, 0f, height.toFloat())
            x += UiKit.textWidth(label, labelFont) + GAP
            if (showDirection && !iconFirst) paintIcon()
            x += KEYS_MARGIN
            val chipY = (height - KeyChips.HEIGHT) / 2
            for (key in keys) {
                KeyChips.paint(g2, key, x.toInt(), chipY, KeyChipStyle.SEEK)
                x += KeyChips.width(key, KeyChipStyle.SEEK) + KEY_GAP
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val HEIGHT = 40
        const val MIN_WIDTH = 72
        const val PADDING = 10
        const val GAP = 6
        const val KEY_GAP = 3
        const val KEYS_MARGIN = 2
        const val FLASH_MS = 160
    }
}

/**
 * The size and the texts of a [PlayButton]. [LARGE] is the button of the Points and Scoring tabs.
 * [COMPACT] is the button of the playback bar of the Colors and Transform tabs.
 */
internal data class PlayButtonStyle(
    val size: Int,
    val iconSize: Int,
    val keyFontSize: Float,
    val keyColor: Color,
    val playTooltip: String,
    val pauseTooltip: String,
) {
    companion object {
        val LARGE = PlayButtonStyle(56, 28, 8.5f, Palette.ON_LIME_MUTED, "SPACE — Play", "SPACE — Pause")
        val COMPACT = PlayButtonStyle(48, 26, 8f, Palette.ON_LIME_MUTED, "SPACE - Play", "SPACE - Pause")
    }
}

/** The lime play button with the SPACE key as small text under the icon. */
internal class PlayButton(private val style: PlayButtonStyle = PlayButtonStyle.LARGE) : JButton() {
    private val playIcon = UiKit.icon(Material2RoundMZ.PLAY_ARROW, style.iconSize, Palette.ON_LIME)
    private val pauseIcon = UiKit.icon(Material2RoundMZ.PAUSE, style.iconSize, Palette.ON_LIME)
    private val keyFont = UiKit.font(style.keyFontSize, UiKit.Weight.BOLD).deriveFont(mapOf(TextAttribute.TRACKING to 0.06))

    var playing = false
        set(value) {
            field = value
            toolTipText = if (value) style.pauseTooltip else style.playTooltip
            repaint()
        }

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        // Space must stay the play hotkey, so the button never keeps the focus.
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = style.playTooltip
    }

    override fun getPreferredSize() = Dimension(style.size + 2, style.size + 2)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            // The 1 px glow ring around the button.
            UiKit.paintBox(g2, 0, 0, width, height, 9, null, Palette.LIME_GLOW)
            val fill = when {
                model.isArmed && model.isPressed -> Palette.LIME_PRESSED
                model.isRollover -> Palette.LIME_HOVER
                else -> Palette.LIME_FILL
            }
            UiKit.paintBox(g2, 1, 1, width - 2, height - 2, 8, fill, null)
            val icon = if (playing) pauseIcon else playIcon
            val keyMetrics = keyFont.getLineMetrics(KEY, UiKit.frc)
            val keyHeight = keyMetrics.ascent + keyMetrics.descent
            val contentHeight = icon.iconHeight - 1 + keyHeight
            val top = (height - contentHeight) / 2f
            icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, top.toInt())
            g2.font = keyFont
            g2.color = style.keyColor
            g2.drawString(KEY, (width - UiKit.textWidth(KEY, keyFont)) / 2f, top + icon.iconHeight - 1 + keyMetrics.ascent)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val KEY = "SPACE"
    }
}
