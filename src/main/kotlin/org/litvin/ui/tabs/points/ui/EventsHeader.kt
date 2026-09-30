package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.ceil
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit

/** The "Points & events" title with the counts of marked points, favorites and comments. */
internal class EventsHeader : JPanel(null) {
    val markedCount = CountLabel(UiKit.icon(Material2AL.FLAG, 14, Palette.LIME), "marked", "Marked points").apply {
        name = "points-point-count"
    }
    val favoriteCount = CountLabel(UiKit.icon(Material2MZ.STAR, 14, Palette.YELLOW), "favorites", "Favorite points").apply {
        name = "points-favorite-count"
    }
    val commentCount = CountLabel(UiKit.icon(Material2AL.CHAT_BUBBLE_OUTLINE, 14, Palette.FG_2), "comments", "Comments").apply {
        name = "points-comment-count"
    }
    private val counts = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        add(markedCount)
        add(Box.createHorizontalStrut(14))
        add(favoriteCount)
        add(Box.createHorizontalStrut(14))
        add(commentCount)
    }
    private val titleFont get() = UiKit.font(16f, UiKit.Weight.SEMIBOLD)

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 14, 12, 14)
        add(counts)
    }

    fun setCounts(marked: Int, favorites: Int, comments: Int) {
        markedCount.count = marked
        favoriteCount.count = favorites
        commentCount.count = comments
    }

    override fun getPreferredSize(): Dimension {
        val insets = insets
        return Dimension(0, insets.top + TITLE_HEIGHT + TITLE_GAP + counts.preferredSize.height + insets.bottom)
    }

    override fun doLayout() {
        val insets = insets
        counts.setBounds(insets.left, insets.top + TITLE_HEIGHT + TITLE_GAP, width - insets.left - insets.right, counts.preferredSize.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.drawText(g2, "Points & events", titleFont, Palette.FG, insets.left.toFloat(), insets.top.toFloat(), TITLE_HEIGHT.toFloat())
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val TITLE_HEIGHT = 20
        const val TITLE_GAP = 6
    }
}

/**
 * One count of the header: an icon, the number in bold and a word, for example "128 marked".
 * It is a [JLabel], so its text is the plain sentence and the UI tests can read it.
 */
internal class CountLabel(private val countIcon: Icon, private val noun: String, tooltip: String) : JLabel() {
    var count: Int = -1
        set(value) {
            if (field == value) return
            field = value
            text = "$value $noun"
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
