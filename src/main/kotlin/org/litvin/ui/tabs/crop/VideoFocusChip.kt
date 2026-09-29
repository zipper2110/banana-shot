package org.litvin.ui.tabs.crop

import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.media.OverlayShape
import org.litvin.ui.commons.UiKit
import java.awt.Color
import kotlin.math.max

/**
 * The chip at the top-left corner of the video. It tells if the arrow keys move the crop rectangle.
 * Without the keyboard focus it says "Click the video to move the crop with the arrow keys".
 * With the focus it shows the keys, and a lime frame goes around the video.
 *
 * The player draws the chip over the video with libass, so the chip is a list of [OverlayShape]s.
 * The text widths come from the AWT metrics of the same font.
 */
internal object VideoFocusChip {
    const val HINT = "Click the video to move the crop with the arrow keys"
    val ARROWS = listOf("←", "↑", "→", "↓")
    const val MOVE_TEXT = "move 1 px ·"
    const val SHIFT_KEY = "Shift"
    const val FAST_TEXT = "10 px"

    private const val FONT = "Segoe UI"
    private const val LEFT = 12.0
    private const val TOP = 12.0
    private const val HEIGHT = 28.0
    private const val PAD_LEFT = 8.0
    private const val PAD_RIGHT = 10.0
    private const val GAP = 7.0
    private const val ICON = 16.0
    private const val TEXT_EM = 11.5
    private const val KEY_EM = 10.0
    private const val KEY_HEIGHT = 16.0
    private const val FRAME_WIDTH = 2.0

    private val BACKGROUND = Color(0, 0, 0, 184)
    private val BORDER = Color(0x3A, 0x3A, 0x3A)
    private val BORDER_FOCUSED = Color(161, 254, 0, 128)
    private val FRAME = Color(161, 254, 0, 140)
    private val KEY_BG = Color(0x16, 0x16, 0x16)
    private val KEY_LINE = Color(0x45, 0x45, 0x45)

    /** A part of the chip content. */
    private sealed interface Item {
        data class Text(val text: String) : Item
        data class Key(val label: String) : Item
    }

    /** The texts of the chip in reading order, for tests and the accessible description. */
    fun text(focused: Boolean): String =
        if (focused) (ARROWS + listOf(MOVE_TEXT, SHIFT_KEY, FAST_TEXT)).joinToString(" ") else HINT

    /**
     * The shapes of the chip, and of the focus frame when [focused] is true.
     * [width] and [height] are the size of the video component in pixels.
     */
    fun shapes(focused: Boolean, width: Double, height: Double): List<OverlayShape> {
        val shapes = mutableListOf<OverlayShape>()
        if (focused && width > FRAME_WIDTH * 2 && height > FRAME_WIDTH * 2) {
            val half = FRAME_WIDTH / 2
            shapes += OverlayShape.Rect(half, half, width - FRAME_WIDTH, height - FRAME_WIDTH, stroke = FRAME, strokeWidth = FRAME_WIDTH)
        }
        val items = if (focused) {
            ARROWS.map { Item.Key(it) } + listOf(Item.Text(MOVE_TEXT), Item.Key(SHIFT_KEY), Item.Text(FAST_TEXT))
        } else {
            listOf(Item.Text(HINT))
        }
        val textSize = assSize(TEXT_EM, bold = false)
        val keySize = assSize(KEY_EM, bold = true)
        val widths = items.map { item ->
            when (item) {
                is Item.Text -> ScoreboardFonts.textWidth(item.text, FONT, false, textSize)
                is Item.Key -> keyWidth(item.label, keySize)
            }
        }
        val chipWidth = PAD_LEFT + ICON + GAP * items.size + widths.sum() + PAD_RIGHT
        shapes += OverlayShape.Rect(
            LEFT, TOP, chipWidth, HEIGHT,
            fill = BACKGROUND, stroke = if (focused) BORDER_FOCUSED else BORDER, strokeWidth = 1.0, radius = HEIGHT / 2,
        )
        var x = LEFT + PAD_LEFT
        shapes += keyboardIcon(x, TOP + (HEIGHT - ICON) / 2, if (focused) UiKit.LIME else UiKit.FG_2)
        x += ICON + GAP
        val textColor = if (focused) UiKit.FG else UiKit.FG_2
        items.forEachIndexed { index, item ->
            when (item) {
                is Item.Text -> shapes += OverlayShape.Text(x, TOP + (HEIGHT - textSize) / 2, item.text, textSize, textColor, FONT)
                is Item.Key -> shapes += key(item.label, x, TOP + (HEIGHT - KEY_HEIGHT) / 2, widths[index], keySize)
            }
            x += widths[index] + GAP
        }
        return shapes
    }

    /** The libass size (the line box height) of a font with an em size of [em] pixels. */
    private fun assSize(em: Double, bold: Boolean): Double {
        val reference = 100.0
        val awtEm = ScoreboardFonts.awtFont(FONT, bold, reference).size2D.toDouble()
        return if (awtEm > 0.0) em * reference / awtEm else em * 1.33
    }

    private fun keyWidth(label: String, size: Double): Double =
        max(KEY_HEIGHT, ScoreboardFonts.textWidth(label, FONT, true, size) + 10.0)

    /** A key chip: a dark box with a lighter border that is thicker at the bottom, and the label in the middle. */
    private fun key(label: String, x: Double, y: Double, width: Double, size: Double): List<OverlayShape> {
        val labelWidth = ScoreboardFonts.textWidth(label, FONT, true, size)
        return listOf(
            OverlayShape.Rect(x, y, width, KEY_HEIGHT, fill = KEY_LINE, radius = 4.0),
            OverlayShape.Rect(x + 1, y + 1, width - 2, KEY_HEIGHT - 3, fill = KEY_BG, radius = 3.0),
            OverlayShape.Text(x + (width - labelWidth) / 2, y + (KEY_HEIGHT - 1 - size) / 2, label, size, UiKit.FG_2, FONT, bold = true),
        )
    }

    /** A small keyboard: the outline, two rows of keys and the space bar, in a box of [ICON] pixels. */
    private fun keyboardIcon(x: Double, y: Double, color: Color): List<OverlayShape> {
        val shapes = mutableListOf<OverlayShape>(
            OverlayShape.Rect(x + 1, y + 3, ICON - 2, 10.0, stroke = color, strokeWidth = 1.3, radius = 1.5),
        )
        for (row in 0..1) {
            for (column in 0..3) {
                shapes += OverlayShape.Rect(x + 3.6 + column * 2.6, y + 5.2 + row * 2.4, 1.4, 1.4, fill = color)
            }
        }
        shapes += OverlayShape.Rect(x + 5.0, y + 10.0, 6.0, 1.3, fill = color)
        return shapes
    }
}
