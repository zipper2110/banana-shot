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

/** One section of the More window. The list on the left shows [title]. The right side shows [content]. */
data class MoreSection(val title: String, val content: JComponent)

/** The window that the More button opens. It has the sections that are not a part of one tab. */
class MoreDialog(owner: Window?, sections: List<MoreSection>) : JDialog(owner, "More", ModalityType.MODELESS) {
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

    /** Shows the window. If [title] is not null, the window shows that section. */
    fun open(title: String? = null) {
        if (title != null) morePanel.selectSection(title)
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

    var selectedTitle: String = sections.first().title
        private set

    init {
        require(sections.isNotEmpty()) { "The More window needs at least one section" }
        background = Palette.BG
        navigation = ToolNav("more-navigation", sections.map { ToolNavItem(it.title, it.title, iconOf(it.title)) }) { showSection(it) }

        content.name = "more-content"
        content.background = Palette.PANEL
        sections.forEach { section -> content.add(scrollOf(section.content), section.title) }

        add(navigation, BorderLayout.WEST)
        add(content, BorderLayout.CENTER)
        selectSection(sections.first().title)
    }

    fun sectionTitles(): List<String> = sections.map { it.title }

    fun selectSection(title: String) {
        if (sections.none { it.title == title }) return
        navigation.selected = title
        showSection(title)
    }

    private fun showSection(title: String) {
        selectedTitle = title
        cardLayout.show(content, title)
    }

    private fun scrollOf(component: JComponent): JScrollPane =
        (component as? ToolPage)?.inScrollPane() ?: JScrollPane(component).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            viewport.background = Palette.PANEL
            applyDarkScrollbar(this, Palette.PANEL)
        }

    private companion object {
        fun iconOf(title: String): Ikon = when (title) {
            SettingsPage.TITLE -> Material2MZ.SETTINGS
            "Privacy" -> Material2MZ.SECURITY
            AboutPage.TITLE -> Material2AL.INFO
            ContactPage.TITLE -> Material2MZ.MAIL_OUTLINE
            else -> Material2AL.ARTICLE
        }
    }
}
