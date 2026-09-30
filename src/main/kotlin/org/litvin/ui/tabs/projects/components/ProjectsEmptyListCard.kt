package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import org.litvin.ui.commons.Palette

/**
 * The list area on the first start: an icon, "No projects yet", the [message], and a lime hint that points
 * to the "Import new match" button in the start panel.
 */
class ProjectsEmptyListCard(private val message: String) : JComponent() {
    private val icon = ProjectsUi.icon(Material2OutlinedMZ.VIDEO_LIBRARY, 40, Palette.FG_3)
    private val arrow = ProjectsUi.icon(Material2MZ.WEST, 18, Palette.LIME)
    private val titleFont = ProjectsUi.font(16f, ProjectsUi.Weight.BOLD)
    private val textFont = ProjectsUi.font(13f)
    private val hintFont = ProjectsUi.font(12.5f)

    init {
        name = "projects-empty-list"
    }

    override fun getPreferredSize() = Dimension(360, CONTENT_HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            var y = (height - CONTENT_HEIGHT) / 2
            icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, y)
            y += icon.iconHeight + 10
            centered(g2, TITLE, titleFont, Palette.FG, y, 22)
            y += 22 + 4
            centered(g2, message, textFont, Palette.FG_2, y, 18)
            y += 18 + 14
            val hintWidth = arrow.iconWidth + 6 + ProjectsUi.textWidth(HINT, hintFont)
            val x = (width - hintWidth) / 2f
            arrow.paintIcon(this, g2, x.toInt(), y + (20 - arrow.iconHeight) / 2)
            ProjectsUi.drawText(g2, HINT, hintFont, Palette.LIME, x + arrow.iconWidth + 6, y.toFloat(), 20f)
        } finally {
            g2.dispose()
        }
    }

    private fun centered(g2: Graphics2D, text: String, font: Font, color: Color, top: Int, lineHeight: Int) {
        val x = (width - ProjectsUi.textWidth(text, font)) / 2f
        ProjectsUi.drawText(g2, text, font, color, x, top.toFloat(), lineHeight.toFloat())
    }

    private companion object {
        const val TITLE = "No projects yet"
        const val HINT = "\"Import new match\" is in the New project panel"
        const val CONTENT_HEIGHT = 40 + 10 + 22 + 4 + 18 + 14 + 20
    }
}
