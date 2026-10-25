package org.litvin.export

import org.litvin.export.comments.CommentAss
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardFonts
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The "REPLAY" badge of a replay (B-50) as ASS event texts. The badge is a lime box with dark bold text at the
 * upper right of the frame, as in the design mockup. The sizes are relative to the frame height.
 */
object ReplayBadgeAss {
    private const val FONT = "Arial"
    private const val BADGE_COLOR = 0xC8EC46
    private const val TEXT_COLOR = 0x142000

    /** The badge height, the space from the top, and the space from the right, as parts of the frame. */
    private const val HEIGHT_SHARE = 0.07
    private const val TOP_SHARE = 0.045
    private const val RIGHT_SHARE = 0.03

    /** The parts of the badge height for the font size, the side padding, and the corner radius. */
    private const val FONT_SHARE = 0.54
    private const val PAD_SHARE = 0.43
    private const val RADIUS_SHARE = 0.18

    /** The letter spacing as a part of the font size. */
    private const val SPACING_SHARE = 0.12

    /**
     * The events of the badge in a frame of [frameWidth] x [frameHeight] at ([frameX], [frameY]): first the box,
     * then the text. The export uses the whole output frame. The preview uses the video area of the player.
     */
    fun events(text: String, frameWidth: Double, frameHeight: Double, frameX: Double = 0.0, frameY: Double = 0.0): List<String> {
        if (text.isBlank()) return emptyList()
        val height = (frameHeight * HEIGHT_SHARE).roundToInt().coerceAtLeast(8)
        val fontSize = height * FONT_SHARE
        val spacing = fontSize * SPACING_SHARE
        val family = ScoreboardFonts.family(FONT, text)
        // The letter spacing also comes after the last letter, so the text loses one spacing at the right.
        val textWidth = ScoreboardFonts.textWidth(text, family, true, fontSize, spacing) - spacing
        val width = (textWidth + 2 * height * PAD_SHARE).roundToInt()
        val left = frameX + frameWidth * (1 - RIGHT_SHARE) - width
        val top = frameY + frameHeight * TOP_SHARE
        val box = "{\\an7\\pos(${fmt(left)},${fmt(top)})\\bord0\\shad0\\blur0\\fscx100\\fscy100\\frz0" +
            "\\1c${ScoreboardAss.color(BADGE_COLOR)}\\1a${ScoreboardAss.alpha(1.0)}\\p1}" +
            CommentAss.roundedRect(width, height, (height * RADIUS_SHARE).roundToInt()) + "{\\p0}"
        val centerY = ScoreboardFonts.middleYForCapCenter(top + height / 2.0, family, true, fontSize)
        val label = "{\\an5\\pos(${fmt(left + width / 2.0 + spacing / 2.0)},${fmt(centerY)})\\q2" +
            "\\fn$family\\fs${fmt(fontSize)}\\b1\\i0\\u0\\s0\\fsp${fmt(spacing)}\\fscx100\\fscy100\\frz0" +
            "\\1c${ScoreboardAss.color(TEXT_COLOR)}\\1a${ScoreboardAss.alpha(1.0)}\\bord0\\shad0\\blur0}" +
            escape(text)
        return listOf(box, label)
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}")
}
