package org.litvin.ui.tabs.share

import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.share.InMemoryShareDefaults
import org.litvin.share.ShareIO
import org.litvin.share.ThumbnailStyle
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.KeyFrame
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.VideoFrameLoader
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwingSharePanelTest {
    private val projectDir: File = Files.createTempDirectory("share-panel").toFile()
    private val exportDir: File = File(projectDir, "exports").apply { mkdirs() }
    private val video = File(projectDir, "match.mp4").apply { writeText("not a real video") }
    private val manifest = File(projectDir, "match.trproj")
    private val defaults = InMemoryShareDefaults()

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun theTabStartsWithTheFirstFavoriteAndBestPoints() {
        writeProject(favorites = setOf(3, 5))
        val panel = openPanel()

        onEdt {
            assertEquals("p3", (panel.find<JComboBox<*>>("share-point")!!.selectedItem as org.litvin.share.SharePoint).id)
            assertEquals(org.litvin.share.ThumbnailVideoType.BEST_POINTS, panel.find<SegmentedChoice<*>>("share-type")!!.selected)
            assertEquals("BEST POINTS", panel.find<JTextField>("share-field-head")!!.text)
            assertEquals("Dima / Yana", panel.find<JTextField>("share-field-team1")!!.text)
            assertFalse(panel.find<NoteBox>("share-pick-note")!!.isVisible)
            assertEquals(ThumbnailStyle.entries.size, all(panel).filterIsInstance<StyleCard>().count())
            // A 1080p video: the zoom stops at 150 %.
            assertEquals(150, panel.find<JSlider>("share-zoom")!!.maximum)
            assertTrue(panel.find<AbstractButton>("share-save")!!.isEnabled)
        }
    }

    @Test
    fun withoutFavoritesTheTabSaysWhichPointItSelected() {
        writeProject(favorites = emptySet())
        val panel = openPanel()

        onEdt {
            assertEquals("p5", (panel.find<JComboBox<*>>("share-point")!!.selectedItem as org.litvin.share.SharePoint).id)
            assertEquals(org.litvin.share.ThumbnailVideoType.FULL_MATCH, panel.find<SegmentedChoice<*>>("share-type")!!.selected)
            assertTrue(panel.find<NoteBox>("share-pick-note")!!.isVisible)
            assertFalse(panel.find<AbstractButton>("share-favorites-only")!!.isEnabled)
        }
    }

    @Test
    fun saveWritesThePngAndTheProjectRemembersIt() {
        writeProject(favorites = setOf(3))
        val panel = openPanel()

        onEdt {
            assertEquals("Save thumbnail", panel.find<AbstractButton>("share-save")!!.text)
            panel.find<AbstractButton>("share-save")!!.doClick()
        }

        val png = File(exportDir, "Doubles final-best-points.png")
        assertTrue(png.isFile)
        val image = ImageIO.read(png)
        assertEquals(1280, image.width)
        assertEquals(720, image.height)
        assertTrue(png.length() < 2 * 1024 * 1024)
        onEdt {
            assertEquals("Replace thumbnail", panel.find<AbstractButton>("share-save")!!.text)
            assertTrue(panel.find<FileLine>("share-file-current")!!.text.startsWith("Best points · today"))
            assertEquals("Full match · not saved", panel.find<FileLine>("share-file-other")!!.text)

            panel.find<AbstractButton>("share-style-card")!!.doClick()
            assertTrue(panel.find<FileLine>("share-file-current")!!.text.endsWith("changed since"))
        }
        onEdt { panel.onDeactivated() }

        val stored = assertNotNull(ShareIO.readForProjectDir(projectDir.path))
        assertEquals("card", stored.style)
        assertEquals(png.absolutePath, stored.saved["best"]?.path)
    }

    @Test
    fun aReopenedProjectShowsTheStoredSettings() {
        writeProject(favorites = setOf(3))
        val first = openPanel()
        onEdt {
            first.find<AbstractButton>("share-next-point")!!.doClick()
            first.find<AbstractButton>("share-style-tv")!!.doClick()
            first.find<SegmentedChoice<*>>("share-place")!!.let { choice ->
                all(choice).filterIsInstance<AbstractButton>().first { it.name == "share-place-1" }.doClick()
            }
            first.find<JTextField>("share-field-event")!!.text = "Club league"
            first.onDeactivated()
        }

        val second = openPanel()
        onEdt {
            assertEquals("p4", (second.find<JComboBox<*>>("share-point")!!.selectedItem as org.litvin.share.SharePoint).id)
            assertTrue(second.find<StyleCard>("share-style-tv")!!.selectedStyle)
            assertEquals(org.litvin.share.TextPlace.TOP, second.find<SegmentedChoice<*>>("share-place")!!.selected)
            assertEquals("Club league", second.find<JTextField>("share-field-event")!!.text)
        }
    }

    @Test
    fun theNextProjectStartsWithTheLastStyleButNotWithTheTexts() {
        writeProject(favorites = setOf(3))
        val first = openPanel()
        onEdt {
            first.find<AbstractButton>("share-style-poster")!!.doClick()
            first.find<JTextField>("share-field-team1")!!.text = "Someone else"
            first.onDeactivated()
        }
        File(projectDir, "share.json").delete()

        val second = openPanel()
        onEdt {
            assertTrue(second.find<StyleCard>("share-style-poster")!!.selectedStyle)
            assertEquals("Dima / Yana", second.find<JTextField>("share-field-team1")!!.text)
        }
    }

    @Test
    fun theTextsTabShowsOnlyTheFieldsOfTheStyle() {
        writeProject(favorites = setOf(3))
        val panel = openPanel()

        fun shown(field: String) = generateSequence<Component>(panel.find<JTextField>("share-field-$field")) { it.parent }
            .takeWhile { it !== panel }
            .all { it.isVisible }

        onEdt {
            // Big words shows only the large words and the match type.
            assertTrue(shown("head"))
            assertTrue(shown("kind"))
            assertFalse(shown("team1"))
            assertFalse(shown("date"))

            panel.find<AbstractButton>("share-style-poster")!!.doClick()
            assertTrue(shown("team1"))
            assertTrue(shown("date"))
            assertEquals("Dima / Yana", panel.find<JTextField>("share-field-team1")!!.text)
        }
    }

    @Test
    fun theFinalScoreIsAChoiceAndNeverTheDefault() {
        writeProject(favorites = setOf(3))
        val panel = openPanel()

        onEdt {
            val score = panel.find<ChipButton>("share-head-score")!!
            // The project has no scored points, so it has no final score.
            assertFalse(score.isEnabled)
            assertEquals("BEST POINTS", panel.find<JTextField>("share-field-head")!!.text)
        }
    }

    @Test
    fun withoutPointsTheTabOffersThePointsTab() {
        writeProject(favorites = emptySet(), count = 0)
        var opened = 0
        val panel = openPanel(onOpenPoints = { opened++ }, waitForFrame = false)

        onEdt {
            panel.find<AbstractButton>("share-open-points")!!.doClick()
            assertEquals(1, opened)
        }
    }

    @Test
    fun aFrameThatTheAppCannotReadDisablesSave() {
        writeProject(favorites = setOf(3))
        val panel = openPanel(frames = VideoFrameLoader { null })

        onEdt {
            assertFalse(panel.find<AbstractButton>("share-save")!!.isEnabled)
            assertNotNull(panel.find<ThumbnailView>("share-preview")!!.painter.message)
        }
    }

    @Test
    fun whileTheFrameLoadsThePreviewShowsTheNearestKeyFrame() {
        writeProject(favorites = setOf(3))
        val release = CountDownLatch(1)
        val slow = VideoFrameLoader { request ->
            release.await(5, TimeUnit.SECONDS)
            BufferedImage(request.width, request.width * 9 / 16, BufferedImage.TYPE_INT_RGB)
        }
        val panel = openPanel(waitForFrame = false, frames = slow)

        waitFor { panel.find<MomentStrip>("share-strip")!!.cells.all { it != null } }
        onEdt {
            assertEquals(320, panel.find<ThumbnailView>("share-preview")!!.painter.frame?.width)
            assertFalse(panel.find<AbstractButton>("share-save")!!.isEnabled)
        }
        release.countDown()
        waitFor { panel.find<ThumbnailView>("share-preview")!!.painter.frame?.width == 1920 }
        onEdt { assertTrue(panel.find<AbstractButton>("share-save")!!.isEnabled) }
    }

    // ---------- Helpers ----------

    private fun writeProject(favorites: Set<Int>, count: Int = 9) {
        ManifestIO.write(
            manifest.path,
            ProjectManifestV1(id = "id", name = "Doubles final", createdAt = "2026-10-11T10:00:00Z", lastOpenedAt = "2026-10-11T10:00:00Z", sourceVideo = video.path),
        )
        val points = (1..count).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 6_000, favorite = it in favorites) }
        EdlIO.writeForProjectDir(projectDir.path, EdlV1(points = points))
        ScoreIO.writeForProjectDir(projectDir.path, ScoreV1(player1Name = "Dima / Yana", player2Name = "Iliya / Marina"))
    }

    private fun openPanel(
        onOpenPoints: () -> Unit = {},
        waitForFrame: Boolean = true,
        frames: VideoFrameLoader = VideoFrameLoader { request -> BufferedImage(request.width, request.width * 9 / 16, BufferedImage.TYPE_INT_RGB) },
    ): SwingSharePanel {
        lateinit var panel: SwingSharePanel
        onEdt {
            panel = SwingSharePanel(
                noDialogs(),
                defaults,
                exportFolder = { exportDir },
                onOpenPoints = onOpenPoints,
                frameLoader = frames,
                stripLoader = keyFrames(),
                probeSource = { org.litvin.ui.tabs.share.ShareSourceFacts(1920, 1080, 30.0) },
            )
            panel.setSize(1280, 800)
            panel.setProjectManifest(manifest.path)
            panel.onActivated()
        }
        // The probe and the frame load on background threads. After the probe, the frame has the width for the zoom.
        if (waitForFrame) {
            waitFor { panel.find<JSlider>("share-zoom")!!.maximum == 150 }
            waitFor { panel.find<ThumbnailView>("share-preview")!!.painter.let { it.frame?.width == 1920 || it.message != null } }
        }
        return panel
    }

    /** One key frame for each second of the point, as from a camera. */
    private fun keyFrames() = VideoFrameLoader(extractKeyFrames = { range ->
        (range.startMs until range.endMs step 1_000).map { KeyFrame(it, BufferedImage(range.width, range.width * 9 / 16, BufferedImage.TYPE_INT_RGB)) }
    }) { null }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) {
            var done = false
            SwingUtilities.invokeAndWait { done = runCatching(condition).getOrDefault(false) }
            if (done) return
            Thread.sleep(20)
        }
    }

    private fun noDialogs(): UserDialogService =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(UserDialogService::class.java)) { _, method, _ ->
            error("Unexpected dialog: ${method.name}")
        } as UserDialogService

    private fun onEdt(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { failure = runCatching(block).exceptionOrNull() }
        failure?.let { throw it }
    }

    private inline fun <reified T : Component> Container.find(name: String): T? = all(this).filterIsInstance<T>().firstOrNull { it.name == name }

    private fun all(container: Container): Sequence<Component> = container.components.asSequence().flatMap { child ->
        sequenceOf(child) + if (child is Container) all(child) else emptySequence()
    }
}
