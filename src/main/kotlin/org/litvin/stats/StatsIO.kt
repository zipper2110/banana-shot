package org.litvin.stats

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.litvin.JsonFileIO
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.Sport
import java.io.File

/**
 * The statistics setup of a project. It is stored in stats.json in the project directory.
 *
 * The file is separate from score.json, because the Scoring tab writes score.json from its own copy of the data.
 */
data class StatsSettingsV1(
    val version: Int = 1,
    /** The [MatchStat.key] values of the rows that the video shows. The app ignores unknown keys. */
    val videoStats: List<String> = MatchStat.defaultVideoKeys,
    /** A short point lasts this time or less, from the start mark to the end mark. */
    val shortPointMaxSeconds: Int = DEFAULT_SHORT_POINT_MAX_SECONDS,
    /** A long point lasts this time or more, from the start mark to the end mark. */
    val longPointMinSeconds: Int = DEFAULT_LONG_POINT_MIN_SECONDS,
    /** True when the card has a page with the momentum chart. The default card has no chart page. */
    val videoMomentum: Boolean = false,
    /** How much of the video shows through the card panel, in percent. 0 is a solid panel. */
    val cardTransparencyPercent: Int = DEFAULT_CARD_TRANSPARENCY_PERCENT,
) {
    fun inVideo(stat: MatchStat): Boolean = stat.key in videoStats

    /** Returns a copy where [stat] is in the video or not. The keys keep the order of [MatchStat]. */
    fun withInVideo(stat: MatchStat, selected: Boolean): StatsSettingsV1 {
        val keys = videoStats.toMutableSet()
        if (selected) keys += stat.key else keys -= stat.key
        return copy(videoStats = MatchStat.entries.map { it.key }.filter { it in keys })
    }

    /**
     * Returns a copy with the point length limits and the card transparency in their ranges.
     * A long point is always longer than a short point.
     */
    fun normalized(): StatsSettingsV1 {
        val short = shortPointMaxSeconds.coerceIn(MIN_POINT_LIMIT_SECONDS, MAX_POINT_LIMIT_SECONDS - 1)
        return copy(
            shortPointMaxSeconds = short,
            longPointMinSeconds = longPointMinSeconds.coerceIn(short + 1, MAX_POINT_LIMIT_SECONDS),
            cardTransparencyPercent = cardTransparencyPercent.coerceIn(0, MAX_CARD_TRANSPARENCY_PERCENT),
        )
    }

    companion object {
        const val DEFAULT_SHORT_POINT_MAX_SECONDS = 10
        const val DEFAULT_LONG_POINT_MIN_SECONDS = 13
        // Padel points are usually longer than tennis points. These values are an estimate, not a measurement (P-5 of padel.md).
        const val PADEL_SHORT_POINT_MAX_SECONDS = 12
        const val PADEL_LONG_POINT_MIN_SECONDS = 18
        const val MIN_POINT_LIMIT_SECONDS = 1
        const val MAX_POINT_LIMIT_SECONDS = 120
        const val DEFAULT_CARD_TRANSPARENCY_PERCENT = 18
        /** More transparency makes the text difficult to read on a bright video. */
        const val MAX_CARD_TRANSPARENCY_PERCENT = 80

        /** The settings of a project without stats.json. */
        fun defaults(sport: Sport): StatsSettingsV1 = when (sport) {
            Sport.TENNIS -> StatsSettingsV1()
            Sport.PADEL -> StatsSettingsV1(
                shortPointMaxSeconds = PADEL_SHORT_POINT_MAX_SECONDS,
                longPointMinSeconds = PADEL_LONG_POINT_MIN_SECONDS,
            )
        }
    }
}

object StatsIO {
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    fun statsFilePath(projectDir: String): String = File(projectDir, "stats.json").absolutePath

    /** Returns the default settings of the project sport (from score.json) when the file does not exist. */
    fun readForProjectDir(projectDir: String): StatsSettingsV1 {
        val path = statsFilePath(projectDir)
        if (!File(path).exists()) return StatsSettingsV1.defaults(ScoreIO.readForProjectDir(projectDir).sport)
        return JsonFileIO.read(mapper, path, StatsSettingsV1::class.java)
    }

    fun writeForProjectDir(projectDir: String, settings: StatsSettingsV1) {
        JsonFileIO.writeAtomically(mapper, statsFilePath(projectDir), settings)
    }
}
