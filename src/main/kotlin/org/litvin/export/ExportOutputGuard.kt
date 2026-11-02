package org.litvin.export

import java.io.File
import java.nio.file.Files

/**
 * Finds an export output path that is the source video (B-38 T5, F-08). The final move of an export replaces the
 * output file, so an export to the source path deletes the source video.
 */
object ExportOutputGuard {
    /**
     * True when [output] and [source] are the same file. The check compares the canonical paths. On Windows the
     * comparison ignores the letter case. When both files exist, the check also asks the file system. This finds a
     * different letter case on a case-insensitive macOS volume, a symbolic link and a hard link.
     */
    fun isSourceVideo(output: File, source: File, windows: Boolean = isWindows()): Boolean {
        val outputPath = canonical(output).path
        val sourcePath = canonical(source).path
        if (outputPath.equals(sourcePath, ignoreCase = windows)) return true
        if (!output.exists() || !source.exists()) return false
        return try {
            Files.isSameFile(output.toPath(), source.toPath())
        } catch (_: Exception) {
            false
        }
    }

    private fun canonical(file: File): File =
        try {
            file.canonicalFile
        } catch (_: Exception) {
            file.absoluteFile.normalize()
        }

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}
