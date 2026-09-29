package org.litvin.ui

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.Path2D
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JCheckBox
import javax.swing.JRadioButton
// Ikonli (icon packs)
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2RoundMZ
import org.kordamp.ikonli.swing.FontIcon
import kotlin.math.ceil
import kotlin.math.max

/**
 * Shared Swing UI styles to match the mock (projects.html):
 * - Primary CTA button: rounded, lime gradient background with olive text and a leading plus icon (optional).
 * - Primary small button: solid neon green pill.
 * - Secondary button: subtle dark surface with thin rounded border.
 */
object UiStyles {
    // Small action icons for cards
    fun targetIcon(size: Int = 18) = ikon(Material2AL.ASSIGNMENT_TURNED_IN, size, LIME)

    fun plusCircleIcon(size: Int = 18) = ikon(Material2AL.ADD_CIRCLE, size, ICON_CIRCLE_DARK)

    fun pencilIcon(size: Int = 18): Icon = ikon(Material2AL.EDIT, size, LIME)

    fun crossIcon(size: Int = 18): Icon = ikon(Material2AL.BACKSPACE, size, RED)

    fun deleteIcon(size: Int = 18, color: Color = RED): Icon = ikon(Material2AL.DELETE, size, color)

    /** A crossed-out video camera. It tells that the video file of a project is not on the disk. */
    fun videoMissingIcon(size: Int = 16): Icon = ikon(Material2MZ.VIDEOCAM_OFF, size, RED)

    /** Filled rounded square that shows a comment color next to its action button. */
    fun colorSwatchIcon(color: Color, size: Int = 18): Icon = object : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = (g as? Graphics2D)?.create() as? Graphics2D ?: return
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val inset = (size * 0.12).toInt()
                val side = size - inset * 2
                g2.color = color
                g2.fillRoundRect(x + inset, y + inset, side, side, 4, 4)
                g2.color = FG_SECONDARY
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(x + inset, y + inset, side, side, 4, 4)
            } finally {
                g2.dispose()
            }
        }
    }

    fun smallIconButton(icon: Icon, tooltip: String? = null, onClick: () -> Unit): JButton = JButton().apply {
        this.icon = icon
        toolTipText = tooltip
        isFocusPainted = false
        isBorderPainted = false
        isContentAreaFilled = true
        background = SURFACE_HIGH
        foreground = FG_PRIMARY
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        )
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { onClick() }
    }
    // Palette inspired by design/projects.html dark theme
    val DARK_BG: Color = Color(0x0E, 0x0E, 0x0E)            // background / surface-dim
    val SURFACE_HIGH: Color = Color(0x30, 0x30, 0x30)       // surface-container-high
    val CARD_BG: Color = Color(0x1A, 0x1A, 0x1A)            // surface-container
    val CARD_BORDER: Color = Color(0x26, 0x26, 0x26)        // surface-variant border
    val FG_PRIMARY: Color = Color(0xD8, 0xD8, 0xD8)         // on-surface
    val FG_SECONDARY: Color = Color(0xAD, 0xAA, 0xAA)       // on-surface-variant
    val FG_DISABLED: Color = Color(0x6A, 0x6A, 0x6A)        // labels of disabled controls
    val GREEN: Color = Color(0xAF, 0xF6, 0x25)              // primary-fixed
    val ACCENT_TEXT: Color = Color(0xA3, 0xC5, 0x86)        // sage: accent text, calmer than GREEN on dark cards
    val YELLOW: Color = Color(0xFF, 0xD5, 0x4A)            // warning/emphasis
    val BLUE: Color = Color(0x3B, 0x82, 0xF6)              // informational accent
    val RED: Color = Color(0xCC, 0x46, 0x46)               // errors and destructive actions

    // Sidebar specific palette (from mock)
    val SIDEBAR_BG: Color = Color(0x12, 0x12, 0x12)
    val SIDEBAR_FG: Color = Color(0xD8, 0xD8, 0xD8)
    val SIDEBAR_FG_MUTED: Color = Color(0x9A, 0x9A, 0x9A)
    val SIDEBAR_GROUP_OUTLINE: Color = Color(0x33, 0x33, 0x33)
    val SIDEBAR_HOVER_BG: Color = Color(0x2C, 0x2C, 0x2C)
    val SIDEBAR_ACTIVE_BG: Color = Color(0x18, 0x18, 0x18)
    val LIME: Color = Color(0xA1, 0xFE, 0x00)

    // CTA gradient (mock: light lime to bright neon green)
    private val GRADIENT_START = Color(0xDD, 0xFF, 0xB0)     // #ddffb0
    private val GRADIENT_END = Color(0xA1, 0xFE, 0x00)       // #a1fe00
    private val GRADIENT_START_HOVER = GRADIENT_START.brighter()
    private val GRADIENT_END_HOVER = GRADIENT_END.brighter()

    // CTA content colors to match mock
    private val TEXT_ON_PRIMARY = Color(0x2B, 0x49, 0x00)    // dark olive text
    private val ICON_CIRCLE_DARK = Color(0x3C, 0x43, 0x00)   // deep olive circle
    private val ICON_PLUS_LIGHT = Color(0xED, 0xFF, 0xC8)    // pale lime for plus

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

    fun playIcon(size: Int = 28) = ikon(Material2RoundMZ.PLAY_ARROW, size, Color(0x1A, 0x2E, 0x00))

    fun pauseIcon(size: Int = 28) = ikon(Material2RoundMZ.PAUSE, size, Color(0x1A, 0x2E, 0x00))

    fun seekRightIcon(size: Int = 18): Icon = ikon(Feather.CHEVRON_RIGHT, size, LIME)
    fun seekLeftIcon(size: Int = 18): Icon = ikon(Feather.CHEVRON_LEFT, size, LIME)

    fun chevronDownIcon(size: Int = 16, color: Color = FG_SECONDARY): Icon = ikon(Feather.CHEVRON_DOWN, size, color)
    fun chevronRightIcon(size: Int = 16, color: Color = FG_SECONDARY): Icon = ikon(Feather.CHEVRON_RIGHT, size, color)

    fun forward5Icon(size: Int = 18): Icon = ikon(Feather.CHEVRONS_RIGHT, size, LIME)
    fun backward5Icon(size: Int = 18): Icon = ikon(Feather.CHEVRONS_LEFT, size, LIME)

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
                val grad = GradientPaint(0f, 0f, GRADIENT_START, 0f, h.toFloat(), GRADIENT_END)
                g2.paint = if (model.isRollover) GradientPaint(
                    0f,
                    0f,
                    GRADIENT_START_HOVER,
                    0f,
                    h.toFloat(),
                    GRADIENT_END_HOVER
                ) else grad
                g2.fillRoundRect(0,0,w,h,r,r)
                // icon
                this.icon.paintIcon(this, g2, (w - this.icon.iconWidth)/2, (h - this.icon.iconHeight)/2)
                // subtle inner shadow
                g2.color = Color(0, 0, 0, 40)
                g2.drawRoundRect(0,0,w-1,h-1,r,r)
            }
        }
    }

    // Sidebar container styling
    fun styleSidebarContainer(panel: JPanel) {
        panel.background = SIDEBAR_BG
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
                g2.color = SIDEBAR_GROUP_OUTLINE
                g2.drawRoundRect(0, lineY, width - 1, height - lineY - 1, 12, 12)
                val textWidth = metrics.stringWidth(caption)
                val textX = (width - textWidth) / 2
                // Clear the outline behind the caption, so that the caption interrupts the top line.
                g2.color = SIDEBAR_BG
                g2.fillRect(textX - 4, 0, textWidth + 8, metrics.height)
                g2.color = SIDEBAR_FG_MUTED
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
            foreground = SIDEBAR_FG
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
                    g2.color = SIDEBAR_ACTIVE_BG
                    g2.fillRoundRect(0, 0, w, h, r, r)
                    // lime accent bar on the left
                    g2.color = LIME
                    g2.fillRoundRect(0, 0, 4, h, 6, 6)
                }
                hover -> {
                    g2.color = SIDEBAR_HOVER_BG
                    g2.fillRoundRect(0, 0, w, h, r, r)
                }
            }
            // text color
            foreground = if (active) Color.WHITE else SIDEBAR_FG
            super.paintComponent(g)
        }
    }

    // Small, simple icons for sidebar
    fun folderIcon(size: Int = 20): Icon = ikon(Material2MZ.SOURCE, size, LIME)

    fun colorsIcon(size: Int = 20): Icon = ikon(Material2AL.COLOR_LENS, size, LIME)

    fun exportIcon(size: Int = 20): Icon = ikon(Feather.FILM, size, LIME)

    // Tab icon: Crop (prefer Ikonli Feather.CROP with fallback)
    fun cropRotateIcon(size: Int = 20): Icon = ikon(Material2AL.CROP_ROTATE, size, LIME)

    // Tab icon: Crop (prefer Ikonli Feather.CROP with fallback)
    fun pointsIcon(size: Int = 20): Icon = ikon(Material2MZ.SPORTS_TENNIS, size, LIME)

    fun statsIcon(size: Int = 20): Icon = ikon(Material2AL.BAR_CHART, size, LIME)

    fun helpIcon(size: Int = 20): Icon = ikon(Material2AL.HELP_OUTLINE, size, LIME)

    fun moreIcon(size: Int = 20): Icon = ikon(Material2MZ.MORE_HORIZ, size, LIME)

    fun infoIcon(size: Int = 14, color: Color = FG_SECONDARY): Icon = ikon(Material2AL.INFO, size, color)

    /** Marks a value that opens its point in the Scoring tab. */
    fun openPointIcon(size: Int = 14, color: Color = ACCENT_TEXT): Icon = ikon(Material2MZ.PLAY_CIRCLE_OUTLINE, size, color)

    // Close button of a hint balloon
    fun closeIcon(size: Int = 14, color: Color = FG_SECONDARY): Icon = ikon(Material2AL.CLOSE, size, color)

    /** Primary CTA button with gradient; includes a leading circle-plus icon. */
    /**
     * Returns [size] with more width, so that [text] shows fully and without "...".
     * The app paints text with fractional font metrics. Swing measures text with integer metrics.
     * On scaled displays, the painted text can be wider than the measured text.
     */
    fun widenForText(c: JComponent, size: Dimension, text: String?): Dimension {
        if (text.isNullOrEmpty()) return size
        val font = c.font ?: return size
        val measured = c.getFontMetrics(font).stringWidth(text)
        val frc = FontRenderContext(c.graphicsConfiguration?.defaultTransform, true, true)
        val painted = ceil(font.getStringBounds(text, frc).width).toInt()
        val extra = max(0, painted - measured) + ceil(measured * TEXT_WIDTH_SLACK).toInt() + 2
        return Dimension(size.width + extra, size.height)
    }

    private const val TEXT_WIDTH_SLACK = 0.04

    fun primaryButton(text: String, onClick: () -> Unit): JButton = object : JButton(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            val r = 12
            val hover = model.isRollover
            val pressed = model.isArmed && model.isPressed
            val c1 = if (pressed) GRADIENT_START.darker() else if (hover) GRADIENT_START_HOVER else GRADIENT_START
            val c2 = if (pressed) GRADIENT_END.darker() else if (hover) GRADIENT_END_HOVER else GRADIENT_END
            val paint = GradientPaint(0f, 0f, c1, w.toFloat(), h.toFloat(), c2)
            g2.paint = paint
            g2.fillRoundRect(0, 0, w, h, r, r)
            super.paintComponent(g)
        }

        override fun getPreferredSize(): Dimension = widenForText(this, super.getPreferredSize(), text)
        override fun getMaximumSize(): Dimension = preferredSize
    }.apply {
        addActionListener { onClick() }
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isRolloverEnabled = true
        border = BorderFactory.createEmptyBorder(10, 20, 10, 20)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        icon = plusCircleIcon(size = 22)
        iconTextGap = 10
        foreground = TEXT_ON_PRIMARY
        font = font.deriveFont(Font.BOLD, font.size2D + 1.5f)
        isFocusPainted = false
    }

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
        background = GREEN
        foreground = TEXT_ON_PRIMARY
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
        btn.background = SURFACE_HIGH
        btn.foreground = FG_PRIMARY
        btn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        )
        btn.isFocusPainted = false
        btn.font = btn.font.deriveFont(Font.BOLD)
    }

    // Small painter for the leading plus-in-circle icon on the primary CTA
    private class PlusInCircleIcon(
        private val size: Int,
        private val circleColor: Color,
        private val plusColor: Color
    ) : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            if (g == null) return
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val r = size
            g2.color = circleColor
            g2.fillOval(x, y, r, r)
            g2.color = plusColor
            val bar = (r * 0.16).toInt().coerceAtLeast(2)
            val len = (r * 0.52).toInt()
            val cx = x + r / 2
            val cy = y + r / 2
            g2.fillRoundRect(cx - len / 2, cy - bar / 2, len, bar, bar, bar)
            g2.fillRoundRect(cx - bar / 2, cy - len / 2, bar, len, bar, bar)
        }
    }

    /**
     * Apply secondary/tertiary text style for labels and helper texts on card-like surfaces.
     * - Foreground: FG_SECONDARY
     * - Background: CARD_BG
     * - Slightly reduce font size to de-emphasize
     */
    fun styleHelper(c: JComponent) {
        c.foreground = FG_SECONDARY
        c.background = CARD_BG
        try {
            val f = c.font
            if (f != null) c.font = f.deriveFont((f.size2D - 1f).coerceAtLeast(11f))
        } catch (_: Throwable) { }
    }

    /** Primary text style on card-like surfaces: FG_PRIMARY on CARD_BG. */
    fun stylePrimary(c: JComponent) {
        c.foreground = FG_PRIMARY
        c.background = CARD_BG
    }

    /** Monospace variant of [styleHelper] using Consolas when available. */
    fun styleMono(c: JComponent) {
        styleHelper(c)
        try {
            c.font = Font("Consolas", Font.PLAIN, c.font.size)
        } catch (_: Throwable) { }
    }

    /**
     * Simple card container with title header and body.
     * - Background: CARD_BG, Foreground: FG_PRIMARY
     * - Thin rounded border with CARD_BORDER
     * - 12px internal padding
     */
    fun card(title: String, body: JComponent): JPanel {
        val container = JPanel(BorderLayout())
        container.background = CARD_BG
        container.foreground = FG_PRIMARY
        container.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        )
        val header = JLabel(title)
        header.font = header.font.deriveFont(Font.BOLD)
        header.foreground = FG_PRIMARY
        container.add(header, BorderLayout.NORTH)
        container.add(body, BorderLayout.CENTER)
        return container
    }

    /** Apply dark theme styling to JCheckBox with custom minimalist box and checkmark. */
    fun styleCheckBox(cb: JCheckBox) {
        try {
            cb.isOpaque = false
            cb.foreground = FG_PRIMARY
            cb.background = CARD_BG
            cb.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
            cb.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            fun boxIcon(selected: Boolean, disabled: Boolean = false): Icon = object : Icon {
                private val size = 16
                override fun getIconWidth() = size
                override fun getIconHeight() = size
                override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val w = size; val h = size
                    val r = 4
                    // Box background
                    val bgCol = if (disabled) Color(0x1F,0x1F,0x1F) else SURFACE_HIGH
                    g2.color = bgCol
                    g2.fillRoundRect(x, y, w, h, r, r)
                    // Border
                    g2.color = if (disabled) CARD_BORDER.darker() else CARD_BORDER
                    g2.drawRoundRect(x, y, w - 1, h - 1, r, r)
                    if (selected) {
                        // Fill with accent tint and draw check
                        val fill = if (disabled) Color(0x3A,0x3A,0x2F) else Color(0x22, 0x2F, 0x16)
                        g2.color = fill
                        g2.fillRoundRect(x + 1, y + 1, w - 2, h - 2, r, r)
                        // Check mark
                        g2.color = if (disabled) FG_SECONDARY else LIME
                        g2.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        val p = java.awt.geom.Path2D.Float()
                        p.moveTo((x + w*0.26f), (y + h*0.54f))
                        p.lineTo((x + w*0.44f), (y + h*0.72f))
                        p.lineTo((x + w*0.78f), (y + h*0.30f))
                        g2.draw(p)
                    }
                }
            }
            cb.icon = boxIcon(false, disabled = false)
            cb.selectedIcon = boxIcon(true, disabled = false)
            cb.disabledIcon = boxIcon(false, disabled = true)
            cb.disabledSelectedIcon = boxIcon(true, disabled = true)
            // Keep text spacing pleasant
            cb.iconTextGap = 8
        } catch (_: Throwable) { }
    }

    /** Apply dark theme styling to JRadioButton: a round box with a lime dot, like [styleCheckBox]. */
    fun styleRadioButton(rb: JRadioButton) {
        rb.isOpaque = false
        rb.foreground = FG_PRIMARY
        rb.background = CARD_BG
        rb.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
        rb.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        rb.isFocusPainted = false

        fun roundIcon(selected: Boolean, disabled: Boolean): Icon = object : Icon {
            private val size = 16
            override fun getIconWidth() = size
            override fun getIconHeight() = size
            override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                val g2 = g?.create() as? Graphics2D ?: return
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = if (disabled) Color(0x1F, 0x1F, 0x1F) else SURFACE_HIGH
                    g2.fillOval(x, y, size - 1, size - 1)
                    g2.color = if (selected && !disabled) LIME else CARD_BORDER
                    g2.drawOval(x, y, size - 1, size - 1)
                    if (selected) {
                        g2.color = if (disabled) FG_SECONDARY else LIME
                        g2.fillOval(x + 4, y + 4, size - 8, size - 8)
                    }
                } finally {
                    g2.dispose()
                }
            }
        }
        rb.icon = roundIcon(selected = false, disabled = false)
        rb.selectedIcon = roundIcon(selected = true, disabled = false)
        rb.rolloverIcon = rb.icon
        rb.rolloverSelectedIcon = rb.selectedIcon
        rb.disabledIcon = roundIcon(selected = false, disabled = true)
        rb.disabledSelectedIcon = roundIcon(selected = true, disabled = true)
        rb.iconTextGap = 8
    }

}
