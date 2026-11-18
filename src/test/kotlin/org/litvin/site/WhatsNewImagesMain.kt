package org.litvin.site

import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.export.preview.ExportPreviewTimeline
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import org.litvin.i18n.tr
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.export.ExportReplay
import org.litvin.stats.StatsCard
import org.litvin.stats.StatsCardVideo
import org.litvin.stats.StatsReport
import org.litvin.stats.StatsSettingsV1
import org.litvin.points.ReplayV1
import org.litvin.media.VideoReadCheck
import org.litvin.ui.commons.AppTheme
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.ScoreboardSceneImage
import org.litvin.ui.commons.Theme
import org.litvin.ui.commons.UiButton
import org.litvin.ui.tabs.export.ExportPreviewStrip
import org.litvin.ui.tabs.projects.components.NewProjectDialog
import org.litvin.ui.tabs.projects.components.NewProjectRequest
import org.kordamp.ikonli.feather.Feather
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Window
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.random.Random

/**
 * Renders the images of the "What's new" page of 1.5.0 (B-54) with the drawing code of the app:
 * the "New project" dialog with several files (B-49) and the export preview (B-75).
 * The images are 546 px wide, as the images of 1.4.0, on the background of the popup.
 *
 * Run it from the repository root (not headless, because the dialog needs a window peer):
 * ```
 * mvn -q test-compile exec:java -Dexec.classpathScope=test -Dapp.mainClass=org.litvin.site.WhatsNewImagesMainKt -Dexec.args="--language=de"
 * ```
 * Options:
 * - `--out=<folder>`: the output folder. The default is `site/public/changelog/1.5.0`.
 * - `--language=<code>`: the app language. The default is English. Other languages get the suffix `.<code>`.
 * - `--frame=<file.png>`: the video frame of the preview image. The default is `target/wn150/hero.png`
 *   (`ffmpeg -i site/public/assets/frames/hero.webp target/wn150/hero.png`).
 */
fun main(args: Array<String>) {
    Locale.setDefault(Locale.US)
    val options = args.associate { arg ->
        require(arg.startsWith("--") && "=" in arg) { "Unknown argument: $arg. Use --out=, --language=, or --frame=." }
        arg.removePrefix("--").substringBefore('=') to arg.substringAfter('=')
    }
    val unknown = options.keys - setOf("out", "language", "frame")
    require(unknown.isEmpty()) { "Unknown options: $unknown. Use --out=, --language=, or --frame=." }
    val language = options["language"]?.let { code ->
        requireNotNull(AppLanguage.ofCode(code)) { "Unknown language: $code. Use one of ${AppLanguage.entries.map { it.code }}." }
    }
    language?.let { I18n.install(Messages.of(it)) }
    val suffix = language?.takeIf { it != AppLanguage.ENGLISH }?.let { ".${it.code}" }.orEmpty()
    val out = File(options["out"] ?: "site/public/changelog/1.5.0").apply { mkdirs() }
    val frame = ImageIO.read(File(options["frame"] ?: "target/wn150/hero.png"))

    SwingUtilities.invokeAndWait {
        Theme.start(AppTheme.DARK)
        write(WhatsNewImages.files(), File(out, "several-files$suffix.png"))
        write(WhatsNewImages.preview(frame), File(out, "export-preview$suffix.png"))
    }
    System.exit(0)
}

private fun write(image: BufferedImage, file: File) {
    ImageIO.write(image, "png", file)
    println("${file.path}: ${image.width} x ${image.height}")
}

internal object WhatsNewImages {
    const val WIDTH = 546
    private const val PAD = 20
    private val BACKGROUND = Color(0x1D2126)
    private val FRAME_LINE = Color(0x2C3138)

    /** The "Video" field of the "New project" dialog with three files of one match. */
    fun files(): BufferedImage {
        val folder = "/Users/coach/Movies/Club final"
        val paths = listOf("GX010042.MP4", "GX020042.MP4", "GX030042.MP4").map { "$folder/$it" }
        val request = NewProjectRequest(name = "Club final", sourceVideoPath = paths.first(), sourceVideoPaths = paths)
        val constructor = NewProjectDialog::class.java.declaredConstructors.single { it.parameterCount == 4 }
        constructor.isAccessible = true
        val chooser: (Component, String) -> List<String>? = { _, _ -> null }
        val dialog = constructor.newInstance(null, request, chooser, VideoReadCheck { null }) as JDialog
        try {
            val content = dialog.contentPane as JComponent
            content.size = content.preferredSize
            layoutAll(content)
            val hint = find(content, "new-project-video-hint")
            val form = hint.parent
            val field = form.components[form.components.indexOf(hint) - 1]
            val top = SwingUtilities.convertRectangle(form, field.bounds, content)
            val bottom = SwingUtilities.convertRectangle(form, hint.bounds, content)
            val left = SwingUtilities.convertPoint(form, 0, 0, content).x
            val crop = Rectangle(left - 4, top.y - 8, form.width + 8, bottom.y + bottom.height - top.y + 16)
            return framed(paint(content, crop), Palette.BG)
        } finally {
            dialog.dispose()
        }
    }

    /** The export preview: a video frame with the scoreboard, and the item strip under it. */
    fun preview(frame: BufferedImage): BufferedImage {
        val inner = WIDTH - 2 * PAD
        val videoH = inner * 9 / 16
        val video = BufferedImage(inner, videoH, BufferedImage.TYPE_INT_ARGB)
        video.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(frame, 0, 0, inner, videoH, null)
            val display = ScoreboardComponent.display(
                OverlaySpan(
                    startMs = 0L, endMs = 1L, text = "",
                    p1Name = "Edvard", p2Name = "Dima", p2ColorHex = "#FF3B3B",
                    p1Pts = 2, p2Pts = 1, gamesP1 = 4, gamesP2 = 3,
                    completedSets = listOf(6 to 4), server = 1,
                ),
            )
            val settings = ScoreboardSettingsV1(title = "Club final")
            val scene = ScoreboardLayouts.scene(display, settings)
            val place = ScoreboardAss.place(scene, settings, 0.0, 0.0, inner.toDouble(), videoH.toDouble())
            ScoreboardSceneImage.draw(this, scene, place.x, place.y, place.scale)
            dispose()
        }

        val card = StatsCardVideo(listOf(), pageDurationMs = 6_000L)
        val items = mutableListOf<ExportPreviewTimeline.Item>()
        var out = 0L
        val lengths = listOf(9_000L, 14_000L, 7_000L, 21_000L, 11_000L, 8_000L, 16_000L, 10_000L)
        lengths.forEachIndexed { index, length ->
            val start = 60_000L * (index + 1)
            items += ExportPreviewTimeline.Video(out, start, start + length, start, "p${index + 1}")
            out += length
            if (index == 3) {
                val replay = ExportReplay("p4", (start + length).toInt(), ReplayV1((start + length - 5_000).toInt(), (start + length).toInt()))
                items += ExportPreviewTimeline.Replay(out, replay)
                out += replay.playMs
            }
        }
        items += ExportPreviewTimeline.Card(out, card.copy(pages = List(2) { StatsCard.pages(sampleContent()).first() }), null, null)
        val timeline = ExportPreviewTimeline(items)
        val strip = ExportPreviewStrip(timeline, (1..lengths.size).associateBy { "p$it" }, onSelect = {})
        val current = items.indexOfFirst { it is ExportPreviewTimeline.Video && it.pointId == "p3" }
        strip.setPosition(current, items[current].outStartMs + 3_000L)

        val controls = JPanel(BorderLayout(10, 0)).apply {
            background = Palette.PANEL
            border = BorderFactory.createEmptyBorder(10, 0, 10, 0)
            add(UiButton(tr("export.preview.previous"), Feather.SKIP_BACK), BorderLayout.WEST)
            add(strip, BorderLayout.CENTER)
            add(UiButton(tr("export.preview.next"), Feather.SKIP_FORWARD), BorderLayout.EAST)
        }
        controls.size = Dimension(inner, controls.preferredSize.height)
        layoutAll(controls)
        val controlsImage = paint(controls, Rectangle(0, 0, controls.width, controls.height))

        val body = BufferedImage(inner, videoH + controlsImage.height, BufferedImage.TYPE_INT_ARGB)
        body.createGraphics().apply {
            color = Palette.PANEL
            fillRect(0, 0, body.width, body.height)
            drawImage(video, 0, 0, null)
            drawImage(controlsImage, 0, videoH, null)
            dispose()
        }
        return framed(body, Palette.PANEL, padding = 0)
    }

    private fun sampleContent() = StatsCard.content(
        sampleReport(),
        StatsSettingsV1(
            videoStats = listOf("rating_overall", "rating_serve", "rating_return", "rating_pressure"),
            cardGroupTitles = false,
        ),
    )

    /** A close basic match with a server mark on the first point. Edvard wins about 53% of the points. */
    private fun sampleReport(): StatsReport {
        val random = Random(SEED)
        val points = (1..240).map { PointV1(id = "p$it", startMs = it * 20_000, endMs = it * 20_000 + 6_000) }
        val outcomes = points.associate { it.id to if (random.nextDouble() < 0.53) Outcome.P1 else Outcome.P2 }
        val score = ScoreV1(
            player1Name = "Edvard", player2Name = "Dima", player2ColorHex = "#FF3B3B",
            outcomes = outcomes, serverMarks = mapOf("p1" to Outcome.P1),
        )
        return StatsReport.build(EdlV1(points = points), score)
    }

    var SEED = System.getProperty("seed")?.toInt() ?: 34

    /** [content] in a rounded frame with a line, on the background of the popup. */
    private fun framed(content: BufferedImage, fill: Color, padding: Int = 0): BufferedImage {
        val boxW = content.width + 2 * padding
        val boxH = content.height + 2 * padding
        val image = BufferedImage(WIDTH, boxH + 2 * PAD, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = BACKGROUND
        g.fillRect(0, 0, image.width, image.height)
        val x = (WIDTH - boxW) / 2.0
        val shape = RoundRectangle2D.Double(x, PAD.toDouble(), boxW.toDouble(), boxH.toDouble(), 12.0, 12.0)
        g.color = fill
        g.fill(shape)
        val saved = g.clip
        g.clip(shape)
        g.drawImage(content, (x + padding).toInt(), PAD + padding, null)
        g.clip = saved
        g.color = FRAME_LINE
        g.draw(shape)
        g.dispose()
        return image
    }

    private fun paint(component: JComponent, area: Rectangle): BufferedImage {
        val image = BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics() as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
        g.translate(-area.x, -area.y)
        component.printAll(g)
        g.dispose()
        return image
    }

    private fun layoutAll(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layoutAll)
    }

    private fun find(root: Container, name: String): Component =
        root.components.firstNotNullOfOrNull { child ->
            if (child.name == name) child else (child as? Container)?.let { runCatching { find(it, name) }.getOrNull() }
        } ?: error("No component $name")

    @Suppress("unused")
    private fun owner(): Window? = null
}
