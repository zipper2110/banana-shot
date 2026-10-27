package org.litvin.share

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Font
import java.awt.font.TextAttribute
import java.util.concurrent.ConcurrentHashMap

/**
 * The fonts of the thumbnail styles of B-53. The app bundles them (Google Fonts, SIL Open Font License, and Apache
 * License 2.0 for Permanent Marker), so that the PNG looks the same on each computer.
 * The files are in src/main/resources/fonts/thumbnail.
 */
enum class ThumbnailFace(internal val file: String, val title: String) {
    ANTON("Anton-Regular.ttf", "Anton"),
    MARKER("PermanentMarker-Regular.ttf", "Permanent Marker"),
    BUNGEE("Bungee-Regular.ttf", "Bungee"),
    SHOULDERS("BigShouldersDisplay-Black.ttf", "Big Shoulders"),
    BARLOW_BOLD("BarlowCondensed-Bold.ttf", "Barlow Condensed Bold"),
    BARLOW_EXTRA_BOLD("BarlowCondensed-ExtraBold.ttf", "Barlow Condensed"),
    INTER_BOLD("Inter-Bold.ttf", "Inter"),
    ARCHIVO("ArchivoBlack-Regular.ttf", "Archivo Black"),
    EXO_ITALIC("Exo2-BlackItalic.ttf", "Exo 2"),
    ;

    companion object {
        /** The fonts that the user can select for the brand name. Barlow Condensed is in the list one time. */
        val BRAND = entries - BARLOW_BOLD

        fun byKey(key: String?): ThumbnailFace? = entries.firstOrNull { it.name == key }
    }
}

internal object ThumbnailFonts {
    private val logger = KotlinLogging.logger {}
    private val loaded = ConcurrentHashMap<ThumbnailFace, Font>()
    private val fallbacks = ConcurrentHashMap<ThumbnailFace, Boolean>()

    /** The font at size 1. Null when the file cannot load: then the caller uses the font of the B-46 styles. */
    fun base(face: ThumbnailFace): Font? {
        loaded[face]?.let { return it }
        if (fallbacks[face] == true) return null
        val font = runCatching {
            ThumbnailFonts::class.java.getResourceAsStream("/fonts/thumbnail/${face.file}").use { stream ->
                requireNotNull(stream) { "No font file ${face.file}" }
                Font.createFont(Font.TRUETYPE_FONT, stream)
            }
        }.onFailure { logger.warn(it) { "Cannot load the thumbnail font ${face.file}" } }.getOrNull()
        if (font == null) {
            fallbacks[face] = true
            return null
        }
        loaded[face] = font
        return font
    }

    /** The font at [size] px with [spacing] px between the letters (the CSS letter-spacing). */
    fun font(face: ThumbnailFace, size: Double, spacing: Double = 0.0): Font {
        val font = base(face)?.deriveFont(size.toFloat()) ?: ThumbnailRenderer.font(ThumbnailRenderer.Weight.BLACK, size)
        if (spacing == 0.0) return font
        return font.deriveFont(mapOf(TextAttribute.TRACKING to (spacing / size).toFloat()))
    }

    /** True when [font] is a bundled font and it has all letters of [text]. */
    fun displays(font: Font, text: String): Boolean = font.canDisplayUpTo(text) == -1
}
