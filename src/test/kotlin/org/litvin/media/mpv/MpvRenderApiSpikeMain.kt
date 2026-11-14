package org.litvin.media.mpv

import com.sun.jna.Pointer
import org.litvin.ApplicationLayout
import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.Robot
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import kotlin.math.abs
import kotlin.math.max

/** JNI part of the spike: `src/test/native/macos/mpv_gl_layer.m`. */
internal object MpvGlLayerNative {
    @JvmStatic external fun attach(canvas: Canvas, mpvHandle: Long): Long
    @JvmStatic external fun detach(handle: Long)
    @JvmStatic external fun nanos(): Long
    @JvmStatic external fun debug(handle: Long): String
    @JvmStatic external fun redraw(handle: Long)
    @JvmStatic external fun setProbe(handle: Long, on: Boolean, x: Double, y: Double)
    @JvmStatic external fun stats(handle: Long): LongArray
    @JvmStatic external fun resetStats(handle: Long)
    @JvmStatic external fun capture(handle: Long, path: String, timeoutMs: Long): Boolean
    @JvmStatic external fun putUnderWindowLayer(handle: Long, canvas: Canvas)
    @JvmStatic external fun setFrame(handle: Long, x: Double, y: Double, w: Double, h: Double)
}

/**
 * Embed spike (B-13, 2026-11-14): the libmpv render API (vo=libmpv, OpenGL) in a CAOpenGLLayer in the Java window,
 * through the JAWT surface layers. Manual harness, not a unit test. macOS only.
 *
 * Build the native part first (see the comment in `src/test/native/macos/mpv_gl_layer.m`), then run:
 *   mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=org.litvin.media.mpv.MpvRenderApiSpikeMainKt -Dexec.args="<mode> <args>"
 * Modes:
 *   play <video> <seconds> [startSeconds]   playback counts and render timing
 *   shader <video>                         delay of one shader change (baked parameters, new file each change)
 *   osd <video> <outDir>                   scoreboard (osd-overlay) and comments (sub-add), PNG of the layer
 *   parity <outDir> [hdr]                  preview against export (SDR), or gpu against gpu-next (HDR)
 *   swing <video> <outDir>                 Swing popups, a dialog, and a Swing component over the video
 */
fun main(args: Array<String>) {
    System.load(File("target/libmpvgllayer.dylib").absolutePath)
    // AWT runs the main thread of AppKit.
    java.awt.Toolkit.getDefaultToolkit()
    val spike = RenderApiSpike()
    try {
        when (args[0]) {
            "play" -> spike.play(File(args[1]), args[2].toInt(), args.getOrNull(3)?.toDouble() ?: 0.0)
            "shader" -> spike.shader(File(args[1]))
            "osd" -> spike.osd(File(args[1]), File(args[2]))
            "parity" -> spike.parity(File(args[1]), args.getOrNull(2) == "hdr")
            "swing" -> spike.swing(File(args[1]), File(args[2]))
            else -> error("unknown mode ${args[0]}")
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    } finally {
        spike.close()
        System.exit(0)
    }
}

internal class RenderApiSpike {
    private val lib = LibMpv.instanceOrNull() ?: error("no libmpv")
    private lateinit var frame: JFrame
    private lateinit var canvas: Canvas
    private lateinit var layered: JLayeredPane
    private var mpv: MpvCore? = null
    private var layer = 0L
    private val restart = java.util.concurrent.Semaphore(0)
    private val props = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var shaderCounter = 0
    private val shaderDir = File("target/render-api-spike/shaders").apply { deleteRecursively(); mkdirs() }

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    private fun log(line: String) = println("[spike ${"%.3f".format(System.nanoTime() / 1e9 % 1000)}] $line")

    /** Opens a window with a canvas of [videoW]x[videoH] points and a control row under it. */
    fun open(videoW: Int = 1200, videoH: Int = 675, options: List<Pair<String, String>> = emptyList()) {
        onEdt {
            frame = JFrame("render API spike")
            canvas = Canvas().apply {
                background = Color.BLACK
                preferredSize = Dimension(videoW, videoH)
            }
            layered = JLayeredPane().apply {
                preferredSize = Dimension(videoW, videoH)
                add(canvas, JLayeredPane.DEFAULT_LAYER)
                canvas.setBounds(0, 0, videoW, videoH)
            }
            frame.contentPane.add(layered, BorderLayout.CENTER)
            frame.contentPane.add(
                JPanel().apply {
                    add(JSlider(0, 100, 50))
                    add(JComboBox(arrayOf("Item one", "Item two", "Item three", "Item four", "Item five")))
                    add(JButton("Button"))
                },
                BorderLayout.SOUTH,
            )
            frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            frame.pack()
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
        }
        Thread.sleep(500)
        val all = baseOptions() + options
        val core = MpvCore(lib, all, "render-api", ::onEvent)
        mpv = core
        for (p in listOf(
            "hwdec-current", "current-vo", "frame-drop-count", "decoder-frame-drop-count", "vo-delayed-frame-count",
            "estimated-vf-fps", "video-params/w", "video-params/h", "osd-dimensions/w", "osd-dimensions/h",
            "osd-dimensions/ml", "osd-dimensions/mr", "osd-dimensions/mt", "osd-dimensions/mb", "container-fps",
        )) core.observe(p, LibMpv.FORMAT_STRING)
        val handle = MpvCore::class.java.getDeclaredField("handle").apply { isAccessible = true }.get(core) as Pointer
        onEdt { layer = MpvGlLayerNative.attach(canvas, Pointer.nativeValue(handle)) }
        check(layer != 0L) { "attach failed" }
        // The JDK does not set the frame of the layer, so the app must follow the canvas.
        val follow = object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) = updateFrame()
            override fun componentMoved(e: java.awt.event.ComponentEvent) = updateFrame()
        }
        onEdt {
            canvas.addComponentListener(follow)
            frame.addComponentListener(follow)
            updateFrame()
        }
        Thread.sleep(300)
        log("attached layer; mpv render context made")
    }

    private fun updateFrame() {
        if (layer == 0L || !canvas.isShowing) return
        val p = SwingUtilities.convertPoint(canvas, 0, 0, frame)
        val insets = frame.insets
        MpvGlLayerNative.setFrame(layer, (p.x - insets.left).toDouble(), (p.y - insets.top).toDouble(), canvas.width.toDouble(), canvas.height.toDouble())
    }

    private fun baseOptions(): List<Pair<String, String>> = buildList {
        add("config" to "no")
        add("load-scripts" to "no")
        add("ytdl" to "no")
        add("osc" to "no")
        add("terminal" to "no")
        add("input-default-bindings" to "no")
        add("osd-level" to "0")
        add("idle" to "yes")
        add("keep-open" to "always")
        add("pause" to "yes")
        add("vo" to "libmpv")
        add("hwdec" to "videotoolbox,auto-safe")
        add("ao" to "avfoundation")
        add("hdr-compute-peak" to "no")
        add("hr-seek" to "yes")
        add("hr-seek-framedrop" to "no")
        add("background-color" to "#000000")
        System.getenv("MPV_EXTRA")?.split(',')?.filter { it.contains('=') }?.forEach { add(it.substringBefore('=') to it.substringAfter('=')) }
        org.litvin.shared.util.BundledFonts.directoryOrNull()?.let { fonts ->
            add("osd-fonts-dir" to fonts.absolutePath)
            add("sub-fonts-dir" to fonts.absolutePath)
        }
    }

    private fun onEvent(event: MpvEvent) {
        when (event.id) {
            LibMpv.EVENT_PLAYBACK_RESTART -> restart.release()
            LibMpv.EVENT_LOG_MESSAGE -> event.logMessage()?.let { (prefix, level, text) -> log("mpv $level [$prefix] $text") }
            LibMpv.EVENT_PROPERTY_CHANGE -> {
                val name = event.propertyName() ?: return
                props[name] = event.propertyString() ?: "-"
            }
            LibMpv.EVENT_END_FILE -> log("end file reason=${event.endFileReason()} error=${event.endFileError()}")
        }
    }

    private fun load(video: File, startSeconds: Double = 0.0) {
        restart.drainPermits()
        mpv!!.command("loadfile", video.absolutePath, "replace", "-1", "start=$startSeconds")
        check(restart.tryAcquire(20, TimeUnit.SECONDS)) { "mpv did not show $video" }
        Thread.sleep(300)
        log("loaded ${video.name}: hwdec=${props["hwdec-current"]} vo=${props["current-vo"]} " +
            "size=${props["video-params/w"]}x${props["video-params/h"]} fps=${props["container-fps"]} " +
            "osd=${props["osd-dimensions/w"]}x${props["osd-dimensions/h"]} layer=${MpvGlLayerNative.stats(layer).let { "${it[9]}x${it[10]}" }}")
    }

    fun close() {
        val core = mpv ?: return
        if (layer != 0L) MpvGlLayerNative.detach(layer)
        core.close()
        if (this::frame.isInitialized) onEdt { frame.dispose() }
    }

    // ---- Q1: playback ---------------------------------------------------------------------------------

    fun play(video: File, seconds: Int, start: Double) {
        open()
        load(video, start)
        playFor(seconds, "play ${video.name}")
    }

    private fun playFor(seconds: Int, label: String, during: (() -> Unit)? = null) {
        val core = mpv!!
        val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val drop0 = props["frame-drop-count"]?.toLongOrNull() ?: 0
        val dec0 = props["decoder-frame-drop-count"]?.toLongOrNull() ?: 0
        val late0 = props["vo-delayed-frame-count"]?.toLongOrNull() ?: 0
        core.setProperty("pause", "no")
        Thread.sleep(1000)
        MpvGlLayerNative.resetStats(layer)
        val cpu0 = os.processCpuTime
        val t0 = System.nanoTime()
        while (System.nanoTime() - t0 < seconds * 1_000_000_000L) {
            if (during != null) during() else Thread.sleep(100)
        }
        val wall = (System.nanoTime() - t0) / 1e9
        val cpu = (os.processCpuTime - cpu0) / 1e9 / wall
        val s = MpvGlLayerNative.stats(layer)
        log("debug: ${MpvGlLayerNative.debug(layer)}")
        core.setProperty("pause", "yes")
        Thread.sleep(300)
        val drops = (props["frame-drop-count"]?.toLongOrNull() ?: 0) - drop0
        val decDrops = (props["decoder-frame-drop-count"]?.toLongOrNull() ?: 0) - dec0
        val late = (props["vo-delayed-frame-count"]?.toLongOrNull() ?: 0) - late0
        log(String.format(
            "%s: %.1f s; renders=%d (%.1f/s); render ms mean=%.2f max=%.2f; interval ms mean=%.2f; intervals>25ms=%d; " +
                "vo drops=%d; decoder drops=%d; vo delayed=%d; est fps=%s; process CPU=%.2f cores; hwdec=%s",
            label, wall, s[0], s[0] / wall, s[2] / 1e6 / max(1, s[0]), s[3] / 1e6,
            s[5] / 1e6 / max(1, s[6]), s[4], drops, decDrops, late, props["estimated-vf-fps"], cpu, props["hwdec-current"],
        ))
    }

    // ---- Q2: shader parameters written into the shader text ------------------------------------------

    /** Writes the parameter values of [opts] into the shader text. Returns null when tr_active is 0. */
    internal fun bakedShader(opts: String): String? {
        val values = opts.split(',').associate { it.substringBefore('=') to it.substringAfter('=') }
        if (values["tr_active"] == "0") return null
        val source = MpvShaderFile.path()!!.readText()
        val out = StringBuilder()
        val lines = source.lines()
        var i = 0
        var inHookHeader = false
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("//!PARAM") -> {
                    // Skip the PARAM header lines and the default value line.
                    i++
                    while (i < lines.size && lines[i].startsWith("//!")) i++
                    i++
                    continue
                }
                line.startsWith("//!HOOK") -> {
                    inHookHeader = true
                    out.appendLine(line)
                }
                line.startsWith("//!WHEN") -> Unit
                inHookHeader && !line.startsWith("//!") -> {
                    inHookHeader = false
                    for ((k, v) in values) out.appendLine("#define $k $v")
                    out.appendLine(line)
                }
                else -> out.appendLine(line)
            }
            i++
        }
        return out.toString()
    }

    /** Sets the shader for [adjustments] with a new file, as the app would do after the debounce. */
    private fun applyBaked(adjustments: AdjustmentsV1, w: Int, h: Int, sync: Boolean = false): String {
        val opts = MpvShaderParams.build(adjustments, MpvVideoInfo(w, h, "bt.709", "limited"))
        val text = bakedShader(opts)
        val value = if (text == null) "" else File(shaderDir, "tr-${shaderCounter++}.hook").apply { writeText(text) }.absolutePath
        if (sync) mpv!!.setPropertySync("glsl-shaders", value) else mpv!!.setProperty("glsl-shaders", value)
        return value
    }

    fun shader(video: File) {
        open()
        load(video, 5.0)
        val w = props["video-params/w"]!!.toInt()
        val h = props["video-params/h"]!!.toInt()
        // The probe reads the middle pixel after each render.
        MpvGlLayerNative.setProbe(layer, true, 0.5, 0.5)
        applyBaked(AdjustmentsV1(), w, h, sync = true)
        Thread.sleep(500)

        // A. Paused: alternate the brightness. Measure from the property set to the first render with a new pixel.
        val delays = mutableListOf<Double>()
        repeat(16) { n ->
            val before = MpvGlLayerNative.stats(layer)[8]
            val adj = AdjustmentsUiConverter.slidersToModel(if (n % 2 == 0) 50 else -50, 0, 0, 0, 0, 0)
            val t0 = MpvGlLayerNative.nanos()
            applyBaked(adj, w, h)
            var s = MpvGlLayerNative.stats(layer)
            while ((s[8] == before || s[7] < t0) && MpvGlLayerNative.nanos() - t0 < 3_000_000_000L) {
                Thread.sleep(1)
                s = MpvGlLayerNative.stats(layer)
            }
            delays += (s[7] - t0) / 1e6
            Thread.sleep(300)
        }
        log("shader change delay, paused, new file each change (ms): ${delays.joinToString { "%.1f".format(it) }}; " +
            "median=${"%.1f".format(delays.sorted()[delays.size / 2])}")

        // B. Same file path, new content: does mpv load the file again?
        val fixed = File(shaderDir, "fixed.hook")
        fixed.writeText(bakedShader(MpvShaderParams.build(AdjustmentsUiConverter.slidersToModel(50, 0, 0, 0, 0, 0), MpvVideoInfo(w, h, "bt.709", "limited")))!!)
        mpv!!.setPropertySync("glsl-shaders", fixed.absolutePath)
        Thread.sleep(500)
        val p1 = MpvGlLayerNative.stats(layer)[8]
        fixed.writeText(bakedShader(MpvShaderParams.build(AdjustmentsUiConverter.slidersToModel(-50, 0, 0, 0, 0, 0), MpvVideoInfo(w, h, "bt.709", "limited")))!!)
        mpv!!.setPropertySync("glsl-shaders", "")
        mpv!!.setPropertySync("glsl-shaders", fixed.absolutePath)
        Thread.sleep(500)
        val p2 = MpvGlLayerNative.stats(layer)[8]
        log("same path, new text: pixel before=${Integer.toHexString(p1.toInt())} after=${Integer.toHexString(p2.toInt())} " +
            "(${if (p1 == p2) "mpv did NOT load the file again" else "mpv loaded the file again"})")

        // C. Playback with a slider drag: a change each 50 ms, 100 ms debounce (as a drag that never stops would be
        // too slow, apply at most each 100 ms).
        MpvGlLayerNative.setProbe(layer, false, 0.5, 0.5)
        playFor(10, "play, no shader changes")
        var step = 0
        var lastApply = 0L
        playFor(10, "play, shader change each 100 ms (geometry + color)") {
            step++
            val now = System.nanoTime()
            if (now - lastApply >= 100_000_000L) {
                lastApply = now
                val adj = AdjustmentsUiConverter.slidersToModel((step % 40) - 20, (step % 30) - 15, 10, 0, 0, 0)
                    .copy(rotationDeg = ((step % 20) - 10).toFloat() / 2f, zoom = 1.2f)
                applyBaked(adj, w, h)
            }
            Thread.sleep(50)
        }
        log("shader changes during play: ${shaderCounter} files")
    }

    // ---- Q3: scoreboard and comments ------------------------------------------------------------------

    fun osd(video: File, outDir: File) {
        outDir.mkdirs()
        open()
        load(video, 5.0)
        val osdW = props["osd-dimensions/w"]!!.toInt()
        val osdH = props["osd-dimensions/h"]!!.toInt()
        val ml = props["osd-dimensions/ml"]!!.toDouble()
        val mt = props["osd-dimensions/mt"]!!.toDouble()
        val mr = props["osd-dimensions/mr"]!!.toDouble()
        val mb = props["osd-dimensions/mb"]!!.toDouble()
        val area = Rectangle2D.Double(ml, mt, osdW - ml - mr, osdH - mt - mb)
        val display = ScoreboardComponent.display(
            OverlaySpan(startMs = 0, endMs = 1, text = "", gamesP1 = 3, gamesP2 = 2, p1Pts = 3, p2Pts = 1, completedSets = listOf(6 to 4)),
        )
        val settings = ScoreboardSettingsV1()
        val scene = ScoreboardLayouts.scene(display, settings)
        val events = ScoreboardAss.events(scene, ScoreboardAss.place(scene, settings, area.x, area.y, area.width, area.height))
        mpv!!.commandSync("osd-overlay", "2", "ass-events", events.joinToString("\n"), osdW.toString(), osdH.toString(), "0")
        val ass = File(outDir, "comment.ass")
        ass.writeText(
            """
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 1920
            PlayResY: 1080

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Inter,64,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,1,0,0,0,100,100,0,0,3,4,0,2,40,40,80,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:00.00,9:00:00.00,Default,,0,0,0,,Comment from sub-add: great rally!
            """.trimIndent(),
        )
        mpv!!.commandSync("sub-add", ass.absolutePath, "select")
        Thread.sleep(800)
        val png = File(outDir, "osd-layer.png")
        log("osd: osd=${osdW}x$osdH margins=$ml,$mt,$mr,$mb; capture=${MpvGlLayerNative.capture(layer, png.absolutePath, 3000)} -> $png")
        // Also with the shader active (rotation), to see that the overlay is not changed by the shader.
        applyBaked(AdjustmentsV1(rotationDeg = 8f, contrast = 1.4f), props["video-params/w"]!!.toInt(), props["video-params/h"]!!.toInt(), sync = true)
        Thread.sleep(800)
        val png2 = File(outDir, "osd-layer-shader.png")
        log("osd + shader: capture=${MpvGlLayerNative.capture(layer, png2.absolutePath, 3000)} -> $png2")
        screen(File(outDir, "osd-screen.png"))
    }

    private fun screen(file: File) {
        runCatching {
            val bounds = Rectangle(frame.contentPane.locationOnScreen, frame.contentPane.size)
            ImageIO.write(Robot().createScreenCapture(bounds), "png", file)
            log("screen capture -> $file")
        }.onFailure { log("screen capture failed: $it") }
    }

    // ---- Q4: preview against export -------------------------------------------------------------------

    private val pw = 1280
    private val ph = 720
    private val columns = 8
    private val rows = 4
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

    fun parity(outDir: File, hdr: Boolean) {
        outDir.mkdirs()
        val pattern = File(outDir, "pattern.png").also { ImageIO.write(patternImage(), "png", it) }
        val video = File(outDir, if (hdr) "pattern-pq.mp4" else "pattern.mp4")
        if (hdr) {
            // The pattern codes are read as PQ BT.2020 (10 bit): a simple HDR10 signal for the tone mapping.
            ffmpeg(
                "-loop", "1", "-i", pattern.absolutePath, "-t", "1", "-r", "30",
                "-vf", "scale=out_color_matrix=bt2020:out_range=tv,format=yuv420p10le",
                "-c:v", "libx265", "-x265-params", "lossless=1:log-level=error:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:range=limited", "-color_range", "tv", "-colorspace", "bt2020nc",
                "-color_primaries", "bt2020", "-color_trc", "smpte2084", video.absolutePath,
            )
        } else {
            ffmpeg(
                "-loop", "1", "-i", pattern.absolutePath, "-t", "1", "-r", "30",
                "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
                "-c:v", "libx264", "-qp", "0", "-color_range", "tv", "-colorspace", "bt709",
                "-color_primaries", "bt709", "-color_trc", "bt709", video.absolutePath,
            )
        }
        // The canvas is 640x360 points: 1280x720 pixels on a Retina display.
        open(640, 360)
        load(video)
        val report = StringBuilder()
        if (hdr) {
            val file = File(outDir, "hdr-gpu.png")
            MpvGlLayerNative.capture(layer, file.absolutePath, 3000)
            val reference = File(outDir, "hdr-gpu-next.png")
            gpuNextScreenshot(video, reference)
            val a = patchMeans(ImageIO.read(file))
            val b = patchMeans(ImageIO.read(reference))
            val d = a.indices.map { i -> DoubleArray(3) { abs(a[i][it] - b[i][it]) } }
            report.appendLine("HDR (PQ) tone mapping, render API gpu vs child window gpu-next: worst=${"%.1f".format(d.maxOf { it.max() })} mean=${"%.2f".format(d.sumOf { it.average() } / d.size)}")
            for (i in a.indices) report.appendLine("patch $i: gpu ${a[i].joinToString(" ") { "%.0f".format(it) }} / gpu-next ${b[i].joinToString(" ") { "%.0f".format(it) }}")
        } else {
            var worst = 0.0
            for ((name, s) in cases) {
                val adjustments = AdjustmentsUiConverter.slidersToModel(s[0], s[1], s[2], s[3], s[4], s[5])
                applyBaked(adjustments, pw, ph, sync = true)
                Thread.sleep(500)
                val slug = name.replace("+", "plus").replace("-", "minus").replace(Regex("[^a-z0-9]+"), "-")
                val previewFile = File(outDir, "$slug-preview.png")
                check(MpvGlLayerNative.capture(layer, previewFile.absolutePath, 3000)) { "no capture" }
                val exportFile = File(outDir, "$slug-export.png")
                ffmpeg("-i", video.absolutePath, "-vf", exportFilter(adjustments, video, outDir) + ",scale=in_color_matrix=bt709:in_range=tv:out_range=pc,format=rgb24", "-frames:v", "1", exportFile.absolutePath)
                val preview = patchMeans(ImageIO.read(previewFile))
                val export = patchMeans(ImageIO.read(exportFile))
                val diff = preview.indices.map { i -> DoubleArray(3) { abs(preview[i][it] - export[i][it]) } }
                val wi = diff.indices.maxBy { diff[it].max() }
                worst = max(worst, diff[wi].max())
                report.appendLine("$name: worst patch $wi diff ${diff[wi].joinToString(" ") { "%.1f".format(it) }}, mean ${"%.2f".format(diff.sumOf { it.average() } / diff.size)}")
            }
            report.appendLine("SDR worst over all cases: ${"%.1f".format(worst)} codes")
        }
        File(outDir, if (hdr) "report-hdr.txt" else "report-sdr.txt").writeText(report.toString())
        log("parity:\n$report")
    }

    /** The current preview path: gpu-next in an own mpv window, `screenshot-to-file ... window`. */
    private fun gpuNextScreenshot(video: File, out: File) {
        val loaded = CountDownLatch(1)
        val options = listOf(
            "config" to "no", "terminal" to "no", "osd-level" to "0", "idle" to "yes", "keep-open" to "always",
            "pause" to "yes", "vo" to "gpu-next", "gpu-api" to "vulkan", "gpu-context" to "macvk",
            "hwdec" to "videotoolbox,auto-safe", "force-window" to "yes", "border" to "no", "geometry" to "${pw}x$ph",
            "hidpi-window-scale" to "no", "hdr-compute-peak" to "no", "background-color" to "#000000",
        )
        val other = MpvCore(lib, options, "gpu-next") { if (it.id == LibMpv.EVENT_PLAYBACK_RESTART) loaded.countDown() }
        try {
            other.commandSync("loadfile", video.absolutePath)
            check(loaded.await(20, TimeUnit.SECONDS))
            Thread.sleep(1000)
            check(other.commandSync("screenshot-to-file", out.absolutePath, "window") >= 0)
        } finally {
            other.close()
        }
    }

    private fun exportFilter(adjustments: AdjustmentsV1, video: File, outDir: File): String {
        val presets = ExportPresetsIO.load()
        val args = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = video.absolutePath,
                outputPath = File(outDir, "unused.mp4").absolutePath,
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = pw,
                outHeight = ph,
                idleTrim = false,
                adjustments = adjustments,
                sourceWidth = pw,
                sourceHeight = ph,
            ),
        ).args
        return args[args.indexOf("-vf") + 1]
    }

    private fun patternImage(): BufferedImage {
        val colors = listOf(
            (0 until columns).map { Color(it * 255 / (columns - 1), it * 255 / (columns - 1), it * 255 / (columns - 1)) },
            listOf(Color(220, 30, 30), Color(30, 200, 40), Color(30, 60, 220), Color(30, 210, 210), Color(210, 40, 200), Color(230, 220, 40), Color(240, 140, 30), Color(225, 170, 140)),
            listOf(Color(60, 20, 20), Color(20, 60, 25), Color(20, 25, 70), Color(40, 40, 40), Color(70, 50, 30), Color(30, 50, 60), Color(15, 15, 15), Color(90, 70, 60)),
            listOf(Color(250, 200, 200), Color(200, 250, 210), Color(200, 210, 250), Color(245, 245, 230), Color(195, 225, 140), Color(130, 170, 90), Color(160, 120, 80), Color(100, 140, 190)),
        )
        val image = BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        for (row in 0 until rows) for (column in 0 until columns) {
            g.color = colors[row][column]
            g.fillRect(column * pw / columns, row * ph / rows, pw / columns, ph / rows)
        }
        g.dispose()
        return image
    }

    private fun patchMeans(image: BufferedImage): List<DoubleArray> {
        check(abs(image.width.toDouble() / image.height - pw.toDouble() / ph) < 0.01) { "image is ${image.width}x${image.height}" }
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
        val out = process.inputStream.bufferedReader().readText()
        check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed: $out" }
    }

    // ---- Q5: Swing over the video ---------------------------------------------------------------------

    fun swing(video: File, outDir: File) {
        outDir.mkdirs()
        open()
        load(video, 5.0)
        // A lightweight Swing label over the canvas, in a higher layer of the same window.
        onEdt {
            val label = JLabel("Swing label over the video", JLabel.CENTER).apply {
                isOpaque = true
                background = Color(255, 200, 0)
                font = Font("SansSerif", Font.BOLD, 28)
                setBounds(100, 100, 500, 80)
            }
            layered.add(label, JLayeredPane.PALETTE_LAYER)
            layered.repaint()
        }
        Thread.sleep(800)
        screen(File(outDir, "1-label-over-video.png"))
        // A popup menu over the video. Swing selects a light, medium, or heavy weight popup.
        lateinit var popup: JPopupMenu
        onEdt {
            popup = JPopupMenu().apply { repeat(5) { add(JMenuItem("Popup menu item ${it + 1}")) } }
            popup.show(canvas, 300, 250)
        }
        Thread.sleep(800)
        log("popup: window of the popup=${SwingUtilities.getWindowAncestor(popup)?.javaClass?.simpleName} heavyweight=${SwingUtilities.getWindowAncestor(popup) != frame}")
        screen(File(outDir, "2-popup-over-video.png"))
        onEdt { popup.isVisible = false }
        // The same with the light weight popup forced on.
        onEdt {
            popup.isLightWeightPopupEnabled = true
            ToolTipManager.sharedInstance().isLightWeightPopupEnabled = true
            popup.show(canvas, 300, 250)
        }
        Thread.sleep(800)
        log("popup 2: window=${SwingUtilities.getWindowAncestor(popup)?.javaClass?.simpleName}")
        screen(File(outDir, "3-popup-lightweight.png"))
        onEdt { popup.isVisible = false }
        // A dialog over the video.
        lateinit var dialog: JDialog
        onEdt {
            dialog = JDialog(frame, "Dialog", false).apply {
                contentPane.add(JLabel("A modeless dialog over the video", JLabel.CENTER))
                setSize(360, 160)
                setLocation(frame.x + 200, frame.y + 300)
                isVisible = true
            }
        }
        Thread.sleep(800)
        screen(File(outDir, "4-dialog.png"))
        onEdt { dialog.dispose() }
        // Play and resize the window: the layer must follow the canvas.
        mpv!!.setProperty("pause", "no")
        onEdt { frame.setSize(frame.width - 300, frame.height - 150) }
        Thread.sleep(800)
        screen(File(outDir, "5-resized.png"))
        log("after resize: canvas=${canvas.width}x${canvas.height} layer px=${MpvGlLayerNative.stats(layer).let { "${it[9]}x${it[10]}" }}")
        mpv!!.setProperty("pause", "yes")
        // Experiment: the video layer under the Java window layer, with a transparent hole in Swing.
        MpvGlLayerNative.putUnderWindowLayer(layer, canvas)
        Thread.sleep(800)
        screen(File(outDir, "6-under-window-layer.png"))
    }
}
