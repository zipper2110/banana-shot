package org.litvin.autodetect

import org.litvin.JsonFileIO
import java.io.File
import java.time.LocalDateTime

/**
 * A note of the user about the detection of one clip: what the detection did not do correctly. The lab reads the
 * notes to improve the detection. [rallyStart] and [rallyEnd] are the times of the detected rally in seconds, so the
 * lab finds the rally after the user moves the clip. [resultFile] and [importFile] name the result (the run) and the
 * import record of the clip (G11), or are null for a clip that no import made.
 */
data class DetectNote(
    val pointId: String = "",
    val text: String = "",
    val pointStartMs: Int = 0,
    val pointEndMs: Int = 0,
    val rallyStart: Double? = null,
    val rallyEnd: Double? = null,
    val updated: String = "",
    val resultFile: String? = null,
    val importFile: String? = null,
)

data class DetectNotesFile(val version: Int = 1, val notes: List<DetectNote> = emptyList())

/** The notes of a project, in `autodetect/review-notes.json`. */
object DetectNotes {
    private const val FILE = "review-notes.json"

    private fun file(projectDir: File) = File(File(projectDir, DetectImport.FOLDER), FILE)

    fun read(projectDir: File): Map<String, DetectNote> {
        val file = file(projectDir)
        if (!file.isFile) return emptyMap()
        return runCatching { DetectJson.mapper.readValue(file, DetectNotesFile::class.java).notes.associateBy { it.pointId } }
            .getOrDefault(emptyMap())
    }

    /** Saves [note]. A note with an empty text removes the note of the clip. */
    fun save(projectDir: File, note: DetectNote) {
        val notes = LinkedHashMap(read(projectDir))
        if (note.text.isBlank()) notes.remove(note.pointId) else notes[note.pointId] = note.copy(updated = LocalDateTime.now().withNano(0).toString())
        val file = file(projectDir)
        JsonFileIO.writeAtomically(DetectJson.mapper, file.absolutePath, DetectNotesFile(notes = notes.values.toList()))
    }
}
