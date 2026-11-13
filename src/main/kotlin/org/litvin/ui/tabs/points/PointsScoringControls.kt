package org.litvin.ui.tabs.points

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.scoring.Ending
import org.litvin.scoring.EndingChoice
import org.litvin.scoring.Outcome
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.ui.EndingButtons
import org.litvin.ui.tabs.points.ui.FavoriteButton
import org.litvin.ui.tabs.points.ui.OutcomeButton
import org.litvin.ui.tabs.points.ui.ScorePlayers
import org.litvin.ui.tabs.points.ui.ScoringButton
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.litvin.i18n.tr

/** What the point controls show. A negative [index] means that no point is selected. */
internal data class PointControlsState(
    val index: Int = -1,
    val total: Int = 0,
    val favorite: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    /** The outcome of the selected point. It is null only when no point is selected. */
    val outcome: Outcome? = null,
    /** False for a point without a valid length. Such a point gets no outcome. */
    val canScore: Boolean = false,
    /** True while a point waits for its end. A winner key ends the point and gives it the winner. */
    val pending: Boolean = false,
    /** 1, 2 or null when the server is not known. */
    val server: Int? = null,
    /** True when the user marked the server on this point. */
    val serverMarked: Boolean = false,
    /** True when the point has an override of the score. */
    val overridden: Boolean = false,
    /** True in advanced mode (B-68): the point bar shows the ending buttons in place of the Q and E buttons. */
    val advanced: Boolean = false,
    /** True while the point bar asks "Who serves?". The ending buttons are not active until the user answers. */
    val askServer: Boolean = false,
    /** The server (1 or 2) of the clip that the ending keys change: the pending point, or the selected point. */
    val endingServer: Int? = null,
    /** The ending button of the selected point, or null when the point has no ending. */
    val ending: EndingChoice? = null,
) {
    val selected: Boolean get() = index >= 0
}

/**
 * The point controls. Two parts show them:
 * - [navigator], at the top of the side column: "Point x / y" with the favorite star, and Previous and Next under it.
 * - This panel, in the point bar under the video, after Point start: "Winner:", the three outcome buttons,
 *   and Override score at the right end. The outcome buttons fill the remaining width.
 *   In advanced mode (B-68), the ending buttons and W take the place of the outcome buttons. Q and E have no buttons.
 *   While the bar asks "Who serves?", it shows the question and one button for each player.
 * - [serveButton], at the start of the point bar, before Point start. The point bar places it.
 */
internal class PointsScoringControls(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOutcome: (Outcome) -> Unit,
    /** S or a click: the other side serves this point. */
    onSwitchServe: () -> Unit,
    onOverrideScore: () -> Unit,
    /** An ending button of advanced mode (B-68). */
    onEnding: (EndingChoice) -> Unit = {},
    /** The answer (1 or 2) to "Who serves?". */
    onServeAnswer: (Int) -> Unit = {},
) : JPanel(null) {

    val previousButton = ScoringButton(
        tr("points.previous"), keys = listOf("⇧", "R"), buttonHeight = HEIGHT, padding = 10, keyStyle = KeyChipStyle.SMALL,
    ).apply {
        name = "points-previous-point"
        toolTipText = tr("points.previous.tooltip", "key" to tr("key.withShift", "key" to "R"))
    }
    val nextButton = ScoringButton(
        tr("points.next"), keys = listOf("R"), buttonHeight = HEIGHT, padding = 10, keyStyle = KeyChipStyle.SMALL,
    ).apply {
        name = "points-next-point"
        toolTipText = tr("points.next.tooltip", "key" to "R")
    }
    val pointLabel = JLabel(tr("points.current.none"), SwingConstants.LEFT).apply {
        name = "points-current-point-label"
        font = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
    }
    val favoriteButton = FavoriteButton(STAR).apply {
        name = "points-current-point-favorite"
        toolTipText = tr("points.favorite.tooltip", "key" to "A")
    }
    val overrideButton = ScoringButton(
        OVERRIDE_TEXT, Material2AL.EDIT, buttonHeight = HEIGHT, padding = 10,
    ).apply {
        name = "points-override-score"
    }
    val serveButton = ServeButton(HEIGHT).apply {
        name = "points-serve"
    }
    val player1Button = OutcomeButton(1, "Q").apply {
        name = "points-player-1-point"
        addActionListener { onOutcome(Outcome.P1) }
    }
    val noPointButton = OutcomeButton(0, "W").apply {
        name = "points-no-point"
        addActionListener { onOutcome(Outcome.NONE) }
    }
    val player2Button = OutcomeButton(2, "E").apply {
        name = "points-player-2-point"
        addActionListener { onOutcome(Outcome.P2) }
    }
    private val outcomeButtons = listOf(player1Button, noPointButton, player2Button)
    private val endings = EndingButtons(onEnding)
    private val serveQuestion = JLabel(tr("points.serveQuestion")).apply {
        name = "points-serve-question"
        font = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
    }
    private val serveAnswers = listOf(1, 2).map { player ->
        OutcomeButton(player, "").apply {
            name = "points-serve-answer-$player"
            addActionListener { onServeAnswer(player) }
        }
    }
    private val winnerLabel = JLabel(tr("points.outcome.caption")).apply {
        name = "points-winner-label"
        font = UiKit.font(12f)
        foreground = Palette.FG_2
    }

    /** The current point with Previous and Next, for the top of the side column. */
    val navigator = PointNavigator(previousButton, pointLabel, favoriteButton, nextButton)

    private var state = PointControlsState()
    private var players = ScorePlayers()

    init {
        isOpaque = false
        listOf(winnerLabel, player1Button, noPointButton, player2Button, overrideButton, serveQuestion).forEach(::add)
        endings.buttons.values.forEach(::add)
        serveAnswers.forEach(::add)
        previousButton.addActionListener { onPrevious() }
        nextButton.addActionListener { onNext() }
        favoriteButton.addActionListener { onToggleFavorite() }
        overrideButton.addActionListener { onOverrideScore() }
        serveButton.addActionListener { onSwitchServe() }
        setPlayers(ScorePlayers())
        render(state)
    }

    fun setPlayers(players: ScorePlayers) {
        this.players = players
        player1Button.setPlayer(players.p1Name, players.p1Color)
        player2Button.setPlayer(players.p2Name, players.p2Color)
        noPointButton.setPlayer(tr("points.outcome.noWinner"), Palette.LINE_6)
        // setPlayer gives "Point for ..." tooltips, so the buttons get their own tooltips.
        player1Button.toolTipText = outcomeTip("Q", tr("points.outcome.won.tooltip", "name" to players.p1Name))
        player2Button.toolTipText = outcomeTip("E", tr("points.outcome.won.tooltip", "name" to players.p2Name))
        noPointButton.toolTipText = outcomeTip("W", tr("points.outcome.none.tooltip"))
        endings.setPlayers(players)
        serveAnswers.forEach { button ->
            val player = if (button === serveAnswers[0]) 1 else 2
            button.setPlayer(players.name(player), players.color(player))
            button.toolTipText = tr("points.serveQuestion.tooltip", "name" to players.name(player))
        }
        render(state)
    }

    private fun outcomeTip(key: String, text: String) =
        "<html>$key — $text.<br>${tr("points.outcome.pending.tooltip")}</html>"

    fun render(state: PointControlsState) {
        this.state = state
        previousButton.isEnabled = state.selected && state.hasPrevious
        nextButton.isEnabled = state.selected && state.hasNext
        // Next is the lime next step when the point has a score.
        nextButton.kind = if (state.selected && state.outcome != null) ScoringButton.Kind.LIME else ScoringButton.Kind.SECONDARY
        when {
            state.pending -> {
                pointLabel.text = tr("points.current.pending")
                pointLabel.foreground = Palette.FG_2
            }
            state.selected -> {
                pointLabel.text = tr("points.current", "number" to state.index + 1, "total" to state.total)
                pointLabel.foreground = Palette.FG
            }
            else -> {
                pointLabel.text = tr("points.current.none")
                pointLabel.foreground = Palette.FG_3
            }
        }
        favoriteButton.isVisible = state.selected && !state.pending
        favoriteButton.favorite = state.favorite
        overrideButton.isEnabled = state.selected && !state.pending
        // A lime pencil: the point has a score that the user typed.
        overrideButton.iconTint = if (state.overridden && overrideButton.isEnabled) Palette.LIME else null
        overrideButton.toolTipText = if (state.overridden) {
            tr("points.override.typed.tooltip")
        } else {
            tr("points.override.tooltip")
        }
        renderServe(state)
        // A pending point gets its winner from these buttons, so they are the next step.
        val enabled = state.pending || (state.selected && state.canScore)
        val next = state.pending || (enabled && state.outcome == null)
        val outcome = state.outcome.takeUnless { state.pending }
        player1Button.update(enabled, outcome == Outcome.P1 && !state.advanced, next)
        noPointButton.update(enabled, outcome == Outcome.NONE, next)
        player2Button.update(enabled, outcome == Outcome.P2 && !state.advanced, next)
        val ending = state.ending.takeUnless { state.pending }
        endings.buttons.forEach { (choice, button) ->
            button.update(enabled, ending == choice, next)
        }
        renderMode(state)
        revalidate()
        repaint()
        navigator.revalidate()
        navigator.repaint()
    }

    /** Shows the buttons of the mode: Q, W, E in basic mode; the ending buttons and W in advanced mode; or the question. */
    private fun renderMode(state: PointControlsState) {
        val asking = state.advanced && state.askServer
        player1Button.isVisible = !state.advanced
        player2Button.isVisible = !state.advanced
        noPointButton.isVisible = !asking
        endings.buttons.forEach { (choice, button) ->
            // Only the group of the server shows the ace button.
            button.isVisible = state.advanced && !asking && (choice.ending != Ending.ACE || state.endingServer == choice.winner)
        }
        serveQuestion.isVisible = asking
        serveAnswers.forEach { it.isVisible = asking }
    }

    private fun renderServe(state: PointControlsState) {
        val active = state.selected && !state.pending
        val server = state.server.takeIf { active }
        serveButton.isEnabled = active && state.canScore
        serveButton.color = server?.let(players::color)
        serveButton.toolTipText = when {
            server == null -> tr("points.serve.none.tooltip", "key" to "S")
            state.serverMarked -> tr("points.serve.marked.tooltip", "name" to players.name(server), "key" to "S")
            else -> tr("points.serve.computed.tooltip", "name" to players.name(server), "key" to "S")
        }
    }

    private fun labelWidth(): Int = winnerLabel.preferredSize.width

    private val overrideFullWidth: Int get() = overrideButton.preferredWidthFor(OVERRIDE_TEXT)

    private fun outcomesWidth() = OUTCOME_MIN * 3 + OUTCOME_GAP * 2

    override fun getPreferredSize(): Dimension {
        val outcomes = if (state.advanced) {
            advancedWidths(full = false).let { it.sum() + gapsOf(it.size) }
        } else {
            labelWidth() + LABEL_GAP + outcomesWidth()
        }
        return Dimension(outcomes + GROUP_GAP + overrideFullWidth, HEIGHT)
    }

    override fun getMinimumSize() = Dimension(0, HEIGHT)

    /**
     * The width that shows "Winner:" and the full names of the outcome buttons, with the pencil of Override score.
     * In advanced mode, it is the width of the short labels of the ending buttons, so that Point start keeps its text
     * when the short labels fit.
     */
    fun fullNamesWidth(): Int {
        val pencil = GROUP_GAP + overrideButton.preferredWidthFor("")
        if (!state.advanced) return labelWidth() + LABEL_GAP + outcomeButtons.sumOf { it.fullWidth() } + OUTCOME_GAP * 2 + pencil
        return advancedWidths(full = false).let { it.sum() + gapsOf(it.size) } + pencil
    }

    /** The buttons of advanced mode in the order of the point bar: the left group, W, and the right group. */
    private fun advancedButtons(): List<OutcomeButton> {
        val visible = endings.buttons.values.filter { it.isVisible }
        val left = EndingButtons.ORDER.filter { it.winner == 1 }.mapNotNull { endings.buttons[it] }.filter { it in visible }
        val right = EndingButtons.ORDER.filter { it.winner == 2 }.mapNotNull { endings.buttons[it] }.filter { it in visible }
        return left + noPointButton + right
    }

    /** The widths of [advancedButtons] with the full labels or with the short labels. */
    private fun advancedWidths(full: Boolean): List<Int> {
        val choices = endings.buttons.entries.associate { (choice, button) -> button to choice }
        return advancedButtons().map { button ->
            val choice = choices[button] ?: return@map button.fullWidth()
            button.widthFor(if (full) endings.fullLabel(choice) else endings.shortLabel(choice))
        }
    }

    /** The gaps between [count] buttons of advanced mode. The two groups and W have a wider gap between them. */
    private fun gapsOf(count: Int): Int = (count - 1).coerceAtLeast(0) * OUTCOME_GAP + 2 * (GROUP_SPLIT - OUTCOME_GAP)

    override fun doLayout() {
        // A narrow bar shows only the pencil of Override score, so that the outcome buttons keep their width.
        val compact = width < preferredSize.width
        val text = if (compact) "" else OVERRIDE_TEXT
        if (overrideButton.text != text) overrideButton.text = text
        val override = overrideButton.preferredWidthFor(text)
        val outcomesEnd = max(0, width - override - GROUP_GAP)
        overrideButton.setBounds(outcomesEnd + GROUP_GAP, 0, override, HEIGHT)
        if (state.advanced) {
            winnerLabel.isVisible = false
            if (state.askServer) layoutQuestion(outcomesEnd) else layoutEndings(outcomesEnd)
            return
        }
        // When the full names do not fit, the bar hides "Winner:". The colors and the tooltips of the buttons still
        // tell what they do (B-11 E4-T8, E3-T22).
        val needs = outcomeButtons.map { it.fullWidth() }
        val namesWidth = needs.sum() + OUTCOME_GAP * 2
        val label = if (labelWidth() + LABEL_GAP + namesWidth <= outcomesEnd) labelWidth() else 0
        winnerLabel.isVisible = label > 0
        winnerLabel.setBounds(0, 0, label, HEIGHT)
        val x = if (label > 0) label + LABEL_GAP else 0
        val area = max(0, outcomesEnd - x - OUTCOME_GAP * 2)
        // Three equal columns when the longest name fits. Else each button gets a part of the width for its name, so
        // that the short "No point" gives its free space to the names of the players.
        val widths = if (needs.max() * 3 <= area) {
            List(3) { area / 3 }
        } else {
            needs.map { (it.toLong() * area / needs.sum().coerceAtLeast(1)).toInt() }
        }
        var bx = x
        outcomeButtons.forEachIndexed { i, button ->
            val w = if (i == 2) outcomesEnd - bx else widths[i]
            button.setBounds(bx, 0, max(0, w), HEIGHT)
            bx += w + OUTCOME_GAP
        }
    }

    /** "Who serves?" and one button for each player. */
    private fun layoutQuestion(end: Int) {
        val label = min(ceil(UiKit.textWidth(serveQuestion.text, serveQuestion.font)).toInt() + 2, end / 3)
        serveQuestion.setBounds(0, 0, label, HEIGHT)
        val x = label + LABEL_GAP
        val w = max(0, (end - x - OUTCOME_GAP) / 2)
        serveAnswers[0].setBounds(x, 0, w, HEIGHT)
        serveAnswers[1].setBounds(x + w + OUTCOME_GAP, 0, max(0, end - x - w - OUTCOME_GAP), HEIGHT)
    }

    /**
     * The ending buttons and W. The full labels show when they all fit, else the short labels. When the short labels
     * also do not fit, each button gets a part of the width for its short label, and the label ends with "…".
     */
    private fun layoutEndings(end: Int) {
        val buttons = advancedButtons()
        val area = max(0, end - gapsOf(buttons.size))
        val full = advancedWidths(full = true)
        val useFull = full.sum() <= area
        val needs = if (useFull) full else advancedWidths(full = false)
        val choices = endings.buttons.entries.associate { (choice, button) -> button to choice }
        buttons.forEach { button ->
            val choice = choices[button] ?: return@forEach
            val label = if (useFull) endings.fullLabel(choice) else endings.shortLabel(choice)
            if (button.text != label) button.text = label
        }
        val extra = max(0, area - needs.sum())
        var x = 0
        buttons.forEachIndexed { i, button ->
            val share = if (needs.sum() <= area) needs[i] + extra / buttons.size else (needs[i].toLong() * area / needs.sum().coerceAtLeast(1)).toInt()
            val w = if (i == buttons.lastIndex) end - x else share
            button.setBounds(x, 0, max(0, w), HEIGHT)
            x += w + if (button === noPointButton || buttons.getOrNull(i + 1) === noPointButton) GROUP_SPLIT else OUTCOME_GAP
        }
    }

    internal companion object {
        const val HEIGHT = OutcomeButton.HEIGHT
        /** The gap between the two groups of ending buttons and W. */
        private const val GROUP_SPLIT = 12
        const val STAR = 24
        private const val GROUP_GAP = 16
        private const val LABEL_GAP = 8
        private const val OUTCOME_GAP = 6
        private const val OUTCOME_MIN = 110
        private val OVERRIDE_TEXT: String get() = tr("points.override")
    }
}

/**
 * The current point at the top of the side column. The first row has "Point x / y" with the favorite star.
 * The second row has Previous and Next, each in one half of the width.
 */
internal class PointNavigator(
    private val previousButton: JButton,
    private val pointLabel: JLabel,
    private val favoriteButton: JButton,
    private val nextButton: JButton,
) : JPanel(null) {

    init {
        name = "points-point-navigator"
        isOpaque = false
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(14, 14, 12, 14),
        )
        listOf(pointLabel, favoriteButton, previousButton, nextButton).forEach(::add)
    }

    override fun getPreferredSize(): Dimension {
        val insets = insets
        return Dimension(0, insets.top + TITLE_HEIGHT + ROW_GAP + BUTTON_HEIGHT + insets.bottom)
    }

    override fun doLayout() {
        val insets = insets
        val x = insets.left
        val w = width - insets.left - insets.right
        var y = insets.top
        val star = if (favoriteButton.isVisible) STAR + ICON_GAP else 0
        val label = min(ceil(UiKit.textWidth(pointLabel.text, pointLabel.font)).toInt() + 4, max(0, w - star))
        pointLabel.setBounds(x, y, label, TITLE_HEIGHT)
        favoriteButton.setBounds(x + label + ICON_GAP, y + (TITLE_HEIGHT - STAR) / 2, STAR, STAR)
        y += TITLE_HEIGHT + ROW_GAP
        val half = (w - BUTTON_GAP) / 2
        previousButton.setBounds(x, y, half, BUTTON_HEIGHT)
        nextButton.setBounds(x + half + BUTTON_GAP, y, w - half - BUTTON_GAP, BUTTON_HEIGHT)
    }

    private companion object {
        const val STAR = PointsScoringControls.STAR
        const val BUTTON_HEIGHT = PointsScoringControls.HEIGHT
        const val TITLE_HEIGHT = 24
        const val ICON_GAP = 2
        const val ROW_GAP = 8
        const val BUTTON_GAP = 6
    }
}
