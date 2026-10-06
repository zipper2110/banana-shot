package org.litvin.projects

import org.litvin.shared.util.SafeFileName
import java.io.File

/** Rules for the name and the source video of a new project. */
object NewProjectRules {
    val VIDEO_EXTENSIONS = listOf("mp4", "mov", "mkv", "avi", "m4v", "wmv")
    const val MAX_NAME_LENGTH = 80
    const val DEFAULT_NAME = "Untitled Match"
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
            trimmed.isEmpty() -> "Type a project name."
            trimmed.length > MAX_NAME_LENGTH -> "Use a maximum of $MAX_NAME_LENGTH characters in the project name."
            else -> null
        }
    }

    /** Returns a correct Windows folder name for the project name. For example, "Who won?" gives "Who won_". */
    fun folderName(name: String): String = SafeFileName.of(name.trim(), FALLBACK_FOLDER_NAME)

    /** Returns an error message for a path that is not a supported video file, or null for a correct path. */
    fun sourceVideoError(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return "Select the match video."
        val file = File(trimmed)
        return when {
            !file.isFile -> "The video file does not exist."
            file.extension.lowercase() !in VIDEO_EXTENSIONS ->
                "Select a video file (${VIDEO_EXTENSIONS.joinToString(", ") { it.uppercase() }})."
            file.length() == 0L -> "The video file is empty."
            else -> null
        }
    }
}
