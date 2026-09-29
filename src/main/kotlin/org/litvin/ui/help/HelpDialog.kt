package org.litvin.ui.help

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.AppInfo
import org.litvin.ui.commons.KeyChip
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.LeadRow
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.ToolCard
import org.litvin.ui.commons.ToolNav
import org.litvin.ui.commons.ToolNavItem
import org.litvin.ui.commons.ToolPage
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Insets
import java.awt.Window
import java.awt.geom.Ellipse2D
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * The Help window. It does not block the app. The layout comes from design/dialogs-redesign/help.html:
 * the page list with the icons and the groups of the sidebar on the left, and the page on the right.
 */
class HelpDialog(owner: Window?) : JDialog(owner, "${AppInfo.NAME} Help", ModalityType.MODELESS) {
    private val helpPanel = HelpPanel()

    init {
        defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        contentPane = helpPanel
        minimumSize = Dimension(720, 520)
        size = Dimension(900, 650)
        setLocationRelativeTo(owner)
    }

    val selectedPage: HelpPage
        get() = helpPanel.selectedPage

    /** Shows [page]. [currentTab] is the page of the tab that is open in the app; the list marks it with "This tab". */
    fun open(page: HelpPage, currentTab: HelpPage? = page) {
        helpPanel.currentTab = currentTab?.takeIf { it != HelpPage.OVERVIEW }
        helpPanel.selectPage(page)
        if (!isVisible) isVisible = true
        toFront()
        requestFocus()
    }
}

class HelpPanel : JPanel(BorderLayout()) {
    private val navigation = ToolNav(
        "help-navigation",
        HelpPage.entries.map { page -> ToolNavItem(page, page.title, iconOf(page), captionOf(page)) },
        markerText = "This tab",
    ) { renderPage(it) }
    private val page = ToolPage().apply { name = "help-content" }
    private val scroll = page.inScrollPane()

    var selectedPage: HelpPage = HelpPage.OVERVIEW
        private set

    /** The page of the tab that is open in the app. The list marks it with "This tab". */
    var currentTab: HelpPage?
        get() = navigation.marked
        set(value) {
            navigation.marked = value
        }

    init {
        background = UiKit.BG
        add(navigation, BorderLayout.WEST)
        add(scroll, BorderLayout.CENTER)
        selectPage(HelpPage.OVERVIEW)
    }

    fun selectPage(page: HelpPage) {
        navigation.selected = page
        renderPage(page)
    }

    fun pageTitles(): List<String> = HelpPage.entries.map { it.title }

    private fun renderPage(help: HelpPage) {
        selectedPage = help
        val content = HelpCatalog.content(help)
        val column = page.column
        column.removeAll()
        column.add(ToolPage.title(help.title), gapAfter = 6)
        column.add(WrapText(content.summary, UiKit.font(15f), UiKit.FG_2, 1.55f, ToolPage.MAX_WIDTH), gapAfter = 20)

        section(column, "Main workflow", Material2MZ.TIMELINE, content.workflow.mapIndexed { index, step ->
            LeadRow(StepNumber(index + 1), bodyText(step), 26, 10, Insets(6, 0, 6, 0))
        })
        section(column, "Available actions", Material2MZ.TOUCH_APP, content.actions.map { action ->
            LeadRow(bulletIcon(Material2AL.CHEVRON_RIGHT, UiKit.FG_3), bodyText(action), 18, 8, Insets(5, 0, 5, 0))
        })
        section(column, "Good to know", Material2AL.LIGHTBULB, content.goodToKnow.map { fact ->
            LeadRow(bulletIcon(Material2AL.INFO, UiKit.SAGE), bodyText(fact), 18, 8, Insets(5, 0, 5, 0))
        })
        if (content.shortcuts.isNotEmpty()) {
            section(column, "Keyboard shortcuts", Material2AL.KEYBOARD, listOf(shortcutTable(content.shortcuts)))
        }

        page.revalidate()
        page.repaint()
        SwingUtilities.invokeLater { scroll.verticalScrollBar.value = 0 }
    }

    /** A caption and its rows. An empty section does not show. */
    private fun section(column: Stack, caption: String, ikon: Ikon, rows: List<JComponent>) {
        if (rows.isEmpty()) return
        column.add(ToolPage.caption(caption, ikon), gapAfter = 8)
        rows.forEachIndexed { index, row -> column.add(row, gapAfter = if (index == rows.lastIndex) 22 else 2) }
    }

    private fun bodyText(text: String) = WrapText(text, UiKit.font(14f), UiKit.FG, 1.5f, ToolPage.MAX_WIDTH - 40)

    private fun bulletIcon(ikon: Ikon, color: Color): JComponent = JLabel(UiKit.icon(ikon, 16, color)).apply {
        preferredSize = Dimension(18, 21)
        verticalAlignment = JLabel.CENTER
    }

    /** The shortcuts in a card: the keys on the left, the action on the right. */
    private fun shortcutTable(shortcuts: List<HelpShortcut>): JComponent = ToolCard(Insets(1, 1, 1, 1), 0).apply {
        shortcuts.forEachIndexed { index, shortcut ->
            val keys = JPanel(FlowLayout(FlowLayout.LEFT, 3, 0)).apply {
                isOpaque = false
                shortcut.shortcut.display.split('+').forEach { add(KeyChip(it, KEY_STYLE)) }
                preferredSize = Dimension(150, KEY_STYLE.height)
            }
            val row = LeadRow(keys, bodyText(shortcut.action), 150, 12, Insets(8, 12, 8, 12))
            add(if (index == 0) row else RowLine(row))
        }
    }

    /** A row with a line at the top, between two rows of the shortcut table. */
    private class RowLine(private val row: LeadRow) : JPanel(BorderLayout()), HeightForWidth {
        init {
            isOpaque = false
            add(row, BorderLayout.CENTER)
        }

        override fun heightForWidth(width: Int) = row.heightForWidth(width)

        override fun paintComponent(g: Graphics) {
            g.color = UiKit.LINE
            g.fillRect(0, 0, width, 1)
        }
    }

    /** The number of a workflow step in a lime circle. */
    private class StepNumber(private val number: Int) : JComponent() {
        override fun getPreferredSize() = Dimension(22, 22)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                g2.color = UiKit.LIME_TINT
                g2.fill(Ellipse2D.Double(0.0, 0.0, 22.0, 22.0))
                g2.color = UiKit.LIME_LINE
                g2.draw(Ellipse2D.Double(0.5, 0.5, 21.0, 21.0))
                val font = UiKit.font(11.5f, UiKit.Weight.BOLD)
                val text = number.toString()
                UiKit.drawText(g2, text, font, UiKit.LIME, (22 - UiKit.textWidth(text, font)) / 2f, 0f, 22f)
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        val KEY_STYLE = KeyChipStyle(fill = UiKit.RAISED_2, border = Color(0x3A3A3A), text = Color(0xFFD54A), height = 24, fontSize = 12.5f)

        /** The icons of the sidebar buttons, so the user finds the same tab in the list. */
        fun iconOf(page: HelpPage): Ikon = when (page) {
            HelpPage.OVERVIEW -> Material2AL.HOME
            HelpPage.PROJECTS -> Material2MZ.SOURCE
            HelpPage.POINTS -> Material2MZ.SPORTS_TENNIS
            HelpPage.SCORING -> Material2AL.ASSIGNMENT_TURNED_IN
            HelpPage.STATISTICS -> Material2AL.BAR_CHART
            HelpPage.COLORS -> Material2AL.COLOR_LENS
            HelpPage.CROP -> Material2AL.CROP_ROTATE
            HelpPage.EXPORT -> Feather.FILM
        }

        /** The group captions of the sidebar. */
        fun captionOf(page: HelpPage): String? = when (page) {
            HelpPage.POINTS -> "Match"
            HelpPage.COLORS -> "Video"
            HelpPage.EXPORT -> " "
            else -> null
        }
    }
}
