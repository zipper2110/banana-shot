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

/** What the detection does now. */
sealed interface DetectState {
    /** No run, and no result of this session. */
    data object Idle : DetectState

    /**
     * A run for the project in [projectDir]. [stage] is null before the first stage. [progress] is the part of the
     * stage that is done (0 to 1), or null when the stage does not tell it. [startedAtMs] is the wall clock time of
     * the start.
     */
    data class Running(
        val projectDir: File,
        val stage: DetectStage? = null,
        val progress: Double? = null,
        val startedAtMs: Long = System.currentTimeMillis(),
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

    fun saveSettings(settings: DetectSettings) {
        runCatching { DetectSettings.save(appDataRoot, settings) }.onFailure { logger.warn(it) { "Detection settings not saved" } }
    }

    /**
     * Starts a run for the video [video] of the project in [projectDir]. [workName] names the work folder of the
     * project in the work root of the lab: a second run for the same project continues from the files of the first.
     * Returns false when a run is active.
     */
    @Synchronized
    fun start(projectDir: File, workName: String, video: File, settings: DetectSettings): Boolean {
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
        publish(DetectState.Running(projectDir))
        thread(name = "autodetect-run", isDaemon = true) { run(builder, projectDir, work, logFile) }
        return true
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
        try {
            val p = builder.start()
            process = p
            logFile.bufferedWriter().use { log ->
                log.appendLine("# ${LocalDateTime.now()} ${builder.command().joinToString(" ")}")
                p.inputStream.bufferedReader().forEachLine { line ->
                    log.appendLine(line)
                    if (!line.startsWith("@")) return@forEachLine
                    log.flush()
                    val parts = line.trim().split(" ")
                    when (parts[0]) {
                        "@stage" -> if (parts.getOrNull(2) == "start") advance(DetectStage.of(parts[1]), null)
                        "@progress" -> advance(DetectStage.of(parts[1]), parts.getOrNull(2)?.toDoubleOrNull())
                        "@result" -> result = File(line.removePrefix("@result").trim())
                        "@error" -> error = line.removePrefix("@error").trim()
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
            file.copyTo(File(folder, "result-${LocalDateTime.now().format(DetectImport.STAMP)}.json"), overwrite = true)
        }
        copy.fold(
            onSuccess = { publish(DetectState.Finished(projectDir, it)) },
            onFailure = { publish(DetectState.Failed(projectDir, it.message, logFile)) },
        )
    }

    private fun advance(stage: DetectStage?, progress: Double?) {
        val running = state as? DetectState.Running ?: return
        publish(running.copy(stage = stage ?: running.stage, progress = progress?.coerceIn(0.0, 1.0)))
    }

    private fun publish(next: DetectState) {
        state = next
        listeners.forEach { listener -> runCatching { listener(next) }.onFailure { logger.warn(it) { "Detection listener failed" } } }
    }
}
