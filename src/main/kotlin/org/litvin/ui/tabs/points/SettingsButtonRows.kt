package org.litvin.ui.tabs.points

import java.awt.Dimension
import javax.swing.JPanel

/**
 * The two settings buttons under the points list: in one row, each in one half of the width, or in two rows when one
 * text does not fit in its half (B-11 E4-T8). The side column has a fixed width, so the rows change only with a
 * language or with the screen scale.
 */
internal class SettingsButtonRows : JPanel(null) {
    private val buttons get() = components.filter { it.isVisible }

    /** The rows that the last preferred size used. */
    private var preferredTwoRows = false

    private fun twoRows(w: Int): Boolean {
        val half = (w - insets.left - insets.right - GAP) / 2
        return w > 0 && buttons.any { it.preferredSize.width > half }
    }

    private fun rowHeight() = buttons.maxOfOrNull { it.preferredSize.height } ?: 0

    override fun getPreferredSize(): Dimension {
        preferredTwoRows = twoRows(width)
        val rows = if (preferredTwoRows) buttons.size else 1
        return Dimension(0, insets.top + insets.bottom + rowHeight() * rows + GAP * (rows - 1))
    }

    override fun doLayout() {
        val twoRows = twoRows(width)
        val x = insets.left
        val w = width - insets.left - insets.right
        val h = rowHeight()
        if (twoRows) {
            buttons.forEachIndexed { i, button -> button.setBounds(x, insets.top + i * (h + GAP), w, h) }
        } else {
            val half = (w - GAP) / 2
            buttons.forEachIndexed { i, button ->
                button.setBounds(x + i * (half + GAP), insets.top, if (i == buttons.lastIndex) w - i * (half + GAP) else half, h)
            }
        }
        // The first layout comes after the first preferred size, which did not know the width.
        if (twoRows != preferredTwoRows) javax.swing.SwingUtilities.invokeLater { revalidate() }
    }

    private companion object {
        const val GAP = 8
    }
}
