package org.litvin.autodetect

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.json.JsonReadFeature
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
 * - [trimOnly]: the import skips the rallies outside the trim of the video.
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
    val trimOnly: Boolean = true,
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
 * - [p1Near]: true when player 1 of the detection is at the near end in this rally. Null when the run had no ball stage.
 * - [view]: a picture of the rally that shows the two players, for the player question. Null when the pose stage
 *   found no such picture.
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
    val p1Near: Boolean? = null,
    val view: RallyView? = null,
    /** The probability that the player at the near end wins: from the ball only, and after the score model. */
    val pBall: Double? = null,
    val pNearWins: Double? = null,
    /** True when the server is at the near end. Null when the detection does not know the server. */
    val serverNear: Boolean? = null,
    /** True when the detection saw other shirt colors at the near end than in the rally before. */
    val endChangeBefore: Boolean? = null,
    val lastShot: LastShot? = null,
    /** The ball into the net that ends the rally, when the detection saw one. The score model does not use it. */
    val netHit: NetHit? = null,
    /** The shots of the rally and the winner evidence from them (shot_chain.py). The score model does not use it. */
    val chain: ShotChain? = null,
)

/**
 * The shot chain of a rally. [case] names the rule that decides, [nearWins] is its decision (null: no decision),
 * [p] is the measured share of right decisions of that rule.
 */
data class ShotChain(
    val case: String? = null,
    val nearWins: Boolean? = null,
    val p: Double? = null,
    val shots: List<ChainShot> = emptyList(),
)

/**
 * One shot: [t] is the start in s, [near] is true when the player at the near end hit it. [how] says how the hitter
 * is known: "first", "hit" (the hit was seen), "back" (the ball came back), "gap" (the return was not seen), "net".
 * [end] is "in", "out", "own" (a bounce on the side of the hitter), "net" or "none" (not seen). [court] is the
 * place of the bounce in m, [margin] its distance to the lines in m (negative: out).
 */
data class ChainShot(
    val t: Double = 0.0,
    val near: Boolean? = null,
    val how: String? = null,
    val end: String? = null,
    val kmh: Double? = null,
    val court: List<Double>? = null,
    val margin: Double? = null,
)

/**
 * The last shot of a rally, the input of the ball rule for the winner.
 * - [nearWins]: what the ball rule says. Null: no decision.
 * - [case]: the case of the rule: "net", "in", "out", with "_early" (the shot is more than 4 s before the end of the
 *   rally) or "_seen" (the bounce was seen).
 * - [t]: the video time of the start of the shot, in seconds. [tRel] is that time minus the end of the rally.
 * - [toFar]: true when the ball goes to the far end, so the player at the near end hit it.
 * - [speed]: km/h. [src]: 0 no bounce, 1 the bounce comes from the flight, 2 the bounce was seen.
 * - [margin]: the distance of the bounce to the lines in m. More than 0 is inside the court.
 * - [netZ]: the height of the ball over the net line, in m.
 */
/**
 * A ball into the net that ends the rally: [t] is the time of the touch in s, [hitterNear] is true when the player
 * at the near end hit the ball, [conf] is the confidence of the touch and [dirConf] that of the hitter (0 to 1).
 * [serve] is true when the ball was a serve.
 */
data class NetHit(
    val t: Double = 0.0,
    val hitterNear: Boolean = false,
    val conf: Double? = null,
    val dirConf: Double? = null,
    val serve: Boolean = false,
)

data class LastShot(
    val nearWins: Boolean? = null,
    val case: String? = null,
    val t: Double? = null,
    val tRel: Double? = null,
    val toFar: Boolean = false,
    val speed: Double? = null,
    val src: Int = 0,
    val margin: Double? = null,
    val netZ: Double? = null,
)

/**
 * A picture of a rally with the two players. [second] is the second of the video (the work files have one picture for
 * each second). [near] and [far] are the boxes of the player at the near end and at the far end: x1, y1, x2, y2 in px
 * of that picture.
 */
data class RallyView(val second: Int = 0, val near: List<Double> = emptyList(), val far: List<Double> = emptyList())

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

/** The court lines of a part of the video. Each line is x1, y1, x2, y2 in px of the overlay frame. */
data class OverlayCourt(val t0: Double = 0.0, val t1: Double = 0.0, val lines: List<List<Double>> = emptyList(), val net: List<Double> = emptyList())

/** A fitted ball flight: its time range, its landing point in the image, and the speed at its start. */
data class OverlayFlight(
    val t0: Double = 0.0,
    val t1: Double = 0.0,
    val landT: Double? = null,
    val land: List<Double>? = null,
    val speedKmh: Double = 0.0,
    val good: Boolean = false,
    /** True when the ball goes to the far end. */
    val toFar: Boolean? = null,
    /** The start point of the flight on the court: x across, y along (0 at the net), z up, in m. */
    val start: List<Double>? = null,
    /** The landing point on the court: x, y in m. */
    val landM: List<Double>? = null,
    /** The height of the ball over the net line, in m. */
    val netZ: Double? = null,
    /**
     * The path of the fit in the image: rows of time (s), x, y (px). It goes from the start of the flight to its
     * bounce or its landing. The part after [curveObsEnd] (the time of the last detection) is a prediction.
     */
    val curve: List<List<Double>>? = null,
    val curveObsEnd: Double? = null,
)

/**
 * An event of the ball: "bounce" (the ball touches the court), "hit" (a player hits it), "net" (the ball stops at the
 * net), "cord" (the ball touches the net and goes over), "end" (the track ends). [px] is the place in the image,
 * [court] the place on the court in m (x across, y along, 0 at the net). [conf] is 0 to 1. [src] names the evidence.
 */
data class OverlayEvent(
    val t: Double = 0.0,
    val kind: String = "",
    val px: List<Double>? = null,
    val court: List<Double>? = null,
    val conf: Double? = null,
    val src: String? = null,
    val toFar: Boolean? = null,
)

data class OverlayServe(val t: Double = 0.0, val near: Boolean = false, val kind: String? = null, val speedKmh: Double = 0.0)

/**
 * The data that the app draws on the preview (`overlay.json` of the lab script). The image points are px of a
 * [width] x [height] frame, the source frame of the video with no rotation and no crop. [ball] has one row for each
 * frame with a detection: time in seconds, x, y. The rows are in time order. [poses] has one row for each person on
 * each picture of the pose stage (one picture each second): time in seconds, then x, y of the 17 COCO body points.
 * A point that the pose model did not see is -1, -1. The rows are in time order.
 */
data class DetectOverlayData(
    val version: Int = 1,
    val width: Int = 1920,
    val height: Int = 1080,
    val court: List<OverlayCourt> = emptyList(),
    val ball: List<List<Double>> = emptyList(),
    val flights: List<OverlayFlight> = emptyList(),
    val serves: List<OverlayServe> = emptyList(),
    val poses: List<List<Double>> = emptyList(),
    val events: List<OverlayEvent> = emptyList(),
) {
    companion object {
        fun read(file: File): DetectOverlayData = DetectJson.mapper.readValue(file, DetectOverlayData::class.java)
    }
}

internal object DetectJson {
    val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        // Python writes NaN for a value that it does not have. The readers ignore such a value.
        .configure(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature(), true)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)
}
