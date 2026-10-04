package org.litvin.ui.tabs.scoring

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.RecordingAnalytics
import org.litvin.media.MediaScreen
import org.litvin.points.CommentDefaultsV1
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.flow.fakes.FakeMediaPlayer
import org.litvin.ui.tabs.scoring.ui.OutcomeButton
import org.litvin.ui.tabs.scoring.ui.PlusButton
import org.litvin.ui.tabs.scoring.ui.ScoringButton
import java.awt.Component
import java.awt.Container
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ScheduledThreadPoolExecutor
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Scoring tab with a fake player: the score panel, the list and the saved score work together. */
class SwingScoringPanelFlowTest {
    private val scheduler = ScheduledThreadPoolExecutor(1)
    private val adjustments = AdjustmentsSession(scheduler, 60_000)
    private val projectDir: File = Files.createTempDirectory("scoring-flow-").toFile()
    private val manifestPath = File(projectDir, "project.trproj").absolutePath
    private val errors = mutableListOf<String>()
    private val analytics = RecordingAnalytics()
    private lateinit var panel: SwingScoringPanel

    private val dialogs = object : UserDialogService {
        override fun showInfo(parent: Component?, message: String, title: String) = Unit
        override fun showError(parent: Component?, message: String, title: String) {
            errors += "$title: $message"
        }
        override fun confirm(parent: Component?, message: String, title: String) = false
    }

    init {
        ManifestIO.write(
            manifestPath,
            ProjectManifestV1(
                id = "123e4567-e89b-42d3-a456-426614174000",
                name = "Match",
                createdAt = "2026-09-29T00:00:00Z",
                lastOpenedAt = "2026-09-29T00:00:00Z",
                sourceVideo = File(projectDir, "missing.mp4").absolutePath,
            ),
        )
        EdlIO.writeForProjectDir(
            projectDir.absolutePath,
            EdlV1((1..3).map { PointV1(id = "p$it", startMs = it * 2_000, endMs = it * 2_000 + 1_500) }),
        )
    }

    private fun open(score: ScoreV1 = ScoreV1(player1Name = "Alex", player2Name = "Sam", scoreSettingsReviewed = true)) {
        ScoreIO.writeForProjectDir(projectDir.absolutePath, score)
        SwingUtilities.invokeAndWait {
            panel = SwingScoringPanel(FakeMediaPlayer(MediaScreen.SCORING), adjustments, dialogs, analytics = analytics)
            panel.setSize(1400, 900)
            panel.setProjectManifest(manifestPath)
            panel.onActivated()
            layout(panel)
        }
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeAndWait { if (::panel.isInitialized) panel.close() }
        assertEquals(emptyList(), errors)
        scheduler.shutdownNow()
        projectDir.deleteRecursively()
    }

    @Test
    fun scoringAPointSavesItAndMakesNextTheLimeStep() {
        open()
        SwingUtilities.invokeAndWait {
            assertEquals("Point 1 / 3", find<JLabel>("current-point-label").text)
            val next = find<ScoringButton>("next-point")
            assertEquals(ScoringButton.Kind.SECONDARY, next.kind)
            assertEquals("Alex", find<OutcomeButton>("scoring-player-1-point").text)

            find<OutcomeButton>("scoring-player-1-point").doClick()

            assertEquals(Outcome.P1, ScoreIO.readForProjectDir(projectDir.absolutePath).outcomes["p1"])
            assertEquals("15", find<JLabel>("scoring-score-summary").text)
            assertEquals(ScoringButton.Kind.LIME, next.kind)
            assertTrue(find<OutcomeButton>("scoring-player-1-point").isOn)

            next.doClick()
            assertEquals("Point 2 / 3", find<JLabel>("current-point-label").text)
            assertTrue(find<AbstractButton>("previous-point").isEnabled)
        }
    }

    @Test
    fun analyticsCountEachNewOutcomeAndEachPointThatBecomesAFavorite() {
        open()
        SwingUtilities.invokeAndWait {
            find<OutcomeButton>("scoring-player-1-point").doClick()
            find<OutcomeButton>("scoring-player-2-point").doClick()
            val favorite = panel.actionMap.get("toggleFavorite")
            favorite.actionPerformed(java.awt.event.ActionEvent(panel, java.awt.event.ActionEvent.ACTION_PERFORMED, "test"))
            favorite.actionPerformed(java.awt.event.ActionEvent(panel, java.awt.event.ActionEvent.ACTION_PERFORMED, "test"))
        }
        assertEquals(2, analytics.count(AnalyticsEvent.ScoreRecorded))
        assertEquals(1, analytics.count(AnalyticsEvent.PointFavorited))
    }

    @Test
    fun favoriteKeepsTheCommentsOfTheProject() {
        val comments = listOf(
            CommentV1(id = 1, startMs = 500, durationMs = 2_000, text = "Serve", colorHex = "#FF0000"),
            CommentV1(id = 4, startMs = 3_000, durationMs = 1_000, text = "Rally"),
        )
        EdlIO.writeForProjectDir(
            projectDir.absolutePath,
            EdlIO.readForProjectDir(projectDir.absolutePath).copy(
                comments = comments,
                commentDefaults = CommentDefaultsV1("#FF0000"),
                nextCommentId = 5,
            ),
        )
        open()
        SwingUtilities.invokeAndWait {
            val favorite = panel.actionMap.get("toggleFavorite")
            favorite.actionPerformed(java.awt.event.ActionEvent(panel, java.awt.event.ActionEvent.ACTION_PERFORMED, "test"))
        }

        val saved = EdlIO.readForProjectDir(projectDir.absolutePath)
        assertTrue(saved.points.first { it.id == "p1" }.favorite)
        assertEquals(comments, saved.comments)
        assertEquals(CommentDefaultsV1("#FF0000"), saved.commentDefaults)
        assertEquals(5, saved.nextCommentId)
    }

    @Test
    fun activationKeepsTheSelectedPoint() {
        open()
        SwingUtilities.invokeAndWait {
            panel.selectPoint("p3")
            assertEquals("Point 3 / 3", find<JLabel>("current-point-label").text)
            panel.onDeactivated()
            panel.onActivated()
            assertEquals("Point 3 / 3", find<JLabel>("current-point-label").text)
        }
    }

    @Test
    fun goToPointOpensThePointsTabAtThatPoint() {
        open()
        val opened = mutableListOf<String>()
        SwingUtilities.invokeAndWait {
            panel.onGoToPoint = { opened += it }
            find<AbstractButton>("scoring-go-to-point-p2").doClick()
            assertEquals(listOf("p2"), opened)
            assertEquals("Point 2 / 3", find<JLabel>("current-point-label").text)
        }
    }

    @Test
    fun manualScoringUsesThePlusButtons() {
        open(ScoreV1(rules = MatchRulesV1(manualScoring = true), scoreSettingsReviewed = true))
        SwingUtilities.invokeAndWait {
            val gamePlus = find<PlusButton>("scoring-player-2-game-plus")
            assertTrue(gamePlus.isVisible)
            assertFalse(find<Component>("scoring-player-2-game-won").isVisible)

            gamePlus.doClick()
            assertEquals(Outcome.P2, ScoreIO.readForProjectDir(projectDir.absolutePath).manualGameWins["p1"])
            assertTrue(gamePlus.on)
            gamePlus.doClick()
            assertEquals(null, ScoreIO.readForProjectDir(projectDir.absolutePath).manualGameWins["p1"])

            find<PlusButton>("scoring-player-2-point-plus").doClick()
            assertEquals(Outcome.P2, ScoreIO.readForProjectDir(projectDir.absolutePath).outcomes["p1"])
        }
    }

    @Test
    fun keyBindingsStayInstalled() {
        open()
        SwingUtilities.invokeAndWait {
            for (action in listOf("togglePlayPause", "scoreP1", "scoreNone", "scoreP2", "nextPoint", "previousPoint", "toggleFavorite", "switchServe", "toggleFrameStep")) {
                assertNotNull(panel.actionMap.get(action), action)
            }
        }
    }

    private fun layout(component: Component) {
        component.doLayout()
        if (component is Container) component.components.forEach(::layout)
    }

    private inline fun <reified T : Component> find(name: String): T {
        val found = search(panel, name)
        assertNotNull(found, "No component named $name")
        return found as T
    }

    private fun search(root: Component, name: String): Component? {
        if (root.name == name) return root
        if (root is Container) root.components.forEach { child -> search(child, name)?.let { return it } }
        return null
    }
}
