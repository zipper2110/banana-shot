package org.litvin.ui.more

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.ToolNav
import org.litvin.ui.commons.ToolNavItem
import org.litvin.ui.commons.ToolPage
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.WindowConstants
import org.litvin.i18n.tr

/**
 * One section of the More window. The list on the left shows [title]. The right side shows [content].
 * Code finds a section by its [id], not by its title, because the title is translated (B-11).
 */
data class MoreSection(val title: String, val content: JComponent, val id: String = title)

/** The window that the More button opens. It has the sections that are not a part of one tab. */
class MoreDialog(owner: Window?, sections: List<MoreSection>) : JDialog(owner, tr("more.title"), ModalityType.MODELESS) {
    private val morePanel = MorePanel(sections)

    init {
        defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        contentPane = morePanel
        minimumSize = Dimension(640, 440)
        size = Dimension(820, 560)
        setLocationRelativeTo(owner)
    }

    val selectedTitle: String
        get() = morePanel.selectedTitle

    /** Shows the window. If [sectionId] is not null, the window shows that section. */
    fun open(sectionId: String? = null) {
        if (sectionId != null) morePanel.selectSection(sectionId)
        if (!isVisible) isVisible = true
        toFront()
        requestFocus()
    }
}

/** The section list on the left and the selected section on the right: design/dialogs-redesign/more.html. */
class MorePanel(private val sections: List<MoreSection>) : JPanel(BorderLayout()) {
    private val navigation: ToolNav<String>
    private val cardLayout = CardLayout()
    private val content = JPanel(cardLayout)

    /** The ID of the selected section. */
    var selectedId: String = sections.first().id
        private set

    /** The title of the selected section. */
    val selectedTitle: String get() = sections.first { it.id == selectedId }.title

    init {
        require(sections.isNotEmpty()) { "The More window needs at least one section" }
        background = Palette.BG
        navigation = ToolNav("more-navigation", sections.map { ToolNavItem(it.id, it.title, iconOf(it.id)) }) { showSection(it) }

        content.name = "more-content"
        content.background = Palette.PANEL
        sections.forEach { section -> content.add(scrollOf(section.content), section.id) }

        add(navigation, BorderLayout.WEST)
        add(content, BorderLayout.CENTER)
        selectSection(sections.first().id)
    }

    fun sectionTitles(): List<String> = sections.map { it.title }

    /** Shows the section with [id]. An unknown ID has no effect. */
    fun selectSection(id: String) {
        if (sections.none { it.id == id }) return
        navigation.selected = id
        showSection(id)
    }

    private fun showSection(id: String) {
        selectedId = id
        cardLayout.show(content, id)
    }

    private fun scrollOf(component: JComponent): JScrollPane =
        (component as? ToolPage)?.inScrollPane() ?: JScrollPane(component).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            viewport.background = Palette.PANEL
            applyDarkScrollbar(this, Palette.PANEL)
        }

    companion object {
        /** The ID of the Privacy section. The `ui.privacy` package makes the page. */
        const val PRIVACY_ID = "privacy"

        private fun iconOf(id: String): Ikon = when (id) {
            SettingsPage.ID -> Material2MZ.SETTINGS
            PRIVACY_ID -> Material2MZ.SECURITY
            AboutPage.ID -> Material2AL.INFO
            ContactPage.ID -> Material2MZ.MAIL_OUTLINE
            else -> Material2AL.ARTICLE
        }
    }
}
