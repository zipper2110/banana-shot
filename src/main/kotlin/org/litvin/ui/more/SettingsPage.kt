package org.litvin.ui.more

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SectionPage
import org.litvin.ui.commons.UiKit
import java.io.File

/**
 * The Settings section of the More window.
 * [onShowHintsAgain] makes all hints show again, and [onOpenFolder] opens a folder in the file manager.
 */
internal class SettingsPage(
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
        lateinit var hintsStatus: StatusLine
        buttonRow(secondaryButton("Show all hints again", Material2MZ.RESTORE) {
            onShowHintsAgain()
            hintsStatus.text = "The hints show again when you get to them."
        }.apply { name = "more-settings-show-hints" })
        hintsStatus = statusLine(Palette.SAGE)

        subheading("App data")
        paragraph("The app keeps your projects, the export history, and the logs in this folder. The videos stay where they are.")
        valueRow("Folder", dataFolder.absolutePath)
        lateinit var folderStatus: StatusLine
        buttonRow(secondaryButton("Open folder", Material2AL.FOLDER_OPEN) {
            folderStatus.text = if (onOpenFolder(dataFolder)) "" else "The app cannot open the folder."
        }.apply { name = "more-settings-open-data-folder" })
        folderStatus = statusLine()
    }

    companion object {
        const val TITLE = "Settings"
    }
}
