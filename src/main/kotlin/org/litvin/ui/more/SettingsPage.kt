package org.litvin.ui.more

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ui.commons.ColorPickerDialog
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SectionPage
import org.litvin.ui.commons.SwatchButton
import org.litvin.ui.commons.ThemeSettings
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.ThemeSwitch
import java.awt.Dimension
import java.io.File
import javax.swing.JButton

/**
 * The Settings section of the More window.
 * [themeSettings] saves and applies the theme and the accent color that the user selects.
 * [onShowHintsAgain] makes all hints show again, and [onOpenFolder] opens a folder in the file manager.
 */
internal class SettingsPage(
    dataFolder: File,
    private val themeSettings: ThemeSettings,
    private val onShowHintsAgain: () -> Unit,
    private val onOpenFolder: (File) -> Boolean,
) : SectionPage(TITLE) {
    init {
        name = "more-settings"
        subheading("Theme")
        paragraph("Select the colors of the app.")
        addItem(ThemeSwitch("more-settings-theme", themeSettings))
        paragraph("The accent color is on the main buttons, the selection, and the marks.")
        lateinit var defaultAccent: JButton
        // The swatch paints the live accent token, so it always shows the current accent.
        val accentSwatch = SwatchButton(Palette.LIME_FILL, Dimension(SWATCH_WIDTH, UiButton.SMALL_HEIGHT)).apply {
            name = "more-settings-accent"
            toolTipText = "Choose the accent color"
            addActionListener {
                val before = Palette.accent
                val picked = ColorPickerDialog.pick(this, "Accent color", before, onLiveChange = themeSettings::previewAccent)
                if (picked != null) {
                    themeSettings.selectAccent(picked)
                    defaultAccent.isEnabled = true
                }
            }
        }
        defaultAccent = secondaryButton("Default", Material2MZ.RESTORE) {
            themeSettings.selectAccent(null)
            defaultAccent.isEnabled = false
        }.apply {
            name = "more-settings-accent-default"
            toolTipText = "Use the accent color of the theme"
            isEnabled = themeSettings.customAccent != null
        }
        themeSettings.onChange { defaultAccent.isEnabled = themeSettings.customAccent != null }
        buttonRow(accentSwatch, defaultAccent)

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
        private const val SWATCH_WIDTH = 40
    }
}
