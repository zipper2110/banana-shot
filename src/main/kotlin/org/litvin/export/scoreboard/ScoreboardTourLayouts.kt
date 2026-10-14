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
 * `design/scoreboard-tour/more.html` (Chalkboard, Sunset). Stadium and Stadium Compact come from screenshots of tour TV graphics.
 */
internal object ScoreboardTourLayouts {
    /** The color of the set winners and of the serve dot on the Stadium boards. The accent color is the blue. */
    const val STADIUM_YELLOW = 0xE4F03C

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

    /**
     * Padel Glass (see padel.md): a padel-blue board in a light glass frame, as the glass walls of a padel
     * court. A frosted glass band at the top, white court lines, a frosted point column and an accent tile for the
     * leading points.
     */
    fun padelGlass(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        // Darker than the court blue (Palette.COURT_PADEL), so that the board stands out on a padel video.
        val blue = 0x143A6B
        val deep = 0x0D2A50
        val glass = 0xD6EBFF
        val lost = 0x9DB8DA
        val title = look.title?.uppercase(Locale.US)
        val edge = 2.0
        val padX = 18.0
        val glassH = if (title != null) 42.0 else 8.0
        val rowH = 54.0
        val nameSize = 25.0
        val setSize = 26.0
        val pointSize = 31.0
        val titleSize = 18.0
        val titleSpacing = 2.0
        val nameX = if (look.playerColors) padX + 12.0 + 12.0 else padX
        val setColW = 48.0
        val pointColW = 82.0
        val tileInset = 7.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 26.0)
        val contentW = nameX + nameWidth(rows, SEGOE, nameSize, 140.0) + 18.0 + serveW + columns * setColW + pointColW + edge
        val titleW = if (title != null) 2 * padX + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) else 0.0
        val width = max(contentW, titleW)
        val rowsBottom = glassH + 2 * rowH
        val footerH = if (look.credit != null) 28.0 else 0.0
        val height = rowsBottom + footerH + edge
        val pointX = width - edge - pointColW
        val setsX = pointX - columns * setColW
        val gamesX = setsX + (columns - 1) * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, blue, look.opacity)
        // The frosted glass band. Without a title, it is a thin strip.
        items += SceneItem.Box(0.0, 0.0, width, glassH, glass, 0.22)
        items += SceneItem.Box(0.0, glassH - 1.0, width, 1.0, glass, 0.6)
        if (title != null) items += centered(padX, glassH / 2, title, SEGOE, titleSize, WHITE, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
        items += SceneItem.Box(gamesX, glassH, setColW, 2 * rowH, deep, look.opacity * 0.6)
        items += SceneItem.Box(pointX, glassH, pointColW, 2 * rowH, glass, 0.16)
        // White court lines: between the rows and before the point column.
        items += SceneItem.Box(edge, glassH + rowH - 1.0, width - 2 * edge, 2.0, WHITE, 0.8)
        items += SceneItem.Box(pointX - 1.0, glassH, 2.0, 2 * rowH, WHITE, 0.8)

        rows.forEachIndexed { index, row ->
            val cy = glassH + index * rowH + rowH / 2
            if (look.playerColors) items += SceneItem.Box(padX, cy - 6.0, 12.0, 12.0, row.rgb, 1.0, Corners.all(2.0))
            items += centered(nameX, cy, row.name, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 13.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, SEGOE, setSize, WHITE, lost)
            }
            items += centered(gamesX + setColW / 2, cy, row.games.toString(), SEGOE, setSize, WHITE, TextAnchor.CENTER)
            if (row.leading) {
                val tileH = rowH - 2 * tileInset
                items += SceneItem.Box(pointX + tileInset, cy - tileH / 2, pointColW - 2 * tileInset, tileH, look.accentRgb, 1.0, Corners.all(4.0))
            }
            items += centered(
                pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, if (row.leading) inkOn(look.accentRgb, deep) else WHITE, TextAnchor.CENTER,
                opacity = if (row.trailing) 0.6 else 1.0,
            )
        }
        if (look.credit != null) {
            items += SceneItem.Box(0.0, rowsBottom, width, footerH, glass, 0.12)
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, SEGOE, 15.0, glass, TextAnchor.CENTER, bold = false, spacing = 0.6)
        }
        // The glass frame around the board.
        items += frame(0.0, 0.0, width, height, edge, glass, 0.85)
        return ScoreboardScene(width, height, items)
    }

    /**
     * Stadium: the full board of tour TV graphics. A dark body in a thin accent frame, an accent title bar,
     * the current games in an accent column, and yellow games for the set winner. The first name is regular
     * and the last name is bold. The player color is a flag after the name, and a yellow dot before the name
     * marks the server. A "|" in the title starts a second part in regular text, for example "Round 4 | Stadium court".
     */
    fun stadium(display: ScoreboardDisplay, look: Look): ScoreboardScene = stadiumBoard(display, look, compact = false)

    /**
     * Stadium Compact: the small board of the same TV graphics. No frame, bold names, and smaller rows.
     * The title is an accent tab above the board.
     */
    fun stadiumCompact(display: ScoreboardDisplay, look: Look): ScoreboardScene = stadiumBoard(display, look, compact = true)

    /** The size of the credit line on the full Stadium board. */
    private const val CREDIT_SIZE = 16.0

    private fun stadiumBoard(display: ScoreboardDisplay, look: Look, compact: Boolean): ScoreboardScene {
        val rows = rows(display, look)
        val body = 0x0A0C1C
        val yellow = STADIUM_YELLOW
        val lost = 0xA3A7B4
        val titleParts = look.title?.uppercase(Locale.US)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val titleMain = titleParts.firstOrNull()
        val titleSub = titleParts.drop(1).joinToString(" ").ifEmpty { null }
        val radius = 6.0
        // The full board has an accent frame. With the title, the title bar is the top of the frame.
        val border = if (compact) 0.0 else 3.0
        val headerH = when {
            titleMain == null -> border
            compact -> 32.0
            else -> 50.0
        }
        val padX = if (compact) 14.0 else 18.0
        val rowH = if (compact) 46.0 else 54.0
        val nameSize = if (compact) 24.0 else 26.0
        val numSize = if (compact) 25.0 else 28.0
        val tiebreakSize = numSize * 0.55
        val titleSize = if (compact) 17.0 else 23.0
        val titleSpacing = 0.6
        val flagW = if (compact) 24.0 else 30.0
        val flagH = if (compact) 16.0 else 20.0
        val flagSpace = if (look.playerColors) 12.0 + flagW else 0.0
        val setColW = if (compact) 44.0 else 50.0
        val gamesW = if (compact) 46.0 else 52.0
        val pointColW = if (compact) 62.0 else 72.0
        val ruleGap = 8.0

        /**
         * The name in two parts: the first name in regular text and the last name in bold. Both boards show the complete
         * name; only the text weight changes. The compact board shows all of the name in bold. A team name such as
         * "LEE / KIM" and a default name such as "TEAM 1" stay in bold too.
         */
        fun nameParts(name: String): Pair<String?, String> {
            val split = name.lastIndexOf(' ')
            if (compact || split <= 0 || name.any { it == '/' || it == '&' || it == '+' }) return null to name
            val last = name.substring(split + 1)
            if (last.none { it.isLetter() }) return null to name
            return name.substring(0, split).trimEnd() to last
        }

        fun nameWidth(name: String): Double {
            val (first, last) = nameParts(name)
            val firstW = if (first != null) ScoreboardFonts.textWidth("$first ", SEGOE, false, nameSize) else 0.0
            return firstW + ScoreboardFonts.textWidth(last, SEGOE, true, nameSize)
        }

        fun titleWidth(main: String, sub: String?): Double {
            val subW = if (sub != null) 26.0 + ScoreboardFonts.textWidth(sub, SEGOE, false, titleSize, titleSpacing) else 0.0
            return ScoreboardFonts.textWidth(main, SEGOE, true, titleSize, titleSpacing) + subW
        }

        val serveW = serveColumnWidth(display, look, 22.0)
        val nameStart = border + padX
        val nameX = nameStart + serveW
        val namesW = max(if (compact) 110.0 else 150.0, rows.maxOf { nameWidth(it.name) })
        val count = display.completedSets.size
        val contentW = nameX + namesW + flagSpace + 22.0 + count * setColW + gamesW + pointColW + border
        val titleW = if (titleMain != null) 2 * padX + titleWidth(titleMain, titleSub) else 0.0
        // The compact title is a tab, so it can be narrower than the board. The full title bar is as wide as the board.
        val width = if (compact) contentW else max(contentW, titleW)
        val tabW = if (titleMain != null && compact) minOf(width, titleW) else width
        val top = headerH
        val rowsBottom = top + 2 * rowH
        val footerH = if (look.credit != null) (if (compact) 26.0 else 30.0) else 0.0
        // The frame closes under the rows. The credit line is below the frame.
        val frameBottom = rowsBottom + border
        val height = frameBottom + footerH
        val pointX = width - border - pointColW
        val gamesX = pointX - gamesW
        val setsX = gamesX - count * setColW
        val items = mutableListOf<SceneItem>()

        // The full board has a credit strip under the frame, at the left edge and half as wide as the board.
        // The compact board has the credit line across the full width.
        val creditStripW = if (look.credit != null && !compact) {
            val creditTextW = ScoreboardFonts.textWidth(look.credit, SEGOE, false, CREDIT_SIZE, 0.6)
            minOf(width, max(width / 2, creditTextW + 2 * padX))
        } else {
            width
        }
        val titleInk = inkOn(look.accentRgb, body)
        if (compact) {
            if (titleMain != null) items += SceneItem.Box(0.0, 0.0, tabW, headerH, look.accentRgb, 1.0, Corners(radius, radius, 0.0, 0.0))
            val topLeftR = if (titleMain != null) 0.0 else radius
            val topRightR = if (titleMain != null && tabW >= width) 0.0 else radius
            items += SceneItem.Box(0.0, top, width, height - top, body, look.opacity, Corners(topLeftR, topRightR, radius, radius))
        } else {
            // The accent title bar (or the top of the frame), the frame and the dark body.
            val frameBottomR = if (look.credit != null) 0.0 else border
            items += SceneItem.Box(0.0, 0.0, width, headerH, look.accentRgb, 1.0, Corners(radius, radius, 0.0, 0.0))
            items += SceneItem.Box(0.0, headerH, border, rowsBottom - headerH, look.accentRgb, 1.0)
            items += SceneItem.Box(width - border, headerH, border, rowsBottom - headerH, look.accentRgb, 1.0)
            items += SceneItem.Box(0.0, rowsBottom, width, border, look.accentRgb, 1.0, Corners(0.0, 0.0, frameBottomR, frameBottomR))
            items += SceneItem.Box(border, headerH, width - 2 * border, rowsBottom - headerH, body, look.opacity)
            if (look.credit != null) {
                items += SceneItem.Box(0.0, frameBottom, creditStripW, footerH, body, look.opacity, Corners(0.0, 0.0, radius, radius))
            }
        }
        if (titleMain != null) {
            items += centered(padX, headerH / 2, titleMain, SEGOE, titleSize, titleInk, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
            if (titleSub != null) {
                val dividerX = padX + ScoreboardFonts.textWidth(titleMain, SEGOE, true, titleSize, titleSpacing) + 12.0
                items += SceneItem.Box(dividerX, headerH / 2 - titleSize / 2, 2.0, titleSize, titleInk, 0.7)
                items += centered(dividerX + 14.0, headerH / 2, titleSub, SEGOE, titleSize, titleInk, TextAnchor.MIDDLE_LEFT, bold = false, spacing = titleSpacing)
            }
        }
        // The point column at the right edge has the round corners of the board when there is no frame.
        val pointTopR = if (compact && (titleMain == null || tabW < width)) radius else 0.0
        val pointBottomR = if (compact && look.credit == null) radius else 0.0
        items += SceneItem.Box(pointX, top, pointColW, 2 * rowH, WHITE, 0.05, Corners(0.0, pointTopR, pointBottomR, 0.0))
        items += SceneItem.Box(pointX, top + rowH - 0.5, pointColW, 1.0, WHITE, 0.14)
        items += SceneItem.Box(gamesX, top, gamesW, 2 * rowH, look.accentRgb, 1.0)
        items += SceneItem.Box(gamesX, top + rowH - 1.0, gamesW, 2.0, body, 0.45)
        // The rule between the rows is in two parts: under the names and under the completed sets.
        items += SceneItem.Box(nameStart, top + rowH - 0.5, nameX + namesW + flagSpace - nameStart, 1.0, WHITE, 0.28)
        if (count > 0) items += SceneItem.Box(setsX + ruleGap, top + rowH - 0.5, count * setColW - 2 * ruleGap, 1.0, WHITE, 0.28)

        rows.forEachIndexed { index, row ->
            val cy = top + index * rowH + rowH / 2
            if (row.serving) items += serveBall(nameStart + serveW / 2, cy, 10.0, yellow)
            val (first, last) = nameParts(row.name)
            var x = nameX
            if (first != null) {
                items += centered(x, cy, first, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT, bold = false)
                x += ScoreboardFonts.textWidth("$first ", SEGOE, false, nameSize)
            }
            items += centered(x, cy, last, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            if (look.playerColors) {
                val flagX = x + ScoreboardFonts.textWidth(last, SEGOE, true, nameSize) + 12.0
                items += SceneItem.Box(flagX, cy - flagH / 2, flagW, flagH, row.rgb, 1.0, Corners.all(2.0))
            }
            row.sets.indices.forEach { setIndex ->
                val setCx = setsX + setIndex * setColW + setColW / 2
                items += setGames(setCx, cy, row, setIndex, SEGOE, numSize, yellow, lost)
                // The tiebreak points of the set loser are small, at the top right of the games: 6⁶.
                val tiebreak = row.lostTiebreaks.getOrNull(setIndex) ?: return@forEach
                val gamesHalfW = ScoreboardFonts.textWidth(row.sets[setIndex].toString(), SEGOE, false, numSize) / 2
                val capTop = cy - ScoreboardFonts.capHeight(SEGOE, false, numSize) / 2
                val tiebreakCy = capTop + ScoreboardFonts.capHeight(SEGOE, false, tiebreakSize) / 2 - 2.0
                items += centered(setCx + gamesHalfW + 1.0, tiebreakCy, tiebreak.toString(), SEGOE, tiebreakSize, lost, TextAnchor.MIDDLE_LEFT, bold = false)
            }
            items += centered(gamesX + gamesW / 2, cy, row.games.toString(), SEGOE, numSize, inkOn(look.accentRgb, body), TextAnchor.CENTER)
            items += centered(pointX + pointColW / 2, cy, row.points, SEGOE, numSize, WHITE, TextAnchor.CENTER)
        }
        if (look.credit != null) {
            // The full board has the frame above the credit line, so only the compact board needs a rule.
            if (compact) items += SceneItem.Box(0.0, rowsBottom - 0.5, width, 1.0, WHITE, 0.12)
            items += centered(creditStripW / 2, frameBottom + footerH / 2, look.credit, SEGOE, if (compact) 14.0 else CREDIT_SIZE, lost, TextAnchor.CENTER, bold = false, spacing = 0.6)
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
