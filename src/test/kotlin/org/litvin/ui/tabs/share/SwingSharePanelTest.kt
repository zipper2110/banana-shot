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
import org.litvin.share.StyleGroup
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
    fun theGroupRowShowsTheCardsOfOneGroup() {
        writeProject(favorites = setOf(3))
        val panel = openPanel()

        onEdt {
            fun visibleCards(): List<String?> = all(panel).filterIsInstance<StyleCard>().filter { it.isVisible }.map { it.name }.toList()
            assertEquals<List<String?>>(ThumbnailStyle.entries.filter { it.group == StyleGroup.CLASSIC }.map { "share-style-${it.key}" }, visibleCards())

            all(panel).filterIsInstance<AbstractButton>().first { it.name == "share-style-group-${StyleGroup.FIGHT.ordinal}" }.doClick()
            assertEquals<List<String?>>(StyleGroup.FIGHT.styles.map { "share-style-${it.key}" }, visibleCards())
            // The group row does not change the style.
            assertTrue(panel.find<StyleCard>("share-style-words")!!.selectedStyle)

            panel.find<AbstractButton>("share-style-clash")!!.doClick()
            assertTrue(panel.find<StyleCard>("share-style-clash")!!.selectedStyle)
            assertEquals(StyleGroup.FIGHT, panel.find<SegmentedChoice<*>>("share-style-group")!!.selected)
        }
    }

    @Test
    fun theUserAddsALogoAndItGoesToTheNextProject() {
        writeProject(favorites = setOf(3))
        val source = File(projectDir, "club.png").also { ImageIO.write(BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB), "png", it) }
        val logoFolder = File(projectDir, "app-data")
        val panel = openPanel(logoFolder = logoFolder, chooseLogo = { source })

        onEdt {
            // The classic styles have no logo.
            assertFalse(panel.find<Component>("share-logo")!!.isVisible)
            panel.find<AbstractButton>("share-style-splash")!!.doClick()
            assertTrue(panel.find<Component>("share-logo")!!.isVisible)
            assertFalse(panel.find<AbstractButton>("share-logo-remove")!!.isVisible)

            panel.find<AbstractButton>("share-logo-add")!!.doClick()

            assertTrue(File(logoFolder, "logo.png").isFile)
            assertEquals(File(logoFolder, "logo.png").absolutePath, defaults.load().logo)
            assertTrue(panel.find<AbstractButton>("share-logo-remove")!!.isVisible)
            val layout = panel.find<ThumbnailView>("share-preview")!!.painter.layout!!
            assertTrue(layout.items.any { it is org.litvin.share.ThumbnailRenderer.Item.Picture })

            // The brand name goes to all projects as the user types. No save is necessary.
            panel.find<JTextField>("share-field-club")!!.text = "Batumi beach club"
            assertEquals("Batumi beach club", defaults.load().club)
        }

        val second = openPanel(logoFolder = logoFolder)
        onEdt {
            assertEquals("Batumi beach club", second.find<JTextField>("share-field-club")!!.text)
            assertTrue(second.find<AbstractButton>("share-logo-remove")!!.isVisible)

            second.find<AbstractButton>("share-logo-remove")!!.doClick()
            assertEquals(null, defaults.load().logo)
            val layout = second.find<ThumbnailView>("share-preview")!!.painter.layout!!
            assertFalse(layout.items.any { it is org.litvin.share.ThumbnailRenderer.Item.Picture })
        }
    }

    @Test
    fun theBrandNameHasAFontAndAColorForAllStyles() {
        writeProject(favorites = setOf(3))
        val panel = openPanel()

        onEdt {
            panel.find<AbstractButton>("share-style-splash")!!.doClick()
            val fonts = panel.find<JComboBox<*>>("share-brand-font")!!
            assertEquals(0, fonts.selectedIndex)
            assertTrue(panel.find<ColorSwatch>("share-brand-color-auto")!!.chosen)

            fonts.selectedIndex = 1 + org.litvin.share.ThumbnailFace.BRAND.indexOf(org.litvin.share.ThumbnailFace.MARKER)
            panel.find<AbstractButton>("share-brand-color-magenta")!!.doClick()

            assertEquals("MARKER", defaults.load().brandFont)
            assertEquals("#ff2e88", defaults.load().brandColor)
            assertTrue(panel.find<ColorSwatch>("share-brand-color-magenta")!!.chosen)

            // The font and the color stay when the user selects another style.
            panel.find<AbstractButton>("share-style-scoreBug")!!.doClick()
            assertEquals(1 + org.litvin.share.ThumbnailFace.BRAND.indexOf(org.litvin.share.ThumbnailFace.MARKER), fonts.selectedIndex)

            fonts.selectedIndex = 0
            panel.find<AbstractButton>("share-brand-color-auto")!!.doClick()
            assertEquals(null, defaults.load().brandFont)
            assertEquals(null, defaults.load().brandColor)
        }
    }

    @Test
    fun aFileThatIsNotAnImageShowsAnErrorAndKeepsTheOldLogo() {
        writeProject(favorites = setOf(3))
        val source = File(projectDir, "notes.png").apply { writeText("not an image") }
        val errors = mutableListOf<String>()
        val panel = openPanel(logoFolder = File(projectDir, "app-data"), chooseLogo = { source }, onError = { errors += it })

        onEdt {
            panel.find<AbstractButton>("share-style-splash")!!.doClick()
            panel.find<AbstractButton>("share-logo-add")!!.doClick()

            assertEquals(1, errors.size)
            assertEquals(null, defaults.load().logo)
            assertFalse(panel.find<AbstractButton>("share-logo-remove")!!.isVisible)
        }
    }

    @Test
    fun theScoreChipFillsTheScoreWithTheFinalScore() {
        writeProject(favorites = setOf(3))
        val panel = openPanel()

        onEdt {
            panel.find<AbstractButton>("share-style-scoreline")!!.doClick()
            val score = panel.find<JTextField>("share-field-score")!!
            assertEquals("", score.text)
            val chip = panel.find<AbstractButton>("share-score-final")!!
            if (chip.isEnabled) {
                chip.doClick()
                assertTrue(score.text.isNotEmpty())
            }
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
    fun whileTheFrameLoadsThePreviewDoesNotShowAKeyFrameOfTheStrip() {
        writeProject(favorites = setOf(3))
        val release = CountDownLatch(1)
        val slow = VideoFrameLoader { request ->
            release.await(5, TimeUnit.SECONDS)
            BufferedImage(request.width, request.width * 9 / 16, BufferedImage.TYPE_INT_RGB)
        }
        val panel = openPanel(waitForFrame = false, frames = slow)

        waitFor { panel.find<MomentStrip>("share-strip")!!.cells.all { it != null } }
        onEdt {
            // A key frame and then the exact frame look like a flicker.
            assertEquals(null, panel.find<ThumbnailView>("share-preview")!!.painter.frame)
            assertFalse(panel.find<AbstractButton>("share-save")!!.isEnabled)
        }
        release.countDown()
        waitFor { panel.find<ThumbnailView>("share-preview")!!.painter.frame?.width == 1920 }
        onEdt { assertTrue(panel.find<AbstractButton>("share-save")!!.isEnabled) }
    }

    @Test
    fun theLargePreviewIsAVideoPlayerAtTheMomentWithTheZoomAndTheTexts() {
        writeProject(favorites = setOf(3))
        val player = RecordingPlayer()
        val panel = openPanel(player = player)
        waitFor { player.seeks.isNotEmpty() }

        onEdt {
            assertEquals(listOf(video.absolutePath), player.loads)
            // Point 3 is 30.0 to 36.0 s. The moment is the middle. The frame is the first frame after 32.960 s, as in ffmpeg.
            assertEquals(listOf(32_965L), player.seeks)
            assertNotNull(player.overlay)
            assertEquals(java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE, player.overlay!!.render(320, 180).type)
            // Until the player shows a frame, the preview shows the frame image.
            assertTrue(panel.find<ThumbnailView>("share-preview")!!.isVisible)
            assertFalse(player.component.isVisible)
        }

        player.onPlaybackRestart?.invoke()
        waitFor { player.component.isVisible }
        onEdt {
            assertFalse(panel.find<ThumbnailView>("share-preview")!!.isVisible)
            panel.find<JSlider>("share-zoom")!!.value = 150
        }
        waitFor { player.view != null }
        onEdt {
            val view = player.view!!
            assertEquals(1 / 1.5, view.zoomWidth, 1e-9)
            assertEquals((1 - 1 / 1.5) * 0.5, view.zoomX, 1e-9)
            assertEquals((1 - 1 / 1.5) * 0.5, view.zoomY, 1e-9)

            // A click on the strip: one exact seek, without a key frame first. A small slip of the pointer is not a drag.
            val strip = panel.find<MomentStrip>("share-strip")!!.apply { setSize(600, 30) }
            fun mouse(id: Int, x: Int) =
                strip.dispatchEvent(java.awt.event.MouseEvent(strip, id, 0L, 0, x, 10, 1, false, java.awt.event.MouseEvent.BUTTON1))
            player.seeks.clear()
            mouse(java.awt.event.MouseEvent.MOUSE_PRESSED, 150)
            mouse(java.awt.event.MouseEvent.MOUSE_DRAGGED, 151)
            mouse(java.awt.event.MouseEvent.MOUSE_RELEASED, 151)
            // A quarter of point 3 (30.0 to 36.0 s) is 31.5 s.
            assertEquals(listOf(31_465L), player.seeks)
            assertEquals(emptyList(), player.scrubs)

            // A drag shows exact frames, not key frames. The player still decodes the frame of the click,
            // so the seeks of the drag wait, and only the newest one stays.
            player.seeks.clear()
            mouse(java.awt.event.MouseEvent.MOUSE_PRESSED, 150)
            mouse(java.awt.event.MouseEvent.MOUSE_DRAGGED, 300)
            mouse(java.awt.event.MouseEvent.MOUSE_DRAGGED, 450)
            mouse(java.awt.event.MouseEvent.MOUSE_RELEASED, 450)
            assertEquals(emptyList(), player.seeks)
        }
        // The player shows the frame of the click. The newest seek of the drag goes now.
        player.onPlaybackRestart?.invoke()
        waitFor { player.seeks.isNotEmpty() }
        onEdt {
            assertEquals(listOf(34_465L), player.seeks)
            assertEquals(emptyList(), player.scrubs)
            panel.onDeactivated()
            assertEquals(listOf("share deactivated"), player.deactivations)
            assertTrue(panel.find<ThumbnailView>("share-preview")!!.isVisible)
        }
    }

    /** Records what the Share tab asks from the video player. */
    private class RecordingPlayer : org.litvin.media.SwingMediaPlayer {
        override val component: Component = javax.swing.JPanel().apply { name = "share-test-player" }
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((org.litvin.media.PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        override var onPlaybackRestart: (() -> Unit)? = null
        val loads = mutableListOf<String>()
        val seeks = mutableListOf<Long>()
        val scrubs = mutableListOf<Long>()
        val deactivations = mutableListOf<String>()
        @Volatile var view: org.litvin.media.ReplayView? = null
        @Volatile var overlay: org.litvin.media.ImageOverlay? = null

        override fun load(file: File) {
            loads += file.absolutePath
        }

        override fun seek(ms: Long) {
            seeks += ms
        }

        override fun scrub(ms: Long) {
            scrubs += ms
        }

        override fun setReplayView(view: org.litvin.media.ReplayView?) {
            this.view = view
        }

        override fun setImageOverlay(overlay: org.litvin.media.ImageOverlay?) {
            this.overlay = overlay
        }

        override fun deactivatePreview(reason: String) {
            deactivations += reason
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun setRate(rate: Float) = Unit
        override fun status() = org.litvin.media.PlayerStatus.PAUSED
        override fun currentTimeMs() = seeks.lastOrNull() ?: 0L
        override fun totalDurationMs() = 120_000L
        override fun isAdjustSupported() = true
        override fun applyColorAdjustments(adj: org.litvin.adjustments.AdjustmentsV1) = Unit
        override fun applyGeometryAdjustments(adj: org.litvin.adjustments.AdjustmentsV1) = true
        override fun applyPreviewAdjustments(adj: org.litvin.adjustments.AdjustmentsV1) = Unit
        override fun applyPreviewRotation(rotationDeg: Float, reason: String) = Unit
        override fun setPreviewOverlay(overlay: org.litvin.media.VideoOverlay?) = Unit
        override fun stepFrameForward(maximumTimeMs: Long) = 0L
        override fun stepFrameBackward(minimumTimeMs: Long) = 0L
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File) = false
        override fun activatePreview(reason: String) = Unit
        override fun close() = Unit
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
        player: org.litvin.media.SwingMediaPlayer? = null,
        logoFolder: File = File(projectDir, "app-data"),
        chooseLogo: (javax.swing.JComponent) -> File? = { null },
        onError: (String) -> Unit = {},
    ): SwingSharePanel {
        lateinit var panel: SwingSharePanel
        onEdt {
            panel = SwingSharePanel(
                dialogs(onError),
                defaults,
                exportFolder = { exportDir },
                onOpenPoints = onOpenPoints,
                frameLoader = frames,
                stripLoader = keyFrames(),
                probeSource = { org.litvin.ui.tabs.share.ShareSourceFacts(1920, 1080, 30.0) },
                createPlayer = player?.let { created -> { created } },
                logoFolder = { logoFolder },
                chooseLogo = chooseLogo,
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

    /** A dialog service that gives each error to [onError]. Any other dialog fails the test. */
    private fun dialogs(onError: (String) -> Unit): UserDialogService =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(UserDialogService::class.java)) { _, method, args ->
            if (method.name != "showError") error("Unexpected dialog: ${method.name}")
            onError(args.filterIsInstance<String>().joinToString(" | "))
            null
        } as UserDialogService

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
