package org.litvin.autodetect

import org.litvin.JsonFileIO
import java.io.File
import java.time.LocalDateTime

/**
 * An automatic clip that the user declined in the review (deleted as a false rally, A60 step 4). The lab counts the
 * false rallies with these records. [kind] is the kind of the automatic mark, [winnerPercent] and [nearPlayer] come
 * from the mark. [rallyStart] and [rallyEnd] are the times of the detected rally in seconds, [resultFile] and
 * [importFile] name the run and the import record (null when the project has no detection data for the clip).
 */
data class DeclinedPoint(
    val pointId: String = "",
    val pointStartMs: Int = 0,
    val pointEndMs: Int = 0,
    val kind: String? = null,
    val winnerPercent: Int? = null,
    val nearPlayer: Int? = null,
    val rallyStart: Double? = null,
    val rallyEnd: Double? = null,
    val rallyProb: Double? = null,
    val resultFile: String? = null,
    val importFile: String? = null,
    val declined: String = "",
)

data class DeclinedPointsFile(val version: Int = 1, val points: List<DeclinedPoint> = emptyList())

/** The declined automatic clips of a project, in `autodetect/declined.json`. */
object DetectDeclined {
    private const val FILE = "declined.json"

    private fun file(projectDir: File) = File(File(projectDir, DetectImport.FOLDER), FILE)

    fun read(projectDir: File): List<DeclinedPoint> {
        val file = file(projectDir)
        if (!file.isFile) return emptyList()
        return runCatching { DetectJson.mapper.readValue(file, DeclinedPointsFile::class.java).points }.getOrDefault(emptyList())
    }

    /** Adds [point] to the record, with the time now. A record of the same clip is replaced. */
    fun add(projectDir: File, point: DeclinedPoint) {
        val stamped = point.copy(declined = LocalDateTime.now().withNano(0).toString())
        write(projectDir, read(projectDir).filter { it.pointId != point.pointId } + stamped)
    }

    /** Removes the record of the clip with [pointId] (Undo of the decline). */
    fun remove(projectDir: File, pointId: String) {
        val points = read(projectDir)
        if (points.any { it.pointId == pointId }) write(projectDir, points.filter { it.pointId != pointId })
    }

    private fun write(projectDir: File, points: List<DeclinedPoint>) {
        val file = file(projectDir).apply { parentFile.mkdirs() }
        JsonFileIO.writeAtomically(DetectJson.mapper, file.absolutePath, DeclinedPointsFile(points = points))
    }
}
