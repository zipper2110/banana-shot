package org.litvin.ui.more

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder

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

class MorePanel(private val sections: List<MoreSection>) : JPanel(BorderLayout()) {
    private val navigation = JList(sections.toTypedArray())
    private val cardLayout = CardLayout()
    private val content = JPanel(cardLayout)

    var selectedTitle: String = sections.first().title
        private set

    init {
        require(sections.isNotEmpty()) { "The More window needs at least one section" }
        background = UiStyles.DARK_BG
        border = EmptyBorder(12, 12, 12, 12)

        navigation.name = "more-navigation"
        navigation.selectionMode = ListSelectionModel.SINGLE_SELECTION
        navigation.background = UiStyles.CARD_BG
        navigation.foreground = UiStyles.FG_PRIMARY
        navigation.fixedCellHeight = 42
        navigation.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? MoreSection)?.title.orEmpty()
                border = EmptyBorder(0, 12, 0, 12)
                background = if (isSelected) UiStyles.SURFACE_HIGH else UiStyles.CARD_BG
                foreground = if (isSelected) UiStyles.LIME else UiStyles.FG_PRIMARY
                return label
            }
        }
        navigation.addListSelectionListener {
            if (!it.valueIsAdjusting) navigation.selectedValue?.let(::showSection)
        }

        val navigationScroll = JScrollPane(navigation).apply {
            border = BorderFactory.createLineBorder(UiStyles.CARD_BORDER)
            preferredSize = Dimension(180, 0)
            applyDarkScrollbar(this, UiStyles.CARD_BG)
        }

        content.name = "more-content"
        content.background = UiStyles.CARD_BG
        sections.forEach { section ->
            val scroll = JScrollPane(section.content).apply {
                border = BorderFactory.createLineBorder(UiStyles.CARD_BORDER)
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                viewport.background = UiStyles.CARD_BG
                applyDarkScrollbar(this, UiStyles.CARD_BG)
            }
            content.add(scroll, section.title)
        }

        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationScroll, content).apply {
            dividerLocation = 180
            dividerSize = 8
            isContinuousLayout = true
            resizeWeight = 0.0
            border = BorderFactory.createEmptyBorder()
            background = UiStyles.DARK_BG
        }, BorderLayout.CENTER)

        navigation.selectedIndex = 0
    }

    fun sectionTitles(): List<String> = sections.map { it.title }

    fun selectSection(title: String) {
        val section = sections.firstOrNull { it.title == title } ?: return
        navigation.setSelectedValue(section, true)
    }

    private fun showSection(section: MoreSection) {
        selectedTitle = section.title
        cardLayout.show(content, section.title)
    }
}
