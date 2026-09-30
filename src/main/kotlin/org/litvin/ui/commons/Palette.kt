package org.litvin.ui.commons

import java.awt.Color
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * The color scheme of the app. All UI code gets its colors from this object.
 * Do not write a color value in other UI files: add a token here, or use a token that has the same purpose.
 * ArchitectureDependencyHygieneTest finds color values outside this file.
 *
 * A token that has the same value as a different token refers to that token, for example [ROW_LINE] = [OVERLAY].
 * Thus a theme can change one purpose and keep the other.
 * The base values come from the shared.css files of the redesign folders in design/.
 *
 * Experimental: two seed colors can change at runtime, the [accent] and the [background]. The Test tab sets them.
 * A token that comes from a seed ("by surface", "by accentOf", ...) calculates its value again after [setSeeds].
 * The lime tokens have base values for [BASE_ACCENT]. With another accent seed, they follow the seed.
 * With the default background seed, each surface token has its base value. [Theme] changes the seeds and updates the open windows.
 */
internal object Palette {
    // ---- Seeds ----

    /** The accent that the base values of the lime tokens were made for: the lime of the redesign. */
    val BASE_ACCENT = Color(0xA1FE00)

    /** The default accent seed of the app. */
    val DEFAULT_ACCENT = Color(0xC8EC46)

    /** The default background seed: the base value of [BG]. */
    val DEFAULT_BACKGROUND = Color(0x0E0E0E)

    var accent: Color = DEFAULT_ACCENT
        private set

    var background: Color = DEFAULT_BACKGROUND
        private set

    /** Increases at each seed change. A token with an older generation calculates its value again. */
    @Volatile
    private var generation = 0

    private val seeded = mutableListOf<Token>()

    /** Sets the seed colors. The alpha of the seeds is ignored. */
    fun setSeeds(accent: Color, background: Color) {
        this.accent = Color(accent.rgb and 0xFFFFFF)
        this.background = Color(background.rgb and 0xFFFFFF)
        generation++
    }

    /** The current values of all tokens that come from a seed, always in the same order. */
    fun seededValues(): List<Color> = seeded.map { it.value() }

    // ---- Surfaces, from the darkest to the lightest ----

    /** The window, the tabs and the video area around the tables. */
    val BG by surface(0x0E0E0E)

    /** Areas below the surface: inputs, table heads, the sidebar, comment rows, the timeline gutter. */
    val INSET by surface(0x121212)

    /** Player rows of the score panel, layout tiles, group rows of the exports table. */
    val PANEL_DIM by surface(0x141414)

    /** Side panels and key chips. */
    val PANEL by surface(0x161616)

    /** The row under the pointer, the dialog footer and the active sidebar button. */
    val ROW_HOVER by surface(0x181818)

    val CARD by surface(0x1A1A1A)

    /** Dialogs, popups, the playback bar, and a tile or a button under the pointer. */
    val OVERLAY by surface(0x1C1C1C)

    /** Secondary buttons and other raised controls. */
    val RAISED by surface(0x202020)

    /** The selected row of a list. */
    val SELECTED by surface(0x242424)

    /** A raised control under the pointer, the selected segment of a segmented control, the sidebar button under the pointer. */
    val RAISED_2 by surface(0x2A2A2A)

    /** The raised control of the older screens (Test tab, transport bar). */
    val CONTROL by surface(0x303030)

    // ---- Lines and neutral marks, from the darkest to the lightest ----

    /** The line between two rows of a table. */
    val ROW_LINE by token { OVERLAY }

    /** The line between two slider rows of a group card. */
    val CARD_ROW_LINE by token { RAISED }

    /** Card borders and dividers. Also a placeholder fill (skeleton) and a quiet pill. */
    val LINE by surface(0x262626)

    /** Progress and bar tracks, hint lines and the step line of the workflow card. */
    val TRACK by surface(0x2C2C2C)

    /** Borders of controls and slider tracks. */
    val LINE_2 by surface(0x363636)

    /** Key chip borders, axis and tick lines, the track of a switch that is off, disabled fills. */
    val LINE_3 by surface(0x3A3A3A)

    /** The border of a control under the pointer. Also key chip borders and minor ticks. */
    val HOVER_LINE by surface(0x444444)

    /** Strong neutral marks: ticks, radio and check borders, the middle label of a slider. */
    val LINE_5 by surface(0x5A5A5A)

    /** A check border under the pointer and the "No point" fill. */
    val LINE_6 by surface(0x8A8A8A)

    // ---- Text ----

    val FG by text(0xE4E4E4)
    val FG_2 by text(0xADAAAA)
    val FG_3 by text(0x6A6A6A)

    /** The text with the most contrast: the leading value, and the text on a strong fill. */
    val FG_STRONG by text(0xFFFFFF)

    /** Light neutral marks: slider thumbs, the chart line, the "No point" color, the racket badge. */
    val NEUTRAL_LIGHT = Color(0xD8D8D8)

    /** Dark text on a light fill, for example on a light player color. */
    val ON_LIGHT = Color(0x111111)

    // ---- The lime accent ----

    /** The accent seed itself. */
    val LIME by token { accent }
    val LIME_HOVER by accentOf(0xB4FF33)
    val LIME_PRESSED by token { LIME.darker() }

    /** The light start of the lime gradient of the older primary buttons and the app mark. */
    val LIME_LIGHT by accentOf(0xDDFFB0)

    /** The dark start of the lime progress fill. */
    val LIME_DEEP by accentOf(0x7FCC00)

    /** Text and icons on a lime fill. */
    val ON_LIME by accentOf(0x142000)

    /** Quiet text on a lime fill, for example "SPACE" under the play icon. */
    val ON_LIME_MUTED by accentOf(0x4D6B16)

    /** Lime tints, from the weakest to the strongest. */
    val LIME_WASH by token { withAlpha(LIME, 10) }
    val LIME_TINT by token { withAlpha(LIME, 18) }
    val LIME_TINT_2 by token { withAlpha(LIME, 28) }
    val LIME_EDGE by token { withAlpha(LIME, 48) }
    val LIME_GLOW by token { withAlpha(LIME, 64) }
    val LIME_LINE by token { withAlpha(LIME, 153) }

    /** The selected text of an input. */
    val SELECTION by token { LIME_GLOW }

    /** A row or a tile with a lime state: a running export, the selected scoreboard layout. */
    val LIME_ROW by accentSurface(0x1D2116)
    val LIME_ROW_HOVER by accentSurface(0x20251A)

    /** The border of a checked option card. */
    val LIME_DIM_LINE by accentSurface(0x3D4A2A)

    val SAGE by accentOf(0xA3C586)
    val SAGE_TINT by token { withAlpha(SAGE, 26) }

    // ---- Status colors ----

    /** Favorites, warnings and notes. */
    val YELLOW = Color(0xF2D64B)
    val YELLOW_TINT = withAlpha(YELLOW, 20)
    val YELLOW_LINE = withAlpha(YELLOW, 89)
    val YELLOW_TEXT = Color(0xF3E2A4)

    /** Errors, failures and destructive actions. */
    val RED = Color(0xFF7351)
    val RED_TINT = withAlpha(RED, 26)
    val RED_LINE = withAlpha(RED, 102)
    val RED_LINE_2 = withAlpha(RED, 160)
    val RED_TEXT = Color(0xFFB4A3)

    /** The dark fill of a quiet destructive button. */
    val RED_FILL by surface(0x3A1812)
    val RED_FILL_HOVER by surface(0x4A1D15)

    /** The fill of a strong destructive button. */
    val RED_SOLID = Color(0xC93D1C)
    val RED_SOLID_HOVER = Color(0xE0492A)

    /** A failed row of the exports table. */
    val RED_ROW by surface(0x221613)

    /** Information. */
    val BLUE = Color(0x6FB5FF)
    val BLUE_TINT = withAlpha(BLUE, 31)

    /** Marked points on the timeline. */
    val GREEN = Color(0x4CAF50)

    // ---- Shades and highlights on any surface ----

    val SHADE = withAlpha(Color.BLACK, 40)
    val SHADE_2 = withAlpha(Color.BLACK, 89)
    val SCRIM = withAlpha(Color.BLACK, 150)
    val SCRIM_2 = withAlpha(Color.BLACK, 184)
    val HIGHLIGHT = withAlpha(Color.WHITE, 14)
    val HIGHLIGHT_2 = withAlpha(Color.WHITE, 31)
    val HIGHLIGHT_3 = withAlpha(Color.WHITE, 64)

    // ---- Video and timeline ----

    /** The area around the video frame. */
    val VIDEO_BG: Color = Color.BLACK

    /** The video range on the timeline. */
    val VIDEO_RANGE = withAlpha(BLUE, 140)

    val PLAYHEAD: Color = RED

    // ---- Scoring ----

    /** The racket of the serve button, and its fill and border. */
    val RACKET = Color(0xC2DB43)
    val SERVE_FILL by surface(0x3A3F24)
    val SERVE_LINE by surface(0x59622C)

    // ---- Pictures of a court (previews that stand in for a video frame) ----

    val COURT_GRASS = Color(0x3A6E46)
    val COURT_GRASS_DARK = Color(0x1D3F28)
    val COURT_SURROUND = Color(0x2E4B3A)
    val COURT_HARD = Color(0x3D6B8C)
    val COURT_LINE = withAlpha(Color.WHITE, 70)
    val COURT_LINE_2 = withAlpha(Color.WHITE, 140)

    /** The warm stop of the app mark gradient. */
    val BRAND_GOLD = Color(0xEDE450)

    // ---- Fixed colors for color math. A theme does not change them. ----

    val PURE_BLACK: Color = Color.BLACK
    val PURE_WHITE: Color = Color.WHITE
    val CLEAR = Color(0, 0, 0, 0)

    // ---- Seed math ----

    /** A token value that is calculated again after a seed change. */
    private class Token(private val compute: () -> Color) : ReadOnlyProperty<Any?, Color> {
        private var cached: Color? = null
        private var cachedGeneration = -1

        fun value(): Color {
            val current = generation
            val known = cached
            if (known != null && cachedGeneration == current) return known
            return compute().also {
                cached = it
                cachedGeneration = current
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): Color = value()
    }

    private fun token(compute: () -> Color) = Token(compute).also { seeded += it }

    /** A surface, a line or a dark tinted fill. It follows the background seed. */
    private fun surface(rgb: Int) = token { shiftSurface(Color(rgb)) }

    /** A text color. It is inverted on a light background. */
    private fun text(rgb: Int) = token { if (isLight(background)) invert(Color(rgb)) else Color(rgb) }

    /** An accent color. It follows the hue, the saturation and the brightness of the accent seed. */
    private fun accentOf(rgb: Int) = token { shiftAccent(Color(rgb), keepBrightness = false) }

    /** A surface with an accent tint. The brightness follows the background seed. The tint follows the accent seed. */
    private fun accentSurface(rgb: Int) = token { shiftAccent(shiftSurface(Color(rgb)), keepBrightness = true) }

    /**
     * Moves [color] from the default background to the background seed. The distance from the default background stays
     * the same. On a light background the gray part of the distance is negative, so that the steps go darker.
     */
    private fun shiftSurface(color: Color): Color {
        val seed = background
        if (seed == DEFAULT_BACKGROUND) return color
        val base = DEFAULT_BACKGROUND
        val dr = color.red - base.red
        val dg = color.green - base.green
        val db = color.blue - base.blue
        val gray = (dr + dg + db) / 3
        val step = if (isLight(seed)) -gray else gray
        return Color(
            (seed.red + step + dr - gray).coerceIn(0, 255),
            (seed.green + step + dg - gray).coerceIn(0, 255),
            (seed.blue + step + db - gray).coerceIn(0, 255),
        )
    }

    /** Turns the hue of [color] by the hue difference between the base accent and the accent seed. Scales the saturation. */
    private fun shiftAccent(color: Color, keepBrightness: Boolean): Color {
        val seed = accent
        if (seed == BASE_ACCENT) return color
        val from = Color.RGBtoHSB(BASE_ACCENT.red, BASE_ACCENT.green, BASE_ACCENT.blue, null)
        val to = Color.RGBtoHSB(seed.red, seed.green, seed.blue, null)
        val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
        val hue = hsb[0] + to[0] - from[0]
        val saturation = (hsb[1] * to[1] / from[1]).coerceIn(0f, 1f)
        val brightness = if (keepBrightness) hsb[2] else (hsb[2] * to[2] / from[2]).coerceIn(0f, 1f)
        return Color(Color.HSBtoRGB(hue, saturation, brightness) and 0xFFFFFF)
    }

    private fun isLight(color: Color) = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue > 128

    private fun invert(color: Color) = Color(255 - color.red, 255 - color.green, 255 - color.blue)

    /** [color] with the opacity [alpha] (0 to 255). */
    fun withAlpha(color: Color, alpha: Int): Color = Color(color.red, color.green, color.blue, alpha.coerceIn(0, 255))

    /** [color] as "#RRGGBB", or as "#RRGGBBAA" when it is not opaque. For FlatLaf style strings. */
    fun hex(color: Color): String =
        if (color.alpha == 255) String.format("#%06X", color.rgb and 0xFFFFFF)
        else String.format("#%06X%02X", color.rgb and 0xFFFFFF, color.alpha)
}
