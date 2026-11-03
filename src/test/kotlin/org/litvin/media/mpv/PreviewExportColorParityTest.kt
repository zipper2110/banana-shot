package org.litvin.media.mpv

import com.sun.jna.Platform
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.litvin.ApplicationLayout
import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertTrue

/**
 * Compares the colors of the preview with the colors of the export (B-13, step 6). It is not a unit test: it opens
 * an mpv window and uses the GPU, so it runs only with `-Dbananashot.previewParity=true` on a computer with a screen.
 *
 * The test makes a video of color patches. For each color setting, it renders one frame in two ways:
 * - the preview: libmpv with the options of the app, `vo=gpu-next`, the `tr-adjust.hook` shader, and the shader
 *   options of [MpvShaderParams], then `screenshot-to-file ... window`;
 * - the export: ffmpeg with the `-vf` filter of [FFmpegCommandBuilder], converted to RGB in the same way.
 * Then it compares the mean color of the middle of each patch.
 *
 * Run: mvn -o test -Dtest=PreviewExportColorParityTest -Dbananashot.previewParity=true -DargLine=-Djava.awt.headless=false
 * The report is in target/preview-parity.
 */
class PreviewExportColorParityTest {
    private val outDir = File("target/preview-parity")
    private val width = 1280
    private val height = 720
    private val columns = 8
    private val rows = 4

    /** The largest allowed difference of a patch mean, in 8-bit RGB codes. */
    private val tolerance = 4.0

    /** Slider values: brightness, contrast, saturation, shadows, highlights, temperature. */
    private val cases = linkedMapOf(
        "neutral" to listOf(0, 0, 0, 0, 0, 0),
        "contrast +40" to listOf(0, 40, 0, 0, 0, 0),
        "contrast -40" to listOf(0, -40, 0, 0, 0, 0),
        "saturation +60" to listOf(0, 0, 60, 0, 0, 0),
        "saturation -70" to listOf(0, 0, -70, 0, 0, 0),
        "brightness +40" to listOf(40, 0, 0, 0, 0, 0),
        "brightness -40" to listOf(-40, 0, 0, 0, 0, 0),
        "shadows +60 highlights -60" to listOf(0, 0, 0, 60, -60, 0),
        "temperature +80" to listOf(0, 0, 0, 0, 0, 80),
        "temperature -80" to listOf(0, 0, 0, 0, 0, -80),
        "all" to listOf(-20, 25, 30, 35, -40, 45),
    )

    @Test
    fun `the preview shows the colors of the export`() {
        assumeTrue(System.getProperty("bananashot.previewParity") == "true", "run with -Dbananashot.previewParity=true")
        assumeTrue(!GraphicsEnvironment.isHeadless(), "needs a screen")
        val lib = LibMpv.instanceOrNull()
        assumeTrue(lib != null, "libmpv is not available")
        val shader = MpvShaderFile.path()
        assumeTrue(shader != null, "the shader is not available")
        // On macOS, mpv makes its window on the main thread. AWT runs the main thread of AppKit.
        Toolkit.getDefaultToolkit()

        outDir.deleteRecursively()
        outDir.mkdirs()
        val pattern = File(outDir, "pattern.png").also { ImageIO.write(patternImage(), "png", it) }
        val video = File(outDir, "pattern.mp4")
        ffmpeg(
            "-loop", "1", "-i", pattern.absolutePath, "-t", "1", "-r", "30",
            "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
            "-c:v", "libx264", "-qp", "0", "-color_range", "tv", "-colorspace", "bt709",
            "-color_primaries", "bt709", "-color_trc", "bt709", video.absolutePath,
        )

        val report = StringBuilder("case, worst patch, worst difference (R, G, B), mean difference\n")
        var worst = 0.0
        val loaded = CountDownLatch(1)
        val mpv = MpvCore(lib!!, previewOptions(shader!!), "parity") { event ->
            if (event.id == LibMpv.EVENT_PLAYBACK_RESTART) loaded.countDown()
        }
        try {
            mpv.commandSync("loadfile", video.absolutePath)
            check(loaded.await(20, TimeUnit.SECONDS)) { "mpv did not show the video" }
            Thread.sleep(1000)
            for ((name, sliders) in cases) {
                val (brightness, contrast, saturation, shadows, highlights, temperature) = sliders
                val adjustments = AdjustmentsUiConverter.slidersToModel(brightness, contrast, saturation, shadows, highlights, temperature)
                val slug = name.replace("+", "plus").replace("-", "minus").replace(Regex("[^a-z0-9]+"), "-")

                mpv.setPropertySync("glsl-shader-opts", MpvShaderParams.build(adjustments, MpvVideoInfo(width, height, "bt.709", "limited")))
                Thread.sleep(700)
                val previewFile = File(outDir, "$slug-preview.png")
                check(mpv.commandSync("screenshot-to-file", previewFile.absolutePath, "window") >= 0) { "mpv did not save $previewFile" }
                val exportFile = File(outDir, "$slug-export.png")
                ffmpeg("-i", video.absolutePath, "-vf", exportFilter(adjustments, video) + ",scale=in_color_matrix=bt709:in_range=tv:out_range=pc,format=rgb24", "-frames:v", "1", exportFile.absolutePath)

                val preview = patchMeans(ImageIO.read(previewFile))
                val export = patchMeans(ImageIO.read(exportFile))
                val differences = preview.indices.map { i -> DoubleArray(3) { abs(preview[i][it] - export[i][it]) } }
                val worstIndex = differences.indices.maxBy { differences[it].max() }
                val caseWorst = differences[worstIndex].max()
                worst = max(worst, caseWorst)
                val mean = differences.sumOf { it.average() } / differences.size
                report.append(
                    "$name, ${worstIndex % columns},${worstIndex / columns}, " +
                        differences[worstIndex].joinToString(" ") { "%.1f".format(it) } +
                        " (preview ${preview[worstIndex].joinToString(" ") { "%.0f".format(it) }}," +
                        " export ${export[worstIndex].joinToString(" ") { "%.0f".format(it) }}), ${"%.2f".format(mean)}\n",
                )
            }
        } finally {
            mpv.close()
        }
        File(outDir, "report.csv").writeText(report.toString())
        println(report)
        assertTrue(worst <= tolerance, "The preview differs from the export by up to $worst codes. See ${outDir.absolutePath}/report.csv\n$report")
    }

    /** The options of the app preview (see MpvSwingMediaPlayerAdapter), with an own window of the video size. */
    private fun previewOptions(shader: File): List<Pair<String, String>> = buildList {
        add("config" to "no")
        add("load-scripts" to "no")
        add("terminal" to "no")
        add("input-default-bindings" to "no")
        add("osd-level" to "0")
        add("idle" to "yes")
        add("keep-open" to "always")
        add("pause" to "yes")
        add("vo" to "gpu-next")
        if (Platform.isMac()) {
            add("gpu-api" to "vulkan")
            add("gpu-context" to "macvk")
            add("hwdec" to "videotoolbox,auto-safe")
        } else {
            add("gpu-api" to "d3d11")
            add("hwdec" to "d3d11va,auto-safe")
        }
        add("force-window" to "yes")
        add("border" to "no")
        add("geometry" to "${width}x$height")
        add("hidpi-window-scale" to "no")
        add("hdr-compute-peak" to "no")
        add("background-color" to "#000000")
        add("glsl-shaders" to shader.absolutePath)
    }

    /** The color part of the export filter for [adjustments], as the app builds it. */
    private fun exportFilter(adjustments: AdjustmentsV1, video: File): String {
        val presets = ExportPresetsIO.load()
        val args = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = video.absolutePath,
                outputPath = File(outDir, "unused.mp4").absolutePath,
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = width,
                outHeight = height,
                idleTrim = false,
                adjustments = adjustments,
                sourceWidth = width,
                sourceHeight = height,
            ),
        ).args
        val index = args.indexOf("-vf")
        check(index >= 0) { "The export command has no -vf: $args" }
        return args[index + 1]
    }

    /** Gray steps, saturated colors, dark colors, and light colors. */
    private fun patternImage(): BufferedImage {
        val colors = listOf(
            (0 until columns).map { Color(it * 255 / (columns - 1), it * 255 / (columns - 1), it * 255 / (columns - 1)) },
            listOf(Color(220, 30, 30), Color(30, 200, 40), Color(30, 60, 220), Color(30, 210, 210), Color(210, 40, 200), Color(230, 220, 40), Color(240, 140, 30), Color(225, 170, 140)),
            listOf(Color(60, 20, 20), Color(20, 60, 25), Color(20, 25, 70), Color(40, 40, 40), Color(70, 50, 30), Color(30, 50, 60), Color(15, 15, 15), Color(90, 70, 60)),
            listOf(Color(250, 200, 200), Color(200, 250, 210), Color(200, 210, 250), Color(245, 245, 230), Color(195, 225, 140), Color(130, 170, 90), Color(160, 120, 80), Color(100, 140, 190)),
        )
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        for (row in 0 until rows) for (column in 0 until columns) {
            g.color = colors[row][column]
            g.fillRect(column * width / columns, row * height / rows, width / columns, height / rows)
        }
        g.dispose()
        return image
    }

    /** The mean RGB of the middle half of each patch. The image can have any size with the aspect of the video. */
    private fun patchMeans(image: BufferedImage): List<DoubleArray> {
        check(abs(image.width.toDouble() / image.height - width.toDouble() / height) < 0.01) {
            "The image is ${image.width}x${image.height}, not 16:9"
        }
        val cellW = image.width.toDouble() / columns
        val cellH = image.height.toDouble() / rows
        return (0 until rows * columns).map { index ->
            val x0 = ((index % columns + 0.25) * cellW).toInt()
            val y0 = ((index / columns + 0.25) * cellH).toInt()
            val sum = DoubleArray(3)
            var count = 0
            for (y in y0 until (y0 + cellH / 2).toInt()) for (x in x0 until (x0 + cellW / 2).toInt()) {
                val rgb = image.getRGB(x, y)
                sum[0] += (rgb shr 16 and 0xFF).toDouble()
                sum[1] += (rgb shr 8 and 0xFF).toDouble()
                sum[2] += (rgb and 0xFF).toDouble()
                count++
            }
            DoubleArray(3) { sum[it] / count }
        }
    }

    private fun ffmpeg(vararg args: String) {
        val process = ProcessBuilder(listOf(ApplicationLayout.current().ffmpegExecutable, "-v", "error", "-y") + args)
            .redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed: $log" }
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
