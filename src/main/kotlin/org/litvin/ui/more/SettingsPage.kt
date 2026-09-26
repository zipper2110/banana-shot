package org.litvin.ui.more

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.SectionPage
import java.io.File
import javax.swing.JTextArea

/**
 * The Settings section of the More window.
 * [onShowHintsAgain] makes all hints show again, and [onOpenFolder] opens a folder in the file manager.
 */
class SettingsPage(
    dataFolder: File,
    private val onShowHintsAgain: () -> Unit,
    private val onOpenFolder: (File) -> Boolean,
) : SectionPage(TITLE) {
    init {
        name = "more-settings"
        subheading("Hints and help")
        paragraph(
            "The app shows some hints only one time. " +
                "Show the hints again, and the Overview help at the next start of the app.",
        )
        lateinit var hintsStatus: JTextArea
        buttonRow(secondaryButton("Show all hints again") {
            onShowHintsAgain()
            hintsStatus.text = "The hints show again when you get to them."
        }.apply { name = "more-settings-show-hints" })
        hintsStatus = statusLine().apply { foreground = UiStyles.ACCENT_TEXT }

        subheading("App data")
        paragraph("The app keeps your projects, the export history, and the logs in this folder. The videos stay where they are.")
        valueRow("Folder", dataFolder.absolutePath)
        lateinit var folderStatus: JTextArea
        buttonRow(secondaryButton("Open folder") {
            folderStatus.text = if (onOpenFolder(dataFolder)) "" else "The app cannot open the folder."
        }.apply { name = "more-settings-open-data-folder" })
        folderStatus = statusLine()
    }

    companion object {
        const val TITLE = "Settings"
    }
}
