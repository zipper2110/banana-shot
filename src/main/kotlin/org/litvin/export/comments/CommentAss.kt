package org.litvin.export.comments

import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import java.awt.geom.Rectangle2D
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Converts a comment to ASS event texts (the Text field of a Dialogue line) in the lower third of the frame.
 * [CommentStyle] selects the look: an outlined text, a rounded card, or a pill for each line.
 *
 * The export writes these texts into the ASS file for FFmpeg. The Points and Scoring previews send the same
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
    ): List<String> {
        if (text.isBlank() || frameWidth <= 0.0 || frameHeight <= 0.0) return emptyList()
        val scale = (frameHeight / 1080.0).coerceAtLeast(0.25)
        val fontSize = (66 * scale).coerceIn(24.0, 128.0)
        val rgb = colorHex.removePrefix("#").toIntOrNull(16) ?: LIGHT
        val layout = Layout(scale, fontSize, frameX + frameWidth / 2, frameY + frameHeight * 0.82)
        val maxTextWidth = frameWidth * 0.86 - 2 * layout.padX(style)
        val lines = wrapLines(text, maxTextWidth.coerceAtLeast(fontSize)) { measure(it, fontSize) }
        return when (style) {
            CommentStyle.OUTLINE -> outline(lines, rgb, layout)
            CommentStyle.CARD -> card(lines, rgb, layout)
            CommentStyle.PILL -> pills(lines, rgb, layout)
        }
    }

    /** The comments on screen at [timeMs]: from the start of each comment to the end of its duration. */
    fun activeAt(comments: List<CommentV1>, timeMs: Long): List<CommentV1> = comments.filter { comment ->
        comment.text.isNotBlank() && comment.startMs <= timeMs && timeMs < comment.startMs.toLong() + comment.durationMs
    }

    /** The events of all [comments] in the video area of a preview, in drawing order. */
    fun previewEvents(comments: List<CommentV1>, videoArea: Rectangle2D.Double): List<String> = comments.flatMap { comment ->
        events(comment.text, comment.colorHex, videoArea.x, videoArea.y, videoArea.width, videoArea.height, comment.style)
    }

    /**
     * Black (a near-black) or white, whichever has the higher contrast with [rgb] (WCAG relative luminance).
     * The text on a pill, the card under a text and the outline of a text use it.
     */
    internal fun contrastColor(rgb: Int): Int = if (isLight(rgb)) DARK else LIGHT

    /** True when black has a higher contrast with [rgb] than white has. */
    internal fun isLight(rgb: Int): Boolean = luminance(rgb) > LUMINANCE_THRESHOLD

    /** The sizes of one comment, in frame pixels. [bottom] is the lower edge of the comment. */
    private class Layout(val scale: Double, val fontSize: Double, val centerX: Double, val bottom: Double) {
        val lineHeight = fontSize // libass sets the font size to the height of the line box.
        fun padX(style: CommentStyle): Double = when (style) {
            CommentStyle.OUTLINE -> 0.0
            CommentStyle.CARD -> 46 * scale
            CommentStyle.PILL -> 34 * scale
        }
    }

    private fun outline(lines: List<String>, rgb: Int, layout: Layout): List<String> {
        val edge = contrastColor(rgb)
        // A dark outline gets a dark drop shadow. A light outline gets no shadow, because a light shadow looks like a smear.
        val shadow = if (edge == DARK) "\\shad${fmt(4 * layout.scale)}\\4c${ScoreboardAss.color(0x000000)}\\4a${ScoreboardAss.alpha(0.56)}" else "\\shad0"
        val tags = "\\bord${fmt(5 * layout.scale)}\\3c${ScoreboardAss.color(edge)}\\3a&H00&$shadow\\blur${fmt(layout.scale)}"
        return listOf(text(layout.centerX, layout.bottom, lines, rgb, layout.fontSize, tags))
    }

    private fun card(lines: List<String>, rgb: Int, layout: Layout): List<String> {
        val padX = layout.padX(CommentStyle.CARD)
        val padY = 24 * layout.scale
        val width = (lines.maxOf { measure(it, layout.fontSize) } + 2 * padX).roundToInt()
        val height = (lines.size * layout.lineHeight + 2 * padY).roundToInt()
        val left = layout.centerX - width / 2.0
        val top = layout.bottom - height
        val path = roundedRect(width, height, (22 * layout.scale).roundToInt())
        val cardColor = if (isLight(rgb)) DARK_CARD else LIGHT_CARD
        return listOf(
            shadow(left, top + 8 * layout.scale, path, 14 * layout.scale),
            shape(left, top, path, cardColor, ScoreboardAss.alpha(CARD_OPACITY)),
            text(layout.centerX, layout.bottom - padY, lines, rgb, layout.fontSize, NO_EDGE),
        )
    }

    private fun pills(lines: List<String>, rgb: Int, layout: Layout): List<String> {
        val padX = layout.padX(CommentStyle.PILL)
        val padY = 12 * layout.scale
        val gap = 10 * layout.scale
        val pillHeight = (layout.lineHeight + 2 * padY).roundToInt()
        val shadows = mutableListOf<String>()
        val pills = mutableListOf<String>()
        val texts = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val bottom = layout.bottom - (lines.size - 1 - index) * (pillHeight + gap)
            if (line.isBlank()) return@forEachIndexed
            val width = (measure(line, layout.fontSize) + 2 * padX).roundToInt()
            val left = layout.centerX - width / 2.0
            val top = bottom - pillHeight
            val path = roundedRect(width, pillHeight, pillHeight / 2)
            shadows += shadow(left, top + 6 * layout.scale, path, 10 * layout.scale)
            pills += shape(left, top, path, rgb, "&H00&")
            texts += text(layout.centerX, bottom - padY, listOf(line), contrastColor(rgb), layout.fontSize, NO_EDGE)
        }
        return shadows + pills + texts
    }

    private const val NO_EDGE = "\\bord0\\shad0\\blur0"

    private fun text(x: Double, y: Double, lines: List<String>, rgb: Int, fontSize: Double, edgeTags: String): String = buildString {
        append("{\\an2\\pos(${fmt(x)},${fmt(y)})\\q2")
        append("\\fn$FONT\\fs${fmt(fontSize)}\\b1\\i0\\u0\\s0\\fsp0\\fscx100\\fscy100\\frz0")
        append("\\1c${ScoreboardAss.color(rgb)}\\1a&H00&$edgeTags}")
        append(lines.joinToString("\\N") { escape(it) })
    }

    /** A filled path. The path starts at (0, 0) of its bounds, so \an7\pos places the top-left corner exactly. */
    private fun shape(left: Double, top: Double, path: String, rgb: Int, alpha: String, blur: Double = 0.0): String =
        "{\\an7\\pos(${fmt(left)},${fmt(top)})\\bord0\\shad0\\blur${fmt(blur)}\\fscx100\\fscy100\\frz0" +
            "\\1c${ScoreboardAss.color(rgb)}\\1a$alpha\\p1}$path{\\p0}"

    private fun shadow(left: Double, top: Double, path: String, blur: Double): String =
        shape(left, top, path, 0x000000, ScoreboardAss.alpha(SHADOW_OPACITY), blur)

    /** A rectangle with rounded corners, as an ASS drawing. Each corner is a Bezier curve. */
    internal fun roundedRect(width: Int, height: Int, radius: Int): String {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val r = radius.coerceIn(0, minOf(w, h) / 2)
        return "m $r 0 l ${w - r} 0 b $w 0 $w 0 $w $r l $w ${h - r} b $w $h $w $h ${w - r} $h " +
            "l $r $h b 0 $h 0 $h 0 ${h - r} l 0 $r b 0 0 0 0 $r 0"
    }

    private fun measure(text: String, fontSize: Double): Double = ScoreboardFonts.textWidth(text, FONT, true, fontSize)

    /** Wraps each line of [text] at spaces, so that no line is wider than [maxWidth]. A longer word breaks between letters. */
    internal fun wrapLines(text: String, maxWidth: Double, measure: (String) -> Double): List<String> =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').flatMap { line ->
            wrapLine(line, maxWidth, measure)
        }

    private fun wrapLine(line: String, maxWidth: Double, measure: (String) -> Double): List<String> {
        val words = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
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
