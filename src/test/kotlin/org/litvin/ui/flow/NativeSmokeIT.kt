package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.ActiveQueueSnapshot
import org.litvin.ApplicationLayout
import org.litvin.FfmpegColorAdjustmentStrategy
import org.litvin.RenderJob
import org.litvin.RenderStatus
import org.litvin.ToneRange
import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.GeometryPlan
import org.litvin.media.MediaScreen
import org.litvin.ui.flow.fixtures.VideoFrames
import org.litvin.ui.flow.harness.NativeMediaPlayers
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.harness.UiFlowMedia
import org.litvin.ui.flow.screens.ApplicationScreen
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The native checks of the packaged smoke test. The flow uses the real libmpv preview and the real FFmpeg export.
 *
 * `qa/windows/Run-UiSmoke.ps1` runs this test with the natives of the packaged app.
 * The test writes the measured values to `native-results.txt` and the frames to PNG files
 * in the folder of the `tennis.record.nativeSmoke.resultsDir` property.
 */
class NativeSmokeIT {
    private val results = linkedMapOf<String, String>()

    @Test
    fun `the preview and the FFmpeg export apply the adjustment controls`(context: UiFlowContext) {
        val application = ApplicationScreen(context)
        val layout = ApplicationLayout.current()
        record("ffmpeg", layout.ffmpegExecutable)
        record("libmpv", layout.mpvDirectory?.absolutePath ?: "not configured")
        try {
            application.projects.importMatch()
            application.points.assertReady()
            val project = context.fixtures.onlyProject()

            checkBrightnessPreview(context, application, project.directory)
            checkRotationPreview(context, application, project.directory)
            checkExport(context, application, project.directory)
        } finally {
            writeResults()
        }
    }

    private fun checkBrightnessPreview(context: UiFlowContext, application: ApplicationScreen, projectDirectory: Path) {
        application.colors.open()
        val player = loadedPlayer(context, MediaScreen.COLORS)
        val before = stableCapture(context, player, "colors-before")

        application.colors.setBrightness(BRIGHTNESS_SLIDER)
            .assertColorEditPersisted(projectDirectory, brightness = BRIGHTNESS)
        val expectedLift = FfmpegColorAdjustmentStrategy
            .map(AdjustmentsIO.readForProjectDir(projectDirectory.toString()), ToneRange.LIMITED)
            .brightnessLift
        var after: BufferedImage? = null
        context.driver.waitUntil("the mpv preview to show the brightness change", PREVIEW_TIMEOUT) {
            val lift = shaderOption(player, "tr_brightness_lift")
            val image = capture(player)?.also { after = it }
            lift != null && lift > 0.0 && image != null &&
                VideoFrames.meanLuma(image) - VideoFrames.meanLuma(before) >= expectedLift / 2
        }
        saveFrame("colors-after", checkNotNull(after))
        record("preview.brightness.lift", shaderOption(player, "tr_brightness_lift").toString())
        record("preview.brightness.lumaBefore", format(VideoFrames.meanLuma(before)))
        record("preview.brightness.lumaAfter", format(VideoFrames.meanLuma(checkNotNull(after))))
    }

    private fun checkRotationPreview(context: UiFlowContext, application: ApplicationScreen, projectDirectory: Path) {
        application.crop.open()
        val player = loadedPlayer(context, MediaScreen.CROP)
        val before = stableCapture(context, player, "crop-before")

        application.crop.setRotationDegrees(ROTATION_DEGREES)
            .assertRotationPersisted(projectDirectory, ROTATION_DEGREES)
        var after: BufferedImage? = null
        context.driver.waitUntil("the mpv preview to show the rotation", PREVIEW_TIMEOUT) {
            val rotation = shaderOption(player, "tr_rotation_deg")
            val image = capture(player)?.also { after = it }
            rotation != null && abs(rotation - ROTATION_DEGREES) < 0.001 && image != null &&
                VideoFrames.correlation(before, image) <= PREVIEW_ROTATION_MAX_CORRELATION
        }
        saveFrame("crop-after", checkNotNull(after))
        record("preview.rotation.degrees", shaderOption(player, "tr_rotation_deg").toString())
        record("preview.rotation.correlationWithUnrotated", format(VideoFrames.correlation(before, checkNotNull(after))))
    }

    private fun checkExport(context: UiFlowContext, application: ApplicationScreen, projectDirectory: Path) {
        val layout = ApplicationLayout.current()
        val source = context.fixtures.sourceVideo
        val output = context.workspace.resolve("exports").resolve("native-smoke.mp4").toAbsolutePath().normalize()
        Files.createDirectories(output.parent)
        val jobs = ConcurrentHashMap<String, RenderJob>()
        val observer: (ActiveQueueSnapshot) -> Unit = { snapshot ->
            (listOfNotNull(snapshot.current) + snapshot.queued).forEach { job -> jobs[job.id] = job }
        }

        context.services.renderService.observe(observer).use {
            application.export.open()
                .selectContent("full")
                .initialize(output)
            context.driver.waitUntil("the FFmpeg export to complete", EXPORT_TIMEOUT) {
                jobs.values.firstOrNull { it.status == RenderStatus.FAILED || it.status == RenderStatus.CANCELED }
                    ?.let { job ->
                        throw AssertionError(
                            "The export ${job.status}: ${job.failureReason}\nFFmpeg output:\n${job.stderrTail}",
                        )
                    }
                context.services.completedRenders.loadAll().any { samePath(it.outputPath, output) } &&
                    Files.isRegularFile(output)
            }
        }
        val render = context.services.completedRenders.loadAll().single { samePath(it.outputPath, output) }
        record("export.encoder", render.encoderLabel)

        val sourceStream = VideoFrames.probe(layout.ffprobeExecutable, source, "stream=width,height,duration")
        val outputStream = VideoFrames.probe(layout.ffprobeExecutable, output, "stream=codec_name,width,height,duration")
        record("export.stream", outputStream.toString())

        val adjustments = AdjustmentsIO.readForProjectDir(projectDirectory.toString())
        val sourceFrame = VideoFrames.frameAt(layout.ffmpegExecutable, source, FRAME_SECONDS)
        val outputFrame = VideoFrames.frameAt(layout.ffmpegExecutable, output, FRAME_SECONDS)
        val plan = GeometryPlan.of(adjustments, sourceFrame.width, sourceFrame.height)
        val expected = VideoFrames.applyGeometry(sourceFrame, plan, outputFrame.width, outputFrame.height)
        val unrotated = VideoFrames.scaled(sourceFrame, outputFrame.width, outputFrame.height)
        saveFrame("export-frame", outputFrame)
        saveFrame("export-expected-geometry", expected)

        val expectedCorrelation = VideoFrames.correlation(outputFrame, expected)
        val unrotatedCorrelation = VideoFrames.correlation(outputFrame, unrotated)
        val lumaChange = VideoFrames.meanLuma(outputFrame) - VideoFrames.meanLuma(expected)
        val expectedLift = FfmpegColorAdjustmentStrategy.map(adjustments, ToneRange.LIMITED).brightnessLift
        record("export.rotation.correlationWithExpected", format(expectedCorrelation))
        record("export.rotation.correlationWithUnrotated", format(unrotatedCorrelation))
        record("export.brightness.lumaChange", format(lumaChange))
        record("export.brightness.expectedLift", format(expectedLift))

        check(expectedCorrelation >= EXPORT_MIN_CORRELATION && expectedCorrelation - unrotatedCorrelation >= EXPORT_MIN_ROTATION_GAIN) {
            "The export does not show the rotation: correlation $expectedCorrelation with the rotated source, " +
                "$unrotatedCorrelation with the unrotated source."
        }
        check(lumaChange >= expectedLift / 2) {
            "The export does not show the brightness change: the mean luma changed by $lumaChange, expected about $expectedLift."
        }

        // The default quality keeps the source size, and the export has the size that the export record shows.
        assertEquals("export width", render.outWidth.toString(), outputStream["width"])
        assertEquals("export height", render.outHeight.toString(), outputStream["height"])
        assertEquals("export width (the source width)", sourceStream["width"], outputStream["width"])
        assertEquals("export height (the source height)", sourceStream["height"], outputStream["height"])
        val sourceDuration = sourceStream.getValue("duration").toDouble()
        val outputDuration = outputStream.getValue("duration").toDouble()
        check(abs(outputDuration - sourceDuration) <= DURATION_TOLERANCE_SECONDS) {
            "The export is $outputDuration s long. The source is $sourceDuration s long."
        }
    }

    private fun loadedPlayer(context: UiFlowContext, screen: MediaScreen): NativeMediaPlayers.Player {
        var player: NativeMediaPlayers.Player? = null
        context.driver.waitUntil("the $screen mpv player to load the video", PREVIEW_TIMEOUT) {
            val candidate = context.nativePlayers.players.lastOrNull { it.screen == screen }
            player = candidate
            candidate != null &&
                (candidate.property("video-params/w")?.toIntOrNull() ?: 0) > 0 &&
                onEdt { candidate.component.isShowing } &&
                candidate.videoBounds() != null
        }
        val loaded = checkNotNull(player)
        record("preview.$screen.vo", loaded.property("current-vo").orEmpty())
        record("preview.$screen.shader", loaded.property("glsl-shaders").orEmpty())
        check(!loaded.property("glsl-shaders").isNullOrBlank()) { "The $screen mpv player has no adjustment shader" }
        return loaded
    }

    /** Waits until two captures in sequence are the same picture, so that mpv has drawn the frame. */
    private fun stableCapture(context: UiFlowContext, player: NativeMediaPlayers.Player, name: String): BufferedImage {
        var previous: BufferedImage? = null
        var stable: BufferedImage? = null
        context.driver.waitUntil("the ${player.screen} mpv preview to show a still frame", PREVIEW_TIMEOUT) {
            val image = capture(player)
            val last = previous
            previous = image
            val ready = last != null && image != null && VideoFrames.meanLuma(image) > MIN_FRAME_LUMA &&
                VideoFrames.correlation(last, image) >= STILL_FRAME_MIN_CORRELATION
            if (ready) stable = image
            ready
        }
        return checkNotNull(stable).also { saveFrame(name, it) }
    }

    /** Captures the video area of [player] from the screen. Returns null when mpv does not report the area. */
    private fun capture(player: NativeMediaPlayers.Player): BufferedImage? {
        val bounds = onEdt {
            val video = player.videoBounds() ?: return@onEdt null
            val origin = player.component.locationOnScreen
            Rectangle(
                origin.x + video.x.toInt() + CAPTURE_INSET,
                origin.y + video.y.toInt() + CAPTURE_INSET,
                video.width.toInt() - 2 * CAPTURE_INSET,
                video.height.toInt() - 2 * CAPTURE_INSET,
            )
        } ?: return null
        return robot.createScreenCapture(bounds)
    }

    private fun shaderOption(player: NativeMediaPlayers.Player, name: String): Double? =
        player.property("glsl-shader-opts")
            ?.split(',')
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.toDoubleOrNull()

    private fun record(key: String, value: String) {
        results[key] = value
    }

    private fun saveFrame(name: String, image: BufferedImage) {
        val directory = resultsDirectory() ?: return
        Files.createDirectories(directory)
        ImageIO.write(image, "png", directory.resolve("$name.png").toFile())
    }

    private fun writeResults() {
        val directory = resultsDirectory() ?: return
        Files.createDirectories(directory)
        Files.writeString(
            directory.resolve("native-results.txt"),
            results.entries.joinToString(System.lineSeparator(), postfix = System.lineSeparator()) { "${it.key}=${it.value}" },
        )
    }

    private fun resultsDirectory(): Path? =
        System.getProperty(RESULTS_DIRECTORY_PROPERTY)?.takeIf { it.isNotBlank() }?.let { Path.of(it) }

    private fun samePath(path: String, expected: Path): Boolean =
        Path.of(path).toAbsolutePath().normalize() == expected

    private fun assertEquals(description: String, expected: String?, actual: String?) {
        if (expected != actual) throw AssertionError("$description: expected $expected, but was $actual")
    }

    private fun format(value: Double): String = String.format(java.util.Locale.US, "%.4f", value)

    private fun <T> onEdt(action: () -> T): T {
        if (EventQueue.isDispatchThread()) return action()
        val task = FutureTask(action)
        EventQueue.invokeAndWait(task)
        return task.get()
    }

    companion object {
        private const val RESULTS_DIRECTORY_PROPERTY = "tennis.record.nativeSmoke.resultsDir"
        private const val BRIGHTNESS_SLIDER = 20
        private const val BRIGHTNESS = 1.2f
        private const val ROTATION_DEGREES = 15f
        private const val FRAME_SECONDS = 2.0
        private const val DURATION_TOLERANCE_SECONDS = 0.25
        private const val CAPTURE_INSET = 4
        private const val MIN_FRAME_LUMA = 8.0
        private const val STILL_FRAME_MIN_CORRELATION = 0.99
        private const val PREVIEW_ROTATION_MAX_CORRELATION = 0.9
        private const val EXPORT_MIN_CORRELATION = 0.9
        private const val EXPORT_MIN_ROTATION_GAIN = 0.1
        private val PREVIEW_TIMEOUT: Duration = Duration.ofSeconds(20)
        private val EXPORT_TIMEOUT: Duration = Duration.ofMinutes(3)

        private val robot by lazy { Robot() }

        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension(
            artifactsRoot = Path.of("target", "ui-smoke-artifacts"),
            media = UiFlowMedia.NATIVE,
        )
    }
}
