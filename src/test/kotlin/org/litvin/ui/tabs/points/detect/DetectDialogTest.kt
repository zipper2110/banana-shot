package org.litvin.ui.tabs.points.detect

import org.junit.jupiter.api.Test
import org.litvin.autodetect.DetectImport
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

        override fun importResult(resultFile: File, settings: DetectSettings): ImportPlan? {
            imports += resultFile to settings
            return ImportPlan(emptyList(), emptyMap(), emptyMap(), emptyList())
        }
    }

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
    fun `Add points is off for a project with no result`() = withProject { project, runner ->
        SwingUtilities.invokeAndWait {
            val dialog = DetectDialog(JPanel(), runner, Host(project)).build()
            try {
                assertFalse((dialog.find("detect-add") as JButton).isEnabled)
                assertTrue((dialog.find("detect-start") as JButton).isEnabled)
            } finally {
                dialog.dispose()
            }
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
