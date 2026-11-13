package org.litvin.ui.tabs.points

import org.litvin.i18n.tr
import org.litvin.scoring.ScoringMode
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.UiKit
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * The scoring mode switch above the point bar (B-68, "Scoring modes"): "Scoring mode" and a segmented control with
 * Basic and Advanced. Only tennis projects show it. The switch never takes the keyboard focus, so that Space stays the
 * play key and the digit keys stay the ending keys.
 */
internal class ScoringModeBar(onSelect: (ScoringMode) -> Unit) : JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)) {
    private val caption = JLabel(tr("points.mode.caption")).apply {
        name = "points-scoring-mode-label"
        font = UiKit.font(12f)
        foreground = Palette.FG_2
    }
    private val choice = SegmentedChoice(
        "points-scoring-mode",
        ScoringMode.entries.map {
            SegmentedChoice.Option(it, ScoringModeDefaults.label(it), tooltip = ScoringModeDefaults.tooltip(it))
        },
        focusable = false,
    ).apply { onChange(onSelect) }

    init {
        name = "points-scoring-mode-bar"
        isOpaque = true
        background = Palette.OVERLAY
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(6, 8, 6, 16),
        )
        add(caption)
        add(choice)
        isVisible = false
    }

    /** Shows the switch with [mode] selected, or hides it when [visible] is false. */
    fun render(visible: Boolean, mode: ScoringMode) {
        if (choice.selected != mode) choice.selected = mode
        if (isVisible != visible) {
            isVisible = visible
            parent?.revalidate()
        }
    }
}
