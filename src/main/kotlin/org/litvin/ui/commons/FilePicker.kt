package org.litvin.ui.commons

import com.formdev.flatlaf.util.SystemFileChooser
import org.litvin.projects.NewProjectRules
import java.awt.Component
import java.io.File

interface FilePicker {
    fun chooseSourceVideo(
        parent: Component?,
        title: String,
        initialDirectory: File? = null,
        suggestedFile: File? = null,
    ): File?

    /**
     * Asks where to save an export. The extension of [suggestedFile] is the file type of the export.
     * When the user selects a file that exists, the picker asks to replace it, so the caller does not ask again.
     */
    fun chooseExportDestination(
        parent: Component?,
        title: String,
        initialDirectory: File? = null,
        suggestedFile: File? = null,
    ): File?
}

/**
 * The file dialog of the operating system: the Explorer dialog on Windows, the Finder panel on macOS, and the GTK dialog on Linux.
 * If the native dialog is not available, FlatLaf shows its Swing file chooser.
 */
class SystemFilePicker : FilePicker {
    override fun chooseSourceVideo(
        parent: Component?,
        title: String,
        initialDirectory: File?,
        suggestedFile: File?,
    ): File? {
        val chooser = chooser(title, initialDirectory, suggestedFile).apply {
            fileSelectionMode = SystemFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = SystemFileChooser.FileNameExtensionFilter("Video files", *NewProjectRules.VIDEO_EXTENSIONS.toTypedArray())
        }
        return if (chooser.showOpenDialog(parent) == SystemFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    override fun chooseExportDestination(
        parent: Component?,
        title: String,
        initialDirectory: File?,
        suggestedFile: File?,
    ): File? {
        val chooser = chooser(title, initialDirectory, suggestedFile)
        val extension = suggestedFile?.extension?.lowercase()?.takeIf { it.isNotBlank() }
        if (extension != null) {
            // The dialog adds the extension when the user types a name without it, and then asks to replace an existing file.
            chooser.fileFilter = SystemFileChooser.FileNameExtensionFilter("${extension.uppercase()} video", extension)
            chooser.putPlatformProperty(SystemFileChooser.WINDOWS_DEFAULT_EXTENSION, extension)
        }
        return if (chooser.showSaveDialog(parent) == SystemFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun chooser(title: String, initialDirectory: File?, suggestedFile: File?): SystemFileChooser =
        SystemFileChooser(initialDirectory?.takeIf { it.isDirectory }).apply {
            dialogTitle = title
            if (suggestedFile != null) selectedFile = suggestedFile
        }
}
