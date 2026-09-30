package org.litvin.ui.tabs.scoring.ui

import org.kordamp.ikonli.Ikon
import org.litvin.scoring.ScoringEngine.MatchState
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import javax.swing.Icon
import javax.swing.JButton
import kotlin.math.ceil
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit

/**
 * Helpers of the Scoring tab. The tab uses the colors of [Palette] and the fonts and key chips of the Points tab
 * ([UiKit], [KeyChips]), because design/scoring-redesign/shared.css has the same base.
 */
internal object ScoringUi {
    /** The opacity of a disabled control. */
    const val DISABLED_ALPHA = 0.4f

    /**
     * The text color on a surface in the player color [bg]: [Palette.ON_LIGHT] or white, whichever has the higher WCAG contrast.
     * The user can select any player color, also a dark one.
     */
    fun onPlayer(bg: Color): Color =
        if (contrast(bg, Palette.ON_LIGHT) >= contrast(bg, Palette.PURE_WHITE)) Palette.ON_LIGHT else Palette.PURE_WHITE

    /** The lightest row background of the points list: the selected row. */
    private val LIST_ROW = Palette.SELECTED

    /**
     * True when a mark in the player [color] is not visible enough on a row of the points list.
     * 3:1 is the WCAG minimum contrast for graphics.
     */
    fun needsBadge(color: Color): Boolean = contrast(color, LIST_ROW) < 3.0

    /** The WCAG contrast ratio of two colors, from 1 to 21. */
    fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    /** The key chip on a surface in the player color [bg]. */
    fun onPlayerKeyStyle(bg: Color): KeyChipStyle =
        if (onPlayer(bg) == Palette.ON_LIGHT) ON_LIGHT_KEY else ON_DARK_KEY

    private val ON_LIGHT_KEY = KeyChipStyle(fill = Palette.SHADE, border = Palette.SHADE_2, text = Palette.ON_LIGHT)
    private val ON_DARK_KEY = KeyChipStyle(fill = Palette.HIGHLIGHT_2, border = Palette.HIGHLIGHT_3, text = Palette.PURE_WHITE)

    /** "#4DA3FF" as a color, or [fallback] when the text is not a color. */
    fun parseColor(hex: String?, fallback: Color): Color {
        val text = hex?.trim()?.removePrefix("#") ?: return fallback
        if (text.length != 6) return fallback
        return text.toIntOrNull(16)?.let(::Color) ?: fallback
    }

    /** The points of [player] (1 or 2) as the scoreboard shows them: 0, 15, 30, 40, Ad, or the tiebreak count. */
    fun pointsText(state: MatchState, player: Int): String {
        val mine = if (player == 1) state.p1Pts else state.p2Pts
        val other = if (player == 1) state.p2Pts else state.p1Pts
        if (state.isTiebreak) return mine.toString()
        if (mine < 4 && other < 4) return arrayOf("0", "15", "30", "40")[mine.coerceIn(0, 3)]
        return if (mine > other) "Ad" else "40"
    }

    /** True when the points of [player] are ahead in the current game, as the lime value in the design. */
    fun leads(state: MatchState, player: Int): Boolean {
        val mine = pointsText(state, player)
        val other = pointsText(state, 3 - player)
        if (mine == other) return false
        if (mine == "Ad") return true
        if (other == "Ad") return false
        return (mine.toIntOrNull() ?: 0) > (other.toIntOrNull() ?: 0)
    }

    /**
     * The "scoreboard" glyph of Material Symbols Outlined (weight 400), as in the design.
     * The Ikonli packs do not have this glyph, so the icon paints the SVG path of the glyph.
     */
    fun scoreboardIcon(size: Int, color: Color): Icon = SvgPathIcon(SCOREBOARD, size, color)

    /** The path of the glyph, moved from the view box `0 -960 960 960` to `0 0 960 960`. */
    private val SCOREBOARD: Path2D by lazy {
        svgPath(
            "M620-360q-17 0-28.5-11.5T580-400v-160q0-17 11.5-28.5T620-600h100q17 0 28.5 11.5T760-560v160q0 17-11.5 28.5" +
                "T720-360H620Zm20-60h60v-120h-60v120Zm-440 60v-100q0-17 11.5-28.5T240-500h80v-40H200v-60h140q17 0 28.5 11.5" +
                "T380-560v60q0 17-11.5 28.5T340-460h-80v40h120v60H200Zm250-160v-60h60v60h-60Zm0 140v-60h60v60h-60Z" +
                "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h120v-80h80v80h240v-80h80v80h120q33 0 56.5 23.5" +
                "T880-720v480q0 33-23.5 56.5T800-160H160Zm0-80h290v-60h60v60h290v-480H510v60h-60v-60H160v480Zm0 0v-480 480Z",
        ).apply { transform(AffineTransform.getTranslateInstance(0.0, 960.0)) }
    }

    /**
     * Reads an SVG path with the commands M, L, H, V, Q, T and Z, in absolute and relative forms.
     * The Material Symbols glyphs use only these commands.
     */
    internal fun svgPath(d: String): Path2D.Double {
        val tokens = Regex("[A-Za-z]|-?(?:\\d+\\.?\\d*|\\.\\d+)").findAll(d).map { it.value }.toList()
        val path = Path2D.Double(Path2D.WIND_NON_ZERO)
        var i = 0
        var command = 'M'
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0
        // The control point of the last quadratic curve, for the reflection of T.
        var controlX = 0.0
        var controlY = 0.0
        var lastWasQuad = false
        fun number(): Double = tokens[i++].toDouble()
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) command = tokens[i++][0]
            val relative = command.isLowerCase()
            val dx = if (relative) x else 0.0
            val dy = if (relative) y else 0.0
            var quad = false
            when (command.uppercaseChar()) {
                'M' -> {
                    x = dx + number()
                    y = dy + number()
                    path.moveTo(x, y)
                    startX = x
                    startY = y
                    // More pairs after a move are lines.
                    command = if (relative) 'l' else 'L'
                }
                'L' -> {
                    x = dx + number()
                    y = dy + number()
                    path.lineTo(x, y)
                }
                'H' -> {
                    x = dx + number()
                    path.lineTo(x, y)
                }
                'V' -> {
                    y = dy + number()
                    path.lineTo(x, y)
                }
                'Q' -> {
                    controlX = dx + number()
                    controlY = dy + number()
                    x = dx + number()
                    y = dy + number()
                    path.quadTo(controlX, controlY, x, y)
                    quad = true
                }
                'T' -> {
                    controlX = if (lastWasQuad) 2 * x - controlX else x
                    controlY = if (lastWasQuad) 2 * y - controlY else y
                    x = dx + number()
                    y = dy + number()
                    path.quadTo(controlX, controlY, x, y)
                    quad = true
                }
                'Z' -> {
                    path.closePath()
                    x = startX
                    y = startY
                }
                else -> throw IllegalArgumentException("SVG path command $command is not supported")
            }
            lastWasQuad = quad
        }
        return path
    }
}

/** Paints a filled [path] from a 960 × 960 view box at [size] × [size] pixels. */
private class SvgPathIcon(private val path: Path2D, private val size: Int, private val color: Color) : Icon {
    override fun getIconWidth() = size
    override fun getIconHeight() = size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.translate(x, y)
            g2.scale(size / 960.0, size / 960.0)
            g2.color = color
            g2.fill(path)
        } finally {
            g2.dispose()
        }
    }
}

/**
 * A text button of the Scoring tab, the `.btn` element of the design, with an optional icon and key chips.
 * [Kind.GHOST] has no fill until the pointer is over it. [Kind.LIME] is the next step.
 * A [alignLeft] button puts its content at the left edge, as the settings buttons of the design.
 */
internal class ScoringButton(
    text: String,
    ikon: Ikon? = null,
    private val keys: List<String> = emptyList(),
    kind: Kind = Kind.SECONDARY,
    private val buttonHeight: Int = 34,
    private val padding: Int = 12,
    private val alignLeft: Boolean = false,
    private val keyStyle: KeyChipStyle = KeyChipStyle.DEFAULT,
    glyph: ((size: Int, color: Color) -> Icon)? = null,
) : JButton(text) {
    enum class Kind { SECONDARY, GHOST, LIME }

    var kind: Kind = kind
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }

    /** Makes the icon in a size and a color: [glyph] when it is set, else the Ikonli icon. */
    private val iconMaker: ((Int, Color) -> Icon)? =
        glyph ?: ikon?.let { i -> { size: Int, color: Color -> UiKit.icon(i, size, color) } }
    private val icons = HashMap<Color, Icon>()

    private val textFont get() = if (kind == Kind.LIME) UiKit.font(12.5f, UiKit.Weight.BOLD) else UiKit.font(12.5f)

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        // Space must stay the play hotkey, so the buttons never keep the focus.
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private fun keysWidth(): Int =
        if (keys.isEmpty()) 0 else keys.sumOf { KeyChips.width(it, keyStyle) } + KEY_GAP * (keys.size - 1) + GAP

    private fun contentWidth(): Float =
        (if (iconMaker != null) ICON_SIZE + GAP else 0) + UiKit.textWidth(text, textFont) + keysWidth()

    override fun getPreferredSize() = Dimension(ceil(contentWidth()).toInt() + padding * 2, buttonHeight)
    override fun getMinimumSize() = Dimension(0, buttonHeight)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, buttonHeight)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ScoringUi.DISABLED_ALPHA)
            val hover = model.isRollover && isEnabled
            val (fill, border, fg) = when (kind) {
                Kind.SECONDARY -> Triple(
                    if (hover) Palette.RAISED_2 else Palette.RAISED,
                    if (hover) Palette.HOVER_LINE else Palette.LINE_2,
                    Palette.FG,
                )
                Kind.GHOST -> Triple(
                    if (hover) Palette.RAISED else null,
                    if (hover) Palette.LINE_2 else null,
                    if (hover) Palette.FG else Palette.FG_2,
                )
                Kind.LIME -> Triple(if (hover) Palette.LIME_HOVER else Palette.LIME, null, Palette.ON_LIME)
            }
            UiKit.paintBox(g2, 0, 0, width, height, 4, fill, border)
            var x = if (alignLeft) padding.toFloat() else (width - contentWidth()) / 2f
            if (iconMaker != null) {
                val iconColor = when (kind) {
                    Kind.LIME -> Palette.ON_LIME
                    else -> Palette.FG_2
                }
                val icon = icons.getOrPut(iconColor) { iconMaker(ICON_SIZE, iconColor) }
                icon.paintIcon(this, g2, x.toInt(), (height - icon.iconHeight) / 2)
                x += ICON_SIZE + GAP
            }
            val available = width - x - padding - keysWidth()
            val label = UiKit.ellipsize(text, textFont, available)
            UiKit.drawText(g2, label, textFont, fg, x, 0f, height.toFloat())
            x += UiKit.textWidth(label, textFont) + GAP
            val chipY = (height - keyStyle.height) / 2
            for (key in keys) {
                KeyChips.paint(g2, key, x.toInt(), chipY, keyStyle)
                x += KeyChips.width(key, keyStyle) + KEY_GAP
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val GAP = 6
        const val KEY_GAP = 3
        const val ICON_SIZE = 17
    }
}
