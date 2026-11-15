package org.litvin.stats

import org.litvin.export.scoreboard.Corners
import org.litvin.export.scoreboard.INTER
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.export.scoreboard.TextAnchor
import org.litvin.i18n.DisplayFormats
import org.litvin.scoring.PerPlayer
import org.litvin.stats.StatsCard.Area
import org.litvin.stats.StatsCard.RowLayout
import org.litvin.stats.StatsCard.label
import org.litvin.stats.StatsCard.width

/**
 * Draws the rows of one page of the statistics card in the look of its [CardStyle].
 * All styles use the sizes of the [RowLayout], so that each style shows the same rows on a page.
 */
internal object StatsCardRows {
    fun items(content: StatsCardContent, layout: RowLayout, rows: List<StatRow>, area: Area): List<SceneItem> {
        val items = mutableListOf<SceneItem>()
        val palette = content.palette
        var top = area.top
        rows.forEachIndexed { index, row ->
            if (content.groupTitles && (index == 0 || row.stat.group != rows[index - 1].stat.group)) {
                items += groupTitle(row.stat.group.title, layout, palette, area, top)
                top += layout.groupTitleHeight
            }
            val place = Place(top, index, layout, area, palette, content.cardColors)
            items += when (content.style) {
                CardStyle.CLASSIC -> classic(row, place)
                CardStyle.MIRROR -> mirror(row, place)
                CardStyle.STRIPES -> stripes(row, place)
                CardStyle.LABELS -> labels(row, place)
                CardStyle.SHEET -> sheet(row, place)
            }
            top += layout.rowHeight
        }
        return items
    }

    /** The place of a row on the page: its [top], its [index] on the page, and the sizes and colors of the card. */
    private class Place(
        val top: Double,
        val index: Int,
        val layout: RowLayout,
        val area: Area,
        val palette: CardPalette,
        val colors: PerPlayer<Int>,
    ) {
        val middle: Double get() = top + layout.rowHeight / 2
    }

    /** The label over a bar in two parts, and the values at the sides. */
    private fun classic(row: StatRow, at: Place): List<SceneItem> {
        val layout = at.layout
        val values = row.values ?: return sharedStacked(row, at, upper = false)
        val items = mutableListOf<SceneItem>()
        items += label(at.area.center, at.top + layout.labelY, row.label, layout.labelSize, at.palette.soft, TextAnchor.CENTER, bold = false,
            maxWidth = at.area.width - 2 * BAR_INSET)
        items += sideValues(values, at, at.top + layout.valueY, layout.valueSize, PerPlayer(at.palette.text, at.palette.text),
            layout.detailSize, showDetail = row.stat in DETAIL_STATS)
        row.bar?.let {
            items += splitBar(it, at.colors, at.area.left + BAR_INSET, at.area.right - BAR_INSET, at.top + layout.barY, layout.barHeight)
        }
        return items
    }

    /** An upper case label, the values at the sides, and bars that grow from the center to each player. */
    private fun mirror(row: StatRow, at: Place): List<SceneItem> {
        val layout = at.layout
        val values = row.values ?: return sharedStacked(row, at, upper = true)
        val items = mutableListOf<SceneItem>()
        items += label(at.area.center, at.top + layout.labelY, DisplayFormats.upper(row.label), layout.labelSize * 0.9, at.palette.text,
            TextAnchor.CENTER, spacing = 1.5, maxWidth = at.area.width - 2 * BAR_INSET)
        items += sideValues(values, at, at.top + layout.valueY, layout.valueSize, PerPlayer(at.palette.text, at.palette.text),
            layout.detailSize, showDetail = row.stat in DETAIL_STATS)
        items += centerBars(row, at, at.top + layout.barY, layout.barHeight, at.area.width / 2 - BAR_INSET - CENTER_GAP)
        return items
    }

    /** A band for each row. The better value has the player color. */
    private fun stripes(row: StatRow, at: Place): List<SceneItem> {
        val layout = at.layout
        val gap = layout.rowHeight * BAND_GAP
        val items = mutableListOf<SceneItem>()
        items += SceneItem.Box(at.area.left, at.top + gap, at.area.width, layout.rowHeight - 2 * gap, at.palette.text,
            opacity = if (at.index % 2 == 0) 0.10 else 0.05, corners = Corners.all(layout.rowHeight * 0.12))
        val values = row.values ?: return items + sharedInline(row, at)
        items += label(at.area.center, at.middle, row.label, layout.labelSize * 1.05, at.palette.soft, TextAnchor.CENTER, bold = false,
            maxWidth = at.area.width - 2 * BAR_INSET)
        val leader = leader(row)
        val colors = PerPlayer(
            if (leader == 1) at.colors.p1 else at.palette.text,
            if (leader == 2) at.colors.p2 else at.palette.text,
        )
        items += sideValues(values, at, at.middle, layout.valueSize * 0.8, colors, layout.detailSize * 0.9,
            showDetail = row.stat in DETAIL_STATS, inset = STRIPE_PAD)
        return items
    }

    /** A dim strip for each row, a label band in the center with the opposite brightness of the card, and the values at the sides. */
    private fun labels(row: StatRow, at: Place): List<SceneItem> {
        val layout = at.layout
        val gap = layout.rowHeight * BAND_GAP
        val bandTop = at.top + gap
        val bandHeight = layout.rowHeight - 2 * gap
        val items = mutableListOf<SceneItem>()
        items += SceneItem.Box(at.area.left, bandTop, at.area.width, bandHeight, at.palette.text, opacity = 0.05, corners = Corners.all(6.0))
        val values = row.values ?: return items + sharedInline(row, at)
        val pillWidth = at.area.width - 2 * LABEL_INSET
        items += SceneItem.Box(at.area.left + LABEL_INSET, bandTop, pillWidth, bandHeight, at.palette.band, corners = Corners.all(6.0))
        items += label(at.area.center, at.middle, DisplayFormats.upper(row.label), layout.labelSize * 0.95, at.palette.bandText, TextAnchor.CENTER,
            spacing = 1.0, maxWidth = pillWidth - 2 * LABEL_PAD)
        items += sideValues(values, at, at.middle, layout.valueSize * 0.85, PerPlayer(at.palette.text, at.palette.text),
            layout.detailSize * 0.9, showDetail = row.stat in DETAIL_STATS, inset = LABEL_PAD)
        return items
    }

    /**
     * A bold label and the values on one line, and bars from the center under them. Each value shows its detail,
     * for example the fraction of a percentage. The values of the error rows are red.
     */
    private fun sheet(row: StatRow, at: Place): List<SceneItem> {
        val layout = at.layout
        val values = row.values ?: return sharedInline(row, at)
        val textY = at.top + layout.rowHeight * 0.36
        val items = mutableListOf<SceneItem>()
        items += label(at.area.center, textY, row.label, layout.labelSize, at.palette.text, TextAnchor.CENTER,
            maxWidth = at.area.width - 2 * SHEET_LABEL_INSET)
        val color = if (row.stat in LOWER_IS_BETTER) at.palette.error else at.palette.text
        items += sideValues(values, at, textY, layout.valueSize * 0.78, PerPlayer(color, color), layout.detailSize * 0.85,
            showDetail = true)
        items += centerBars(row, at, at.top + layout.rowHeight * 0.64, layout.barHeight, at.area.width / 2 - CENTER_GAP)
        return items
    }

    /** A row with one value for both players: the label, and the value under it. */
    private fun sharedStacked(row: StatRow, at: Place, upper: Boolean): List<SceneItem> {
        val layout = at.layout
        val text = if (upper) DisplayFormats.upper(row.label) else row.label
        return listOf(
            label(at.area.center, at.top + layout.sharedLabelY, text, layout.labelSize * if (upper) 0.9 else 1.0,
                if (upper) at.palette.text else at.palette.soft, TextAnchor.CENTER, bold = upper, spacing = if (upper) 1.5 else 0.0,
                maxWidth = at.area.width),
            label(at.area.center, at.top + layout.sharedValueY, row.shared?.text.orEmpty(), layout.sharedValueSize, at.palette.text,
                TextAnchor.CENTER),
        )
    }

    /** A row with one value for both players: the label and the value on one line, at the two sides of the center. */
    private fun sharedInline(row: StatRow, at: Place): List<SceneItem> {
        val layout = at.layout
        val half = at.area.width / 2 - SHARED_GAP
        return listOf(
            label(at.area.center - SHARED_GAP, at.middle, row.label, layout.labelSize, at.palette.soft, TextAnchor.MIDDLE_RIGHT, bold = false,
                maxWidth = half),
            label(at.area.center + SHARED_GAP, at.middle, row.shared?.text.orEmpty(), layout.labelSize * 1.25, at.palette.text,
                TextAnchor.MIDDLE_LEFT, maxWidth = half),
        )
    }

    /**
     * The values at the two sides of the row, with their cap centers at [y]. With [showDetail], each value has its
     * detail on the inner side, for example "36%".
     */
    private fun sideValues(
        values: PerPlayer<StatValue>,
        at: Place,
        y: Double,
        size: Double,
        colors: PerPlayer<Int>,
        detailSize: Double,
        showDetail: Boolean,
        inset: Double = 0.0,
    ): List<SceneItem> {
        val p1 = label(at.area.left + inset, y, values.p1.text, size, colors.p1, TextAnchor.MIDDLE_LEFT)
        val p2 = label(at.area.right - inset, y, values.p2.text, size, colors.p2, TextAnchor.MIDDLE_RIGHT)
        val items = mutableListOf<SceneItem>(p1, p2)
        if (showDetail) {
            // The detail is on the inner side of the value, on the same baseline.
            val baseline = y + ScoreboardFonts.capHeight(INTER, true, size) / 2
            val capCenter = baseline - ScoreboardFonts.capHeight(INTER, false, detailSize) / 2
            values.p1.detail?.let {
                items += label(p1.x + width(p1) + DETAIL_GAP, capCenter, it, detailSize, at.palette.muted, TextAnchor.MIDDLE_LEFT, bold = false)
            }
            values.p2.detail?.let {
                items += label(p2.x - width(p2) - DETAIL_GAP, capCenter, it, detailSize, at.palette.muted, TextAnchor.MIDDLE_RIGHT, bold = false)
            }
        }
        return items
    }

    /** A bar in two parts. The part of each player is its share of the sum. Two zeros give a dim bar in two equal parts. */
    private fun splitBar(values: PerPlayer<Double>, colors: PerPlayer<Int>, x0: Double, x1: Double, y: Double, height: Double): List<SceneItem> {
        val total = values.p1 + values.p2
        val share = if (total > 0) values.p1 / total else 0.5
        val opacity = if (total > 0) 1.0 else 0.3
        val width = x1 - x0 - BAR_GAP
        val p1Width = width * share
        val p2Width = width - p1Width
        val radius = height / 2
        return listOfNotNull(
            SceneItem.Box(x0, y, p1Width, height, colors.p1, opacity, Corners.all(radius)).takeIf { p1Width > 0.5 },
            SceneItem.Box(x1 - p2Width, y, p2Width, height, colors.p2, opacity, Corners.all(radius)).takeIf { p2Width > 0.5 },
        )
    }

    /**
     * Two bars that grow from the center: to the left for player 1, and to the right for player 2. A dim track shows the
     * full length. A percentage uses the scale of [StatRow.barMax]. Another value uses the larger of the two values.
     */
    private fun centerBars(row: StatRow, at: Place, y: Double, height: Double, maxLength: Double): List<SceneItem> {
        val bar = row.bar ?: return emptyList()
        val center = at.area.center
        val corners = Corners.all(height / 2)
        val trackOpacity = at.palette.ruleOpacity / 2
        val items = mutableListOf<SceneItem>(
            SceneItem.Box(center - CENTER_GAP - maxLength, y, maxLength, height, at.palette.rule, trackOpacity, corners),
            SceneItem.Box(center + CENTER_GAP, y, maxLength, height, at.palette.rule, trackOpacity, corners),
        )
        val scale = row.barMax ?: maxOf(bar.p1, bar.p2)
        if (scale <= 0) return items
        val p1 = maxLength * (bar.p1 / scale).coerceIn(0.0, 1.0)
        val p2 = maxLength * (bar.p2 / scale).coerceIn(0.0, 1.0)
        if (p1 > 0.5) items += SceneItem.Box(center - CENTER_GAP - p1, y, p1, height, at.colors.p1, 1.0, corners)
        if (p2 > 0.5) items += SceneItem.Box(center + CENTER_GAP, y, p2, height, at.colors.p2, 1.0, corners)
        return items
    }

    /** The player with the better value: 1, 2, or null when the values are equal or have no bar. */
    internal fun leader(row: StatRow): Int? {
        val bar = row.bar ?: return null
        if (bar.p1 == bar.p2) return null
        val p1Better = (bar.p1 > bar.p2) != (row.stat in LOWER_IS_BETTER)
        return if (p1Better) 1 else 2
    }

    /** The title of a group in upper case, with a line at each side, in the space of [RowLayout.groupTitleHeight] at [top]. */
    private fun groupTitle(text: String, layout: RowLayout, palette: CardPalette, area: Area, top: Double): List<SceneItem> {
        val y = top + layout.groupTitleHeight / 2
        val title = label(area.center, y, DisplayFormats.upper(text), layout.groupTitleSize, palette.muted, TextAnchor.CENTER,
            spacing = 3.0, maxWidth = area.width - 2 * BAR_INSET)
        val lineWidth = area.width / 2 - width(title) / 2 - GROUP_LINE_GAP
        if (lineWidth < 1) return listOf(title)
        return listOf(
            SceneItem.Box(area.left, y - 1, lineWidth, 2.0, palette.rule, opacity = palette.ruleOpacity),
            title,
            SceneItem.Box(area.right - lineWidth, y - 1, lineWidth, 2.0, palette.rule, opacity = palette.ruleOpacity),
        )
    }

    /** The rows where the smaller value is better. The sheet style shows their values in red. */
    private val LOWER_IS_BETTER = setOf(
        MatchStat.DOUBLE_FAULTS, MatchStat.FORCED_ERRORS, MatchStat.UNFORCED_ERRORS,
        MatchStat.ERRORS_NET, MatchStat.ERRORS_LONG, MatchStat.ERRORS_WIDE,
    )

    /** The rows that show the detail of each value in most styles: the share of the points and of the games. */
    private val DETAIL_STATS = setOf(MatchStat.POINTS_WON, MatchStat.GAMES_WON)
    private const val DETAIL_GAP = 12.0
    /** The space at each side of the label, for the values. The bars of the classic and the mirror styles start here. */
    private const val BAR_INSET = 190.0
    private const val BAR_GAP = 6.0
    /** The space between the center and each bar of the center bars. */
    private const val CENTER_GAP = 4.0
    /** The space between a group title and the line at each side of it. */
    private const val GROUP_LINE_GAP = 24.0
    /** The space above and below a band, as a part of the row height. */
    private const val BAND_GAP = 0.06
    private const val STRIPE_PAD = 28.0
    /** The space at each side of the label band of the labels style, for the values. */
    private const val LABEL_INSET = 250.0
    private const val LABEL_PAD = 20.0
    private const val SHEET_LABEL_INSET = 300.0
    private const val SHARED_GAP = 14.0
}
