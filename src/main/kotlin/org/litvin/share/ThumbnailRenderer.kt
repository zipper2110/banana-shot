package org.litvin.share

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** The texts, the style, the text position, and the colors of a thumbnail. Empty texts do not show. */
data class ThumbnailScene(
    val style: ThumbnailStyle,
    val place: TextPlace,
    val texts: Map<ThumbnailField, String>,
    val colors: ThumbnailColors,
) {
    fun text(field: ThumbnailField): String = texts[field]?.trim().orEmpty()
}

/**
 * The visible part of the frame. [zoom] is 1.0 to 2.0. [panX] and [panY] are 0.0 to 1.0:
 * the point (panX, panY) of the zoomed frame stays at the same point of the thumbnail.
 */
data class FrameView(val zoom: Double = 1.0, val panX: Double = 0.5, val panY: Double = 0.5)

/**
 * The layout of the texts and the shapes of a thumbnail, in the 1280 × 720 coordinates of the PNG.
 * [shrunk]: the app made a long text smaller, so that it fits. [small]: a text is smaller than [ThumbnailRenderer.SMALL_TEXT].
 */
class ThumbnailLayout internal constructor(
    internal val items: List<ThumbnailRenderer.Item>,
    val shrunk: Boolean,
    val small: Boolean,
) {
    /** The bounds of the texts, for the tests. */
    val textBounds: List<Rectangle2D> get() = items.filterIsInstance<ThumbnailRenderer.Item.Text>().map { it.bounds }

    /** The texts and their font sizes, for the tests. */
    val texts: List<Pair<String, Float>> get() = items.filterIsInstance<ThumbnailRenderer.Item.Text>().map { it.text to it.font.size2D }
}

/**
 * Draws the YouTube thumbnail of the Share tab (B-46) with Java2D. The layout styles come from
 * design/share-redesign/final.html (layouts.js and shared.css). All sizes are in the 1280 × 720 coordinates of the PNG.
 */
object ThumbnailRenderer {
    const val WIDTH = 1280
    const val HEIGHT = 720

    /** A text smaller than this (in PNG pixels) is less than 9 px high at the width of YouTube search (about 360 px). */
    const val SMALL_TEXT = 32f

    private const val PLACEHOLDER = 0x151A21
    private const val SHADOW_INK = 0x000000

    private val frc = FontRenderContext(null, true, true)
    private const val SEGOE = "Segoe UI"
    private const val SEGOE_BLACK = "Segoe UI Black"
    private const val SEGOE_SEMIBOLD = "Segoe UI Semibold"

    /** The weights of the texts. Each weight has its own font family on Windows. */
    internal enum class Weight { SEMIBOLD, BOLD, BLACK }

    internal sealed class Item {
        class Fill(val shape: Shape, val paint: Paint) : Item()

        /** A soft shadow under [shape], as the CSS box-shadow. */
        class BoxShadow(val shape: RoundRectangle2D, val offsetY: Double, val blur: Double, val alpha: Double) : Item()

        class Text(
            val text: String,
            val font: Font,
            val color: Color,
            val x: Double,
            val baseline: Double,
            val shadow: TextShadow?,
        ) : Item() {
            val bounds: Rectangle2D
                get() {
                    val metrics = font.getLineMetrics(text, frc)
                    return Rectangle2D.Double(x, baseline - metrics.ascent, width(text, font), (metrics.ascent + metrics.descent).toDouble())
                }
        }
    }

    /** A soft text shadow, as the CSS text-shadow. */
    internal data class TextShadow(val rgb: Int, val alpha: Double, val offsetY: Double, val blur: Double)

    // ---------- Fonts and text sizes ----------

    private val families: Set<String> by lazy {
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun baseFont(weight: Weight, size: Double): Font {
        val font = when (weight) {
            Weight.BLACK -> if (SEGOE_BLACK in families) Font(SEGOE_BLACK, Font.PLAIN, 1) else Font(fallback(), Font.BOLD, 1)
            Weight.BOLD -> Font(if (SEGOE in families) SEGOE else fallback(), Font.BOLD, 1)
            Weight.SEMIBOLD -> if (SEGOE_SEMIBOLD in families) Font(SEGOE_SEMIBOLD, Font.PLAIN, 1) else Font(fallback(), Font.BOLD, 1)
        }
        return font.deriveFont(size.toFloat())
    }

    private fun fallback(): String = if (SEGOE in families) SEGOE else Font.SANS_SERIF

    /** The font at [size] px with [spacing] px between the letters (the CSS letter-spacing). */
    internal fun font(weight: Weight, size: Double, spacing: Double = 0.0): Font {
        val font = baseFont(weight, size)
        if (spacing == 0.0) return font
        return font.deriveFont(mapOf(TextAttribute.TRACKING to (spacing / size).toFloat()))
    }

    internal fun width(text: String, font: Font): Double = if (text.isEmpty()) 0.0 else font.getStringBounds(text, frc).width

    private fun ascent(font: Font): Double = font.getLineMetrics("Hg", frc).ascent.toDouble()
    private fun descent(font: Font): Double = font.getLineMetrics("Hg", frc).descent.toDouble()

    /** The CSS line height "normal": ascent, descent, and leading of the font. */
    private fun normalLine(font: Font): Double = font.getLineMetrics("Hg", frc).let { (it.ascent + it.descent + it.leading).toDouble() }

    /** The baseline of a line box of [lineHeight] at [top]. The text is in the middle of the line box, as in CSS. */
    private fun baseline(font: Font, top: Double, lineHeight: Double): Double =
        top + (lineHeight - ascent(font) - descent(font)) / 2 + ascent(font)

    /** The large words in one or two rows, with the split that gives the narrowest block. */
    fun rows(text: String): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return listOf(words.joinToString(" ")).filter { it.isNotEmpty() }
        val measure = font(Weight.BLACK, 100.0)
        return (1 until words.size)
            .map { i -> listOf(words.take(i).joinToString(" "), words.drop(i).joinToString(" ")) }
            .minBy { split -> split.maxOf { width(it, measure) } }
    }

    // ---------- Layout ----------

    /** Collects the items of a layout, and makes each long text smaller until it fits. */
    private class Builder(val colors: ThumbnailColors) {
        val items = mutableListOf<Item>()
        var shrunk = false
        var small = false

        /** Makes [font] smaller until [text] is not wider than [max]. [extra] is the width of the parts that do not shrink. */
        fun fit(text: String, font: Font, max: Double, extra: (Font) -> Double = { 0.0 }): Font {
            var fitted = font
            var tries = 0
            while (width(text, fitted) + extra(fitted) > max && tries < 40) {
                fitted = fitted.deriveFont(fitted.size2D * 0.95f)
                tries++
                shrunk = true
            }
            return fitted
        }

        fun text(text: String, font: Font, rgb: Int, x: Double, baseline: Double, shadow: TextShadow? = null, alpha: Double = 1.0) {
            if (text.isEmpty()) return
            if (font.size2D < SMALL_TEXT) small = true
            items += Item.Text(text, font, color(rgb, alpha), x, baseline, shadow)
        }

        fun fill(shape: Shape, paint: Paint) {
            items += Item.Fill(shape, paint)
        }

        fun shade(alpha: Double): Color = color(colors.shade, alpha * colors.strength)

        fun textShadow(alpha: Double, offsetY: Double, blur: Double): TextShadow? =
            (alpha * colors.strength).takeIf { it > 0.0 }?.let { TextShadow(colors.shade, it.coerceAtMost(1.0), offsetY, blur) }
    }

    fun layout(scene: ThumbnailScene): ThumbnailLayout {
        val builder = Builder(scene.colors)
        when (scene.style) {
            ThumbnailStyle.WORDS -> words(builder, scene)
            ThumbnailStyle.VERSUS -> versus(builder, scene)
            ThumbnailStyle.CARD -> card(builder, scene)
            ThumbnailStyle.SPLIT -> split(builder, scene)
            ThumbnailStyle.BROADCAST -> broadcast(builder, scene)
            ThumbnailStyle.POSTER -> poster(builder, scene)
        }
        return ThumbnailLayout(builder.items.toList(), builder.shrunk, builder.small)
    }

    private fun upper(text: String) = text.uppercase(Locale.ROOT)
    private fun fullArea() = Rectangle2D.Double(0.0, 0.0, WIDTH.toDouble(), HEIGHT.toDouble())

    /** 1. Big words: the match type and the large words at the right (or the left). Roles: Text, Accent, Shade. */
    private fun words(b: Builder, scene: ThumbnailScene) {
        val left = scene.place == TextPlace.LEFT
        val cx = if (left) 320.0 else 960.0
        b.fill(fullArea(), ellipseGradient(cx, 400.0, 720.0, 420.0, b.shade(0.62), b.shade(0.0)))
        val shadow = b.textShadow(0.55, 4.0, 18.0)
        var top = 290.0
        fun row(text: String, font: Font, rgb: Int) {
            val fitted = b.fit(text, font, 620.0)
            val line = 0.95 * fitted.size2D
            val x = if (left) 56.0 else WIDTH - 56.0 - width(text, fitted)
            b.text(text, fitted, rgb, x, baseline(fitted, top, line), shadow)
            top += line
        }
        upper(scene.text(ThumbnailField.KIND)).takeIf { it.isNotEmpty() }?.let { row(it, font(Weight.BLACK, 64.0, 1.0), b.colors.text) }
        rows(upper(scene.text(ThumbnailField.HEAD))).forEach { row(it, font(Weight.BLACK, 128.0, -1.0), b.colors.accent) }
    }

    /** 2. Versus: the two teams at the side, a tag with the large words. Roles: Text, Accent, Text on accent, Shade. */
    private fun versus(b: Builder, scene: ThumbnailScene) {
        val right = scene.place == TextPlace.RIGHT
        val stops = floatArrayOf(0f, 0.42f, 0.68f, 1f)
        val colors = arrayOf(b.shade(0.86), b.shade(0.55), b.shade(0.0), b.shade(0.0))
        val (from, to) = if (right) WIDTH.toDouble() to 0.0 else 0.0 to WIDTH.toDouble()
        b.fill(fullArea(), LinearGradientPaint(Point2D.Double(from, 0.0), Point2D.Double(to, 0.0), stops, colors))
        fun x(width: Double) = if (right) WIDTH - 56.0 - width else 56.0

        val head = upper(scene.text(ThumbnailField.HEAD))
        if (head.isNotEmpty()) {
            val font = b.fit(head, font(Weight.BLACK, 42.0, 0.5), 640.0 - 44.0)
            val w = width(head, font) + 44.0
            val h = normalLine(font) + 16.0
            val box = RoundRectangle2D.Double(x(w), 200.0, w, h, 12.0, 12.0)
            b.fill(box, color(b.colors.accent))
            b.text(head, font, b.colors.onAccent, box.x + 22.0, baseline(font, 208.0, normalLine(font)))
        }
        val shadow = b.textShadow(0.5, 4.0, 18.0)
        val team1 = upper(scene.text(ThumbnailField.TEAM1))
        val team2 = upper(scene.text(ThumbnailField.TEAM2))
        var top = 300.0
        fun team(text: String) {
            val font = b.fit(text, font(Weight.BLACK, 76.0), 640.0)
            b.text(text, font, b.colors.text, x(width(text, font)), baseline(font, top, font.size2D.toDouble()), shadow)
            top += font.size2D
        }
        if (team1.isNotEmpty()) team(team1)
        if (team1.isNotEmpty() && team2.isNotEmpty()) {
            val font = font(Weight.BLACK, 52.0)
            top += 10.0
            b.text("VS", font, b.colors.accent, x(width("VS", font)), baseline(font, top, 52.0), shadow)
            top += 52.0 + 12.0
        }
        if (team2.isNotEmpty()) team(team2)
    }

    /** 3. Match card: a card at the top left (or right). Roles: Accent, Panel, Text on panel. */
    private fun card(b: Builder, scene: ThumbnailScene) {
        val right = scene.place == TextPlace.RIGHT
        val cardX = if (right) WIDTH - 48.0 - 580.0 else 48.0
        val textX = if (right) cardX + 36.0 else cardX + 10.0 + 36.0
        val max = 508.0
        val texts = mutableListOf<() -> Unit>()
        var top = 48.0 + 32.0
        val start = top

        val event = upper(scene.text(ThumbnailField.EVENT))
        val meta = listOf(scene.text(ThumbnailField.COURT), scene.text(ThumbnailField.DATE)).filter { it.isNotEmpty() }.joinToString(" · ")
        if (event.isNotEmpty()) {
            val font = b.fit(event, font(Weight.BOLD, 30.0, 2.0), max)
            val y = top
            texts += { b.text(event, font, b.colors.accent, textX, baseline(font, y, normalLine(font))) }
            top += normalLine(font)
        }
        if (meta.isNotEmpty()) {
            val font = b.fit(meta, font(Weight.SEMIBOLD, 26.0), max)
            val y = top + 6.0
            texts += { b.text(meta, font, b.colors.onPanel, textX, baseline(font, y, normalLine(font)), alpha = 0.72) }
            top += 6.0 + normalLine(font)
        }
        if (event.isNotEmpty() || meta.isNotEmpty()) {
            val y = top + 22.0
            texts += { b.fill(Rectangle2D.Double(textX, y, 580.0 - 82.0, 2.0), color(b.colors.onPanel, 0.14)) }
            top += 22.0 + 2.0 + 22.0
        }
        val team1 = scene.text(ThumbnailField.TEAM1)
        val team2 = scene.text(ThumbnailField.TEAM2)
        fun team(text: String) {
            val font = b.fit(text, font(Weight.BLACK, 50.0), max)
            val y = top
            val line = 1.05 * font.size2D
            texts += { b.text(text, font, b.colors.onPanel, textX, baseline(font, y, line)) }
            top += line
        }
        if (team1.isNotEmpty()) team(team1)
        if (team1.isNotEmpty() && team2.isNotEmpty()) {
            val font = font(Weight.BOLD, 26.0)
            val y = top + 4.0
            texts += { b.text("vs", font, b.colors.onPanel, textX, baseline(font, y, normalLine(font)), alpha = 0.5) }
            top += 4.0 + normalLine(font) + 4.0
        }
        if (team2.isNotEmpty()) team(team2)
        val head = upper(scene.text(ThumbnailField.HEAD))
        if (head.isNotEmpty()) {
            val font = b.fit(head, font(Weight.BLACK, 46.0), max)
            val y = top + 22.0
            texts += { b.text(head, font, b.colors.accent, textX, baseline(font, y, normalLine(font))) }
            top += 22.0 + normalLine(font)
        }
        if (top == start) return

        val card = RoundRectangle2D.Double(cardX, 48.0, 580.0, top - 48.0 + 34.0, 36.0, 36.0)
        b.items += Item.BoxShadow(card, 12.0, 40.0, 0.45)
        b.fill(card, color(b.colors.panel, 0.92))
        val edgeX = if (right) cardX + 580.0 - 10.0 else cardX
        val edge = Area(card).apply { intersect(Area(Rectangle2D.Double(edgeX, card.y, 10.0, card.height))) }
        b.fill(edge, color(b.colors.accent))
        texts.forEach { it() }
    }

    /** 4. Split: an accent panel at the left (or right). Roles: Accent, Text on accent. */
    private fun split(b: Builder, scene: ThumbnailScene) {
        val right = scene.place == TextPlace.RIGHT
        val panel = Path2D.Double().apply {
            if (right) {
                moveTo(700.0, 0.0); lineTo(1280.0, 0.0); lineTo(1280.0, 720.0); lineTo(816.0, 720.0)
            } else {
                moveTo(0.0, 0.0); lineTo(580.0, 0.0); lineTo(464.0, 720.0); lineTo(0.0, 720.0)
            }
            closePath()
        }
        b.fill(panel, color(b.colors.accent))
        // At the right, each row ends 56 px from the right edge, the same margin as at the left.
        fun x(text: String, font: Font) = if (right) WIDTH - 56.0 - width(text, font) else 56.0
        var top = 150.0
        val kind = upper(scene.text(ThumbnailField.KIND))
        if (kind.isNotEmpty()) {
            val font = b.fit(kind, font(Weight.BLACK, 46.0, 1.0), 440.0)
            b.text(kind, font, b.colors.onAccent, x(kind, font), baseline(font, top, normalLine(font)))
            top += normalLine(font)
        }
        rows(upper(scene.text(ThumbnailField.HEAD))).forEach { row ->
            val font = b.fit(row, font(Weight.BLACK, 106.0, -2.0), 460.0)
            val line = 0.9 * font.size2D
            b.text(row, font, b.colors.onAccent, x(row, font), baseline(font, top, line))
            top += line
        }
        val event = upper(scene.text(ThumbnailField.EVENT))
        if (event.isNotEmpty()) {
            val font = b.fit(event, font(Weight.BOLD, 27.0, 2.0), 410.0)
            val eventTop = if (right) 560.0 else 600.0
            b.text(event, font, b.colors.onAccent, x(event, font), baseline(font, eventTop, normalLine(font)))
        }
    }

    /** 5. Broadcast: a TV lower third. Roles: Accent, Text on accent, Panel, Text on panel, Shade. */
    private fun broadcast(b: Builder, scene: ThumbnailScene) {
        val top = scene.place == TextPlace.TOP
        val (from, to) = if (top) 0.0 to HEIGHT * 0.45 else HEIGHT.toDouble() to HEIGHT * 0.55
        b.fill(fullArea(), LinearGradientPaint(Point2D.Double(0.0, from), Point2D.Double(0.0, to), floatArrayOf(0f, 1f), arrayOf(b.shade(0.55), b.shade(0.0))))

        /** A box of the stack: its height, and a function that draws it at a top. */
        class Box(val height: Double, val draw: (Double) -> Unit)
        val boxes = mutableListOf<Box>()
        val x = 56.0

        val head = upper(scene.text(ThumbnailField.HEAD))
        if (head.isNotEmpty()) {
            val font = b.fit(head, font(Weight.BLACK, 34.0, 1.0), 800.0 - 36.0)
            val h = normalLine(font) + 12.0
            boxes += Box(h) { y ->
                b.fill(Rectangle2D.Double(x, y, width(head, font) + 36.0, h), color(b.colors.accent))
                b.text(head, font, b.colors.onAccent, x + 18.0, baseline(font, y + 6.0, normalLine(font)))
            }
        }
        val teams = listOf(scene.text(ThumbnailField.TEAM1), scene.text(ThumbnailField.TEAM2)).filter { it.isNotEmpty() }.map(::upper)
        val vsFont = font(Weight.BOLD, 34.0)
        val vsWidth = width("vs", vsFont)
        fun bar(parts: List<String>, leadingVs: Boolean, padTop: Double) {
            // The "vs" between two teams has 14 px at each side. A "vs" at the start of the second row has no space before it.
            val gaps = (if (leadingVs) vsWidth + 14.0 else 0.0) + (parts.size - 1) * (vsWidth + 28.0)
            val font = b.fit(parts.joinToString(""), font(Weight.BOLD, 46.0), 900.0 - 48.0) { gaps }
            val line = normalLine(font)
            val h = padTop + line + 12.0
            boxes += Box(h) { y ->
                val w = parts.sumOf { width(it, font) } + gaps + 48.0
                b.fill(Rectangle2D.Double(x, y, w, h), color(b.colors.panel))
                val base = baseline(font, y + padTop, line)
                var tx = x + 24.0
                if (leadingVs) {
                    b.text("vs", vsFont, b.colors.accent, tx, base)
                    tx += vsWidth + 14.0
                }
                parts.forEachIndexed { index, part ->
                    if (index > 0) {
                        b.text("vs", vsFont, b.colors.accent, tx + 14.0, base)
                        tx += vsWidth + 28.0
                    }
                    b.text(part, font, b.colors.onPanel, tx, base)
                    tx += width(part, font)
                }
            }
        }
        // Long names go in two rows, so that they stay large.
        if (teams.joinToString("").length > 30 && teams.size == 2) {
            bar(listOf(teams[0]), leadingVs = false, padTop = 12.0)
            bar(listOf(teams[1]), leadingVs = true, padTop = 0.0)
        } else if (teams.isNotEmpty()) {
            bar(teams, leadingVs = false, padTop = 12.0)
        }
        val info = listOf(scene.text(ThumbnailField.EVENT), scene.text(ThumbnailField.COURT)).filter { it.isNotEmpty() }.joinToString(" · ").let(::upper)
        if (info.isNotEmpty()) {
            val font = b.fit(info, font(Weight.BOLD, 28.0, 1.0), 900.0 - 48.0)
            val h = normalLine(font) + 16.0
            boxes += Box(h) { y ->
                b.fill(Rectangle2D.Double(x, y, width(info, font) + 48.0, h), color(b.colors.onPanel))
                b.text(info, font, b.colors.panel, x + 24.0, baseline(font, y + 8.0, normalLine(font)))
            }
        }
        var y = if (top) 56.0 else HEIGHT - 96.0 - boxes.sumOf { it.height }
        boxes.forEach { box ->
            box.draw(y)
            y += box.height
        }
    }

    /** 6. Poster: one row of large words at the top, the teams at the bottom left. Roles: Text, Accent, Shade. */
    private fun poster(b: Builder, scene: ThumbnailScene) {
        b.fill(fullArea(), LinearGradientPaint(Point2D.Double(0.0, 0.0), Point2D.Double(0.0, HEIGHT * 0.48), floatArrayOf(0f, 1f), arrayOf(b.shade(0.72), b.shade(0.0))))
        b.fill(fullArea(), LinearGradientPaint(Point2D.Double(0.0, HEIGHT.toDouble()), Point2D.Double(0.0, HEIGHT * 0.7), floatArrayOf(0f, 1f), arrayOf(b.shade(0.6), b.shade(0.0))))
        var top = 34.0
        val head = upper(scene.text(ThumbnailField.HEAD))
        if (head.isNotEmpty()) {
            val font = b.fit(head, font(Weight.BLACK, 150.0, -3.0), 1180.0)
            b.text(head, font, b.colors.text, (WIDTH - width(head, font)) / 2, baseline(font, top, font.size2D.toDouble()), b.textShadow(0.5, 6.0, 24.0))
            top += font.size2D
        }
        val line = listOf(scene.text(ThumbnailField.KIND), scene.text(ThumbnailField.DATE)).filter { it.isNotEmpty() }.joinToString(" · ").let(::upper)
        if (line.isNotEmpty()) {
            val font = b.fit(line, font(Weight.BOLD, 38.0, 8.0), 1180.0)
            b.text(line, font, b.colors.accent, (WIDTH - width(line, font)) / 2, baseline(font, top + 6.0, normalLine(font)))
        }
        val teams = listOf(scene.text(ThumbnailField.TEAM1), scene.text(ThumbnailField.TEAM2)).filter { it.isNotEmpty() }
        if (teams.isNotEmpty()) {
            // The "vs" has the size of the names, with 12 px at each side.
            val base = font(Weight.BOLD, 40.0)
            val font = b.fit(teams.joinToString("vs"), base, 900.0) { (teams.size - 1) * 24.0 }
            val y = baseline(font, HEIGHT - 44.0 - normalLine(font), normalLine(font))
            val shadow = b.textShadow(0.6, 3.0, 12.0)
            var x = 56.0
            teams.forEachIndexed { index, team ->
                if (index > 0) {
                    b.text("vs", font, b.colors.accent, x + 12.0, y, shadow)
                    x += width("vs", font) + 24.0
                }
                b.text(team, font, b.colors.text, x, y, shadow)
                x += width(team, font)
            }
        }
    }

    // ---------- Painting ----------

    /** Paints the layout in the 1280 × 720 coordinates of [g2]. */
    fun paintLayout(g2: Graphics2D, layout: ThumbnailLayout) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        for (item in layout.items) {
            when (item) {
                is Item.Fill -> {
                    g2.paint = item.paint
                    g2.fill(item.shape)
                }
                is Item.BoxShadow -> paintBoxShadow(g2, item)
                is Item.Text -> {
                    item.shadow?.let { paintTextShadow(g2, item, it) }
                    g2.font = item.font
                    g2.color = item.color
                    g2.drawString(item.text, item.x.toFloat(), item.baseline.toFloat())
                }
            }
        }
    }

    /** An approximation of the CSS blur: layers that grow and fade. */
    private fun paintBoxShadow(g2: Graphics2D, shadow: Item.BoxShadow) {
        val layers = 10
        val each = 1.0 - Math.pow(1.0 - shadow.alpha, 1.0 / layers)
        val shape = shadow.shape
        for (i in 0 until layers) {
            val grow = shadow.blur / 2 * (i + 1) / layers
            g2.color = color(SHADOW_INK, each)
            g2.fill(
                RoundRectangle2D.Double(
                    shape.x - grow, shape.y + shadow.offsetY - grow, shape.width + 2 * grow, shape.height + 2 * grow,
                    shape.arcWidth + 2 * grow, shape.arcHeight + 2 * grow,
                ),
            )
        }
    }

    /** An approximation of the CSS text-shadow: the text at some offsets around the shadow point, each one faint. */
    private fun paintTextShadow(g2: Graphics2D, text: Item.Text, shadow: TextShadow) {
        val radius = shadow.blur / 3
        val offsets = listOf(0.0 to 0.0, radius to 0.0, -radius to 0.0, 0.0 to radius, 0.0 to -radius)
        val each = 1.0 - Math.pow(1.0 - shadow.alpha * 0.8, 1.0 / offsets.size)
        val outline = text.font.createGlyphVector(g2.fontRenderContext, text.text).getOutline(0f, 0f)
        val oldStroke = g2.stroke
        g2.stroke = BasicStroke((radius * 1.2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = color(shadow.rgb, each)
        for ((dx, dy) in offsets) {
            val moved = AffineTransform.getTranslateInstance(text.x + dx, text.baseline + shadow.offsetY + dy).createTransformedShape(outline)
            g2.fill(moved)
            g2.draw(moved)
        }
        g2.stroke = oldStroke
    }

    /**
     * Paints the frame with the zoom in the 1280 × 720 coordinates of [g2]. The frame fills the thumbnail (CSS "cover").
     * Without a frame, the thumbnail is dark.
     */
    fun paintFrame(g2: Graphics2D, frame: BufferedImage?, view: FrameView) {
        if (frame == null) {
            g2.color = color(PLACEHOLDER)
            g2.fill(fullArea())
            return
        }
        val source = sourceRect(frame.width, frame.height, view)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.drawImage(
            frame, 0, 0, WIDTH, HEIGHT,
            source.x.roundToInt(), source.y.roundToInt(), (source.x + source.width).roundToInt(), (source.y + source.height).roundToInt(), null,
        )
    }

    /** The part of the frame that the thumbnail shows, in the pixels of the frame. */
    fun sourceRect(frameWidth: Int, frameHeight: Int, view: FrameView): Rectangle2D.Double {
        val zoom = view.zoom.coerceAtLeast(1.0)
        val cover = max(WIDTH.toDouble() / frameWidth, HEIGHT.toDouble() / frameHeight)
        val coverX = (WIDTH - frameWidth * cover) / 2
        val coverY = (HEIGHT - frameHeight * cover) / 2
        val offsetX = (zoom - 1) * WIDTH * view.panX.coerceIn(0.0, 1.0)
        val offsetY = (zoom - 1) * HEIGHT * view.panY.coerceIn(0.0, 1.0)
        return Rectangle2D.Double(
            (offsetX / zoom - coverX) / cover,
            (offsetY / zoom - coverY) / cover,
            WIDTH / (zoom * cover),
            HEIGHT / (zoom * cover),
        )
    }

    /**
     * The visible part of the frame at [width] × [height], scaled down in steps of one half, so that a large frame
     * stays sharp and smooth. The previews and the PNG use it.
     */
    fun scaledFrame(frame: BufferedImage, view: FrameView, width: Int, height: Int): BufferedImage {
        val source = sourceRect(frame.width, frame.height, view)
        val x = source.x.roundToInt().coerceIn(0, frame.width - 1)
        val y = source.y.roundToInt().coerceIn(0, frame.height - 1)
        val w = source.width.roundToInt().coerceIn(1, frame.width - x)
        val h = source.height.roundToInt().coerceIn(1, frame.height - y)
        var image: BufferedImage = frame.getSubimage(x, y, w, h)
        var currentW = w
        var currentH = h
        while (currentW / 2 >= width && currentH / 2 >= height) {
            currentW /= 2
            currentH /= 2
            image = scale(image, currentW, currentH)
        }
        return scale(image, width, height)
    }

    private fun scale(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val result = BufferedImage(max(1, width), max(1, height), BufferedImage.TYPE_INT_RGB)
        val g2 = result.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.drawImage(image, 0, 0, result.width, result.height, null)
        } finally {
            g2.dispose()
        }
        return result
    }

    /** The PNG image: the frame with the zoom and the texts of the layout. [softness] makes the frame softer (see [ThumbnailPng]). */
    fun render(frame: BufferedImage?, view: FrameView, scene: ThumbnailScene, softness: Int = 0): BufferedImage {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics()
        try {
            if (frame != null) {
                var scaled = scaledFrame(frame, view, WIDTH, HEIGHT)
                repeat(softness) { scaled = ThumbnailPng.soften(scaled) }
                g2.drawImage(scaled, 0, 0, null)
            } else {
                paintFrame(g2, null, view)
            }
            paintLayout(g2, layout(scene))
        } finally {
            g2.dispose()
        }
        return image
    }

    /** The video length as YouTube shows it in the bottom-right corner. Only the previews show it, never the PNG. */
    fun paintLengthBadge(g2: Graphics2D, length: String) {
        val font = font(Weight.SEMIBOLD, 30.0)
        val w = width(length, font) + 18.0
        val h = normalLine(font) + 4.0
        val box = RoundRectangle2D.Double(WIDTH - 12.0 - w, HEIGHT - 12.0 - h, w, h, 12.0, 12.0)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.color = color(SHADOW_INK, 0.8)
        g2.fill(box)
        g2.font = font
        g2.color = color(0xFFFFFF)
        g2.drawString(length, (box.x + 9.0).toFloat(), baseline(font, box.y + 2.0, normalLine(font)).toFloat())
    }

    // ---------- Colors ----------

    internal fun color(rgb: Int, alpha: Double = 1.0): Color =
        Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, (alpha.coerceIn(0.0, 1.0) * 255).roundToInt())

    /** An elliptic radial gradient with radii [rx] and [ry], as the CSS "radial-gradient(ellipse rx ry at cx cy, …)". */
    private fun ellipseGradient(cx: Double, cy: Double, rx: Double, ry: Double, inner: Color, outer: Color): Paint {
        val transform = AffineTransform().apply {
            translate(cx, cy)
            scale(1.0, ry / rx)
        }
        return RadialGradientPaint(
            Point2D.Double(0.0, 0.0), rx.toFloat(), Point2D.Double(0.0, 0.0), floatArrayOf(0f, 1f), arrayOf(inner, outer),
            MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB, transform,
        )
    }
}
