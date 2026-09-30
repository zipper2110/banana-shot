package org.litvin.ui.tabs.stats

import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.scoring.PerPlayer
import org.litvin.stats.MatchStat
import org.litvin.stats.StatGroup
import org.litvin.stats.StatRow
import org.litvin.stats.StatValue
import org.litvin.stats.StatsSettingsV1
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import java.awt.AWTEvent
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

/**
 * The columns of the table: the value of player 1, the statistic, the value of player 2 and "In video".
 * The "In video" column has a fixed width. The other columns share the rest as 1 : 2.2 : 1, with a minimum width.
 */
internal data class StatsColumns(val mid: Int, val p2: Int, val lane: Int, val end: Int) {
    val sideWidth get() = mid
    val midWidth get() = p2 - mid

    companion object {
        const val LANE = 88
        private const val SIDE_MIN = 100
        private const val MID_MIN = 228

        /** The smallest row width that shows all columns. */
        const val MIN_WIDTH = SIDE_MIN * 2 + MID_MIN + LANE

        fun of(width: Int): StatsColumns {
            val lane = (width - LANE).coerceAtLeast(0)
            var side = max(SIDE_MIN, (lane / 4.2).toInt())
            var mid = lane - side * 2
            if (mid < MID_MIN) {
                mid = MID_MIN
                side = ((lane - MID_MIN) / 2).coerceAtLeast(0)
            }
            return StatsColumns(side, side + mid, lane, width)
        }
    }
}

/** Paints the "In video" column of a row: a light lime background with a line on its left. */
internal fun paintLane(g: Graphics, x: Int, width: Int, height: Int, selected: Boolean = false) {
    g.color = if (selected) Palette.LIME_TINT else Palette.LIME_WASH
    g.fillRect(x, 0, width - x, height)
    g.color = Palette.LIME_EDGE
    g.fillRect(x, 0, 1, height)
}

/** The player colors of the table, lighter when a color is too dark for the dark tab. */
internal typealias PlayerColors = PerPlayer<Color>

/**
 * The header of the table: the player names over their columns, "Statistic" and "In video".
 * It stays at the top while the table scrolls.
 */
internal class StatsTableHeader : JPanel(null) {
    private val names = PerPlayer(nameLabel("stats-player-1"), nameLabel("stats-player-2"))
    private var colors: PlayerColors = PerPlayer(Palette.FG, Palette.FG)
    private val laneIcon = UiKit.icon(Material2OutlinedMZ.MOVIE, 15, Palette.SAGE)

    init {
        isOpaque = true
        background = Palette.BG
        add(names.p1)
        add(names.p2)
    }

    private fun nameLabel(componentName: String) = JLabel().apply {
        name = componentName
        font = UiKit.font(13.5f, UiKit.Weight.BOLD)
    }

    fun show(playerNames: PerPlayer<String>, playerColors: PlayerColors) {
        colors = playerColors
        names.p1.text = playerNames.p1
        names.p2.text = playerNames.p2
        names.p1.foreground = playerColors.p1
        names.p2.foreground = playerColors.p2
        names.p1.toolTipText = playerNames.p1
        names.p2.toolTipText = playerNames.p2
        revalidate()
        repaint()
    }

    override fun getPreferredSize() = Dimension(StatsColumns.MIN_WIDTH + StatsTable.PAD_LEFT, HEIGHT)

    private val columns get() = StatsColumns.of(width - StatsTable.PAD_LEFT)

    override fun doLayout() {
        val cols = columns
        val left = StatsTable.PAD_LEFT
        val nameWidth = (cols.sideWidth - PAD - DOT - DOT_GAP).coerceAtLeast(0)
        val h = names.p1.preferredSize.height
        val y = (height - 1 - h) / 2
        names.p1.setBounds(left + PAD + DOT + DOT_GAP, y, min(nameWidth, names.p1.preferredSize.width), h)
        val w2 = min(nameWidth, names.p2.preferredSize.width)
        names.p2.setBounds(left + cols.lane - PAD - w2, y, w2, h)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = UiKit.smooth(g)
        try {
            val cols = columns
            val left = StatsTable.PAD_LEFT
            val centerY = (height - 1) / 2.0
            fun dot(x: Int, color: Color) {
                g2.color = color
                g2.fill(Ellipse2D.Double(x.toDouble(), centerY - DOT / 2.0, DOT.toDouble(), DOT.toDouble()))
            }
            dot(left + PAD, colors.p1)
            dot(names.p2.x - DOT_GAP - DOT, colors.p2)
            val statistic = "Statistic"
            val statisticFont = UiKit.font(15f)
            UiKit.drawText(
                g2, statistic, statisticFont, Palette.FG,
                left + cols.mid + (cols.midWidth - UiKit.textWidth(statistic, statisticFont)) / 2f, 0f, height - 1f,
            )
            g2.translate(left, 0)
            paintLane(g2, cols.lane, cols.end, height - 1)
            val caption = "IN VIDEO"
            val captionWidth = laneIcon.iconWidth + 4 + UiKit.textWidth(caption, CAPTION_FONT)
            val x = cols.lane + (StatsColumns.LANE - captionWidth) / 2f
            laneIcon.paintIcon(this, g2, x.toInt(), (height - 1 - laneIcon.iconHeight) / 2)
            UiKit.drawText(g2, caption, CAPTION_FONT, Palette.SAGE, x + laneIcon.iconWidth + 4, 0f, height - 1f)
            g2.translate(-left, 0)
            g2.color = Palette.LINE
            g2.fillRect(0, height - 1, width, 1)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val HEIGHT = 42
        const val PAD = 12
        const val DOT = 9
        const val DOT_GAP = 7
        val CAPTION_FONT = UiKit.trackedFont(11f, 0.05, UiKit.Weight.SEMIBOLD)
    }
}

/**
 * The fixed part above the scrolled rows: the [StatsTableHeader] and, under it, the Match / Set control.
 * Both stay at the top while the table scrolls. The control is hidden for a match with one set.
 */
internal class StatsTableTop(private val header: StatsTableHeader, private val scopeRow: ScopeRow) : JPanel(null) {
    init {
        isOpaque = true
        background = Palette.BG
        add(header)
        add(scopeRow)
    }

    private fun scopeHeight(width: Int) = if (scopeRow.isVisible) scopeRow.heightForWidth(width - StatsTable.PAD_LEFT) else 0

    override fun getPreferredSize(): Dimension {
        val headerSize = header.preferredSize
        return Dimension(headerSize.width, headerSize.height + scopeHeight(if (width > 0) width else headerSize.width))
    }

    override fun doLayout() {
        val headerHeight = header.preferredSize.height
        header.setBounds(0, 0, width, headerHeight)
        scopeRow.setBounds(StatsTable.PAD_LEFT, headerHeight, (width - StatsTable.PAD_LEFT).coerceAtLeast(0), scopeHeight(width))
    }
}

/** The Match / Set control under the table header. It uses the full width of the table. */
internal class ScopeRow(private val control: ScopeControl) : JPanel(null), HeightForWidth {
    init {
        isOpaque = false
        add(control)
    }

    override fun heightForWidth(width: Int) = PAD_Y * 2 + control.preferredSize.height + 1

    override fun getPreferredSize() = Dimension(StatsColumns.MIN_WIDTH, heightForWidth(width))

    override fun doLayout() {
        control.setBounds(0, PAD_Y, (width - PAD_RIGHT).coerceAtLeast(0), control.preferredSize.height)
    }

    override fun paintComponent(g: Graphics) {
        g.color = Palette.LINE
        g.fillRect(0, height - 1, width, 1)
    }

    private companion object {
        const val PAD_Y = 10
        const val PAD_RIGHT = 12
    }
}

/** The momentum chart with its title, and its "In video" checkbox in the last column. */
internal class MomentumRow(private val chart: MomentumChart, private val check: InVideoCheck) : JPanel(null), HeightForWidth {
    init {
        isOpaque = false
        add(chart)
        add(check)
    }

    override fun heightForWidth(width: Int) = PAD_TOP + TITLE + 2 + CHART + PAD_BOTTOM + 1

    override fun getPreferredSize() = Dimension(StatsColumns.MIN_WIDTH, heightForWidth(width))

    override fun doLayout() {
        val cols = StatsColumns.of(width)
        chart.setBounds(PAD_X, PAD_TOP + TITLE + 2, (cols.lane - PAD_X * 2).coerceAtLeast(0), CHART)
        val size = check.preferredSize
        check.setBounds(cols.lane + (StatsColumns.LANE - size.width) / 2, LANE_TOP, size.width, size.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val cols = StatsColumns.of(width)
            paintLane(g2, cols.lane, width, height)
            val titleFont = UiKit.font(13f, UiKit.Weight.BOLD)
            val helpFont = UiKit.font(11.5f)
            val title = "Momentum"
            val baseline = UiKit.baseline(titleFont, PAD_TOP.toFloat(), TITLE.toFloat())
            g2.font = titleFont
            g2.color = Palette.SAGE
            g2.drawString(title, PAD_X.toFloat(), baseline)
            val helpX = PAD_X + UiKit.textWidth(title, titleFont) + 10
            val help = UiKit.ellipsize(HELP, helpFont, cols.lane - PAD_X - helpX)
            g2.font = helpFont
            g2.color = Palette.FG_2
            g2.drawString(help, helpX, baseline)
            g2.color = Palette.LINE
            g2.fillRect(0, height - 1, width, 1)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HELP = "Click the chart to open a point in the Scoring tab."
        private const val PAD_X = 12
        private const val PAD_TOP = 12
        private const val PAD_BOTTOM = 8
        private const val TITLE = 18
        private const val CHART = 160
        private const val LANE_TOP = 10
    }
}

/**
 * The title of a group, between two lines, and the count of its rows in the video ("2 of 4") in the last column.
 */
internal class GroupHeaderRow(val group: StatGroup) : JPanel(null), HeightForWidth {
    /** The count in the "In video" column, for example "2 of 4". */
    var countText: String = ""
        private set
    private var hasSelected = false

    init {
        name = "stats-group-${group.name.lowercase()}"
        isOpaque = false
    }

    fun showCount(selected: Int, total: Int) {
        countText = "$selected of $total"
        hasSelected = selected > 0
        getAccessibleContext().accessibleName = "${group.title}, $countText in the video"
        repaint()
    }

    override fun heightForWidth(width: Int) = HEIGHT

    override fun getPreferredSize() = Dimension(StatsColumns.MIN_WIDTH, HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val cols = StatsColumns.of(width)
            paintLane(g2, cols.lane, width, height)
            val title = group.title.uppercase()
            val titleWidth = UiKit.textWidth(title, TITLE_FONT)
            val lineTop = PAD_TOP.toFloat()
            val free = (cols.lane - PAD_X * 2 - titleWidth - GAP * 2).coerceAtLeast(0f)
            val before = min(BEFORE_MAX, free / 2)
            val lineY = (lineTop + LINE / 2).toInt()
            g2.color = Palette.LINE_2
            g2.fillRect(PAD_X, lineY, before.toInt(), 1)
            val titleX = PAD_X + before + GAP
            UiKit.drawText(g2, title, TITLE_FONT, Palette.SAGE, titleX, lineTop, LINE.toFloat())
            val afterX = (titleX + titleWidth + GAP).toInt()
            g2.color = Palette.LINE_2
            g2.fillRect(afterX, lineY, (cols.lane - PAD_X - afterX).coerceAtLeast(0), 1)
            val countWidth = UiKit.textWidth(countText, COUNT_FONT)
            UiKit.drawText(
                g2, countText, COUNT_FONT, if (hasSelected) Palette.LIME else Palette.FG_3,
                cols.lane + (StatsColumns.LANE - countWidth) / 2f, (height - PAD_BOTTOM - LINE).toFloat(), LINE.toFloat(),
            )
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 12
        const val PAD_TOP = 18
        const val PAD_BOTTOM = 6
        const val LINE = 16
        const val HEIGHT = PAD_TOP + LINE + PAD_BOTTOM
        const val GAP = 12
        const val BEFORE_MAX = 64f
        val TITLE_FONT = UiKit.trackedFont(11.5f, 0.08, UiKit.Weight.BOLD)
        val COUNT_FONT = UiKit.font(11f)
    }
}

/**
 * The point length limits in a dashed box under the title of the Point length group.
 * The parts go to a second line when the box is too narrow.
 */
internal class LimitsBox(shortLimit: LimitStepper, longLimit: LimitStepper) : JPanel(null) {
    private val items: List<List<JComponent>> = listOf(
        listOf(JLabel(UiKit.icon(Material2MZ.TUNE, 16, Palette.SAGE))),
        listOf(text("Short point: up to"), shortLimit, text("s")),
        listOf(text("Long point:"), longLimit, text("s or more")),
    )

    init {
        isOpaque = false
        toolTipText = TOOLTIP
        items.flatten().forEach { add(it) }
    }

    private fun text(value: String) = JLabel(value).apply {
        font = UiKit.font(12f)
        foreground = Palette.FG_2
    }

    private fun itemWidth(item: List<JComponent>) = item.sumOf { it.preferredSize.width } + PAIR_GAP * (item.size - 1)

    /** The items of each line for an inner width. */
    private fun lines(inner: Int): List<List<List<JComponent>>> {
        val lines = mutableListOf<MutableList<List<JComponent>>>()
        var used = 0
        for (item in items) {
            val w = itemWidth(item)
            val line = lines.lastOrNull()
            if (line != null && used + ITEM_GAP + w <= inner) {
                line += item
                used += ITEM_GAP + w
            } else {
                lines += mutableListOf(item)
                used = w
            }
        }
        return lines
    }

    fun heightForWidth(width: Int): Int {
        val count = lines(width - PAD_X * 2).size
        return PAD_Y * 2 + count * LINE + (count - 1) * LINE_GAP
    }

    override fun getPreferredSize() = Dimension(
        items.sumOf { itemWidth(it) } + ITEM_GAP * (items.size - 1) + PAD_X * 2,
        heightForWidth(if (width > 0) width else 600),
    )

    override fun doLayout() {
        val inner = width - PAD_X * 2
        var y = PAD_Y
        for (line in lines(inner)) {
            val lineWidth = line.sumOf { itemWidth(it) } + ITEM_GAP * (line.size - 1)
            var x = PAD_X + (inner - lineWidth) / 2
            for (item in line) {
                for (part in item) {
                    val size = part.preferredSize
                    part.setBounds(x, y + (LINE - size.height) / 2, size.width, size.height)
                    x += size.width + PAIR_GAP
                }
                x += ITEM_GAP - PAIR_GAP
            }
            y += LINE + LINE_GAP
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val shape = RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, 11.0, 11.0)
            g2.color = Palette.CARD
            g2.fill(shape)
            g2.color = Palette.LINE_2
            g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(3f, 3f), 0f)
            g2.draw(shape)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val TOOLTIP = "Set the limits for your marks. Some marks start before the serve or end after the point."
        private const val PAD_X = 12
        private const val PAD_Y = 7
        private const val LINE = 24
        private const val LINE_GAP = 6
        private const val ITEM_GAP = 16
        private const val PAIR_GAP = 6
    }
}

/** The row of the [LimitsBox]. The box uses the width of the first three columns. */
internal class LimitsRow(private val box: LimitsBox) : JPanel(null), HeightForWidth {
    init {
        isOpaque = false
        add(box)
    }

    private fun boxWidth(width: Int) = (StatsColumns.of(width).lane - PAD_X * 2).coerceAtLeast(0)

    override fun heightForWidth(width: Int) = box.heightForWidth(boxWidth(width)) + PAD_BOTTOM

    override fun getPreferredSize() = Dimension(StatsColumns.MIN_WIDTH, heightForWidth(width))

    override fun doLayout() {
        box.setBounds(PAD_X, 0, boxWidth(width), height - PAD_BOTTOM)
    }

    override fun paintComponent(g: Graphics) = paintLane(g, StatsColumns.of(width).lane, width, height)

    private companion object {
        const val PAD_X = 12
        const val PAD_BOTTOM = 6
    }
}

/**
 * A value of the table: the main text, and the detail in a smaller, muted line under it.
 * A value that comes from one point has a play icon, and a click opens the point in the Scoring tab.
 */
internal class ValueCell(
    componentName: String,
    private val align: Int,
    shared: Boolean,
    private val onOpenPoint: (String) -> Unit,
) : JPanel(null) {
    val main = JLabel().apply {
        name = "$componentName-text"
        font = UiKit.font(if (shared) 18f else 19f, UiKit.Weight.BOLD)
        // The icon is on the inner side of the value: after it on the left, and before it on the right.
        horizontalTextPosition = if (align == SwingConstants.RIGHT) SwingConstants.RIGHT else SwingConstants.LEFT
        iconTextGap = 4
    }
    val detail = JLabel().apply {
        name = "$componentName-detail"
        font = UiKit.font(11.5f)
        foreground = Palette.FG_3
    }
    private var pointId: String? = null
    private var color: Color = Palette.FG_2
    private var hover = false
    private val mouse = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            pointId?.let(onOpenPoint)
        }

        override fun mouseEntered(e: MouseEvent) = setHover(true)
        override fun mouseExited(e: MouseEvent) = setHover(false)
    }

    init {
        name = componentName
        isOpaque = false
        add(main)
        add(detail)
    }

    fun show(value: StatValue, color: Color, pointId: String?) {
        this.color = color
        main.text = value.text
        main.icon = if (pointId != null) PLAY_ICON else null
        detail.text = value.detail.orEmpty()
        detail.isVisible = value.detail != null
        if (pointId != this.pointId) {
            if (this.pointId == null) addMouseListener(mouse)
            if (pointId == null) {
                removeMouseListener(mouse)
                hover = false
            }
            this.pointId = pointId
            // A tooltip adds a mouse listener, so only a value with a point has one.
            toolTipText = if (pointId != null) "Open this point in the Scoring tab." else null
            cursor = Cursor.getPredefinedCursor(if (pointId != null) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
        }
        main.foreground = if (hover) Palette.FG_STRONG else color
        revalidate()
        repaint()
    }

    private fun setHover(value: Boolean) {
        if (hover == value) return
        hover = value
        main.foreground = if (hover) Palette.FG_STRONG else color
        repaint()
    }

    fun contentHeight(): Int = main.preferredSize.height + if (detail.isVisible) detail.preferredSize.height - DETAIL_OVERLAP else 0

    override fun getPreferredSize() = Dimension(
        max(main.preferredSize.width, if (detail.isVisible) detail.preferredSize.width else 0),
        contentHeight(),
    )

    override fun doLayout() {
        fun x(w: Int) = when (align) {
            SwingConstants.RIGHT -> width - w
            SwingConstants.CENTER -> (width - w) / 2
            else -> 0
        }
        val mainSize = main.preferredSize
        val mainWidth = min(mainSize.width, width)
        main.setBounds(x(mainWidth), 0, mainWidth, mainSize.height)
        val detailSize = detail.preferredSize
        val detailWidth = min(detailSize.width, width)
        detail.setBounds(x(detailWidth), mainSize.height - DETAIL_OVERLAP, detailWidth, detailSize.height)
    }

    override fun paintComponent(g: Graphics) {
        if (!hover || pointId == null) return
        val g2 = UiKit.smooth(g)
        try {
            val b = main.bounds
            UiKit.paintBox(g2, b.x - 2, b.y + 2, b.width + 4, b.height - 4, 3, Palette.LIME_TINT_2, null)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        /** The two lines are closer than the label heights, like a line height of 1.2. */
        const val DETAIL_OVERLAP = 3
        val PLAY_ICON = UiKit.icon(Material2MZ.PLAY_CIRCLE_FILLED, 15, Palette.LIME)
    }
}

/**
 * One statistic, mirrored like the video card: the value of player 1 on the left, the label and the bar
 * in the middle, the value of player 2 on the right, and the "In video" checkbox in the last column.
 * The value of the player that leads is brighter. A row without a value shows why under its label.
 */
internal class StatRowView(val stat: MatchStat, onInVideo: (MatchStat, Boolean, InVideoCheck) -> Unit, onOpenPoint: (String) -> Unit) :
    JPanel(null), HeightForWidth {
    private val key = stat.key
    val p1 = ValueCell("stats-value-$key-p1", SwingConstants.LEFT, shared = false, onOpenPoint)
    val p2 = ValueCell("stats-value-$key-p2", SwingConstants.RIGHT, shared = false, onOpenPoint)
    val shared = ValueCell("stats-value-$key", SwingConstants.CENTER, shared = true, onOpenPoint)
    val label = JLabel().apply {
        name = "stats-label-$key"
        font = UiKit.font(12.5f)
        horizontalAlignment = SwingConstants.CENTER
        horizontalTextPosition = SwingConstants.LEFT
        iconTextGap = 5
    }
    private val bar = CompareBar()
    val reason = WrapText("", UiKit.font(11.5f).deriveFont(Font.ITALIC), Palette.FG_3, SwingConstants.CENTER, lineSpacing = 1.3f).apply {
        name = "stats-reason-$key"
    }
    val check = InVideoCheck("stats-in-video-$key").apply {
        addActionListener { onInVideo(stat, isSelected, this) }
    }
    private var selected = false

    var hovered = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    init {
        name = "stats-row-$key"
        isOpaque = false
        listOf(p1, p2, shared, label, bar, reason, check).forEach { add(it) }
    }

    /** Shows [row]. [description] is the longer explanation of the row, or null. */
    fun show(row: StatRow, description: String?, inVideo: Boolean, colors: PlayerColors) {
        label.text = row.label
        label.foreground = if (row.available) Palette.FG else Palette.FG_3
        label.icon = when {
            !row.available -> INFO_ICON
            description != null -> HELP_ICON
            else -> null
        }
        label.toolTipText = if (row.available) description else listOfNotNull(row.unavailableReason, description).joinToString(" ")
        val values = row.values
        val sharedValue = row.shared
        when {
            !row.available -> {
                p1.show(StatValue("—"), Palette.HOVER_LINE, null)
                p2.show(StatValue("—"), Palette.HOVER_LINE, null)
                reason.text = row.unavailableReason.orEmpty()
                setParts(sides = true, bar = false, reason = true, shared = false)
            }
            sharedValue != null -> {
                shared.show(sharedValue, Palette.FG_STRONG, row.sharedPointId)
                setParts(sides = false, bar = false, reason = false, shared = true)
            }
            values != null -> {
                val lead = leader(row.bar)
                p1.show(values.p1, if (lead == 1) Palette.FG_STRONG else Palette.FG_2, row.pointIds?.p1)
                p2.show(values.p2, if (lead == 2) Palette.FG_STRONG else Palette.FG_2, row.pointIds?.p2)
                bar.values = row.bar
                bar.colors = colors
                setParts(sides = true, bar = row.bar != null, reason = false, shared = false)
            }
        }
        check.isSelected = inVideo
        check.toolTipText = if (row.available) "Show this row in the exported video." else
            "Show this row in the exported video. The video leaves out the row while it has no value."
        selected = inVideo
        revalidate()
        repaint()
    }

    /** Shows the new selection after a click, before the next [show]. */
    fun showSelected(inVideo: Boolean) {
        check.isSelected = inVideo
        selected = inVideo
        repaint()
    }

    private fun setParts(sides: Boolean, bar: Boolean, reason: Boolean, shared: Boolean) {
        p1.isVisible = sides
        p2.isVisible = sides
        this.bar.isVisible = bar
        this.reason.isVisible = reason
        this.shared.isVisible = shared
    }

    private fun midHeight(midWidth: Int): Int {
        var h = label.preferredSize.height
        if (bar.isVisible) h += BAR_TOP + CompareBar.HEIGHT
        if (reason.isVisible) h += REASON_TOP + reason.heightForWidth(midWidth)
        if (shared.isVisible) h += shared.contentHeight() - SHARED_OVERLAP
        return h
    }

    private fun innerMidWidth(cols: StatsColumns) = (cols.midWidth - MID_PAD_X * 2).coerceAtLeast(0)

    override fun heightForWidth(width: Int): Int {
        val cols = StatsColumns.of(width)
        val sides = if (p1.isVisible) max(p1.contentHeight(), p2.contentHeight()) else 0
        return maxOf(MIN_HEIGHT, midHeight(innerMidWidth(cols)) + PAD_Y * 2, sides + PAD_Y * 2)
    }

    override fun getPreferredSize() = Dimension(StatsColumns.MIN_WIDTH, heightForWidth(if (width > 0) width else StatsColumns.MIN_WIDTH))

    override fun doLayout() {
        val cols = StatsColumns.of(width)
        val rowHeight = height - 1
        val sideWidth = (cols.sideWidth - SIDE_PAD_X).coerceAtLeast(0)
        fun side(cell: ValueCell, x: Int) {
            val h = cell.contentHeight()
            cell.setBounds(x, (rowHeight - h) / 2, sideWidth, h)
        }
        side(p1, SIDE_PAD_X)
        side(p2, cols.p2)

        val midX = cols.mid + MID_PAD_X
        val midWidth = innerMidWidth(cols)
        var y = (rowHeight - midHeight(midWidth)) / 2
        val labelHeight = label.preferredSize.height
        label.setBounds(midX, y, midWidth, labelHeight)
        y += labelHeight
        if (bar.isVisible) {
            bar.setBounds(midX, y + BAR_TOP, midWidth, CompareBar.HEIGHT)
            y += BAR_TOP + CompareBar.HEIGHT
        }
        if (reason.isVisible) {
            val h = reason.heightForWidth(midWidth)
            reason.setBounds(midX, y + REASON_TOP, midWidth, h)
            y += REASON_TOP + h
        }
        if (shared.isVisible) shared.setBounds(midX, y - SHARED_OVERLAP, midWidth, shared.contentHeight())

        val size = check.preferredSize
        check.setBounds(cols.lane + (StatsColumns.LANE - size.width) / 2, (rowHeight - size.height) / 2, size.width, size.height)
    }

    override fun paintComponent(g: Graphics) {
        val cols = StatsColumns.of(width)
        if (hovered) {
            g.color = Palette.ROW_HOVER
            g.fillRect(0, 0, width, height)
        }
        paintLane(g, cols.lane, width, height, selected)
        g.color = Palette.ROW_LINE
        g.fillRect(0, height - 1, width, 1)
    }

    companion object {
        const val MIN_HEIGHT = 52
        private const val PAD_Y = 5
        private const val SIDE_PAD_X = 12
        private const val MID_PAD_X = 10
        private const val BAR_TOP = 6
        private const val REASON_TOP = 3
        private const val SHARED_OVERLAP = 2
        private val INFO_ICON = UiKit.icon(Material2OutlinedAL.INFO, 14, Palette.FG_3)
        private val HELP_ICON = UiKit.icon(Material2OutlinedAL.HELP_OUTLINE, 14, Palette.FG_3)

        /** The player that leads the row: 1, 2, or 0 for equal values and for rows without a bar. */
        fun leader(bar: PerPlayer<Double>?): Int = when {
            bar == null || bar.p1 == bar.p2 -> 0
            bar.p1 > bar.p2 -> 1
            else -> 2
        }
    }
}

/**
 * The scrolled part of the left side: the momentum chart and the statistics rows in groups.
 * The components stay the same for all projects. [show] and the other calls change their content in place,
 * so a control keeps the focus and the mouse while the values change.
 */
internal class StatsTable(
    onScope: (Int) -> Unit,
    onMomentumInVideo: (Boolean) -> Unit,
    onInVideo: (MatchStat, Boolean, InVideoCheck) -> Unit,
    onShortLimit: (Int) -> Unit,
    onLongLimit: (Int) -> Unit,
    onOpenPoint: (String) -> Unit,
) : JPanel(null), Scrollable {
    val scope = ScopeControl(onScope)
    /** The row of the Match / Set control. It is in the fixed [StatsTableTop], not in the scrolled rows. */
    val scopeRow = ScopeRow(scope)
    val chart = MomentumChart(onOpenPoint)
    val momentumCheck = InVideoCheck("stats-in-video-momentum").apply {
        toolTipText = "Add a page with this chart to the statistics card of the exported video."
        addActionListener { onMomentumInVideo(isSelected) }
    }
    val shortLimit = LimitStepper(
        "stats-short-point-limit", StatsSettingsV1.MIN_POINT_LIMIT_SECONDS, StatsSettingsV1.MAX_POINT_LIMIT_SECONDS - 1,
        StatsSettingsV1.DEFAULT_SHORT_POINT_MAX_SECONDS, onShortLimit,
    )
    val longLimit = LimitStepper(
        "stats-long-point-limit", StatsSettingsV1.MIN_POINT_LIMIT_SECONDS + 1, StatsSettingsV1.MAX_POINT_LIMIT_SECONDS,
        StatsSettingsV1.DEFAULT_LONG_POINT_MIN_SECONDS, onLongLimit,
    )
    val rows: Map<MatchStat, StatRowView> = MatchStat.entries.associateWith { StatRowView(it, onInVideo, onOpenPoint) }
    val groups: Map<StatGroup, GroupHeaderRow> = StatGroup.entries.associateWith { GroupHeaderRow(it) }

    private val hoverListener = AWTEventListener { event -> if (event is MouseEvent) updateHover(event) }

    init {
        name = "stats-table"
        isOpaque = true
        background = Palette.BG
        add(MomentumRow(chart, momentumCheck))
        for (group in StatGroup.entries) {
            val groupRows = MatchStat.entries.filter { it.group == group }
            if (groupRows.isEmpty()) continue
            add(groups.getValue(group))
            if (group == StatGroup.POINT_LENGTH) add(LimitsRow(LimitsBox(shortLimit, longLimit)))
            groupRows.forEach { add(rows.getValue(it)) }
        }
    }

    /** Shows the Match / Set control for two sets or more. */
    fun showScopes(setScores: List<String>, scope: Int) {
        scopeRow.isVisible = setScores.size > 1
        if (scopeRow.isVisible) this.scope.show(setScores, scope)
        scopeRow.parent?.revalidate()
        revalidate()
    }

    /** Shows the count of rows in the video in each group title. */
    fun showGroupCounts(inVideo: (MatchStat) -> Boolean) {
        for ((group, header) in groups) {
            val groupStats = MatchStat.entries.filter { it.group == group }
            header.showCount(groupStats.count(inVideo), groupStats.size)
        }
    }

    private val shown get() = components.filter { it.isVisible }

    private fun contentWidth(width: Int) = (width - PAD_LEFT).coerceAtLeast(0)

    override fun getPreferredSize(): Dimension {
        val viewportWidth = (parent as? JViewport)?.width?.takeIf { it > 0 } ?: width.takeIf { it > 0 } ?: MIN_WIDTH
        val width = max(viewportWidth, MIN_WIDTH)
        val inner = contentWidth(width)
        return Dimension(width, shown.sumOf { it.heightAt(inner) } + PAD_BOTTOM)
    }

    override fun doLayout() {
        val inner = contentWidth(width)
        var y = 0
        for (child in shown) {
            val h = child.heightAt(inner)
            child.setBounds(PAD_LEFT, y, inner, h)
            y += h
        }
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
        max(16, visibleRect.height - 16)
    override fun getScrollableTracksViewportWidth() = ((parent as? JViewport)?.width ?: 0) >= MIN_WIDTH
    override fun getScrollableTracksViewportHeight() = false

    override fun addNotify() {
        super.addNotify()
        Toolkit.getDefaultToolkit().addAWTEventListener(
            hoverListener,
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK,
        )
    }

    override fun removeNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(hoverListener)
        setHovered(null)
        super.removeNotify()
    }

    /** Marks the row under the mouse. The rows get no mouse events when the mouse is on a child, so the table watches all events. */
    private fun updateHover(event: MouseEvent) {
        if (!isShowing) return
        val point = if (event.id == MouseEvent.MOUSE_EXITED) {
            // The mouse can leave a child to a place outside the table, so the exit point is not sufficient.
            getMousePosition(true)
        } else {
            val source = event.component ?: return
            if (!source.isShowing) return
            try {
                SwingUtilities.convertPoint(source, event.point, this)
            } catch (_: Exception) {
                return
            }
        }
        val row = if (point != null && visibleRect.contains(point)) rows.values.firstOrNull { it.isVisible && it.bounds.contains(point) } else null
        setHovered(row)
    }

    private var hoveredRow: StatRowView? = null

    private fun setHovered(row: StatRowView?) {
        if (hoveredRow === row) return
        hoveredRow?.hovered = false
        hoveredRow = row
        row?.hovered = true
    }

    companion object {
        /** The space at the left of the table and its header. */
        const val PAD_LEFT = 16
        private const val PAD_BOTTOM = 24
        const val MIN_WIDTH = StatsColumns.MIN_WIDTH + PAD_LEFT
    }
}
