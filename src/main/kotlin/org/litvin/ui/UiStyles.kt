package org.litvin.ui

import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities
// Ikonli (icon packs)
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2RoundMZ
import org.kordamp.ikonli.swing.FontIcon
import org.litvin.ui.commons.Palette
import java.awt.Color

/**
 * Shared Swing styles of the sidebar and the older screens (transport bar, Test tab).
 * The colors are in [Palette].
 */
object UiStyles {
    // Small action icons for cards
    fun targetIcon(size: Int = 18) = ikon(Material2AL.ASSIGNMENT_TURNED_IN, size, Palette.LIME)

    // Transport icons (prefer Ikonli Feather pack when available)
    private fun ikon(ik: Ikon, size: Int, color: Color): Icon {
        return try {
            FontIcon.of(ik, size).also { it.iconColor = color }
        } catch (_: Throwable) {
            // Fallback: empty label icon of requested size; callers usually pair with background styling
            object : Icon {
                override fun getIconWidth() = size
                override fun getIconHeight() = size
                override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {}
            }
        }
    }

    fun playIcon(size: Int = 28) = ikon(Material2RoundMZ.PLAY_ARROW, size, Palette.ON_LIME)

    fun pauseIcon(size: Int = 28) = ikon(Material2RoundMZ.PAUSE, size, Palette.ON_LIME)

    fun seekRightIcon(size: Int = 18): Icon = ikon(Feather.CHEVRON_RIGHT, size, Palette.LIME)
    fun seekLeftIcon(size: Int = 18): Icon = ikon(Feather.CHEVRON_LEFT, size, Palette.LIME)

    fun forward5Icon(size: Int = 18): Icon = ikon(Feather.CHEVRONS_RIGHT, size, Palette.LIME)
    fun backward5Icon(size: Int = 18): Icon = ikon(Feather.CHEVRONS_LEFT, size, Palette.LIME)

    fun squarePrimaryButton(icon: Icon, size: Int = 64, onClick: (() -> Unit)? = null): JButton {
        return object : JButton() {
            init {
                isFocusPainted = false
                isBorderPainted = false
                isContentAreaFilled = false
                cursor =  Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                preferredSize = Dimension(size, size)
                minimumSize = Dimension(size, size)
                maximumSize = Dimension(size, size)
                this.icon = icon
                addActionListener { onClick?.invoke() }
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val w = width; val h = height
                val r = 18
                val end = if (model.isRollover) Palette.LIME_HOVER else Palette.LIME
                g2.paint = GradientPaint(0f, 0f, Palette.LIME_LIGHT, 0f, h.toFloat(), end)
                g2.fillRoundRect(0,0,w,h,r,r)
                // icon
                this.icon.paintIcon(this, g2, (w - this.icon.iconWidth)/2, (h - this.icon.iconHeight)/2)
                // subtle inner shadow
                g2.color = Palette.SHADE
                g2.drawRoundRect(0,0,w-1,h-1,r,r)
            }
        }
    }

    // Sidebar container styling
    fun styleSidebarContainer(panel: JPanel) {
        panel.background = Palette.INSET
        panel.isOpaque = true
        panel.border = BorderFactory.createEmptyBorder(12, 0, 12, 0)
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    }

    /**
     * A group of sidebar buttons. A thin rounded outline goes around the buttons, and the [title] is set into the top line
     * of the outline. The outline and the small spaced caption make the title different from the button labels.
     */
    class SidebarGroup(title: String) : JPanel() {
        private val caption = title.uppercase()
        private val captionFont: Font = font.deriveFont(Font.BOLD, 9f)
            .deriveFont(mapOf(java.awt.font.TextAttribute.TRACKING to 0.15))

        init {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            val captionHeight = getFontMetrics(captionFont).height
            border = BorderFactory.createEmptyBorder(captionHeight + 2, 3, 4, 3)
            alignmentX = 0f
        }

        // The group keeps its height. The extra height of the sidebar goes to the space above Help.
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.font = captionFont
                val metrics = g2.fontMetrics
                val lineY = metrics.height / 2
                g2.color = Palette.LINE_2
                g2.drawRoundRect(0, lineY, width - 1, height - lineY - 1, 12, 12)
                val textWidth = metrics.stringWidth(caption)
                val textX = (width - textWidth) / 2
                // Clear the outline behind the caption, so that the caption interrupts the top line.
                g2.color = Palette.INSET
                g2.fillRect(textX - 4, 0, textWidth + 8, metrics.height)
                g2.color = Palette.FG_2
                g2.drawString(caption, textX, metrics.ascent)
            } finally {
                g2.dispose()
            }
        }
    }

    /** Sidebar button with custom hover/active styling and an icon. */
    fun sidebarButton(text: String, icon: Icon, onClick: () -> Unit): SidebarButton = SidebarButton(text, icon).apply {
        addActionListener { onClick() }
    }

    class SidebarButton(text: String, icon: Icon) : JButton(text) {
        var active: Boolean = false
            set(value) { field = value; repaint() }

        /** The number in the badge at the top right of the icon. Zero hides the badge. */
        var badgeCount: Int = 0
            set(value) {
                if (field == value) return
                field = value
                repaint()
            }

        init {
            this.icon = icon
            // Center icon and place text under the icon
            horizontalAlignment = CENTER
            horizontalTextPosition = CENTER
            verticalTextPosition = BOTTOM
            iconTextGap = 6
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = Palette.FG
            border = BorderFactory.createEmptyBorder(8, 0, 8, 0)
            preferredSize = Dimension(180, 56)
            minimumSize = Dimension(0, 56)
            maximumSize = Dimension(Int.MAX_VALUE, 56)
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            val hover = model.isRollover
            val r = 10
            // background
            when {
                active -> {
                    g2.color = Palette.ROW_HOVER
                    g2.fillRoundRect(0, 0, w, h, r, r)
                    // lime accent bar on the left
                    g2.color = Palette.LIME
                    g2.fillRoundRect(0, 0, 4, h, 6, 6)
                }
                hover -> {
                    g2.color = Palette.RAISED_2
                    g2.fillRoundRect(0, 0, w, h, r, r)
                }
            }
            // text color
            foreground = if (active) Palette.FG_STRONG else Palette.FG
            super.paintComponent(g)
            if (badgeCount > 0) paintBadge(g2)
        }

        private fun paintBadge(g2: Graphics2D) {
            val iconBounds = iconBounds() ?: return
            val label = if (badgeCount > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else badgeCount.toString()
            g2.font = BADGE_FONT
            val metrics = g2.fontMetrics
            val pillHeight = BADGE_HEIGHT
            val pillWidth = maxOf(pillHeight, metrics.stringWidth(label) + 2 * BADGE_PADDING)
            // The badge sits on the top right corner of the icon.
            val x = iconBounds.x + iconBounds.width - pillHeight / 2
            // The ring must stay inside the button, or the button edge cuts it.
            val y = maxOf(iconBounds.y - pillHeight / 2 + 2, BADGE_RING)
            // A ring in the color behind the badge separates the badge from the icon.
            g2.color = when {
                active -> Palette.ROW_HOVER
                model.isRollover -> Palette.RAISED_2
                else -> parent?.background ?: Palette.BG
            }
            val ring = BADGE_RING
            g2.fillRoundRect(x - ring, y - ring, pillWidth + 2 * ring, pillHeight + 2 * ring, pillHeight + 2 * ring, pillHeight + 2 * ring)
            // Blue contrasts with the lime icons, so the badge is easy to see.
            g2.color = Palette.BLUE
            g2.fillRoundRect(x, y, pillWidth, pillHeight, pillHeight, pillHeight)
            g2.color = Palette.ON_LIGHT
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val textX = x + (pillWidth - metrics.stringWidth(label)) / 2
            val textY = y + (pillHeight - metrics.height) / 2 + metrics.ascent
            g2.drawString(label, textX, textY)
        }

        /** The place of the icon, from the same layout that the button look uses. */
        private fun iconBounds(): Rectangle? {
            val icon = icon ?: return null
            val insets = insets
            val view = Rectangle(insets.left, insets.top, width - insets.left - insets.right, height - insets.top - insets.bottom)
            val iconRect = Rectangle()
            SwingUtilities.layoutCompoundLabel(
                this, getFontMetrics(font), text, icon,
                verticalAlignment, horizontalAlignment, verticalTextPosition, horizontalTextPosition,
                view, iconRect, Rectangle(), iconTextGap,
            )
            return iconRect
        }

        private companion object {
            const val MAX_BADGE_COUNT = 99
            const val BADGE_HEIGHT = 16
            const val BADGE_PADDING = 5
            const val BADGE_RING = 2
            val BADGE_FONT = Font(Font.SANS_SERIF, Font.BOLD, 10)
        }
    }

    // Small, simple icons for sidebar
    fun folderIcon(size: Int = 20): Icon = ikon(Material2MZ.SOURCE, size, Palette.LIME)

    fun colorsIcon(size: Int = 20): Icon = ikon(Material2AL.COLOR_LENS, size, Palette.LIME)

    fun exportIcon(size: Int = 20): Icon = ikon(Feather.FILM, size, Palette.LIME)

    fun cropRotateIcon(size: Int = 20): Icon = ikon(Material2AL.CROP_ROTATE, size, Palette.LIME)

    fun pointsIcon(size: Int = 20): Icon = ikon(Material2MZ.SPORTS_TENNIS, size, Palette.LIME)

    fun statsIcon(size: Int = 20): Icon = ikon(Material2AL.BAR_CHART, size, Palette.LIME)

    fun helpIcon(size: Int = 20): Icon = ikon(Material2AL.HELP_OUTLINE, size, Palette.LIME)

    fun moreIcon(size: Int = 20): Icon = ikon(Material2MZ.MORE_HORIZ, size, Palette.LIME)

    // Close button of a hint balloon
    fun closeIcon(size: Int = 14, color: Color = Palette.FG_2): Icon = ikon(Material2AL.CLOSE, size, color)

    /** Smaller solid green primary button (used in per-card actions). Rounded corners = 5px. */
    fun primarySmallButton(text: String, onClick: () -> Unit): JButton = object : JButton(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            val r = 5 // corner radius in pixels
            g2.color = background
            // arc width/height should be ~2x radius for Swing's round-rect
            g2.fillRoundRect(0, 0, w, h, r * 2, r * 2)
            super.paintComponent(g)
        }
    }.apply {
        addActionListener { onClick() }
        background = Palette.LIME
        foreground = Palette.ON_LIME
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        border = BorderFactory.createEmptyBorder(4, 10, 4, 10)
        font = font.deriveFont(Font.BOLD)
        isFocusPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    /** Secondary button: dark surface with thin rounded border, bold label. */
    fun styleSecondary(btn: AbstractButton) {
        btn.isOpaque = true
        btn.background = Palette.CONTROL
        btn.foreground = Palette.FG
        btn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Palette.LINE, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        )
        btn.isFocusPainted = false
        btn.font = btn.font.deriveFont(Font.BOLD)
    }
}
