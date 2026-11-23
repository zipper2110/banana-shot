package org.litvin.ui.tabs.points.detect

import org.junit.jupiter.api.Test
import org.litvin.adjustments.CropRect
import org.litvin.adjustments.GeometryPlan
import org.litvin.autodetect.DetectImport
import org.litvin.autodetect.DetectOverlayData
import org.litvin.autodetect.DetectedRally
import org.litvin.autodetect.RallyView
import org.litvin.ui.commons.Palette
import org.litvin.media.OverlayShape
import org.litvin.points.PointV1
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.tabs.points.ui.ScorePlayers
import java.awt.geom.Rectangle2D
import javax.swing.JLabel
import org.litvin.autodetect.DetectLab
import org.litvin.autodetect.DetectRunner
import org.litvin.autodetect.DetectSettings
import org.litvin.autodetect.ImportPlan
import org.litvin.points.AutoKind
import org.litvin.points.AutoMarkV1
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetectDialogTest {
    private class Host(override val projectDir: File?, override val video: File? = null) : DetectHost {
        val imports = mutableListOf<Pair<File, DetectSettings>>()
        val ranges = mutableListOf<LongRange?>()
        var points = listOf<PointV1>()
        var reviews = 0

        override val videoSeconds: Double? = 600.0
        override var trimRangeMs: LongRange? = null
        override val players = ScorePlayers("Anna", "Bea")
        override fun autoPoints() = points

        override fun openReview() {
            reviews++
        }

        override fun importResult(resultFile: File, settings: DetectSettings, swapped: Boolean, rangeMs: LongRange?): ImportPlan? {
            imports += resultFile to settings
            ranges += rangeMs
            return ImportPlan(emptyList(), emptyMap(), emptyMap(), emptyList())
        }
    }

    private fun auto(id: String, startMs: Int, checked: Boolean = false) =
        PointV1(id = id, startMs = startMs, endMs = startMs + 5_000, auto = AutoMarkV1(AutoKind.RALLY, 80, checked))

    private fun withProject(block: (File, DetectRunner) -> Unit) {
        val root = Files.createTempDirectory("detect-dialog").toFile()
        try {
            val project = File(root, "project").apply { mkdirs() }
            block(project, DetectRunner(DetectLab("python", "detect.py", File(root, "work").path), File(root, "appdata").apply { mkdirs() }))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun result(project: File): File = File(project, DetectImport.FOLDER).apply { mkdirs() }.resolve("result-20261123-010203.json").apply {
        writeText("""{"videoSeconds": 600, "stageSeconds": {"ball": 3000.0}, "rallies": [{"start": 10.0, "end": 20.0}]}""")
    }

    @Test
    fun `Add points does not show for a project with no result`() = withProject { project, runner ->
        SwingUtilities.invokeAndWait {
            val dialog = DetectDialog(JPanel(), runner, Host(project)).build()
            try {
                assertFalse((dialog.find("detect-add") as JButton).isVisible)
                assertTrue((dialog.find("detect-start") as JButton).isEnabled)
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun `the player question highlights the player when the result has a picture with the two players`() {
        val folder = Files.createTempDirectory("detect-question").toFile()
        try {
            val picture = File(folder, "00046.jpg")
            val light = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
            light.createGraphics().apply { color = Palette.FG; fillRect(0, 0, 1920, 1080); dispose() }
            ImageIO.write(light, "jpg", picture)
            val view = RallyView(45, near = listOf(800.0, 500.0, 1000.0, 1000.0), far = listOf(900.0, 300.0, 950.0, 400.0))
            SwingUtilities.invokeAndWait {
                val marked = PlayerQuestion.build(JPanel(), picture, DetectedRally(start = 40.0, end = 50.0, p1Near = true, view = view), ScorePlayers("Anna", "Bea")) {}
                val plain = PlayerQuestion.build(JPanel(), picture, DetectedRally(start = 40.0, end = 50.0, p1Near = true), ScorePlayers("Anna", "Bea")) {}
                try {
                    assertEquals("Who is this player?", marked.title)
                    // The picture is bright at the player (x 800 to 1000, y 500 to 1000 of 1920 × 1080) and darker around.
                    val shown = marked.contentPane.find("detect-player-picture")!!
                    val painted = BufferedImage(shown.preferredSize.width, shown.preferredSize.height, BufferedImage.TYPE_INT_RGB)
                    painted.createGraphics().apply { shown.size = shown.preferredSize; shown.paint(this); dispose() }
                    val atPlayer = painted.getRGB(300, 250) and 0xFF
                    val around = painted.getRGB(40, 40) and 0xFF
                    assertTrue(atPlayer > 200, "at the player: $atPlayer")
                    assertTrue(around < atPlayer / 2, "around the player: $around")
                    assertTrue((marked.contentPane.find("detect-player-question-text") as JLabel).text.contains("00:00:45.0"))
                    assertEquals("Who is at the near end?", plain.title)
                    snapshot(marked.contentPane, "player-question")
                } finally {
                    marked.dispose()
                    plain.dispose()
                }
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `Add points gives the newest result and the settings of the dialog to the tab`() = withProject { project, runner ->
        val file = result(project)
        val host = Host(project)
        SwingUtilities.invokeAndWait {
            val dialog = DetectDialog(JPanel(), runner, host).build()
            try {
                (dialog.find("detect-pad-after") as JTextField).text = "1.5"
                (dialog.find("detect-add") as JButton).doClick()

                assertEquals(file, host.imports.single().first)
                assertEquals(1_500, host.imports.single().second.padAfterMs)
                snapshot(dialog.contentPane, "detect-dialog")
            } finally {
                dialog.dispose()
            }
        }
        assertEquals(1_500, runner.loadSettings().padAfterMs)
    }

    @Test
    fun `the import gets the trim only when the setting is on`() = withProject { project, runner ->
        result(project)
        val host = Host(project).apply { trimRangeMs = 5_000L..90_000L }
        SwingUtilities.invokeAndWait {
            val dialog = DetectDialog(JPanel(), runner, host).build()
            try {
                (dialog.find("detect-add") as JButton).doClick()
                (dialog.find("detect-trim-only") as SwitchBox).doClick()
                (dialog.find("detect-add") as JButton).doClick()

                assertEquals(listOf<LongRange?>(5_000L..90_000L, null), host.ranges)
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun `the player question gives the clicked player`() {
        SwingUtilities.invokeAndWait {
            var answer: Int? = null
            val rally = DetectedRally(start = 75.0, end = 82.0, p1Near = true)
            val dialog = PlayerQuestion.build(JPanel(), null, rally, ScorePlayers("Anna", "Bea")) { answer = it }
            try {
                val second = dialog.find("detect-near-player-2") as JButton
                assertEquals("Bea", second.text)
                second.doClick()

                assertEquals(2, answer)
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun `the bar shows Continue review while a clip is not reviewed, and opens the review window`() = withProject { project, runner ->
        val host = Host(project).apply { points = listOf(auto("a", 1_000), auto("b", 20_000, checked = true), auto("c", 40_000)) }
        SwingUtilities.invokeAndWait {
            val bar = DetectBar(runner, host)
            bar.refresh()
            val review = bar.find("points-detect-review") as JButton
            assertTrue(review.isVisible)
            assertEquals("Continue review (2)", review.text)

            review.doClick()
            assertEquals(1, host.reviews)

            host.points = host.points.map { it.copy(auto = it.auto?.copy(checked = true)) }
            bar.refresh()
            assertFalse(review.isVisible)
        }
    }

    @Test
    fun `the overlay puts the ball at its place in a cropped video`() {
        val data = DetectOverlayData(ball = listOf(listOf(1.0, 960.0, 540.0)))
        val plan = GeometryPlan(0.0, CropRect(0.25, 0.25, 0.5, 0.5))
        val shapes = DetectOverlay(data) { plan }.shapes(1.02, Rectangle2D.Double(100.0, 50.0, 1280.0, 720.0))

        val ring = shapes.filterIsInstance<OverlayShape.Circle>().last()
        assertEquals(100.0 + 640.0, ring.centerX, 0.01)
        assertEquals(50.0 + 360.0, ring.centerY, 0.01)
        assertTrue(DetectOverlay(data) { plan }.shapes(5.0, Rectangle2D.Double(0.0, 0.0, 1280.0, 720.0)).isEmpty())
    }

    @Test
    fun `the overlay draws a skeleton only near the time of its picture`() {
        // The left arm: shoulder (5), elbow (7), wrist (9). The other body points are not seen.
        val row = MutableList(35) { -1.0 }
        row[0] = 10.0
        listOf(5 to (900.0 to 500.0), 7 to (920.0 to 540.0), 9 to (940.0 to 580.0)).forEach { (point, at) ->
            row[1 + 2 * point] = at.first
            row[2 + 2 * point] = at.second
        }
        val overlay = DetectOverlay(DetectOverlayData(poses = listOf(row))) { null }
        val area = Rectangle2D.Double(0.0, 0.0, 1920.0, 1080.0)

        val shapes = overlay.shapes(10.1, area)
        assertEquals(2, shapes.filterIsInstance<OverlayShape.Line>().size)
        assertEquals(3, shapes.filterIsInstance<OverlayShape.Circle>().size)
        assertTrue(overlay.shapes(10.5, area).isEmpty())
        assertTrue(overlay.shapes(9.5, area).isEmpty())
    }

    @Test
    fun `a time that is not a number keeps the import off`() = withProject { project, runner ->
        result(project)
        val host = Host(project)
        SwingUtilities.invokeAndWait {
            val dialog = DetectDialog(JPanel(), runner, host).build()
            try {
                (dialog.find("detect-pad-before") as JTextField).text = "abc"
                (dialog.find("detect-add") as JButton).doClick()

                assertTrue(host.imports.isEmpty())
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun `a short point and a low winner confidence are clips to check first`() {
        assertTrue(AutoMarkV1(AutoKind.SHORT, 90).isWeak())
        assertTrue(AutoMarkV1(AutoKind.RALLY, null).isWeak())
        assertTrue(AutoMarkV1(AutoKind.RALLY, 62).isWeak())
        assertFalse(AutoMarkV1(AutoKind.RALLY, 85).isWeak())
        assertFalse(AutoMarkV1(AutoKind.FAULT).isWeak())
    }

    /** Writes a picture of [component] when the property `detect.snapshot` names a folder. For a check by eye. */
    private fun snapshot(component: Container, name: String) {
        val folder = System.getProperty("detect.snapshot") ?: System.getenv("DETECT_SNAPSHOT") ?: return
        val image = BufferedImage(component.width.coerceAtLeast(1), component.height.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        component.printAll(g)
        g.dispose()
        ImageIO.write(image, "png", File(folder, "$name.png"))
    }

    private fun Container.find(name: String): Component? {
        for (child in components) {
            if (child.name == name) return child
            if (child is Container) child.find(name)?.let { return it }
        }
        return null
    }
}
