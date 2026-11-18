package org.litvin.ui.expiry

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import org.litvin.license.update.LocalImage
import org.litvin.license.update.UpdateAndRestart
import org.litvin.license.update.UpdateOptions
import org.litvin.license.update.WhatsNewHtml
import org.litvin.ui.commons.AppTheme
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.Theme
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Shows the "What's new" popup (B-54) of a local Markdown page, as a user of an older version sees it, and saves two
 * images: the popup (`popup.png`) and the full page with no scroll (`page.png`). Use it to check a page before the
 * site deploy. The page and its images come from the local folder, not from the site.
 *
 * Run it from the repository root (not headless):
 * ```
 * mvn -q test-compile exec:java -Dexec.classpathScope=test -Dapp.mainClass=org.litvin.ui.expiry.WhatsNewPopupPreviewMainKt -Dexec.args="--page=site/public/changelog/1.5.0/whats-new.md"
 * ```
 * Options:
 * - `--page=<file.md>`: the page. Its images are in the same folder.
 * - `--out=<folder>`: the folder of the images. The default is `target/whats-new-preview`.
 * - `--language=<code>`: the app language. The default is English.
 * - `--show=<true|false>`: keep the popup open on the screen until it closes. The default is false.
 */
fun main(args: Array<String>) {
    Locale.setDefault(Locale.US)
    val options = args.associate { arg ->
        require(arg.startsWith("--") && "=" in arg) { "Unknown argument: $arg. Use --page=, --out=, --language=, or --show=." }
        arg.removePrefix("--").substringBefore('=') to arg.substringAfter('=')
    }
    val unknown = options.keys - setOf("page", "out", "language", "show")
    require(unknown.isEmpty()) { "Unknown options: $unknown. Use --page=, --out=, --language=, or --show=." }
    val page = File(requireNotNull(options["page"]) { "Give the page with --page=<file.md>." })
    val out = File(options["out"] ?: "target/whats-new-preview").apply { mkdirs() }
    options["language"]?.let { code ->
        I18n.install(Messages.of(requireNotNull(AppLanguage.ofCode(code)) { "Unknown language: $code." }))
    }
    val show = options["show"].toBoolean()

    SwingUtilities.invokeAndWait { Theme.start(AppTheme.DARK) }
    // The page code loads only HTTPS images, so the page gets an address on the site. Each image comes from the folder.
    val base = URI("https://banana-shot-editor.app/whats-new-preview/${page.name}")
    val html = WhatsNewHtml.render(page.readText(), base, WhatsNewDialog.IMAGE_WIDTH) { uri ->
        val file = File(page.absoluteFile.parentFile, uri.path.substringAfterLast('/'))
        ImageIO.read(file)?.let { LocalImage(file.toURI(), it.width, it.height) }
    }

    SwingUtilities.invokeLater {
        val frame = JFrame("BananaShot").apply {
            size = Dimension(1100, 760)
            setLocationRelativeTo(null)
            isVisible = true
        }
        val runner = UpdateRunner(
            update = UpdateAndRestart(downloader = { _, _, _ -> }, launcher = { }),
            background = { it.run() },
            exportRuns = { false },
            closeSequence = {},
            exit = {},
            openUrl = { println("Open: $it") },
            parent = { frame },
        )
        val buttons = UpdateButtons("whats-new", runner)
        buttons.show(UpdateOptions(showsUpdateAndRestart = true, installerUrl = "https://example.invalid", downloadPageUrl = "https://example.invalid"))
        val popup = WhatsNewDialog(frame, buttons) { println("Open: $it") }
        Timer(4_000) {
            popup.page.repaint()
            save(popup.dialog.rootPane, File(out, "popup.png"))
            val full = popup.page
            val width = full.width
            full.size = Dimension(width, full.preferredSize.height)
            save(full, File(out, "page.png"), Palette.BG)
            if (!show) {
                popup.hide()
                frame.dispose()
                System.exit(0)
            }
        }.apply { isRepeats = false }.start()
        // Modal: the call returns when the popup closes.
        popup.show(html, "https://banana-shot-editor.app/changelog/")
        frame.dispose()
        System.exit(0)
    }
}

private fun save(component: JComponent, file: File, background: java.awt.Color? = null) {
    val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    background?.let {
        g.color = it
        g.fillRect(0, 0, image.width, image.height)
    }
    component.printAll(g)
    g.dispose()
    ImageIO.write(image, "png", file)
    println("${file.path}: ${image.width} x ${image.height}")
}
