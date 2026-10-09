package org.litvin.projects

import org.junit.jupiter.api.io.TempDir
import org.litvin.license.AllowNewWork
import org.litvin.points.EdlIO
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.Sport
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-14: a new project keeps the sport and the rules of the "New project" dialog in score.json. */
class FileProjectsRepositorySportTest {
    @TempDir
    lateinit var dir: File

    private val repository by lazy { FileProjectsRepository(dir.resolve("projects"), AllowNewWork) }
    private val video by lazy { dir.resolve("match.mp4").apply { writeText("video") } }

    @Test
    fun `a padel project starts with its rules, team names, and the default scoreboard`() {
        val rules = MatchRulesV1(deuce = DeuceRule.NO_AD)
        val created = repository.createProject(video.absolutePath, "Club padel", Sport.PADEL, rules)

        val score = ScoreIO.readForProjectDir(EdlIO.projectDirFromManifest(created.path))
        assertEquals(Sport.PADEL, score.sport)
        assertEquals(rules, score.rules)
        assertEquals("Team 1", score.player1Name)
        assertEquals("Team 2", score.player2Name)
        assertTrue(score.useDefaultScoreboard)
        assertFalse(score.scoreSettingsReviewed, "The Scoring tab still asks for the team names")
        assertEquals(Sport.PADEL, repository.stats(created.path).sport)
    }

    @Test
    fun `a project without a sport has no score file`() {
        val created = repository.createProject(video.absolutePath, "Club tennis")

        assertFalse(ScoreIO.existsForProjectDir(EdlIO.projectDirFromManifest(created.path)))
        assertEquals(Sport.TENNIS, repository.stats(created.path).sport)
    }
}
