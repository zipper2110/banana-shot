package org.litvin.share

import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders a sheet of thumbnail previews to a PNG file. Use it to look at a style change without the app.
 * The thumbnails come from [ThumbnailRenderer.render], as the PNG of the Share tab.
 *
 * Run it from the repository root:
 * ```
 * mvn -q test-compile exec:java -Dexec.classpathScope=test -Dapp.mainClass=org.litvin.share.ThumbnailPreviewMainKt "-Dexec.args=--styles=SPLASH,CLASH"
 * ```
 * Options:
 * - `--out=<file.png>`: the output file. The default is `target/thumbnail-preview/thumbnails.png`.
 * - `--styles=<ID,ID>`: the [ThumbnailStyle] names. The default is all styles.
 * - `--frame=<image>`: the video frame. The default is a gray gradient.
 * - `--logo=<image>`: a logo file in a format of [ThumbnailLogos.EXTENSIONS]. The default is no logo.
 * - `--texts=sample|long|short`: the sample texts. The default is `sample` (the texts of the design canvas).
 * - `--columns=<n>`: the number of thumbnails in a row. The default is 3.
 */
fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val known = setOf("out", "styles", "frame", "logo", "texts", "columns")
    val options = args.associate { arg ->
        require(arg.startsWith("--") && "=" in arg) { "Unknown argument: $arg. Use ${known.joinToString { "--$it=" }}." }
        arg.removePrefix("--").substringBefore('=') to arg.substringAfter('=')
    }
    val unknown = options.keys - known
    require(unknown.isEmpty()) { "Unknown options: $unknown. Use ${known.joinToString { "--$it=" }}." }
    val out = File(options["out"] ?: "target/thumbnail-preview/thumbnails.png")
    val styles = options["styles"]?.split(',')?.map { ThumbnailStyle.valueOf(it.trim().uppercase()) } ?: ThumbnailStyle.entries
    val frame = options["frame"]?.let { requireNotNull(ImageIO.read(File(it))) { "Cannot read the frame $it" } }
    val logo = options["logo"]?.let { path ->
        val image = ThumbnailLogos.fit(ThumbnailLogos.read(File(path), System.getenv("FFMPEG_PATH")?.let { "$it/ffmpeg.exe" } ?: "ffmpeg"))
        ThumbnailLogo("preview", image)
    }
    val texts = ThumbnailPreviewSheet.texts(options["texts"] ?: "sample")
    val columns = options["columns"]?.toInt() ?: 3

    out.absoluteFile.parentFile.mkdirs()
    ImageIO.write(ThumbnailPreviewSheet.render(styles, frame, logo, texts, columns), "png", out)
    println("Thumbnail previews: ${out.absolutePath}")
}

/** A sheet of thumbnail previews: one cell for each style, at half size, with the style name and the layout flags. */
object ThumbnailPreviewSheet {
    private const val CELL_W = 640
    private const val CELL_H = 360
    private const val LABEL_H = 28
    private const val GAP = 16

    /** The texts of the design canvas, and two cases for the fit of long and short texts. */
    fun texts(name: String): Map<ThumbnailField, String> = when (name) {
        "sample" -> mapOf(
            ThumbnailField.HEAD to "Best rally",
            ThumbnailField.KIND to "Mixed doubles",
            ThumbnailField.TEAM1 to "Anna & Mark",
            ThumbnailField.TEAM2 to "Leo & Kate",
            ThumbnailField.EVENT to "Spring Cup 2026",
            ThumbnailField.COURT to "Court 3",
            ThumbnailField.DATE to "May 12",
            ThumbnailField.ROUND to "Semi-final",
            ThumbnailField.CLUB to "Your club",
            ThumbnailField.SCORE to "6-4 7-5",
        )
        "long" -> mapOf(
            ThumbnailField.HEAD to "The most incredible comeback",
            ThumbnailField.KIND to "Men's doubles open category",
            ThumbnailField.TEAM1 to "Alexander Zverev / Maximilian Marterer",
            ThumbnailField.TEAM2 to "Alexander Bublik / Mikhail Kukushkin",
            ThumbnailField.EVENT to "National Beach Tennis Championship 2026",
            ThumbnailField.COURT to "Center court",
            ThumbnailField.DATE to "September 28, 2026",
            ThumbnailField.ROUND to "Quarter-final",
            ThumbnailField.CLUB to "Beach Tennis Club Copacabana",
            ThumbnailField.SCORE to "7-6 4-6 11-9",
        )
        "short" -> mapOf(
            ThumbnailField.HEAD to "Wow",
            ThumbnailField.TEAM1 to "Ann",
            ThumbnailField.TEAM2 to "Leo",
        )
        else -> error("Unknown texts: $name. Use sample, long, or short.")
    }

    fun render(
        styles: List<ThumbnailStyle>,
        frame: BufferedImage?,
        logo: ThumbnailLogo?,
        texts: Map<ThumbnailField, String>,
        columns: Int,
    ): BufferedImage {
        val rows = (styles.size + columns - 1) / columns
        val sheet = BufferedImage(
            GAP + columns * (CELL_W + GAP),
            GAP + rows * (CELL_H + LABEL_H + GAP),
            BufferedImage.TYPE_INT_RGB,
        )
        val source = frame ?: sampleFrame()
        val g2 = sheet.createGraphics()
        try {
            g2.color = Color(0x202124)
            g2.fillRect(0, 0, sheet.width, sheet.height)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.font = Font(Font.SANS_SERIF, Font.BOLD, 16)
            styles.forEachIndexed { index, style ->
                val x = GAP + (index % columns) * (CELL_W + GAP)
                val y = GAP + (index / columns) * (CELL_H + LABEL_H + GAP)
                val scene = ThumbnailScene(
                    style = style,
                    place = style.places.first(),
                    texts = texts.filterKeys { it in style.fields },
                    colors = ThumbnailLooks.colors(style, null),
                    logo = logo,
                )
                val layout = ThumbnailRenderer.layout(scene)
                val image = ThumbnailRenderer.render(source, FrameView(), scene)
                g2.drawImage(image, x, y + LABEL_H, CELL_W, CELL_H, null)
                val smallest = texts(layout.items).minByOrNull { it.font.size2D }
                val flags = listOfNotNull(
                    "shrunk".takeIf { layout.shrunk },
                    smallest?.takeIf { layout.small }?.let { "small: ${it.font.size2D.toInt()} px \"${it.text}\"" },
                ).joinToString(", ")
                g2.color = if (flags.isEmpty()) Color(0xE8EAED) else Color(0xFFB74D)
                g2.drawString(style.name + if (flags.isEmpty()) "" else "  ($flags)", x, y + 20)
            }
        } finally {
            g2.dispose()
        }
        return sheet
    }

    private fun texts(items: List<ThumbnailRenderer.Item>): List<ThumbnailRenderer.Item.Text> = items.flatMap {
        when (it) {
            is ThumbnailRenderer.Item.Text -> listOf(it)
            is ThumbnailRenderer.Item.Group -> texts(it.items)
            else -> emptyList()
        }
    }

    /** A gray court-like frame, when there is no video frame. */
    private fun sampleFrame(): BufferedImage {
        val image = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics()
        try {
            g2.paint = GradientPaint(0f, 0f, Color(0x6E8CA8), 0f, 1080f, Color(0xC9B48A))
            g2.fillRect(0, 0, 1920, 1080)
            g2.color = Color(0xEDE6D6)
            g2.fillRect(0, 620, 1920, 460)
            g2.color = Color(0x2F2F2F)
            g2.fillRect(0, 600, 1920, 12)
        } finally {
            g2.dispose()
        }
        return image
    }
}
