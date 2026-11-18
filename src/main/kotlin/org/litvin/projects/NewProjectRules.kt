package org.litvin.projects

import org.litvin.shared.util.DrawnText
import org.litvin.shared.util.SafeFileName
import java.io.File
import org.litvin.i18n.tr

/** Rules for the name and the source video of a new project. */
object NewProjectRules {
    val VIDEO_EXTENSIONS = listOf("mp4", "mov", "mkv", "avi", "m4v", "wmv")
    const val MAX_NAME_LENGTH = 80
    val DEFAULT_NAME: String get() = tr("projects.defaultName")
    private const val FALLBACK_FOLDER_NAME = "Project"

    /** Returns the file name of the video without the extension. */
    fun suggestedName(sourceVideoPath: String): String =
        File(sourceVideoPath.trim()).name.substringBeforeLast('.').trim().ifBlank { DEFAULT_NAME }

    /**
     * Returns an error message for an incorrect project name, or null for a correct name. The name can have characters
     * that Windows does not permit in a file name, because the project folder has a different name ([folderName]).
     */
    fun nameError(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> tr("projects.name.error.empty")
            trimmed.length > MAX_NAME_LENGTH -> tr("projects.name.error.long", "max" to MAX_NAME_LENGTH)
            else -> null
        }
    }

    /**
     * The project name that the app saves: without the spaces at the start and the end, and in the composed form
     * (NFC). Thus "é" as one character and "e" with U+0301 give the same name (B-11 E3-T23).
     */
    fun cleanName(name: String): String = DrawnText.composed(name.trim())

    /** Returns a correct Windows folder name for the project name. For example, "Who won?" gives "Who won_". */
    fun folderName(name: String): String = SafeFileName.of(name.trim(), FALLBACK_FOLDER_NAME)

    /**
     * The video files of one match in play order (B-49): sorted by file name, with the numbers in the names compared
     * as numbers. Thus "GH2.MP4" comes before "GH10.MP4". A file that is two times in the list stays only one time.
     */
    fun sortedVideoPaths(paths: List<String>): List<String> =
        paths.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(::pathKey)
            .sortedWith { a, b -> compareNatural(File(a).name, File(b).name) }

    /**
     * The list after "Add videos" (B-49): [current] stays in its order, and the [selected] files come at the end,
     * sorted by file name. A selected file that is already in the list is not added again.
     */
    fun appendVideoPaths(current: List<String>, selected: List<String>): List<String> {
        val known = current.map(::pathKey).toMutableSet()
        return current + sortedVideoPaths(selected).filter { known.add(pathKey(it)) }
    }

    /** Two paths of the same file give the same key. Windows file names do not use the letter case. */
    private fun pathKey(path: String): String = File(path.trim()).absolutePath.lowercase()

    /** Compares [a] and [b] without the letter case. A sequence of digits compares as a number. */
    internal fun compareNatural(a: String, b: String): Int {
        val tokens = Regex("""\d+|\D+""")
        val left = tokens.findAll(a.lowercase()).map { it.value }.toList()
        val right = tokens.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]
            val y = right[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 }
                    ?: x.trimStart('0').compareTo(y.trimStart('0')).takeIf { it != 0 }
                    ?: x.length.compareTo(y.length)
            } else {
                x.compareTo(y)
            }
            if (result != 0) return result
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
    }

    /** Returns an error message for a path that is not a supported video file, or null for a correct path. */
    fun sourceVideoError(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return tr("projects.video.error.empty")
        val file = File(trimmed)
        return when {
            !file.isFile -> tr("projects.video.error.missing")
            file.extension.lowercase() !in VIDEO_EXTENSIONS ->
                tr("projects.video.error.type", "types" to VIDEO_EXTENSIONS.joinToString(", ") { it.uppercase() })
            file.length() == 0L -> tr("projects.video.error.emptyFile")
            else -> null
        }
    }
}
