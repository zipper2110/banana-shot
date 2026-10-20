package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import org.litvin.i18n.tr
import org.litvin.i18n.I18n

/** The look of the scoreboard. The scoring preview and the export use the same style. */
enum class ScoreboardStyleId {
    @JsonEnumDefaultValue
    BROADCAST,
    CENTER_COURT,
    COMPACT,
    GRASS_COURT,
    CLAY_COURT,
    HARD_COURT,
    LED_BOARD,
    MINIMAL,
    TILES,
    TICKER,
    RETRO,
    BOLD_BLOCK,
    NEXT_GEN,
    VIOLET,
    CHALKBOARD,
    SUNSET,
    PADEL_GLASS,
    STADIUM,
    STADIUM_COMPACT,
    ;

    val title: String
        get() = when (this) {
            BROADCAST -> tr("style.broadcast")
            CENTER_COURT -> tr("style.centerCourt")
            COMPACT -> tr("style.compact")
            GRASS_COURT -> tr("style.grassCourt")
            CLAY_COURT -> tr("style.clayCourt")
            HARD_COURT -> tr("style.hardCourt")
            LED_BOARD -> tr("style.ledBoard")
            MINIMAL -> tr("style.minimal")
            TILES -> tr("style.tiles")
            TICKER -> tr("style.ticker")
            RETRO -> tr("style.retro")
            BOLD_BLOCK -> tr("style.boldBlock")
            NEXT_GEN -> tr("style.nextGen")
            VIOLET -> tr("style.violet")
            CHALKBOARD -> tr("style.chalkboard")
            SUNSET -> tr("style.sunset")
            PADEL_GLASS -> tr("style.padelGlass")
            STADIUM -> tr("style.stadium")
            STADIUM_COMPACT -> tr("style.stadiumCompact")
        }
}

/** The corner of the video that shows the scoreboard. */
enum class ScoreboardPosition {
    @JsonEnumDefaultValue
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    ;

    val title: String
        get() = when (this) {
            TOP_LEFT -> tr("position.topLeft")
            TOP_RIGHT -> tr("position.topRight")
            BOTTOM_LEFT -> tr("position.bottomLeft")
            BOTTOM_RIGHT -> tr("position.bottomRight")
        }
}

/**
 * Scoreboard settings of a project. They are stored in score.json.
 *
 * Null values use the default of the selected style.
 */
data class ScoreboardSettingsV1(
    val style: ScoreboardStyleId = ScoreboardStyleId.BROADCAST,
    val title: String = DEFAULT_TITLE,
    val showTitle: Boolean = true,
    /** Shows the [APP_CREDIT] line at the bottom of the scoreboard. */
    val showAppCredit: Boolean = true,
    /** Shows the color marker of each player next to the name. */
    val showPlayerColors: Boolean = true,
    /** Shows a ball next to the player who serves. The board shows it only when the server is known. */
    val showServe: Boolean = true,
    /** Shows a badge such as "GOLDEN POINT" or "STAR POINT" next to the board during a deciding point. */
    val showDecidingPoint: Boolean = true,
    val position: ScoreboardPosition = ScoreboardPosition.TOP_LEFT,
    val sizePercent: Int = 100,
    val backgroundOpacityPercent: Int? = null,
    val accentColorHex: String? = null,
) {
    /**
     * The title in the video. A [DEFAULT_TITLE] of an app language shows in the video language, because the user did
     * not write it (B-11 E5-T3). A function, so that the project file does not get it as a field.
     */
    fun videoTitle(): String = if (title.trim() in defaultTitles()) DEFAULT_TITLE else title

    /** Returns a copy with all values in their allowed ranges. */
    fun normalized(): ScoreboardSettingsV1 = copy(
        title = title.trim().take(MAX_TITLE_LENGTH),
        sizePercent = sizePercent.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT),
        backgroundOpacityPercent = backgroundOpacityPercent?.coerceIn(MIN_OPACITY_PERCENT, 100),
        accentColorHex = accentColorHex?.let(::normalizeHex),
    )

    companion object {
        /**
         * The title of a new project. A project saves it as a user text. Thus an older project keeps the English text,
         * and a project from a Spanish start keeps the Spanish text (see [defaultTitles]).
         */
        val DEFAULT_TITLE: String get() = tr("video.defaultTitle")

        /** [DEFAULT_TITLE] in all app languages. */
        fun defaultTitles(): Set<String> = I18n.inAllLanguages("video.defaultTitle")

        /** The credit line of the scoreboard. It is the brand text, the same in all languages. */
        const val APP_CREDIT = "BananaShot app"
        const val MAX_TITLE_LENGTH = 40
        const val MIN_SIZE_PERCENT = 50
        const val MAX_SIZE_PERCENT = 200
        const val MIN_OPACITY_PERCENT = 10

        private fun normalizeHex(value: String): String? {
            val clean = value.trim().removePrefix("#")
            if (clean.length != 6 || clean.toIntOrNull(16) == null) return null
            return "#" + clean.uppercase()
        }
    }
}
