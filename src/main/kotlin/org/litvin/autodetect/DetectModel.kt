package org.litvin.autodetect

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.litvin.JsonFileIO
import java.io.File

/**
 * The automatic point detection (docs/auto-analysis/app-integration.md, iteration 1). The detection is the lab
 * script `tools/auto-analysis/detect.py`. The app starts it as an external process and reads its result file.
 */

/**
 * Where the lab is on this PC. The file `autodetect.json` in the app data folder gives it. The app shows the detection
 * only when this file is there and is correct.
 * - [python]: the Python of the lab venv.
 * - [script]: the path of `detect.py`.
 * - [workRoot]: the folder for the work files of the runs. They are large (the keyframes of a match are some GB).
 * - [environment]: more environment variables for the process, for example `HF_HOME`.
 */
data class DetectLab(
    val python: String = "",
    val script: String = "",
    val workRoot: String = "",
    val environment: Map<String, String> = emptyMap(),
) {
    companion object {
        const val FILE_NAME = "autodetect.json"

        /** The lab of [appDataRoot], or null when the file is not there, does not parse, or names a missing file. */
        fun load(appDataRoot: File): DetectLab? {
            val file = File(appDataRoot, FILE_NAME)
            if (!file.isFile) return null
            val lab = runCatching { DetectJson.mapper.readValue(file, DetectLab::class.java) }.getOrNull() ?: return null
            return lab.takeIf { File(it.python).isFile && File(it.script).isFile && it.workRoot.isNotBlank() }
        }
    }
}

/**
 * The settings of a detection run and of the import of its result.
 * - [ballPass]: false skips the ball stage, the slow one. Then the result has only the boundaries of the rallies.
 * - [threshold]: the rally probability that starts a rally. A lower value gives more rallies.
 * - [shortPoints]: adds short points (an ace, a double fault) from the serve events of the ball.
 * - [padBeforeMs], [padAfterMs]: the time that the import adds before the start and after the end of a detected
 *   rally. The rally model learned the boundaries from marked projects, so 0 gives clips like those marks.
 * - [addWinners]: the import gives a point the detected winner.
 * - [minWinnerPercent]: a winner with a lower confidence is not used. The point then has no winner.
 * - [addFaults]: the import adds a fault clip for a first-serve fault candidate (only in advanced scoring mode).
 */
data class DetectSettings(
    val ballPass: Boolean = true,
    val threshold: Double = 0.5,
    val shortPoints: Boolean = true,
    val padBeforeMs: Int = 0,
    val padAfterMs: Int = 0,
    val addWinners: Boolean = true,
    val minWinnerPercent: Int = 50,
    val addFaults: Boolean = true,
) {
    /** The values inside their limits. */
    fun normalized(): DetectSettings = copy(
        threshold = threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD),
        padBeforeMs = padBeforeMs.coerceIn(-MAX_PAD_MS, MAX_PAD_MS),
        padAfterMs = padAfterMs.coerceIn(-MAX_PAD_MS, MAX_PAD_MS),
        minWinnerPercent = minWinnerPercent.coerceIn(50, 100),
    )

    companion object {
        const val FILE_NAME = "autodetect-settings.json"
        const val MIN_THRESHOLD = 0.2
        const val MAX_THRESHOLD = 0.9
        const val MAX_PAD_MS = 5_000

        /** The settings that the user used last, or the defaults. */
        fun load(appDataRoot: File): DetectSettings {
            val file = File(appDataRoot, FILE_NAME)
            if (!file.isFile) return DetectSettings()
            return runCatching { DetectJson.mapper.readValue(file, DetectSettings::class.java).normalized() }
                .getOrDefault(DetectSettings())
        }

        fun save(appDataRoot: File, settings: DetectSettings) {
            JsonFileIO.writeAtomically(DetectJson.mapper, File(appDataRoot, FILE_NAME).absolutePath, settings.normalized())
        }
    }
}

/** A first-serve fault candidate before a rally: the time of the serve in the video, in seconds. */
data class DetectedFault(val t: Double = 0.0, val kind: String? = null, val speed: Double? = null)

/**
 * One detected rally of `result.json`. The times are seconds of the video.
 * - [kind]: "rally" from the rally model, or "short" for a short point from a serve event.
 * - [winner], [server]: "P1", "P2", or null. Player 1 is the player at the near end in the first rally.
 * - [confidence]: the probability of [winner], 0.5 to 1.
 */
data class DetectedRally(
    val start: Double = 0.0,
    val end: Double = 0.0,
    val kind: String = "rally",
    val prob: Double? = null,
    val pNotPoint: Double? = null,
    val winner: String? = null,
    val confidence: Double? = null,
    val server: String? = null,
    val serveSpeedKmh: Double? = null,
    val fault: DetectedFault? = null,
    val shots: Int? = null,
)

/** The result file of a run. [stageSeconds] has the run time of each stage. */
data class DetectResult(
    val version: Int = 1,
    val video: String = "",
    val videoSeconds: Int = 0,
    val rallyModel: String? = null,
    val settings: Map<String, Any?> = emptyMap(),
    val stageSeconds: Map<String, Double> = emptyMap(),
    val created: String? = null,
    /** What the run could not do, for example "the court model is not plausible". English texts of the lab script. */
    val warnings: List<String> = emptyList(),
    val rallies: List<DetectedRally> = emptyList(),
) {
    companion object {
        fun read(file: File): DetectResult = DetectJson.mapper.readValue(file, DetectResult::class.java)
    }
}

internal object DetectJson {
    val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)
}
