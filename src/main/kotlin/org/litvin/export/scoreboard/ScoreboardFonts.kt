package org.litvin.export.scoreboard

import org.litvin.shared.util.DrawnText
import org.litvin.i18n.I18n
import org.litvin.i18n.ScriptFonts
import org.litvin.i18n.TextFit
import org.litvin.shared.util.BundledFonts
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
 *
 * The user can choose one font for all text of the board (B-13, step 10). [withChosenFamily] sets it while a layout
 * builds the scene. Then [family], [textWidth], and [capHeight] use the chosen font in place of a bundled style font
 * ([face]). The other functions take the family of a drawn label, so they use the family as it is.
 */
object ScoreboardFonts {
    init {
        // Java measures with the same fonts as libass and mpv (B-13, step 10).
        BundledFonts.registerInJava()
    }

    private val frc = FontRenderContext(null, true, true)
    private val metricsCache = ConcurrentHashMap<Pair<String, Boolean>, Metrics>()
    private val displayCache = ConcurrentHashMap<String, Font>()

    private data class Metrics(val lineHeightPerEm: Double, val ascentShare: Double, val capHeightShare: Double)

    /** A family name and a weight. */
    data class Face(val family: String, val bold: Boolean)

    /** The font that the user chose for the scene that this thread builds now, or null for the fonts of the style. */
    private val chosenFamily = ThreadLocal<String?>()

    /** The bundled fonts of the styles. The chosen font replaces them, but not a font of a script such as Japanese. */
    private val STYLE_FAMILIES = setOf(INTER, INTER_BLACK, LIBERATION_SANS, ARCHIVO_BLACK, GELASIO, FIRA_SANS, OPEN_SANS, CASCADIA_MONO, CAVEAT)

    /** The fonts with only one heavy face. The styles use them with a regular weight. */
    private val BLACK_FAMILIES = setOf(INTER_BLACK, ARCHIVO_BLACK)

    /** Runs [block] with [family] in place of the style fonts. A null [family] keeps the style fonts. */
    fun <T> withChosenFamily(family: String?, block: () -> T): T {
        if (family == null) return block()
        val previous = chosenFamily.get()
        chosenFamily.set(family)
        try {
            return block()
        } finally {
            chosenFamily.set(previous)
        }
    }

    /**
     * The face that draws a text that a style gives in [family] and [bold]. Without a chosen font, it is the same face.
     * With a chosen font, a style font becomes the chosen font. A black font becomes the bold face of the chosen font,
     * so that a heavy text stays heavy. A second call with the result gives the same result.
     */
    fun face(family: String, bold: Boolean): Face {
        val chosen = chosenFamily.get() ?: return Face(family, bold)
        if (family !in STYLE_FAMILIES) return Face(family, bold)
        return Face(chosen, bold || family in BLACK_FAMILIES)
    }

    /** The AWT font of a drawn label: [family] is the family of the label, not a style font. */
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

    /** The height of the capital letters and digits for the libass size [assSize]. [family] can be a style font. */
    fun capHeight(family: String, bold: Boolean, assSize: Double): Double =
        face(family, bold).let { metrics(it.family, it.bold).capHeightShare * assSize }

    /**
     * The family that draws [text] of the style font [styleFamily]: first the chosen font or [styleFamily] ([face]).
     * Then: [family] when it shows all characters of [text]. If not, the font of the script of the text in the video
     * language (outside of a video block, the app language), when that font shows the text. If not, the first
     * installed font of [FALLBACKS] that shows the text. Else [family].
     */
    fun family(styleFamily: String, text: String): String {
        val family = face(styleFamily, false).family
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
     * The fallback fonts of a style font that cannot show the letters of a language. Archivo Black has no Cyrillic
     * letters. Inter Black is also a very heavy font, so a Russian name keeps the look of the style.
     */
    private val FALLBACKS = mapOf(ARCHIVO_BLACK to listOf(INTER_BLACK))

    /**
     * The bundled Inter has the Latin, the Cyrillic, and the Greek letters. Gelasio (no Cyrillic) and Caveat (no
     * Greek) fall back to it.
     */
    private const val LAST_FALLBACK = INTER

    /** The width of [text] in the composed form, as the video draws it (see [DrawnText]). */
    fun textWidth(text: String, family: String, bold: Boolean, assSize: Double, spacing: Double = 0.0): Double {
        if (text.isEmpty()) return 0.0
        val drawn = DrawnText.composed(text)
        return drawnWidth(drawn, family(family, drawn), face(family, bold).bold, assSize, spacing)
    }

    /** The width of the composed text [drawn] in the family of a drawn label. */
    private fun drawnWidth(drawn: String, family: String, bold: Boolean, assSize: Double, spacing: Double): Double {
        if (drawn.isEmpty()) return 0.0
        val width = awtFont(family, bold, assSize).getStringBounds(drawn, frc).width
        // The video adds the spacing once for each character. An emoji is one character, but two Java chars.
        return width + spacing * drawn.codePointCount(0, drawn.length)
    }

    /**
     * Fits [text] and the [suffix] after it in [maxWidth] (B-11 E3-T19): a smaller font, and then a cut with "…".
     * See [TextFit]. [family] is the family that draws the text, for example a result of [family]: it shows all
     * characters of [text].
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
        width = { shown, size -> drawnWidth(DrawnText.composed(shown), family, bold, size, spacing) },
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
