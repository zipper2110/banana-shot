package org.litvin.autodetect

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.JsonFileIO
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** The stages of the lab script, in run order. The script names them in its "@stage" lines. */
enum class DetectStage(val code: String) {
    MEDIA("media"),
    AUDIO("audio"),
    IMAGE("image"),
    COURT("court"),
    BALL("ball"),
    FLIGHTS("flights"),
    POSE("pose"),
    RALLIES("rallies"),
    WINNERS("winners"),
    ;

    companion object {
        fun of(code: String): DetectStage? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One line of the lab script for the app: a line that starts with "@" (see `detect.py`). [kind] is the first word
 * ("@stage", "@progress", "@result", ...), [args] are the next words, and [text] is the rest of the line after [kind].
 */
internal data class ScriptLine(val kind: String, val args: List<String>, val text: String) {
    companion object {
        /** The line for the app, or null for another line (log text of the script). */
        fun parse(line: String): ScriptLine? {
            if (!line.startsWith("@")) return null
            val parts = line.trim().split(" ")
            return ScriptLine(parts[0], parts.drop(1), line.trim().removePrefix(parts[0]).trim())
        }
    }
}

/** What the detection does now. */
sealed interface DetectState {
    /** No run, and no result of this session. */
    data object Idle : DetectState

    /**
     * A run for the project in [projectDir]. [stage] is null before the first stage. [progress] is the part of the
     * stage that is done (0 to 1), or null when the stage does not tell it. [startedAtMs] is the wall clock time of
     * the start, and [stageStartedAtMs] is that of the start of [stage]. [videoSeconds] is the length of the video
     * when the caller knows it, and [ballPass] is the setting of the run: [DetectProgress] uses them for the time
     * estimate.
     */
    data class Running(
        val projectDir: File,
        val stage: DetectStage? = null,
        val progress: Double? = null,
        val startedAtMs: Long = System.currentTimeMillis(),
        val stageStartedAtMs: Long = startedAtMs,
        val videoSeconds: Double? = null,
        val ballPass: Boolean = true,
    ) : DetectState

    /** The run ended. [resultFile] is the copy of the result in the project folder. */
    data class Finished(val projectDir: File, val resultFile: File) : DetectState

    /** The run failed or the user stopped it. [message] is the error line of the script, or null for a stop. */
    data class Failed(val projectDir: File, val message: String?, val logFile: File?) : DetectState
}

/**
 * Runs the lab script for one project at a time. The run continues when the user leaves the Points tab. The listeners
 * get each new state on the thread of the run, not on the EDT.
 */
private const val OVERLAY_LAYERS_FILE = "autodetect-overlay-layers.txt"

class DetectRunner(val lab: DetectLab, private val appDataRoot: File) {
    private val logger = KotlinLogging.logger {}
    private val listeners = CopyOnWriteArrayList<(DetectState) -> Unit>()

    @Volatile
    var state: DetectState = DetectState.Idle
        private set

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stopped = false

    /** Adds a listener. The result removes it. */
    fun listen(listener: (DetectState) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    val isRunning: Boolean get() = state is DetectState.Running

    /** The settings that the user used last. */
    fun loadSettings(): DetectSettings = DetectSettings.load(appDataRoot)

    /** The names of the overlay parts that the user had on in the review window. */
    fun loadOverlayLayers(): Set<String> = runCatching {
        File(appDataRoot, OVERLAY_LAYERS_FILE).takeIf { it.isFile }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
    }.getOrNull() ?: emptySet()

    fun saveOverlayLayers(layers: Set<String>) {
        runCatching { File(appDataRoot, OVERLAY_LAYERS_FILE).writeText(layers.joinToString(System.lineSeparator())) }
            .onFailure { logger.warn(it) { "Overlay switches not saved" } }
    }

    fun saveSettings(settings: DetectSettings) {
        runCatching { DetectSettings.save(appDataRoot, settings) }.onFailure { logger.warn(it) { "Detection settings not saved" } }
    }

    /**
     * Starts a run for the video [video] of the project in [projectDir]. [workName] names the work folder of the
     * project in the work root of the lab: a second run for the same project continues from the files of the first.
     * Returns false when a run is active. [videoSeconds] is the length of the video, for the time estimate.
     */
    @Synchronized
    fun start(projectDir: File, workName: String, video: File, settings: DetectSettings, videoSeconds: Double? = null): Boolean {
        if (isRunning) return false
        stopped = false
        val work = File(lab.workRoot, workName).apply { mkdirs() }
        val settingsFile = File(work, "settings.json")
        JsonFileIO.writeAtomically(
            DetectJson.mapper,
            settingsFile.absolutePath,
            mapOf("ballPass" to settings.ballPass, "threshold" to settings.threshold, "shortPoints" to settings.shortPoints),
        )
        val logFile = File(work, "run.log")
        val command = listOf(
            lab.python, "-u", lab.script, "--video", video.absolutePath, "--work", work.absolutePath,
            "--settings", settingsFile.absolutePath,
        )
        val builder = ProcessBuilder(command).directory(File(lab.script).parentFile).redirectErrorStream(true)
        builder.environment().putAll(lab.environment)
        publish(DetectState.Running(projectDir, videoSeconds = videoSeconds, ballPass = settings.ballPass))
        thread(name = "autodetect-run", isDaemon = true) { run(builder, projectDir, work, logFile) }
        return true
    }

    /**
     * The picture number [picture] (file `kf/<picture+1>.jpg`) of the work folder of [result], or null when it is not
     * there. A result with no work folder (an old result) uses the old work name: the name of [projectDir]. A picture
     * number is not a second of the video (see [RallyView]).
     */
    fun frameFile(result: DetectResult, projectDir: File, picture: Int): File? {
        val work = result.work?.takeIf { it.isNotBlank() }?.let(::File) ?: File(lab.workRoot, projectDir.name)
        return File(work, "kf/%05d.jpg".format(java.util.Locale.ROOT, picture + 1)).takeIf { it.isFile }
    }

    companion object {
        /**
         * The name of the work folder for [video] of the project in [projectDir]: the project folder name and the
         * short identity of the video (G12). Another video, or another project with the same folder name, gets
         * another work folder, so no run reads the work files of another video.
         */
        fun workName(projectDir: File, video: File): String = "${projectDir.name}-${VideoIdentity.short(video)}"
    }

    /** Stops the active run. The work files stay, so the next run continues after the last complete stage. */
    fun stop() {
        stopped = true
        process?.let { p ->
            p.descendants().forEach { it.destroyForcibly() }
            p.destroyForcibly()
        }
    }

    /** Forgets a finished or failed run. */
    @Synchronized
    fun reset() {
        if (!isRunning) publish(DetectState.Idle)
    }

    private fun run(builder: ProcessBuilder, projectDir: File, work: File, logFile: File) {
        var error: String? = null
        var result: File? = null
        var overlay: File? = null
        try {
            val p = builder.start()
            process = p
            logFile.bufferedWriter().use { log ->
                log.appendLine("# ${LocalDateTime.now()} ${builder.command().joinToString(" ")}")
                p.inputStream.bufferedReader().forEachLine { line ->
                    log.appendLine(line)
                    val script = ScriptLine.parse(line) ?: return@forEachLine
                    log.flush()
                    val args = script.args
                    when (script.kind) {
                        "@stage" -> if (args.getOrNull(1) == "start") advance(args.getOrNull(0)?.let(DetectStage::of), null)
                        "@progress" -> advance(args.getOrNull(0)?.let(DetectStage::of), args.getOrNull(1)?.toDoubleOrNull())
                        "@result" -> result = File(script.text)
                        "@overlay" -> overlay = File(script.text)
                        "@error" -> error = script.text
                    }
                }
            }
            val exit = p.waitFor()
            if (exit != 0 && error == null && !stopped) error = "exit code $exit"
        } catch (t: Exception) {
            logger.warn(t) { "Detection run failed" }
            if (!stopped) error = t.message ?: t.javaClass.simpleName
        } finally {
            process = null
        }
        val file = result?.takeIf { it.isFile && error == null && !stopped }
        if (file == null) {
            publish(DetectState.Failed(projectDir, error.takeIf { !stopped }, logFile.takeIf { it.isFile }))
            return
        }
        val copy = runCatching {
            val folder = File(projectDir, DetectImport.FOLDER).apply { mkdirs() }
            val stamp = LocalDateTime.now().format(DetectImport.STAMP)
            // The overlay data is a debug view: a failed copy must not fail the run.
            overlay?.takeIf { it.isFile }?.let { source ->
                runCatching { source.copyTo(File(folder, "overlay-$stamp.json"), overwrite = true) }
                    .onFailure { logger.warn(it) { "Overlay data not copied" } }
            }
            file.copyTo(File(folder, "result-$stamp.json"), overwrite = true)
        }
        copy.fold(
            onSuccess = { publish(DetectState.Finished(projectDir, it)) },
            onFailure = { publish(DetectState.Failed(projectDir, it.message, logFile)) },
        )
    }

    private fun advance(stage: DetectStage?, progress: Double?) {
        val running = state as? DetectState.Running ?: return
        val next = stage ?: running.stage
        publish(
            running.copy(
                stage = next,
                progress = progress?.coerceIn(0.0, 1.0),
                stageStartedAtMs = if (next != running.stage) System.currentTimeMillis() else running.stageStartedAtMs,
            ),
        )
    }

    private fun publish(next: DetectState) {
        state = next
        listeners.forEach { listener -> runCatching { listener(next) }.onFailure { logger.warn(it) { "Detection listener failed" } } }
    }
}
