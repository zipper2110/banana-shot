package org.litvin.ui.tabs.scoring.ui

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ScoreboardComponent
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.FinalSetRule
import org.litvin.scoring.MatchFormatPreset
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStructure
import org.litvin.ui.commons.ColorPickerDialog
import org.litvin.ui.commons.DialogGroup
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.SwatchButton
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Insets
import java.awt.Window
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument

/** The values that the "Scoring settings" dialog edits. */
data class ScoreSettings(
    val player1Name: String,
    val player2Name: String,
    val player1ColorHex: String,
    val player2ColorHex: String,
    val rules: MatchRulesV1,
)

/** Opens the score settings and returns the saved values, or null after Cancel. Tests replace the dialog with a fake. */
fun interface ScoreSettingsEditor {
    fun edit(parent: Component, current: ScoreSettings): ScoreSettings?
}

/**
 * Modal "Scoring settings" dialog: player names and colors, the match format (point counting rules),
 * and the fully manual scoring option. The layout comes from design/dialogs-redesign/scoring-settings.html.
 *
 * The format list holds popular formats. The rule controls under it show the rules of the selected format.
 * A change to a rule selects the matching format, or "Custom". The unused rules stay visible, but dim,
 * so the dialog does not change its height.
 */
class ScoreSettingsDialog private constructor(
    owner: Window?,
    initial: ScoreSettings,
) : JDialog(owner, "Scoring settings", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : ScoreSettingsEditor {
        const val MAX_NAME_LENGTH = 24
        private const val WIDTH = 560

        /** The name length from which the dialog shows the count, for example "21/24". */
        private const val COUNT_FROM = 20

        override fun edit(parent: Component, current: ScoreSettings): ScoreSettings? {
            val dialog = ScoreSettingsDialog(SwingUtilities.getWindowAncestor(parent), current)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    /** A combo box item with a display text. */
    private data class Choice<T>(val value: T, val label: String) {
        override fun toString(): String = label
    }

    private var rules = initial.rules.normalized()
    private var player1Color = initial.player1ColorHex
    private var player2Color = initial.player2ColorHex
    private var result: ScoreSettings? = null
    private var updatingControls = false

    private val player1Name = nameField("score-settings-player-1-name", initial.player1Name)
    private val player2Name = nameField("score-settings-player-2-name", initial.player2Name)
    private val player1Count = countLabel()
    private val player2Count = countLabel()
    private val player1ColorButton = colorButton("score-settings-player-1-color", "Player 1", player1Color)
    private val player2ColorButton = colorButton("score-settings-player-2-color", "Player 2", player2Color)

    private val format = JComboBox<Choice<MatchFormatPreset>>().apply {
        name = "score-settings-format"
        MatchFormatPreset.entries.forEach { addItem(Choice(it, it.title)) }
        DialogKit.styleCombo(this)
    }
    private val formatDescription = WrapText("", UiKit.font(12f), Palette.FG_2, 1.5f, WIDTH).apply {
        name = "score-settings-format-description"
    }
    private val bestOf = SegmentedChoice(
        "score-settings-sets",
        listOf(SegmentedChoice.Option(1, "One set"), SegmentedChoice.Option(3, "Best of 3"), SegmentedChoice.Option(5, "Best of 5")),
    )
    private val gamesPerSet = SegmentedChoice(
        "score-settings-games-per-set",
        MatchRulesV1.GAMES_PER_SET_OPTIONS.map { SegmentedChoice.Option(it, "$it games") },
    )
    private val setTiebreak = SegmentedChoice("score-settings-set-tiebreak", setTiebreakOptions(rules.gamesPerSet))
    private val tiebreakPoints = SegmentedChoice(
        "score-settings-tiebreak-points",
        MatchRulesV1.TIEBREAK_POINTS_OPTIONS.map { SegmentedChoice.Option(it, "$it points") },
    )
    private val finalSet = SegmentedChoice(
        "score-settings-final-set",
        listOf(
            SegmentedChoice.Option(FinalSetRule.FULL_SET, "Full set"),
            SegmentedChoice.Option(FinalSetRule.MATCH_TIEBREAK, "Match tiebreak", "${MatchRulesV1.MATCH_TIEBREAK_POINTS} points"),
        ),
    )
    private val deuce = SegmentedChoice(
        "score-settings-deuce",
        listOf(
            SegmentedChoice.Option(DeuceRule.ADVANTAGE, "Advantage", "win by 2 points"),
            SegmentedChoice.Option(DeuceRule.NO_AD, "No-ad", "deciding point at 40–40"),
        ),
    )
    private val manualScoring = SwitchBox("Fully manual scoring").apply {
        name = "score-settings-manual"
        toolTipText = "The app counts points only. You mark each game and set win."
    }
    private val manualHint = WrapText(MANUAL_HINT, UiKit.font(12f), Palette.FG_2, 1.5f, WIDTH)

    private val formatGroup = DialogGroup("Match format", Material2MZ.RULE, "score-settings-format-group")

    /** The label of each rule control, so that a disabled rule also dims its label. */
    private val ruleLabels = mutableMapOf<Component, JLabel>()

    init {
        name = "score-settings-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        format.addActionListener {
            @Suppress("UNCHECKED_CAST")
            val preset = (format.selectedItem as? Choice<MatchFormatPreset>)?.value ?: return@addActionListener
            update { preset.applyTo(it) }
        }
        bestOf.onChange { value -> update { it.copy(bestOfSets = value) } }
        gamesPerSet.onChange { value -> update { it.copy(gamesPerSet = value) } }
        setTiebreak.onChange { value -> update { it.copy(setTiebreak = value) } }
        tiebreakPoints.onChange { value -> update { it.copy(tiebreakPoints = value) } }
        finalSet.onChange { value -> update { it.copy(finalSet = value) } }
        deuce.onChange { value -> update { it.copy(deuce = value) } }
        manualScoring.addActionListener { update { it.copy(manualScoring = manualScoring.isSelected) } }
        player1ColorButton.addActionListener {
            chooseColor("Player 1 color", player1Color)?.let { player1Color = it }
            syncControls()
        }
        player2ColorButton.addActionListener {
            chooseColor("Player 2 color", player2Color)?.let { player2Color = it }
            syncControls()
        }
        listOf(player1Name to player1Count, player2Name to player2Count).forEach { (field, count) ->
            field.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = showCount(field, count)
                override fun removeUpdate(e: DocumentEvent) = showCount(field, count)
                override fun changedUpdate(e: DocumentEvent) = showCount(field, count)
            })
            showCount(field, count)
        }

        val saveButton = UiButton("Save", kind = UiButton.Kind.LIME).apply {
            name = "score-settings-save"
            addActionListener { save() }
        }
        val cancelButton = UiButton("Cancel").apply {
            name = "score-settings-cancel"
            addActionListener { dispose() }
        }

        val body = Stack(pad = Insets(8, DialogKit.PAD_X, 14, DialogKit.PAD_X), gap = 12).apply {
            add(playersGroup())
            add(formatGroup())
            add(manualGroup())
        }
        contentPane = DialogKit.content(
            WIDTH,
            DialogKit.head("Scoring settings"),
            DialogKit.scrollBody(body),
            DialogKit.footer(
                left = listOf(DialogKit.keysHint("Enter" to "save", "Esc" to "cancel")),
                right = listOf(cancelButton, saveButton),
            ),
        )
        rootPane.defaultButton = saveButton
        DialogKit.onEscape(this) { dispose() }

        syncControls()
        DialogKit.packToScreen(this)
        minimumSize = Dimension(width, 300)
        player1Name.requestFocusInWindow()
    }

    private fun playersGroup() = DialogGroup("Players", Material2AL.GROUP).apply {
        row("Player 1", playerRow(player1ColorButton, player1Name, player1Count))
        row("Player 2", playerRow(player2ColorButton, player2Name, player2Count))
    }

    private fun formatGroup() = formatGroup.apply {
        ruleLabels[format] = row("Format", format)
        text(formatDescription, indent = true, lineAbove = true, top = 8)
        ruleLabels[bestOf] = row("Sets", bestOf)
        ruleLabels[gamesPerSet] = row("Games in a set", gamesPerSet)
        ruleLabels[setTiebreak] = row("Set tiebreak", setTiebreak)
        ruleLabels[tiebreakPoints] = row("Tiebreak to", tiebreakPoints)
        ruleLabels[finalSet] = row("Deciding set", finalSet)
        ruleLabels[deuce] = row("Deuce", deuce)
    }

    private fun manualGroup() = DialogGroup("Manual scoring", Material2MZ.TOUCH_APP).apply {
        wide(manualScoring)
        text(manualHint, top = 2)
    }

    private fun playerRow(colorButton: SwatchButton, field: JTextField, count: JLabel) = JPanel(BorderLayout(8, 0)).apply {
        isOpaque = false
        add(colorButton, BorderLayout.WEST)
        add(DialogKit.inputBox(field), BorderLayout.CENTER)
        add(count, BorderLayout.EAST)
    }

    private fun showCount(field: JTextField, count: JLabel) {
        val length = field.text.length
        count.text = if (length >= COUNT_FROM) "$length/$MAX_NAME_LENGTH" else ""
    }

    /** Applies [change] to the rules, then refreshes the controls. */
    private fun update(change: (MatchRulesV1) -> MatchRulesV1) {
        if (updatingControls) return
        rules = change(rules).normalized()
        syncControls()
    }

    private fun syncControls() {
        updatingControls = true
        try {
            val preset = MatchFormatPreset.of(rules)
            for (i in 0 until format.itemCount) {
                if (format.getItemAt(i).value == preset && format.selectedIndex != i) format.selectedIndex = i
            }
            bestOf.selected = rules.bestOfSets
            gamesPerSet.selected = rules.gamesPerSet
            // The tiebreak choice names the game score, for example "Tiebreak at 6–6".
            setTiebreak.setOptions(setTiebreakOptions(rules.gamesPerSet))
            setTiebreak.selected = rules.setTiebreak
            tiebreakPoints.selected = rules.tiebreakPoints
            finalSet.selected = rules.finalSet
            deuce.selected = rules.deuce
            manualScoring.isSelected = rules.manualScoring

            val automatic = !rules.manualScoring
            val sets = rules.structure == MatchStructure.SETS
            setRuleEnabled(format, automatic)
            setRuleEnabled(bestOf, automatic && sets)
            setRuleEnabled(gamesPerSet, automatic && sets)
            setRuleEnabled(setTiebreak, automatic && sets)
            setRuleEnabled(
                tiebreakPoints,
                automatic && (rules.structure == MatchStructure.SINGLE_TIEBREAK || (sets && rules.setTiebreak)),
            )
            setRuleEnabled(finalSet, automatic && sets && rules.bestOfSets > 1)
            // Tiebreak points have no deuce.
            setRuleEnabled(deuce, automatic && (sets || rules.structure == MatchStructure.GAMES_ONLY))
            formatDescription.runs = listOf(TextRun(preset.description, UiKit.font(12f), if (automatic) Palette.FG_2 else Palette.FG_3))
            formatGroup.aside.text = if (automatic) "" else "Not used in manual scoring"
            manualHint.runs = listOf(TextRun(MANUAL_HINT, UiKit.font(12f), if (rules.manualScoring) Palette.FG_2 else Palette.FG_3))

            player1ColorButton.color = colorOf(player1Color)
            player2ColorButton.color = colorOf(player2Color)
        } finally {
            updatingControls = false
        }
    }

    private fun setRuleEnabled(component: Component, enabled: Boolean) {
        component.isEnabled = enabled
        ruleLabels[component]?.foreground = if (enabled) Palette.FG_2 else Palette.FG_3
    }

    private fun save() {
        result = ScoreSettings(
            player1Name = player1Name.text.trim(),
            player2Name = player2Name.text.trim(),
            player1ColorHex = player1Color,
            player2ColorHex = player2Color,
            rules = rules.normalized(),
        )
        dispose()
    }

    private fun chooseColor(title: String, currentHex: String): String? {
        val chosen = ColorPickerDialog.pick(this, title, colorOf(currentHex)) ?: return null
        return ColorPickerDialog.hex(chosen)
    }

    private fun colorOf(hex: String): Color =
        Color(hex.trim().removePrefix("#").toIntOrNull(16) ?: ScoreboardComponent.DEFAULT_PLAYER1_RGB)

    private fun nameField(componentName: String, text: String) = JTextField(text, 18).apply {
        name = componentName
        (document as? AbstractDocument)?.documentFilter = MaxLengthFilter(MAX_NAME_LENGTH)
    }

    private fun countLabel() = JLabel().apply {
        font = UiKit.font(11f)
        foreground = Palette.FG_3
    }

    private fun colorButton(componentName: String, player: String, hex: String) = SwatchButton(colorOf(hex)).apply {
        name = componentName
        toolTipText = "Pick the $player color for the buttons and the scoreboard"
        getAccessibleContext().accessibleName = "$player color"
    }

    private fun setTiebreakOptions(games: Int) = listOf(
        SegmentedChoice.Option(true, "Tiebreak at $games–$games"),
        SegmentedChoice.Option(false, "No tiebreak", "win by 2 games"),
    )
}

private const val MANUAL_HINT = "The app counts points only. Use the + buttons in the score panel to mark each game and set win."
