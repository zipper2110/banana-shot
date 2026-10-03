package org.litvin.ui.tabs.points

import org.litvin.adjustments.AdjustmentsStore
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.RecordingAnalytics
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.media.MediaScreen
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.ui.flow.fakes.FakeMediaPlayer
import org.litvin.ui.flow.fakes.ScriptedDialogService
import org.litvin.ui.tabs.points.ui.PointsTableView
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the selection behaviour of the Points tab cards:
 * a comment card has no active state, and Delete removes the selected point.
 */
class PointsCardSelectionTest {

    private lateinit var projectDir: File
    private var panel: SwingPointsPanel? = null

    @BeforeTest
    fun setUp() {
        System.setProperty("java.awt.headless", "true")
        projectDir = Files.createTempDirectory("points-comments").toFile()
        val video = File(projectDir, "source.mp4").apply { writeText("not a real video") }
        val manifest = ManifestIO.manifestFilePath(projectDir.absolutePath)
        ManifestIO.write(
            manifest,
            ProjectManifestV1(
                id = "p1",
                name = "Comments",
                createdAt = ManifestIO.nowIsoUtc(),
                lastOpenedAt = ManifestIO.nowIsoUtc(),
                sourceVideo = video.absolutePath,
            ),
        )
        EdlIO.writeForProjectDir(
            projectDir.absolutePath,
            EdlV1(
                points = listOf(PointV1(id = "pt-1", startMs = 0, endMs = 1_000)),
                comments = listOf(CommentV1(id = 7, startMs = 4_000, durationMs = 2_000, text = "Good depth", colorHex = "#FFFFFF")),
                nextCommentId = 8,
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        panel?.let { target -> SwingUtilities.invokeAndWait { target.close() } }
        projectDir.deleteRecursively()
    }

    @Test
    fun commentCardsHaveNoActiveStateAndDeleteRemovesTheSelectedPoint() {
        val player = FakeMediaPlayer(MediaScreen.POINTS)
        SwingUtilities.invokeAndWait {
            panel = SwingPointsPanel(
                player,
                AdjustmentsStore.legacySession(),
                Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-autosave") },
                ScriptedDialogService(),
            ).also { it.setProjectManifest(ManifestIO.manifestFilePath(projectDir.absolutePath)) }
        }
        val target = panel!!
        drainEventQueue()
        val cards = findCardsView(target)
        assertEquals(listOf("#1", "Comment #7"), cards.visibleTitles())

        // A playhead inside a comment must not give that card an active state.
        player.seek(4_500)
        drainEventQueue()
        drainEventQueue()
        assertNull(cards.selectedTitle(), "a comment must never become the active card")

        // A playhead inside a point still selects it, and Delete removes it
        // instead of recursing into itself.
        player.seek(500)
        drainEventQueue()
        drainEventQueue()
        assertEquals("#1", cards.selectedTitle())
        SwingUtilities.invokeAndWait {
            target.actionMap.get("points.delete").actionPerformed(null)
        }
        drainEventQueue()
        assertEquals(listOf("Comment #7"), cards.visibleTitles())
    }

    @Test
    fun analyticsCountNewPointsFavoritesAndDeletes() {
        val player = FakeMediaPlayer(MediaScreen.POINTS)
        val analytics = RecordingAnalytics()
        SwingUtilities.invokeAndWait {
            panel = SwingPointsPanel(
                player,
                AdjustmentsStore.legacySession(),
                Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-autosave") },
                ScriptedDialogService(),
                analytics = analytics,
            ).also { it.setProjectManifest(ManifestIO.manifestFilePath(projectDir.absolutePath)) }
        }
        val target = panel!!
        drainEventQueue()
        fun run(action: String, atMs: Long) {
            player.seek(atMs)
            drainEventQueue()
            drainEventQueue()
            SwingUtilities.invokeAndWait { target.actionMap.get(action).actionPerformed(null) }
            drainEventQueue()
        }

        run("points.toggleFavorite", 500)
        run("points.toggleFavorite", 500)
        run("points.pointStart", 2_000)
        run("points.pointEnd", 3_000)
        run("points.delete", 500)

        assertEquals(1, analytics.count(AnalyticsEvent.PointFavorited), "a favorite that is removed again does not count")
        assertEquals(1, analytics.count(AnalyticsEvent.PointAdded))
        assertEquals(1, analytics.count(AnalyticsEvent.PointDeleted))
    }

    private fun drainEventQueue() {
        EventQueue.invokeAndWait { }
        Thread.sleep(50)
        EventQueue.invokeAndWait { }
    }

    private fun findCardsView(root: Component): PointsTableView =
        findCardsViewOrNull(root) ?: error("PointsTableView not found")

    private fun findCardsViewOrNull(root: Component): PointsTableView? {
        if (root is PointsTableView) return root
        if (root is Container) {
            root.components.forEach { child ->
                findCardsViewOrNull(child)?.let { return it }
            }
        }
        return null
    }
}
