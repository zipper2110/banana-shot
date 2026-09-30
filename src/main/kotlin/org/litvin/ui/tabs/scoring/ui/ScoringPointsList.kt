package org.litvin.ui.tabs.scoring.ui

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoringEngine.MatchState
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.RowIconButton
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingUtilities
import kotlin.math.ceil
import org.litvin.ui.commons.formatSeconds

/** The data of the points list. [states] has the score after each point, in the order of [points]. */
internal data class PointsListData(
    val points: List<PointV1> = emptyList(),
    val outcomes: Map<String, Outcome> = emptyMap(),
    val states: List<MatchState> = emptyList(),
    val serverMarks: Map<String, Outcome> = emptyMap(),
    val players: ScorePlayers = ScorePlayers(),
)

/**
 * The points list of the side column: the "Points" title with the scored and favorite counts and the scored
 * progress, the column captions, and one 40 px row for each point. The color strip of a row shows the winner.
 * A click on a row selects the point. "Go to point" (on hover) opens the Points tab at that point.
 */
internal class ScoringPointsList(
    private val onSelect: (index: Int) -> Unit,
    private val onToggleFavorite: (index: Int) -> Unit,
    private val onGoToPoint: (index: Int) -> Unit,
) : JPanel(BorderLayout()) {

    val head = ListHead()
    private val captions = ListCaptions()
    private val rowsPanel = RowsPanel()
    private val scroll = JScrollPane(rowsPanel).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val rows = mutableListOf<ScoringPointRow>()
    private var data = PointsListData()
    private var selectedIndex = -1
    private var hoveredRow: ScoringPointRow? = null

    private val hoverListener = object : MouseAdapter() {
        override fun mouseEntered(event: MouseEvent) {
            rowOf(event.component)?.let(::setHovered)
        }

        override fun mouseExited(event: MouseEvent) {
            val row = rowOf(event.component) ?: return
            // The pointer can go from the row into one of its buttons. Keep the hover while it is inside the row.
            SwingUtilities.invokeLater {
                val inside = runCatching {
                    val pointer = MouseInfo.getPointerInfo()?.location ?: return@runCatching false
                    row.isShowing && Rectangle(row.locationOnScreen, row.size).contains(pointer)
                }.getOrDefault(false)
                if (!inside && hoveredRow === row) setHovered(null)
            }
        }

        override fun mouseClicked(event: MouseEvent) {
            if (!SwingUtilities.isLeftMouseButton(event)) return
            val row = event.component as? ScoringPointRow ?: return
            onSelect(row.index)
        }
    }

    init {
        name = "scoring-points-list"
        isOpaque = true
        background = Palette.BG
        add(head, BorderLayout.NORTH)
        val table = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(captions, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }
        add(table, BorderLayout.CENTER)
        runCatching { applyDarkScrollbar(scroll, Palette.BG) }
        scroll.verticalScrollBar.unitIncrement = ScoringPointRow.HEIGHT
        scroll.verticalScrollBar.addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) = syncCaptions()
            override fun componentHidden(e: ComponentEvent) = syncCaptions()
            override fun componentResized(e: ComponentEvent) = syncCaptions()
        })
    }

    /** The captions stay above their columns when the scroll bar takes width from the rows. */
    private fun syncCaptions() {
        captions.scrollBarWidth = if (scroll.verticalScrollBar.isVisible) scroll.verticalScrollBar.width else 0
    }

    fun setData(data: PointsListData) {
        this.data = data
        head.setCounts(
            scored = data.points.count { it.id in data.outcomes },
            total = data.points.size,
            favorites = data.points.count { it.favorite },
        )
        rebuild()
    }

    /** Shows [index] as the selected row. [scroll] brings the row into view. */
    fun setSelectedIndex(index: Int, scroll: Boolean) {
        selectedIndex = index
        rows.forEach { it.selected = it.index == index }
        if (scroll) scrollIntoView(index)
    }

    /**
     * Brings the row into view. The list can get new rows or a new size in the same event (for example when the tab
     * opens), so the list scrolls again after the layout.
     */
    fun scrollIntoView(index: Int) {
        if (index !in rows.indices) return
        val scrollNow = { rowsPanel.scrollRectToVisible(Rectangle(0, index * ScoringPointRow.HEIGHT, 1, ScoringPointRow.HEIGHT)) }
        scrollNow()
        SwingUtilities.invokeLater {
            if (selectedIndex == index) {
                scroll.validate()
                scrollNow()
            }
        }
    }

    internal fun rowCount(): Int = rows.size

    internal fun row(index: Int): ScoringPointRow? = rows.getOrNull(index)

    private fun rebuild() {
        rowsPanel.removeAll()
        rows.clear()
        hoveredRow = null
        data.points.forEachIndexed { index, point ->
            val row = ScoringPointRow(index, point, data).apply {
                selected = index == selectedIndex
                favoriteButton.addActionListener { onToggleFavorite(index) }
                goToButton.addActionListener { onGoToPoint(index) }
            }
            row.addMouseListener(hoverListener)
            row.components.forEach { it.addMouseListener(hoverListener) }
            rows += row
            rowsPanel.add(row)
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
        // A click on a star rebuilds the rows. The pointer stays on the new row, but Swing sends no enter event.
        SwingUtilities.invokeLater(::restoreHover)
    }

    private fun restoreHover() {
        val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
        if (!rowsPanel.isShowing) return
        val local = pointer.location.also { SwingUtilities.convertPointFromScreen(it, rowsPanel) }
        if (!rowsPanel.visibleRect.contains(local)) return
        setHovered(rows.firstOrNull { it.bounds.contains(local) })
    }

    private fun setHovered(row: ScoringPointRow?) {
        if (hoveredRow === row) return
        hoveredRow?.hovered = false
        hoveredRow = row
        row?.hovered = true
    }

    private fun rowOf(component: Component): ScoringPointRow? =
        component as? ScoringPointRow ?: SwingUtilities.getAncestorOfClass(ScoringPointRow::class.java, component) as? ScoringPointRow

    /** The rows, one under the other. It follows the viewport width and shows the empty text without rows. */
    private inner class RowsPanel : JPanel(null), Scrollable {
        init {
            isOpaque = true
            background = Palette.BG
        }

        override fun doLayout() {
            var y = 0
            for (child in components) {
                child.setBounds(0, y, width, ScoringPointRow.HEIGHT)
                y += ScoringPointRow.HEIGHT
            }
        }

        override fun getPreferredSize(): Dimension {
            val viewportWidth = (parent as? JViewport)?.width ?: 0
            return Dimension(viewportWidth, if (componentCount == 0) EMPTY_HEIGHT else componentCount * ScoringPointRow.HEIGHT)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (componentCount > 0) return
            val g2 = UiKit.smooth(g)
            try {
                val font = UiKit.font(13f)
                val lines = listOf("No points yet. Open the Points tab", "and add point markers.")
                lines.forEachIndexed { i, line ->
                    UiKit.drawText(g2, line, font, Palette.FG_3, (width - UiKit.textWidth(line, font)) / 2f, 28f + i * 20f, 20f)
                }
            } finally {
                g2.dispose()
            }
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = ScoringPointRow.HEIGHT
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }

    private companion object {
        const val EMPTY_HEIGHT = 90
    }
}

/** "Points", the scored and favorite counts, and the scored progress bar. */
internal class ListHead : JComponent() {
    var scored = 0
        private set
    var total = 0
        private set
    var favorites = 0
        private set

    private val titleFont get() = UiKit.font(15f, UiKit.Weight.SEMIBOLD)
    private val statFont get() = UiKit.font(12f)
    private val statBold get() = UiKit.font(12f, UiKit.Weight.BOLD)
    private val checkIcon by lazy { UiKit.icon(Material2AL.CHECK_CIRCLE, 14, Palette.LIME) }
    private val starIcon by lazy { UiKit.icon(Material2MZ.STAR, 14, Palette.YELLOW) }

    init {
        name = "scoring-points-head"
        toolTipText = ""
    }

    fun setCounts(scored: Int, total: Int, favorites: Int) {
        this.scored = scored
        this.total = total
        this.favorites = favorites
        repaint()
    }

    override fun getPreferredSize() = Dimension(0, HEIGHT)

    /** The text parts of the stat line, from left to right, with their fonts. */
    private fun scoredParts() = listOf(scored.toString() to statBold, " / " to statFont, total.toString() to statBold, "  scored" to statFont)

    private fun partsWidth(parts: List<Pair<String, java.awt.Font>>) = parts.sumOf { (text, font) -> UiKit.textWidth(text, font).toDouble() }.toFloat()

    private fun favoritesX(): Float = width - PAD_X - (starIcon.iconWidth + 4 + UiKit.textWidth(favorites.toString(), statBold))

    private fun scoredX(): Float = favoritesX() - STAT_GAP - (checkIcon.iconWidth + 4 + partsWidth(scoredParts()))

    override fun getToolTipText(event: MouseEvent): String? = when {
        event.y > TITLE_TOP + TITLE_HEIGHT -> "$scored of $total points have a score"
        event.x >= favoritesX() -> "Favorite points"
        event.x >= scoredX() -> "Scored points"
        else -> null
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            g2.color = Palette.BG
            g2.fillRect(0, 0, width, height)
            val top = TITLE_TOP.toFloat()
            val h = TITLE_HEIGHT.toFloat()
            UiKit.drawText(g2, "Points", titleFont, Palette.FG, PAD_X.toFloat(), top, h)

            var x = scoredX()
            checkIcon.paintIcon(this, g2, x.toInt(), (top + (h - checkIcon.iconHeight) / 2f).toInt())
            x += checkIcon.iconWidth + 4
            for ((text, font) in scoredParts()) {
                UiKit.drawText(g2, text, font, if (font == statBold) Palette.FG else Palette.FG_2, x, top, h)
                x += UiKit.textWidth(text, font)
            }
            x = favoritesX()
            starIcon.paintIcon(this, g2, x.toInt(), (top + (h - starIcon.iconHeight) / 2f).toInt())
            UiKit.drawText(g2, favorites.toString(), statBold, Palette.FG, x + starIcon.iconWidth + 4, top, h)

            // The scored progress.
            val barY = TITLE_TOP + TITLE_HEIGHT + 8
            val barWidth = width - PAD_X * 2
            UiKit.paintBox(g2, PAD_X, barY, barWidth, 4, 2, PROGRESS_BG, null)
            if (total > 0 && scored > 0) {
                val fill = ceil(barWidth * scored.toDouble() / total).toInt().coerceAtMost(barWidth)
                UiKit.paintBox(g2, PAD_X, barY, fill, 4, 2, Palette.LIME, null)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 14
        const val TITLE_TOP = 14
        const val TITLE_HEIGHT = 20
        const val STAT_GAP = 14
        const val HEIGHT = TITLE_TOP + TITLE_HEIGHT + 8 + 4 + 10
        val PROGRESS_BG = Palette.RAISED_2
    }
}

/**
 * The columns of the list: "32px 64px 50px minmax(0,1fr) 50px 24px 24px" with 6 px gaps,
 * 14 px padding at the left and 8 px at the right: number, start, length, label, marks, Go to point, star.
 */
internal data class ListColumns(val width: Int) {
    val number = PAD_LEFT
    val start = number + 32 + GAP
    val length = start + 64 + GAP
    val lengthRight = length + 50
    val label = lengthRight + GAP
    val star = width - PAD_RIGHT - 24
    val jump = star - GAP - 24
    val marksRight = jump - GAP
    val labelRight = marksRight - 50 - GAP

    companion object {
        const val PAD_LEFT = 14
        const val PAD_RIGHT = 8
        const val GAP = 6
    }
}

/** The caption row of the list: #, Start, Length and the star. */
internal class ListCaptions : JComponent() {
    private val captionFont = UiKit.trackedFont(10.5f, 0.07)
    private val starIcon = UiKit.icon(Material2MZ.STAR, 11, Palette.FG_3)

    /** The width of the list scroll bar. */
    var scrollBarWidth: Int = 0
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    override fun getPreferredSize() = Dimension(0, HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            g2.color = Palette.INSET
            g2.fillRect(0, 0, width, height)
            g2.color = Palette.LINE
            g2.fillRect(0, 0, width, 1)
            g2.fillRect(0, height - 1, width, 1)
            val columns = ListColumns(width - scrollBarWidth)
            fun caption(text: String, x: Float) = UiKit.drawText(g2, text, captionFont, Palette.FG_3, x, 1f, height - 2f)
            caption("#", columns.number.toFloat())
            caption("START", columns.start.toFloat())
            caption("LENGTH", columns.lengthRight - UiKit.textWidth("LENGTH", captionFont))
            starIcon.paintIcon(this, g2, columns.star + (24 - starIcon.iconWidth) / 2, (height - starIcon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 26
    }
}

/** One point of the list. The list sets [hovered] and [selected]; the row paints them. */
internal class ScoringPointRow(
    val index: Int,
    val point: PointV1,
    private val data: PointsListData,
) : JPanel(null) {
    private val outcome = data.outcomes[point.id]
    private val state = data.states.getOrNull(index)
    private val serveMark = when (data.serverMarks[point.id]) {
        Outcome.P1 -> 1
        Outcome.P2 -> 2
        else -> null
    }

    /** A set win shows SET; a game win shows GAME. A point that wins the set also wins a game, so it shows SET only. */
    private val milestone: Pair<String, Int>? = when {
        state?.lastSetWonBy != null -> "SET" to state.lastSetWonBy
        state?.lastGameWonBy != null -> "GAME" to state.lastGameWonBy
        else -> null
    }

    val favoriteButton = FavoriteButton(24).apply {
        name = "scoring-favorite-point-${point.id}"
        favorite = point.favorite
        toolTipText = "Favorite [A]"
    }
    val goToButton = RowIconButton(Material2MZ.OPEN_IN_NEW, "Go to point ${index + 1} in the Points tab", color = Palette.FG_2, side = 24).apply {
        name = "scoring-go-to-point-${point.id}"
    }

    var hovered = false
        set(value) {
            if (field == value) return
            field = value
            updateButtons()
            repaint()
        }

    var selected = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    private val racketIcon by lazy { serveMark?.let { UiKit.icon(Material2MZ.SPORTS_TENNIS, 15, data.players.color(it)) } }

    /** A racket in a dark player color stands on a light disc, so that it stays visible on the dark row. */
    internal val racketOnBadge: Boolean get() = serveMark?.let { ScoringUi.needsBadge(data.players.color(it)) } ?: false

    private fun racketWidth() = if (racketOnBadge) RACKET_BADGE_SIDE else 15

    init {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        add(goToButton)
        add(favoriteButton)
        toolTipText = ""
        updateButtons()
    }

    private fun updateButtons() {
        // The star of a favorite stays visible. Go to point and an empty star show only while the pointer is over the row.
        favoriteButton.isVisible = point.favorite || hovered
        goToButton.isVisible = hovered
    }

    override fun getPreferredSize() = Dimension(0, HEIGHT)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, HEIGHT)

    override fun doLayout() {
        val columns = ListColumns(width)
        goToButton.setBounds(columns.jump, (height - 24) / 2, 24, 24)
        favoriteButton.setBounds(columns.star, (height - 24) / 2, 24, 24)
    }

    /** The marks at the right end of the marks column: the serve mark and the GAME or SET badge. */
    private fun badgeWidth(text: String) = ceil(UiKit.textWidth(text, BADGE_FONT)).toInt() + 10

    override fun getToolTipText(event: MouseEvent): String? {
        val columns = ListColumns(width)
        var right = columns.marksRight
        milestone?.let { (text, player) ->
            val left = right - badgeWidth(text)
            if (event.x in left..right) return "${if (text == "SET") "Set" else "Game"} won by ${data.players.name(player)}"
            right = left - 4
        }
        serveMark?.let { player ->
            if (event.x in (right - racketWidth())..right) return "Serve marked: ${data.players.name(player)} serves"
        }
        if (event.x < columns.start && !point.label.isNullOrBlank()) return point.label
        if (event.x in columns.label..columns.labelRight && !point.label.isNullOrBlank()) return point.label
        return null
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val fill = when {
                selected -> SELECTED
                hovered -> Palette.ROW_HOVER
                else -> null
            }
            if (fill != null) {
                g2.color = fill
                g2.fillRect(0, 0, width, height)
            }
            g2.color = Palette.ROW_LINE
            g2.fillRect(0, height - 1, width, 1)
            if (selected) UiKit.paintBox(g2, 0, 0, width, height, 0, null, Palette.LIME_LINE)
            // The winner strip: the player color, light gray for no point, nothing for a point without a score.
            val strip = when (outcome) {
                Outcome.P1 -> data.players.p1Color
                Outcome.P2 -> data.players.p2Color
                Outcome.NONE -> Palette.NEUTRAL_LIGHT
                null -> null
            }
            if (strip != null) {
                g2.color = strip
                g2.fillRect(0, 0, STRIP, height)
            }

            val columns = ListColumns(width)
            val h = height - 1f
            val scored = outcome != null
            val numberColor = when {
                selected -> Palette.LIME
                scored -> Palette.FG
                else -> Palette.FG_2
            }
            UiKit.drawText(g2, (index + 1).toString(), NUMBER_FONT, numberColor, columns.number.toFloat(), 0f, h)
            val start = Timecode.format(point.startMs.toLong()).substring(0, 8)
            UiKit.drawText(g2, start, TEXT_FONT, if (scored) Palette.FG else Palette.FG_2, columns.start.toFloat(), 0f, h)
            val length = formatSeconds((point.endMs - point.startMs).toLong().coerceAtLeast(0))
            UiKit.drawText(g2, length, TEXT_FONT, Palette.FG_3, columns.lengthRight - UiKit.textWidth(length, TEXT_FONT), 0f, h)
            point.label?.takeIf { it.isNotBlank() }?.let { label ->
                val text = UiKit.ellipsize(label.replace('\n', ' '), TEXT_FONT, (columns.labelRight - columns.label).toFloat())
                UiKit.drawText(g2, text, TEXT_FONT, Palette.FG_3, columns.label.toFloat(), 0f, h)
            }

            var right = columns.marksRight
            milestone?.let { (text, player) ->
                val color = data.players.color(player)
                val w = badgeWidth(text)
                val x = right - w
                val y = (height - BADGE_HEIGHT) / 2
                // SET is the outline badge: the player color on its contrast color. GAME is the filled badge.
                val onColor = ScoringUi.onPlayer(color)
                if (text == "SET") {
                    UiKit.paintBox(g2, x, y, w, BADGE_HEIGHT, 3, onColor, color)
                    UiKit.drawText(g2, text, BADGE_FONT, color, x + 5f, y.toFloat(), BADGE_HEIGHT.toFloat())
                } else {
                    UiKit.paintBox(g2, x, y, w, BADGE_HEIGHT, 3, color, null)
                    UiKit.drawText(g2, text, BADGE_FONT, onColor, x + 5f, y.toFloat(), BADGE_HEIGHT.toFloat())
                }
                right = x - 4
            }
            racketIcon?.let { icon ->
                val side = racketWidth()
                val x = right - side
                if (racketOnBadge) {
                    g2.color = Palette.NEUTRAL_LIGHT
                    g2.fillOval(x, (height - side) / 2, side, side)
                }
                icon.paintIcon(this, g2, x + (side - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 40
        const val STRIP = 4
        const val BADGE_HEIGHT = 16
        const val RACKET_BADGE_SIDE = 21
        val SELECTED = Palette.SELECTED
        private val NUMBER_FONT get() = UiKit.font(12.5f, UiKit.Weight.BOLD)
        private val TEXT_FONT get() = UiKit.font(12.5f)
        private val BADGE_FONT get() = UiKit.trackedFont(9.5f, 0.05, UiKit.Weight.BOLD)
    }
}
