package org.litvin.ui.tabs.points

import org.litvin.adjustments.AdjustmentsStore
import org.litvin.media.MediaScreen
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.ui.flow.fakes.FakeMediaPlayer
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.flow.fakes.ScriptedDialogService
import java.awt.Component
import java.awt.EventQueue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A change that waits for the autosave goes to its own project when another project opens. A pending Start does not go to the next project. */
class PointsProjectSwitchTest {
    private lateinit var projectA: File
    private lateinit var projectB: File
    private var panel: SwingPointsPanel? = null

    @BeforeTest
    fun setUp() {
        System.setProperty("java.awt.headless", "true")
        projectA = createProject("points-switch-a", PointV1(id = "a-1", startMs = 0, endMs = 1_000))
        projectB = createProject("points-switch-b", PointV1(id = "b-1", startMs = 5_000, endMs = 6_000))
    }

    @AfterTest
    fun tearDown() {
        panel?.let { target -> SwingUtilities.invokeAndWait { target.close() } }
        projectA.deleteRecursively()
        projectB.deleteRecursively()
    }

    @Test
    fun openingAnotherProjectSavesThePendingChangeToTheFirstProject() {
        val player = FakeMediaPlayer(MediaScreen.POINTS)
        SwingUtilities.invokeAndWait {
            panel = SwingPointsPanel(
                player,
                AdjustmentsStore.legacySession(),
                Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-autosave") },
                ScriptedDialogService(),
            ).also { it.setProjectManifest(manifest(projectA)) }
        }
        val target = panel!!
        drainEventQueue()

        player.seek(2_000)
        SwingUtilities.invokeAndWait { target.actionMap.get("points.pointStart").actionPerformed(null) }
        player.seek(3_000)
        SwingUtilities.invokeAndWait { target.actionMap.get("points.scoreNone").actionPerformed(null) }
        // The change schedules the debounced autosave. Open project B before the autosave runs.
        SwingUtilities.invokeAndWait { }
        SwingUtilities.invokeAndWait { target.setProjectManifest(manifest(projectB)) }
        drainEventQueue()
        SwingUtilities.invokeAndWait { target.saveNow() }

        val savedA = EdlIO.readForProjectDir(projectA.absolutePath)
        assertEquals(listOf(0 to 1_000, 2_000 to 3_000), savedA.points.map { it.startMs to it.endMs })
        assertEquals(listOf("Rally A"), savedA.comments.map { it.text })
        val savedB = EdlIO.readForProjectDir(projectB.absolutePath)
        assertEquals(listOf("b-1"), savedB.points.map { it.id })
        assertEquals(listOf("Rally B"), savedB.comments.map { it.text })
    }

    @Test
    fun openingAnotherProjectClearsThePendingStart() {
        val dialogs = RecordingDialogs()
        val (player, target) = openPanel(dialogs)

        // A pending Start in project A, at a time that is free in project B.
        player.seek(2_000)
        SwingUtilities.invokeAndWait { target.actionMap.get("points.pointStart").actionPerformed(null) }
        SwingUtilities.invokeAndWait { target.setProjectManifest(manifest(projectB)) }
        drainEventQueue()
        player.seek(3_000)
        SwingUtilities.invokeAndWait { target.actionMap.get("points.scoreNone").actionPerformed(null) }
        drainEventQueue()
        SwingUtilities.invokeAndWait { target.saveNow() }

        // W finds no Start, so it makes no point.
        assertEquals(emptyList(), dialogs.messages)
        val savedB = EdlIO.readForProjectDir(projectB.absolutePath)
        assertEquals(listOf("b-1"), savedB.points.map { it.id })
    }

    @Test
    fun escapeCancelsThePendingStart() {
        val dialogs = RecordingDialogs()
        val (player, target) = openPanel(dialogs)

        player.seek(2_000)
        SwingUtilities.invokeAndWait { target.actionMap.get("points.pointStart").actionPerformed(null) }
        SwingUtilities.invokeAndWait { target.actionMap.get("points.cancelPending").actionPerformed(null) }
        player.seek(3_000)
        SwingUtilities.invokeAndWait { target.actionMap.get("points.scoreNone").actionPerformed(null) }
        drainEventQueue()
        SwingUtilities.invokeAndWait { target.saveNow() }

        // W finds no Start, so it makes no point.
        assertEquals(emptyList(), dialogs.messages)
        val savedA = EdlIO.readForProjectDir(projectA.absolutePath)
        assertEquals(listOf("a-1"), savedA.points.map { it.id })
    }

    /** Records the messages. The tests run headless, so no dialog can show. */
    private class RecordingDialogs : UserDialogService {
        val messages = mutableListOf<String>()
        override fun showInfo(parent: Component?, message: String, title: String) { messages += message }
        override fun showError(parent: Component?, message: String, title: String) { messages += message }
        override fun confirm(parent: Component?, message: String, title: String): Boolean = error("Unexpected confirm: $message")
    }

    private fun openPanel(dialogs: UserDialogService): Pair<FakeMediaPlayer, SwingPointsPanel> {
        val player = FakeMediaPlayer(MediaScreen.POINTS)
        SwingUtilities.invokeAndWait {
            panel = SwingPointsPanel(
                player,
                AdjustmentsStore.legacySession(),
                Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-autosave") },
                dialogs,
            ).also { it.setProjectManifest(manifest(projectA)) }
        }
        drainEventQueue()
        return player to panel!!
    }

    private fun createProject(prefix: String, point: PointV1): File {
        val dir = Files.createTempDirectory(prefix).toFile()
        val video = File(dir, "source.mp4").apply { writeText("not a real video") }
        ManifestIO.write(
            manifest(dir),
            ProjectManifestV1(
                id = prefix,
                name = prefix,
                createdAt = ManifestIO.nowIsoUtc(),
                lastOpenedAt = ManifestIO.nowIsoUtc(),
                sourceVideo = video.absolutePath,
            ),
        )
        val label = if (prefix.endsWith("a")) "Rally A" else "Rally B"
        EdlIO.writeForProjectDir(
            dir.absolutePath,
            EdlV1(
                points = listOf(point),
                comments = listOf(CommentV1(id = 1, startMs = 0, durationMs = 1_000, text = label)),
                nextCommentId = 2,
            ),
        )
        // The Scoring settings dialog cannot open in a headless test.
        ScoreIO.writeForProjectDir(dir.absolutePath, ScoreV1(scoreSettingsReviewed = true))
        return dir
    }

    private fun manifest(dir: File) = ManifestIO.manifestFilePath(dir.absolutePath)

    private fun drainEventQueue() {
        EventQueue.invokeAndWait { }
        Thread.sleep(50)
        EventQueue.invokeAndWait { }
    }
}
