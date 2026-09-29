package org.litvin.ui.flow.fixtures

import org.litvin.adjustments.GeometryPlan
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.sqrt

/** Frame and pixel helpers for the native smoke checks. */
object VideoFrames {
    private const val COMPARE_WIDTH = 96
    private const val COMPARE_HEIGHT = 64

    /** Decodes the frame at [seconds] of [video] with [ffmpegExecutable]. */
    fun frameAt(ffmpegExecutable: String, video: Path, seconds: Double): BufferedImage {
        val output = run(
            ffmpegExecutable, "-v", "error", "-ss", String.format(Locale.US, "%.3f", seconds),
            "-i", video.toString(), "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-",
        )
        return ImageIO.read(output.inputStream())
            ?: throw AssertionError("FFmpeg did not decode a frame at $seconds s of $video")
    }

    /** Reads one stream entry of [video] with [ffprobeExecutable], for example `stream=width`. */
    fun probe(ffprobeExecutable: String, video: Path, entries: String): Map<String, String> {
        val output = run(
            ffprobeExecutable, "-v", "error", "-select_streams", "v:0", "-show_entries", entries,
            "-of", "default=nw=1", video.toString(),
        ).toString(Charsets.UTF_8)
        return output.lineSequence()
            .mapNotNull { line -> line.trim().split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key to value }
    }

    /** The mean Rec. 601 luma of [image], from 0 to 255. */
    fun meanLuma(image: BufferedImage): Double {
        var sum = 0.0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) sum += luma(image.getRGB(x, y))
        }
        return sum / (image.width.toLong() * image.height)
    }

    /**
     * The Pearson correlation of the luma of [first] and [second], after both are scaled to the same small size.
     * The value is 1 for the same picture. A brightness change does not change the value much.
     */
    fun correlation(first: BufferedImage, second: BufferedImage): Double {
        val a = smallLuma(first)
        val b = smallLuma(second)
        val meanA = a.average()
        val meanB = b.average()
        var covariance = 0.0
        var varianceA = 0.0
        var varianceB = 0.0
        for (index in a.indices) {
            val da = a[index] - meanA
            val db = b[index] - meanB
            covariance += da * db
            varianceA += da * da
            varianceB += db * db
        }
        if (varianceA == 0.0 || varianceB == 0.0) return 0.0
        return covariance / sqrt(varianceA * varianceB)
    }

    /**
     * Applies [plan] to [source] with Java 2D: rotate clockwise about the center (black corners, same size),
     * crop, then scale to [width] x [height]. The export does the same with FFmpeg filters.
     */
    fun applyGeometry(source: BufferedImage, plan: GeometryPlan, width: Int, height: Int): BufferedImage {
        val rotated = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        rotated.createGraphics().apply {
            color = Color.BLACK
            fillRect(0, 0, source.width, source.height)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            transform = AffineTransform.getRotateInstance(
                Math.toRadians(plan.rotationDeg),
                source.width / 2.0,
                source.height / 2.0,
            )
            drawImage(source, 0, 0, null)
            dispose()
        }
        val (cropWidth, cropHeight, cropX, cropY) = plan.cropPixels(source.width, source.height).toList()
        return scaled(rotated.getSubimage(cropX, cropY, cropWidth, cropHeight), width, height)
    }

    fun scaled(image: BufferedImage, width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(image, 0, 0, width, height, null)
                dispose()
            }
        }

    private fun smallLuma(image: BufferedImage): DoubleArray {
        val small = scaled(image, COMPARE_WIDTH, COMPARE_HEIGHT)
        return DoubleArray(COMPARE_WIDTH * COMPARE_HEIGHT) { index ->
            luma(small.getRGB(index % COMPARE_WIDTH, index / COMPARE_WIDTH))
        }
    }

    private fun luma(rgb: Int): Double {
        val red = (rgb shr 16) and 0xff
        val green = (rgb shr 8) and 0xff
        val blue = rgb and 0xff
        return 0.299 * red + 0.587 * green + 0.114 * blue
    }

    private fun run(vararg command: String): ByteArray {
        val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = ByteArrayOutputStream()
        val reader = Thread { process.inputStream.use { it.copyTo(output) } }.apply { start() }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("Timed out: ${command.joinToString(" ")}")
        }
        reader.join(5_000)
        if (process.exitValue() != 0) {
            throw AssertionError("Exit code ${process.exitValue()}: ${command.joinToString(" ")}")
        }
        return output.toByteArray()
    }
}
