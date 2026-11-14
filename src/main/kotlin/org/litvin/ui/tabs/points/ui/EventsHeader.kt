package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.ceil
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/**
 * The "Points & events" title with the counts of marked points, favorites and comments. The counts are on one line.
 * When they do not fit in the width of the side column, a count goes to the next line.
 */
internal class EventsHeader : JPanel(null) {
    val markedCount = CountLabel(UiKit.icon(Material2AL.FLAG, 14, Palette.LIME), "points.count.marked", tr("points.count.marked.tooltip")).apply {
        name = "points-point-count"
    }
    val favoriteCount = CountLabel(UiKit.icon(Material2MZ.STAR, 14, Palette.YELLOW), "points.count.favorites", tr("points.count.favorites.tooltip")).apply {
        name = "points-favorite-count"
    }
    val commentCount = CountLabel(UiKit.icon(Material2AL.CHAT_BUBBLE_OUTLINE, 14, Palette.FG_2), "points.count.comments", tr("points.count.comments.tooltip")).apply {
        name = "points-comment-count"
    }
    private val counts = listOf(markedCount, favoriteCount, commentCount)
    private val titleFont get() = UiKit.font(16f, UiKit.Weight.SEMIBOLD)

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 14, 12, 14)
        counts.forEach(::add)
    }

    fun setCounts(marked: Int, favorites: Int, comments: Int) {
        markedCount.count = marked
        favoriteCount.count = favorites
        commentCount.count = comments
    }

    /** The number of lines of the counts at the last preferred size. */
    private var preferredLines = 1

    override fun getPreferredSize(): Dimension {
        val insets = insets
        preferredLines = placeCounts(width) { _, _, _ -> }
        return Dimension(0, insets.top + TITLE_HEIGHT + TITLE_GAP + countsHeight(preferredLines) + insets.bottom)
    }

    override fun doLayout() {
        val lines = placeCounts(width) { label, x, y -> label.setBounds(x, y, label.preferredSize.width, label.preferredSize.height) }
        // The first layout comes after the first preferred size, which did not know the width.
        if (lines != preferredLines) SwingUtilities.invokeLater { revalidate() }
    }

    /**
     * Places the counts from left to right, [COUNT_GAP] apart. A count that does not fit in [width] goes to the next
     * line. A width of 0 (before the first layout) gives one line. Returns the number of lines.
     */
    private fun placeCounts(width: Int, place: (CountLabel, Int, Int) -> Unit): Int {
        val insets = insets
        val right = if (width > 0) width - insets.right else Int.MAX_VALUE
        var x = insets.left
        var y = insets.top + TITLE_HEIGHT + TITLE_GAP
        var lines = 1
        counts.forEach { label ->
            val w = label.preferredSize.width
            if (x > insets.left && x + w > right) {
                x = insets.left
                y += label.preferredSize.height + LINE_GAP
                lines++
            }
            place(label, x, y)
            x += w + COUNT_GAP
        }
        return lines
    }

    private fun countsHeight(lines: Int): Int {
        val h = counts.maxOf { it.preferredSize.height }
        return h * lines + LINE_GAP * (lines - 1)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.drawText(g2, tr("points.events.title"), titleFont, Palette.FG, insets.left.toFloat(), insets.top.toFloat(), TITLE_HEIGHT.toFloat())
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val TITLE_HEIGHT = 20
        const val TITLE_GAP = 6
        const val COUNT_GAP = 14
        const val LINE_GAP = 4
    }
}

/**
 * One count of the header: an icon, the number in bold and a word, for example "128 marked".
 * It is a [JLabel], so its text is the plain sentence and the UI tests can read it.
 * [nounKey] is a catalog text with the plural forms of the word.
 */
internal class CountLabel(private val countIcon: Icon, private val nounKey: String, tooltip: String) : JLabel() {
    private var noun = ""
    var count: Int = -1
        set(value) {
            if (field == value) return
            field = value
            noun = tr(nounKey, "count" to value)
            text = "${DisplayFormats.integer(value)} $noun"
            revalidate()
            repaint()
        }

    private val numberFont get() = UiKit.font(12f, UiKit.Weight.BOLD)
    private val nounFont get() = UiKit.font(12f)

    init {
        toolTipText = tooltip
        count = 0
    }

    override fun getPreferredSize(): Dimension {
        val number = count.toString()
        val w = countIcon.iconWidth + GAP + UiKit.textWidth(number, numberFont) + UiKit.textWidth(" $noun", nounFont)
        return Dimension(ceil(w).toInt() + 1, HEIGHT)
    }

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            countIcon.paintIcon(this, g2, 0, (height - countIcon.iconHeight) / 2)
            var x = countIcon.iconWidth + GAP.toFloat()
            val number = count.toString()
            UiKit.drawText(g2, number, numberFont, Palette.FG, x, 0f, height.toFloat())
            x += UiKit.textWidth(number, numberFont)
            UiKit.drawText(g2, " $noun", nounFont, Palette.FG_2, x, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val GAP = 4
        const val HEIGHT = 18
    }
}
