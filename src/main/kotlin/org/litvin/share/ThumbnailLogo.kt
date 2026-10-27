package org.litvin.share

import com.github.weisj.jsvg.parser.SVGLoader
import com.github.weisj.jsvg.view.ViewBox
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The logo of the user on a thumbnail (B-53). [id] identifies the image, so that two logos with the same pixels are equal.
 * The image has an alpha channel.
 */
class ThumbnailLogo(val id: String, val image: BufferedImage) {
    override fun equals(other: Any?): Boolean = other is ThumbnailLogo && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ThumbnailLogo($id)"
}

/**
 * Reads a logo file and keeps a PNG copy of it in the folder of the app. Thus the logo stays when the user moves or
 * deletes the original file, and the app reads each format only one time.
 *
 * ImageIO reads PNG, JPG, GIF (the first frame), BMP, and TIFF. JSVG draws SVG. FFmpeg (a separate process) reads
 * WEBP and the other formats that ImageIO cannot read.
 */
object ThumbnailLogos {
    /** The file types of the file dialog. */
    val EXTENSIONS = listOf("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff", "webp", "svg")

    /** The longer side of the stored PNG. The logo is never larger than 240 px on the thumbnail, so this keeps it sharp. */
    const val MAX_SIDE = 1024

    private const val FFMPEG_TIMEOUT_S = 20L

    /**
     * Reads [source], scales it down to [MAX_SIDE], and writes it as `logo.png` in [folder]. Returns the PNG.
     * [ffmpeg] is the path of ffmpeg.exe, for the formats that ImageIO cannot read. Throws an [IOException] when the file
     * is not an image.
     */
    fun store(source: File, folder: File, ffmpeg: String?): File {
        val image = fit(read(source, ffmpeg))
        folder.mkdirs()
        val target = File(folder, "logo.png")
        val temp = File(folder, ".logo.png.tmp")
        if (!ImageIO.write(image, "png", temp)) throw IOException("Cannot write ${temp.absolutePath}")
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return target
    }

    /** The stored logo, or null when [file] is missing or broken. */
    fun load(file: File?): ThumbnailLogo? {
        if (file == null || !file.isFile) return null
        return runCatching {
            val bytes = file.readBytes()
            val image = ImageIO.read(bytes.inputStream()) ?: return null
            ThumbnailLogo(digest(bytes), argb(image))
        }.getOrNull()
    }

    fun read(source: File, ffmpeg: String?): BufferedImage {
        if (!source.isFile) throw IOException("No file ${source.absolutePath}")
        if (source.extension.equals("svg", ignoreCase = true)) return svg(source)
        runCatching { ImageIO.read(source) }.getOrNull()?.let { return argb(it) }
        if (ffmpeg != null) withFfmpeg(source, ffmpeg)?.let { return argb(it) }
        throw IOException("Cannot read the image ${source.name}")
    }

    /** Draws an SVG file at a size with [MAX_SIDE] for the longer side. */
    private fun svg(source: File): BufferedImage {
        val document = SVGLoader().load(source.toURI().toURL()) ?: throw IOException("Cannot read the SVG file ${source.name}")
        val size = document.size()
        val w0 = size.width.toDouble().takeIf { it > 0 } ?: MAX_SIDE.toDouble()
        val h0 = size.height.toDouble().takeIf { it > 0 } ?: MAX_SIDE.toDouble()
        val scale = MAX_SIDE / max(w0, h0)
        val w = (w0 * scale).roundToInt().coerceAtLeast(1)
        val h = (h0 * scale).roundToInt().coerceAtLeast(1)
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            document.render(null, g2, ViewBox(0f, 0f, w.toFloat(), h.toFloat()))
        } finally {
            g2.dispose()
        }
        return image
    }

    /** Converts the first frame of [source] to a PNG with FFmpeg, and reads it. Null when FFmpeg cannot read the file. */
    private fun withFfmpeg(source: File, ffmpeg: String): BufferedImage? {
        val temp = Files.createTempFile("bananashot-logo", ".png").toFile()
        try {
            val process = ProcessBuilder(ffmpeg, "-v", "error", "-y", "-i", source.absolutePath, "-frames:v", "1", "-c:v", "png", temp.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(FFMPEG_TIMEOUT_S, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0 || temp.length() == 0L) return null
            return ImageIO.read(temp)
        } catch (_: IOException) {
            return null
        } finally {
            temp.delete()
        }
    }

    /** The image with an alpha channel, scaled down so that its longer side is not more than [MAX_SIDE]. */
    internal fun fit(image: BufferedImage): BufferedImage {
        val longer = max(image.width, image.height)
        if (longer <= MAX_SIDE) return argb(image)
        val scale = MAX_SIDE.toDouble() / longer
        var current = argb(image)
        var w = image.width
        var h = image.height
        val targetW = (image.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (image.height * scale).roundToInt().coerceAtLeast(1)
        // Steps of one half keep a large image smooth.
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2
            h /= 2
            current = scaled(current, w, h)
        }
        return scaled(current, targetW, targetH)
    }

    private fun scaled(image: BufferedImage, w: Int, h: Int): BufferedImage {
        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g2 = result.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.drawImage(image, 0, 0, w, h, null)
        } finally {
            g2.dispose()
        }
        return result
    }

    private fun argb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_ARGB) return image
        val result = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val g2 = result.createGraphics()
        try {
            g2.drawImage(image, 0, 0, null)
        } finally {
            g2.dispose()
        }
        return result
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).take(12).joinToString("") { "%02x".format(it) }
}
