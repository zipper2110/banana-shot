package org.litvin.projects

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

    /** Returns a correct Windows folder name for the project name. For example, "Who won?" gives "Who won_". */
    fun folderName(name: String): String = SafeFileName.of(name.trim(), FALLBACK_FOLDER_NAME)

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
