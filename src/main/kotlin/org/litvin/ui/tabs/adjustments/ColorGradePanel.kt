package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.i18n.LineBreaks
import org.litvin.ui.commons.CardColumn
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.Palette
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
import org.litvin.i18n.tr

/**
 * The Colors sub-tab of the Colors & Transform tab: a header with the changed count and "Reset all",
 * and the Light and Color cards with the six sliders.
 * When the player cannot show a live color preview, a yellow notice shows above the cards
 * and each slider tooltip tells it.
 */
internal class ColorGradePanel(liveSupported: Boolean) : JPanel(BorderLayout()) {
    val rows: Map<ColorControl, ColorRow> = ColorControl.entries.associateWith { control ->
        ColorRow(control, first = ColorControl.entries.first { it.group == control.group } == control)
    }

    val resetButton = ResetAllButton("colors-reset", tr("adjust.colorGrade.reset"))

    private val statusLabel = JLabel().apply { name = "colors-status" }
    private val header = SidePanelHeader(tr("adjust.colorGrade"), statusLabel, resetButton)

    private val notice = LivePreviewNotice().apply {
        name = "colors-live-preview-notice"
        isVisible = !liveSupported
    }

    fun slider(control: ColorControl): JSlider = rows.getValue(control).slider

    /** The number of controls that are not at the default 0. */
    val changedCount: Int get() = rows.values.count { it.isChanged }

    init {
        name = "adjust-colors"
        isOpaque = true
        background = Palette.BG
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
        refreshStatus()
    }

    /** Updates the header text and the Reset all style after a slider change. */
    private fun refreshStatus() = header.showChanged(changedCount, rows.size)

    companion object {
        const val PANEL_WIDTH = SIDE_PANEL_WIDTH

        /** The tooltip note that each slider gets when the player cannot show a live color preview. */
        val UNSUPPORTED_TIP: String get() = tr("adjust.livePreview.tooltip")
        val NOTICE_TEXT: String get() = tr("adjust.livePreview.notice")
    }
}

/**
 * A yellow notice. The text wraps at the width of the panel. By default it tells that the system has no live color
 * preview. [text] gives the text each time the notice paints, so a caller can change it and call revalidate.
 */
internal class LivePreviewNotice(
    private val text: () -> String = { ColorGradePanel.NOTICE_TEXT },
    ikon: org.kordamp.ikonli.Ikon = Material2OutlinedAL.INFO,
) : JComponent(), HeightForWidth {
    private val infoIcon = UiKit.icon(ikon, 17, Palette.YELLOW)
    private val textFont = UiKit.font(12f)

    init {
        isOpaque = false
    }

    private fun textWidth(width: Int) = (width - MARGIN_X * 2 - PAD_X * 2 - infoIcon.iconWidth - GAP).toFloat()

    /** Breaks the text into lines that fit in [maxWidth]. */
    private fun lines(maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text().split(' ')) {
            // A Japanese word breaks between its characters, and its pieces join without a space.
            LineBreaks.pieces(word).forEachIndexed { index, part ->
                val candidate = if (current.isEmpty()) part else if (index == 0) "$current $part" else "$current$part"
                if (current.isNotEmpty() && UiKit.textWidth(candidate, textFont) > maxWidth) {
                    lines += current
                    current = part
                } else {
                    current = candidate
                }
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
            UiKit.paintBox(g2, MARGIN_X, 0, width - MARGIN_X * 2, height, 6, Palette.YELLOW_TINT, Palette.YELLOW_LINE)
            val iconX = MARGIN_X + PAD_X
            infoIcon.paintIcon(this, g2, iconX, PAD_Y)
            val textX = (iconX + infoIcon.iconWidth + GAP).toFloat()
            lines(textWidth(width)).forEachIndexed { index, line ->
                UiKit.drawText(g2, line, textFont, Palette.FG, textX, PAD_Y + index * LINE, LINE)
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
