package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.CardColumn
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.ResetAllButton
import org.litvin.ui.commons.SIDE_PANEL_WIDTH
import org.litvin.ui.commons.SidePanelHeader
import org.litvin.ui.commons.UiKit
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import kotlin.math.ceil

/**
 * The right panel of the Colors tab: a header with the changed count and "Reset all",
 * the Light and Color cards with the six sliders, and a footer.
 * When the player cannot show a live color preview, a yellow notice shows above the cards
 * and each slider tooltip tells it.
 */
internal class ColorGradePanel(liveSupported: Boolean) : JPanel(BorderLayout()) {
    val rows: Map<ColorControl, ColorRow> = ColorControl.entries.associateWith { control ->
        ColorRow(control, first = ColorControl.entries.first { it.group == control.group } == control)
    }

    val resetButton = ResetAllButton("colors-reset", "Reset Color Grade to defaults")

    private val statusLabel = JLabel().apply { name = "colors-status" }
    private val header = SidePanelHeader("Color grade", statusLabel, resetButton)

    private val notice = LivePreviewNotice().apply {
        name = "colors-live-preview-notice"
        isVisible = !liveSupported
    }

    fun slider(control: ColorControl): JSlider = rows.getValue(control).slider

    /** The number of controls that are not at the default 0. */
    val changedCount: Int get() = rows.values.count { it.isChanged }

    init {
        name = "adj-color-right"
        isOpaque = true
        background = UiKit.BG
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, UiKit.LINE)
        preferredSize = Dimension(PANEL_WIDTH, 600)
        minimumSize = Dimension(PANEL_WIDTH, 360)

        if (!liveSupported) {
            rows.values.forEach { row -> row.slider.toolTipText = row.slider.toolTipText + "\n" + UNSUPPORTED_TIP }
        }
        rows.values.forEach { row -> row.slider.addChangeListener { refreshStatus() } }

        val column = CardColumn().apply {
            add(notice)
            ColorGroup.entries.forEach { group ->
                add(ColorGroupCard(group, rows.values.filter { it.control.group == group }))
            }
        }

        add(header, BorderLayout.NORTH)
        add(column.inScrollPane(), BorderLayout.CENTER)
        add(Footer(), BorderLayout.SOUTH)
        refreshStatus()
    }

    /** Updates the header text and the Reset all style after a slider change. */
    private fun refreshStatus() = header.showChanged(changedCount, rows.size)

    /** "Saved to the project. Export uses these values." with a save icon, under a line. */
    private class Footer : JComponent() {
        private val saveIcon = UiKit.icon(Material2OutlinedMZ.SAVE, 15, UiKit.FG_3)
        private val textFont = UiKit.font(11.5f)

        init {
            name = "colors-footer"
            toolTipText = FOOTER_TEXT
        }

        override fun getPreferredSize() = Dimension(PANEL_WIDTH, FOOTER_HEIGHT)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                g2.color = UiKit.LINE
                g2.fillRect(0, 0, width, 1)
                val top = 1
                val inner = height - top
                saveIcon.paintIcon(this, g2, PAD_X, top + (inner - saveIcon.iconHeight) / 2)
                UiKit.drawText(g2, FOOTER_TEXT, textFont, UiKit.FG_3, (PAD_X + saveIcon.iconWidth + 8).toFloat(), top.toFloat(), inner.toFloat())
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        const val PANEL_WIDTH = SIDE_PANEL_WIDTH

        /** The tooltip note that each slider gets when the player cannot show a live color preview. */
        const val UNSUPPORTED_TIP =
            "Live preview for this control may not be available on this system; values will still be saved for export."
        const val NOTICE_TEXT = "Live preview may not be available on this system. The values are still saved for export."
        const val FOOTER_TEXT = "Saved to the project. Export uses these values."

        private const val PAD_X = 16
        private const val FOOTER_HEIGHT = 37
    }
}

/** The yellow notice for a system without a live color preview. The text wraps at the width of the panel. */
internal class LivePreviewNotice : JComponent(), HeightForWidth {
    private val infoIcon = UiKit.icon(Material2OutlinedAL.INFO, 17, UiKit.YELLOW)
    private val textFont = UiKit.font(12f)

    init {
        isOpaque = false
    }

    private fun textWidth(width: Int) = (width - MARGIN_X * 2 - PAD_X * 2 - infoIcon.iconWidth - GAP).toFloat()

    /** Breaks the text into lines that fit in [maxWidth]. */
    private fun lines(maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in ColorGradePanel.NOTICE_TEXT.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && UiKit.textWidth(candidate, textFont) > maxWidth) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    override fun heightForWidth(width: Int): Int = PAD_Y * 2 + ceil(lines(textWidth(width)).size * LINE).toInt()

    override fun getPreferredSize() = Dimension(ColorGradePanel.PANEL_WIDTH, heightForWidth(if (width > 0) width else ColorGradePanel.PANEL_WIDTH - 24))

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, MARGIN_X, 0, width - MARGIN_X * 2, height, 6, UiKit.YELLOW_TINT, UiKit.YELLOW_LINE)
            val iconX = MARGIN_X + PAD_X
            infoIcon.paintIcon(this, g2, iconX, PAD_Y)
            val textX = (iconX + infoIcon.iconWidth + GAP).toFloat()
            lines(textWidth(width)).forEachIndexed { index, line ->
                UiKit.drawText(g2, line, textFont, UiKit.FG, textX, PAD_Y + index * LINE, LINE)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val MARGIN_X = 4
        const val PAD_X = 10
        const val PAD_Y = 9
        const val GAP = 8
        const val LINE = 17.4f
    }
}
