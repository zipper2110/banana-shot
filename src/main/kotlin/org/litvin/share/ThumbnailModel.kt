package org.litvin.share

import java.util.Locale
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/**
 * The parts of a YouTube thumbnail of the Share tab (B-46): the video type, the text fields, the layout styles,
 * the text positions, and the color roles. The design is in design/share-redesign/final.html.
 */

/**
 * The video type sets the preset of the large words and the name of the PNG file.
 * The constructors of the enums in this file get catalog keys for the texts.
 */
enum class ThumbnailVideoType(val key: String, private val titleKey: String, val fileSuffix: String) {
    FULL_MATCH("full", "share.type.full", "full-match"),
    BEST_POINTS("best", "share.type.best", "best-points"),
    ;

    val title: String get() = tr(titleKey)

    /** The preset of the large words: the title in upper case, for example "FULL MATCH". */
    val words: String get() = DisplayFormats.upper(title)

    companion object {
        fun byKey(key: String?): ThumbnailVideoType? = entries.firstOrNull { it.key == key }
    }
}

/** The text fields of the thumbnail. A layout style shows a field only when the field is not empty. */
enum class ThumbnailField(val key: String, private val labelKey: String, private val sourceKey: String, val maxLength: Int) {
    HEAD("head", "share.field.head", "share.source.type", 24),
    KIND("kind", "share.field.kind", "share.source.match", 24),
    TEAM1("team1", "share.field.team1", "share.source.players", 40),
    TEAM2("team2", "share.field.team2", "share.source.players", 40),
    EVENT("event", "share.field.event", "share.source.last", 40),
    COURT("court", "share.field.court", "share.source.scoreboard", 40),
    DATE("date", "share.field.date", "share.source.file", 24),

    /** The round of an event, for example "SEMI-FINAL". It has no preset. */
    ROUND("round", "share.field.round", "share.source.none", 24),

    /** The brand name under the logo. It is the same in all projects, as the logo. */
    CLUB("club", "share.field.club", "share.source.brand", 32),

    /** The score, for example "6-4 7-5". It is never a preset, because many users do not want to show the result. */
    SCORE("score", "share.field.score", "share.source.none", 24),
    ;

    val label: String get() = tr(labelKey)
    val source: String get() = tr(sourceKey)

    companion object {
        fun byKey(key: String): ThumbnailField? = entries.firstOrNull { it.key == key }
    }
}

/** A position of the texts in a layout style. No position puts a text in the bottom-right corner (the video length). */
enum class TextPlace(val key: String, private val titleKey: String) {
    LEFT("left", "adjust.left"),
    RIGHT("right", "adjust.right"),
    TOP("top", "share.place.top"),
    BOTTOM("bottom", "share.place.bottom"),
    CENTER("center", "adjust.center"),
    ;

    val title: String get() = tr(titleKey)

    companion object {
        fun byKey(key: String?): TextPlace? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The color roles. All styles use the same roles, and each style shows only the roles that it uses.
 * A color set gives a value to each role, so that a set changes all styles in the same way.
 * A role with [auto] can be "Auto": the app selects dark or white, from the contrast with the color under it.
 * The order of the entries is the order of the rows on the Colors tab.
 */
enum class ColorRole(val key: String, private val titleKey: String, val auto: Boolean = false) {
    TEXT("text", "share.role.text"),
    ACCENT("acc", "share.role.accent"),
    ON_ACCENT("onAcc", "share.role.onAccent", auto = true),
    /** The second and the third accent: the sides of the two teams, and the red boxes of the TV styles. */
    SECOND("acc2", "share.role.second"),
    THIRD("acc3", "share.role.third"),
    PANEL("pan", "share.role.panel"),
    ON_PANEL("onPan", "share.role.onPanel", auto = true),
    /** The light boxes: the paper, the info bars, the cards, and the score cells. */
    CARD("card", "share.role.card"),
    ON_CARD("onCard", "share.role.onCard", auto = true),
    SHADE("shade", "share.role.shade"),
    ;

    val title: String get() = tr(titleKey)

    companion object {
        fun byKey(key: String): ColorRole? = entries.firstOrNull { it.key == key }
    }
}

/** A named color of the swatch rows. [rgb] is 0xRRGGBB. [nameKey] is the catalog key of the name. */
data class NamedColor(val rgb: Int, private val nameKey: String) {
    val name: String get() = tr(nameKey)

    /** The color in component names, for example "light-gray". It does not change with the language. */
    val id: String get() = nameKey.substringAfterLast('.').replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
}

/** A color set gives a value to each role. The "Text on" roles are always Auto in a set. [nameKey] is a catalog key. */
data class ColorSet(
    val id: String,
    private val nameKey: String,
    val text: Int,
    val accent: Int,
    val panel: Int,
    val shade: Int,
    val card: Int = 0xFFFFFF,
    val second: Int = 0xE3262E,
    val third: Int = 0x1F5BFF,
) {
    val name: String get() = tr(nameKey)

    fun color(role: ColorRole): Int? = when (role) {
        ColorRole.TEXT -> text
        ColorRole.ACCENT -> accent
        ColorRole.SECOND -> second
        ColorRole.THIRD -> third
        ColorRole.PANEL -> panel
        ColorRole.CARD -> card
        ColorRole.SHADE -> shade
        ColorRole.ON_ACCENT, ColorRole.ON_PANEL, ColorRole.ON_CARD -> null
    }
}

object ThumbnailColorSets {
    /** The color of the torn paper. */
    private const val PAPER = 0xF2EFE6

    val LIME = ColorSet("lime", "share.color.lime", 0xFFFFFF, 0xC8EC46, 0x0E1014, 0x0A0E14)
    val BROADCAST = ColorSet("tv", "style.broadcast", 0xFFFFFF, 0xFFD54A, 0x0B1E3A, 0x0A0E14, second = 0xE8202A)
    val STREET = ColorSet("street", "share.set.street", 0xFFFFFF, 0xF5E50A, 0x111111, 0x0A0A0A, card = PAPER)
    val NEON = ColorSet("neon", "share.set.neon", 0xFFFFFF, 0xFF2E88, 0x111111, 0x0A0A0A, card = PAPER)
    val CUP = ColorSet("cup", "share.set.cup", 0xFFFFFF, 0xFFC629, 0x0B1E3A, 0x0B1E3A)
    val FIGHT = ColorSet("fight", "share.set.fight", 0xFFFFFF, 0xFFD54A, 0x111111, 0x08080A)
    val all: List<ColorSet> = listOf(
        LIME,
        BROADCAST,
        ColorSet("sunset", "style.sunset", 0xFFFFFF, 0xFF8A3D, 0x3A0D14, 0x3A0D14),
        ColorSet("ocean", "share.set.ocean", 0xFFFFFF, 0x3DD6F5, 0x0B1E3A, 0x0B1E3A),
        ColorSet("red", "share.color.red", 0xFFFFFF, 0xFF4D4D, 0x0E1014, 0x0A0E14),
        ColorSet("light", "share.set.light", 0x141414, 0x4D8BFF, 0xFFFFFF, 0xFFFFFF),
        STREET,
        NEON,
        CUP,
        FIGHT,
    )

    fun byId(id: String?): ColorSet? = all.firstOrNull { it.id == id }

    private val ink = listOf(
        NamedColor(0xFFFFFF, "share.color.white"), NamedColor(0x141414, "share.color.black"), NamedColor(0x0B1E3A, "share.color.navy"),
        NamedColor(0xC8EC46, "share.color.lime"), NamedColor(0xFFD54A, "share.color.yellow"),
    )

    /** The swatches of the Colors tab for each role. "Other color" can select any color. */
    fun swatches(role: ColorRole): List<NamedColor> = when (role) {
        ColorRole.TEXT -> listOf(
            NamedColor(0xFFFFFF, "share.color.white"), NamedColor(0xD9D9D9, "share.color.lightGray"), NamedColor(0x141414, "share.color.black"),
            NamedColor(0xC8EC46, "share.color.lime"), NamedColor(0xFFD54A, "share.color.yellow"),
        )
        ColorRole.ACCENT -> listOf(
            NamedColor(0xC8EC46, "share.color.lime"), NamedColor(0xFFD54A, "share.color.yellow"), NamedColor(0xFF8A3D, "share.color.orange"),
            NamedColor(0xFF4D4D, "share.color.red"), NamedColor(0xFF5FA2, "share.color.pink"), NamedColor(0x3DD6F5, "share.color.cyan"),
            NamedColor(0x4D8BFF, "share.color.blue"), NamedColor(0xFFFFFF, "share.color.white"),
            NamedColor(0xF5E50A, "share.color.lemon"), NamedColor(0xFF2E88, "share.color.magenta"), NamedColor(0xFFC629, "share.color.gold"),
        )
        ColorRole.PANEL -> listOf(
            NamedColor(0x0E1014, "share.color.black"), NamedColor(0x0B1E3A, "share.color.navy"), NamedColor(0x0F2A1C, "share.color.darkGreen"),
            NamedColor(0x3A0D14, "share.color.darkRed"), NamedColor(0x24123D, "share.color.purple"), NamedColor(0xFFFFFF, "share.color.white"),
        )
        ColorRole.SHADE -> listOf(
            NamedColor(0x0A0E14, "share.color.black"), NamedColor(0x0B1E3A, "share.color.navy"), NamedColor(0x0F2A1C, "share.color.darkGreen"),
            NamedColor(0x3A0D14, "share.color.darkRed"), NamedColor(0xFFFFFF, "share.color.white"),
        )
        ColorRole.SECOND, ColorRole.THIRD -> listOf(
            NamedColor(0xE3262E, "share.color.red"), NamedColor(0x1F5BFF, "share.color.blue"), NamedColor(0xFF8A3D, "share.color.orange"),
            NamedColor(0xFF2E88, "share.color.magenta"), NamedColor(0x3DD6F5, "share.color.cyan"), NamedColor(0xC8EC46, "share.color.lime"),
            NamedColor(0xFFC629, "share.color.gold"), NamedColor(0x0B1E3A, "share.color.navy"), NamedColor(0x141414, "share.color.black"),
        )
        ColorRole.CARD -> listOf(
            NamedColor(0xFFFFFF, "share.color.white"), NamedColor(PAPER, "share.color.cream"), NamedColor(0xD9D9D9, "share.color.lightGray"),
            NamedColor(0xF5E50A, "share.color.lemon"), NamedColor(0x0B1E3A, "share.color.navy"), NamedColor(0x141414, "share.color.black"),
        )
        ColorRole.ON_ACCENT, ColorRole.ON_PANEL, ColorRole.ON_CARD -> ink
    }
}

/** The groups of the style cards. Classic has the six styles of B-46. The other groups are the styles of B-53. */
enum class StyleGroup(val key: String, private val titleKey: String) {
    CLASSIC("classic", "share.group.classic"),
    STREET("street", "share.group.street"),
    COMPETITION("competition", "share.group.competition"),
    FIGHT("fight", "share.group.fight"),
    BOLD("bold", "share.group.bold"),
    TV("tv", "share.group.tv"),
    ;

    val title: String get() = tr(titleKey)

    val styles: List<ThumbnailStyle> get() = ThumbnailStyle.entries.filter { it.group == this }
}

/** The fields and the color roles of the styles of B-53. Each style has one text position. */
private object B53 {
    val HEAD = ThumbnailField.HEAD
    val KIND = ThumbnailField.KIND
    val TEAM1 = ThumbnailField.TEAM1
    val TEAM2 = ThumbnailField.TEAM2
    val EVENT = ThumbnailField.EVENT
    val COURT = ThumbnailField.COURT
    val DATE = ThumbnailField.DATE
    val ROUND = ThumbnailField.ROUND
    val CLUB = ThumbnailField.CLUB
    val SCORE = ThumbnailField.SCORE

    // The roles with the parts that use them, as most styles use them.
    val text = ColorRole.TEXT to "share.use.frameTexts"
    val players = ColorRole.TEXT to "scoreSettings.players"
    val accent = ColorRole.ACCENT to "share.use.shapes"
    val onAccent = ColorRole.ON_ACCENT to "share.use.tagWords"
    val onShapes = ColorRole.ON_ACCENT to "share.use.onShapes"
    val team1 = ColorRole.SECOND to "share.use.team1"
    val team2 = ColorRole.THIRD to "share.use.team2"
    val panel = ColorRole.PANEL to "share.use.ink"
    val onPanel = ColorRole.ON_PANEL to "scoreSettings.players"
    val onCard = ColorRole.ON_CARD to "share.use.lightTexts"
    val side = ColorRole.SHADE to "share.use.side"

    /** Each style shows only the roles that change a part of it. A role that only colors the brand name is not there. */
    fun roles(vararg roles: Pair<ColorRole, String>): Map<ColorRole, String> = mapOf(*roles)
}

/**
 * A layout style sets the placement and the colors of the texts.
 * [fields]: the fields that the style shows. [places]: the text positions, the first one is the default.
 * [roles]: the color roles of the style, with the parts that use each role.
 * [showsLogo]: the style shows the logo of the user (B-53).
 */
enum class ThumbnailStyle(
    val key: String,
    private val titleKey: String,
    val fields: Set<ThumbnailField>,
    val defaultSet: ColorSet,
    val places: List<TextPlace>,
    private val roleKeys: Map<ColorRole, String>,
    val group: StyleGroup = StyleGroup.CLASSIC,
    val showsLogo: Boolean = false,
) {
    WORDS(
        "words", "share.style.words", setOf(ThumbnailField.HEAD, ThumbnailField.KIND), ThumbnailColorSets.LIME,
        listOf(TextPlace.RIGHT, TextPlace.LEFT),
        mapOf(ColorRole.TEXT to "share.field.kind", ColorRole.ACCENT to "share.field.head", ColorRole.SHADE to "share.use.behindWords"),
    ),
    VERSUS(
        "versus", "share.style.versus", setOf(ThumbnailField.HEAD, ThumbnailField.TEAM1, ThumbnailField.TEAM2), ThumbnailColorSets.LIME,
        listOf(TextPlace.LEFT, TextPlace.RIGHT),
        mapOf(
            ColorRole.TEXT to "scoreSettings.players", ColorRole.ACCENT to "share.use.tagVsUpper", ColorRole.ON_ACCENT to "share.use.tagWords",
            ColorRole.SHADE to "share.use.side",
        ),
    ),
    CARD(
        "card", "share.style.card",
        setOf(ThumbnailField.HEAD, ThumbnailField.EVENT, ThumbnailField.COURT, ThumbnailField.DATE, ThumbnailField.TEAM1, ThumbnailField.TEAM2),
        ThumbnailColorSets.LIME,
        listOf(TextPlace.LEFT, TextPlace.RIGHT),
        mapOf(ColorRole.ACCENT to "share.use.edge", ColorRole.PANEL to "share.use.card", ColorRole.ON_PANEL to "share.use.playersCourtDate"),
    ),
    SPLIT(
        "split", "share.style.split", setOf(ThumbnailField.HEAD, ThumbnailField.KIND, ThumbnailField.EVENT), ThumbnailColorSets.LIME,
        listOf(TextPlace.LEFT, TextPlace.RIGHT),
        mapOf(ColorRole.ACCENT to "share.role.panel", ColorRole.ON_ACCENT to "share.use.allTexts"),
    ),
    BROADCAST(
        "tv", "style.broadcast",
        setOf(ThumbnailField.HEAD, ThumbnailField.TEAM1, ThumbnailField.TEAM2, ThumbnailField.EVENT, ThumbnailField.COURT),
        ThumbnailColorSets.BROADCAST,
        listOf(TextPlace.BOTTOM, TextPlace.TOP),
        mapOf(
            ColorRole.ACCENT to "share.use.tagVs", ColorRole.ON_ACCENT to "share.use.tagWords",
            ColorRole.PANEL to "share.use.playerBar", ColorRole.ON_PANEL to "share.use.playersInfo", ColorRole.SHADE to "share.use.behindBars",
        ),
    ),
    POSTER(
        "poster", "share.style.poster",
        setOf(ThumbnailField.HEAD, ThumbnailField.KIND, ThumbnailField.DATE, ThumbnailField.TEAM1, ThumbnailField.TEAM2),
        ThumbnailColorSets.LIME,
        listOf(TextPlace.CENTER),
        mapOf(ColorRole.TEXT to "share.use.wordsPlayers", ColorRole.ACCENT to "share.use.kindDateVs", ColorRole.SHADE to "share.use.topBottom"),
    ),

    // ---------- B-53: Street ----------
    SPLASH(
        "splash", "share.style.splash", setOf(B53.HEAD, B53.KIND, B53.CLUB), ThumbnailColorSets.STREET,
        listOf(TextPlace.RIGHT), B53.roles(B53.text, B53.accent, B53.panel), StyleGroup.STREET, showsLogo = true,
    ),
    SPRAY(
        "spray", "share.style.spray", setOf(B53.HEAD, B53.EVENT, B53.CLUB), ThumbnailColorSets.STREET,
        listOf(TextPlace.BOTTOM), B53.roles(B53.text, B53.accent, B53.panel), StyleGroup.STREET, showsLogo = true,
    ),
    BURST(
        "burst", "share.style.burst", setOf(B53.HEAD, B53.CLUB), ThumbnailColorSets.NEON,
        listOf(TextPlace.RIGHT), B53.roles(B53.accent, B53.onShapes, B53.panel), StyleGroup.STREET, showsLogo = true,
    ),
    TORN(
        "torn", "share.style.torn", setOf(B53.HEAD, B53.KIND, B53.EVENT, B53.CLUB), ThumbnailColorSets.NEON,
        listOf(TextPlace.RIGHT), B53.roles(B53.accent, B53.onAccent, ColorRole.CARD to "share.use.paper", B53.onCard),
        StyleGroup.STREET, showsLogo = true,
    ),
    STENCIL(
        "stencil", "share.style.stencil", setOf(B53.HEAD, B53.TEAM1, B53.TEAM2, B53.CLUB), ThumbnailColorSets.STREET,
        listOf(TextPlace.RIGHT), B53.roles(B53.text, B53.accent, B53.panel, B53.side), StyleGroup.STREET, showsLogo = true,
    ),

    // ---------- B-53: Competition ----------
    // The large words have the place of the round in the artboards. The round is a field only where it has its own place.
    OFFICIAL_BAR(
        "officialBar", "share.style.officialBar", setOf(B53.HEAD, B53.KIND, B53.EVENT, B53.COURT, B53.DATE, B53.CLUB),
        ThumbnailColorSets.CUP, listOf(TextPlace.BOTTOM),
        B53.roles(B53.text, B53.accent, ColorRole.CARD to "share.use.infoBar", B53.onCard, B53.side), StyleGroup.COMPETITION, showsLogo = true,
    ),
    SIDE_PANEL(
        "sidePanel", "share.style.sidePanel", setOf(B53.CLUB, B53.EVENT, B53.HEAD, B53.TEAM1, B53.TEAM2),
        ThumbnailColorSets.CUP, listOf(TextPlace.LEFT),
        B53.roles(ColorRole.TEXT to "share.use.panelTexts", B53.accent, ColorRole.PANEL to "share.use.side"), StyleGroup.COMPETITION, showsLogo = true,
    ),
    HEADER_BAND(
        "headerBand", "share.style.headerBand", setOf(B53.EVENT, B53.DATE, B53.HEAD, B53.TEAM1, B53.TEAM2),
        ThumbnailColorSets.CUP, listOf(TextPlace.TOP), B53.roles(B53.text, B53.accent, B53.onAccent, B53.panel, B53.side),
        StyleGroup.COMPETITION, showsLogo = true,
    ),
    ROUND_CARD(
        "roundCard", "share.style.roundCard", setOf(B53.HEAD, B53.EVENT, B53.DATE, B53.COURT),
        ThumbnailColorSets.CUP, listOf(TextPlace.RIGHT), B53.roles(B53.accent, ColorRole.CARD to "share.use.card", B53.onCard),
        StyleGroup.COMPETITION, showsLogo = true,
    ),
    CORNER_FRAME(
        "cornerFrame", "share.style.cornerFrame", setOf(B53.EVENT, B53.DATE, B53.COURT, B53.HEAD, B53.TEAM1, B53.TEAM2),
        ThumbnailColorSets.CUP, listOf(TextPlace.LEFT), B53.roles(B53.text, B53.accent, ColorRole.SHADE to "share.use.topBottom"),
        StyleGroup.COMPETITION, showsLogo = true,
    ),

    // ---------- B-53: Fight night ----------
    CLASH(
        "clash", "share.style.clash", setOf(B53.TEAM1, B53.TEAM2, B53.HEAD), ThumbnailColorSets.FIGHT,
        listOf(TextPlace.CENTER), B53.roles(B53.text, B53.accent, B53.team1, B53.team2, B53.panel), StyleGroup.FIGHT, showsLogo = true,
    ),
    FIGHT_CARD(
        "fightCard", "share.style.fightCard", setOf(B53.HEAD, B53.TEAM1, B53.TEAM2, B53.CLUB), ThumbnailColorSets.FIGHT,
        listOf(TextPlace.RIGHT), B53.roles(B53.text, B53.accent, B53.onAccent, B53.side), StyleGroup.FIGHT, showsLogo = true,
    ),
    BOLT(
        "bolt", "share.style.bolt", setOf(B53.TEAM1, B53.TEAM2, B53.HEAD, B53.CLUB), ThumbnailColorSets.FIGHT,
        listOf(TextPlace.CENTER),
        B53.roles(B53.players, B53.accent, B53.team1, B53.team2, B53.panel, ColorRole.SHADE to "share.use.behindWords"),
        StyleGroup.FIGHT, showsLogo = true,
    ),
    PLATES(
        "plates", "share.style.plates", setOf(B53.TEAM1, B53.TEAM2, B53.HEAD, B53.CLUB), ThumbnailColorSets.FIGHT,
        listOf(TextPlace.CENTER), B53.roles(B53.text, B53.accent, B53.onShapes, B53.team1, B53.team2, B53.panel),
        StyleGroup.FIGHT, showsLogo = true,
    ),
    SCORELINE(
        "scoreline", "share.style.scoreline", setOf(B53.HEAD, B53.TEAM1, B53.TEAM2, B53.SCORE, B53.CLUB),
        ThumbnailColorSets.FIGHT, listOf(TextPlace.RIGHT), B53.roles(B53.text, B53.accent, B53.onAccent, B53.side),
        StyleGroup.FIGHT, showsLogo = true,
    ),

    // ---------- B-53: Bold blocks ----------
    SPLIT_XL(
        "splitXl", "share.style.splitXl", setOf(B53.KIND, B53.HEAD, B53.TEAM1, B53.TEAM2, B53.EVENT, B53.ROUND, B53.CLUB),
        ThumbnailColorSets.LIME, listOf(TextPlace.RIGHT), B53.roles(B53.accent, B53.onShapes, B53.panel, B53.onPanel),
        StyleGroup.BOLD, showsLogo = true,
    ),
    CROSSOVER(
        "crossover", "share.style.crossover", setOf(B53.KIND, B53.HEAD, B53.TEAM1, B53.TEAM2, B53.CLUB),
        ThumbnailColorSets.LIME, listOf(TextPlace.RIGHT), B53.roles(B53.text, B53.accent, B53.onShapes, B53.side),
        StyleGroup.BOLD, showsLogo = true,
    ),
    BAND(
        "band", "share.style.band", setOf(B53.HEAD, B53.TEAM1, B53.TEAM2, B53.KIND, B53.EVENT, B53.CLUB),
        ThumbnailColorSets.LIME, listOf(TextPlace.BOTTOM), B53.roles(B53.accent, B53.onShapes, B53.panel, B53.onPanel),
        StyleGroup.BOLD, showsLogo = true,
    ),
    DUOTONE(
        "duotone", "share.style.duotone", setOf(B53.KIND, B53.HEAD, B53.TEAM1, B53.TEAM2, B53.CLUB),
        ThumbnailColorSets.LIME, listOf(TextPlace.RIGHT), B53.roles(B53.text, B53.accent), StyleGroup.BOLD, showsLogo = true,
    ),
    ECHO(
        "echo", "share.style.echo", setOf(B53.HEAD, B53.TEAM1, B53.TEAM2, B53.CLUB),
        ThumbnailColorSets.LIME, listOf(TextPlace.RIGHT), B53.roles(B53.text, B53.accent, B53.side), StyleGroup.BOLD, showsLogo = true,
    ),

    // ---------- B-53: Sports TV ----------
    SLASH(
        "slash", "share.style.slash", setOf(B53.KIND, B53.HEAD, B53.TEAM1, B53.TEAM2, B53.CLUB),
        ThumbnailColorSets.BROADCAST, listOf(TextPlace.RIGHT),
        B53.roles(
            B53.text, B53.accent, ColorRole.SECOND to "share.use.tagVs", B53.panel, ColorRole.CARD to "share.use.playersBox", B53.onCard,
        ),
        StyleGroup.TV, showsLogo = true,
    ),
    RANK(
        "rank", "share.style.rank", setOf(B53.HEAD, B53.TEAM1, B53.TEAM2, B53.CLUB),
        ThumbnailColorSets.BROADCAST, listOf(TextPlace.RIGHT), B53.roles(B53.text, B53.accent, B53.panel, B53.side), StyleGroup.TV, showsLogo = true,
    ),
    SCORE_BUG(
        "scoreBug", "share.style.scoreBug", setOf(B53.HEAD, B53.TEAM1, B53.TEAM2, B53.SCORE, B53.EVENT, B53.ROUND, B53.CLUB),
        ThumbnailColorSets.BROADCAST, listOf(TextPlace.RIGHT),
        B53.roles(
            ColorRole.TEXT to "share.use.playersInfo", B53.accent, B53.onAccent, ColorRole.SECOND to "share.use.infoBar", B53.panel,
            ColorRole.CARD to "share.use.scoreCells", B53.onCard, B53.side,
        ),
        StyleGroup.TV, showsLogo = true,
    ),
    LOWER_THIRD(
        "lowerThird", "share.style.lowerThird", setOf(B53.HEAD, B53.TEAM1, B53.TEAM2, B53.EVENT, B53.COURT, B53.CLUB),
        ThumbnailColorSets.BROADCAST, listOf(TextPlace.RIGHT),
        B53.roles(
            B53.players, B53.accent, B53.onAccent, B53.panel, ColorRole.CARD to "share.use.infoBar", B53.onCard,
            ColorRole.SHADE to "share.use.topBottom",
        ),
        StyleGroup.TV, showsLogo = true,
    ),
    ALERT(
        "alert", "share.style.alert", setOf(B53.HEAD, B53.ROUND, B53.KIND, B53.TEAM1, B53.TEAM2, B53.CLUB),
        ThumbnailColorSets.BROADCAST, listOf(TextPlace.RIGHT),
        B53.roles(B53.text, B53.accent, B53.onShapes, ColorRole.SECOND to "share.use.behindWords", B53.panel), StyleGroup.TV, showsLogo = true,
    ),
    ;

    val title: String get() = tr(titleKey)

    /** The color roles of the style, with the parts that use each role, in the app language. */
    val roles: Map<ColorRole, String>
        get() = roleKeys.entries.sortedBy { it.key.ordinal }.associate { it.key to tr(it.value) }

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
    val card: Int = 0xFFFFFF,
    val onCard: Int = 0x141414,
    val second: Int = 0xE3262E,
    val third: Int = 0x1F5BFF,
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
        val card = rgb(ColorRole.CARD) ?: WHITE
        return ThumbnailColors(
            text = rgb(ColorRole.TEXT) ?: WHITE,
            accent = accent,
            onAccent = rgb(ColorRole.ON_ACCENT) ?: onColor(accent),
            panel = panel,
            onPanel = rgb(ColorRole.ON_PANEL) ?: onColor(panel),
            shade = rgb(ColorRole.SHADE) ?: DARK_INK,
            card = card,
            // The panel color on a light box, as navy texts on a white card. Dark or white when the contrast is low.
            onCard = rgb(ColorRole.ON_CARD) ?: inkOn(card, panel),
            second = rgb(ColorRole.SECOND) ?: colorSet(style, look).second,
            third = rgb(ColorRole.THIRD) ?: colorSet(style, look).third,
            strength = strength(look),
        )
    }

    /** [preferred] on [background] when the contrast is at least 3, else dark or white. */
    fun inkOn(background: Int, preferred: Int): Int = if (contrast(background, preferred) >= 3.0) preferred else onColor(background)

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
