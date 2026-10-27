package org.litvin.share

import org.litvin.share.ThumbnailRenderer.Builder
import org.litvin.share.ThumbnailRenderer.Item
import org.litvin.share.ThumbnailRenderer.TextDrop
import org.litvin.share.ThumbnailRenderer.TextOutline
import org.litvin.share.ThumbnailRenderer.TextShadow
import org.litvin.share.ThumbnailRenderer.baseline
import org.litvin.share.ThumbnailRenderer.color
import org.litvin.share.ThumbnailRenderer.width
import java.awt.Color
import java.awt.Font
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.Paint
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * The layouts of the 25 thumbnail styles of B-53. Each layout follows its artboard of the Claude Design canvas
 * "Thumbnail Styles" (see docs/backlog.md). All sizes are in the 1280 × 720 coordinates of the PNG, as in the artboards.
 *
 * The texts come from the fields. The artboards show sample texts: "SEMI-FINAL" is the round, "SPRING CUP 2026" is the
 * league or event, "YOUR CLUB" is the club name. A style that shows the round shows the large words when the round is empty.
 */
internal object ThumbnailDesigns {
    private const val W = ThumbnailRenderer.WIDTH.toDouble()
    private const val H = ThumbnailRenderer.HEIGHT.toDouble()

    // Fixed colors of the artboards. The color roles change the other parts.
    private const val INK = 0x111111
    private const val BLACK = 0x000000

    /** The skew of a synthetic italic, as the browsers draw an italic of a font without an italic face. */
    private const val ITALIC = -0.25

    /** The secondary texts (for example the second team) are a little darker than the main texts. */
    private const val DIM = 0.8

    private val CLUB_SHADOW = TextShadow(BLACK, 0.75, 2.0, 10.0)

    fun layout(b: Builder, scene: ThumbnailScene) {
        val ctx = Ctx(b, scene)
        when (scene.style) {
            ThumbnailStyle.SPLASH -> ctx.splash()
            ThumbnailStyle.SPRAY -> ctx.spray()
            ThumbnailStyle.BURST -> ctx.burst()
            ThumbnailStyle.TORN -> ctx.torn()
            ThumbnailStyle.STENCIL -> ctx.stencil()
            ThumbnailStyle.OFFICIAL_BAR -> ctx.officialBar()
            ThumbnailStyle.SIDE_PANEL -> ctx.sidePanel()
            ThumbnailStyle.HEADER_BAND -> ctx.headerBand()
            ThumbnailStyle.ROUND_CARD -> ctx.roundCard()
            ThumbnailStyle.CORNER_FRAME -> ctx.cornerFrame()
            ThumbnailStyle.CLASH -> ctx.clash()
            ThumbnailStyle.FIGHT_CARD -> ctx.fightCard()
            ThumbnailStyle.BOLT -> ctx.bolt()
            ThumbnailStyle.PLATES -> ctx.plates()
            ThumbnailStyle.SCORELINE -> ctx.scoreline()
            ThumbnailStyle.SPLIT_XL -> ctx.splitXl()
            ThumbnailStyle.CROSSOVER -> ctx.crossover()
            ThumbnailStyle.BAND -> ctx.band()
            ThumbnailStyle.DUOTONE -> ctx.duotone()
            ThumbnailStyle.ECHO -> ctx.echo()
            ThumbnailStyle.SLASH -> ctx.slash()
            ThumbnailStyle.RANK -> ctx.rank()
            ThumbnailStyle.SCORE_BUG -> ctx.scoreBug()
            ThumbnailStyle.LOWER_THIRD -> ctx.lowerThird()
            ThumbnailStyle.ALERT -> ctx.alert()
            ThumbnailStyle.WORDS, ThumbnailStyle.VERSUS, ThumbnailStyle.CARD, ThumbnailStyle.SPLIT, ThumbnailStyle.BROADCAST,
            ThumbnailStyle.POSTER,
            -> error("${scene.style} is a style of B-46")
        }
    }

    /** The sets of a score such as "6-4 7-5" or "7–6 4–6 11–9". At most 5 sets. */
    fun sets(score: String): List<Pair<String, String>> =
        Regex("(\\d+)\\s*[-–—:]\\s*(\\d+)").findAll(score).map { it.groupValues[1] to it.groupValues[2] }.take(5).toList()

    /** A team name in two rows, before the separator ("/", "&", "+"). "ANNA & MARK" becomes "ANNA" and "& MARK". */
    fun teamRows(name: String): List<String> {
        val at = name.indexOfAny(charArrayOf('/', '&', '+'))
        if (at <= 0) return listOf(name).filter { it.isNotBlank() }
        return listOf(name.substring(0, at).trim(), name.substring(at).trim()).filter { it.isNotEmpty() }
    }

    // ---------- Helpers ----------

    private enum class Align { LEFT, CENTER, RIGHT }

    /** A part of a line with its own color. [font] null: the font of the line. */
    private class Run(val text: String, val rgb: Int, val alpha: Double = 1.0, val font: Font? = null)

    private class Pad(val top: Double, val right: Double, val bottom: Double, val left: Double)

    /** The texts and the colors of a scene, and the builder. */
    private class Ctx(val b: Builder, val scene: ThumbnailScene) {
        val c = b.colors
        fun up(field: ThumbnailField) = scene.text(field).uppercase(Locale.ROOT)
        fun raw(field: ThumbnailField) = scene.text(field)
        val head get() = up(ThumbnailField.HEAD)
        val kind get() = up(ThumbnailField.KIND)
        val event get() = up(ThumbnailField.EVENT)

        val teams get() = listOf(up(ThumbnailField.TEAM1), up(ThumbnailField.TEAM2)).filter { it.isNotEmpty() }
        val bothTeams get() = teams.size == 2

        /** "ANNA & MARK vs LEO & KATE", with the "vs" in [vsRgb]. */
        fun versus(rgb: Int, vsRgb: Int, vs: String = " vs ", alpha: Double = 1.0): List<Run> = when (teams.size) {
            0 -> emptyList()
            1 -> listOf(Run(teams[0], rgb, alpha))
            else -> listOf(Run(teams[0], rgb, alpha), Run(vs, vsRgb), Run(teams[1], rgb, alpha))
        }

        fun joined(vararg fields: ThumbnailField): String = fields.map { up(it) }.filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** A bundled font. It becomes the font of the B-46 styles when it has no letter of [text] (for example Cyrillic). */
    private fun face(face: ThumbnailFace, size: Double, text: String, spacing: Double = 0.0): Font {
        val font = ThumbnailFonts.font(face, size, spacing)
        if (text.isEmpty() || ThumbnailFonts.displays(font, text)) return font
        return ThumbnailRenderer.font(ThumbnailRenderer.Weight.BLACK, size, spacing)
    }

    /** Makes the font smaller until [text] fits. A font smaller than [normal] × the start size makes the layout "shrunk". */
    private fun Builder.fitted(face: ThumbnailFace, size: Double, text: String, max: Double, normal: Double = 0.6, spacing: Double = 0.0): Font =
        fit(text, face(face, size, text, spacing), max, normal = size * normal)

    /** One line of text in a line box of [lineHeight] at [top]. [x]: the left edge, the middle, or the right edge. Returns the width. */
    private fun Builder.line(
        text: String,
        font: Font,
        rgb: Int?,
        x: Double,
        top: Double,
        lineHeight: Double,
        align: Align = Align.LEFT,
        shadow: TextShadow? = null,
        outline: TextOutline? = null,
        drop: TextDrop? = null,
        alpha: Double = 1.0,
    ): Double {
        if (text.isEmpty()) return 0.0
        val w = width(text, font)
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2
            Align.RIGHT -> x - w
        }
        text(text, font, rgb, left, baseline(font, top, lineHeight), shadow, alpha, outline, drop)
        return w
    }

    private fun runsWidth(runs: List<Run>, font: Font): Double = runs.sumOf { width(it.text, it.font ?: font) }

    /** The parts of a line. The line box uses [font]. Returns the width. */
    private fun Builder.runs(
        runs: List<Run>,
        font: Font,
        x: Double,
        top: Double,
        lineHeight: Double,
        align: Align = Align.LEFT,
        shadow: TextShadow? = null,
    ): Double {
        val parts = runs.filter { it.text.isNotEmpty() }
        val total = runsWidth(parts, font)
        var left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - total / 2
            Align.RIGHT -> x - total
        }
        val base = baseline(font, top, lineHeight)
        for (run in parts) {
            val runFont = run.font ?: font
            text(run.text, runFont, run.rgb, left, base, shadow, run.alpha)
            left += width(run.text, runFont)
        }
        return total
    }

    private fun Builder.fitRuns(runs: List<Run>, face: ThumbnailFace, size: Double, max: Double, normal: Double = 0.6, spacing: Double = 0.0): Font {
        val text = runs.joinToString("") { it.text }
        return fit(text, face(face, size, text, spacing), max, normal = size * normal) { font ->
            runs.filter { it.font != null }.sumOf { width(it.text, it.font!!) - width(it.text, font) }
        }
    }

    /**
     * Parts of a line on a box with [pad]. The line box is [lineHeight] (the font size, as "line-height: 1").
     * [x]: the left edge, the middle, or the right edge of the box. Returns the box.
     */
    private fun Builder.tag(
        runs: List<Run>,
        font: Font,
        box: Int,
        x: Double,
        top: Double,
        pad: Pad,
        align: Align = Align.LEFT,
        lineHeight: Double = font.size2D.toDouble(),
        drop: Pair<Double, Double>? = null,
        dropRgb: Int = INK,
    ): Rectangle2D {
        val w = pad.left + runsWidth(runs, font) + pad.right
        val h = pad.top + lineHeight + pad.bottom
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2
            Align.RIGHT -> x - w
        }
        val rect = Rectangle2D.Double(left, top, w, h)
        drop?.let { (dx, dy) -> fill(Rectangle2D.Double(left + dx, top + dy, w, h), color(dropRgb)) }
        fill(rect, color(box))
        runs(runs, font, left + pad.left, top + pad.top, lineHeight)
        return rect
    }

    private fun Builder.tag(text: String, rgb: Int, font: Font, box: Int, x: Double, top: Double, pad: Pad, align: Align = Align.LEFT): Rectangle2D? =
        if (text.isEmpty()) null else tag(listOf(Run(text, rgb)), font, box, x, top, pad, align)

    private fun rotate(degrees: Double, cx: Double, cy: Double) = AffineTransform.getRotateInstance(Math.toRadians(degrees), cx, cy)

    /** The CSS skewX around the point [cx], [cy]. */
    private fun skewX(degrees: Double, cx: Double, cy: Double) = AffineTransform().apply {
        translate(cx, cy)
        shear(tan(Math.toRadians(degrees)), 0.0)
        translate(-cx, -cy)
    }

    /** A synthetic italic of the text with the baseline [baseline]. */
    private fun italic(baseline: Double) = AffineTransform().apply {
        translate(0.0, baseline)
        shear(ITALIC, 0.0)
        translate(0.0, -baseline)
    }

    /** "x,y x,y …" as a closed path. */
    private fun polygon(points: String): Path2D {
        val path = Path2D.Double()
        points.trim().split(Regex("\\s+")).forEachIndexed { index, point ->
            val (x, y) = point.split(',').map { it.toDouble() }
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.closePath()
        return path
    }

    /** An SVG with [viewW] × [viewH] in the box [left], [top], [w], [h] (preserveAspectRatio="none"). */
    private fun viewBox(viewW: Double, viewH: Double, left: Double, top: Double, w: Double, h: Double) = AffineTransform().apply {
        translate(left, top)
        scale(w / viewW, h / viewH)
    }

    private fun full() = Rectangle2D.Double(0.0, 0.0, W, H)

    /** The CSS linear-gradient of the full thumbnail at [angle] degrees (0 is "to top", 90 is "to right"). */
    private fun cssLinear(angle: Double, vararg stops: Pair<Float, Color>): Paint {
        val a = Math.toRadians(angle)
        val dx = sin(a)
        val dy = -cos(a)
        val length = abs(W * sin(a)) + abs(H * cos(a))
        val start = Point2D.Double(W / 2 - dx * length / 2, H / 2 - dy * length / 2)
        val end = Point2D.Double(W / 2 + dx * length / 2, H / 2 + dy * length / 2)
        return LinearGradientPaint(start, end, stops.map { it.first }.toFloatArray(), stops.map { it.second }.toTypedArray())
    }

    /** A text color on a fixed background: [preferred] when it is readable there, else dark or white. */
    /** A color between [from] and [to]: [t] 0.0 is [from], 1.0 is [to]. The gray texts and lines of the light boxes use it. */
    private fun blend(from: Int, to: Int, t: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val z = (to shr shift) and 0xFF
            return (a + (z - a) * t).roundToInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** The gray text of a light box: [ThumbnailColors.onCard], a little lighter. */
    private val ThumbnailColors.grayOnCard: Int get() = blend(card, onCard, 0.8)

    /** The thin lines of a light box. */
    private val ThumbnailColors.cardLine: Int get() = blend(card, onCard, 0.17)

    /** The logo, as large as possible in [maxW] × [maxH], in the middle of the box height. Returns the box of the image. */
    private fun Ctx.logo(x: Double, top: Double, maxW: Double, maxH: Double, align: Align = Align.LEFT): Rectangle2D? {
        val image = scene.logo?.image ?: return null
        val scale = min(maxW / image.width, maxH / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2
            Align.RIGHT -> x - w
        }
        val y = top + (maxH - h) / 2
        b.add(Item.Picture(image, left, y, w, h))
        return Rectangle2D.Double(left, y, w, h)
    }

    /** The size of the logo in [maxW] × [maxH], or null without a logo. */
    private fun Ctx.logoSize(maxW: Double, maxH: Double): Pair<Double, Double>? {
        val image = scene.logo?.image ?: return null
        val scale = min(maxW / image.width, maxH / image.height)
        return image.width * scale to image.height * scale
    }

    /**
     * The logo at the top left (or right) and the club name under it, as in most artboards.
     * Without a logo, the club name goes up to the place of the logo.
     */
    private fun Ctx.corner(
        face: ThumbnailFace,
        right: Boolean = false,
        x: Double = 40.0,
        logoTop: Double = 36.0,
        clubTop: Double = 140.0,
        withClub: Boolean = true,
    ) {
        val align = if (right) Align.RIGHT else Align.LEFT
        val edge = if (right) W - x else x
        val box = logo(edge, logoTop, 240.0, 96.0, align)
        if (!withClub) return
        val club = up(ThumbnailField.CLUB)
        if (club.isEmpty()) return
        val font = b.fitted(scene.brandFace ?: face, 36.0, club, 440.0, normal = 0.8, spacing = 2.0)
        b.line(club, font, scene.brandRgb ?: c.text, edge, if (box != null) clubTop else logoTop + 4, 1.1 * font.size2D, align, CLUB_SHADOW)
    }

    /** The rows of [text], each fitted to [max]. The first row starts at [sizes] first, the second at the second size. */
    private fun Ctx.rowFonts(
        rows: List<String>,
        face: ThumbnailFace,
        sizes: List<Double>,
        max: Double,
        spacing: List<Double> = listOf(0.0, 0.0),
        normal: Double = 0.55,
    ): List<Font> = rows.mapIndexed { index, row ->
        val size = sizes.getOrElse(index) { sizes.last() }
        b.fitted(face, size, row, max, normal, spacing.getOrElse(index) { spacing.last() })
    }

    /** The rows of the event with the last word in the accent color, as "SPRING CUP 26" in the artboards. */
    private fun accentLast(rows: List<String>, rgb: Int, accent: Int, alpha: Double = 1.0): List<List<Run>> = rows.mapIndexed { index, row ->
        if (index != rows.lastIndex) return@mapIndexed listOf(Run(row, rgb, alpha))
        val space = row.lastIndexOf(' ')
        when {
            space > 0 -> listOf(Run(row.substring(0, space + 1), rgb, alpha), Run(row.substring(space + 1), accent))
            rows.size > 1 -> listOf(Run(row, accent))
            else -> listOf(Run(row, rgb, alpha))
        }
    }

    // ---------- 1 · Street ----------

    private const val SPLAT = "708,340 605,370 705,434 575,424 695,554 527,467 547,567 463,492 438,589 392,499 332,565 316,496 184,586 " +
        "261,452 142,476 188,411 103,387 200,340 81,289 230,283 131,198 259,227 184,94 318,189 321,83 392,177 439,84 471,164 586,50 " +
        "527,213 619,183 581,253 707,246 618,308"

    /** 1.1 Splash: a paint splash with the large words, and the match type on a marker tag. */
    private fun Ctx.splash() {
        val splat = polygon(SPLAT)
        b.fill(viewBox(820.0, 680.0, 410.0, 0.0, 900.0, 740.0).createTransformedShape(splat), color(c.panel))
        val drops = Area(splat).apply {
            listOf(Triple(760.0, 120.0, 18.0), Triple(790.0, 170.0, 9.0), Triple(70.0, 560.0, 14.0), Triple(40.0, 610.0, 7.0)).forEach { (x, y, r) ->
                add(Area(Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)))
            }
        }
        b.fill(viewBox(820.0, 680.0, 400.0, -10.0, 900.0, 740.0).createTransformedShape(drops), color(c.accent))

        val kind = raw(ThumbnailField.KIND)
        val rows = ThumbnailRenderer.rows(head)
        val fonts = rowFonts(rows, ThumbnailFace.ANTON, if (rows.size > 1) listOf(232.0, 164.0) else listOf(232.0), 520.0, listOf(4.0))
        val tagFont = kind.takeIf { it.isNotEmpty() }?.let { b.fitted(ThumbnailFace.MARKER, 62.0, it, 460.0) }
        val tagH = tagFont?.let { 8 + it.size2D + 12.0 } ?: 0.0
        val height = (if (tagFont != null) tagH + 6 else 0.0) + fonts.sumOf { 0.92 * it.size2D }
        if (height > 0) {
            val cx = 850.0
            val top = 112.0
            b.group(rotate(-6.0, cx, top + height / 2)) {
                var y = top
                if (tagFont != null) {
                    val w = width(kind, tagFont) + 52
                    val box = Rectangle2D.Double(cx - w / 2, y, w, tagH)
                    b.group(rotate(3.0, box.centerX, box.centerY)) {
                        b.fill(box, color(c.panel))
                        b.line(kind, tagFont, c.text, cx, y + 8, tagFont.size2D.toDouble(), Align.CENTER)
                    }
                    y += tagH + 6
                }
                rows.forEachIndexed { index, row ->
                    val font = fonts[index]
                    val line = 0.92 * font.size2D
                    b.line(row, font, c.panel, cx, y, line, Align.CENTER, outline = TextOutline(c.accent, 14.0))
                    y += line
                }
            }
        }
        corner(ThumbnailFace.ANTON)
    }

    private const val SPRAY_BAND = "0,0 29,9 66,8 103,1 143,14 173,6 209,1 234,3 255,0 278,6 319,10 346,6 369,14 401,8 423,5 464,3 508,8 " +
        "541,0 586,14 612,6 651,9 680,4 724,14 765,12 798,6 818,4 856,15 899,16 928,4 953,3 996,13 1032,13 1069,15 1107,8 1147,5 " +
        "1191,6 1235,12 1258,2 1298,2 1343,11 1377,2 1377,244 1343,250 1298,257 1258,258 1235,247 1191,250 1147,257 1107,247 1069,257 " +
        "1032,245 996,252 953,254 928,248 899,259 856,251 818,248 798,244 765,248 724,257 680,257 651,256 612,247 586,255 541,252 " +
        "508,246 464,244 423,248 401,245 369,260 346,250 319,246 278,244 255,246 234,249 209,245 173,246 143,257 103,258 66,254 " +
        "29,259 0,251"

    /** 1.2 Spray band: the large words on a sprayed band, and the event in marker letters. */
    private fun Ctx.spray() {
        val event = raw(ThumbnailField.EVENT)
        if (event.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.MARKER, 76.0, event, 760.0)
            val w = width(event, font)
            b.group(rotate(-7.0, 44 + w / 2, 300 + font.size2D / 2.0)) {
                b.line(event, font, c.text, 44.0, 300.0, font.size2D.toDouble(), shadow = TextShadow(BLACK, 0.6, 0.0, 18.0), drop = TextDrop(c.panel, 0.0, 4.0))
            }
        }
        val band = rotate(-7.0, 640.0, 590.0).apply { translate(-60.0, 410.0) }
        b.group(band) {
            b.fill(viewBox(1377.0, 260.0, 0.0, 0.0, 1400.0, 260.0).createTransformedShape(polygon(SPRAY_BAND)), color(c.panel))
            listOf(Triple(150.0, 16.0, 70.0), Triple(238.0, 11.0, 38.0), Triple(420.0, 18.0, 96.0), Triple(610.0, 12.0, 46.0), Triple(790.0, 16.0, 78.0))
                .forEach { (x, w, h) -> b.fill(RoundRectangle2D.Double(x, 250.0, w, h, w, w), color(c.panel)) }
            val font = b.fitted(ThumbnailFace.ANTON, 214.0, head, 1240.0, spacing = 4.0)
            b.line(head, font, c.accent, 700.0, 4.0, 1.12 * font.size2D, Align.CENTER)
        }
        corner(ThumbnailFace.ANTON)
    }

    private const val STAR = "380,10 418,113 484,25 492,134 580,69 557,176 660,138 607,234 717,226 639,304 746,327 650,380 746,433 " +
        "639,456 717,534 607,526 660,622 557,584 580,691 492,626 484,735 418,647 380,750 342,647 276,735 268,626 180,691 203,584 " +
        "100,622 153,526 43,534 121,456 14,433 110,380 14,327 121,304 43,226 153,234 100,138 203,176 180,69 268,134 276,25 342,113"

    /** 1.3 Spike burst: the large words on a star burst. */
    private fun Ctx.burst() {
        val star = polygon(STAR)
        val shadow = rotate(14.0, 934.0, 360.0).apply {
            translate(524.0, -50.0)
            scale(820.0 / 760.0, 820.0 / 760.0)
        }
        b.fill(shadow.createTransformedShape(star), color(c.panel))
        b.fill(rotate(4.0, 940.0, 360.0).apply { translate(560.0, -20.0) }.createTransformedShape(star), color(c.accent))
        val rows = ThumbnailRenderer.rows(head)
        val fonts = rowFonts(rows, ThumbnailFace.BUNGEE, if (rows.size > 1) listOf(116.0, 150.0) else listOf(150.0), 540.0)
        val height = fonts.sumOf { it.size2D.toDouble() }
        if (height > 0) {
            val top = 343.0 - height / 2
            b.group(rotate(-8.0, 940.0, 343.0)) {
                var y = top
                rows.forEachIndexed { index, row ->
                    val font = fonts[index]
                    b.line(row, font, c.onAccent, 940.0, y, font.size2D.toDouble(), Align.CENTER)
                    y += font.size2D
                }
            }
        }
        corner(ThumbnailFace.BUNGEE)
    }

    private const val TORN_EDGE = "0,0 551,0 556,23 568,42 549,67 558,91 566,119 574,137 555,153 545,179 541,201 567,226 571,249 564,268 " +
        "560,291 569,313 557,342 542,366 563,392 569,422 554,440 541,465 546,486 542,502 545,528 554,546 543,574 559,595 569,624 " +
        "550,651 553,672 574,700 546,717 548,734 561,756 555,760 0,760"

    /** "scaleX(-1) rotate(deg)" around the middle of the box, then the SVG box. */
    private fun mirrored(degrees: Double, left: Double, top: Double, w: Double, h: Double) = AffineTransform().apply {
        translate(left + w / 2, top + h / 2)
        scale(-1.0, 1.0)
        rotate(Math.toRadians(degrees))
        translate(-(left + w / 2), -(top + h / 2))
        translate(left, top)
        scale(w / 600.0, h / 760.0)
    }

    /** 1.4 Torn poster: a torn paper at the right with the large words, the match type tag, and the event. */
    private fun Ctx.torn() {
        val edge = polygon(TORN_EDGE)
        b.fill(mirrored(-3.0, 680.0, -20.0, 620.0, 780.0).createTransformedShape(edge), color(c.accent))
        b.fill(mirrored(-1.0, 722.0, -20.0, 600.0, 780.0).createTransformedShape(edge), color(c.card))
        val ink = c.onCard
        if (kind.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.ANTON, 52.0, kind, 380.0, spacing = 3.0)
            val box = Rectangle2D.Double(840.0, 46.0, width(kind, font) + 56, 12 + font.size2D + 14.0)
            b.group(rotate(-9.0, box.centerX, box.centerY)) {
                b.fill(Rectangle2D.Double(box.x, box.y + 6, box.width, box.height), color(BLACK, 0.25))
                b.fill(box, color(c.accent))
                b.line(kind, font, c.onAccent, box.x + 28, box.y + 12, font.size2D.toDouble())
            }
        }
        val rows = ThumbnailRenderer.rows(head)
        val fonts = rowFonts(rows, ThumbnailFace.ANTON, listOf(222.0, 176.0), 440.0)
        val event = raw(ThumbnailField.EVENT)
        val eventFont = event.takeIf { it.isNotEmpty() }?.let { b.fitted(ThumbnailFace.MARKER, 50.0, it, 440.0) }
        val height = fonts.sumOf { 0.95 * it.size2D } + (eventFont?.let { 18 + 1.1 * it.size2D } ?: 0.0)
        if (height <= 0) return corner(ThumbnailFace.ANTON)
        b.group(rotate(-2.0, 790.0 + 230, 150 + height / 2)) {
            var y = 150.0
            rows.forEachIndexed { index, row ->
                val font = fonts[index]
                b.line(row, font, ink, 790.0, y, 0.95 * font.size2D)
                y += 0.95 * font.size2D
            }
            if (eventFont != null) {
                val line = 1.1 * eventFont.size2D
                b.group(rotate(-3.0, 790 + width(event, eventFont) / 2, y + 18 + line / 2)) {
                    b.line(event, eventFont, ink, 790.0, y + 18, line)
                }
            }
        }
        corner(ThumbnailFace.ANTON)
    }

    /** 1.5 Stencil drip: a hazard stripe, the large words with paint drips, and the teams on a black bar. */
    private fun Ctx.stencil() {
        b.fill(full(), cssLinear(90.0, 0.3f to b.shade(0.0), 0.7f to b.shade(0.55)))
        val stripe = Rectangle2D.Double(-40.0, 34.0, 1400.0, 64.0)
        b.group(rotate(-4.0, 660.0, 66.0)) {
            b.fill(Rectangle2D.Double(stripe.x, stripe.y + 6, stripe.width, stripe.height), color(BLACK, 0.35))
            val d = Math.sqrt(0.5) * 72
            val stripes = LinearGradientPaint(
                Point2D.Double(0.0, 0.0), Point2D.Double(-d, -d), floatArrayOf(0f, 0.4999f, 0.5f, 1f),
                arrayOf(color(c.accent), color(c.accent), color(c.panel), color(c.panel)), MultipleGradientPaint.CycleMethod.REPEAT,
            )
            b.fill(stripe, stripes)
        }
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 330.0, head, 770.0, normal = 0.45, spacing = 6.0)
            val w = b.line(head, font, c.accent, 1240.0, 120.0, font.size2D.toDouble(), Align.RIGHT, outline = TextOutline(c.panel, 12.0), drop = TextDrop(c.panel, 12.0, 12.0))
            val k = font.size2D / 330.0
            val base = baseline(font, 120.0, font.size2D.toDouble())
            listOf(Triple(0.17, 16.0, 64.0), Triple(0.41, 12.0, 38.0), Triple(0.68, 18.0, 84.0), Triple(0.90, 12.0, 30.0)).forEach { (at, dw, dh) ->
                b.fill(RoundRectangle2D.Double(1240 - w + at * w - dw * k / 2, base - 6 * k, dw * k, dh * k, dw * k, dw * k), color(c.accent))
            }
        }
        val runs = versus(c.text, c.accent, " VS ")
        if (runs.isNotEmpty()) {
            val font = b.fitRuns(runs, ThumbnailFace.SHOULDERS, 64.0, 800.0, spacing = 1.0)
            b.tag(runs, font, c.panel, W - 44, 532.0, Pad(12.0, 24.0, 14.0, 24.0), Align.RIGHT)
        }
        corner(ThumbnailFace.SHOULDERS, x = 44.0, logoTop = 128.0, clubTop = 232.0)
    }

    // ---------- 2 · Competition ----------

    /** 2.1 Official bar: the round at the top right, and a white bar with the logo, the event, and the details. */
    private fun Ctx.officialBar() {
        b.fill(full(), cssLinear(200.0, 0f to b.shade(0.75), 0.45f to b.shade(0.0)))
        var y = 30.0
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 176.0, head, 1000.0)
            b.line(head, font, c.text, 1232.0, y, 0.95 * font.size2D, Align.RIGHT, b.textShadow(0.5, 6.0, 24.0))
            y += 0.95 * font.size2D
        }
        if (kind.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 70.0, kind, 900.0, spacing = 4.0)
            b.line(kind, font, c.accent, 1232.0, y, font.size2D.toDouble(), Align.RIGHT, b.textShadow(0.5, 4.0, 16.0))
        }
        val details = listOf(raw(ThumbnailField.COURT), raw(ThumbnailField.DATE)).filter { it.isNotEmpty() }.joinToString(" · ")
        val club = raw(ThumbnailField.CLUB)
        val info = listOf(details, club).filter { it.isNotEmpty() }.joinToString(" · ")
        if (event.isEmpty() && info.isEmpty() && scene.logo == null) return
        val bar = polygon("0,508 1110,508 1043.4,676 0,676")
        b.fill(bar, color(c.card))
        b.group(clip = bar) { b.fill(Rectangle2D.Double(0.0, 508.0, 1110.0, 12.0), color(c.accent)) }
        var x = 44.0
        val box = logo(x, 536.0, 220.0, 112.0)
        if (box != null) {
            x = box.maxX + 30
            b.fill(Rectangle2D.Double(x, 540.0, 4.0, 104.0), color(c.cardLine))
            x += 34
        }
        val max = 1043.0 - 40 - x
        val eventFont = event.takeIf { it.isNotEmpty() }?.let { b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 86.0, it, max) }
        // The brand name is the last part of the info line, in the font and the color of the brand.
        val brandFace = scene.brandFace ?: ThumbnailFace.INTER_BOLD
        fun brandFont(info: Font) = face(brandFace, info.size2D.toDouble(), club)
        val infoFont = info.takeIf { it.isNotEmpty() }?.let {
            b.fit(it, face(ThumbnailFace.INTER_BOLD, 34.0, it), max, normal = 34.0 * 0.8) { font ->
                if (club.isEmpty()) 0.0 else width(club, brandFont(font)) - width(club, font)
            }
        }
        val height = (eventFont?.let { 0.95 * it.size2D } ?: 0.0) + (if (eventFont != null && infoFont != null) 4.0 else 0.0) +
            (infoFont?.let { 1.1 * it.size2D } ?: 0.0)
        var top = 508 + (168 - height) / 2
        if (eventFont != null) {
            b.line(event, eventFont, c.onCard, x, top, 0.95 * eventFont.size2D)
            top += 0.95 * eventFont.size2D + 4
        }
        if (infoFont != null) {
            val runs = listOfNotNull(
                Run(details, c.grayOnCard).takeIf { details.isNotEmpty() },
                Run(" · ", c.grayOnCard).takeIf { details.isNotEmpty() && club.isNotEmpty() },
                Run(club, scene.brandRgb ?: c.grayOnCard, font = brandFont(infoFont)).takeIf { club.isNotEmpty() },
            )
            b.runs(runs, infoFont, x, top, 1.1 * infoFont.size2D)
        }
    }

    /** 2.2 Side panel: a dark panel at the left with the logo, the club, the event, the round, and the teams. */
    private fun Ctx.sidePanel() {
        b.fill(Rectangle2D.Double(0.0, 0.0, 460.0, H), color(c.panel))
        b.fill(Rectangle2D.Double(460.0, 0.0, 12.0, H), color(c.accent))
        val x = 48.0
        val max = 372.0
        var y = 44.0
        val club = raw(ThumbnailField.CLUB)
        val size = logoSize(160.0, 96.0)
        if (size != null || club.isNotEmpty()) {
            val clubX = if (size != null) x + size.first + 18 else x
            val clubFont = club.takeIf { it.isNotEmpty() }?.let { b.fitted(scene.brandFace ?: ThumbnailFace.INTER_BOLD, 32.0, it, x + max - clubX, normal = 0.7) }
            val clubH = clubFont?.let { 1.15 * it.size2D } ?: 0.0
            val rowH = max(size?.second ?: 0.0, clubH)
            if (size != null) logo(x, y + (rowH - size.second) / 2, 160.0, size.second)
            if (clubFont != null) {
                b.line(club, clubFont, scene.brandRgb ?: c.text, clubX, y + (rowH - clubH) / 2, clubH, alpha = if (scene.brandRgb != null) 1.0 else DIM)
            }
            y += rowH + 30
        }
        if (event.isNotEmpty()) {
            val rows = ThumbnailRenderer.rows(event)
            val fonts = rowFonts(rows, ThumbnailFace.BARLOW_EXTRA_BOLD, listOf(132.0), max)
            accentLast(rows, c.text, c.accent).forEachIndexed { index, runs ->
                val font = fonts[index]
                b.runs(runs, font, x, y, 0.9 * font.size2D)
                y += 0.9 * font.size2D
            }
        }
        b.fill(Rectangle2D.Double(x, y + 26, 96.0, 8.0), color(c.accent))
        y += 34
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 58.0, head, max)
            b.line(head, font, c.text, x, y + 22, font.size2D.toDouble())
            y += 22 + font.size2D
        }
        if (teams.isNotEmpty()) {
            y += 20
            val font = b.fitted(ThumbnailFace.BARLOW_BOLD, 50.0, teams.maxBy { it.length } + " vs", max)
            b.line(teams[0], font, c.text, x, y, 1.05 * font.size2D, alpha = DIM)
            if (bothTeams) b.runs(listOf(Run("vs ", c.accent), Run(teams[1], c.text, DIM)), font, x, y + 1.05 * font.size2D, 1.05 * font.size2D)
        }
    }

    /** 2.3 Header band: a band at the top with the logo, the event, and the date; the round and the teams at the right. */
    private fun Ctx.headerBand() {
        b.fill(full(), cssLinear(270.0, 0f to b.shade(0.8), 0.55f to b.shade(0.0)))
        b.fill(Rectangle2D.Double(0.0, 0.0, W, 140.0), color(c.panel))
        var right = W
        val date = up(ThumbnailField.DATE)
        if (date.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 64.0, date, 420.0)
            val w = width(date, font) + 80
            b.fill(Rectangle2D.Double(W - w, 0.0, w, 140.0), color(c.accent))
            b.line(date, font, c.onAccent, W - w + 40, (140 - font.size2D) / 2.0, font.size2D.toDouble())
            right = W - w
        }
        var x = 40.0
        logo(x, 24.0, 180.0, 92.0)?.let { x = it.maxX + 26 }
        if (event.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 84.0, event, right - 26 - x)
            b.line(event, font, c.text, x, (140 - font.size2D) / 2.0, font.size2D.toDouble())
        }
        val rx = 1232.0
        val max = 700.0
        var y = 220.0
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 50.0, head, max - 40, spacing = 3.0)
            val box = b.tag(head, c.onAccent, font, c.accent, rx, y, Pad(10.0, 20.0, 8.0, 20.0), Align.RIGHT)
            y += (box?.height ?: 0.0) + 18
        }
        val shadow = b.textShadow(0.5, 6.0, 20.0)
        teams.forEachIndexed { index, team ->
            if (index == 1) {
                val vs = face(ThumbnailFace.BARLOW_EXTRA_BOLD, 60.0, "VS")
                b.line("VS", vs, c.accent, rx, y, 63.0, Align.RIGHT, b.textShadow(0.5, 4.0, 14.0))
                y += 63
            }
            val font = b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 112.0, team, max)
            b.line(team, font, c.text, rx, y, 0.95 * font.size2D, Align.RIGHT, shadow)
            y += 0.95 * font.size2D
        }
    }

    /** 2.4 Round card: a white card at the right with the logo, the round, the event, and the details. */
    private fun Ctx.roundCard() {
        val card = RoundRectangle2D.Double(752.0, 56.0, 480.0, 568.0, 48.0, 48.0)
        b.add(Item.BoxShadow(card, 14.0, 44.0, 0.45))
        b.fill(card, color(c.card))
        b.group(clip = card) { b.fill(Rectangle2D.Double(752.0, 606.0, 480.0, 18.0), color(c.accent)) }
        val cx = 992.0
        val max = 440.0
        val ink = c.onCard
        val size = logoSize(300.0, 116.0)
        val rows = ThumbnailRenderer.rows(head)
        val bigFonts = rowFonts(rows, ThumbnailFace.BARLOW_EXTRA_BOLD, listOf(if (rows.size > 1) 130.0 else 210.0), max, normal = 0.35)
        val eventFont = event.takeIf { it.isNotEmpty() }?.let { b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 62.0, it, max) }
        val info = listOf(raw(ThumbnailField.DATE), raw(ThumbnailField.COURT)).filter { it.isNotEmpty() }.joinToString(" · ")
        val infoFont = info.takeIf { it.isNotEmpty() }?.let { b.fitted(ThumbnailFace.INTER_BOLD, 34.0, it, max, normal = 0.8) }
        val height = (size?.second ?: 0.0) + (if (rows.isNotEmpty()) (if (size != null) 10.0 else 0.0) + bigFonts.sumOf { 0.95 * it.size2D } else 0.0) +
            (eventFont?.let { 6 + it.size2D.toDouble() } ?: 0.0) + (infoFont?.let { 12 + 1.2 * it.size2D } ?: 0.0)
        var y = max(96.0, 56 + (550 - height) / 2)
        if (size != null) {
            logo(cx, y, 300.0, size.second, Align.CENTER)
            y += size.second + 10
        }
        rows.forEachIndexed { index, row ->
            val font = bigFonts[index]
            b.line(row, font, ink, cx, y, 0.95 * font.size2D, Align.CENTER)
            y += 0.95 * font.size2D
        }
        if (eventFont != null) {
            b.line(event, eventFont, ink, cx, y + 6, eventFont.size2D.toDouble(), Align.CENTER)
            y += 6 + eventFont.size2D
        }
        if (infoFont != null) b.line(info, infoFont, c.grayOnCard, cx, y + 12, 1.2 * infoFont.size2D, Align.CENTER)
    }

    /** 2.5 Corner frame: a thin frame, the logo and the event at the top left, the date and the court at the top right. */
    private fun Ctx.cornerFrame() {
        b.fill(
            full(),
            LinearGradientPaint(
                Point2D.Double(0.0, 0.0), Point2D.Double(0.0, H), floatArrayOf(0f, 0.3f, 0.55f, 1f),
                arrayOf(b.shade(0.7), b.shade(0.0), b.shade(0.0), b.shade(0.8)),
            ),
        )
        val frame = Area(Rectangle2D.Double(26.0, 26.0, 1228.0, 668.0)).apply { subtract(Area(Rectangle2D.Double(32.0, 32.0, 1216.0, 656.0))) }
        b.fill(frame, color(c.accent))

        val rows = if (event.isEmpty()) emptyList() else ThumbnailRenderer.rows(event)
        val size = logoSize(200.0, 100.0)
        val textX = if (size != null) 60 + size.first + 22 else 60.0
        val fonts = rowFonts(rows, ThumbnailFace.BARLOW_EXTRA_BOLD, listOf(70.0), 600.0 - textX)
        val textH = fonts.sumOf { 0.95 * it.size2D }
        val blockH = max(size?.second ?: 0.0, textH)
        if (size != null) logo(60.0, 56 + (blockH - size.second) / 2, 200.0, size.second)
        var y = 56 + (blockH - textH) / 2
        accentLast(rows, c.text, c.accent).forEachIndexed { index, runs ->
            val font = fonts[index]
            b.runs(runs, font, textX, y, 0.95 * font.size2D)
            y += 0.95 * font.size2D
        }

        val date = up(ThumbnailField.DATE)
        val court = up(ThumbnailField.COURT)
        var top = 60.0
        if (date.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 64.0, date, 440.0)
            b.line(date, font, c.text, W - 64, top, font.size2D.toDouble(), Align.RIGHT)
            top += font.size2D
        }
        if (court.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.BARLOW_BOLD, 46.0, court, 440.0)
            b.line(court, font, c.text, W - 64, top, 1.1 * font.size2D, Align.RIGHT, alpha = DIM + 0.05)
        }

        val runs = versus(c.text, c.accent, alpha = DIM + 0.05)
        val teamFont = runs.takeIf { it.isNotEmpty() }?.let { b.fitRuns(it, ThumbnailFace.BARLOW_BOLD, 54.0, 930.0) }
        val bigFont = head.takeIf { it.isNotEmpty() }?.let { b.fitted(ThumbnailFace.BARLOW_EXTRA_BOLD, 160.0, it, 930.0) }
        var bottom = H - 56
        if (teamFont != null) {
            bottom -= 1.1 * teamFont.size2D
            b.runs(runs, teamFont, 60.0, bottom, 1.1 * teamFont.size2D)
        }
        if (bigFont != null) {
            bottom -= 0.9 * bigFont.size2D
            b.line(head, bigFont, c.text, 60.0, bottom, 0.9 * bigFont.size2D, shadow = b.textShadow(0.5, 6.0, 24.0))
        }
    }

    // ---------- 3 · Fight night ----------

    /** 3.1 Diagonal clash: a red side and a blue side, the teams on them, the round at the top, and a large "VS". */
    private fun Ctx.clash() {
        b.fill(polygon("0,0 470,0 330,720 0,720"), color(c.second))
        b.fill(polygon("880,0 1280,0 1280,720 740,720"), color(c.third))
        val drop = TextDrop(BLACK, 0.0, 6.0, 0.3)
        val team1 = up(ThumbnailField.TEAM1)
        val team2 = up(ThumbnailField.TEAM2)
        var y = 170.0
        for (row in teamRows(team1)) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 136.0, row, 560.0)
            b.line(row, font, c.text, 40.0, y, 0.9 * font.size2D, drop = drop)
            y += 0.9 * font.size2D
        }
        y = 170.0
        for (row in teamRows(team2)) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 136.0, row, 560.0)
            b.line(row, font, c.text, W - 44, y, 0.88 * font.size2D, Align.RIGHT, drop = drop)
            y += 0.88 * font.size2D
        }
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 56.0, head, 620.0, spacing = 6.0)
            b.tag(head, c.text, font, c.panel, W / 2, 28.0, Pad(10.0, 26.0, 12.0, 26.0), Align.CENTER)
        }
        if (bothTeams) {
            val font = face(ThumbnailFace.SHOULDERS, 230.0, "VS")
            b.group(italic(baseline(font, 486.0, 230.0))) {
                b.line("VS", font, c.accent, W / 2, 486.0, 230.0, Align.CENTER, outline = TextOutline(c.panel, 10.0))
            }
        }
        corner(ThumbnailFace.SHOULDERS, withClub = false)
    }

    /** 3.2 Fight card: the large words on a tag, and the two teams with a "VS" between two lines. */
    private fun Ctx.fightCard() {
        b.fill(full(), cssLinear(90.0, 0.25f to b.shade(0.0), 0.62f to b.shade(0.82)))
        val cx = 855.0
        var y = 100.0
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 60.0, head, 700.0, spacing = 6.0)
            val box = b.tag(head, c.onAccent, font, c.accent, cx, y, Pad(10.0, 28.0, 12.0, 28.0), Align.CENTER)
            y += (box?.height ?: 0.0) + 20
        }
        teams.forEachIndexed { index, team ->
            if (index == 1) {
                y += 8
                val vs = face(ThumbnailFace.SHOULDERS, 120.0, "VS")
                val vsW = width("VS", vs)
                val lineW = ((640 - vsW - 56) / 2).coerceAtLeast(0.0)
                b.fill(Rectangle2D.Double(cx - 320, y + 56, lineW, 8.0), color(c.accent))
                b.fill(Rectangle2D.Double(cx + 320 - lineW, y + 56, lineW, 8.0), color(c.accent))
                b.group(italic(baseline(vs, y, 120.0))) {
                    b.line("VS", vs, null, cx, y, 120.0, Align.CENTER, outline = TextOutline(c.accent, 5.0))
                }
                y += 128
            }
            val font = b.fitted(ThumbnailFace.SHOULDERS, 140.0, team, 760.0)
            b.line(team, font, c.text, cx, y, 0.9 * font.size2D, Align.CENTER)
            y += 0.9 * font.size2D
        }
        corner(ThumbnailFace.SHOULDERS)
    }

    /** A team on a skewed plate with a hard shadow. */
    private fun Ctx.skewedPlate(team: String, rgb: Int, x: Double, top: Double, max: Double) {
        if (team.isEmpty()) return
        val font = b.fitted(ThumbnailFace.SHOULDERS, 118.0, team, max)
        val box = Rectangle2D.Double(x, top, width(team, font) + 60, 6 + font.size2D + 12.0)
        b.group(skewX(-8.0, box.centerX, box.centerY)) {
            b.fill(Rectangle2D.Double(box.x + 10, box.y + 10, box.width, box.height), color(c.panel))
            b.fill(box, color(rgb))
            b.line(team, font, c.text, box.x + 30, box.y + 6, font.size2D.toDouble())
        }
    }

    /** 3.3 Lightning: a lightning bolt between the teams on skewed plates, and the large words at the bottom left. */
    private fun Ctx.bolt() {
        b.fill(full(), b.shade(0.28))
        b.fill(polygon("704,10 824,10 759,272 844,272 624,730 714,422 634,422"), color(c.panel))
        b.fill(polygon("680,0 800,0 735,260 820,260 600,720 690,410 610,410"), color(c.accent))
        // The first plate stops before the logo and the club name at the top right.
        skewedPlate(up(ThumbnailField.TEAM1), c.second, 330.0, 44.0, 560.0)
        skewedPlate(up(ThumbnailField.TEAM2), c.third, 500.0, 420.0, 660.0)
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 128.0, head, 960.0)
            b.line(head, font, c.accent, 44.0, 560.0, font.size2D.toDouble(), outline = TextOutline(c.panel, 8.0))
        }
        corner(ThumbnailFace.SHOULDERS, right = true)
    }

    /** A team on a plate rotated by -5 degrees, with a hard shadow. [x] is the left edge, or the right edge for [right]. */
    private fun Ctx.rotatedPlate(team: String, rgb: Int, x: Double, top: Double, right: Boolean, max: Double) {
        if (team.isEmpty()) return
        val font = b.fitted(ThumbnailFace.SHOULDERS, 112.0, team, max)
        val w = width(team, font) + 64
        val box = Rectangle2D.Double(if (right) x - w else x, top, w, 8 + font.size2D + 14.0)
        b.group(rotate(-5.0, box.centerX, box.centerY)) {
            b.fill(Rectangle2D.Double(box.x + 12, box.y + 12, box.width, box.height), color(c.panel))
            b.fill(box, color(rgb))
            b.line(team, font, c.text, box.x + 32, box.y + 8, font.size2D.toDouble())
        }
    }

    /** 3.4 Name plates: the teams on rotated plates, a "VS" circle, and the round at the bottom left. */
    private fun Ctx.plates() {
        // The first plate stops before the logo and the club name at the top right.
        rotatedPlate(up(ThumbnailField.TEAM1), c.second, 36.0, 64.0, right = false, max = 600.0)
        rotatedPlate(up(ThumbnailField.TEAM2), c.third, W - 36, 462.0, right = true, max = 760.0)
        if (bothTeams) {
            b.fill(Ellipse2D.Double(900.0, 196.0, 220.0, 220.0), color(c.panel))
            b.fill(Ellipse2D.Double(910.0, 206.0, 200.0, 200.0), color(c.accent))
            val font = face(ThumbnailFace.SHOULDERS, 130.0, "VS")
            b.group(italic(baseline(font, 241.0, 130.0))) {
                b.line("VS", font, c.onAccent, 1010.0, 241.0, 130.0, Align.CENTER)
            }
        }
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 100.0, head, 900.0, spacing = 2.0)
            b.line(head, font, c.text, 44.0, 590.0, font.size2D.toDouble(), shadow = b.textShadow(0.7, 5.0, 18.0))
        }
        corner(ThumbnailFace.SHOULDERS, right = true)
    }

    /** 3.5 Scoreline: the round, the teams, and the score in very large digits at the right. */
    private fun Ctx.scoreline() {
        b.fill(full(), cssLinear(90.0, 0.22f to b.shade(0.0), 0.6f to b.shade(0.85)))
        val rx = 1232.0
        val max = 640.0
        var y = 70.0
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 56.0, head, max, spacing = 6.0)
            val box = b.tag(head, c.onAccent, font, c.accent, rx, y, Pad(10.0, 24.0, 12.0, 24.0), Align.RIGHT)
            y += (box?.height ?: 0.0) + 18
        }
        val team1 = up(ThumbnailField.TEAM1)
        val team2 = up(ThumbnailField.TEAM2)
        if (team1.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.SHOULDERS, 116.0, team1, max)
            b.line(team1, font, c.text, rx, y, 0.92 * font.size2D, Align.RIGHT)
            y += 0.92 * font.size2D
        }
        if (team2.isNotEmpty()) {
            val runs = listOfNotNull(Run("vs ", c.accent).takeIf { team1.isNotEmpty() }, Run(team2, c.text, DIM))
            val font = b.fitRuns(runs, ThumbnailFace.SHOULDERS, 72.0, max)
            b.runs(runs, font, rx, y, 1.05 * font.size2D, Align.RIGHT)
            y += 1.05 * font.size2D
        }
        val score = sets(raw(ThumbnailField.SCORE))
        if (score.isNotEmpty()) {
            val runs = score.flatMapIndexed { index, (a, z) ->
                listOfNotNull(Run(" ", c.text).takeIf { index > 0 }, Run(a, c.text), Run("-", c.accent), Run(z, c.text))
            }
            val font = b.fitRuns(runs, ThumbnailFace.SHOULDERS, 236.0, max, normal = 0.45, spacing = 4.0)
            b.runs(runs, font, rx, y + 6, 0.92 * font.size2D, Align.RIGHT)
        }
        corner(ThumbnailFace.SHOULDERS)
    }

    // ---------- 4 · Bold blocks ----------

    /** 4.1 Split XL: an accent block at the right, cut on a diagonal, with the large words and two black tags. */
    private fun Ctx.splitXl() {
        b.fill(polygon("592,0 626,0 506,720 472,720"), color(c.panel))
        b.fill(polygon("626,0 1280,0 1280,720 506,720"), color(c.accent))
        val rx = 1228.0
        val max = 640.0
        var y = 76.0
        if (kind.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.ARCHIVO, 56.0, kind, max, spacing = 2.0)
            b.line(kind, font, c.onAccent, rx, y, font.size2D.toDouble(), Align.RIGHT)
            y += font.size2D
        }
        val rows = ThumbnailRenderer.rows(head)
        val fonts = rowFonts(rows, ThumbnailFace.ARCHIVO, listOf(190.0, 140.0), max, listOf(-4.0, -3.0))
        rows.forEachIndexed { index, row ->
            val font = fonts[index]
            val line = (if (index == 0) 0.92 else 0.95) * font.size2D
            b.line(row, font, c.onAccent, rx, y, line, Align.RIGHT)
            y += line
        }
        val runs = versus(c.onPanel, c.accent)
        if (runs.isNotEmpty()) {
            val font = b.fitRuns(runs, ThumbnailFace.ARCHIVO, 40.0, max + 20 - 32)
            y += 20
            y += b.tag(runs, font, c.panel, rx, y, Pad(10.0, 16.0, 10.0, 16.0), Align.RIGHT).height
        }
        val info = joined(ThumbnailField.EVENT, ThumbnailField.ROUND)
        if (info.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.ARCHIVO, 36.0, info, max + 20 - 32, spacing = 2.0)
            b.tag(info, c.accent, font, c.panel, rx, y + 10, Pad(10.0, 16.0, 10.0, 16.0), Align.RIGHT)
        }
        corner(ThumbnailFace.ARCHIVO)
    }

    /** 4.2 Crossover: the large words cross from the frame into an accent column, and change their color there. */
    private fun Ctx.crossover() {
        b.fill(full(), cssLinear(90.0, 0.3f to b.shade(0.0), 0.7f to b.shade(0.5)))
        b.fill(Rectangle2D.Double(900.0, 0.0, 380.0, H), color(c.accent))
        val rx = 1230.0
        val rows = ThumbnailRenderer.rows(head)
        val fonts = rowFonts(rows, ThumbnailFace.ARCHIVO, listOf(214.0), 900.0, listOf(-5.0))
        val shadow = b.textShadow(0.6, 6.0, 22.0)
        fun words(rgb: Int, vsRgb: Int, shadow: TextShadow?) {
            var y = 160.0
            rows.forEachIndexed { index, row ->
                val font = fonts[index]
                b.line(row, font, rgb, rx, y, 0.9 * font.size2D, Align.RIGHT, shadow)
                y += 0.9 * font.size2D
            }
            val runs = versus(rgb, vsRgb)
            if (runs.isNotEmpty()) {
                val font = b.fitRuns(runs, ThumbnailFace.ARCHIVO, 46.0, 900.0)
                b.runs(runs, font, rx, y + 16, 1.1 * font.size2D, Align.RIGHT, shadow)
            }
        }
        b.group(clip = Rectangle2D.Double(0.0, 0.0, 900.0, H)) { words(c.text, c.accent, shadow) }
        b.group(clip = Rectangle2D.Double(900.0, 0.0, 380.0, H)) {
            words(c.onAccent, c.onAccent, null)
            if (kind.isNotEmpty()) {
                val font = b.fitted(ThumbnailFace.ARCHIVO, 56.0, kind, 340.0, normal = 0.5, spacing = 2.0)
                b.line(kind, font, c.onAccent, rx, 92.0, font.size2D.toDouble(), Align.RIGHT)
            }
        }
        corner(ThumbnailFace.ARCHIVO)
    }

    /** 4.3 Band: an accent band near the bottom with the large words, and two black tags above it. */
    private fun Ctx.band() {
        b.fill(polygon("0,482 1172,482 1122,656 0,656"), color(c.panel))
        b.fill(polygon("0,468 1150,468 1100,642 0,642"), color(c.accent))
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.ARCHIVO, 124.0, head, 1040.0, spacing = -3.0)
            b.line(head, font, c.onAccent, 40.0, 486.0, 1.1 * font.size2D)
        }
        var bottom = H - 262
        val info = joined(ThumbnailField.KIND, ThumbnailField.EVENT)
        if (info.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.ARCHIVO, 40.0, info, 1000.0, spacing = 2.0)
            bottom -= 24 + font.size2D
            b.tag(info, c.accent, font, c.panel, 40.0, bottom, Pad(12.0, 18.0, 12.0, 18.0))
            bottom -= 8
        }
        val runs = versus(c.onPanel, c.accent)
        if (runs.isNotEmpty()) {
            val font = b.fitRuns(runs, ThumbnailFace.ARCHIVO, 48.0, 1000.0)
            bottom -= 24 + font.size2D
            b.tag(runs, font, c.panel, 40.0, bottom, Pad(12.0, 18.0, 12.0, 18.0))
        }
        corner(ThumbnailFace.ARCHIVO)
    }

    /** 4.4 Duotone half: the right half of the frame is gray and dark, with the large words in the accent color. */
    private fun Ctx.duotone() {
        b.add(Item.GrayFrame(Rectangle2D.Double(640.0, 0.0, 640.0, H)))
        b.fill(Rectangle2D.Double(632.0, 0.0, 12.0, H), color(c.accent))
        val x = 700.0
        val max = 540.0
        var y = 140.0
        if (kind.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.ARCHIVO, 56.0, kind, max, spacing = 3.0)
            b.line(kind, font, c.text, x, y, font.size2D.toDouble())
            y += font.size2D + 10
        }
        val rows = ThumbnailRenderer.rows(head)
        val fonts = rowFonts(rows, ThumbnailFace.ARCHIVO, listOf(196.0, 130.0), max, listOf(-4.0, -3.0))
        rows.forEachIndexed { index, row ->
            val font = fonts[index]
            val line = (if (index == 0) 0.92 else 0.98) * font.size2D
            b.line(row, font, c.accent, x, y, line)
            y += line
        }
        if (teams.isNotEmpty()) {
            y += 18
            val font = b.fitted(ThumbnailFace.ARCHIVO, 46.0, teams.maxBy { it.length } + "vs ", max)
            b.line(teams[0], font, c.text, x, y, 1.05 * font.size2D)
            if (bothTeams) b.runs(listOf(Run("vs ", c.accent), Run(teams[1], c.text)), font, x, y + 1.05 * font.size2D, 1.05 * font.size2D)
        }
        corner(ThumbnailFace.ARCHIVO)
    }

    /** 4.5 Echo: the first word three times (two outlines and the fill), then the other words and the teams. */
    private fun Ctx.echo() {
        b.fill(full(), cssLinear(90.0, 0.25f to b.shade(0.0), 0.62f to b.shade(0.8)))
        val rx = 1228.0
        val max = 680.0
        val words = head.split(' ').filter { it.isNotEmpty() }
        var y = 24.0
        if (words.isNotEmpty()) {
            val first = words.first()
            val font = b.fitted(ThumbnailFace.ARCHIVO, 166.0, first, max, spacing = -4.0)
            val echo = 0.74 * font.size2D
            b.line(first, font, null, rx, y, echo, Align.RIGHT, outline = TextOutline(c.accent, 3.0, 0.55))
            y += echo
            b.line(first, font, null, rx, y, echo, Align.RIGHT, outline = TextOutline(c.accent, 4.0))
            y += echo
            b.line(first, font, c.accent, rx, y, 0.92 * font.size2D, Align.RIGHT)
            y += 0.92 * font.size2D
            val rest = words.drop(1).joinToString(" ")
            if (rest.isNotEmpty()) {
                val restFont = b.fitted(ThumbnailFace.ARCHIVO, 126.0, rest, max, spacing = -3.0)
                b.line(rest, restFont, c.text, rx, y, 0.95 * restFont.size2D, Align.RIGHT)
                y += 0.95 * restFont.size2D
            }
        }
        val runs = versus(c.text, c.accent)
        if (runs.isNotEmpty()) {
            val font = b.fitRuns(runs, ThumbnailFace.ARCHIVO, 44.0, max)
            b.runs(runs, font, rx, y + 14, 1.1 * font.size2D, Align.RIGHT)
        }
        corner(ThumbnailFace.ARCHIVO)
    }

    // ---------- 5 · Sports TV ----------

    /** 5.1 Slash: a skewed dark panel with two accent bars, the match type tag, the large words, and the teams. */
    private fun Ctx.slash() {
        b.group(skewX(-14.0, 0.0, 340.0)) {
            b.fill(Rectangle2D.Double(556.0, 120.0, 18.0, 440.0), color(c.accent))
            b.fill(Rectangle2D.Double(590.0, 120.0, 18.0, 440.0), color(c.accent, 0.6))
            val panel = RoundRectangle2D.Double(640.0, 120.0, 720.0, 440.0, 0.0, 0.0)
            b.add(Item.BoxShadow(panel, 14.0, 40.0, 0.4))
            b.fill(panel, color(c.panel))
        }
        val x = 680.0
        val max = 560.0
        var y = 150.0
        if (kind.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.EXO_ITALIC, 46.0, kind, max, spacing = 2.0)
            y += (b.tag(kind, c.text, font, c.second, x, y, Pad(8.0, 20.0, 10.0, 20.0))?.height ?: 0.0) + 10
        }
        val rows = ThumbnailRenderer.rows(head)
        val fonts = rowFonts(rows, ThumbnailFace.EXO_ITALIC, listOf(186.0, 132.0), max)
        rows.forEachIndexed { index, row ->
            val font = fonts[index]
            b.line(row, font, if (index == 0) c.accent else c.text, x, y, 0.95 * font.size2D)
            y += 0.95 * font.size2D
        }
        val runs = versus(c.onCard, c.second)
        if (runs.isNotEmpty()) {
            val font = b.fitRuns(runs, ThumbnailFace.EXO_ITALIC, 40.0, 900.0)
            b.tag(runs, font, c.card, W - 40, 562.0, Pad(10.0, 24.0, 12.0, 24.0), Align.RIGHT)
        }
        corner(ThumbnailFace.EXO_ITALIC)
    }

    /** 5.2 Rank number: a very large "#1", the large words, and the teams. */
    private fun Ctx.rank() {
        b.fill(full(), cssLinear(90.0, 0.3f to b.shade(0.0), 0.68f to b.shade(0.78)))
        val number = face(ThumbnailFace.EXO_ITALIC, 400.0, "#1", -10.0)
        b.line("#1", number, c.accent, W - 70, -44.0, 400.0, Align.RIGHT, drop = TextDrop(c.panel, 12.0, 12.0))
        val rx = 1228.0
        var y = 352.0
        val shadow = b.textShadow(0.6, 5.0, 18.0)
        val rows = ThumbnailRenderer.rows(head)
        rowFonts(rows, ThumbnailFace.EXO_ITALIC, listOf(98.0), 700.0).forEachIndexed { index, font ->
            b.line(rows[index], font, c.text, rx, y, 0.95 * font.size2D, Align.RIGHT, shadow)
            y += 0.95 * font.size2D
        }
        val runs = versus(c.text, c.accent)
        if (runs.isNotEmpty()) {
            val font = b.fitRuns(runs, ThumbnailFace.EXO_ITALIC, 40.0, 700.0)
            b.tag(runs, font, c.panel, rx, y + 16, Pad(10.0, 22.0, 12.0, 22.0), Align.RIGHT)
        }
        corner(ThumbnailFace.EXO_ITALIC)
    }

    /** 5.3 Score bug XL: a TV score box with the teams and the sets, the large words on top, the event under it. */
    private fun Ctx.scoreBug() {
        b.fill(full(), cssLinear(90.0, 0.35f to b.shade(0.0), 0.7f to b.shade(0.55)))
        val left = 492.0
        val w = 740.0
        val top = 96.0
        val sets = sets(raw(ThumbnailField.SCORE))
        val names = listOf(up(ThumbnailField.TEAM1), up(ThumbnailField.TEAM2))
        val shown = names.indices.filter { names[it].isNotEmpty() }
        val info = joined(ThumbnailField.EVENT, ThumbnailField.ROUND)
        val headFont = head.takeIf { it.isNotEmpty() }?.let { b.fitted(ThumbnailFace.EXO_ITALIC, 56.0, it, w - 48) }
        val headH = headFont?.let { 12 + it.size2D + 14.0 } ?: 0.0
        val rowsH = shown.size * 124.0 + (if (shown.size == 2) 4.0 else 0.0)
        val infoFont = info.takeIf { it.isNotEmpty() }?.let { b.fitted(ThumbnailFace.EXO_ITALIC, 38.0, it, w - 48, spacing = 1.0) }
        val infoH = infoFont?.let { 12 + it.size2D + 14.0 } ?: 0.0
        if (headH + rowsH + infoH <= 0) return corner(ThumbnailFace.EXO_ITALIC)
        b.add(Item.BoxShadow(RoundRectangle2D.Double(left, top, w, headH + rowsH + infoH, 0.0, 0.0), 16.0, 44.0, 0.5))
        var y = top
        if (headFont != null) {
            b.tag(head, c.onAccent, headFont, c.accent, left, y, Pad(12.0, 24.0, 14.0, 24.0))
            y += headH
        }
        val won = sets.map { (a, z) -> (a.toIntOrNull() ?: 0).compareTo(z.toIntOrNull() ?: 0) }
        val winner = when {
            won.count { it > 0 } > won.count { it < 0 } -> 0
            won.count { it < 0 } > won.count { it > 0 } -> 1
            else -> 0
        }
        val cellsLeft = left + w - sets.size * 112.0
        shown.forEachIndexed { row, side ->
            val strong = side == winner
            b.fill(Rectangle2D.Double(left, y, w, 124.0), color(c.panel))
            b.fill(Rectangle2D.Double(left, y, 28.0, 124.0), color(if (strong) c.accent else c.panel))
            val font = b.fitted(ThumbnailFace.EXO_ITALIC, 58.0, names[side], cellsLeft - left - 52 - 16)
            b.line(names[side], font, c.text, left + 52, y + (124 - font.size2D) / 2, font.size2D.toDouble(), alpha = if (strong) 1.0 else 0.75)
            sets.forEachIndexed { index, set ->
                val cell = Rectangle2D.Double(cellsLeft + index * 112, y, 112.0, 124.0)
                b.fill(cell, color(if (strong) c.card else blend(c.card, c.onCard, 0.08)))
                if (index > 0) b.fill(Rectangle2D.Double(cell.x, y, 4.0, 124.0), color(c.cardLine))
                val digits = if (side == 0) set.first else set.second
                val digitFont = b.fitted(ThumbnailFace.EXO_ITALIC, 92.0, digits, 100.0)
                b.line(digits, digitFont, if (strong) c.onCard else blend(c.card, c.onCard, 0.68), cell.centerX + 2, y + 16, 92.0, Align.CENTER)
            }
            y += 124
            if (row == 0 && shown.size == 2) {
                b.fill(Rectangle2D.Double(left, y, w, 4.0), color(blend(c.panel, c.text, 0.1)))
                y += 4
            }
        }
        if (infoFont != null) {
            b.fill(Rectangle2D.Double(left, y, w, infoH), color(c.second))
            b.line(info, infoFont, c.text, left + 24, y + 12, infoFont.size2D.toDouble())
        }
        corner(ThumbnailFace.EXO_ITALIC)
    }

    /** 5.4 Lower third XL: a stack of TV boxes at the right: the round, the two teams, and the event with the court. */
    private fun Ctx.lowerThird() {
        b.fill(full(), cssLinear(0.0, 0f to b.shade(0.6), 0.5f to b.shade(0.0)))
        val rx = 1232.0
        val max = 760.0
        var y = 252.0
        if (head.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.EXO_ITALIC, 54.0, head, max)
            y += b.tag(head, c.onAccent, font, c.accent, rx, y, Pad(10.0, 24.0, 12.0, 24.0), Align.RIGHT)?.height ?: 0.0
        }
        val team1 = up(ThumbnailField.TEAM1)
        val team2 = up(ThumbnailField.TEAM2)
        if (team1.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.EXO_ITALIC, 92.0, team1, max - 56)
            y += b.tag(team1, c.text, font, c.panel, rx, y, Pad(12.0, 28.0, 14.0, 28.0), Align.RIGHT)?.height ?: 0.0
        }
        if (team2.isNotEmpty()) {
            val vs = face(ThumbnailFace.EXO_ITALIC, 60.0, "vs")
            val runs = listOfNotNull(Run("vs ", c.accent, font = vs).takeIf { team1.isNotEmpty() }, Run(team2, c.text))
            val font = b.fitRuns(runs, ThumbnailFace.EXO_ITALIC, 92.0, max - 56)
            y += b.tag(runs, font, c.panel, rx, y, Pad(if (team1.isNotEmpty()) 0.0 else 12.0, 28.0, 14.0, 28.0), Align.RIGHT).height
        }
        val info = joined(ThumbnailField.EVENT, ThumbnailField.COURT)
        if (info.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.EXO_ITALIC, 40.0, info, max - 56, spacing = 1.0)
            b.tag(info, c.onCard, font, c.card, rx, y, Pad(12.0, 28.0, 14.0, 28.0), Align.RIGHT)
        }
        corner(ThumbnailFace.EXO_ITALIC)
    }

    /** 5.5 Alert: the large words on a skewed red box, an accent bar with the round, and the teams. */
    private fun Ctx.alert() {
        val rx = 1240.0
        var y = 70.0
        val rows = ThumbnailRenderer.rows(head)
        val fonts = rowFonts(rows, ThumbnailFace.EXO_ITALIC, listOf(186.0), 760.0)
        if (rows.isNotEmpty()) {
            val boxW = rows.indices.maxOf { width(rows[it], fonts[it]) } + 80
            val boxH = 14 + fonts.sumOf { 0.9 * it.size2D } + 22
            val box = Rectangle2D.Double(rx - boxW, y, boxW, boxH)
            b.group(skewX(-10.0, box.centerX, box.centerY)) {
                b.fill(Rectangle2D.Double(box.x + 14, box.y + 14, box.width, box.height), color(c.panel))
                b.fill(box, color(c.second))
            }
            var rowTop = y + 14
            rows.forEachIndexed { index, row ->
                val font = fonts[index]
                val line = 0.9 * font.size2D
                // The box is skewed, and each row is not: the row moves with the skew of its middle.
                val shift = tan(Math.toRadians(-10.0)) * (rowTop + line / 2 - box.centerY)
                b.line(row, font, c.text, rx - 40 + shift, rowTop, line, Align.RIGHT)
                rowTop += line
            }
            y += boxH + 30
        }
        val info = joined(ThumbnailField.ROUND, ThumbnailField.KIND)
        if (info.isNotEmpty()) {
            val font = b.fitted(ThumbnailFace.EXO_ITALIC, 46.0, info, 700.0)
            val textW = width(info, font)
            val w = 28 + 64 + 18 + textW + 28
            val h = 12 + font.size2D + 14.0
            val left = rx - 24 - w
            b.fill(Rectangle2D.Double(left, y, w, h), color(c.accent))
            val iconTop = y + (h - 40) / 2
            b.fill(polygon("${left + 28},$iconTop ${left + 52},${iconTop + 20} ${left + 28},${iconTop + 40}"), color(c.onAccent))
            b.fill(polygon("${left + 56},$iconTop ${left + 80},${iconTop + 20} ${left + 56},${iconTop + 40}"), color(c.onAccent))
            b.line(info, font, c.onAccent, left + 28 + 64 + 18, y + 12, font.size2D.toDouble())
            y += h + 10
        }
        val runs = versus(c.text, c.accent)
        if (runs.isNotEmpty()) {
            val font = b.fitRuns(runs, ThumbnailFace.EXO_ITALIC, 42.0, 700.0)
            b.tag(runs, font, c.panel, rx - 24, y, Pad(10.0, 26.0, 12.0, 26.0), Align.RIGHT)
        }
        corner(ThumbnailFace.EXO_ITALIC)
    }
}
