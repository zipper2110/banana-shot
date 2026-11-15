package org.litvin.ui.tabs.points

import org.litvin.SessionSettings
import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.MediaScreen
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.ui.flow.fakes.FakeMediaPlayer
import org.litvin.ui.flow.fakes.ScriptedDialogService
import java.awt.EventQueue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** With a trim (B-62), the playhead of the Points tab stays between the trim start and the trim end. */
class PointsTrimPlayheadTest {
    private lateinit var project: File
    private var panel: SwingPointsPanel? = null
    private var frameStepBefore = false

    @BeforeTest
    fun setUp() {
        System.setProperty("java.awt.headless", "true")
        frameStepBefore = SessionSettings.frameStepWhenPaused
        project = createProject()
    }

    @AfterTest
    fun tearDown() {
        SessionSettings.frameStepWhenPaused = frameStepBefore
        panel?.let { target -> SwingUtilities.invokeAndWait { target.close() } }
        // The adjustments store is shared. Remove the trim for the next tests.
        AdjustmentsStore.legacySession().set(AdjustmentsV1())
        project.deleteRecursively()
    }

    @Test
    fun aLoadedVideoStartsAtTheTrimStart() {
        val (player, _) = openPanel()

        // The fake player starts at 0, as a new video does.
        assertEquals(0L, player.currentTimeMs())
        loadVideo(player)

        assertEquals(TRIM_START_MS, player.currentTimeMs())
    }

    @Test
    fun theArrowKeysDoNotSeekOutOfTheTrim() {
        SessionSettings.frameStepWhenPaused = false
        val (player, target) = openPanel()
        loadVideo(player)

        player.seek(TRIM_START_MS + 1_000)
        press(target, "points.seekLeftBig")
        assertEquals(TRIM_START_MS, player.currentTimeMs())
        press(target, "points.seekLeft")
        assertEquals(TRIM_START_MS, player.currentTimeMs())

        player.seek(TRIM_END_MS - 1_000)
        press(target, "points.seekRightBig")
        assertEquals(TRIM_END_MS, player.currentTimeMs())
        press(target, "points.seekRight")
        assertEquals(TRIM_END_MS, player.currentTimeMs())
    }

    @Test
    fun theFrameStepsDoNotGoOutOfTheTrim() {
        SessionSettings.frameStepWhenPaused = true
        val (player, target) = openPanel()
        loadVideo(player)

        press(target, "points.seekLeft")
        assertEquals(TRIM_START_MS, player.currentTimeMs())

        player.seek(TRIM_END_MS)
        press(target, "points.seekRight")
        assertEquals(TRIM_END_MS, player.currentTimeMs())

        // A playhead outside the trim goes into the trim first.
        player.seek(0)
        press(target, "points.seekRight")
        assertEquals(TRIM_START_MS, player.currentTimeMs())
    }

    @Test
    fun theTabMovesThePlayheadIntoTheTrimWhenItOpens() {
        val (player, target) = openPanel()
        loadVideo(player)

        // The user sets a later trim start on the Trim sub-tab, then opens the Points tab again.
        AdjustmentsStore.legacySession().set { it.copy(trimStartMs = 5_000L) }
        SwingUtilities.invokeAndWait { target.onActivated() }
        drainEventQueue()

        assertEquals(5_000L, player.currentTimeMs())
    }

    private fun press(target: SwingPointsPanel, action: String) {
        SwingUtilities.invokeAndWait { target.actionMap.get(action).actionPerformed(null) }
        drainEventQueue()
    }

    /** The tests run headless, so the panel does not load the video. This sends the event of a loaded video. */
    private fun loadVideo(player: FakeMediaPlayer) {
        SwingUtilities.invokeAndWait { player.onReady?.invoke() }
        drainEventQueue()
    }

    private fun openPanel(): Pair<FakeMediaPlayer, SwingPointsPanel> {
        val player = FakeMediaPlayer(MediaScreen.POINTS)
        SwingUtilities.invokeAndWait {
            panel = SwingPointsPanel(
                player,
                AdjustmentsStore.legacySession(),
                Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-autosave") },
                ScriptedDialogService(),
            ).also { it.setProjectManifest(ManifestIO.manifestFilePath(project.absolutePath)) }
        }
        drainEventQueue()
        return player to panel!!
    }

    private fun createProject(): File {
        val dir = Files.createTempDirectory("points-trim").toFile()
        val video = File(dir, "source.mp4").apply { writeText("not a real video") }
        ManifestIO.write(
            ManifestIO.manifestFilePath(dir.absolutePath),
            ProjectManifestV1(
                id = "points-trim",
                name = "points-trim",
                createdAt = ManifestIO.nowIsoUtc(),
                lastOpenedAt = ManifestIO.nowIsoUtc(),
                sourceVideo = video.absolutePath,
            ),
        )
        EdlIO.writeForProjectDir(dir.absolutePath, EdlV1())
        // The Scoring settings dialog cannot open in a headless test.
        ScoreIO.writeForProjectDir(dir.absolutePath, ScoreV1(scoreSettingsReviewed = true))
        AdjustmentsIO.writeForProjectDir(dir.absolutePath, AdjustmentsV1(trimStartMs = TRIM_START_MS, trimEndMs = TRIM_END_MS))
        return dir
    }

    private fun drainEventQueue() {
        EventQueue.invokeAndWait { }
        Thread.sleep(50)
        EventQueue.invokeAndWait { }
    }

    private companion object {
        const val TRIM_START_MS = 3_000L
        const val TRIM_END_MS = 8_000L
    }
}
