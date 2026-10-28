package org.litvin.scoring

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import java.io.File

/** B-11 E3-T18: a project saves "not set" for a default text, so the text follows the app language. */
class DefaultTextsTest {
    @TempDir
    lateinit var dir: File

    @AfterEach
    fun restore() {
        I18n.install(Messages.english())
    }

    private fun file(): String = File(dir, "score.json").readText()

    @Test
    fun `a new project saves no default text`() {
        ScoreIO.writeForProjectDir(dir.path, ScoreV1.newProject(Sport.PADEL, Sport.PADEL.defaultRules()))
        val json = file()
        assertFalse("player1Name" in json, json)
        assertFalse("player2Name" in json, json)
        assertFalse("\"title\"" in json, json)
    }

    @Test
    fun `a default text of another language is also not set`() {
        val spanish = ScoreV1(
            player1Name = "Jugador 1",
            player2Name = "  ",
            scoreboard = ScoreboardSettingsV1(title = "Tu torneo o club"),
        )
        ScoreIO.writeForProjectDir(dir.path, spanish)
        assertFalse("Jugador" in file() || "torneo" in file() || "player2Name" in file(), file())
    }

    @Test
    fun `an old project with the English default texts reads the defaults of the app language`() {
        File(dir, "score.json").writeText(
            """{"version":1,"player1Name":"Team 1","player2Name":"Team 2","sport":"PADEL","scoreboard":{"title":"Your tournament or club"}}""",
        )
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        val score = ScoreIO.readForProjectDir(dir.path)
        assertEquals("Pareja 1", score.player1Name)
        assertEquals("Pareja 2", score.player2Name)
        assertEquals(ScoreboardSettingsV1.DEFAULT_TITLE, score.scoreboard.title)
        assertTrue(score.scoreboard.hasDefaultTitle())
    }

    @Test
    fun `a text that the user wrote stays as written`() {
        val score = ScoreV1(player1Name = "Jugador 1 ", player2Name = "Ana", scoreboard = ScoreboardSettingsV1(title = "Club final"))
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        ScoreIO.writeForProjectDir(dir.path, score)
        I18n.install(Messages.english())
        val read = ScoreIO.readForProjectDir(dir.path)
        assertEquals("Ana", read.player2Name)
        assertEquals("Club final", read.scoreboard.title)
        // "Jugador 1" is a default name of Spanish, so it is not set, and English shows its own default name.
        assertEquals("Player 1", read.player1Name)
    }

    /**
     * The reader of the current release (1.2.0) has the same Jackson settings and data classes, with the English
     * default texts. A plain mapper with these defaults stands in for it here.
     */
    @Test
    fun `the reader of the current release opens a file with not set texts, and a round trip keeps not set`() {
        ScoreIO.writeForProjectDir(dir.path, ScoreV1.newProject(Sport.TENNIS, Sport.TENNIS.defaultRules()))
        val olderReader = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val older = olderReader.readValue(File(dir, "score.json"), OlderScore::class.java)
        assertEquals("Player 1", older.player1Name)
        assertEquals("Your tournament or club", older.scoreboard.title)

        // The older build saves its English defaults. The new build reads them as not set and saves not set again.
        File(dir, "score.json").writeText(olderReader.writeValueAsString(older))
        val again = ScoreIO.readForProjectDir(dir.path)
        assertTrue(again.scoreboard.hasDefaultTitle())
        ScoreIO.writeForProjectDir(dir.path, again)
        assertFalse("player1Name" in file() || "\"title\"" in file(), file())
    }

    @Test
    fun `the test language shows its own default title in the video`() {
        I18n.install(Messages(AppLanguage.ENGLISH, Messages.english().let { emptyMap() }, org.litvin.i18n.MessageCatalog.load(AppLanguage.SOURCE), pseudo = true))
        val title = ScoreboardSettingsV1(title = "Your tournament or club").videoTitle()
        assertTrue(title.startsWith("[") && "Your" !in title, title)
    }

    /** The score file as the 1.2.0 build reads it: the side names and the title with English defaults. */
    data class OlderScore(
        val version: Int = 1,
        val player1Name: String = "Player 1",
        val player2Name: String = "Player 2",
        val scoreboard: OlderBoard = OlderBoard(),
    )

    data class OlderBoard(val title: String = "Your tournament or club")
}
