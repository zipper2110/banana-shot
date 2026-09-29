package org.litvin.ui.tabs.projects.components

import org.litvin.AppInfo
import org.litvin.ui.commons.AnimatedAppMark
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.ceil

/** The brand at the top of the start panel: the app mark, the app name and the tagline. */
class ProjectsHeader : JPanel() {
    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        val titleText = Box.createVerticalBox().apply {
            add(BrandText(AppInfo.NAME.uppercase(), ProjectsUi.trackedFont(20f, 0.06, ProjectsUi.Weight.BOLD), 24, true))
            add(BrandText(AppInfo.TAGLINE.uppercase(), ProjectsUi.trackedFont(11f, 0.12), 15, false))
            alignmentY = Component.CENTER_ALIGNMENT
        }
        add(AnimatedAppMark(LOGO_SIZE).apply {
            name = "projects-app-logo"
            alignmentY = Component.CENTER_ALIGNMENT
        })
        add(Box.createHorizontalStrut(12))
        add(titleText)
        add(Box.createHorizontalGlue())
        alignmentX = Component.LEFT_ALIGNMENT
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    private companion object {
        const val LOGO_SIZE = 44
    }

    /** One line of the brand. It paints its own text, so the text never gets "...". */
    private class BrandText(
        private val text: String,
        private val textFont: Font,
        private val lineHeight: Int,
        private val primary: Boolean,
    ) : JComponent() {
        init {
            alignmentX = Component.LEFT_ALIGNMENT
        }

        override fun getPreferredSize() = Dimension(ceil(ProjectsUi.textWidth(text, textFont)).toInt() + 4, lineHeight)
        override fun getMinimumSize() = preferredSize
        override fun getMaximumSize() = preferredSize

        override fun paintComponent(g: Graphics) {
            val g2 = ProjectsUi.smooth(g)
            try {
                ProjectsUi.drawText(
                    g2, text, textFont, if (primary) ProjectsUi.FG else ProjectsUi.FG_2, 0f, 0f, height.toFloat(),
                )
            } finally {
                g2.dispose()
            }
        }
    }
}
