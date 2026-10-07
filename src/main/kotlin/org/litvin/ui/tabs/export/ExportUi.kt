package org.litvin.ui.tabs.export

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.swing.FontIcon
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.UIManager
import kotlin.math.ceil
import kotlin.math.max
import org.litvin.ui.commons.Palette

/**
 * Fonts and small painted parts of the Export tab. The colors are in [Palette].
 * The values come from design/export-redesign/final.html and its shared.css.
 */
internal object ExportUi {
    /** The opacity of a disabled tile or card. */
    const val DISABLED_ALPHA = 0.45f

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
    fun trackedFont(size: Float, tracking: Double): Font =
        font(size).deriveFont(mapOf(TextAttribute.TRACKING to tracking))

    fun icon(ikon: Ikon, size: Int, color: Color): Icon = FontIcon.of(ikon, size, color)

    /** The text measurement of the tab. It is the same as the painting: antialiased text with fractional metrics. */
    val frc = FontRenderContext(null, true, true)

    fun textWidth(text: String, font: Font): Float = font.getStringBounds(text, frc).width.toFloat()

    /** A copy of [g] with antialiasing for shapes and the text settings of the desktop. */
    fun smooth(g: Graphics): Graphics2D = (g.create() as Graphics2D).apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        applyTextHints(this)
    }

    fun applyTextHints(g2: Graphics2D) {
        val hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") as? Map<*, *>
        if (hints != null) g2.addRenderingHints(hints)
        else g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
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

    /** A radio dot: a grey ring, or a lime ring with a lime dot when [selected]. */
    fun paintRadio(g2: Graphics2D, x: Int, y: Int, size: Int, selected: Boolean) {
        val stroke = 1.5
        g2.stroke = BasicStroke(stroke.toFloat())
        g2.color = if (selected) Palette.LIME else Palette.LINE_5
        g2.draw(Ellipse2D.Double(x + stroke / 2, y + stroke / 2, size - stroke, size - stroke))
        if (selected) {
            val inset = stroke + 3
            g2.fill(Ellipse2D.Double(x + inset, y + inset, size - inset * 2, size - inset * 2))
        }
    }

    /** A 16 px check box: a grey outline, or a lime square with a dark check mark when [checked]. */
    fun paintCheck(g2: Graphics2D, x: Int, y: Int, checked: Boolean) {
        val size = 16.0
        if (checked) {
            g2.color = Palette.LIME_FILL
            g2.fill(RoundRectangle2D.Double(x.toDouble(), y.toDouble(), size, size, 6.0, 6.0))
            g2.color = Palette.ON_LIME
            g2.stroke = BasicStroke(2.2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
            g2.draw(Path2D.Double().apply {
                moveTo(x + 4.4, y + 8.0)
                lineTo(x + 7.0, y + 10.6)
                lineTo(x + 11.8, y + 5.4)
            })
        } else {
            g2.color = Palette.LINE_5
            g2.stroke = BasicStroke(1.5f)
            g2.draw(RoundRectangle2D.Double(x + 0.75, y + 0.75, size - 1.5, size - 1.5, 4.5, 4.5))
        }
    }

    /** Paints [paint] with the opacity of a disabled control when [enabled] is false. */
    inline fun paintFaded(g: Graphics, enabled: Boolean, alpha: Float = DISABLED_ALPHA, paint: (Graphics) -> Unit) {
        if (enabled) {
            paint(g)
            return
        }
        val g2 = g.create() as Graphics2D
        try {
            g2.composite = AlphaComposite.SrcOver.derive(alpha)
            paint(g2)
        } finally {
            g2.dispose()
        }
    }
}

/** A component whose height depends on the width that the layout gives it, for example wrapped text. */
internal interface HeightForWidth {
    fun heightForWidth(width: Int): Int
}

/** The height of this component at [width]. */
internal fun Component.heightAt(width: Int): Int =
    if (this is HeightForWidth) heightForWidth(width) else preferredSize.height

/** A piece of text with its own font and color. */
internal data class TextRun(val text: String, val font: Font, val color: Color)

/**
 * Read-only text that wraps at the width that the layout gives it, like a paragraph of the design.
 * A word that is wider than the line breaks at any character. "\n" starts a new line.
 * With [ellipsis], the text stays on one line and ends with "…" when it is too long.
 * The text has no mouse listeners, so a click on it goes to the button under it.
 */
internal class WrapText(
    runs: List<TextRun> = emptyList(),
    private val lineHeight: Float = 1.4f,
    var align: Align = Align.LEFT,
    private val ellipsis: Boolean = false,
) : JComponent(), HeightForWidth {
    enum class Align { LEFT, CENTER, RIGHT }

    constructor(text: String, font: Font, color: Color, lineHeight: Float = 1.4f, align: Align = Align.LEFT, ellipsis: Boolean = false) :
        this(listOf(TextRun(text, font, color)), lineHeight, align, ellipsis)

    var runs: List<TextRun> = runs
        set(value) {
            if (field == value) return
            field = value
            cache = null
            revalidate()
            repaint()
        }

    val text: String get() = runs.joinToString("") { it.text }

    private class Piece(val text: String, val font: Font, val color: Color, val width: Float, val space: Boolean)
    private class Line(val pieces: List<Piece>, val width: Float, val height: Float, val ascent: Float, val descent: Float)

    private var cache: Pair<Float, List<Line>>? = null

    init {
        isOpaque = false
    }

    fun setText(text: String, font: Font, color: Color) {
        runs = listOf(TextRun(text, font, color))
    }

    /** Changes only the colors, for example when a button becomes selected. */
    fun recolor(color: (TextRun) -> Color) {
        runs = runs.map { it.copy(color = color(it)) }
    }

    override fun heightForWidth(width: Int): Int = ceil(lines(width.toFloat()).sumOf { it.height.toDouble() }).toInt()

    override fun getPreferredSize(): Dimension {
        if (isPreferredSizeSet) return super.getPreferredSize()
        val natural = naturalWidth()
        return Dimension(natural, heightForWidth(if (width > 0) width else natural))
    }

    override fun getMinimumSize(): Dimension =
        if (isMinimumSizeSet) super.getMinimumSize() else Dimension(0, heightForWidth(if (width > 0) width else naturalWidth()))

    /** The width of the widest line without wrapping. One more pixel keeps the last character visible. */
    fun naturalWidth(): Int = ceil(lines(Float.MAX_VALUE).maxOfOrNull { it.width } ?: 0f).toInt() + 1

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            var y = 0f
            for (line in lines(width.toFloat())) {
                var x = when (align) {
                    Align.LEFT -> 0f
                    Align.CENTER -> (width - line.width) / 2f
                    Align.RIGHT -> width - line.width
                }
                val baseline = y + (line.height - line.ascent - line.descent) / 2f + line.ascent
                for (piece in line.pieces) {
                    if (!piece.space) {
                        g2.font = piece.font
                        g2.color = piece.color
                        g2.drawString(piece.text, x, baseline)
                    }
                    x += piece.width
                }
                y += line.height
            }
        } finally {
            g2.dispose()
        }
    }

    private fun lines(maxWidth: Float): List<Line> {
        cache?.let { (w, lines) -> if (w == maxWidth) return lines }
        val lines = if (ellipsis) listOf(ellipsisLine(maxWidth)) else wrap(maxWidth)
        cache = maxWidth to lines
        return lines
    }

    private fun wrap(maxWidth: Float): List<Line> {
        val lines = mutableListOf<Line>()
        val current = mutableListOf<Piece>()
        var x = 0f
        var lastFont = runs.firstOrNull()?.font ?: font ?: ExportUi.font(13f)

        fun flush() {
            while (current.lastOrNull()?.space == true) current.removeAt(current.lastIndex)
            lines += line(current.toList(), lastFont)
            current.clear()
            x = 0f
        }

        for (run in runs) {
            lastFont = run.font
            for (token in TOKEN.findAll(run.text).map { it.value }) {
                when {
                    token == "\n" -> flush()
                    token.isBlank() -> if (current.isNotEmpty()) {
                        val w = ExportUi.textWidth(token, run.font)
                        current += Piece(token, run.font, run.color, w, space = true)
                        x += w
                    }
                    else -> {
                        var rest = token
                        while (rest.isNotEmpty()) {
                            val w = ExportUi.textWidth(rest, run.font)
                            if (x + w <= maxWidth || current.none { !it.space } && w <= maxWidth) {
                                current += Piece(rest, run.font, run.color, w, space = false)
                                x += w
                                rest = ""
                            } else if (current.any { !it.space }) {
                                flush()
                            } else {
                                // The word is wider than the line. Put as many characters as fit, at least one.
                                var count = 1
                                while (count < rest.length && ExportUi.textWidth(rest.substring(0, count + 1), run.font) <= maxWidth) count++
                                val part = rest.substring(0, count)
                                current.clear()
                                current += Piece(part, run.font, run.color, ExportUi.textWidth(part, run.font), space = false)
                                flush()
                                rest = rest.substring(count)
                            }
                        }
                    }
                }
            }
        }
        if (current.isNotEmpty() || lines.isEmpty()) flush()
        return lines
    }

    private fun ellipsisLine(maxWidth: Float): Line {
        val run = runs.firstOrNull() ?: return line(emptyList(), font ?: ExportUi.font(13f))
        val full = text.replace('\n', ' ')
        val fullWidth = ExportUi.textWidth(full, run.font)
        if (fullWidth <= maxWidth) return line(listOf(Piece(full, run.font, run.color, fullWidth, space = false)), run.font)
        var count = full.length
        var shown = "…"
        while (count > 0) {
            shown = full.substring(0, count).trimEnd() + "…"
            if (ExportUi.textWidth(shown, run.font) <= maxWidth) break
            count--
        }
        return line(listOf(Piece(shown, run.font, run.color, ExportUi.textWidth(shown, run.font), space = false)), run.font)
    }

    private fun line(pieces: List<Piece>, fallbackFont: Font): Line {
        val fonts = pieces.map { it.font }.ifEmpty { listOf(fallbackFont) }
        val biggest = fonts.maxBy { it.size2D }
        val metrics = biggest.getLineMetrics("Hg", ExportUi.frc)
        val height = max(fonts.maxOf { it.size2D * lineHeight }, metrics.ascent + metrics.descent)
        return Line(pieces, pieces.sumOf { it.width.toDouble() }.toFloat(), height, metrics.ascent, metrics.descent)
    }

    private companion object {
        val TOKEN = Regex("\n|[ \t]+|[^ \t\n]+")
    }
}

/**
 * A vertical stack: each visible child gets the full width and the height for that width.
 * [gap] is the space between two visible children.
 */
internal open class Stack(private val gap: Int = 0) : JPanel(null), HeightForWidth {
    init {
        isOpaque = false
    }

    private val shown get() = components.filter { it.isVisible }

    override fun heightForWidth(width: Int): Int {
        val inner = width - insets.left - insets.right
        val children = shown
        return insets.top + insets.bottom + children.sumOf { it.heightAt(inner) } + gap * (children.size - 1).coerceAtLeast(0)
    }

    override fun getPreferredSize(): Dimension {
        if (isPreferredSizeSet) return super.getPreferredSize()
        val natural = (shown.maxOfOrNull { it.preferredSize.width } ?: 0) + insets.left + insets.right
        return Dimension(natural, heightForWidth(if (width > 0) width else natural))
    }

    override fun getMinimumSize(): Dimension = if (isMinimumSizeSet) super.getMinimumSize() else Dimension(0, preferredSize.height)

    override fun doLayout() {
        val inner = width - insets.left - insets.right
        var y = insets.top
        for (child in shown) {
            val h = child.heightAt(inner)
            child.setBounds(insets.left, y, inner, h)
            y += h + gap
        }
    }
}

/** A [Stack] in a scroll pane. It follows the width of the scroll pane, so only a vertical scroll bar can show. */
internal open class ScrollableStack(gap: Int = 0) : Stack(gap), Scrollable {
    override fun getPreferredSize(): Dimension {
        val viewportWidth = (parent as? JViewport)?.width?.takeIf { it > 0 } ?: width.takeIf { it > 0 } ?: super.getPreferredSize().width
        return Dimension(viewportWidth, heightForWidth(viewportWidth))
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}

/**
 * Rows of equal columns, like a CSS grid with "repeat(n, 1fr)". All cells of a row get the height of the tallest cell.
 */
internal open class GridRows(private val columns: Int, private val hgap: Int, private val vgap: Int) : JPanel(null), HeightForWidth {
    init {
        isOpaque = false
    }

    private fun cellWidth(width: Int) = ((width - insets.left - insets.right - hgap * (columns - 1)) / columns).coerceAtLeast(0)

    private fun rows(): List<List<Component>> = components.filter { it.isVisible }.chunked(columns)

    override fun heightForWidth(width: Int): Int {
        val cell = cellWidth(width)
        val rows = rows()
        return insets.top + insets.bottom + rows.sumOf { row -> row.maxOf { it.heightAt(cell) } } + vgap * (rows.size - 1).coerceAtLeast(0)
    }

    override fun getPreferredSize(): Dimension {
        if (isPreferredSizeSet) return super.getPreferredSize()
        val natural = (components.maxOfOrNull { it.preferredSize.width } ?: 0) * columns + hgap * (columns - 1) + insets.left + insets.right
        return Dimension(natural, heightForWidth(if (width > 0) width else natural))
    }

    override fun getMinimumSize(): Dimension = if (isMinimumSizeSet) super.getMinimumSize() else Dimension(0, preferredSize.height)

    override fun doLayout() {
        val cell = cellWidth(width)
        var y = insets.top
        for (row in rows()) {
            val h = row.maxOf { it.heightAt(cell) }
            row.forEachIndexed { index, child ->
                // The last column takes the pixels that the division leaves.
                val x = insets.left + index * (cell + hgap)
                val w = if (index == columns - 1) width - insets.right - x else cell
                child.setBounds(x, y, w, h)
            }
            y += h + vgap
        }
    }
}

/** A fixed empty space of [height] pixels in a [Stack]. */
internal class VGap(private val height: Int) : JComponent() {
    override fun getPreferredSize() = Dimension(0, height)
}

/** Small text in a rectangle with slightly round corners, for example "original". A muted tag is grey. */
internal class Tag(text: String, muted: Boolean = false) : JComponent() {
    var text: String = text
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }
    var muted: Boolean = muted
        set(value) {
            field = value
            repaint()
        }

    private val tagFont get() = ExportUi.font(10.5f, if (muted) ExportUi.Weight.REGULAR else ExportUi.Weight.SEMIBOLD)

    override fun getPreferredSize() = Dimension(ceil(ExportUi.textWidth(text, tagFont)).toInt() + 14, 17)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            ExportUi.paintBox(g2, 0, 0, width, height, 3, null, if (muted) Palette.LINE_2 else Palette.LIME_LINE)
            g2.font = tagFont
            g2.color = if (muted) Palette.FG_2 else Palette.LIME
            val metrics = tagFont.getLineMetrics(text, ExportUi.frc)
            g2.drawString(text, 7f, (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent)
        } finally {
            g2.dispose()
        }
    }
}
