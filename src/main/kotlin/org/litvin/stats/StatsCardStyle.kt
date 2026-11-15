package org.litvin.stats

/**
 * The look of the rows on the statistics card. Each style works with the normal and the compact rows,
 * with the group titles, and with the light and the dark card. [key] is stored in stats.json.
 * Do not change a key after a release.
 */
enum class CardStyle(val key: String) {
    /** A label over a bar in two parts. Each part is the share of a player. */
    CLASSIC("classic"),

    /** Bars that grow from the center to each player. A percentage uses a scale of 100. */
    MIRROR("mirror"),

    /** A band for each row, and no bars. The better value has the player color. */
    STRIPES("stripes"),

    /** A label band in the center, with the opposite brightness of the card, and the values at the sides. */
    LABELS("labels"),

    /** A percentage shows its fraction next to it, and the bars grow from the center. The values of the error rows are red. */
    SHEET("sheet"),
    ;

    companion object {
        /** The style of [key], or [CLASSIC] for an unknown key. */
        fun ofKey(key: String?): CardStyle = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/**
 * The colors of the dark or the light card, as RGB values. [rule] is the color of the thin lines, which use [ruleOpacity].
 * [band] and [bandText] are the label band of the labels style. It is light on the dark card, and dark on the light card.
 * [error] is the color of the error values of the sheet style.
 */
internal class CardPalette(
    val panel: Int,
    val text: Int,
    val soft: Int,
    val muted: Int,
    val rule: Int,
    val ruleOpacity: Double,
    val band: Int,
    val bandText: Int,
    val error: Int,
) {
    companion object {
        val DARK = CardPalette(panel = 0x0B0F14, text = 0xFFFFFF, soft = 0xD6D6D6, muted = 0xA8A8A8, rule = 0xFFFFFF,
            ruleOpacity = 0.16, band = 0xECECEC, bandText = 0x111111, error = 0xFF5A4E)
        val LIGHT = CardPalette(panel = 0xF6F5F1, text = 0x161616, soft = 0x3A3A3A, muted = 0x6E6E6E, rule = 0x000000,
            ruleOpacity = 0.14, band = 0x1C2026, bandText = 0xFFFFFF, error = 0xD92D20)
    }
}
