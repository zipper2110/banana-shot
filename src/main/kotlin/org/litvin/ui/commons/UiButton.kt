package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import javax.swing.Icon
import javax.swing.JButton
import kotlin.math.ceil

/**
 * A text button of the dialogs and the tab bars, the `.btn` element of the designs.
 * [Kind.SECONDARY] is the dark default. [Kind.LIME] is the main action of a dialog.
 * [Kind.DANGER] deletes or cancels work. [Kind.GHOST] has no box until the user moves the pointer on it.
 * [Kind.QUIET] has only a thin border, for a less important action such as "Reset to default".
 */
internal class UiButton(
    text: String,
    private val ikon: Ikon? = null,
    private val kind: Kind = Kind.SECONDARY,
    private val buttonHeight: Int = HEIGHT,
    private val fontSize: Float = 12.5f,
) : JButton(text) {
    enum class Kind { SECONDARY, LIME, DANGER, GHOST, QUIET }

    private val textFont
        get() = when (kind) {
            Kind.LIME -> UiKit.font(fontSize, UiKit.Weight.BOLD)
            Kind.DANGER -> UiKit.font(fontSize, UiKit.Weight.SEMIBOLD)
            else -> UiKit.font(fontSize)
        }

    private var cachedIcon: Pair<Color, Icon>? = null

    /** A color square in front of the text, for a color button. Null shows no square. */
    var swatch: Color? = null
        set(value) {
            field = value
            revalidate()
            repaint()
        }

    /** A short text after the main text in a monospace font, for example the hex code of a color. */
    var detail: String? = null
        set(value) {
            field = value
            revalidate()
            repaint()
        }

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

    private fun iconFor(color: Color): Icon? {
        val source = ikon ?: return null
        cachedIcon?.let { (c, icon) -> if (c == color) return icon }
        return UiKit.icon(source, ICON_SIZE, color).also { cachedIcon = color to it }
    }

    private fun leadingWidth() = when {
        swatch != null -> SWATCH + SWATCH_GAP
        ikon != null -> ICON_SIZE + if (text.isNullOrEmpty()) 0 else GAP
        else -> 0
    }

    private fun detailWidth(): Float = detail?.let { DETAIL_GAP + UiKit.textWidth(it, DETAIL_FONT) } ?: 0f

    private fun contentWidth(): Float = leadingWidth() + UiKit.textWidth(text.orEmpty(), textFont) + detailWidth()

    override fun getPreferredSize(): Dimension {
        val padding = if (buttonHeight < HEIGHT) 10 else 12
        return Dimension(ceil(contentWidth()).toInt() + padding * 2 + 2, buttonHeight)
    }

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = if (stretch) Dimension(Int.MAX_VALUE, buttonHeight) else preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover && isEnabled
            val fill: Color?
            val border: Color?
            val fg: Color
            val iconColor: Color
            when (kind) {
                Kind.SECONDARY -> {
                    fill = if (hover) Palette.RAISED_2 else Palette.RAISED
                    border = if (hover) Palette.HOVER_LINE else Palette.LINE_2
                    fg = Palette.FG
                    iconColor = Palette.FG_2
                }
                Kind.LIME -> {
                    fill = if (hover) Palette.LIME_HOVER else Palette.LIME_FILL
                    border = null
                    fg = Palette.ON_LIME
                    iconColor = Palette.ON_LIME
                }
                Kind.DANGER -> {
                    fill = if (hover) Palette.RED_FILL_HOVER else Palette.RED_FILL
                    border = if (hover) Palette.RED else Palette.RED_LINE_2
                    fg = if (hover) Palette.FG_STRONG else Palette.RED_TEXT
                    iconColor = fg
                }
                Kind.GHOST -> {
                    fill = if (hover) Palette.RAISED else null
                    border = if (hover) Palette.LINE_2 else null
                    fg = if (hover) Palette.FG else Palette.FG_2
                    iconColor = fg
                }
                Kind.QUIET -> {
                    fill = if (hover) Palette.RAISED else null
                    border = Palette.LINE
                    fg = if (hover) Palette.FG else Palette.FG_2
                    iconColor = fg
                }
            }
            val alpha = if (isEnabled) 1f else 0.35f
            if (alpha < 1f) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            UiKit.paintBox(g2, 0, 0, width, height, 4, fill, border)
            var x = (width - contentWidth()) / 2f
            val color = swatch
            val icon = iconFor(iconColor)
            if (color != null) {
                UiKit.paintBox(g2, x.toInt(), (height - SWATCH) / 2, SWATCH, SWATCH, 3, color, Palette.HIGHLIGHT_3)
                x += SWATCH + SWATCH_GAP
            } else if (icon != null) {
                icon.paintIcon(this, g2, x.toInt(), (height - icon.iconHeight) / 2)
                x += ICON_SIZE + GAP
            }
            UiKit.drawText(g2, text.orEmpty(), textFont, fg, x, 0f, height.toFloat())
            detail?.let {
                x += UiKit.textWidth(text.orEmpty(), textFont) + DETAIL_GAP
                UiKit.drawText(g2, it, DETAIL_FONT, Palette.FG_2, x, 0f, height.toFloat())
            }
            if (isFocusOwner) {
                g2.composite = AlphaComposite.SrcOver
                UiKit.paintBox(g2, 0, 0, width, height, 4, null, Palette.LIME_LINE)
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        /** The height of a dialog button. */
        const val HEIGHT = 30

        /** The height of a small button, for example in a card of the More window. */
        const val SMALL_HEIGHT = 26

        private const val GAP = 6
        private const val ICON_SIZE = 17
        private const val SWATCH = 16
        private const val SWATCH_GAP = 8
        private const val DETAIL_GAP = 8
        private val DETAIL_FONT get() = MonoFont.of(12f)
    }
}

/** The monospace font of paths and color codes: Consolas when the PC has it. */
internal object MonoFont {
    private val fonts = HashMap<Float, Font>()

    private val family: String by lazy {
        try {
            val names = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
            listOf("Consolas", "Cascadia Mono").firstOrNull { it in names } ?: Font.MONOSPACED
        } catch (_: Throwable) {
            Font.MONOSPACED
        }
    }

    fun of(size: Float): Font = fonts.getOrPut(size) { Font(family, Font.PLAIN, 1).deriveFont(size) }
}
