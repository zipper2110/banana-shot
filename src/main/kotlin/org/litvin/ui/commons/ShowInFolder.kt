package org.litvin.ui.commons

import java.awt.Desktop
import java.io.File

/**
 * Opens the folder of a file in the file manager of the system. When the file exists, the file manager also selects
 * it: Finder with `open -R` on macOS (B-13), Explorer with `/select,` on Windows. On other systems, and when the file
 * does not exist, it opens only the folder.
 */
class ShowInFolder(
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val start: (List<String>) -> Unit = { ProcessBuilder(it).start() },
    private val openFolder: (File) -> Unit = { Desktop.getDesktop().open(it) },
) {
    /** Throws the cause when the folder does not open. */
    fun show(file: File) {
        val target = file.absoluteFile
        val folder = target.parentFile ?: return
        when {
            isMac() && target.isFile -> start(listOf("open", "-R", target.path))
            isWindows() && target.isFile -> start(listOf("explorer.exe", "/select,", target.path))
            else -> try {
                openFolder(folder)
            } catch (failure: Exception) {
                // Desktop.open can fail when the system has no default file manager action.
                when {
                    isMac() -> start(listOf("open", folder.path))
                    isWindows() -> start(listOf("explorer.exe", folder.path))
                    else -> throw failure
                }
            }
        }
    }

    private fun isMac(): Boolean = osName.startsWith("Mac", ignoreCase = true)

    private fun isWindows(): Boolean = osName.startsWith("Windows", ignoreCase = true)
}
