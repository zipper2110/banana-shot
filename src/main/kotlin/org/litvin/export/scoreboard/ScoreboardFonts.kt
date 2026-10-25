package org.litvin.export.scoreboard

import org.litvin.i18n.I18n
import org.litvin.i18n.ScriptFonts
import java.awt.Font
import java.awt.font.FontRenderContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts libass font sizes to AWT fonts and measures text.
 *
 * libass sets the font size to the height of the line box (ascent + descent).
 * AWT sets the font size to the em size. [awtFont] converts between the two, so that the
 * scoreboard layout and the Java2D thumbnails use the same text sizes as libass.
 *
 * The fonts of the styles have no Japanese letters. [family] gives the font of the script for such a text (B-11
 * E8-T2), and [textWidth] measures the text in that font. Thus the export, the preview, and the Java2D images use
 * the same font.
 */
object ScoreboardFonts {
    private val frc = FontRenderContext(null, true, true)
    private val metricsCache = ConcurrentHashMap<Pair<String, Boolean>, Metrics>()
    private val displayCache = ConcurrentHashMap<String, Font>()

    private data class Metrics(val lineHeightPerEm: Double, val ascentShare: Double, val capHeightShare: Double)

    fun awtFont(family: String, bold: Boolean, assSize: Double): Font {
        val metrics = metrics(family, bold)
        return Font(family, if (bold) Font.BOLD else Font.PLAIN, 1)
            .deriveFont((assSize / metrics.lineHeightPerEm).toFloat())
    }

    /** The part of the line box above the baseline, 0.0 to 1.0. */
    fun ascentShare(family: String, bold: Boolean): Double = metrics(family, bold).ascentShare

    /**
     * Returns the y value for a middle-anchored label, so that the capital letters and digits
     * are centered on [capCenterY]. The middle of the line box is lower than the middle of the capitals.
     */
    fun middleYForCapCenter(capCenterY: Double, family: String, bold: Boolean, assSize: Double): Double {
        val metrics = metrics(family, bold)
        val capCenterFromTop = (metrics.ascentShare - metrics.capHeightShare / 2.0) * assSize
        return capCenterY - (capCenterFromTop - assSize / 2.0)
    }

    /** The height of the capital letters and digits for the libass size [assSize]. */
    fun capHeight(family: String, bold: Boolean, assSize: Double): Double = metrics(family, bold).capHeightShare * assSize

    /**
     * [family] when it shows all characters of [text]. If not, the font of the script of the text in the video
     * language (outside of a video block, the app language), when that font shows the text. Else [family].
     */
    fun family(family: String, text: String): String {
        if (displays(family, text)) return family
        val language = I18n.videoLanguage ?: I18n.language
        return ScriptFonts.videoFamily(language, text)?.takeIf { displays(it, text) } ?: family
    }

    private fun displays(family: String, text: String): Boolean =
        displayCache.getOrPut(family) { Font(family, Font.PLAIN, 1) }.canDisplayUpTo(text) == -1

    fun textWidth(text: String, family: String, bold: Boolean, assSize: Double, spacing: Double = 0.0): Double {
        if (text.isEmpty()) return 0.0
        val width = awtFont(family(family, text), bold, assSize).getStringBounds(text, frc).width
        return width + spacing * text.length
    }

    private fun metrics(family: String, bold: Boolean): Metrics = metricsCache.getOrPut(family to bold) {
        val reference = Font(family, if (bold) Font.BOLD else Font.PLAIN, REFERENCE_SIZE)
        val line = reference.getLineMetrics("Hg", frc)
        val height = (line.ascent + line.descent).toDouble()
        val capHeight = reference.createGlyphVector(frc, "H").visualBounds.height
        if (height <= 0.0 || capHeight <= 0.0) {
            Metrics(lineHeightPerEm = 1.15, ascentShare = 0.8, capHeightShare = 0.6)
        } else {
            Metrics(
                lineHeightPerEm = height / REFERENCE_SIZE,
                ascentShare = line.ascent / height,
                capHeightShare = capHeight / height,
            )
        }
    }

    private const val REFERENCE_SIZE = 100
}
