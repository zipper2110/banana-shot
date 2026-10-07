package org.litvin.export.scoreboard

import org.litvin.ScoreboardDisplay
import org.litvin.export.comments.CommentAss
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Scoreboard styles that are based on tour TV graphics and on scoreboard images. [ScoreboardLayouts] selects
 * the style. The mockups are `design/scoreboard-tour/index.html` (Next Gen, Violet) and
 * `design/scoreboard-tour/more.html` (Chalkboard, Sunset).
 */
internal object ScoreboardTourLayouts {
    /**
     * Next Gen: slanted black blocks, a slanted accent title tab with a magenta mark, magenta current games
     * and slanted point tiles. The leading player's tile has the accent color.
     */
    fun nextGen(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val black = 0x0A0A0A
        val tile = 0x26262C
        val magenta = 0xFF2D95
        val muted = 0x5E5E66
        val title = look.title?.uppercase(Locale.US)
        val rowH = 54.0
        val gap = 4.0
        // Each block leans by this value for each unit of height, so the left edges make one line.
        val lean = 0.22
        val nameSize = 25.0
        val setSize = 27.0
        val pointSize = 31.0
        val titleSize = 15.0
        val titleSpacing = 1.6
        val sliverW = if (look.playerColors) 12.0 else 0.0
        val padX = 14.0
        val setW = 46.0
        val pointW = 74.0
        val pointGap = 6.0
        val tileInset = 6.0
        val markGap = 6.0
        val markW = 10.0
        val columns = display.completedSets.size + 1

        val titleH = if (title != null) 30.0 else 0.0
        val top = if (title != null) titleH + gap else 0.0
        val serveW = serveColumnWidth(display, look, 24.0)
        val bodyW = sliverW + padX + nameWidth(rows, SEGOE_BLACK, nameSize, 130.0, bold = false) + 14.0 + serveW +
            columns * setW + pointGap + pointW
        val rowsBottom = top + 2 * rowH + gap
        val creditH = if (look.credit != null) 26.0 else 0.0
        val creditW = if (look.credit != null) 16.0 + ScoreboardFonts.textWidth(look.credit, SEGOE, false, 15.0, 0.8) + 16.0 else 0.0
        val height = if (look.credit != null) rowsBottom + gap + creditH else rowsBottom

        /** The x of the bottom left corner of a block that ends at [bottom]. */
        fun leftX(bottom: Double) = lean * (height - bottom)

        fun slanted(x: Double, y: Double, w: Double, h: Double, rgb: Int, opacity: Double = 1.0) = SceneItem.Polygon(
            listOf(ScenePoint(x + lean * h, y), ScenePoint(x + w + lean * h, y), ScenePoint(x + w, y + h), ScenePoint(x, y + h)),
            rgb,
            opacity,
        )

        val tabW = if (title != null) 14.0 + ScoreboardFonts.textWidth(title, SEGOE_BLACK, false, titleSize, titleSpacing) + 18.0 else 0.0
        val titleRight = if (title != null) leftX(titleH) + tabW + markGap + markW + lean * titleH else 0.0
        val width = max(leftX(top + rowH) + bodyW + lean * rowH, titleRight)
        val items = mutableListOf<SceneItem>()

        if (title != null) {
            val x = leftX(titleH)
            items += slanted(x, 0.0, tabW, titleH, look.accentRgb)
            items += slanted(x + tabW + markGap, 0.0, markW, titleH, magenta)
            items += centered(x + lean * titleH / 2 + 14.0, titleH / 2, title, SEGOE_BLACK, titleSize, inkOn(look.accentRgb, black), TextAnchor.MIDDLE_LEFT, bold = false, spacing = titleSpacing)
        }
        rows.forEachIndexed { index, row ->
            val y = top + index * (rowH + gap)
            val cy = y + rowH / 2
            val x = leftX(y + rowH)
            // The text of a row is centered on the slant at the middle of the row.
            val mid = x + lean * rowH / 2
            items += slanted(x, y, bodyW, rowH, black, look.opacity)
            if (look.playerColors) items += slanted(x, y, sliverW, rowH, row.rgb)
            items += centered(mid + sliverW + padX, cy, row.name, SEGOE_BLACK, nameSize, WHITE, TextAnchor.MIDDLE_LEFT, bold = false)
            val setsX = mid + bodyW - pointW - pointGap - columns * setW
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 12.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setW + setW / 2, cy, row, setIndex, SEGOE, setSize, WHITE, muted)
            }
            items += centered(setsX + row.sets.size * setW + setW / 2, cy, row.games.toString(), SEGOE, setSize, magenta, TextAnchor.CENTER)
            // The tile is inside the row block, with the same slant.
            val tileH = rowH - 2 * tileInset
            val tileW = pointW - tileInset
            val tileX = x + bodyW - pointW + lean * tileInset - tileInset / 2
            val tileRgb = if (row.leading) look.accentRgb else tile
            items += slanted(tileX, y + tileInset, tileW, tileH, tileRgb, if (row.leading) 1.0 else look.opacity)
            items += centered(
                tileX + lean * tileH / 2 + tileW / 2, cy, row.points, SEGOE_BLACK, pointSize,
                if (row.leading) inkOn(look.accentRgb, black) else WHITE, TextAnchor.CENTER,
                bold = false, opacity = if (row.trailing) 0.55 else 1.0,
            )
        }
        if (look.credit != null) {
            val y = rowsBottom + gap
            items += slanted(leftX(height), y, creditW, creditH, black, look.opacity)
            items += centered(leftX(height) + lean * creditH / 2 + 16.0, y + creditH / 2, look.credit, SEGOE, 15.0, WHITE, TextAnchor.MIDDLE_LEFT, bold = false, opacity = 0.6, spacing = 0.8)
        }
        return ScoreboardScene(width, height, items)
    }

    /**
     * Violet: a purple title bar with a magenta band, white rows, a lavender column for the current games
     * and the points in pills. The leading player's pill has the accent color.
     */
    fun violet(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val purple = 0x3D1A6E
        val magenta = 0xE0399A
        val ink = 0x22163A
        val lavender = 0xF1ECF8
        val rule = 0xE4DCEF
        val lost = 0xA89DB9
        val title = look.title?.uppercase(Locale.US)
        val radius = 10.0
        val rowH = 54.0
        val bandH = 4.0
        val nameSize = 25.0
        val setSize = 26.0
        val pointSize = 28.0
        val titleSize = 18.0
        val titleSpacing = 1.5
        val colorW = if (look.playerColors) 6.0 else 0.0
        val padX = 18.0
        val setColW = 48.0
        val pointColW = 84.0
        val pillW = 64.0
        val pillH = 38.0
        val columns = display.completedSets.size + 1

        val headerH = if (title != null) 40.0 else 0.0
        val top = if (title != null) headerH + bandH else 0.0
        val serveW = serveColumnWidth(display, look, 26.0)
        val contentW = colorW + padX + nameWidth(rows, SEGOE, nameSize, 140.0) + 16.0 + serveW + columns * setColW + pointColW
        val titleW = if (title != null) padX + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) + padX else 0.0
        val width = max(contentW, titleW)
        val rowsBottom = top + 2 * rowH
        val footerH = if (look.credit != null) 28.0 else 0.0
        val height = rowsBottom + footerH
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val gamesX = setsX + (columns - 1) * setColW
        // The rows have round corners only where they are the edge of the board.
        val rowsTopR = if (title != null) 0.0 else radius
        val rowsBottomR = if (look.credit != null) 0.0 else radius
        val items = mutableListOf<SceneItem>()

        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, width, headerH, purple, look.opacity, Corners(radius, radius, 0.0, 0.0))
            items += centered(padX, headerH / 2, title, SEGOE, titleSize, WHITE, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
            items += SceneItem.Box(0.0, headerH, width, bandH, magenta, 1.0)
        }
        items += SceneItem.Box(0.0, top, width, 2 * rowH, WHITE, look.opacity, Corners(rowsTopR, rowsTopR, rowsBottomR, rowsBottomR))
        items += SceneItem.Box(gamesX, top, setColW, 2 * rowH, lavender, look.opacity)
        items += SceneItem.Box(colorW, top + rowH - 0.5, width - colorW, 1.0, rule, 1.0)

        rows.forEachIndexed { index, row ->
            val rowTop = top + index * rowH
            val cy = rowTop + rowH / 2
            if (look.playerColors) {
                val corners = Corners(topLeft = if (index == 0) rowsTopR else 0.0, bottomLeft = if (index == 1) rowsBottomR else 0.0)
                items += SceneItem.Box(0.0, rowTop, colorW, rowH, row.rgb, 1.0, corners)
            }
            items += centered(colorW + padX, cy, row.name, SEGOE, nameSize, ink, TextAnchor.MIDDLE_LEFT)
            // A dark ring makes a light accent ball visible on the white rows.
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 15.0, look.accentRgb, ringRgb = ink)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, SEGOE, setSize, ink, lost)
            }
            items += centered(gamesX + setColW / 2, cy, row.games.toString(), SEGOE, setSize, ink, TextAnchor.CENTER)
            val pillRgb = if (row.leading) look.accentRgb else purple
            items += SceneItem.Box(pointX + (pointColW - pillW) / 2, cy - pillH / 2, pillW, pillH, pillRgb, 1.0, Corners.all(pillH / 2))
            items += centered(
                pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, inkOn(pillRgb, ink), TextAnchor.CENTER,
                opacity = if (row.trailing) 0.6 else 1.0,
            )
        }
        if (look.credit != null) {
            items += SceneItem.Box(0.0, rowsBottom, width, footerH, purple, look.opacity, Corners(0.0, 0.0, radius, radius))
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, SEGOE, 17.0, 0xC9B8E6, TextAnchor.CENTER, bold = false, spacing = 0.6)
        }
        return ScoreboardScene(width, height, items)
    }

    /**
     * Chalkboard: a slate in a wooden frame, hand-written text and chalk lines. A chalk circle in the accent color
     * marks the leading points. The title keeps its own letter case, as hand writing does.
     */
    fun chalkboard(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val slate = 0x26332E
        val wood = 0x7A4E2A
        val chalk = 0xEFEFE6
        val title = look.title
        val border = 7.0
        val padX = 16.0
        val rowH = 52.0
        val titleH = if (title != null) 44.0 else 0.0
        val footerH = if (look.credit != null) 28.0 else 0.0
        // Ink Free has a low line box, so the same libass size gives larger letters than Segoe UI.
        val nameSize = 21.0
        val numSize = 23.0
        val pointSize = 26.0
        val titleSize = 21.0
        val nameX = border + padX + if (look.playerColors) 28.0 else 0.0
        val setColW = 46.0
        val pointColW = 80.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 24.0)
        val contentW = nameX + nameWidth(rows, INK_FREE, nameSize, 140.0) + 16.0 + serveW + columns * setColW + pointColW + border
        val titleW = if (title != null) 2 * (border + padX) + ScoreboardFonts.textWidth(title, INK_FREE, true, titleSize) else 0.0
        val width = max(contentW, titleW)
        val top = border + if (title != null) titleH else 4.0
        val rowsBottom = top + 2 * rowH
        val height = rowsBottom + (if (look.credit != null) footerH else 4.0) + border
        val pointX = width - border - pointColW
        val setsX = pointX - columns * setColW
        val items = mutableListOf<SceneItem>()

        items += frame(0.0, 0.0, width, height, border, wood, look.opacity)
        items += SceneItem.Box(border, border, width - 2 * border, height - 2 * border, slate, look.opacity)
        if (title != null) {
            items += centered(border + padX, border + titleH / 2, title, INK_FREE, titleSize, chalk, TextAnchor.MIDDLE_LEFT)
            val titleTextW = ScoreboardFonts.textWidth(title, INK_FREE, true, titleSize)
            items += chalkLine(border + padX, border + titleH - 6.0, border + padX + titleTextW, border + titleH - 6.0, 2.0, chalk, 0.55, 3)
        }
        items += chalkLine(border + padX, top + rowH, width - border - padX, top + rowH, 2.0, chalk, 0.4, 9)
        items += chalkLine(pointX, top + 8.0, pointX, rowsBottom - 8.0, 2.0, chalk, 0.4, 17)

        rows.forEachIndexed { index, row ->
            val cy = top + index * rowH + rowH / 2
            // The player color is a short chalk stroke before the name.
            if (look.playerColors) {
                val x = border + padX
                items += SceneItem.Polyline(listOf(ScenePoint(x, cy + 1.0), ScenePoint(x + 8.0, cy - 1.0), ScenePoint(x + 16.0, cy + 1.0)), 6.0, row.rgb, 0.9)
            }
            items += centered(nameX, cy, row.name, INK_FREE, nameSize, chalk, TextAnchor.MIDDLE_LEFT, opacity = 0.95)
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 12.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, INK_FREE, numSize, chalk, chalk, lostOpacity = 0.45)
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), INK_FREE, numSize, chalk, TextAnchor.CENTER, opacity = 0.95)
            val pointCx = pointX + pointColW / 2
            items += centered(
                pointCx, cy, row.points, INK_FREE, pointSize, if (row.leading) look.accentRgb else chalk, TextAnchor.CENTER,
                opacity = if (row.trailing) 0.5 else 0.95,
            )
            if (row.leading) items += chalkCircle(pointCx, cy, 30.0, 19.0, look.accentRgb)
        }
        if (look.credit != null) {
            items += centered(width - border - padX, rowsBottom + footerH / 2, look.credit, INK_FREE, 13.0, chalk, TextAnchor.MIDDLE_RIGHT, bold = false, opacity = 0.6)
        }
        return ScoreboardScene(width, height, items)
    }

    /**
     * Sunset: a header of six color bands from orange to purple, plum rows and a warm pill for the leading points.
     * Without the title, a thin strip keeps the bands.
     */
    fun sunset(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val bands = listOf(0xFFB347, 0xFF8F4A, 0xFF6D5A, 0xEC5478, 0xC94490, 0x9B3C9E)
        val plum = 0x2A1533
        val cell = 0x3A1F45
        val lost = 0x8C6F93
        val peach = 0xFFE2C6
        val title = look.title?.uppercase(Locale.US)
        // The first band is as high as the corner radius, so the round corners stay inside it.
        val radius = 6.0
        val padX = 18.0
        val rowH = 52.0
        val headerH = if (title != null) 42.0 else 6.0
        val nameSize = 25.0
        val setSize = 26.0
        val pointSize = 29.0
        val titleSize = 18.0
        val titleSpacing = 2.0
        val colorW = if (look.playerColors) 5.0 else 0.0
        val setColW = 46.0
        val pointColW = 80.0
        val pillW = 60.0
        val pillH = 36.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 26.0)
        val contentW = colorW + padX + nameWidth(rows, SEGOE, nameSize, 140.0) + 16.0 + serveW + columns * setColW + pointColW
        val titleW = if (title != null) 2 * padX + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) else 0.0
        val width = max(contentW, titleW)
        val rowsBottom = headerH + 2 * rowH
        val footerH = if (look.credit != null) 26.0 else 0.0
        val height = rowsBottom + footerH
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val gamesX = setsX + (columns - 1) * setColW
        // The thin strip is too low for round corners, so the board without the title has sharp top corners.
        val topR = if (title != null) radius else 0.0
        val bottomR = if (look.credit != null) 0.0 else radius
        val items = mutableListOf<SceneItem>()

        val bandH = headerH / bands.size
        bands.forEachIndexed { index, rgb ->
            val corners = if (index == 0) Corners(topR, topR, 0.0, 0.0) else Corners.NONE
            // Each band overlaps the next band a little, so no gap shows between them.
            items += SceneItem.Box(0.0, index * bandH, width, if (index < bands.size - 1) bandH + 0.5 else bandH, rgb, 1.0, corners)
        }
        if (title != null) items += centered(padX, headerH / 2, title, SEGOE, titleSize, WHITE, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
        items += SceneItem.Box(0.0, headerH, width, 2 * rowH + footerH, plum, look.opacity, Corners(0.0, 0.0, radius, radius))
        items += SceneItem.Box(gamesX, headerH, setColW, 2 * rowH, cell, look.opacity)
        items += SceneItem.Box(colorW, headerH + rowH - 0.5, width - colorW, 1.0, WHITE, 0.08)

        rows.forEachIndexed { index, row ->
            val rowTop = headerH + index * rowH
            val cy = rowTop + rowH / 2
            if (look.playerColors) items += SceneItem.Box(0.0, rowTop, colorW, rowH, row.rgb, 1.0, Corners(bottomLeft = if (index == 1) bottomR else 0.0))
            items += centered(colorW + padX, cy, row.name, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 13.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, SEGOE, setSize, peach, lost)
            }
            items += centered(gamesX + setColW / 2, cy, row.games.toString(), SEGOE, setSize, WHITE, TextAnchor.CENTER)
            if (row.leading) {
                items += SceneItem.Box(pointX + (pointColW - pillW) / 2, cy - pillH / 2, pillW, pillH, look.accentRgb, 1.0, Corners.all(pillH / 2))
            }
            items += centered(
                pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, if (row.leading) inkOn(look.accentRgb, plum) else WHITE, TextAnchor.CENTER,
                opacity = if (row.trailing) 0.55 else 1.0,
            )
        }
        if (look.credit != null) {
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, SEGOE, 14.0, 0xC9A3C9, TextAnchor.CENTER, bold = false, spacing = 0.6)
        }
        return ScoreboardScene(width, height, items)
    }

    /** A fixed wobble for the chalk lines, so that each frame of the video draws the same lines. */
    private fun wobble(index: Int, amplitude: Double) = sin(index * 12.9898 + 4.1) * amplitude

    /** A hand-drawn horizontal or vertical chalk line. [seed] makes each line wobble differently. */
    private fun chalkLine(x1: Double, y1: Double, x2: Double, y2: Double, width: Double, rgb: Int, opacity: Double, seed: Int): SceneItem.Polyline {
        val steps = max(2, (hypot(x2 - x1, y2 - y1) / 14.0).roundToInt())
        val points = (0..steps).map { step ->
            val t = step.toDouble() / steps
            ScenePoint(
                x1 + (x2 - x1) * t + if (x1 == x2) wobble(seed + step, 1.2) else 0.0,
                y1 + (y2 - y1) * t + if (y1 == y2) wobble(seed + step, 1.2) else 0.0,
            )
        }
        return SceneItem.Polyline(points, width, rgb, opacity)
    }

    /** A hand-drawn ellipse around ([cx], [cy]). The ends overlap a little, as a chalk stroke does. */
    private fun chalkCircle(cx: Double, cy: Double, rx: Double, ry: Double, rgb: Int): SceneItem.Polyline {
        val points = (0..26).map { step ->
            val angle = -0.6 + step / 24.0 * 2 * PI
            ScenePoint(cx + cos(angle) * (rx + wobble(step, 1.5)), cy + sin(angle) * (ry + wobble(step + 7, 1.2)))
        }
        return SceneItem.Polyline(points, 2.5, rgb, 0.85)
    }

    /** [dark] on a light [background], white on a dark one. The user can set a light accent color. */
    private fun inkOn(background: Int, dark: Int): Int = if (CommentAss.isLight(background)) dark else WHITE
}
