package org.litvin.export.comments

import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.i18n.LineBreaks
import org.litvin.points.CommentPosition
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import java.awt.geom.Rectangle2D
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Converts a comment to ASS event texts (the Text field of a Dialogue line).
 * [CommentStyle] selects the look: an outlined text, a rounded card, or a pill for each line.
 * A comment without a [CommentPosition] is centered in the lower third of the frame. A comment with a position
 * moves inside the frame when its box does not fit at that position.
 * The size percent multiplies all sizes of the comment. The opacity percent multiplies the opacity of all its parts.
 * The rotation turns all parts of the comment around the center of its box. The shadows stay below the comment.
 * The bounding box of the rotated comment must fit in the frame.
 *
 * The export writes these texts into the ASS file for FFmpeg. The Points preview sends the same
 * texts to the mpv `osd-overlay` command. Both use libass, so a comment looks the same in the preview and the export.
 * Every event sets all of its override tags, so the event style has no effect on the result.
 */
object CommentAss {
    private const val FONT = "Arial"

    /** The shadows under the card and the pills are 56% transparent black. */
    private const val SHADOW_OPACITY = 0.44

    /** The card is 85% opaque, so the video shows through a little. */
    private const val CARD_OPACITY = 0.85

    private const val DARK = 0x141414
    private const val LIGHT = 0xFFFFFF
    private const val DARK_CARD = 0x14161A
    private const val LIGHT_CARD = 0xF4F4F4

    /**
     * Returns the events of a comment, in drawing order: first the shadows, then the backdrops, then the texts.
     * The frame is the video area: the whole output frame for the export, or the video area of the preview window.
     * A blank text gives no events.
     */
    fun events(
        text: String,
        colorHex: String,
        frameX: Double,
        frameY: Double,
        frameWidth: Double,
        frameHeight: Double,
        style: CommentStyle = CommentStyle.OUTLINE,
        position: CommentPosition? = null,
        sizePercent: Int = CommentV1.DEFAULT_SIZE_PERCENT,
        opacityPercent: Int = 100,
        rotationDeg: Int = 0,
    ): List<String> {
        val placed = place(text, frameX, frameY, frameWidth, frameHeight, style, position, sizePercent, opacityPercent, rotationDeg)
            ?: return emptyList()
        val rgb = colorHex.removePrefix("#").toIntOrNull(16) ?: LIGHT
        return when (style) {
            CommentStyle.OUTLINE -> outline(placed.lines, rgb, placed.layout)
            CommentStyle.CARD -> card(placed.lines, rgb, placed.layout)
            CommentStyle.PILL -> pills(placed.lines, rgb, placed.layout)
        }
    }

    /**
     * Where [events] draws a comment in the frame: the box of the comment and the position after the move into the
     * frame. The box is the card, the pills, or the outlined text, without the shadows. A blank text gives null.
     * The box is not rotated: the comment turns by [rotationDeg] around the center of the box.
     */
    fun placement(
        text: String,
        frameX: Double,
        frameY: Double,
        frameWidth: Double,
        frameHeight: Double,
        style: CommentStyle = CommentStyle.OUTLINE,
        position: CommentPosition? = null,
        sizePercent: Int = CommentV1.DEFAULT_SIZE_PERCENT,
        rotationDeg: Int = 0,
    ): CommentPlacement? {
        val placed = place(text, frameX, frameY, frameWidth, frameHeight, style, position, sizePercent, 100, rotationDeg)
            ?: return null
        val wanted = position ?: CommentPosition.DEFAULT
        // A value that did not move keeps its exact fraction, so the default position stays equal to DEFAULT.
        val x = if (placed.layout.centerX == frameX + frameWidth * wanted.x) wanted.x else (placed.layout.centerX - frameX) / frameWidth
        val y = if (placed.layout.bottom == frameY + frameHeight * wanted.y) wanted.y else (placed.layout.bottom - frameY) / frameHeight
        return CommentPlacement(placed.box, CommentPosition(x, y))
    }

    /** The placement of [comment] in the video area of a preview. */
    fun placement(comment: CommentV1, videoArea: Rectangle2D.Double): CommentPlacement? =
        placement(
            comment.text, videoArea.x, videoArea.y, videoArea.width, videoArea.height, comment.style, comment.position,
            comment.sizePercent, comment.rotationDeg,
        )

    private class Placed(val lines: List<String>, val layout: Layout, val box: Rectangle2D.Double)

    private fun place(
        text: String,
        frameX: Double,
        frameY: Double,
        frameWidth: Double,
        frameHeight: Double,
        style: CommentStyle,
        position: CommentPosition?,
        sizePercent: Int,
        opacityPercent: Int,
        rotationDeg: Int,
    ): Placed? {
        if (text.isBlank() || frameWidth <= 0.0 || frameHeight <= 0.0) return null
        val size = sizePercent.coerceIn(CommentV1.MIN_SIZE_PERCENT, CommentV1.MAX_SIZE_PERCENT) / 100.0
        val opacity = opacityPercent.coerceIn(CommentV1.MIN_OPACITY_PERCENT, 100) / 100.0
        val frameScale = (frameHeight / 1080.0).coerceAtLeast(0.25)
        val scale = frameScale * size
        val fontSize = (66 * frameScale).coerceIn(24.0, 128.0) * size
        // A Japanese comment uses the Japanese font (B-11 E8-T2).
        val family = ScoreboardFonts.family(FONT, text)
        val sizes = Layout(scale, fontSize, family, opacity, 0.0, 0.0, 0.0, 0)
        val maxTextWidth = frameWidth * 0.86 - 2 * sizes.padX(style)
        val lines = wrapLines(text, maxTextWidth.coerceAtLeast(fontSize)) { measure(it, sizes) }
        val box = boxSize(lines, style, sizes)
        val wanted = position ?: CommentPosition.DEFAULT
        // The comment moves inside the frame. The bounding box of the rotated box must fit. A comment at the default
        // position without a rotation fits, so it does not move.
        val angle = Math.toRadians(rotationDeg.toDouble())
        val cosine = abs(cos(angle))
        val sine = abs(sin(angle))
        val halfWidth = box.width / 2 * cosine + box.height / 2 * sine
        val halfHeight = box.width / 2 * sine + box.height / 2 * cosine
        val centerX = fit(frameX + frameWidth * wanted.x, frameX + halfWidth, frameX + frameWidth - halfWidth)
        val wantedBottom = frameY + frameHeight * wanted.y
        // The center of the box is above the lower edge of the text by half the box height, less the part below.
        val toCenter = box.below - box.height / 2
        val wantedCenterY = wantedBottom + toCenter
        val centerY = fit(wantedCenterY, frameY + halfHeight, frameY + frameHeight - halfHeight)
        val bottom = if (centerY == wantedCenterY) wantedBottom else centerY - toCenter
        val layout = Layout(scale, fontSize, family, opacity, centerX, bottom, bottom + toCenter, rotationDeg)
        val bounds = Rectangle2D.Double(centerX - box.width / 2, bottom + box.below - box.height, box.width, box.height)
        return Placed(lines, layout, bounds)
    }

    /** The size of a comment box. [below] is the part of the box under the lower edge of the text. */
    private class BoxSize(val width: Double, val height: Double, val below: Double)

    private fun boxSize(lines: List<String>, style: CommentStyle, layout: Layout): BoxSize = when (style) {
        CommentStyle.OUTLINE -> {
            val edge = outlineWidth(layout)
            BoxSize(lines.maxOf { measure(it, layout) } + 2 * edge, lines.size * layout.lineHeight + 2 * edge, edge)
        }
        CommentStyle.CARD -> {
            val padX = layout.padX(CommentStyle.CARD)
            val width = (lines.maxOf { measure(it, layout) } + 2 * padX).roundToInt()
            val height = (lines.size * layout.lineHeight + 2 * cardPadY(layout)).roundToInt()
            BoxSize(width.toDouble(), height.toDouble(), 0.0)
        }
        CommentStyle.PILL -> {
            val padX = layout.padX(CommentStyle.PILL)
            val width = lines.filter { it.isNotBlank() }.maxOfOrNull { (measure(it, layout) + 2 * padX).roundToInt() } ?: 0
            BoxSize(width.toDouble(), lines.size * pillHeight(layout) + (lines.size - 1) * pillGap(layout), 0.0)
        }
    }

    /** [value] limited to [min]..[max]. An empty range means that the box is larger than the frame: the box centers. */
    private fun fit(value: Double, min: Double, max: Double): Double = if (min > max) (min + max) / 2 else value.coerceIn(min, max)

    /** The comments on screen at [timeMs]: from the start of each comment to the end of its duration. */
    fun activeAt(comments: List<CommentV1>, timeMs: Long): List<CommentV1> = comments.filter { comment ->
        comment.text.isNotBlank() && comment.startMs <= timeMs && timeMs < comment.startMs.toLong() + comment.durationMs
    }

    /** The events of all [comments] in the video area of a preview, in drawing order. */
    fun previewEvents(comments: List<CommentV1>, videoArea: Rectangle2D.Double): List<String> = comments.flatMap { comment ->
        events(
            comment.text, comment.colorHex, videoArea.x, videoArea.y, videoArea.width, videoArea.height, comment.style,
            comment.position, comment.sizePercent, comment.opacityPercent, comment.rotationDeg,
        )
    }

    /**
     * Black (a near-black) or white, whichever has the higher contrast with [rgb] (WCAG relative luminance).
     * The text on a pill, the card under a text and the outline of a text use it.
     */
    internal fun contrastColor(rgb: Int): Int = if (isLight(rgb)) DARK else LIGHT

    /** True when black has a higher contrast with [rgb] than white has. */
    internal fun isLight(rgb: Int): Boolean = luminance(rgb) > LUMINANCE_THRESHOLD

    /**
     * The sizes of one comment, in frame pixels. [bottom] is the lower edge of the comment. [opacity] is 0 to 1.
     * The comment turns by [rotationDeg] around ([centerX], [centerY]), the center of its box.
     */
    private class Layout(
        val scale: Double,
        val fontSize: Double,
        val family: String,
        val opacity: Double,
        val centerX: Double,
        val bottom: Double,
        val centerY: Double,
        val rotationDeg: Int,
    ) {
        val lineHeight = fontSize // libass sets the font size to the height of the line box.

        /**
         * The rotation tags of an event. A shadow that is [shadowOffset] below its shape turns around a center that
         * is the same distance below, so the shadow stays below the comment.
         * ASS turns counterclockwise for a positive \frz, so the angle changes its sign.
         */
        fun turn(shadowOffset: Double = 0.0): String =
            if (rotationDeg == 0) "\\frz0" else "\\org(${fmt(centerX)},${fmt(centerY + shadowOffset)})\\frz${-rotationDeg}"

        fun padX(style: CommentStyle): Double = when (style) {
            CommentStyle.OUTLINE -> 0.0
            CommentStyle.CARD -> 46 * scale
            CommentStyle.PILL -> 34 * scale
        }
    }

    private fun outline(lines: List<String>, rgb: Int, layout: Layout): List<String> {
        val edge = contrastColor(rgb)
        // A dark outline gets a dark drop shadow. A light outline gets no shadow, because a light shadow looks like a smear.
        val shadow = if (edge == DARK) "\\shad${fmt(4 * layout.scale)}\\4c${ScoreboardAss.color(0x000000)}\\4a${ScoreboardAss.alpha(0.56 * layout.opacity)}" else "\\shad0"
        val tags = "\\bord${fmt(outlineWidth(layout))}\\3c${ScoreboardAss.color(edge)}\\3a${ScoreboardAss.alpha(layout.opacity)}$shadow\\blur${fmt(layout.scale)}"
        return listOf(text(layout.centerX, layout.bottom, lines, rgb, layout, tags))
    }

    private fun card(lines: List<String>, rgb: Int, layout: Layout): List<String> {
        val padX = layout.padX(CommentStyle.CARD)
        val padY = cardPadY(layout)
        val width = (lines.maxOf { measure(it, layout) } + 2 * padX).roundToInt()
        val height = (lines.size * layout.lineHeight + 2 * padY).roundToInt()
        val left = layout.centerX - width / 2.0
        val top = layout.bottom - height
        val path = roundedRect(width, height, (22 * layout.scale).roundToInt())
        val cardColor = if (isLight(rgb)) DARK_CARD else LIGHT_CARD
        return listOf(
            shadow(left, top, 8 * layout.scale, path, 14 * layout.scale, layout),
            shape(left, top, path, cardColor, ScoreboardAss.alpha(CARD_OPACITY * layout.opacity), layout.turn()),
            text(layout.centerX, layout.bottom - padY, lines, rgb, layout, NO_EDGE),
        )
    }

    private fun pills(lines: List<String>, rgb: Int, layout: Layout): List<String> {
        val padX = layout.padX(CommentStyle.PILL)
        val padY = PILL_PAD_Y * layout.scale
        val gap = pillGap(layout)
        val pillHeight = pillHeight(layout).roundToInt()
        val shadows = mutableListOf<String>()
        val pills = mutableListOf<String>()
        val texts = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val bottom = layout.bottom - (lines.size - 1 - index) * (pillHeight + gap)
            if (line.isBlank()) return@forEachIndexed
            val width = (measure(line, layout) + 2 * padX).roundToInt()
            val left = layout.centerX - width / 2.0
            val top = bottom - pillHeight
            val path = roundedRect(width, pillHeight, pillHeight / 2)
            shadows += shadow(left, top, 6 * layout.scale, path, 10 * layout.scale, layout)
            pills += shape(left, top, path, rgb, ScoreboardAss.alpha(layout.opacity), layout.turn())
            texts += text(layout.centerX, bottom - padY, listOf(line), contrastColor(rgb), layout, NO_EDGE)
        }
        return shadows + pills + texts
    }

    private const val NO_EDGE = "\\bord0\\shad0\\blur0"
    private const val PILL_PAD_Y = 12

    private fun outlineWidth(layout: Layout): Double = 5 * layout.scale
    private fun cardPadY(layout: Layout): Double = 24 * layout.scale
    private fun pillGap(layout: Layout): Double = 10 * layout.scale
    private fun pillHeight(layout: Layout): Double = (layout.lineHeight + 2 * PILL_PAD_Y * layout.scale).roundToInt().toDouble()

    private fun text(x: Double, y: Double, lines: List<String>, rgb: Int, layout: Layout, edgeTags: String): String = buildString {
        append("{\\an2\\pos(${fmt(x)},${fmt(y)})\\q2")
        append("\\fn${layout.family}\\fs${fmt(layout.fontSize)}\\b1\\i0\\u0\\s0\\fsp0\\fscx100\\fscy100${layout.turn()}")
        append("\\1c${ScoreboardAss.color(rgb)}\\1a${ScoreboardAss.alpha(layout.opacity)}$edgeTags}")
        append(lines.joinToString("\\N") { escape(it) })
    }

    /** A filled path. The path starts at (0, 0) of its bounds, so \an7\pos places the top-left corner exactly. */
    private fun shape(left: Double, top: Double, path: String, rgb: Int, alpha: String, turn: String, blur: Double = 0.0): String =
        "{\\an7\\pos(${fmt(left)},${fmt(top)})\\bord0\\shad0\\blur${fmt(blur)}\\fscx100\\fscy100$turn" +
            "\\1c${ScoreboardAss.color(rgb)}\\1a$alpha\\p1}$path{\\p0}"

    /** The shadow of the shape at ([left], [top]). It is [offset] below the shape, also when the comment turns. */
    private fun shadow(left: Double, top: Double, offset: Double, path: String, blur: Double, layout: Layout): String =
        shape(left, top + offset, path, 0x000000, ScoreboardAss.alpha(SHADOW_OPACITY * layout.opacity), layout.turn(offset), blur)

    /** A rectangle with rounded corners, as an ASS drawing. Each corner is a Bezier curve. */
    internal fun roundedRect(width: Int, height: Int, radius: Int): String {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val r = radius.coerceIn(0, minOf(w, h) / 2)
        return "m $r 0 l ${w - r} 0 b $w 0 $w 0 $w $r l $w ${h - r} b $w $h $w $h ${w - r} $h " +
            "l $r $h b 0 $h 0 $h 0 ${h - r} l 0 $r b 0 0 0 0 $r 0"
    }

    private fun measure(text: String, layout: Layout): Double = ScoreboardFonts.textWidth(text, layout.family, true, layout.fontSize)

    /**
     * Wraps each line of [text] at spaces, and between the characters of a Japanese text ([LineBreaks]), so that no
     * line is wider than [maxWidth]. A longer word breaks between letters.
     */
    internal fun wrapLines(text: String, maxWidth: Double, measure: (String) -> Double): List<String> =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').flatMap { line ->
            wrapLine(line, maxWidth, measure)
        }

    private fun wrapLine(line: String, maxWidth: Double, measure: (String) -> Double): List<String> {
        val words = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        // The pieces of a Japanese word join without a space.
        val pieces = words.flatMap { word -> LineBreaks.pieces(word).mapIndexed { index, piece -> (index == 0) to piece } }
        for ((wordStart, word) in pieces) {
            val candidate = if (current.isEmpty()) word else if (wordStart) "$current $word" else "$current$word"
            if (measure(candidate) <= maxWidth) {
                current = candidate
                continue
            }
            if (current.isNotEmpty()) lines += current
            current = word
            while (current.length > 1 && measure(current) > maxWidth) {
                val fit = (1 until current.length).lastOrNull { measure(current.take(it)) <= maxWidth } ?: 1
                lines += current.take(fit)
                current = current.drop(fit)
            }
        }
        lines += current
        return lines
    }

    private fun luminance(rgb: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((rgb shr 16) and 0xFF) + 0.7152 * channel((rgb shr 8) and 0xFF) + 0.0722 * channel(rgb and 0xFF)
    }

    /** At this luminance, black and white have the same contrast with the color: sqrt(1.05 × 0.05) − 0.05. */
    private const val LUMINANCE_THRESHOLD = 0.1791

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
}

/** Where a comment is drawn: its [box] in frame pixels and its [position] after the move into the frame. */
data class CommentPlacement(val box: Rectangle2D.Double, val position: CommentPosition)

/**
 * The comments that a preview shows at the playhead. A tab gives it the comments of the project and each new
 * playhead time. The tab sends a new overlay to the player only when the comments on screen change.
 */
class CommentPreview {
    private var comments: List<CommentV1> = emptyList()
    private var timeMs: Long = 0L

    /** The comments on screen now. */
    var shown: List<CommentV1> = emptyList()
        private set

    /** Sets the comments of the project. Returns true when the comments on screen changed. */
    fun setComments(comments: List<CommentV1>): Boolean {
        this.comments = comments
        return update()
    }

    /** Moves the playhead to [timeMs]. Returns true when the comments on screen changed. */
    fun moveTo(timeMs: Long): Boolean {
        this.timeMs = timeMs
        return update()
    }

    private fun update(): Boolean {
        val now = CommentAss.activeAt(comments, timeMs)
        if (now == shown) return false
        shown = now
        return true
    }
}
