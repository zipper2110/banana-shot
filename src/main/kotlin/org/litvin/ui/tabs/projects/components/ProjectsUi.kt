package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.math.ceil
import org.litvin.ui.commons.Palette

/**
 * Fonts and small painted parts of the Projects tab. The colors are in [Palette].
 * The values come from design/projects-redesign/final.html and its shared.css.
 */
internal object ProjectsUi {
    enum class Weight { REGULAR, SEMIBOLD, BOLD }

    private val fonts = HashMap<Pair<Float, Weight>, Font>()

    private val semiboldFamily: String? by lazy {
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.firstOrNull { it == "Segoe UI Semibold" }
        } catch (_: Throwable) {
            null
        }
    }

    /** A font of the app family with a size in pixels. Semibold uses "Segoe UI Semibold" when the PC has it. */
    fun font(size: Float, weight: Weight = Weight.REGULAR): Font = fonts.getOrPut(size to weight) {
        val family = (UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 13)).family
        when (weight) {
            Weight.REGULAR -> Font(family, Font.PLAIN, 1).deriveFont(size)
            Weight.BOLD -> Font(family, Font.BOLD, 1).deriveFont(size)
            Weight.SEMIBOLD -> semiboldFamily?.let { Font(it, Font.PLAIN, 1).deriveFont(size) }
                ?: Font(family, Font.BOLD, 1).deriveFont(size)
        }
    }

    /** The font of a caption in capital letters with some space between the letters. */
    fun trackedFont(size: Float, tracking: Double, weight: Weight = Weight.REGULAR): Font =
        font(size, weight).deriveFont(mapOf(TextAttribute.TRACKING to tracking))

    fun icon(ikon: Ikon, size: Int, color: Color): Icon = FontIcon.of(ikon, size, color)

    /** The text measurement of the tab. It is the same as the painting: antialiased text with fractional metrics. */
    val frc = FontRenderContext(null, true, true)

    fun textWidth(text: String, font: Font): Float = font.getStringBounds(text, frc).width.toFloat()

    /** The baseline that centers one line of [font] in a box of [height] pixels that starts at [top]. */
    fun baseline(font: Font, top: Float, height: Float): Float {
        val metrics = font.getLineMetrics("Hg", frc)
        return top + (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent
    }

    /** A copy of [g] with antialiasing for shapes and the text settings of the desktop. */
    fun smooth(g: Graphics): Graphics2D = (g.create() as Graphics2D).apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") as? Map<*, *>
        if (hints != null) addRenderingHints(hints)
        else setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    /** Fills a rounded rectangle and draws a 1 px border inside it. A null color skips that part. */
    fun paintBox(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, radius: Int, fill: Color?, border: Color?) {
        val arc = radius * 2.0
        if (fill != null) {
            g2.color = fill
            g2.fill(RoundRectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), arc, arc))
        }
        if (border != null) {
            g2.color = border
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1.0, h - 1.0, arc - 1, arc - 1))
        }
    }

    /** Draws a dashed 1 px border inside the rectangle. */
    fun paintDashedBox(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Color) {
        val arc = radius * 2.0
        g2.color = color
        g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(3f, 3f), 0f)
        g2.draw(RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1.0, h - 1.0, arc - 1, arc - 1))
    }

    /** Draws [text] so that its left edge is at [x] and the line is centered in the box [top]..[top]+[height]. */
    fun drawText(g2: Graphics2D, text: String, font: Font, color: Color, x: Float, top: Float, height: Float) {
        g2.font = font
        g2.color = color
        g2.drawString(text, x, baseline(font, top, height))
    }

    /** Shortens [text] at the end with "…" until it fits in [maxWidth]. */
    fun ellipsize(text: String, font: Font, maxWidth: Float): String {
        if (textWidth(text, font) <= maxWidth) return text
        var count = text.length
        while (count > 0) {
            val shown = text.substring(0, count).trimEnd() + "…"
            if (textWidth(shown, font) <= maxWidth) return shown
            count--
        }
        return "…"
    }

    /** Shortens [text] at the start with "…" until it fits in [maxWidth]. Thus the end of a path stays visible. */
    fun ellipsizeStart(text: String, font: Font, maxWidth: Float): String {
        if (textWidth(text, font) <= maxWidth) return text
        var start = 1
        while (start < text.length) {
            val shown = "…" + text.substring(start)
            if (textWidth(shown, font) <= maxWidth) return shown
            start++
        }
        return "…"
    }

    /**
     * Splits [text] into lines that fit in [maxWidth]. A line breaks after a space or a path separator.
     * A word that is longer than a line breaks at any character.
     */
    fun wrap(text: String, font: Font, maxWidth: Float): List<String> {
        if (maxWidth <= 0f) return listOf(text)
        val lines = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            if (textWidth(rest, font) <= maxWidth) {
                lines += rest
                break
            }
            // The longest start of the text that fits.
            var fit = 1
            while (fit < rest.length && textWidth(rest.substring(0, fit + 1), font) <= maxWidth) fit++
            val breakAt = (fit downTo 1).firstOrNull { rest[it - 1] == ' ' || rest[it - 1] == '\\' || rest[it - 1] == '/' }
            val cut = if (breakAt != null && breakAt > fit / 3) breakAt else fit
            lines += rest.substring(0, cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        return lines.ifEmpty { listOf("") }
    }

    /** Line height of the wrapped texts, as `line-height` in the design. */
    fun lineHeight(font: Font, factor: Float): Float = font.size2D * factor
}

/** A caption in small capital letters above a block of the start panel, the `.caption` element of the design. */
internal class Caption(text: String) : JComponent() {
    private val captionFont = ProjectsUi.trackedFont(10.5f, 0.1, ProjectsUi.Weight.SEMIBOLD)
    private val shown = text.uppercase()

    override fun getPreferredSize() = Dimension(ceil(ProjectsUi.textWidth(shown, captionFont)).toInt() + 4, 16)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            ProjectsUi.drawText(g2, shown, captionFont, Palette.FG_3, 0f, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }
}

/**
 * A text button of the Projects tab, the `.btn` element of the design.
 * [Kind.SECONDARY] is the dark default, [Kind.LIME] is the one main action, and [Kind.GHOST] has no fill until the pointer is over it.
 * [Kind.DANGER] is the red button that confirms a deletion.
 * A secondary button with [limeHover] gets a lime border under the pointer, as the Open buttons of the table.
 */
internal class ProjectsButton(
    text: String,
    private val ikon: Ikon? = null,
    private val kind: Kind = Kind.SECONDARY,
    private val buttonHeight: Int = 30,
    private val fontSize: Float = 12.5f,
    private val limeHover: Boolean = false,
) : JButton(text) {
    enum class Kind { SECONDARY, LIME, GHOST, DANGER }

    private val textFont
        get() = when (kind) {
            Kind.LIME -> ProjectsUi.font(fontSize, ProjectsUi.Weight.BOLD)
            Kind.DANGER -> ProjectsUi.font(fontSize, ProjectsUi.Weight.SEMIBOLD)
            else -> ProjectsUi.font(fontSize)
        }
    private val iconSize get() = if (kind == Kind.LIME) 20 else 16
    private val icons = HashMap<Color, Icon>()

    /** True: the button fills the width that the layout gives it. */
    var stretch = false

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private fun leadingWidth() = if (ikon != null) iconSize + GAP else 0

    override fun getPreferredSize(): Dimension {
        val padding = if (kind == Kind.LIME) 16 else if (buttonHeight < 30) 10 else 12
        return Dimension(ceil(ProjectsUi.textWidth(text, textFont)).toInt() + leadingWidth() + padding * 2 + 2, buttonHeight)
    }

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = if (stretch) Dimension(Int.MAX_VALUE, buttonHeight) else preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            val hover = model.isRollover && isEnabled
            val radius = if (kind == Kind.LIME) 6 else 4
            val fg: Color
            val iconColor: Color
            when (kind) {
                Kind.SECONDARY -> {
                    ProjectsUi.paintBox(
                        g2, 0, 0, width, height, radius,
                        if (hover) Palette.RAISED_2 else Palette.RAISED,
                        if (!hover) Palette.LINE_2 else if (limeHover) Palette.LIME_LINE else Palette.HOVER_LINE,
                    )
                    fg = Palette.FG
                    iconColor = Palette.LIME
                }
                Kind.LIME -> {
                    ProjectsUi.paintBox(
                        g2, 0, 0, width, height, radius,
                        if (!isEnabled) Palette.LINE_3 else if (hover) Palette.LIME_HOVER else Palette.LIME,
                        null,
                    )
                    fg = if (isEnabled) Palette.ON_LIME else Palette.FG_3
                    iconColor = fg
                }
                Kind.DANGER -> {
                    ProjectsUi.paintBox(g2, 0, 0, width, height, radius, if (hover) Palette.RED_SOLID_HOVER else Palette.RED_SOLID, null)
                    fg = Palette.FG_STRONG
                    iconColor = fg
                }
                Kind.GHOST -> {
                    if (hover) ProjectsUi.paintBox(g2, 0, 0, width, height, radius, Palette.RAISED, Palette.LINE_2)
                    fg = if (hover) Palette.FG else Palette.FG_2
                    iconColor = fg
                }
            }
            val contentWidth = leadingWidth() + ProjectsUi.textWidth(text, textFont)
            var x = (width - contentWidth) / 2f
            if (ikon != null) {
                val icon = icons.getOrPut(iconColor) { ProjectsUi.icon(ikon, iconSize, iconColor) }
                icon.paintIcon(this, g2, x.toInt(), (height - icon.iconHeight) / 2)
                x += iconSize + GAP
            }
            ProjectsUi.drawText(g2, text, textFont, if (isEnabled) fg else Palette.FG_3, x, 0f, height.toFloat())
            if (isFocusOwner) {
                val ring = when (kind) {
                    Kind.LIME -> Palette.ON_LIME
                    Kind.DANGER -> Palette.FG_STRONG
                    else -> Palette.LIME_LINE
                }
                ProjectsUi.paintBox(g2, 0, 0, width, height, radius, null, ring)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val GAP = 6
    }
}

/**
 * A small square icon button, the `.btn-icon` element of the design.
 * A [bordered] button has the dark fill and border of a secondary button, for example the pager buttons.
 * Other buttons have no fill until the pointer is over them. [restColor] gives the icon color at rest.
 */
internal class ProjectsIconButton(
    private val ikon: Ikon,
    tooltip: String,
    private val bordered: Boolean = false,
    private val side: Int = 26,
    private val iconSize: Int = 16,
    private val restColor: () -> Color = { Palette.FG_2 },
) : JButton() {
    private val icons = HashMap<Color, Icon>()

    init {
        toolTipText = tooltip
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() = Dimension(side, side)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            val hover = model.isRollover && isEnabled
            if (bordered) {
                ProjectsUi.paintBox(
                    g2, 0, 0, width, height, 4,
                    if (hover) Palette.RAISED_2 else Palette.RAISED,
                    if (hover) Palette.HOVER_LINE else Palette.LINE_2,
                )
            } else if (hover) {
                ProjectsUi.paintBox(g2, 0, 0, width, height, 4, Palette.RAISED, Palette.LINE_2)
            }
            val color = when {
                !isEnabled -> Palette.LINE_3
                hover || bordered -> Palette.FG
                else -> restColor()
            }
            val icon = icons.getOrPut(color) { ProjectsUi.icon(ikon, iconSize, color) }
            icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
            if (isFocusOwner) ProjectsUi.paintBox(g2, 0, 0, width, height, 4, null, Palette.LIME_LINE)
        } finally {
            g2.dispose()
        }
    }
}

/** A fixed empty space. */
internal class Gap(private val w: Int, private val h: Int) : JComponent() {
    override fun getPreferredSize() = Dimension(w, h)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize
}

/** Sets the left alignment that BoxLayout needs for the children of a vertical box. */
internal fun <T : Component> T.leftAligned(): T = apply { (this as? JComponent)?.alignmentX = Component.LEFT_ALIGNMENT }
