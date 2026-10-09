package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchFormatPreset
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.Sport
import org.litvin.ui.commons.DialogGroup
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.Component
import javax.swing.JComboBox
import javax.swing.JLabel

/**
 * The padel rules of the "New project" dialog: the match format, the deuce rule, and the points of an Americano match.
 * The Scoring settings show the same rules, and the user can change them there later.
 * A rule that the selected format does not use stays visible, but dim, so the dialog does not change its height.
 */
internal class PadelRulesGroup(initial: MatchRulesV1) {
    /** The card with the rules. */
    val component = DialogGroup("Padel match", Material2MZ.RULE, "new-project-padel")

    /** A combo box item with a display text. */
    private data class Choice(val value: MatchFormatPreset, val label: String) {
        override fun toString(): String = label
    }

    /** The rules that the controls show. */
    var rules: MatchRulesV1 = initial.normalized()
        private set

    private var updating = false
    private val labels = mutableMapOf<Component, JLabel>()

    private val format = JComboBox<Choice>().apply {
        name = "new-project-padel-format"
        MatchFormatPreset.forSport(Sport.PADEL).forEach { addItem(Choice(it, it.title(Sport.PADEL))) }
        DialogKit.styleCombo(this)
    }
    private val formatDescription = WrapText("", UiKit.font(12f), Palette.FG_2, 1.5f).apply {
        name = "new-project-padel-format-description"
    }
    private val deuce = SegmentedChoice(
        "new-project-padel-deuce",
        Sport.PADEL.deuceRules(DeuceRule.STAR_POINT).map { rule ->
            SegmentedChoice.Option(
                rule,
                Sport.PADEL.deuceTitle(rule),
                Sport.PADEL.deuceShortDescription(rule),
                tooltip = Sport.PADEL.deuceDescription(rule),
            )
        },
    )
    private val totalPoints = SegmentedChoice(
        "new-project-padel-total-points",
        MatchRulesV1.TOTAL_POINTS_OPTIONS.map { SegmentedChoice.Option(it, "$it") },
    )
    private val serveTurn = SegmentedChoice(
        "new-project-padel-serve-turn",
        MatchRulesV1.SERVE_TURN_POINTS_OPTIONS.map { SegmentedChoice.Option(it, "$it points") },
    )

    init {
        component.aside.text = "You can change it later"
        component.aside.toolTipText = "The Scoring settings show the same rules."
        labels[format] = component.row("Format", format)
        component.text(formatDescription, indent = true)
        labels[deuce] = component.row("Deuce", deuce)
        labels[totalPoints] = component.row("Total points", totalPoints)
        labels[serveTurn] = component.row("Serve turn", serveTurn)

        format.addActionListener {
            val preset = (format.selectedItem as? Choice)?.value ?: return@addActionListener
            update { preset.applyTo(it) }
        }
        deuce.onChange { value -> update { it.copy(deuce = value) } }
        totalPoints.onChange { value -> update { it.copy(totalPoints = value) } }
        serveTurn.onChange { value -> update { it.copy(serveTurnPoints = value) } }
        sync()
    }

    private fun update(change: (MatchRulesV1) -> MatchRulesV1) {
        if (updating) return
        rules = change(rules).normalized()
        sync()
    }

    private fun sync() {
        updating = true
        try {
            val preset = MatchFormatPreset.of(rules, Sport.PADEL)
            for (i in 0 until format.itemCount) {
                if (format.getItemAt(i).value == preset && format.selectedIndex != i) format.selectedIndex = i
            }
            formatDescription.runs = listOf(TextRun(preset.description(Sport.PADEL), UiKit.font(12f), Palette.FG_2))
            deuce.selected = rules.deuce
            totalPoints.selected = rules.totalPoints
            serveTurn.selected = rules.serveTurnPoints

            val games = rules.structure == MatchStructure.SETS || rules.structure == MatchStructure.GAMES_ONLY
            val americano = rules.structure == MatchStructure.TOTAL_POINTS
            setEnabled(deuce, games)
            setEnabled(totalPoints, americano)
            setEnabled(serveTurn, americano)
        } finally {
            updating = false
        }
    }

    private fun setEnabled(component: Component, enabled: Boolean) {
        component.isEnabled = enabled
        labels[component]?.foreground = if (enabled) Palette.FG_2 else Palette.FG_3
    }
}
