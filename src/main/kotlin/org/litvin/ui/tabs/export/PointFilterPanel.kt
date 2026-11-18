package org.litvin.ui.tabs.export

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.export.FilterEnding
import org.litvin.export.PointFilter
import org.litvin.i18n.tr
import org.litvin.scoring.ErrorKind
import org.litvin.ui.commons.Palette
import org.litvin.ui.tabs.export.ExportUi.Weight
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.JToggleButton
import kotlin.math.ceil
import kotlin.math.max

/**
 * The filter buttons of a paid export (B-69, in docs/backlog-paid.md). "Only points" and "Only favorites" each get a
 * [FilterButton] on the right side. A click opens [PointFilterPanel] in a dropdown under the button, on top of the tab
 * content. Both buttons show the same filter. [onChange] gets the new filter after each click on a chip.
 */
internal class PointFilterDropdown(onChange: (PointFilter) -> Unit) {
    private val buttons = mutableListOf<FilterButton>()
    val panel = PointFilterPanel { filter ->
        buttons.forEach { it.selectedCount = filter.selectedCount }
        onChange(filter)
    }

    /** The time when the dropdown closed last. A press on a filter button closes the dropdown before its click. */
    private var closedAtMs = 0L

    private val popup = JPopupMenu().apply {
        name = "export-filter-popup"
        isFocusable = false
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Palette.LINE_2),
            BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD),
        )
        background = Palette.RAISED
        layout = BorderLayout()
        add(panel, BorderLayout.CENTER)
        addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) = Unit
            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                closedAtMs = System.currentTimeMillis()
            }
            override fun popupMenuCanceled(e: PopupMenuEvent) = Unit
        })
    }

    /** The filter of the selected chips. */
    val filter: PointFilter get() = panel.filter

    /** True while the dropdown shows. */
    val isOpen: Boolean get() = popup.isVisible

    /** A new filter button, for example for the "Only points" card. */
    fun newButton(componentName: String): FilterButton = FilterButton(componentName).also { button ->
        buttons += button
        button.isVisible = available
        button.addActionListener { toggle(button) }
    }

    /** Shows or hides the filter buttons. Hidden buttons also close the dropdown. */
    var available: Boolean = false
        set(value) {
            field = value
            buttons.forEach { it.isVisible = value }
            if (!value) popup.isVisible = false
        }

    /** Shows the side names on the player chips. */
    fun setPlayers(name1: String, name2: String) = panel.setPlayers(name1, name2)

    /** Selects no chip. The buttons then show no count. */
    fun clear() {
        panel.clear()
        buttons.forEach { it.selectedCount = 0 }
    }

    /** Opens the dropdown under [button], or closes it when it shows. */
    fun toggle(button: FilterButton) {
        // The press that closed the dropdown is part of this click: then the click only closes it.
        if (popup.isVisible || System.currentTimeMillis() - closedAtMs < REOPEN_DELAY_MS) {
            popup.isVisible = false
            return
        }
        val inner = POPUP_WIDTH - PAD * 2 - 2
        panel.preferredSize = Dimension(inner, panel.heightForWidth(inner))
        popup.pack()
        // The dropdown opens under the button, with its right edge at the right edge of the button.
        popup.show(button, button.width - popup.preferredSize.width, button.height + 4)
    }

    private companion object {
        const val PAD = 12
        const val POPUP_WIDTH = 380
        const val REOPEN_DELAY_MS = 250L
    }
}

/**
 * The filter button on a content card: a filter icon in a small frame. With a filter, the icon is lime and a badge
 * shows the number of selected values.
 */
internal class FilterButton(componentName: String) : JButton() {
    private val normalIcon = ExportUi.icon(Material2AL.FILTER_ALT, 16, Palette.FG_2)
    private val hoverIcon = ExportUi.icon(Material2AL.FILTER_ALT, 16, Palette.FG)
    private val activeIcon = ExportUi.icon(Material2AL.FILTER_ALT, 16, Palette.LIME)

    /** The number of selected values. 0 shows no badge. */
    var selectedCount: Int = 0
        set(value) {
            field = value
            getAccessibleContext().accessibleDescription = if (value > 0) tr("export.filter.active") else null
            revalidate()
            repaint()
        }

    init {
        name = componentName
        plain()
        toolTipText = tr("export.filter.title")
        getAccessibleContext().accessibleName = tr("export.filter.title")
    }

    private val badge: String get() = selectedCount.toString()
    private val badgeFont get() = ExportUi.font(11f, Weight.BOLD)

    override fun getPreferredSize(): Dimension {
        val extra = if (selectedCount > 0) BADGE_GAP + ceil(ExportUi.textWidth(badge, badgeFont)).toInt() + 3 else 0
        return Dimension(SIZE + extra, SIZE)
    }

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val hover = isEnabled && model.isRollover
            val active = selectedCount > 0
            val border = when {
                active -> Palette.LIME_LINE
                hover -> Palette.HOVER_LINE
                else -> Palette.LINE_2
            }
            ExportUi.paintBox(g2, 0, 0, width, height, 4, if (hover) Palette.RAISED_2 else Palette.RAISED, border)
            val icon = when {
                active -> activeIcon
                hover -> hoverIcon
                else -> normalIcon
            }
            val iconX = (SIZE - icon.iconWidth) / 2
            icon.paintIcon(this, g2, iconX, (height - icon.iconHeight) / 2)
            if (active) {
                val metrics = badgeFont.getLineMetrics(badge, ExportUi.frc)
                g2.font = badgeFont
                g2.color = Palette.LIME
                g2.drawString(badge, (iconX + icon.iconWidth + BADGE_GAP).toFloat(), (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val SIZE = 30
        const val BADGE_GAP = 3
    }
}

/**
 * The content of the filter dropdown (B-69). Three groups of toggle chips: the player who made the ending, the ending,
 * and the error kind. The user can select more than one chip in a group. [onChange] gets the new filter after each
 * click.
 */
internal class PointFilterPanel(private val onChange: (PointFilter) -> Unit) : Stack(8) {
    private val player1 = FilterChip("export-filter-player-1", "")
    private val player2 = FilterChip("export-filter-player-2", "")
    private val endings = linkedMapOf(
        FilterEnding.ACE to FilterChip("export-filter-ace", tr("stats.row.aces")),
        FilterEnding.UNRETURNED_SERVE to FilterChip("export-filter-unreturned-serve", tr("stats.row.unreturnedServes")),
        FilterEnding.WINNER to FilterChip("export-filter-winner", tr("stats.row.winners")),
        FilterEnding.FORCED_ERROR to FilterChip("export-filter-forced-error", tr("stats.row.forcedErrors")),
        FilterEnding.UNFORCED_ERROR to FilterChip("export-filter-unforced-error", tr("stats.row.unforcedErrors")),
        FilterEnding.DOUBLE_FAULT to FilterChip("export-filter-double-fault", tr("stats.row.doubleFaults")),
    )
    private val errorKinds = linkedMapOf(
        ErrorKind.NET to FilterChip("export-filter-net", tr("pointEdit.errorKind.net")),
        ErrorKind.LONG to FilterChip("export-filter-long", tr("pointEdit.errorKind.long")),
        ErrorKind.WIDE to FilterChip("export-filter-wide", tr("pointEdit.errorKind.wide")),
    )
    private val allChips = listOf(player1, player2) + endings.values + errorKinds.values
    private val clearButton = GhostButton(tr("export.filter.clear"), Material2AL.CLOSE).apply {
        name = "export-filter-clear"
        isVisible = false
        addActionListener { clear(notify = true) }
    }

    /** The filter of the selected chips. */
    val filter: PointFilter
        get() = PointFilter(
            players = buildSet {
                if (player1.isSelected) add(1)
                if (player2.isSelected) add(2)
            },
            endings = endings.filterValues { it.isSelected }.keys,
            errorKinds = errorKinds.filterValues { it.isSelected }.keys,
        )

    init {
        name = "export-point-filter"
        add(FilterHeader(tr("export.filter.title"), clearButton))
        add(WrapText(tr("export.filter.summary"), ExportUi.font(11.5f), Palette.FG_2, lineHeight = 1.35f))
        add(group(tr("export.filter.player"), listOf(player1, player2)))
        add(group(tr("pointEdit.ending"), endings.values.toList()))
        add(group(tr("pointEdit.errorKind"), errorKinds.values.toList()))
        allChips.forEach { chip ->
            chip.addActionListener {
                clearButton.isVisible = filter.isActive
                revalidate()
                onChange(filter)
            }
        }
        player1.toolTipText = tr("export.filter.player.tooltip")
        player2.toolTipText = tr("export.filter.player.tooltip")
    }

    /** Shows the side names on the player chips. */
    fun setPlayers(name1: String, name2: String) {
        player1.chipText = name1
        player2.chipText = name2
        revalidate()
        repaint()
    }

    /** Selects no chip. With [notify], [onChange] gets the empty filter. */
    fun clear(notify: Boolean = false) {
        allChips.forEach { it.isSelected = false }
        clearButton.isVisible = false
        revalidate()
        repaint()
        if (notify) onChange(filter)
    }

    private fun group(caption: String, chips: List<FilterChip>) = Stack(5).apply {
        add(WrapText(caption, ExportUi.font(11.5f, Weight.SEMIBOLD), Palette.FG_2))
        add(ChipFlow().apply { chips.forEach(::add) })
    }

}

/** The number of selected values of all groups. */
internal val PointFilter.selectedCount: Int get() = players.size + endings.size + errorKinds.size

/** The title of the filter box on the left and the Clear button on the right. */
private class FilterHeader(title: String, private val button: Component) : JPanel(null), HeightForWidth {
    private val titleText = WrapText(title, ExportUi.font(12.5f, Weight.SEMIBOLD), Palette.FG)

    init {
        isOpaque = false
        add(titleText)
        add(button)
    }

    private fun titleWidth(width: Int) = (width - button.preferredSize.width - GAP).coerceAtLeast(0)

    override fun heightForWidth(width: Int): Int = max(titleText.heightForWidth(titleWidth(width)), button.preferredSize.height)

    override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 380))

    override fun doLayout() {
        val titleHeight = titleText.heightForWidth(titleWidth(width))
        titleText.setBounds(0, (height - titleHeight) / 2, titleWidth(width), titleHeight)
        val size = button.preferredSize
        button.setBounds(width - size.width, (height - size.height) / 2, size.width, size.height)
    }

    private companion object {
        const val GAP = 8
    }
}

/** Chips in rows: a chip goes to the next row when the row has no space for it. */
private class ChipFlow : JPanel(null), HeightForWidth {
    init {
        isOpaque = false
    }

    /** The bounds of each chip at [width]. A chip that is wider than the row gets the full row width. */
    private fun place(width: Int): List<Pair<Component, java.awt.Rectangle>> {
        var x = 0
        var y = 0
        var rowHeight = 0
        return components.map { chip ->
            val size = chip.preferredSize
            val w = minOf(size.width, width.coerceAtLeast(1))
            if (x > 0 && x + w > width) {
                x = 0
                y += rowHeight + GAP
                rowHeight = 0
            }
            val bounds = java.awt.Rectangle(x, y, w, size.height)
            x += w + GAP
            rowHeight = max(rowHeight, size.height)
            chip to bounds
        }
    }

    override fun heightForWidth(width: Int): Int = place(width).maxOfOrNull { it.second.y + it.second.height } ?: 0

    override fun getPreferredSize(): Dimension {
        val natural = components.sumOf { it.preferredSize.width + GAP }
        return Dimension(natural, heightForWidth(if (width > 0) width else natural))
    }

    override fun getMinimumSize() = Dimension(0, preferredSize.height)

    override fun doLayout() {
        place(width).forEach { (chip, bounds) -> chip.bounds = bounds }
    }

    private companion object {
        const val GAP = 6
    }
}

/** A toggle chip of the point filter: a round frame with a label. A selected chip has a lime frame and a check. */
private class FilterChip(componentName: String, text: String) : JToggleButton() {
    var chipText: String = text
        set(value) {
            field = value
            getAccessibleContext().accessibleName = value
            revalidate()
            repaint()
        }

    init {
        name = componentName
        plain()
        getAccessibleContext().accessibleName = text
        addItemListener { repaint() }
    }

    private val chipFont get() = ExportUi.font(12f, if (isSelected) Weight.SEMIBOLD else Weight.REGULAR)

    override fun getPreferredSize() =
        Dimension(PAD_X * 2 + CHECK_SPACE + ceil(ExportUi.textWidth(chipText, ExportUi.font(12f, Weight.SEMIBOLD))).toInt() + 1, HEIGHT)

    override fun getMinimumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val border = when {
                isSelected -> Palette.LIME_LINE
                model.isRollover -> Palette.HOVER_LINE
                else -> Palette.LINE_2
            }
            ExportUi.paintBox(g2, 0, 0, width, height, height / 2, if (isSelected) Palette.RAISED_2 else null, border)
            val font = chipFont
            val metrics = font.getLineMetrics(chipText, ExportUi.frc)
            val baseline = (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent
            // A selected chip shows a check before the text. An unselected chip centers the text, so the chip
            // keeps its width.
            val textX = if (isSelected) {
                paintCheckMark(g2, PAD_X.toDouble(), height / 2.0)
                PAD_X + CHECK_SPACE
            } else {
                ((width - ExportUi.textWidth(chipText, font)) / 2f).toInt().coerceAtLeast(PAD_X)
            }
            g2.font = font
            g2.color = if (isSelected || model.isRollover) Palette.FG else Palette.FG_2
            val clip = g2.clip
            g2.clipRect(0, 0, width - PAD_X + 2, height)
            g2.drawString(chipText, textX.toFloat(), baseline)
            g2.clip = clip
        } finally {
            g2.dispose()
        }
    }

    /** A lime check mark, 10 px wide, with its left edge at [x] and its middle at [centerY]. */
    private fun paintCheckMark(g2: java.awt.Graphics2D, x: Double, centerY: Double) {
        g2.color = Palette.LIME
        g2.stroke = java.awt.BasicStroke(1.8f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
        g2.draw(java.awt.geom.Path2D.Double().apply {
            moveTo(x, centerY)
            lineTo(x + 3.5, centerY + 3.5)
            lineTo(x + 10, centerY - 3.5)
        })
    }

    private companion object {
        const val PAD_X = 11
        const val CHECK_SPACE = 16
        const val HEIGHT = 26
    }
}
