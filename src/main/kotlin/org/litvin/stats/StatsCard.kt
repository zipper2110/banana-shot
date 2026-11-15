package org.litvin.stats

import org.litvin.export.scoreboard.Corners
import org.litvin.export.scoreboard.INTER
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScenePoint
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.export.scoreboard.TextAnchor
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/**
 * The content of a statistics card: who played, the score, and the rows that the card shows.
 * [rows] holds only rows that have a value.
 */
data class StatsCardContent(
    val names: PerPlayer<String>,
    val colors: PerPlayer<Int>,
    val title: String,
    val score: String,
    val rows: List<StatRow>,
    /** The line at the bottom of the card, or null when the card has no such line. */
    val credit: String? = null,
    /** The chart on the last page, or null when the card has no chart page. */
    val momentum: Momentum? = null,
    /** The opacity of the card panel, from 0 to 1. */
    val panelOpacity: Double = 1.0 - StatsSettingsV1.DEFAULT_CARD_TRANSPARENCY_PERCENT / 100.0,
    /** True when the card uses the smaller rows of [StatsCard.COMPACT_ROWS_PER_PAGE]. */
    val compact: Boolean = false,
    /** True when the card shows the title of each group above its rows. */
    val groupTitles: Boolean = false,
    /** The look of the rows. */
    val style: CardStyle = CardStyle.CLASSIC,
    /** True for dark text on a light panel. False for light text on a dark panel. */
    val light: Boolean = false,
    /** The player colors for the light card. A light player color becomes darker, so that it shows on the light panel. */
    val lightColors: PerPlayer<Int> = PerPlayer(StatsCard.onLight(colors.p1), StatsCard.onLight(colors.p2)),
) {
    /** The player colors on the card: [colors] on the dark card, or [lightColors] on the light card. */
    val cardColors: PerPlayer<Int> get() = if (light) lightColors else colors

    internal val palette: CardPalette get() = if (light) CardPalette.LIGHT else CardPalette.DARK
}

/**
 * Draws the statistics card of the exported video, as one [ScoreboardScene] for each page.
 *
 * The scene covers the full video frame. Its units are pixels of a 1080p frame: the scene is 1080 units high,
 * and its width follows the frame aspect ratio. The export draws the pages with libass, and the Stats tab
 * draws the same pages with Java2D.
 */
object StatsCard {
    const val HEIGHT = 1080.0
    const val WIDTH_16_9 = 1920.0
    const val ROWS_PER_PAGE = 7
    /** The rows on a page of the compact card. */
    const val COMPACT_ROWS_PER_PAGE = 12

    /** The neutral bar colors, when the scoreboard does not show the player colors. */
    const val NEUTRAL_P1 = 0xF2F2F2
    const val NEUTRAL_P2 = 0x8A8A8A
    /** The neutral bar colors on the light card. */
    const val NEUTRAL_LIGHT_P1 = 0x2B2B2B
    const val NEUTRAL_LIGHT_P2 = 0x8C8C8C

    /**
     * Makes the card content for the full match ([scope] 0) or for one set ([scope] 1 and more).
     * The rows are the rows that [settings] selects for the video and that have a value.
     * The card makes dark player colors lighter, so that they show on the dark panel.
     * For the light card, it makes light player colors darker.
     */
    fun content(report: StatsReport, settings: StatsSettingsV1, scope: Int = 0): StatsCardContent {
        val score = report.score
        val scoreboard = score.scoreboard.normalized()
        val stats = if (scope == 0) report.match else report.sets[scope - 1]
        val setScores = report.setScores()
        val raw = PerPlayer(rgb(score.player1ColorHex, NEUTRAL_P1), rgb(score.player2ColorHex, NEUTRAL_P2))
        val colors = if (scoreboard.showPlayerColors) PerPlayer(onPanel(raw.p1), onPanel(raw.p2)) else PerPlayer(NEUTRAL_P1, NEUTRAL_P2)
        val lightColors = if (scoreboard.showPlayerColors) PerPlayer(onLight(raw.p1), onLight(raw.p2))
        else PerPlayer(NEUTRAL_LIGHT_P1, NEUTRAL_LIGHT_P2)
        return StatsCardContent(
            names = PerPlayer(
                score.sport.videoSideName(score.player1Name, 1).trim(),
                score.sport.videoSideName(score.player2Name, 2).trim(),
            ),
            colors = colors,
            title = if (scope == 0) tr("stats.card.match") else tr("stats.card.set", "number" to scope),
            score = if (scope == 0) setScores.joinToString("   ") else setScores.getOrElse(scope - 1) { "" },
            rows = StatRows.build(stats, score.rules.normalized(), settings).filter { it.available && settings.inVideo(it.stat) },
            credit = ScoreboardSettingsV1.APP_CREDIT.takeIf { scoreboard.showAppCredit },
            momentum = Momentum.of(report, scope).takeIf { settings.videoMomentum && it.points.size >= MIN_CHART_POINTS },
            panelOpacity = 1.0 - settings.normalized().cardTransparencyPercent / 100.0,
            compact = settings.compactCard,
            groupTitles = settings.cardGroupTitles,
            style = CardStyle.ofKey(settings.cardStyle),
            light = settings.lightCard,
            lightColors = lightColors,
        )
    }

    /**
     * The pages of the card, and then the momentum chart. A page shows at most [ROWS_PER_PAGE] rows,
     * or [COMPACT_ROWS_PER_PAGE] rows on a compact card. Group titles use some of this space.
     * A card without rows and without a chart has no pages.
     */
    fun pages(content: StatsCardContent, frameWidth: Double = WIDTH_16_9): List<ScoreboardScene> {
        val layout = RowLayout.of(content.compact)
        val chunks = splitRows(content.rows, layout, content.groupTitles)
        val chart = content.momentum
        val count = chunks.size + if (chart != null) 1 else 0
        // All pages have the same height, so the card does not change size between pages.
        // The chart needs the height of some rows.
        val mostHeight = chunks.maxOfOrNull { layout.height(it, content.groupTitles) } ?: 0.0
        val bodyHeight = if (chart != null) maxOf(mostHeight, CHART_SLOTS * NORMAL_ROW_HEIGHT) else mostHeight
        val pages = chunks.mapIndexed { index, rows ->
            page(content, bodyHeight, index, count, frameWidth) { area -> StatsCardRows.items(content, layout, rows, area) }
        }
        return if (chart == null) pages else pages + page(content, bodyHeight, chunks.size, count, frameWidth) { area ->
            chartItems(content, chart, area)
        }
    }

    /**
     * Splits [rows] into pages. Each page fits in the body height of [layout].
     * The split uses the smallest number of pages. When possible, the pages break only between groups,
     * so that a group stays on one page. Of these splits, the split with the smallest largest page wins.
     * When no such split is possible, the pages share the rows equally, for example 8 rows into 4 + 4 and not 7 + 1.
     * When two splits are equal, the first pages get more rows.
     * With [groupTitles], a page shows the title of each group that it has, so that the title uses some space.
     */
    internal fun splitRows(rows: List<StatRow>, layout: RowLayout = RowLayout.NORMAL, groupTitles: Boolean = false): List<List<StatRow>> {
        if (rows.isEmpty()) return emptyList()
        fun height(start: Int, end: Int) = layout.height(rows.subList(start, end), groupTitles)
        // The smallest number of pages: fill each page before the next page starts.
        var pageCount = 0
        var start = 0
        while (start < rows.size) {
            var end = start + 1
            while (end < rows.size && height(start, end + 1) <= layout.bodyHeight) end++
            pageCount++
            start = end
        }
        val groupStarts = rows.indices.filter { it > 0 && rows[it].stat.group != rows[it - 1].stat.group }
        val breaks = bestBreaks(rows.size, pageCount, groupStarts, layout.bodyHeight, ::height)
            ?: bestBreaks(rows.size, pageCount, (1 until rows.size).toList(), layout.bodyHeight, ::height)
            ?: error("No split of ${rows.size} rows into $pageCount pages")
        return (listOf(0) + breaks).zip(breaks + rows.size) { from, to -> rows.subList(from, to) }
    }

    /**
     * The page breaks from [candidates] that split [size] rows into [pageCount] pages of at most [bodyHeight].
     * Returns the breaks with the smallest largest page, or null when no such breaks exist.
     */
    private fun bestBreaks(
        size: Int,
        pageCount: Int,
        candidates: List<Int>,
        bodyHeight: Double,
        height: (Int, Int) -> Double,
    ): List<Int>? {
        var best: List<Int>? = null
        var bestHeight = Double.MAX_VALUE
        fun search(from: Int, breaks: List<Int>, largest: Double) {
            val pageStart = breaks.lastOrNull() ?: 0
            if (breaks.size == pageCount - 1) {
                val last = height(pageStart, size)
                val max = maxOf(largest, last)
                // "<=": of two equal splits, the later breaks win, so that the first pages get more rows.
                if (last <= bodyHeight && max <= bestHeight) {
                    best = breaks
                    bestHeight = max
                }
                return
            }
            for (index in from until candidates.size) {
                val end = candidates[index]
                if (end <= pageStart) continue
                val page = height(pageStart, end)
                if (page > bodyHeight) break
                search(index + 1, breaks + end, maxOf(largest, page))
            }
        }
        search(0, emptyList(), 0.0)
        return best
    }

    /** The sizes and the positions of the parts of a row. A position is from the top of the row. */
    internal enum class RowLayout(
        val rowsPerPage: Int,
        val rowHeight: Double,
        val groupTitleHeight: Double,
        val groupTitleSize: Double,
        val labelY: Double,
        val labelSize: Double,
        val valueY: Double,
        val valueSize: Double,
        val detailSize: Double,
        val barY: Double,
        val barHeight: Double,
        val sharedLabelY: Double,
        val sharedValueY: Double,
        val sharedValueSize: Double,
    ) {
        NORMAL(ROWS_PER_PAGE, NORMAL_ROW_HEIGHT, 56.0, 24.0, 30.0, 30.0, 52.0, 54.0, 30.0, 62.0, 10.0, 30.0, 72.0, 46.0),
        COMPACT(COMPACT_ROWS_PER_PAGE, 60.0, 34.0, 20.0, 22.0, 25.0, 30.0, 42.0, 24.0, 40.0, 8.0, 16.0, 44.0, 32.0),
        ;

        /**
         * The maximum height of the rows and the group titles on a page. It is the same for the two layouts,
         * so that the compact card is not larger than the normal card. It holds [rowsPerPage] rows without group titles.
         */
        val bodyHeight: Double get() = ROWS_PER_PAGE * NORMAL_ROW_HEIGHT

        /** The height of [rows] on one page, with the title of each group when [groupTitles] is true. */
        fun height(rows: List<StatRow>, groupTitles: Boolean): Double {
            val titles = if (groupTitles) rows.indices.count { it == 0 || rows[it].stat.group != rows[it - 1].stat.group } else 0
            return rows.size * rowHeight + titles * groupTitleHeight
        }

        companion object {
            fun of(compact: Boolean) = if (compact) COMPACT else NORMAL
        }
    }

    /** The part of a page between the header and the footer. */
    internal class Area(val left: Double, val right: Double, val top: Double, val bottom: Double) {
        val center: Double get() = (left + right) / 2
        val width: Double get() = right - left
    }

    /** A page: the dim frame, the panel, the header, the footer, and the [body] between the header and the footer. */
    private fun page(
        content: StatsCardContent,
        bodyHeight: Double,
        index: Int,
        count: Int,
        frameWidth: Double,
        body: (Area) -> List<SceneItem>,
    ): ScoreboardScene {
        val items = mutableListOf<SceneItem>()
        items += SceneItem.Box(0.0, 0.0, frameWidth, HEIGHT, BLACK, opacity = DIM_OPACITY)

        val panelWidth = minOf(PANEL_WIDTH, frameWidth - 2 * FRAME_MARGIN)
        val panelHeight = HEADER_HEIGHT + bodyHeight + FOOTER_HEIGHT
        val left = (frameWidth - panelWidth) / 2
        val top = (HEIGHT - panelHeight) / 2
        val right = left + panelWidth
        val center = left + panelWidth / 2
        val inner = left + PADDING
        val innerRight = right - PADDING
        val palette = content.palette
        val colors = content.cardColors
        items += SceneItem.Box(left, top, panelWidth, panelHeight, palette.panel, opacity = content.panelOpacity, corners = Corners.all(28.0))

        // Header: the names at the sides, the title and the score in the middle.
        val nameWidth = panelWidth * 0.3
        items += label(inner, top + 78, content.names.p1, NAME_SIZE, colors.p1, TextAnchor.MIDDLE_LEFT, maxWidth = nameWidth)
        items += label(innerRight, top + 78, content.names.p2, NAME_SIZE, colors.p2, TextAnchor.MIDDLE_RIGHT, maxWidth = nameWidth)
        items += label(center, top + 50, DisplayFormats.upper(content.title), 26.0, palette.muted, TextAnchor.CENTER, bold = false,
            spacing = 3.0, maxWidth = innerRight - inner)
        items += label(center, top + 100, content.score, 44.0, palette.text, TextAnchor.CENTER,
            maxWidth = panelWidth - 2 * nameWidth - 2 * PADDING)
        items += SceneItem.Box(inner, top + HEADER_HEIGHT - 22, innerRight - inner, 2.0, palette.rule, opacity = palette.ruleOpacity)

        items += body(Area(inner, innerRight, top + HEADER_HEIGHT, top + HEADER_HEIGHT + bodyHeight))

        val footerY = top + panelHeight - FOOTER_HEIGHT / 2
        if (count > 1) items += label(center, footerY, "${index + 1} / $count", 26.0, palette.muted, TextAnchor.CENTER, bold = false)
        content.credit?.let { items += label(innerRight, footerY, it, 24.0, palette.muted, TextAnchor.MIDDLE_RIGHT, bold = false, opacity = 0.8) }
        return ScoreboardScene(frameWidth, HEIGHT, items)
    }

    internal fun width(label: SceneItem.Label): Double =
        ScoreboardFonts.textWidth(label.text, label.font, label.bold, label.size, label.spacing)

    /**
     * The momentum chart: the point difference after each point. The area above the zero line has the color
     * of player 1, and the area below it has the color of player 2. Dashed lines mark the start of each set.
     */
    private fun chartItems(content: StatsCardContent, momentum: Momentum, area: Area): List<SceneItem> {
        val items = mutableListOf<SceneItem>()
        val palette = content.palette
        val colors = content.cardColors
        val lead = momentum.maxLead
        val labelTop = area.top + 26
        val labelBottom = area.bottom - 26
        val plotTop = area.top + 58
        val plotBottom = area.bottom - 58
        // Each side has the height of the largest lead on that side, so the chart has no empty side.
        val above = maxOf(lead.p1, 1)
        val below = maxOf(lead.p2, 1)
        val unit = (plotBottom - plotTop) / (above + below)
        val zeroY = plotTop + above * unit
        val points = momentum.points
        fun x(position: Double) = area.left + position * (area.right - area.left) / points.size
        fun y(difference: Double) = zeroY - difference * unit

        items += label(area.right, labelBottom, tr("stats.card.pointDifference"), 26.0, palette.muted, TextAnchor.MIDDLE_RIGHT, bold = false,
            maxWidth = area.width * 0.6)
        // A long name is cut, and the lead after it stays.
        val p1Label = label(area.left, labelTop, content.names.p1, 30.0, colors.p1, TextAnchor.MIDDLE_LEFT,
            maxWidth = area.width * 0.3, suffix = " +${lead.p1}")
        items += p1Label
        items += label(area.left, labelBottom, content.names.p2, 30.0, colors.p2, TextAnchor.MIDDLE_LEFT,
            maxWidth = area.width * 0.3, suffix = " +${lead.p2}")

        // The set lines. A set label that would cover the label of player 1 is left out.
        val p1LabelRight = area.left + ScoreboardFonts.textWidth(p1Label.text, INTER, p1Label.bold, p1Label.size)
        for (start in momentum.setStarts) {
            val lineX = x(start.toDouble())
            var dashY = plotTop
            while (dashY < plotBottom) {
                items += SceneItem.Box(lineX - 1, dashY, 2.0, minOf(DASH, plotBottom - dashY), palette.muted, opacity = 0.7)
                dashY += DASH + DASH_GAP
            }
            val text = tr("override.set", "number" to points[start].set)
            val halfWidth = ScoreboardFonts.textWidth(text, INTER, false, 26.0) / 2
            if (lineX - halfWidth > p1LabelRight + 20) {
                items += label(lineX, plotTop - 18, text, 26.0, palette.muted, TextAnchor.CENTER, bold = false)
            }
        }

        // The line starts at 0 before the first point. A line that crosses zero gets a point at zero,
        // so the two areas meet exactly on the zero line.
        val line = mutableListOf(0.0 to 0.0)
        points.forEachIndexed { index, point ->
            val (x0, d0) = line.last()
            val d1 = point.difference.toDouble()
            if (d0 * d1 < 0) line += x0 + d0 / (d0 - d1) to 0.0
            line += (index + 1).toDouble() to d1
        }
        fun area(select: (Double) -> Double) = SceneItem.Polygon(
            line.map { (position, difference) -> ScenePoint(x(position), y(select(difference))) } +
                ScenePoint(x(points.size.toDouble()), zeroY),
            0, AREA_OPACITY,
        )
        if (lead.p1 > 0) items += area { maxOf(it, 0.0) }.copy(rgb = colors.p1)
        if (lead.p2 > 0) items += area { minOf(it, 0.0) }.copy(rgb = colors.p2)
        items += SceneItem.Box(area.left, zeroY - 1, area.right - area.left, 2.0, palette.text, opacity = 0.3)
        items += SceneItem.Polyline(line.map { (position, difference) -> ScenePoint(x(position), y(difference)) }, 4.0, palette.text)
        return items
    }

    /**
     * A label that gets smaller, and then shorter with an ellipsis, when it is wider than [maxWidth] (B-11 E3-T19).
     * The [suffix] after the text is never cut.
     */
    internal fun label(
        x: Double,
        y: Double,
        text: String,
        size: Double,
        rgb: Int,
        anchor: TextAnchor,
        bold: Boolean = true,
        spacing: Double = 0.0,
        opacity: Double = 1.0,
        maxWidth: Double = Double.MAX_VALUE,
        suffix: String = "",
    ): SceneItem.Label {
        // A Japanese text uses the Japanese font (B-11 E8-T2).
        val family = ScoreboardFonts.family(INTER, text + suffix)
        val fitted = ScoreboardFonts.fit(text, family, bold, size, maxWidth, spacing, suffix)
        val middleY = if (anchor == TextAnchor.TOP_LEFT || anchor == TextAnchor.TOP_RIGHT) y else
            ScoreboardFonts.middleYForCapCenter(y, family, bold, fitted.size)
        return SceneItem.Label(x, middleY, fitted.text, family, fitted.size, rgb, bold, anchor, opacity, spacing)
    }

    private fun rgb(hex: String, fallback: Int): Int = hex.removePrefix("#").toIntOrNull(16) ?: fallback

    /**
     * Returns [rgb], or a lighter color when [rgb] is too dark for the dark panel.
     * The lighter color is a mix with white that has the minimum relative luminance.
     */
    fun onPanel(rgb: Int): Int {
        if (luminance(rgb) >= MIN_LUMINANCE) return rgb
        var white = 0.0
        var mixed = rgb
        while (luminance(mixed) < MIN_LUMINANCE && white < 1.0) {
            white += 0.01
            mixed = mix(rgb, WHITE, white)
        }
        return mixed
    }

    /**
     * Returns [rgb], or a darker color when [rgb] is too light for the panel of the light card.
     * The darker color is a mix with black that has the maximum relative luminance.
     */
    fun onLight(rgb: Int): Int {
        if (luminance(rgb) <= MAX_LIGHT_LUMINANCE) return rgb
        var black = 0.0
        var mixed = rgb
        while (luminance(mixed) > MAX_LIGHT_LUMINANCE && black < 1.0) {
            black += 0.01
            mixed = mix(rgb, BLACK, black)
        }
        return mixed
    }

    /** The relative luminance of the WCAG definition, from 0 (black) to 1 (white). */
    internal fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((rgb shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun mix(from: Int, to: Int, part: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * part).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF
    /** A player color darker than this is difficult to see on the panel. The neutral gray of player 2 is just above it. */
    private const val MIN_LUMINANCE = 0.2
    /** A player color lighter than this is difficult to see on the light panel. */
    private const val MAX_LIGHT_LUMINANCE = 0.2
    private const val DIM_OPACITY = 0.45
    private const val PANEL_WIDTH = 1240.0
    private const val FRAME_MARGIN = 48.0
    private const val PADDING = 64.0
    private const val HEADER_HEIGHT = 170.0
    private const val FOOTER_HEIGHT = 70.0
    private const val NORMAL_ROW_HEIGHT = 104.0
    private const val NAME_SIZE = 58.0
    /** The chart page has the height of this number of normal rows, or more when a page has more rows. */
    private const val CHART_SLOTS = 4
    private const val MIN_CHART_POINTS = 2
    private const val AREA_OPACITY = 0.55
    private const val DASH = 10.0
    private const val DASH_GAP = 8.0
}
