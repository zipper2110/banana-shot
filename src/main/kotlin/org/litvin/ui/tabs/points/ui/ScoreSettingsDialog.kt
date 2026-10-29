package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ScoreboardComponent
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.FinalSetRule
import org.litvin.scoring.MatchFormatPreset
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.Sport
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
import java.awt.Cursor
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.Window
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import org.litvin.i18n.tr
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** The values that the "Scoring settings" dialog edits. */
data class ScoreSettings(
    val player1Name: String,
    val player2Name: String,
    val player1ColorHex: String,
    val player2ColorHex: String,
    val rules: MatchRulesV1,
    val sport: Sport = Sport.TENNIS,
)

/** Opens the score settings and returns the saved values, or null after Cancel. Tests replace the dialog with a fake. */
fun interface ScoreSettingsEditor {
    fun edit(parent: Component, current: ScoreSettings): ScoreSettings?
}

/**
 * Modal "Scoring settings" dialog: player names and colors, the sport, the match format (point counting rules),
 * and the fully manual scoring option. The layout comes from design/dialogs-redesign/scoring-settings.html.
 *
 * The format list holds popular formats. The deuce rule shows under the format, because the formats do not set it.
 * The other rule controls are under "Custom scoring rules". They show the rules of the selected format.
 * This part opens when the rules match no format ("Custom"), and the user can open and close it.
 * A change to a rule selects the matching format, or "Custom". The unused rules stay visible, but dim.
 *
 * The sport sets the formats in the list, the deuce rules, and the names of the sides ("Player" or "Team").
 * A change of the sport resets the rules to the defaults of the new sport.
 */
class ScoreSettingsDialog private constructor(
    owner: Window?,
    initial: ScoreSettings,
) : JDialog(owner, tr("points.scoringSettings"), Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : ScoreSettingsEditor {
        // The scoreboard shows the full name up to this length.
        const val MAX_NAME_LENGTH = ScoreboardComponent.PLAYER_NAME_MAX_CHARS
        private const val WIDTH = 560

        /** The name length from which the dialog shows the count, for example "21/24". */
        private const val COUNT_FROM = 20

        override fun edit(parent: Component, current: ScoreSettings): ScoreSettings? {
            val dialog = ScoreSettingsDialog(SwingUtilities.getWindowAncestor(parent), current)
            DialogKit.centerOnScreen(dialog)
            dialog.isVisible = true
            return dialog.result
        }
    }

    /** A combo box item with a display text. */
    private data class Choice<T>(val value: T, val label: String) {
        override fun toString(): String = label
    }

    private var rules = initial.rules.normalized()
    private var sport = initial.sport
    private var player1Color = initial.player1ColorHex
    private var player2Color = initial.player2ColorHex
    private var result: ScoreSettings? = null
    private var updatingControls = false

    private val player1Name = nameField("score-settings-player-1-name", initial.player1Name)
    private val player2Name = nameField("score-settings-player-2-name", initial.player2Name)
    private val player1Count = countLabel()
    private val player2Count = countLabel()
    private val player1ColorButton = colorButton("score-settings-player-1-color", sport.defaultSideName(1), player1Color)
    private val player2ColorButton = colorButton("score-settings-player-2-color", sport.defaultSideName(2), player2Color)
    private var player1Label: JLabel? = null
    private var player2Label: JLabel? = null

    private val sportChoice = SegmentedChoice(
        "score-settings-sport",
        Sport.entries.map { SegmentedChoice.Option(it, it.title) },
    )
    private val format = JComboBox<Choice<MatchFormatPreset>>().apply {
        name = "score-settings-format"
        DialogKit.styleCombo(this)
    }
    private val formatDescription = WrapText("", UiKit.font(12f), Palette.FG_2, 1.5f, WIDTH).apply {
        name = "score-settings-format-description"
    }
    private val bestOf = SegmentedChoice(
        "score-settings-sets",
        listOf(
            SegmentedChoice.Option(1, tr("scoreSettings.sets.one")),
            SegmentedChoice.Option(3, tr("scoreSettings.sets.bestOf", "count" to 3)),
            SegmentedChoice.Option(5, tr("scoreSettings.sets.bestOf", "count" to 5)),
            SegmentedChoice.Option(MatchRulesV1.NO_MATCH_END, tr("scoreSettings.sets.noEnd")),
        ),
    )
    private val gamesPerSet = SegmentedChoice(
        "score-settings-games-per-set",
        MatchRulesV1.GAMES_PER_SET_OPTIONS.map { SegmentedChoice.Option(it, tr("scoreSettings.games", "count" to it)) },
    )
    private val setTiebreak = SegmentedChoice("score-settings-set-tiebreak", setTiebreakOptions(rules.gamesPerSet))
    private val tiebreakPoints = SegmentedChoice(
        "score-settings-tiebreak-points",
        MatchRulesV1.TIEBREAK_POINTS_OPTIONS.map { SegmentedChoice.Option(it, tr("rules.serveTurn.points", "count" to it)) },
    )
    private val finalSet = SegmentedChoice("score-settings-final-set", finalSetOptions())
    private val deuce = SegmentedChoice("score-settings-deuce", deuceOptions())
    private val totalPoints = SegmentedChoice(
        "score-settings-total-points",
        MatchRulesV1.TOTAL_POINTS_OPTIONS.map { SegmentedChoice.Option(it, tr("rules.serveTurn.points", "count" to it)) },
    )
    private val serveTurn = SegmentedChoice(
        "score-settings-serve-turn",
        MatchRulesV1.SERVE_TURN_POINTS_OPTIONS.map { SegmentedChoice.Option(it, tr("rules.serveTurn.points", "count" to it)) },
    )
    private val manualScoring = SwitchBox(tr("scoreSettings.manual")).apply {
        name = "score-settings-manual"
        toolTipText = tr("scoreSettings.manual.tooltip")
    }
    private val manualHint = WrapText(MANUAL_HINT, UiKit.font(12f), Palette.FG_2, 1.5f, WIDTH)

    private val formatGroup = DialogGroup(tr("scoreSettings.formatGroup"), Material2MZ.RULE, "score-settings-format-group")

    /** The label of each rule control, so that a disabled rule also dims its label. */
    private val ruleLabels = mutableMapOf<Component, JLabel>()

    /** The rows under "Custom scoring rules". */
    private val customRuleRows = mutableListOf<Component>()
    private val customRulesToggle = DisclosureRow(tr("scoreSettings.customRules"), "score-settings-custom-rules")

    init {
        name = "score-settings-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        fillFormats()
        sportChoice.onChange { value -> changeSport(value) }
        format.addActionListener {
            @Suppress("UNCHECKED_CAST")
            val preset = (format.selectedItem as? Choice<MatchFormatPreset>)?.value ?: return@addActionListener
            if (updatingControls) return@addActionListener
            update { preset.applyTo(it) }
            // "Custom" has no rules of its own, so the user sets them under "Custom scoring rules".
            if (preset == MatchFormatPreset.CUSTOM) showCustomRules(true)
        }
        customRulesToggle.addActionListener { showCustomRules(!customRulesToggle.open) }
        bestOf.onChange { value -> update { it.copy(bestOfSets = value) } }
        // A new games count gets its usual tiebreak: early only for the pro set to 9 games.
        gamesPerSet.onChange { value ->
            update { it.copy(gamesPerSet = value, earlyTiebreak = value in MatchRulesV1.EARLY_TIEBREAK_GAMES) }
        }
        setTiebreak.onChange { value ->
            update { it.copy(setTiebreak = value != SetTiebreak.NONE, earlyTiebreak = value == SetTiebreak.EARLY) }
        }
        tiebreakPoints.onChange { value -> update { it.copy(tiebreakPoints = value) } }
        finalSet.onChange { value -> update { it.copy(finalSet = value) } }
        deuce.onChange { value -> update { it.copy(deuce = value) } }
        totalPoints.onChange { value -> update { it.copy(totalPoints = value) } }
        serveTurn.onChange { value -> update { it.copy(serveTurnPoints = value) } }
        manualScoring.addActionListener { update { it.copy(manualScoring = manualScoring.isSelected) } }
        player1ColorButton.addActionListener {
            chooseColor(tr("scoreSettings.sideColor", "side" to sport.defaultSideName(1)), player1Color)?.let { player1Color = it }
            syncControls()
        }
        player2ColorButton.addActionListener {
            chooseColor(tr("scoreSettings.sideColor", "side" to sport.defaultSideName(2)), player2Color)?.let { player2Color = it }
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

        val saveButton = UiButton(tr("common.save"), kind = UiButton.Kind.LIME).apply {
            name = "score-settings-save"
            addActionListener { save() }
        }
        val cancelButton = UiButton(tr("common.cancel")).apply {
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
            DialogKit.head(tr("points.scoringSettings")),
            DialogKit.scrollBody(body),
            DialogKit.footer(
                left = listOf(DialogKit.keysHint(tr("key.enter") to tr("scoreSettings.keys.save"), tr("key.escape") to tr("scoreSettings.keys.cancel"))),
                right = listOf(cancelButton, saveButton),
            ),
        )
        rootPane.defaultButton = saveButton
        DialogKit.onEscape(this) { dispose() }

        syncControls()
        setCustomRulesVisible(MatchFormatPreset.of(rules, sport) == MatchFormatPreset.CUSTOM)
        DialogKit.packToScreen(this)
        minimumSize = Dimension(width, 300)
        player1Name.requestFocusInWindow()
    }

    private fun playersGroup() = DialogGroup(tr("scoreSettings.players"), Material2AL.GROUP).apply {
        player1Label = row(sport.defaultSideName(1), playerRow(player1ColorButton, player1Name, player1Count))
        player2Label = row(sport.defaultSideName(2), playerRow(player2ColorButton, player2Name, player2Count))
    }

    private fun formatGroup() = formatGroup.apply {
        row(tr("projects.new.sport"), sportChoice)
        ruleLabels[format] = row(tr("rules.format"), format)
        text(formatDescription, indent = true, lineAbove = true, top = 8)
        ruleLabels[deuce] = row(tr("rules.deuce"), deuce, line = true)
        wide(customRulesToggle)
        listOf(
            bestOf to tr("scoreSettings.sets"),
            gamesPerSet to tr("scoreSettings.gamesPerSet"),
            setTiebreak to tr("scoreSettings.setTiebreak"),
            tiebreakPoints to tr("scoreSettings.tiebreakPoints"),
            finalSet to tr("scoreSettings.finalSet"),
            totalPoints to tr("rules.totalPoints"),
            serveTurn to tr("rules.serveTurn"),
        ).forEach { (control, label) ->
            val rowLabel = row(label, control)
            ruleLabels[control] = rowLabel
            customRuleRows += rowLabel.parent
        }
    }

    /** Opens or closes "Custom scoring rules". The dialog grows or shrinks, and its top edge stays. */
    private fun showCustomRules(open: Boolean) {
        if (open == customRulesToggle.open) return
        setCustomRulesVisible(open)
        val left = x
        val top = y
        DialogKit.packToScreen(this)
        val config = graphicsConfiguration ?: return
        val insets = toolkit.getScreenInsets(config)
        val screenTop = config.bounds.y + insets.top
        val screenBottom = config.bounds.y + config.bounds.height - insets.bottom
        setLocation(left, max(screenTop, min(top, screenBottom - height)))
    }

    private fun setCustomRulesVisible(open: Boolean) {
        customRulesToggle.open = open
        customRuleRows.forEach { it.isVisible = open }
        formatGroup.revalidate()
    }

    /** The formats of the current sport. */
    private fun fillFormats() {
        updatingControls = true
        try {
            format.removeAllItems()
            MatchFormatPreset.forSport(sport).forEach { format.addItem(Choice(it, it.title(sport))) }
        } finally {
            updatingControls = false
        }
    }

    /** Padel calls the match tiebreak the super tiebreak. */
    private fun finalSetOptions() = listOf(
        SegmentedChoice.Option(FinalSetRule.FULL_SET, tr("scoreSettings.finalSet.full")),
        SegmentedChoice.Option(
            FinalSetRule.MATCH_TIEBREAK,
            if (sport == Sport.PADEL) tr("scoreSettings.finalSet.superTiebreak") else tr("scoreSettings.finalSet.matchTiebreak"),
            tr("rules.serveTurn.points", "count" to MatchRulesV1.MATCH_TIEBREAK_POINTS),
        ),
    )

    /** Two rules have space for the full explanation. With three rules, the tooltip has it. */
    private fun deuceOptions(): List<SegmentedChoice.Option<DeuceRule>> {
        val deuceRules = sport.deuceRules(rules.deuce)
        return deuceRules.map { rule ->
            val sub = if (deuceRules.size > 2) sport.deuceShortDescription(rule) else sport.deuceDescription(rule)
            SegmentedChoice.Option(rule, sport.deuceTitle(rule), sub, tooltip = sport.deuceDescription(rule))
        }
    }

    /**
     * Changes the sport. The rules get the defaults of the new sport, because the old format can be one that the new
     * sport does not have. Manual scoring stays. A default side name changes too, for example "Player 1" to "Team 1".
     */
    private fun changeSport(value: Sport) {
        if (updatingControls || value == sport) return
        val old = sport
        sport = value
        rules = value.defaultRules().copy(manualScoring = rules.manualScoring)
        listOf(player1Name to 1, player2Name to 2).forEach { (field, side) ->
            val text = field.text.trim()
            if (text.isEmpty() || text == old.defaultSideName(side)) field.text = value.defaultSideName(side)
        }
        player1Label?.text = value.defaultSideName(1)
        player2Label?.text = value.defaultSideName(2)
        player1ColorButton.getAccessibleContext().accessibleName = tr("scoreSettings.sideColor", "side" to value.defaultSideName(1))
        player2ColorButton.getAccessibleContext().accessibleName = tr("scoreSettings.sideColor", "side" to value.defaultSideName(2))
        fillFormats()
        syncControls()
    }

    private fun manualGroup() = DialogGroup(tr("scoreSettings.manualGroup"), Material2MZ.TOUCH_APP).apply {
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
            sportChoice.selected = sport
            val preset = MatchFormatPreset.of(rules, sport)
            for (i in 0 until format.itemCount) {
                if (format.getItemAt(i).value == preset && format.selectedIndex != i) format.selectedIndex = i
            }
            bestOf.selected = rules.bestOfSets
            gamesPerSet.selected = rules.gamesPerSet
            // The tiebreak choice names the game score, for example "Tiebreak at 6–6".
            setTiebreak.setOptions(setTiebreakOptions(rules.gamesPerSet))
            setTiebreak.selected = when {
                !rules.setTiebreak -> SetTiebreak.NONE
                rules.earlyTiebreak -> SetTiebreak.EARLY
                else -> SetTiebreak.AT_GAMES
            }
            tiebreakPoints.selected = rules.tiebreakPoints
            finalSet.setOptions(finalSetOptions())
            finalSet.selected = rules.finalSet
            deuce.setOptions(deuceOptions())
            deuce.selected = rules.deuce
            totalPoints.selected = rules.totalPoints
            serveTurn.selected = rules.serveTurnPoints
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
            val totalPointsMatch = automatic && rules.structure == MatchStructure.TOTAL_POINTS
            setRuleEnabled(totalPoints, totalPointsMatch)
            setRuleEnabled(serveTurn, totalPointsMatch)
            formatDescription.runs = listOf(
                TextRun(preset.description(sport), UiKit.font(12f), if (automatic) Palette.FG_2 else Palette.FG_3),
            )
            formatGroup.aside.text = if (automatic) "" else tr("scoreSettings.formatGroup.manual")
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
            sport = sport,
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
        toolTipText = tr("scoreSettings.sideColor.tooltip", "side" to player)
        getAccessibleContext().accessibleName = tr("scoreSettings.sideColor", "side" to player)
    }

    /** The pro set to 9 games also offers the tiebreak at 8–8. Then the set ends at 9 games, for example 9–8. */
    private fun setTiebreakOptions(games: Int): List<SegmentedChoice.Option<SetTiebreak>> {
        val early = if (games in MatchRulesV1.EARLY_TIEBREAK_GAMES) {
            listOf(
                SegmentedChoice.Option(
                    SetTiebreak.EARLY,
                    tr("scoreSettings.setTiebreak.at", "games" to games - 1),
                    tr("scoreSettings.setTiebreak.endsAt", "games" to games),
                ),
            )
        } else {
            emptyList()
        }
        return early + listOf(
            SegmentedChoice.Option(SetTiebreak.AT_GAMES, tr("scoreSettings.setTiebreak.at", "games" to games)),
            SegmentedChoice.Option(SetTiebreak.NONE, tr("scoreSettings.setTiebreak.none"), tr("scoreSettings.setTiebreak.none.sub")),
        )
    }

    private enum class SetTiebreak { AT_GAMES, EARLY, NONE }
}

private val MANUAL_HINT: String get() = tr("scoreSettings.manual.hint")

/** A full-width row that opens and closes the rows under it: the text on the left and a chevron on the right. */
private class DisclosureRow(private val text: String, componentName: String) : JButton() {
    var open = false
        set(value) {
            field = value
            repaint()
        }

    init {
        name = componentName
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        getAccessibleContext().accessibleName = text
    }

    override fun getPreferredSize() = Dimension(ceil(UiKit.textWidth(text, FONT)).toInt() + 8 + ICON, HEIGHT_PX)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, HEIGHT_PX)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val color = if (model.isRollover || isFocusOwner) Palette.FG else Palette.FG_2
            UiKit.drawText(g2, text, FONT, color, 0f, 0f, height.toFloat())
            val chevron = UiKit.icon(if (open) Material2AL.EXPAND_LESS else Material2AL.EXPAND_MORE, ICON, color)
            chevron.paintIcon(this, g2, width - chevron.iconWidth, (height - chevron.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val ICON = 18
        const val HEIGHT_PX = 30
        val FONT = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
    }
}
