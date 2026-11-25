package org.litvin.autodetect

import org.litvin.JsonFileIO
import org.litvin.points.AutoKind
import org.litvin.points.AutoMarkV1
import org.litvin.points.EdlIO
import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetailV1
import org.litvin.scoring.PointDetails
import org.litvin.scoring.ScoreIO
import org.litvin.shared.util.Timecode
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Why the import did not add a detected rally. */
enum class SkipReason {
    /** A clip of the project is already at this time. */
    OVERLAP,

    /** The clip is shorter than the minimum length of a clip. */
    TOO_SHORT,

    /** The rally is outside the trim of the video. */
    OUTSIDE_TRIM,
}

/**
 * What the import did with the rally [rally] (an index into [DetectResult.rallies]): the id of its clip, the id of
 * the fault clip before it, or why it was skipped. The lab compares these ids with the project at a later time to
 * find the corrections of the user.
 */
data class ImportRow(
    val rally: Int,
    val pointId: String? = null,
    val faultPointId: String? = null,
    val skipped: SkipReason? = null,
)

/** The clips that an import adds, with their winners and details. */
data class ImportPlan(
    val points: List<PointV1>,
    val outcomes: Map<String, Outcome>,
    val details: Map<String, PointDetailV1>,
    val rows: List<ImportRow>,
) {
    /** The count of the clips that give a point or a rally (not the fault clips). */
    val rallyCount: Int get() = rows.count { it.pointId != null }
    val faultCount: Int get() = rows.count { it.faultPointId != null }
    val skippedCount: Int get() = rows.count { it.skipped != null }
}

/** The record of an import, in the `autodetect` folder of the project. */
data class ImportRecord(
    val created: String,
    val resultFile: String,
    val settings: DetectSettings,
    val rows: List<ImportRow>,
    /** True when player 1 of the detection is player 2 of the project. */
    val swapped: Boolean = false,
    /** The trim that limited the import, in ms, or null. */
    val rangeStartMs: Long? = null,
    val rangeEndMs: Long? = null,
    /**
     * The ids of the clips that were in the project before the import (G11), or null in an old record. The lab
     * (corrections.py) does not count them as clips that the user added for a missed point.
     */
    val existingPointIds: List<String>? = null,
)

/**
 * The detected rally of a clip of the project. [swapped] is true when player 1 of the detection is player 2 of the
 * project. [resultFile] and [importFile] are the names of the result and of the import record of the clip (G11).
 */
data class PointDetection(
    val rally: DetectedRally,
    val swapped: Boolean,
    val resultFile: String? = null,
    val importFile: String? = null,
)

/** Changes the result of a detection run into clips of the project. */
object DetectImport {
    /** The folder of the project that keeps the result files and the import records. */
    const val FOLDER = "autodetect"

    /** The shortest clip that the Points tab accepts. */
    private const val MIN_CLIP_MS = 200

    /**
     * The problem that stops the import of [result] into the project with the video [video], or null (G14): a result
     * of a newer lab script, or of another video. A result with [DetectResult.videoId] must have the identity of
     * [video]; an old result with no identity must have the file name of [video].
     */
    fun check(result: DetectResult, video: File?): String? {
        if (result.version > DetectResult.CONTRACT_VERSION) {
            return "The result file has version ${result.version}. This app reads version ${DetectResult.CONTRACT_VERSION} or lower: update the app."
        }
        if (video == null) return null
        val other = when (val id = result.videoId) {
            null -> result.video.isNotBlank() && File(result.video).name != video.name
            else -> runCatching { VideoIdentity.of(video) }.getOrNull()?.let { it != id } ?: false
        }
        return if (other) "The result is for another video (${File(result.video).name}). Start a new detection for this video." else null
    }

    /** The kind of the detection data of a fault clip ([faultDetection]). */
    const val FAULT_KIND = "fault"

    /** A fault clip starts this long before the serve and ends this long after it. */
    private const val FAULT_BEFORE_MS = 1_000
    private const val FAULT_AFTER_MS = 2_500

    /**
     * The clips for [result]. [existing] are the clips of the project: a rally at the time of one of them is skipped,
     * so a second import adds nothing twice. Fault clips come only for [advanced] scoring mode. Two detected rallies
     * never overlap: the padding of [settings] stops at the middle of the gap between them.
     * With [swapped], player 1 of the detection is player 2 of the project. A rally that is not fully inside
     * [rangeMs] (the trim of the video) is skipped.
     */
    fun plan(
        result: DetectResult,
        existing: List<PointV1>,
        advanced: Boolean,
        settings: DetectSettings,
        newId: () -> String = EdlIO::generateId,
        swapped: Boolean = false,
        rangeMs: LongRange? = null,
    ): ImportPlan {
        val s = settings.normalized()
        val videoEndMs = if (result.videoSeconds > 0) result.videoSeconds * 1000L else Long.MAX_VALUE
        val order = result.rallies.withIndex().sortedBy { it.value.start }
        val rawStart = order.map { (it.value.start * 1000).roundToLong() }
        val rawEnd = order.map { (it.value.end * 1000).roundToLong() }
        val points = ArrayList<PointV1>()
        val outcomes = LinkedHashMap<String, Outcome>()
        val details = LinkedHashMap<String, PointDetailV1>()
        val rows = ArrayList<ImportRow>()
        var lastEnd = 0

        fun free(start: Int, end: Int) = existing.none { start < it.endMs && it.startMs < end }

        order.forEachIndexed { k, (index, rally) ->
            // The limits of this rally: the middle of the gap to its neighbors.
            val lower = if (k == 0) 0L else maxOf(0L, (minOf(rawEnd[k - 1], rawStart[k]) + rawStart[k]) / 2)
            val upper = if (k == order.lastIndex) videoEndMs else maxOf(rawEnd[k], (rawEnd[k] + maxOf(rawEnd[k], rawStart[k + 1])) / 2)
            val start = Timecode.roundTo10ms((rawStart[k] - s.padBeforeMs).coerceIn(lower, maxOf(lower, rawEnd[k])))
            val end = Timecode.roundTo10ms((rawEnd[k] + s.padAfterMs).coerceIn(rawStart[k], minOf(upper, videoEndMs)))
            val from = maxOf(start, lastEnd)
            when {
                rangeMs != null && (rawStart[k] < rangeMs.first || rawEnd[k] > rangeMs.last) ->
                    rows += ImportRow(index, skipped = SkipReason.OUTSIDE_TRIM)
                end - from < MIN_CLIP_MS -> rows += ImportRow(index, skipped = SkipReason.TOO_SHORT)
                !free(from, end) -> rows += ImportRow(index, skipped = SkipReason.OVERLAP)
                else -> {
                    val faultId = rally.fault?.takeIf { s.addFaults && advanced }?.let { fault ->
                        val t = (fault.t * 1000).roundToLong()
                        val fs = maxOf(Timecode.roundTo10ms(maxOf(0L, t - FAULT_BEFORE_MS)), lastEnd)
                        val fe = minOf(Timecode.roundTo10ms(t + FAULT_AFTER_MS), from)
                        if (fe - fs < MIN_CLIP_MS || !free(fs, fe)) return@let null
                        val id = newId()
                        points += PointV1(id = id, startMs = fs, endMs = fe, auto = AutoMarkV1(AutoKind.FAULT, nearPlayer = nearPlayer(rally, swapped)))
                        outcomes[id] = Outcome.NONE
                        details[id] = PointDetailV1(ending = Ending.SERVE_FAULT)
                        id
                    }
                    val percent = rally.confidence?.let { (it * 100).roundToInt().coerceIn(50, 100) }
                    val winner = when (rally.winner.takeIf { s.addWinners && percent != null && percent >= s.minWinnerPercent }) {
                        "P1" -> if (swapped) Outcome.P2 else Outcome.P1
                        "P2" -> if (swapped) Outcome.P1 else Outcome.P2
                        else -> Outcome.NONE
                    }
                    val id = newId()
                    val kind = if (rally.kind == "short") AutoKind.SHORT else AutoKind.RALLY
                    points += PointV1(
                        id = id, startMs = from, endMs = end,
                        auto = AutoMarkV1(kind, percent.takeIf { winner != Outcome.NONE }, nearPlayer = nearPlayer(rally, swapped)),
                    )
                    outcomes[id] = winner
                    rows += ImportRow(index, pointId = id, faultPointId = faultId)
                    lastEnd = end
                }
            }
        }
        return ImportPlan(points, outcomes, details, rows.sortedBy { it.rally })
    }

    /**
     * Adds the clips of [resultFile] to the project in [projectDir], and writes the record of the import. The caller
     * must write its pending changes of the project before this call, and read the project again after it.
     */
    fun apply(
        projectDir: File,
        resultFile: File,
        settings: DetectSettings,
        swapped: Boolean = false,
        rangeMs: LongRange? = null,
    ): ImportPlan {
        val result = DetectResult.read(resultFile)
        val dir = projectDir.absolutePath
        val edl = EdlIO.readForProjectDir(dir)
        val score = ScoreIO.readForProjectDir(dir)
        val plan = plan(result, edl.points, score.isAdvanced, settings, swapped = swapped, rangeMs = rangeMs)
        if (plan.points.isEmpty()) return plan
        val points = (edl.points + plan.points).sortedWith(compareBy({ it.startMs }, { it.endMs }))
        EdlIO.writeForProjectDir(dir, edl.copy(points = points))
        val updated = score.copy(outcomes = score.outcomes + plan.outcomes, details = score.details + plan.details)
        ScoreIO.writeForProjectDir(dir, PointDetails.resolve(points, updated))
        val stamp = LocalDateTime.now().format(STAMP)
        val record = ImportRecord(
            stamp, resultFile.name, settings.normalized(), plan.rows, swapped, rangeMs?.first, rangeMs?.last,
            existingPointIds = edl.points.map { it.id },
        )
        val folder = File(projectDir, FOLDER).apply { mkdirs() }
        JsonFileIO.writeAtomically(DetectJson.mapper, File(folder, "import-$stamp.json").absolutePath, record)
        return plan
    }

    /**
     * The rally for the player question: the first rally inside [rangeMs] for which the detection knows the end of
     * its player 1 and has a picture with the two players. With no such picture in the result, it is the first rally
     * for which the detection knows the end. Null when the result has no winners, so the import needs no question.
     */
    fun questionRally(result: DetectResult, rangeMs: LongRange?): DetectedRally? {
        val known = result.rallies.sortedBy { it.start }.filter { rally ->
            rally.p1Near != null && rally.kind == "rally" && (
                rangeMs == null ||
                    ((rally.start * 1000).roundToLong() >= rangeMs.first && (rally.end * 1000).roundToLong() <= rangeMs.last)
                )
        }
        return known.firstOrNull { it.view?.near?.size == 4 } ?: known.firstOrNull()
    }

    /**
     * True when player 1 of the detection is player 2 of the project. [nearPlayer] (1 or 2) is the player of the
     * project at the near end in [rally].
     */
    fun isSwapped(rally: DetectedRally, nearPlayer: Int): Boolean = (rally.p1Near == true) != (nearPlayer == 1)

    /** The player of the project (1 or 2) at the near end in [rally], or null when the detection does not know it. */
    fun nearPlayer(rally: DetectedRally, swapped: Boolean): Int? = rally.p1Near?.let { p1Near -> if (p1Near != swapped) 1 else 2 }

    /**
     * The detection data of the fault clip before [rally]: the server and the ends of the rally, and the shots of the
     * fault candidate. Its kind is [FAULT_KIND]. It has no winner. Null when the rally has no fault candidate.
     */
    fun faultDetection(rally: DetectedRally): DetectedRally? = rally.fault?.let { fault ->
        DetectedRally(
            start = fault.t - FAULT_BEFORE_MS / 1000.0, end = fault.t + FAULT_AFTER_MS / 1000.0, kind = FAULT_KIND,
            server = rally.server, serverNear = rally.serverNear, serveSpeedKmh = fault.speed, p1Near = rally.p1Near,
            chain = fault.chain,
        )
    }

    /**
     * The player at the near end for each clip of the imports of the project, from the import records and their
     * result files. It is for the clips of an import before the mark had this value. It does not know a later
     * "Swap players".
     */
    fun nearPlayersOfRecords(projectDir: File): Map<String, Int> {
        val folder = File(projectDir, FOLDER)
        val records = folder.listFiles { f -> f.isFile && f.name.startsWith("import-") && f.name.endsWith(".json") } ?: return emptyMap()
        val near = HashMap<String, Int>()
        records.sortedBy { it.name }.forEach { file ->
            runCatching {
                val record = DetectJson.mapper.readValue(file, ImportRecord::class.java)
                val rallies = DetectResult.read(File(folder, record.resultFile)).rallies
                record.rows.forEach { row ->
                    val id = row.pointId ?: return@forEach
                    rallies.getOrNull(row.rally)?.let { nearPlayer(it, record.swapped) }?.let { player ->
                        near[id] = player
                        row.faultPointId?.let { near[it] = player }    // the fault clip has the ends of its rally
                    }
                }
            }
        }
        return near
    }

    /** The detected rally for each clip of the imports of the project, from the import records and their result files. */
    fun detectionsOfRecords(projectDir: File): Map<String, PointDetection> {
        val folder = File(projectDir, FOLDER)
        val records = folder.listFiles { f -> f.isFile && f.name.startsWith("import-") && f.name.endsWith(".json") } ?: return emptyMap()
        val found = HashMap<String, PointDetection>()
        records.sortedBy { it.name }.forEach { file ->
            runCatching {
                val record = DetectJson.mapper.readValue(file, ImportRecord::class.java)
                val rallies = DetectResult.read(File(folder, record.resultFile)).rallies
                record.rows.forEach { row ->
                    val id = row.pointId ?: return@forEach
                    val rally = rallies.getOrNull(row.rally) ?: return@forEach
                    found[id] = PointDetection(rally, record.swapped, record.resultFile, file.name)
                    row.faultPointId?.let { faultId ->
                        faultDetection(rally)?.let { found[faultId] = PointDetection(it, record.swapped, record.resultFile, file.name) }
                    }
                }
            }
        }
        return found
    }

    /**
     * The warnings of the result of the newest import of the project, for example "no court found". Empty when the
     * project has no import or the files do not open.
     */
    fun latestImportWarnings(projectDir: File): List<String> {
        val folder = File(projectDir, FOLDER)
        val newest = folder.listFiles { f -> f.isFile && f.name.startsWith("import-") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.name } ?: return emptyList()
        return runCatching {
            val record = DetectJson.mapper.readValue(newest, ImportRecord::class.java)
            DetectResult.read(File(folder, record.resultFile)).warnings
        }.getOrDefault(emptyList())
    }

    /**
     * The overlay data of the result of the newest import of the project (G12: the overlay belongs to its result), or
     * of the newest result when the project has no import. Null when that run wrote no overlay: an overlay of another
     * run does not match the clips.
     */
    fun overlayOfResult(projectDir: File): File? {
        val folder = File(projectDir, FOLDER)
        val record = folder.listFiles { f -> f.isFile && f.name.startsWith("import-") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.name }
            ?.let { runCatching { DetectJson.mapper.readValue(it, ImportRecord::class.java) }.getOrNull() }
        val result = record?.resultFile ?: latestResult(projectDir)?.name ?: return null
        return overlayOf(File(folder, result))
    }

    /** The overlay file that the run of [resultFile] wrote (the same time stamp), or null. */
    fun overlayOf(resultFile: File): File? =
        File(resultFile.parentFile, resultFile.name.replaceFirst("result-", "overlay-")).takeIf { it.isFile && it != resultFile }

    /** The newest result file of the project, or null when the project has none. */
    fun latestResult(projectDir: File): File? =
        File(projectDir, FOLDER).listFiles { f -> f.isFile && f.name.startsWith("result-") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.name }

    internal val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", java.util.Locale.ROOT)
}
