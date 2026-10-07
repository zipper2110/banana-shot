package org.litvin.ui.commons

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JButton
import javax.swing.JPanel

/**
 * The Dark / Mid / Light control. Each option is a small picture of the app in the colors of its theme: a sidebar,
 * two text lines and an accent button. A ring in the accent color marks the selected theme. The theme name is the
 * tooltip. The control selects the theme in [settings], and it follows a selection in a different control.
 * Each option is a button with the name "<componentName>-<index>".
 */
internal class ThemeSwitch(componentName: String, private val settings: ThemeSettings) : JPanel(null) {
    var selected: AppTheme = settings.theme
        private set(value) {
            field = value
            repaint()
        }

    private val options = AppTheme.entries.mapIndexed { index, theme -> Option(theme).apply { name = "$componentName-$index" } }

    init {
        name = componentName
        isOpaque = false
        options.forEach { add(it) }
        settings.onChange { selected = settings.theme }
    }

    override fun getPreferredSize() = Dimension(OPTION_WIDTH * options.size + GAP * (options.size - 1), OPTION_HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun doLayout() {
        val top = (height - OPTION_HEIGHT) / 2
        options.forEachIndexed { index, option -> option.setBounds(index * (OPTION_WIDTH + GAP), top, OPTION_WIDTH, OPTION_HEIGHT) }
    }

    private inner class Option(private val theme: AppTheme) : JButton() {
        private val colors = Palette.previewColors(theme.background)

        init {
            toolTipText = "${theme.title} theme"
            getAccessibleContext().accessibleName = toolTipText
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            isRolloverEnabled = true
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                if (selected == theme) return@addActionListener
                selected = theme
                settings.selectTheme(theme)
            }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                when {
                    selected == theme -> ring(g2, Palette.LIME, 2f)
                    isFocusOwner -> ring(g2, Palette.LIME_LINE, 1f)
                    model.isRollover -> ring(g2, Palette.HOVER_LINE, 1f)
                }
                paintPicture(g2, INSET.toDouble(), INSET.toDouble(), (width - INSET * 2).toDouble(), (height - INSET * 2).toDouble())
            } finally {
                g2.dispose()
            }
        }

        private fun ring(g2: Graphics2D, color: Color, stroke: Float) {
            g2.color = color
            g2.stroke = BasicStroke(stroke)
            val half = stroke / 2.0
            g2.draw(RoundRectangle2D.Double(half, half, width - stroke.toDouble(), height - stroke.toDouble(), RING_ARC, RING_ARC))
        }

        /** The picture of the app: the window, the sidebar, two text lines and the accent button. */
        private fun paintPicture(g2: Graphics2D, x: Double, y: Double, w: Double, h: Double) {
            val window = RoundRectangle2D.Double(x, y, w, h, PICTURE_ARC, PICTURE_ARC)
            val oldClip = g2.clip
            g2.clip(window)
            g2.color = colors.background
            g2.fill(window)
            val sidebarWidth = (w * 0.26).coerceAtLeast(6.0)
            g2.color = colors.sidebar
            g2.fill(Rectangle2D.Double(x, y, sidebarWidth, h))
            g2.clip = oldClip

            val left = x + sidebarWidth + 5
            g2.color = colors.text
            g2.fill(RoundRectangle2D.Double(left, y + 6, w * 0.42, 2.5, 2.5, 2.5))
            g2.color = colors.quietText
            g2.fill(RoundRectangle2D.Double(left, y + 11, w * 0.28, 2.5, 2.5, 2.5))
            g2.color = settings.customAccent ?: theme.accent
            g2.fill(RoundRectangle2D.Double(x + w - 15, y + h - 9, 10.0, 5.0, 5.0, 5.0))

            g2.color = colors.border
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, PICTURE_ARC, PICTURE_ARC))
        }
    }

    private companion object {
        const val OPTION_WIDTH = 48
        const val OPTION_HEIGHT = 34
        const val GAP = 6
        const val INSET = 4
        const val RING_ARC = 14.0
        const val PICTURE_ARC = 8.0
    }
}
