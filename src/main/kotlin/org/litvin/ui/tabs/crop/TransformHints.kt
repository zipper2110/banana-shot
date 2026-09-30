package org.litvin.ui.tabs.crop

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import java.awt.Color
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A part of a hint text: plain or bold text, or a keyboard key chip. */
internal sealed interface HintPart {
    data class Text(val text: String, val bold: Boolean = false) : HintPart
    data class Key(val label: String) : HintPart
}

/** One mouse or key hint: an icon and a text with key chips. */
internal data class TransformHint(val ikon: Ikon, val parts: List<HintPart>) {
    /** The hint as plain text, for the tooltip and the accessible description. */
    val plainText: String
        get() = parts.joinToString("") {
            when (it) {
                is HintPart.Text -> it.text
                is HintPart.Key -> it.label
            }
        }

    companion object {
        private fun b(text: String) = HintPart.Text(text, bold = true)
        private fun t(text: String) = HintPart.Text(text)
        private fun k(label: String) = HintPart.Key(label)

        /** The mouse and key hints. The texts come from the Transform help (HelpCatalog.kt). */
        val ALL = listOf(
            TransformHint(Material2OutlinedMZ.OPEN_WITH, listOf(b("Drag inside the rectangle"), t(" to move it."))),
            TransformHint(
                Material2OutlinedAL.ASPECT_RATIO,
                listOf(b("Drag a square handle"), t(" to resize. The aspect ratio and the center stay.")),
            ),
            TransformHint(
                Material2OutlinedMZ.ROTATE_RIGHT,
                listOf(b("Drag the round handle"), t(" to rotate. Hold "), k("Shift"), t(" to snap to 90°.")),
            ),
            TransformHint(
                Material2OutlinedAL.KEYBOARD,
                listOf(
                    b("Click the video, then"), t(" "), k("←"), k("↑"), k("→"), k("↓"),
                    t(" move 1 px. "), k("Shift"), t(" moves 10 px."),
                ),
            ),
        )
    }
}

/**
 * The card with the mouse and key hints under the group cards. It has a dashed border and the caption
 * "ON THE VIDEO". The hint texts wrap at the width of the card.
 */
internal class TransformHints(private val hints: List<TransformHint> = TransformHint.ALL) : JPanel(null), HeightForWidth {
    private val captionIcon = UiKit.icon(Material2OutlinedMZ.MOUSE, 14, Palette.FG_3)
    private val captionFont = UiKit.trackedFont(10.5f, 0.1, UiKit.Weight.BOLD)
    private val textFont = UiKit.font(12f)
    private val boldFont = UiKit.font(12f, UiKit.Weight.SEMIBOLD)
    private val icons = hints.map { UiKit.icon(it.ikon, ICON_SIZE, Palette.FG_3) }

    init {
        name = "crop-hints"
        isOpaque = false
        getAccessibleContext().accessibleName = CAPTION
        getAccessibleContext().accessibleDescription = hints.joinToString(" ") { it.plainText }
    }

    /** A word or a key chip at a position in the text column. */
    private data class Placed(val part: HintPart, val text: String, val x: Float, val line: Int, val width: Float)

    private fun fontOf(part: HintPart.Text): Font = if (part.bold) boldFont else textFont

    /** Breaks the parts of one hint into lines that fit in [maxWidth]. Words and key chips do not break. */
    private fun layout(hint: TransformHint, maxWidth: Float): List<Placed> {
        val placed = mutableListOf<Placed>()
        var x = 0f
        var line = 0
        fun place(part: HintPart, text: String, width: Float, space: Float) {
            if (x > 0f && x + width > maxWidth) {
                line++
                x = 0f
            }
            placed += Placed(part, text, x, line, width)
            x += width + space
        }
        for (part in hint.parts) {
            when (part) {
                is HintPart.Key -> place(part, part.label, KeyChips.width(part.label, KEY_STYLE).toFloat(), KEY_MARGIN * 2)
                is HintPart.Text -> {
                    val font = fontOf(part)
                    val space = UiKit.textWidth(" ", font)
                    if (part.text.startsWith(" ") && x > 0f) x += space
                    val words = part.text.split(' ').filter { it.isNotEmpty() }
                    words.forEachIndexed { index, word ->
                        val last = index == words.lastIndex
                        val gap = if (!last || part.text.endsWith(" ")) space else 0f
                        place(part, word, UiKit.textWidth(word, font), gap)
                    }
                }
            }
        }
        return placed
    }

    private fun textWidth(width: Int): Float = (width - PAD_X * 2 - ICON_COLUMN - ICON_GAP).toFloat()

    private fun lineCount(hint: TransformHint, width: Int): Int =
        (layout(hint, textWidth(width)).maxOfOrNull { it.line } ?: 0) + 1

    override fun heightForWidth(width: Int): Int {
        val lines = hints.sumOf { lineCount(it, width) }
        return PAD_TOP + CAPTION_LINE + CAPTION_GAP_BOTTOM + ceil(lines * LINE).toInt() +
            ITEM_GAP * (hints.size - 1).coerceAtLeast(0) + PAD_BOTTOM
    }

    override fun getPreferredSize() = Dimension(MIN_WIDTH, heightForWidth(if (width > 0) width else DEFAULT_WIDTH))

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            paintDashedBorder(g2)
            captionIcon.paintIcon(this, g2, PAD_X, PAD_TOP + (CAPTION_LINE - captionIcon.iconHeight) / 2)
            val captionX = (PAD_X + captionIcon.iconWidth + CAPTION_ICON_GAP).toFloat()
            UiKit.drawText(g2, CAPTION.uppercase(), captionFont, Palette.FG_3, captionX, PAD_TOP.toFloat(), CAPTION_LINE.toFloat())

            var top = (PAD_TOP + CAPTION_LINE + CAPTION_GAP_BOTTOM).toFloat()
            val textX = (PAD_X + ICON_COLUMN + ICON_GAP).toFloat()
            hints.forEachIndexed { index, hint ->
                val icon = icons[index]
                icon.paintIcon(this, g2, PAD_X + (ICON_COLUMN - icon.iconWidth) / 2, (top + (LINE - icon.iconHeight) / 2f - 1f).toInt())
                val placed = layout(hint, textWidth(width))
                placed.forEach { item ->
                    val lineTop = top + item.line * LINE
                    when (val part = item.part) {
                        is HintPart.Key -> KeyChips.paint(
                            g2, part.label, (textX + item.x + KEY_MARGIN).roundToInt(),
                            (lineTop + (LINE - KEY_STYLE.height) / 2f).roundToInt(), KEY_STYLE,
                        )
                        is HintPart.Text -> UiKit.drawText(
                            g2, item.text, fontOf(part), if (part.bold) Palette.FG else Palette.FG_2,
                            textX + item.x, lineTop, LINE,
                        )
                    }
                }
                val lines = (placed.maxOfOrNull { it.line } ?: 0) + 1
                top += lines * LINE + ITEM_GAP
            }
        } finally {
            g2.dispose()
        }
    }

    private fun paintDashedBorder(g2: Graphics2D) {
        g2.color = HINT_LINE
        g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(3f, 3f), 0f)
        g2.draw(RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, 15.0, 15.0))
    }

    companion object {
        const val CAPTION = "On the video"
        private const val MIN_WIDTH = 200
        private const val DEFAULT_WIDTH = 376
        private const val PAD_X = 14
        private const val PAD_TOP = 10
        private const val PAD_BOTTOM = 12
        private const val CAPTION_LINE = 15
        private const val CAPTION_ICON_GAP = 6
        private const val CAPTION_GAP_BOTTOM = 8
        private const val ICON_SIZE = 17
        private const val ICON_COLUMN = 20
        private const val ICON_GAP = 8
        private const val ITEM_GAP = 7
        private const val LINE = 16.8f
        private val KEY_STYLE = KeyChipStyle.SMALL
        private val HINT_LINE = Palette.TRACK
        private const val KEY_MARGIN = 1f
    }
}
