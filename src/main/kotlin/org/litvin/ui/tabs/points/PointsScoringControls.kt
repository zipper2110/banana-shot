package org.litvin.ui.tabs.points

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.scoring.Outcome
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.ui.FavoriteButton
import org.litvin.ui.tabs.points.ui.OutcomeButton
import org.litvin.ui.tabs.points.ui.ScorePlayers
import org.litvin.ui.tabs.points.ui.ScoringButton
import org.litvin.ui.tabs.points.ui.ScoringUi
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** What the point controls show. A negative [index] means that no point is selected. */
internal data class PointControlsState(
    val index: Int = -1,
    val total: Int = 0,
    val favorite: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
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
) {
    val selected: Boolean get() = index >= 0
}

/**
 * The point controls. Two parts show them:
 * - [navigator], at the top of the side column: "Point x / y" with the favorite star, and Previous and Next under it.
 * - This panel, in the point bar under the video, after Point start: the serve button, the three outcome buttons,
 *   and Override score at the right end. The outcome buttons fill the remaining width.
 */
internal class PointsScoringControls(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOutcome: (Outcome) -> Unit,
    /** S or a click: the other side serves this point. */
    onSwitchServe: () -> Unit,
    onOverrideScore: () -> Unit,
) : JPanel(null) {

    val previousButton = ScoringButton(
        "Previous", keys = listOf("⇧", "R"), buttonHeight = HEIGHT, padding = 10, keyStyle = KeyChipStyle.SMALL,
    ).apply {
        name = "points-previous-point"
        toolTipText = "Shift+R — Previous Point"
    }
    val nextButton = ScoringButton(
        "Next", keys = listOf("R"), buttonHeight = HEIGHT, padding = 10, keyStyle = KeyChipStyle.SMALL,
    ).apply {
        name = "points-next-point"
        toolTipText = "R — Next Point"
    }
    val pointLabel = JLabel("No point selected", SwingConstants.LEFT).apply {
        name = "points-current-point-label"
        font = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
    }
    val favoriteButton = FavoriteButton(STAR).apply {
        name = "points-current-point-favorite"
        toolTipText = "Favorite [A]"
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

    /** The current point with Previous and Next, for the top of the side column. */
    val navigator = PointNavigator(previousButton, pointLabel, favoriteButton, nextButton)

    private var state = PointControlsState()
    private var players = ScorePlayers()

    init {
        isOpaque = false
        listOf(serveButton, player1Button, noPointButton, player2Button, overrideButton).forEach(::add)
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
        player1Button.setPlayer("${players.p1Name} scored", players.p1Color)
        player2Button.setPlayer("${players.p2Name} scored", players.p2Color)
        noPointButton.setPlayer("No score", Palette.LINE_6)
        // setPlayer gives "Point for ..." tooltips, so the buttons get their own tooltips.
        player1Button.toolTipText = outcomeTip("Q", "${players.p1Name} won the point")
        player2Button.toolTipText = outcomeTip("E", "${players.p2Name} won the point")
        noPointButton.toolTipText = outcomeTip("W", "Nobody won the point, for example a let or a replay. The score does not change")
        render(state)
    }

    private fun outcomeTip(key: String, text: String) =
        "<html>$key — $text.<br>While a point is pending, the key also sets the end of the point.</html>"

    fun render(state: PointControlsState) {
        this.state = state
        previousButton.isEnabled = state.selected && state.hasPrevious
        nextButton.isEnabled = state.selected && state.hasNext
        // Next is the lime next step when the point has a score.
        nextButton.kind = if (state.selected && state.outcome != null) ScoringButton.Kind.LIME else ScoringButton.Kind.SECONDARY
        when {
            state.pending -> {
                pointLabel.text = "Pending point"
                pointLabel.foreground = Palette.FG_2
            }
            state.selected -> {
                pointLabel.text = "Point ${state.index + 1} / ${state.total}"
                pointLabel.foreground = Palette.FG
            }
            else -> {
                pointLabel.text = "No point selected"
                pointLabel.foreground = Palette.FG_3
            }
        }
        favoriteButton.isVisible = state.selected && !state.pending
        favoriteButton.favorite = state.favorite
        overrideButton.isEnabled = state.selected && !state.pending
        // A lime pencil: the point has a score that the user typed.
        overrideButton.iconTint = if (state.overridden && overrideButton.isEnabled) Palette.LIME else null
        overrideButton.toolTipText = if (state.overridden) {
            "Override score: this point has a score that you typed. Click to change it"
        } else {
            "Override score: type the score after this point"
        }
        renderServe(state)
        // A pending point gets its winner from these buttons, so they are the next step.
        val enabled = state.pending || (state.selected && state.canScore)
        val next = state.pending || (enabled && state.outcome == null)
        val outcome = state.outcome.takeUnless { state.pending }
        player1Button.update(enabled, outcome == Outcome.P1, next)
        noPointButton.update(enabled, outcome == Outcome.NONE, next)
        player2Button.update(enabled, outcome == Outcome.P2, next)
        revalidate()
        repaint()
        navigator.revalidate()
        navigator.repaint()
    }

    private fun renderServe(state: PointControlsState) {
        val active = state.selected && !state.pending
        val server = state.server.takeIf { active }
        serveButton.isEnabled = active && state.canScore
        serveButton.color = server?.let(players::color)
        serveButton.toolTipText = when {
            server == null -> "S — mark the server of this point. The app then tracks the serve for the whole match"
            state.serverMarked -> "${players.name(server)} serves (marked on this point). S — switch the server"
            else -> "${players.name(server)} serves (computed from your serve marks). S — switch the server"
        }
    }

    private fun serveWidth(): Int = serveButton.preferredSize.width

    private val overrideFullWidth: Int get() = overrideButton.preferredWidthFor(OVERRIDE_TEXT)

    private fun outcomesWidth() = OUTCOME_MIN * 3 + OUTCOME_GAP * 2

    override fun getPreferredSize() = Dimension(
        serveWidth() + GROUP_GAP + outcomesWidth() + GROUP_GAP + overrideFullWidth,
        HEIGHT,
    )

    override fun getMinimumSize() = Dimension(0, HEIGHT)

    override fun doLayout() {
        val serve = serveWidth()
        serveButton.setBounds(0, 0, serve, HEIGHT)
        // A narrow bar shows only the pencil of Override score, so that the outcome buttons keep their width.
        val compact = width < preferredSize.width
        val text = if (compact) "" else OVERRIDE_TEXT
        if (overrideButton.text != text) overrideButton.text = text
        val override = overrideButton.preferredSize.width
        val outcomesEnd = max(0, width - override - GROUP_GAP)
        overrideButton.setBounds(outcomesEnd + GROUP_GAP, 0, override, HEIGHT)
        // The outcome buttons in three equal columns between the serve button and Override score.
        val x = serve + GROUP_GAP
        val area = max(0, outcomesEnd - x)
        val buttonWidth = (area - OUTCOME_GAP * 2) / 3
        outcomeButtons.forEachIndexed { i, button ->
            val bx = x + i * (buttonWidth + OUTCOME_GAP)
            val w = if (i == 2) outcomesEnd - bx else buttonWidth
            button.setBounds(bx, 0, max(0, w), HEIGHT)
        }
    }

    internal companion object {
        const val HEIGHT = OutcomeButton.HEIGHT
        const val STAR = 24
        private const val GROUP_GAP = 16
        private const val OUTCOME_GAP = 6
        private const val OUTCOME_MIN = 110
        private const val OVERRIDE_TEXT = "Override score"
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

/**
 * The serve button: a strip on the left and a racket in the color of the side that serves, and the S key chip.
 * Gray when the server is not known. A dark side color stands on a light disc, so that the racket stays visible on the dark button.
 */
internal class ServeButton(private val buttonHeight: Int) : JButton() {
    var color: Color? = null
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }
    private val icons = HashMap<Color, Icon>()

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        // Space must stay the play hotkey, so the button never keeps the focus.
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() =
        Dimension(STRIP + PAD * 2 + BADGE + GAP + KeyChips.width(KEY, KeyChipStyle.SMALL), buttonHeight)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ScoringUi.DISABLED_ALPHA)
            val hover = model.isRollover && isEnabled
            val sideColor = color
            // The strip on the left has the color of the side that serves, as the strips of the outcome buttons.
            val shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 8.0, 8.0)
            g2.color = if (hover) Palette.RAISED_2 else Palette.RAISED
            g2.fill(shape)
            val clip = g2.clip
            g2.clip(shape)
            g2.color = sideColor ?: Palette.LINE_6
            g2.fillRect(0, 0, STRIP, height)
            g2.clip = clip
            UiKit.paintBox(g2, 0, 0, width, height, 4, null, if (hover) Palette.HOVER_LINE else Palette.LINE_2)
            val iconColor = sideColor ?: Palette.FG_2
            val y = (height - BADGE) / 2
            val badgeX = STRIP + PAD
            if (sideColor != null && ScoringUi.needsBadge(sideColor)) {
                g2.color = Palette.NEUTRAL_LIGHT
                g2.fillOval(badgeX, y, BADGE, BADGE)
            }
            val icon = icons.getOrPut(iconColor) { UiKit.icon(Material2MZ.SPORTS_TENNIS, ICON, iconColor) }
            icon.paintIcon(this, g2, badgeX + (BADGE - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
            val chipX = badgeX + BADGE + GAP
            KeyChips.paint(g2, KEY, chipX, (height - KeyChipStyle.SMALL.height) / 2, KeyChipStyle.SMALL)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val STRIP = OutcomeButton.STRIP
        const val PAD = 10
        const val GAP = 6
        const val ICON = 17
        const val BADGE = 22
        const val KEY = "S"
    }
}
