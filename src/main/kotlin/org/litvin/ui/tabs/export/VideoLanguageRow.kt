package org.litvin.ui.tabs.export

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.tr
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import java.awt.BorderLayout
import java.awt.GridBagLayout
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * The "Video language" row of the Export tab (B-11 E5-T2). The list has the languages of the app, and each language
 * shows in its own language. With only one language, the row does not show.
 *
 * [onSelect] gets the language that the user selects. A change with [language] does not call it.
 */
internal class VideoLanguageRow(
    private val onSelect: (AppLanguage) -> Unit,
    languages: List<AppLanguage> = AppLanguage.entries,
) : JPanel(BorderLayout(12, 0)) {

    /** One entry of the list. */
    private class Option(val language: AppLanguage) {
        override fun toString() = language.nativeName
    }

    private val options = languages.map(::Option)
    private var updating = false

    private val combo = JComboBox(options.toTypedArray()).apply {
        name = "export-video-language"
        toolTipText = tr("export.videoLanguage.tooltip")
        DialogKit.styleCombo(this)
    }

    /** The language that the list shows. */
    var language: AppLanguage
        get() = (combo.selectedItem as Option).language
        set(value) {
            val option = options.firstOrNull { it.language == value } ?: return
            updating = true
            try {
                combo.selectedItem = option
            } finally {
                updating = false
            }
        }

    init {
        isOpaque = false
        isVisible = languages.size > 1
        // WrapText measures the text as it paints it, so the label is never cut. The panel centers it vertically.
        val label = WrapText(tr("export.videoLanguage"), ExportUi.font(13f), Palette.FG_2).apply {
            name = "export-video-language-label"
            toolTipText = tr("export.videoLanguage.tooltip")
        }
        add(JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(label)
        }, BorderLayout.WEST)
        add(combo, BorderLayout.CENTER)
        combo.addActionListener { if (!updating) onSelect(language) }
    }
}
