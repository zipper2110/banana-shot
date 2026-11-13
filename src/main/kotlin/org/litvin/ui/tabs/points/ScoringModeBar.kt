package org.litvin.ui.tabs.points

import org.litvin.i18n.tr
import org.litvin.scoring.ScoringMode
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.UiKit
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * The scoring mode switch above the point bar (B-68, "Scoring modes"): "Scoring mode" and a segmented control with
 * Basic and Advanced. Only tennis projects show it. In advanced mode, the bar also has the project option "Ask error
 * kind" (B-68, "Follow-up questions"). The controls never take the keyboard focus, so that Space stays the play key and
 * the digit keys stay the ending keys.
 */
internal class ScoringModeBar(
    onSelect: (ScoringMode) -> Unit,
    /** The user turned the option "Ask error kind" on or off. */
    onAskErrorKind: (Boolean) -> Unit = {},
) : JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)) {
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
    private val askErrorKind = SwitchBox(tr("points.mode.askErrorKind"), 12f).apply {
        name = "points-ask-error-kind"
        toolTipText = tr("points.mode.askErrorKind.tooltip")
        isFocusable = false
        addActionListener { onAskErrorKind(isSelected) }
    }

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
        // A gap between the mode switch and the option of the mode.
        add(Box.createHorizontalStrut(8))
        add(askErrorKind)
        isVisible = false
    }

    /**
     * Shows the switch with [mode] selected, or hides it when [visible] is false. Advanced mode also shows the option
     * "Ask error kind" with the value [askErrorKind].
     */
    fun render(visible: Boolean, mode: ScoringMode, askErrorKind: Boolean = false) {
        if (choice.selected != mode) choice.selected = mode
        if (this.askErrorKind.isSelected != askErrorKind) this.askErrorKind.isSelected = askErrorKind
        val showOption = mode == ScoringMode.ADVANCED
        if (this.askErrorKind.isVisible != showOption) {
            this.askErrorKind.isVisible = showOption
            revalidate()
        }
        if (isVisible != visible) {
            isVisible = visible
            parent?.revalidate()
        }
    }
}
