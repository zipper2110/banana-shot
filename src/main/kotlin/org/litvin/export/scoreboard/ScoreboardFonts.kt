package org.litvin.export.scoreboard

import org.litvin.shared.util.DrawnText
import org.litvin.i18n.I18n
import org.litvin.i18n.ScriptFonts
import org.litvin.i18n.TextFit
import java.awt.Font
import java.awt.font.FontRenderContext
import java.util.Locale
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
     * language (outside of a video block, the app language), when that font shows the text. If not, the first
     * installed font of [FALLBACKS] that shows the text. Else [family].
     */
    fun family(family: String, text: String): String {
        val drawn = DrawnText.composed(text)
        if (displays(family, drawn)) return family
        val language = I18n.videoLanguage ?: I18n.language
        ScriptFonts.videoFamily(language, drawn)?.takeIf { displays(it, drawn) }?.let { return it }
        val fallbacks = FALLBACKS[family].orEmpty() + LAST_FALLBACK
        return fallbacks.firstOrNull { it != family && installed(it) && displays(it, drawn) } ?: family
    }

    private fun displays(family: String, text: String): Boolean = font(family).canDisplayUpTo(text) == -1

    /** Java gives the logical font "Dialog" for a family that is not installed. */
    private fun installed(family: String): Boolean = font(family).getFamily(Locale.ENGLISH).equals(family, ignoreCase = true)

    private fun font(family: String): Font = displayCache.getOrPut(family) { Font(family, Font.PLAIN, 1) }

    /**
     * The fallback fonts of a style font that cannot show the letters of a language. The Ink Free font of Windows 10
     * has no Cyrillic and no Greek letters. Segoe Print is also a handwriting font, and all Windows versions have it.
     */
    private val FALLBACKS = mapOf(INK_FREE to listOf("Segoe Print"))

    /** All Windows versions have Segoe UI, with the Latin, the Cyrillic, and the Greek letters. */
    private const val LAST_FALLBACK = "Segoe UI"

    /** The width of [text] in the composed form, as the video draws it (see [DrawnText]). */
    fun textWidth(text: String, family: String, bold: Boolean, assSize: Double, spacing: Double = 0.0): Double {
        if (text.isEmpty()) return 0.0
        val drawn = DrawnText.composed(text)
        val width = awtFont(family(family, drawn), bold, assSize).getStringBounds(drawn, frc).width
        // The video adds the spacing once for each character. An emoji is one character, but two Java chars.
        return width + spacing * drawn.codePointCount(0, drawn.length)
    }

    /**
     * Fits [text] and the [suffix] after it in [maxWidth] (B-11 E3-T19): a smaller font, and then a cut with "…".
     * See [TextFit]. [family] is the family that draws the text.
     */
    fun fit(
        text: String,
        family: String,
        bold: Boolean,
        assSize: Double,
        maxWidth: Double,
        spacing: Double = 0.0,
        suffix: String = "",
    ): TextFit.Fitted = TextFit.fit(
        text = text,
        size = assSize,
        maxWidth = maxWidth,
        width = { shown, size -> textWidth(shown, family, bold, size, spacing) },
        suffix = suffix,
        hasEllipsis = displays(family, TextFit.ELLIPSIS),
    )

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
