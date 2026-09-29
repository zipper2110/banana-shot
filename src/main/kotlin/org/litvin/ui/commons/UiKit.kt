package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.Toolkit
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.math.ceil
import kotlin.math.max

/**
 * Colors, fonts and small painted parts that the redesigned tabs share (Points, Scoring, Colors, Transform).
 * The values come from the shared.css files of the design/<tab>-redesign folders, which have the same base.
 */
internal object UiKit {
    val BG = Color(0x0E0E0E)
    val PANEL = Color(0x161616)
    val CARD = Color(0x1A1A1A)
    val RAISED = Color(0x202020)
    val RAISED_2 = Color(0x2A2A2A)
    val LINE = Color(0x262626)
    val LINE_2 = Color(0x363636)
    val FG = Color(0xE4E4E4)
    val FG_2 = Color(0xADAAAA)
    val FG_3 = Color(0x6A6A6A)
    val LIME = Color(0xA1FE00)
    val LIME_HOVER = Color(0xB4FF33)
    val LIME_TINT = Color(161, 254, 0, 18)
    val LIME_LINE = Color(161, 254, 0, 153)
    val SAGE = Color(0xA3C586)
    val ON_LIME = Color(0x142000)
    val YELLOW = Color(0xF2D64B)
    val YELLOW_TINT = Color(242, 214, 75, 20)
    val YELLOW_LINE = Color(242, 214, 75, 89)
    val RED = Color(0xFF7351)
    val RED_TINT = Color(255, 115, 81, 26)
    val RED_LINE = Color(255, 115, 81, 102)
    val ERROR = Color(0xFF6B6B)

    /** The border of a hovered button. */
    val HOVER_LINE = Color(0x444444)

    /** The line between two slider rows of a group card. */
    val CARD_ROW_LINE = Color(0x202020)

    /** The background of the playback bar under the video. */
    val PLAYBAR = Color(0x1C1C1C)

    /** The row lines and the hover color of the points table. */
    val ROW_LINE = Color(0x191919)
    val ROW_HOVER = Color(0x181818)
    val ROW_SELECTED = Color(0x232323)
    val COMMENT_ROW = Color(0x131313)
    val TABLE_HEAD = Color(0x121212)

    /** Timeline colors. */
    val MARK = Color(0x4CAF50)
    val VIDEO = Color(68, 136, 255, 140)
    val PLAYHEAD = Color(0xFF5555)

    enum class Weight { REGULAR, SEMIBOLD, BOLD }

    private val fonts = HashMap<Pair<Float, Weight>, Font>()

    private val semiboldFamily: String? by lazy {
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.firstOrNull { it == "Segoe UI Semibold" }
        } catch (_: Throwable) {
            null
        }
    }

    /** A font of the app family with a size in pixels. Semibold uses "Segoe UI Semibold" when the PC has it. */
    fun font(size: Float, weight: Weight = Weight.REGULAR): Font = fonts.getOrPut(size to weight) {
        val family = (UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 13)).family
        when (weight) {
            Weight.REGULAR -> Font(family, Font.PLAIN, 1).deriveFont(size)
            Weight.BOLD -> Font(family, Font.BOLD, 1).deriveFont(size)
            Weight.SEMIBOLD -> semiboldFamily?.let { Font(it, Font.PLAIN, 1).deriveFont(size) }
                ?: Font(family, Font.BOLD, 1).deriveFont(size)
        }
    }

    /** The font of a caption in capital letters with some space between the letters. */
    fun trackedFont(size: Float, tracking: Double, weight: Weight = Weight.REGULAR): Font =
        font(size, weight).deriveFont(mapOf(TextAttribute.TRACKING to tracking))

    fun icon(ikon: Ikon, size: Int, color: Color): Icon = FontIcon.of(ikon, size, color)

    /** The text measurement of the tab. It is the same as the painting: antialiased text with fractional metrics. */
    val frc = FontRenderContext(null, true, true)

    fun textWidth(text: String, font: Font): Float = font.getStringBounds(text, frc).width.toFloat()

    /** The baseline that centers one line of [font] in a box of [height] pixels that starts at [top]. */
    fun baseline(font: Font, top: Float, height: Float): Float {
        val metrics = font.getLineMetrics("Hg", frc)
        return top + (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent
    }

    /** A copy of [g] with antialiasing for shapes and the text settings of the desktop. */
    fun smooth(g: Graphics): Graphics2D = (g.create() as Graphics2D).apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") as? Map<*, *>
        if (hints != null) addRenderingHints(hints)
        else setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    /** Fills a rounded rectangle and draws a 1 px border inside it. A null color skips that part. */
    fun paintBox(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, radius: Int, fill: Color?, border: Color?) {
        val arc = radius * 2.0
        if (fill != null) {
            g2.color = fill
            g2.fill(RoundRectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), arc, arc))
        }
        if (border != null) {
            g2.color = border
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1.0, h - 1.0, arc - 1, arc - 1))
        }
    }

    /** Draws [text] so that its left edge is at [x] and the line is centered in the box [top]..[top]+[height]. */
    fun drawText(g2: Graphics2D, text: String, font: Font, color: Color, x: Float, top: Float, height: Float) {
        g2.font = font
        g2.color = color
        g2.drawString(text, x, baseline(font, top, height))
    }

    /** A slider value with its sign: "+24", "−24" (with a minus sign, not a hyphen) or "0". */
    fun signed(value: Int): String = when {
        value > 0 -> "+$value"
        value < 0 -> "−${-value}"
        else -> "0"
    }

    /** Shortens [text] with "…" until it fits in [maxWidth]. */
    fun ellipsize(text: String, font: Font, maxWidth: Float): String {
        if (textWidth(text, font) <= maxWidth) return text
        var count = text.length
        while (count > 0) {
            val shown = text.substring(0, count).trimEnd() + "…"
            if (textWidth(shown, font) <= maxWidth) return shown
            count--
        }
        return "…"
    }
}

/**
 * The look of a keyboard key chip, the `kbd` element of the design.
 * [fixedWidth] gives all chips of a group the same width, for example the arrow keys on the seek buttons.
 */
internal data class KeyChipStyle(
    val fill: Color = UiKit.PANEL,
    val border: Color = Color(0x454545),
    val text: Color = UiKit.FG_2,
    val fixedWidth: Int? = null,
    val height: Int = KeyChips.HEIGHT,
    val fontSize: Float = 10.5f,
) {
    companion object {
        val DEFAULT = KeyChipStyle()

        /** The chips on the seek buttons: a lighter border and white text. */
        val SEEK = KeyChipStyle(fill = Color(0x1B1B1B), border = Color(0x555555), text = UiKit.FG, fixedWidth = 18)

        /** The small chips of a compact button, for example Previous and Next of the Scoring tab. */
        val SMALL = KeyChipStyle(height = 16, fontSize = 10f)
    }
}

internal object KeyChips {
    const val HEIGHT = 18

    /** The chip font. Some fonts have no arrow or Shift glyphs; the logical "Dialog" font falls back to a font that has them. */
    private fun fontFor(key: String, size: Float): Font {
        val font = UiKit.font(size, UiKit.Weight.SEMIBOLD)
        return if (font.canDisplayUpTo(key) == -1) font else Font(Font.DIALOG, Font.BOLD, 1).deriveFont(size)
    }

    fun width(key: String, style: KeyChipStyle = KeyChipStyle.DEFAULT): Int =
        style.fixedWidth ?: max(style.height, ceil(UiKit.textWidth(key, fontFor(key, style.fontSize))).toInt() + 10)

    /** Paints one key chip with its top-left corner at [x], [y]. The bottom border is 2 px, like a key. */
    fun paint(g2: Graphics2D, key: String, x: Int, y: Int, style: KeyChipStyle = KeyChipStyle.DEFAULT) {
        val w = width(key, style)
        val h = style.height
        UiKit.paintBox(g2, x, y, w, h, 4, style.border, null)
        UiKit.paintBox(g2, x + 1, y + 1, w - 2, h - 3, 3, style.fill, null)
        g2.color = style.text
        val symbol = symbolFor(key, x + w / 2.0, y + (h - 1) / 2.0)
        if (symbol != null) {
            // The arrow glyphs of the UI font are too thin at this size, so the arrow keys are shapes.
            g2.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.draw(symbol)
            return
        }
        val font = fontFor(key, style.fontSize)
        g2.font = font
        val textX = x + (w - UiKit.textWidth(key, font)) / 2f
        g2.drawString(key, textX, UiKit.baseline(font, y.toFloat(), h - 1f))
    }

    /** A shape for the arrow keys and the Shift key, centered on [cx], [cy]. Null for a key that is text. */
    private fun symbolFor(key: String, cx: Double, cy: Double): Shape? = when (key) {
        "←", "→" -> Path2D.Double().apply {
            val dir = if (key == "←") -1.0 else 1.0
            moveTo(cx - 4.0 * dir, cy)
            lineTo(cx + 4.0 * dir, cy)
            moveTo(cx + 1.0 * dir, cy - 3.0)
            lineTo(cx + 4.0 * dir, cy)
            lineTo(cx + 1.0 * dir, cy + 3.0)
        }
        "↑", "↓" -> Path2D.Double().apply {
            val dir = if (key == "↑") -1.0 else 1.0
            moveTo(cx, cy - 4.0 * dir)
            lineTo(cx, cy + 4.0 * dir)
            moveTo(cx - 3.0, cy + 1.0 * dir)
            lineTo(cx, cy + 4.0 * dir)
            lineTo(cx + 3.0, cy + 1.0 * dir)
        }
        "⇧" -> Path2D.Double().apply {
            moveTo(cx, cy - 4.5)
            lineTo(cx + 4.5, cy)
            lineTo(cx + 2.0, cy)
            lineTo(cx + 2.0, cy + 4.0)
            lineTo(cx - 2.0, cy + 4.0)
            lineTo(cx - 2.0, cy)
            lineTo(cx - 4.5, cy)
            closePath()
        }
        else -> null
    }
}

/** One key chip as a component, for a line of text such as "Press [C] at the start of a point." */
internal class KeyChip(private val key: String, private val style: KeyChipStyle = KeyChipStyle.DEFAULT) : JComponent() {
    override fun getPreferredSize() = Dimension(KeyChips.width(key, style), style.height)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            KeyChips.paint(g2, key, 0, 0, style)
        } finally {
            g2.dispose()
        }
    }
}
