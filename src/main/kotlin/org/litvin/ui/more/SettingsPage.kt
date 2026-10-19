package org.litvin.ui.more

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.tr
import org.litvin.ui.commons.ColorPickerDialog
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SectionPage
import org.litvin.ui.commons.SwatchButton
import org.litvin.ui.commons.ThemeSettings
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.ThemeSwitch
import org.litvin.ui.commons.UiKit
import java.awt.Dimension
import java.io.File
import javax.swing.JButton
import javax.swing.JComboBox

/**
 * The Settings section of the More window.
 * [themeSettings] saves and applies the theme and the accent color that the user selects.
 * [onShowHintsAgain] makes all hints show again, and [onOpenFolder] opens a folder in the file manager.
 * [language] adds the Language card. It is the first card, so that a user who cannot read the current language
 * finds it (B-11, "Path back to English").
 */
internal class SettingsPage(
    dataFolder: File,
    private val themeSettings: ThemeSettings,
    private val onShowHintsAgain: () -> Unit,
    private val onOpenFolder: (File) -> Boolean,
    language: LanguageSetting? = null,
) : SectionPage(TITLE) {
    init {
        name = "more-settings"
        if (language != null) languageCard(language)

        subheading(tr("settings.theme"))
        paragraph(tr("settings.theme.text"))
        addItem(ThemeSwitch("more-settings-theme", themeSettings))
        paragraph(tr("settings.accent.text"))
        lateinit var defaultAccent: JButton
        // The swatch paints the live accent token, so it always shows the current accent.
        val accentSwatch = SwatchButton(Palette.LIME_FILL, Dimension(SWATCH_WIDTH, UiButton.SMALL_HEIGHT)).apply {
            name = "more-settings-accent"
            toolTipText = tr("settings.accent.tooltip")
            addActionListener {
                val before = Palette.accent
                val picked = ColorPickerDialog.pick(this, tr("settings.accent.pick"), before, onLiveChange = themeSettings::previewAccent)
                if (picked != null) {
                    themeSettings.selectAccent(picked)
                    defaultAccent.isEnabled = true
                }
            }
        }
        defaultAccent = secondaryButton(tr("settings.accent.default"), Material2MZ.RESTORE) {
            themeSettings.selectAccent(null)
            defaultAccent.isEnabled = false
        }.apply {
            name = "more-settings-accent-default"
            toolTipText = tr("settings.accent.default.tooltip")
            isEnabled = themeSettings.customAccent != null
        }
        themeSettings.onChange { defaultAccent.isEnabled = themeSettings.customAccent != null }
        buttonRow(accentSwatch, defaultAccent)

        subheading(tr("settings.hints"))
        paragraph(tr("settings.hints.text"))
        lateinit var hintsStatus: StatusLine
        buttonRow(secondaryButton(tr("settings.hints.showAgain"), Material2MZ.RESTORE) {
            onShowHintsAgain()
            hintsStatus.text = tr("settings.hints.shown")
        }.apply { name = "more-settings-show-hints" })
        hintsStatus = statusLine(Palette.SAGE)

        subheading(tr("settings.data"))
        paragraph(tr("settings.data.text"))
        valueRow(tr("settings.data.folder"), dataFolder.absolutePath)
        lateinit var folderStatus: StatusLine
        buttonRow(secondaryButton(tr("settings.data.openFolder"), Material2AL.FOLDER_OPEN) {
            folderStatus.text = if (onOpenFolder(dataFolder)) "" else tr("settings.data.openFailed")
        }.apply { name = "more-settings-open-data-folder" })
        folderStatus = statusLine()
    }

    /** One entry of the language list. The list shows each language in its own language. */
    private class LanguageOption(val language: AppLanguage?, private val label: String) {
        override fun toString() = label
    }

    private fun languageCard(setting: LanguageSetting) {
        // The heading has a globe icon. A translated heading also has the English word "Language".
        subheading(tr("settings.language")).apply {
            name = "more-settings-language-heading"
            icon = UiKit.icon(Material2AL.LANGUAGE, 16, Palette.FG_2)
            iconTextGap = 8
        }
        paragraph(tr("settings.language.text"))
        val options = listOf<AppLanguage?>(null) + AppLanguage.entries
        val combo = JComboBox(
            options.map { language ->
                LanguageOption(language, language?.nativeName ?: tr("settings.language.system", "language" to setting.systemLanguage.nativeName))
            }.toTypedArray(),
        ).apply {
            name = "more-settings-language"
            DialogKit.styleCombo(this)
            selectedIndex = options.indexOf(setting.choice).coerceAtLeast(0)
        }
        val restart = setting.restart
        val restartButton = secondaryButton(tr("settings.language.restart"), Material2MZ.REFRESH) { restart?.invoke() }.apply {
            name = "more-settings-language-restart"
            isVisible = false
        }
        buttonRow(combo, restartButton)
        val status = statusLine(Palette.SAGE).apply { name = "more-settings-language-status" }
        combo.addActionListener {
            val choice = (combo.selectedItem as LanguageOption).language
            if (!setting.save(choice)) {
                status.color = BAD
                status.text = tr("settings.language.saveFailed")
                restartButton.isVisible = false
                return@addActionListener
            }
            val changes = (choice ?: setting.systemLanguage) != setting.running
            status.color = Palette.SAGE
            status.text = when {
                !changes -> ""
                restart != null -> tr("settings.language.afterRestart")
                else -> tr("settings.language.closeAndStart")
            }
            restartButton.isVisible = changes && restart != null
            restartButton.parent?.revalidate()
        }
    }

    companion object {
        /** The ID of the section in the More window. */
        const val ID = "settings"
        val TITLE: String get() = tr("more.settings")
        private const val SWATCH_WIDTH = 40
    }
}
