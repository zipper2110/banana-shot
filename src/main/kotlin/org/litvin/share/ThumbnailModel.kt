package org.litvin.share

import java.util.Locale

/**
 * The parts of a YouTube thumbnail of the Share tab (B-46): the video type, the text fields, the layout styles,
 * the text positions, and the color roles. The design is in design/share-redesign/final.html.
 */

/** The video type sets the preset of the large words and the name of the PNG file. */
enum class ThumbnailVideoType(val key: String, val title: String, val words: String, val fileSuffix: String) {
    FULL_MATCH("full", "Full match", "FULL MATCH", "full-match"),
    BEST_POINTS("best", "Best points", "BEST POINTS", "best-points"),
    ;

    companion object {
        fun byKey(key: String?): ThumbnailVideoType? = entries.firstOrNull { it.key == key }
    }
}

/** The text fields of the thumbnail. A layout style shows a field only when the field is not empty. */
enum class ThumbnailField(val key: String, val label: String, val source: String, val maxLength: Int) {
    HEAD("head", "Large words", "From the video type", 24),
    KIND("kind", "Match type", "From the match", 24),
    TEAM1("team1", "Player or team 1", "From the players", 40),
    TEAM2("team2", "Player or team 2", "From the players", 40),
    EVENT("event", "League or event", "From your last thumbnail", 40),
    COURT("court", "Court", "From the scoreboard title", 40),
    DATE("date", "Date", "From the video file", 24),
    ;

    companion object {
        fun byKey(key: String): ThumbnailField? = entries.firstOrNull { it.key == key }
    }
}

/** A position of the texts in a layout style. No position puts a text in the bottom-right corner (the video length). */
enum class TextPlace(val key: String, val title: String) {
    LEFT("left", "Left"),
    RIGHT("right", "Right"),
    TOP("top", "Top"),
    BOTTOM("bottom", "Bottom"),
    CENTER("center", "Center"),
    ;

    companion object {
        fun byKey(key: String?): TextPlace? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The color roles. All styles use the same roles, and each style uses some of them.
 * A role with [auto] can be "Auto": the app selects dark or white, from the contrast with the color under it.
 */
enum class ColorRole(val key: String, val title: String, val auto: Boolean = false) {
    TEXT("text", "Text"),
    ACCENT("acc", "Accent"),
    ON_ACCENT("onAcc", "Text on accent", auto = true),
    PANEL("pan", "Panel"),
    ON_PANEL("onPan", "Text on panel", auto = true),
    SHADE("shade", "Shade"),
    ;

    companion object {
        fun byKey(key: String): ColorRole? = entries.firstOrNull { it.key == key }
    }
}

/** A named color of the swatch rows. [rgb] is 0xRRGGBB. */
data class NamedColor(val rgb: Int, val name: String)

/** A color set gives a value to each role. The "Text on" roles are always Auto in a set. */
data class ColorSet(val id: String, val name: String, val text: Int, val accent: Int, val panel: Int, val shade: Int) {
    fun color(role: ColorRole): Int? = when (role) {
        ColorRole.TEXT -> text
        ColorRole.ACCENT -> accent
        ColorRole.PANEL -> panel
        ColorRole.SHADE -> shade
        ColorRole.ON_ACCENT, ColorRole.ON_PANEL -> null
    }
}

object ThumbnailColorSets {
    val LIME = ColorSet("lime", "Lime", 0xFFFFFF, 0xC8EC46, 0x0E1014, 0x0A0E14)
    val BROADCAST = ColorSet("tv", "Broadcast", 0xFFFFFF, 0xFFD54A, 0x0B1E3A, 0x0A0E14)
    val all: List<ColorSet> = listOf(
        LIME,
        BROADCAST,
        ColorSet("sunset", "Sunset", 0xFFFFFF, 0xFF8A3D, 0x3A0D14, 0x3A0D14),
        ColorSet("ocean", "Ocean", 0xFFFFFF, 0x3DD6F5, 0x0B1E3A, 0x0B1E3A),
        ColorSet("red", "Red", 0xFFFFFF, 0xFF4D4D, 0x0E1014, 0x0A0E14),
        ColorSet("light", "Light", 0x141414, 0x4D8BFF, 0xFFFFFF, 0xFFFFFF),
    )

    fun byId(id: String?): ColorSet? = all.firstOrNull { it.id == id }

    private val ink = listOf(
        NamedColor(0xFFFFFF, "White"), NamedColor(0x141414, "Black"), NamedColor(0x0B1E3A, "Navy"),
        NamedColor(0xC8EC46, "Lime"), NamedColor(0xFFD54A, "Yellow"),
    )

    /** The swatches of the Colors tab for each role. "Other color" can select any color. */
    fun swatches(role: ColorRole): List<NamedColor> = when (role) {
        ColorRole.TEXT -> listOf(
            NamedColor(0xFFFFFF, "White"), NamedColor(0xD9D9D9, "Light gray"), NamedColor(0x141414, "Black"),
            NamedColor(0xC8EC46, "Lime"), NamedColor(0xFFD54A, "Yellow"),
        )
        ColorRole.ACCENT -> listOf(
            NamedColor(0xC8EC46, "Lime"), NamedColor(0xFFD54A, "Yellow"), NamedColor(0xFF8A3D, "Orange"),
            NamedColor(0xFF4D4D, "Red"), NamedColor(0xFF5FA2, "Pink"), NamedColor(0x3DD6F5, "Cyan"),
            NamedColor(0x4D8BFF, "Blue"), NamedColor(0xFFFFFF, "White"),
        )
        ColorRole.PANEL -> listOf(
            NamedColor(0x0E1014, "Black"), NamedColor(0x0B1E3A, "Navy"), NamedColor(0x0F2A1C, "Dark green"),
            NamedColor(0x3A0D14, "Dark red"), NamedColor(0x24123D, "Purple"), NamedColor(0xFFFFFF, "White"),
        )
        ColorRole.SHADE -> listOf(
            NamedColor(0x0A0E14, "Black"), NamedColor(0x0B1E3A, "Navy"), NamedColor(0x0F2A1C, "Dark green"),
            NamedColor(0x3A0D14, "Dark red"), NamedColor(0xFFFFFF, "White"),
        )
        ColorRole.ON_ACCENT, ColorRole.ON_PANEL -> ink
    }
}

/**
 * A layout style sets the placement and the colors of the texts.
 * [fields]: the fields that the style shows. [places]: the text positions, the first one is the default.
 * [roles]: the color roles of the style, with the parts that use each role.
 */
enum class ThumbnailStyle(
    val key: String,
    val title: String,
    val fields: Set<ThumbnailField>,
    val defaultSet: ColorSet,
    val places: List<TextPlace>,
    val roles: Map<ColorRole, String>,
) {
    WORDS(
        "words", "Big words", setOf(ThumbnailField.HEAD, ThumbnailField.KIND), ThumbnailColorSets.LIME,
        listOf(TextPlace.RIGHT, TextPlace.LEFT),
        mapOf(ColorRole.TEXT to "Match type", ColorRole.ACCENT to "Large words", ColorRole.SHADE to "Behind the words"),
    ),
    VERSUS(
        "versus", "Versus", setOf(ThumbnailField.HEAD, ThumbnailField.TEAM1, ThumbnailField.TEAM2), ThumbnailColorSets.LIME,
        listOf(TextPlace.LEFT, TextPlace.RIGHT),
        mapOf(
            ColorRole.TEXT to "Players", ColorRole.ACCENT to "Tag and \"VS\"", ColorRole.ON_ACCENT to "Words on the tag",
            ColorRole.SHADE to "At the side",
        ),
    ),
    CARD(
        "card", "Match card",
        setOf(ThumbnailField.HEAD, ThumbnailField.EVENT, ThumbnailField.COURT, ThumbnailField.DATE, ThumbnailField.TEAM1, ThumbnailField.TEAM2),
        ThumbnailColorSets.LIME,
        listOf(TextPlace.LEFT, TextPlace.RIGHT),
        mapOf(ColorRole.ACCENT to "Edge, league, large words", ColorRole.PANEL to "Card", ColorRole.ON_PANEL to "Players, court, date"),
    ),
    SPLIT(
        "split", "Split", setOf(ThumbnailField.HEAD, ThumbnailField.KIND, ThumbnailField.EVENT), ThumbnailColorSets.LIME,
        listOf(TextPlace.LEFT, TextPlace.RIGHT),
        mapOf(ColorRole.ACCENT to "Panel", ColorRole.ON_ACCENT to "All texts"),
    ),
    BROADCAST(
        "tv", "Broadcast",
        setOf(ThumbnailField.HEAD, ThumbnailField.TEAM1, ThumbnailField.TEAM2, ThumbnailField.EVENT, ThumbnailField.COURT),
        ThumbnailColorSets.BROADCAST,
        listOf(TextPlace.BOTTOM, TextPlace.TOP),
        mapOf(
            ColorRole.ACCENT to "Tag and \"vs\"", ColorRole.ON_ACCENT to "Words on the tag",
            ColorRole.PANEL to "Player bar, info text", ColorRole.ON_PANEL to "Players, info bar", ColorRole.SHADE to "Behind the bars",
        ),
    ),
    POSTER(
        "poster", "Poster",
        setOf(ThumbnailField.HEAD, ThumbnailField.KIND, ThumbnailField.DATE, ThumbnailField.TEAM1, ThumbnailField.TEAM2),
        ThumbnailColorSets.LIME,
        listOf(TextPlace.CENTER),
        mapOf(ColorRole.TEXT to "Large words, players", ColorRole.ACCENT to "Match type, date, \"vs\"", ColorRole.SHADE to "Top and bottom"),
    ),
    ;

    companion object {
        fun byKey(key: String?): ThumbnailStyle? = entries.firstOrNull { it.key == key }
    }
}

/** The colors of a style, after the color set, the changes of the user, and "Auto". Each color is 0xRRGGBB. */
data class ThumbnailColors(
    val text: Int,
    val accent: Int,
    val onAccent: Int,
    val panel: Int,
    val onPanel: Int,
    val shade: Int,
    /** The strength of the shade, 0.0 to 1.5. */
    val strength: Double,
)

/**
 * The colors that the user selected for one style, and its text position. Empty values use the defaults of the style.
 * [colors]: role key to "#RRGGBB" or "auto". A role value overrides the color set.
 */
data class StyleLookV1(
    val place: String? = null,
    val colorSet: String? = null,
    val colors: Map<String, String> = emptyMap(),
    val shadeStrength: Double? = null,
) {
    /** True when the user changed a role or the strength after the color set. */
    val customized: Boolean get() = colors.isNotEmpty() || shadeStrength != null

    /** True when the colors are not the colors of the style. */
    val colorsEdited: Boolean get() = colorSet != null || customized

    fun withoutColors(): StyleLookV1 = StyleLookV1(place = place)
}

object ThumbnailLooks {
    const val AUTO = "auto"
    const val MAX_STRENGTH = 1.5
    private const val DARK_INK = 0x141414
    private const val WHITE = 0xFFFFFF

    fun place(style: ThumbnailStyle, look: StyleLookV1?): TextPlace =
        TextPlace.byKey(look?.place)?.takeIf { it in style.places } ?: style.places.first()

    fun colorSet(style: ThumbnailStyle, look: StyleLookV1?): ColorSet = ThumbnailColorSets.byId(look?.colorSet) ?: style.defaultSet

    /** The value of a role as the user selected it: "#RRGGBB" or [AUTO]. */
    fun roleValue(style: ThumbnailStyle, look: StyleLookV1?, role: ColorRole): String {
        look?.colors?.get(role.key)?.let { value -> if (value == AUTO && role.auto || parseHex(value) != null) return value }
        val color = colorSet(style, look).color(role) ?: return AUTO
        return hex(color)
    }

    fun strength(look: StyleLookV1?): Double = (look?.shadeStrength ?: 1.0).coerceIn(0.0, MAX_STRENGTH)

    fun colors(style: ThumbnailStyle, look: StyleLookV1?): ThumbnailColors {
        fun rgb(role: ColorRole): Int? = parseHex(roleValue(style, look, role))
        val accent = rgb(ColorRole.ACCENT) ?: WHITE
        val panel = rgb(ColorRole.PANEL) ?: DARK_INK
        return ThumbnailColors(
            text = rgb(ColorRole.TEXT) ?: WHITE,
            accent = accent,
            onAccent = rgb(ColorRole.ON_ACCENT) ?: onColor(accent),
            panel = panel,
            onPanel = rgb(ColorRole.ON_PANEL) ?: onColor(panel),
            shade = rgb(ColorRole.SHADE) ?: DARK_INK,
            strength = strength(look),
        )
    }

    /** The text color on a background: dark or white, the one with more contrast. */
    fun onColor(background: Int): Int =
        if (contrast(background, WHITE) >= contrast(background, DARK_INK)) WHITE else DARK_INK

    fun contrast(a: Int, b: Int): Double {
        val (x, y) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (x + 0.05) / (y + 0.05)
    }

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((rgb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun hex(rgb: Int): String = String.format(Locale.ROOT, "#%06x", rgb and 0xFFFFFF)

    fun parseHex(value: String?): Int? {
        val digits = value?.trim()?.removePrefix("#") ?: return null
        if (digits.length != 6) return null
        return digits.toIntOrNull(16)
    }
}
