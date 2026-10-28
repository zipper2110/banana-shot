package org.litvin.ui.flow.harness

import org.litvin.i18n.LanguageSettings
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.FutureTask
import javax.imageio.ImageIO
import javax.swing.RootPaneContainer
import kotlin.math.ceil

/**
 * Saves screenshots of the windows that a UI flow shows, for the translators (B-11 E4-T4).
 *
 * The property `bananashot.uiFlow.screenshots=true` switches the screenshots on. The files go to
 * `target/translator-screenshots/<language>/<test class>/<test method>/`. The language is the value of
 * `bananashot.language`, or `en` without it. `qa/Make-TranslatorScreenshots.ps1` runs the UI flow tests with both
 * properties.
 *
 * Before each action of the driver, [captureNewWindows] saves each window whose title did not show before in the
 * test. A test can also save a window with [capture], for example a section of a window whose title does not change.
 * When the screenshots are off, both functions do nothing.
 */
class UiFlowScreenshots internal constructor(private val directory: Path?) {
    private val savedTitles = HashSet<String>()
    private var ordinal = 0

    /** True when the screenshots are on. */
    val enabled: Boolean get() = directory != null

    /** When false, only [capture] saves screenshots. A test that names all its screenshots sets it to false. */
    var automatic: Boolean = true

    /** Saves each showing frame and dialog whose title was not saved before in this test. */
    fun captureNewWindows() {
        if (directory == null || !automatic) return
        val screenshots = onEdt {
            Window.getWindows()
                .filter { window -> window.isShowing && (window is Frame || window is Dialog) }
                .filter { window -> savedTitles.add("${window.javaClass.name}|${titleOf(window)}") }
                .map { window -> titleOf(window).ifBlank { window.name ?: window.javaClass.simpleName } to render(window) }
        }
        screenshots.forEach { (label, image) -> write(label, image) }
    }

    /** Saves the window that holds the component [componentName] as [label]. */
    fun capture(componentName: String, label: String) {
        if (directory == null) return
        val image = onEdt {
            val window = Window.getWindows()
                .filter(Window::isShowing)
                .firstNotNullOfOrNull { window -> find(window, componentName)?.let { window } }
                ?: throw AssertionError("No showing window has the component '$componentName'")
            render(window)
        }
        write(label, image)
    }

    private fun write(label: String, image: BufferedImage) {
        val folder = checkNotNull(directory)
        Files.createDirectories(folder)
        val file = folder.resolve("%02d-%s.png".format(++ordinal, fileLabel(label)))
        check(ImageIO.write(image, "png", file.toFile())) { "No PNG writer is available for $file" }
    }

    /** Paints the root pane, so the image also has the title bar of FlatLaf. The image uses the scale of the screen. */
    private fun render(window: Window): BufferedImage {
        val target: Component = (window as? RootPaneContainer)?.rootPane ?: window
        target.validate()
        val scale = window.graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0.0 } ?: 1.0
        val image = BufferedImage(
            ceil(target.width.coerceAtLeast(1) * scale).toInt(),
            ceil(target.height.coerceAtLeast(1) * scale).toInt(),
            BufferedImage.TYPE_INT_ARGB,
        )
        val graphics = image.createGraphics()
        try {
            graphics.scale(scale, scale)
            target.printAll(graphics)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun find(component: Component, name: String): Component? {
        if (component.name == name && component.isShowing) return component
        if (component !is Container) return null
        return component.components.firstNotNullOfOrNull { child -> find(child, name) }
    }

    private fun titleOf(window: Window): String = when (window) {
        is Frame -> window.title.orEmpty()
        is Dialog -> window.title.orEmpty()
        else -> ""
    }

    private fun <T> onEdt(action: () -> T): T {
        if (EventQueue.isDispatchThread()) return action()
        val task = FutureTask(action)
        EventQueue.invokeAndWait(task)
        return task.get()
    }

    companion object {
        /** Switches the screenshots on. */
        const val PROPERTY = "bananashot.uiFlow.screenshots"

        /** The folder of all screenshots. A subfolder holds the screenshots of one language. */
        val ROOT: Path = Path.of("target", "translator-screenshots")

        /** The screenshots of one test, or screenshots that are off. Deletes the old screenshots of the test. */
        internal fun forTest(className: String, methodName: String): UiFlowScreenshots {
            if (!System.getProperty(PROPERTY).toBoolean()) return UiFlowScreenshots(null)
            val language = System.getProperty(LanguageSettings.PROPERTY)?.takeIf(String::isNotBlank) ?: "en"
            val root = ROOT.toAbsolutePath().normalize()
            val directory = root.resolve(fileLabel(language)).resolve(className).resolve(methodName).normalize()
            require(directory.startsWith(root) && directory != root) { "Bad screenshot folder: $directory" }
            if (Files.exists(directory)) {
                Files.walk(directory).use { entries -> entries.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            }
            return UiFlowScreenshots(directory)
        }

        /** A file name part that Windows accepts. Letters of all scripts stay, so a translated title stays readable. */
        private fun fileLabel(value: String): String =
            value.replace(Regex("[<>:\"/\\\\|?*\\p{Cntrl}]"), "_").trim().trimEnd('.').take(80).ifBlank { "window" }
    }
}
