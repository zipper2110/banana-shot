package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import org.litvin.JsonFileIO
import org.litvin.ScoreboardComponent

/**
 * Score v1 schema and JSON read/write helpers for scoring outcomes (task 4.9).
 *
 * Storage:
 * - File name: score.json placed in the project directory (next to edl.json and project.trproj)
 * - JSON library: Jackson Kotlin module (same configuration as EdlIO/ManifestIO)
 * - Unknown fields are ignored on read; pretty-printed; nulls omitted
 */

enum class Outcome { P1, P2, NONE }

data class ScoreV1(
    val outcomes: Map<String, Outcome> = emptyMap(),
    val version: Int = 1,
    /**
     * The side names. A default side name is "not set" (B-11 E3-T18): the file does not keep it, and a read gives the
     * default name of the sport in the app language (see [withDefaultTexts]).
     */
    @get:JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = DefaultSide1NameFilter::class)
    val player1Name: String = Sport.TENNIS.defaultSideName(1),
    @get:JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = DefaultSide2NameFilter::class)
    val player2Name: String = Sport.TENNIS.defaultSideName(2),
    val player1ColorHex: String = ScoreboardComponent.DEFAULT_PLAYER1_HEX,
    val player2ColorHex: String = ScoreboardComponent.DEFAULT_PLAYER2_HEX,
    val scoreboard: ScoreboardSettingsV1 = ScoreboardSettingsV1(),
    val rules: MatchRulesV1 = MatchRulesV1(),
    /** Game wins that the user marked by hand. The engine uses them only in manual scoring. */
    val manualGameWins: Map<String, Outcome> = emptyMap(),
    /** Set wins that the user marked by hand. The engine uses them only in manual scoring. */
    val manualSetWins: Map<String, Outcome> = emptyMap(),
    /** True after the Points tab showed the score settings for this project one time. */
    val scoreSettingsReviewed: Boolean = false,
    /** The server that the user marked on a point. The engine computes the server of the other points from these marks. */
    val serverMarks: Map<String, Outcome> = emptyMap(),
    /** The sport of the project. A file without this field is a tennis project. */
    val sport: Sport = Sport.TENNIS,
    /**
     * True when the Projects tab wrote this file for a new project (with the sport and the rules from the "New project"
     * dialog). The Points tab then gives the project the default scoreboard style, as for a project without score.json.
     */
    val useDefaultScoreboard: Boolean = false,
    /** The scores that the user typed for the end of a point (see [ScoreOverrideV1]). */
    val scoreOverrides: Map<String, ScoreOverrideV1> = emptyMap(),
) {
    fun manualMarks(): ManualScoreMarks = ManualScoreMarks(manualGameWins, manualSetWins, scoreOverrides)

    /**
     * A copy with the default texts of the app language: a blank side name or a default side name of any sport and
     * any language becomes [Sport.defaultSideName] of [sport], and a default title becomes the default title of the app
     * language. The file does not keep these texts, so they follow the app language (B-11 E3-T18).
     */
    fun withDefaultTexts(): ScoreV1 {
        val name1 = if (isDefaultSideName(player1Name, 1)) sport.defaultSideName(1) else player1Name
        val name2 = if (isDefaultSideName(player2Name, 2)) sport.defaultSideName(2) else player2Name
        val board = scoreboard.withDefaultTexts()
        return if (name1 == player1Name && name2 == player2Name && board == scoreboard) this
        else copy(player1Name = name1, player2Name = name2, scoreboard = board)
    }

    companion object {
        /** The score file of a new project of [sport] with [rules]. The side names are "Team 1" and "Team 2" for padel. */
        fun newProject(sport: Sport, rules: MatchRulesV1): ScoreV1 = ScoreV1(
            player1Name = sport.defaultSideName(1),
            player2Name = sport.defaultSideName(2),
            rules = rules.normalized(),
            sport = sport,
            useDefaultScoreboard = true,
        )

        /** True when [name] is blank or a default name of side [side] of any sport in any app language. */
        fun isDefaultSideName(name: String, side: Int): Boolean =
            name.isBlank() || Sport.entries.any { name.trim() in it.defaultSideNames(side) }
    }
}

/** Jackson leaves out a default name of side 1, so that the project file keeps "not set" (B-11 E3-T18). */
class DefaultSide1NameFilter {
    override fun equals(other: Any?): Boolean = other is String && ScoreV1.isDefaultSideName(other, 1)

    override fun hashCode(): Int = 0
}

/** Jackson leaves out a default name of side 2, so that the project file keeps "not set" (B-11 E3-T18). */
class DefaultSide2NameFilter {
    override fun equals(other: Any?): Boolean = other is String && ScoreV1.isDefaultSideName(other, 2)

    override fun hashCode(): Int = 0
}

object ScoreIO {
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    /** Returns absolute path to score.json for a given project directory. */
    fun scoreFilePath(projectDir: String): String = File(projectDir, "score.json").absolutePath

    /** Write ScoreV1 to the given file path. */
    fun write(scoreFilePath: String, score: ScoreV1) {
        JsonFileIO.writeAtomically(mapper, scoreFilePath, score)
    }

    /** Read ScoreV1 from the given file path; returns empty ScoreV1 if file does not exist. */
    fun read(scoreFilePath: String): ScoreV1 {
        val f = File(scoreFilePath)
        if (!f.exists()) return ScoreV1().withDefaultTexts()
        return JsonFileIO.read(mapper, f.path, ScoreV1::class.java).withDefaultTexts()
    }

    /** Convenience: read ScoreV1 for a given project directory. */
    fun readForProjectDir(projectDir: String): ScoreV1 = read(scoreFilePath(projectDir))

    /** Convenience: write ScoreV1 for a given project directory. */
    fun writeForProjectDir(projectDir: String, score: ScoreV1) = write(scoreFilePath(projectDir), score)

    /** True when the project directory has a score.json file. */
    fun existsForProjectDir(projectDir: String): Boolean = File(scoreFilePath(projectDir)).isFile

    /** Converts scoreboard settings to JSON text, for example to keep them as a user preference. */
    fun scoreboardToJson(settings: ScoreboardSettingsV1): String = mapper.writeValueAsString(settings)

    /** Converts match rules to JSON text, for example to keep them as a user preference. */
    fun rulesToJson(rules: MatchRulesV1): String = mapper.writeValueAsString(rules)

    /** Reads match rules from [rulesToJson] text. Returns null if the text is not valid. */
    fun rulesFromJson(json: String): MatchRulesV1? =
        try {
            mapper.readValue(json, MatchRulesV1::class.java).normalized()
        } catch (_: Exception) {
            null
        }

    /** Reads scoreboard settings from [scoreboardToJson] text. Returns null if the text is not valid. */
    fun scoreboardFromJson(json: String): ScoreboardSettingsV1? =
        try {
            mapper.readValue(json, ScoreboardSettingsV1::class.java).normalized().withDefaultTexts()
        } catch (_: Exception) {
            null
        }
}
