package org.litvin.ui.tabs.scoring.ui

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoringEngine.MatchState
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.max
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.UiKit

/** Everything that the score panel shows for the selected point. A negative [index] means that no point is selected. */
internal data class ScorePanelState(
    val index: Int = -1,
    val total: Int = 0,
    val favorite: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val outcome: Outcome? = null,
    /** False for a point without a valid length. Such a point gets no outcome. */
    val canScore: Boolean = false,
    /** The score after the point. */
    val after: MatchState = MatchState.INITIAL,
    /** 1, 2 or null when the server is not known. */
    val server: Int? = null,
    val serverMarked: Boolean = false,
    val manual: Boolean = false,
    val manualGame: Outcome? = null,
    val manualSet: Outcome? = null,
) {
    val selected: Boolean get() = index >= 0
}

/** The names and colors of the two players. */
internal data class ScorePlayers(
    val p1Name: String = "Player 1",
    val p2Name: String = "Player 2",
    val p1Color: Color = Color(0x4DA3FF),
    val p2Color: Color = Color(0xFF6B6B),
) {
    fun name(player: Int) = if (player == 1) p1Name else p2Name
    fun color(player: Int) = if (player == 1) p1Color else p2Color
}

/**
 * The score panel at the top of the side column (the `.panel` card of design/scoring-redesign/final.html):
 * - Previous, "Point x / y" with the favorite star, and Next,
 * - the score after the point: sets, games and points of both players, with the serve buttons,
 * - "Who won the point?" with the three outcome buttons.
 * In manual scoring, "+" buttons mark the set, game and point wins.
 */
internal class ScorePanel(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOutcome: (Outcome) -> Unit,
    onServe: (Outcome) -> Unit,
    onManualGame: (Outcome) -> Unit,
    onManualSet: (Outcome) -> Unit,
) : JPanel(null) {

    val previousButton = ScoringButton(
        "Previous", keys = listOf("⇧", "R"), kind = ScoringButton.Kind.GHOST,
        buttonHeight = NAV_HEIGHT, padding = 7, keyStyle = KeyChipStyle.SMALL,
    ).apply {
        name = "previous-point"
        toolTipText = "Shift+R — Previous Point"
    }
    val nextButton = ScoringButton(
        "Next", keys = listOf("R"), buttonHeight = NAV_HEIGHT, padding = 7, keyStyle = KeyChipStyle.SMALL,
    ).apply {
        name = "next-point"
        toolTipText = "R — Next Point"
    }
    val pointLabel = JLabel("No point selected", SwingConstants.CENTER).apply {
        name = "current-point-label"
        font = UiKit.font(13.5f, UiKit.Weight.SEMIBOLD)
    }
    val favoriteButton = FavoriteButton(24).apply {
        name = "current-point-favorite"
        toolTipText = "Favorite [A]"
    }
    val player1 = PlayerScoreRow(1, onServe, onManualGame, onManualSet, onOutcome)
    val player2 = PlayerScoreRow(2, onServe, onManualGame, onManualSet, onOutcome)
    val player1Button = OutcomeButton(1, "Q").apply {
        name = "scoring-player-1-point"
        addActionListener { onOutcome(Outcome.P1) }
    }
    val noPointButton = OutcomeButton(0, "W").apply {
        name = "no-point"
        addActionListener { onOutcome(Outcome.NONE) }
    }
    val player2Button = OutcomeButton(2, "E").apply {
        name = "scoring-player-2-point"
        addActionListener { onOutcome(Outcome.P2) }
    }

    private var players = ScorePlayers()
    private var state = ScorePanelState()
    private val captionFont get() = UiKit.trackedFont(10f, 0.08)
    private val whoFont get() = UiKit.trackedFont(10.5f, 0.08)

    init {
        isOpaque = false
        listOf(previousButton, pointLabel, favoriteButton, nextButton, player1, player2, player1Button, noPointButton, player2Button)
            .forEach(::add)
        previousButton.addActionListener { onPrevious() }
        nextButton.addActionListener { onNext() }
        favoriteButton.addActionListener { onToggleFavorite() }
        // The panel shows the tooltip of the "After the point" caption.
        toolTipText = ""
        setPlayers(players)
        render(state)
    }

    fun setPlayers(players: ScorePlayers) {
        this.players = players
        player1.players = players
        player2.players = players
        player1Button.setPlayer(players.p1Name, players.p1Color)
        player2Button.setPlayer(players.p2Name, players.p2Color)
        noPointButton.setPlayer("No point", ScoringUi.NO_POINT)
        render(state)
    }

    fun render(state: ScorePanelState) {
        this.state = state
        previousButton.isEnabled = state.selected && state.hasPrevious
        nextButton.isEnabled = state.selected && state.hasNext
        // Next is the lime next step when the point has a score.
        nextButton.kind = if (state.selected && state.outcome != null) ScoringButton.Kind.LIME else ScoringButton.Kind.SECONDARY
        if (state.selected) {
            pointLabel.text = "Point ${state.index + 1} / ${state.total}"
            pointLabel.foreground = UiKit.FG
        } else {
            pointLabel.text = "No point selected"
            pointLabel.foreground = UiKit.FG_3
        }
        favoriteButton.isVisible = state.selected
        favoriteButton.favorite = state.favorite
        player1.render(state)
        player2.render(state)
        val enabled = state.selected && state.canScore
        val next = enabled && state.outcome == null
        player1Button.update(enabled, state.outcome == Outcome.P1, next)
        noPointButton.update(enabled, state.outcome == Outcome.NONE, next)
        player2Button.update(enabled, state.outcome == Outcome.P2, next)
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension = Dimension(0, cardY(PREFERRED_HEIGHT))

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    private fun cardY(inner: Int) = MARGIN_TOP + PAD_TOP + inner + PAD_BOTTOM + 2

    private fun card() = Rectangle(MARGIN_X, MARGIN_TOP, width - MARGIN_X * 2, height - MARGIN_TOP)

    private fun content(): Rectangle {
        val card = card()
        return Rectangle(card.x + 1 + PAD_X, card.y + 1 + PAD_TOP, card.width - 2 - PAD_X * 2, card.height - 2 - PAD_TOP - PAD_BOTTOM)
    }

    /** The column bounds of the score table, shared by the caption row and the player rows. */
    private fun columns(width: Int) = ScoreColumns(width, max(player1.serveButton.preferredSize.width, player2.serveButton.preferredSize.width))

    override fun doLayout() {
        val area = content()
        var y = area.y

        // Previous | Point x / y and the star | Next
        val prev = previousButton.preferredSize
        val next = nextButton.preferredSize
        previousButton.setBounds(area.x, y, prev.width, NAV_HEIGHT)
        nextButton.setBounds(area.x + area.width - next.width, y, next.width, NAV_HEIGHT)
        val middleX = area.x + prev.width + NAV_GAP
        val middleWidth = area.width - prev.width - next.width - NAV_GAP * 2
        // Offscreen and screen text can paint a few pixels wider than the label measures, so the label gets a margin.
        val label = kotlin.math.ceil(UiKit.textWidth(pointLabel.text, pointLabel.font)).toInt() + 8
        val star = if (favoriteButton.isVisible) favoriteButton.preferredSize.width + 2 else 0
        val titleWidth = label + star
        val titleX = middleX + max(0, (middleWidth - titleWidth) / 2)
        pointLabel.setBounds(titleX, y, label.coerceAtMost(middleWidth), NAV_HEIGHT)
        favoriteButton.setBounds(titleX + label + 2, y + (NAV_HEIGHT - 24) / 2, 24, 24)
        y += NAV_HEIGHT + SECTION_GAP

        // The score table: the caption row, then one row for each player.
        y += CAPTION_HEIGHT + ROW_GAP
        player1.setBounds(area.x, y, area.width, PlayerScoreRow.HEIGHT)
        y += PlayerScoreRow.HEIGHT + ROW_GAP
        player2.setBounds(area.x, y, area.width, PlayerScoreRow.HEIGHT)
        y += PlayerScoreRow.HEIGHT + SECTION_GAP
        val columns = columns(area.width)
        player1.columns = columns
        player2.columns = columns

        // "Who won the point?" and the outcome buttons in three equal columns.
        y += WHO_HEIGHT + WHO_GAP
        val buttonWidth = (area.width - OUTCOME_GAP * 2) / 3
        listOf(player1Button, noPointButton, player2Button).forEachIndexed { i, button ->
            val x = area.x + i * (buttonWidth + OUTCOME_GAP)
            val w = if (i == 2) area.x + area.width - x else buttonWidth
            button.setBounds(x, y, w, OutcomeButton.HEIGHT)
        }
    }

    private fun captionBounds(): Rectangle {
        val area = content()
        return Rectangle(area.x, area.y + NAV_HEIGHT + SECTION_GAP, area.width, CAPTION_HEIGHT)
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val caption = captionBounds()
        val columns = columns(caption.width)
        return if (event.y in caption.y until caption.y + caption.height && event.x < caption.x + columns.serveX) {
            "The score after this point"
        } else {
            null
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val card = card()
            UiKit.paintBox(g2, card.x, card.y, card.width, card.height, 8, UiKit.CARD, UiKit.LINE)

            // The caption row of the score table. It has the same right padding as the player rows.
            val caption = captionBounds()
            val columns = columns(caption.width)
            val font = captionFont
            val top = caption.y.toFloat()
            val h = CAPTION_HEIGHT.toFloat()
            UiKit.drawText(g2, "AFTER THE POINT", font, UiKit.FG_3, caption.x.toFloat(), top, h)
            UiKit.drawText(g2, "SETS", font, UiKit.FG_3, (caption.x + columns.setsX).toFloat(), top, h)
            UiKit.drawText(g2, "GAMES", font, UiKit.FG_3, (caption.x + columns.gamesX).toFloat(), top, h)
            val points = "POINTS"
            UiKit.drawText(g2, points, font, UiKit.FG_3, caption.x + columns.pointsRight - UiKit.textWidth(points, font), top, h)

            val who = captionBounds().y + CAPTION_HEIGHT + ROW_GAP * 2 + PlayerScoreRow.HEIGHT * 2 + SECTION_GAP
            UiKit.drawText(g2, "WHO WON THE POINT?", whoFont, UiKit.FG_3, caption.x.toFloat(), who.toFloat(), WHO_HEIGHT.toFloat())
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val MARGIN_X = 12
        const val MARGIN_TOP = 12
        const val PAD_X = 12
        const val PAD_TOP = 10
        const val PAD_BOTTOM = 12
        const val NAV_HEIGHT = 30
        const val NAV_GAP = 6
        const val SECTION_GAP = 12
        const val CAPTION_HEIGHT = 14
        const val ROW_GAP = 4
        const val WHO_HEIGHT = 15
        const val WHO_GAP = 6
        const val OUTCOME_GAP = 6
        const val PREFERRED_HEIGHT = NAV_HEIGHT + SECTION_GAP + CAPTION_HEIGHT + ROW_GAP * 2 + PlayerScoreRow.HEIGHT * 2 +
            SECTION_GAP + WHO_HEIGHT + WHO_GAP + OutcomeButton.HEIGHT
    }
}

/**
 * The columns of the score table: "minmax(0, 1fr) auto 54px 58px 66px" with 8 px gaps and 8 px padding at the right.
 * [serveWidth] is the width of the "auto" column: the wider serve button of the two rows.
 */
internal data class ScoreColumns(val width: Int, val serveWidth: Int) {
    val pointsRight = width - PAD_RIGHT
    val pointsX = pointsRight - POINTS
    val gamesX = pointsX - GAP - GAMES
    val setsX = gamesX - GAP - SETS
    val serveX = setsX - GAP - serveWidth
    val nameRight = serveX - GAP

    companion object {
        const val GAP = 8
        const val PAD_RIGHT = 8
        const val SETS = 54
        const val GAMES = 58
        const val POINTS = 66
    }
}

/**
 * One player row of the score table: the color strip and the name, the serve button, and the sets, games and points
 * after the point. Automatic scoring shows an icon tag for a game or set that this point won.
 * Manual scoring shows "+" buttons instead.
 */
internal class PlayerScoreRow(
    private val player: Int,
    onServe: (Outcome) -> Unit,
    onManualGame: (Outcome) -> Unit,
    onManualSet: (Outcome) -> Unit,
    onOutcome: (Outcome) -> Unit,
) : JPanel(null) {
    private val outcome = if (player == 1) Outcome.P1 else Outcome.P2

    val serveButton = ServeButton().apply { name = "scoring-player-$player-serve" }
    val setsLabel = valueLabel("scoring-player-$player-sets", 16f, UiKit.Weight.SEMIBOLD)
    val gamesLabel = valueLabel("scoring-player-$player-games", 16f, UiKit.Weight.SEMIBOLD)

    /** The points value. The value of player 1 has the name that the UI-flow tests read. */
    val pointsLabel = valueLabel(if (player == 1) "scoring-score-summary" else "scoring-player-2-points", 18f, UiKit.Weight.BOLD).apply {
        horizontalAlignment = SwingConstants.RIGHT
    }
    val setTag = WonTag(Material2AL.EMOJI_EVENTS).apply { name = "scoring-player-$player-set-won" }
    val gameTag = WonTag(Material2AL.FLAG).apply { name = "scoring-player-$player-game-won" }
    val setPlus = PlusButton().apply { name = "scoring-player-$player-set-plus" }
    val gamePlus = PlusButton().apply { name = "scoring-player-$player-game-plus" }
    val pointPlus = PlusButton().apply { name = "scoring-player-$player-point-plus" }

    var players = ScorePlayers()
        set(value) {
            field = value
            val color = value.color(player)
            listOf(setPlus, gamePlus, pointPlus).forEach { it.playerColor = color }
            listOf(setTag, gameTag).forEach { it.playerColor = color }
            toolTipsFor(state)
            repaint()
        }

    var columns = ScoreColumns(0, 0)
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }

    private var state = ScorePanelState()
    private val nameFont get() = UiKit.font(13f, UiKit.Weight.SEMIBOLD)

    init {
        isOpaque = false
        listOf(serveButton, setsLabel, gamesLabel, pointsLabel, setTag, gameTag, setPlus, gamePlus, pointPlus).forEach(::add)
        serveButton.addActionListener { onServe(outcome) }
        setPlus.addActionListener { onManualSet(outcome) }
        gamePlus.addActionListener { onManualGame(outcome) }
        pointPlus.addActionListener { onOutcome(outcome) }
        setTag.toolTipText = "Set won. Computed automatically"
        gameTag.toolTipText = "Game won. Computed automatically"
        // The row shows the full player name as a tooltip (see getToolTipText).
        toolTipText = ""
    }

    private fun valueLabel(componentName: String, size: Float, weight: UiKit.Weight) = JLabel("0").apply {
        name = componentName
        font = UiKit.font(size, weight)
        foreground = UiKit.FG
    }

    fun render(state: ScorePanelState) {
        this.state = state
        val after = state.after
        setsLabel.text = (if (player == 1) after.setsP1 else after.setsP2).toString()
        gamesLabel.text = (if (player == 1) after.gamesP1 else after.gamesP2).toString()
        pointsLabel.text = ScoringUi.pointsText(after, player)
        pointsLabel.foreground = if (state.selected && ScoringUi.leads(after, player)) UiKit.LIME else UiKit.FG

        val enabled = state.selected && state.canScore
        serveButton.isEnabled = enabled
        serveButton.serving = state.selected && state.server == player
        serveButton.marked = serveButton.serving && state.serverMarked

        setTag.on = state.selected && after.lastSetWonBy == player
        gameTag.on = state.selected && after.lastGameWonBy == player
        setTag.isVisible = !state.manual
        gameTag.isVisible = !state.manual
        listOf(setPlus, gamePlus, pointPlus).forEach {
            it.isVisible = state.manual
            it.isEnabled = enabled
        }
        setPlus.on = state.selected && state.manualSet == outcome
        gamePlus.on = state.selected && state.manualGame == outcome
        pointPlus.on = state.selected && state.outcome == outcome
        toolTipsFor(state)
        revalidate()
        repaint()
    }

    private fun toolTipsFor(state: ScorePanelState) {
        val name = players.name(player)
        serveButton.toolTipText = when {
            !serveButton.serving -> "Click to mark $name as the server of this point. S — switch the server"
            serveButton.marked -> "$name serves (marked on this point). Click to clear the mark. S — switch the server"
            else -> "$name serves (computed from your serve marks). S — switch the server"
        }
        pointPlus.toolTipText = "Point for $name [${if (player == 1) "Q" else "E"}]"
        gamePlus.toolTipText = "Game won by $name on this point. Click to mark or clear the win"
        setPlus.toolTipText = "Set won by $name on this point. Click to mark or clear the win"
    }

    override fun doLayout() {
        val c = columns
        val serve = serveButton.preferredSize
        serveButton.setBounds(c.serveX, (height - serve.height) / 2, serve.width, serve.height)
        placeValue(setsLabel, if (state.manual) setPlus else setTag, c.setsX)
        placeValue(gamesLabel, if (state.manual) gamePlus else gameTag, c.gamesX)

        // Points: right aligned, with the "+" button after the value in manual scoring.
        val plusWidth = if (state.manual) PlusButton.SIZE + VALUE_GAP else 0
        val valueWidth = pointsLabel.preferredSize.width + 2
        val valueRight = c.pointsRight - plusWidth
        pointsLabel.setBounds(valueRight - valueWidth, 0, valueWidth, height)
        pointPlus.setBounds(c.pointsRight - PlusButton.SIZE, (height - PlusButton.SIZE) / 2, PlusButton.SIZE, PlusButton.SIZE)
    }

    /** "n" with a minimum width of 12 px, a 5 px gap, and then the tag or the "+" button. */
    private fun placeValue(value: JLabel, after: JComponent, x: Int) {
        val valueWidth = max(MIN_VALUE_WIDTH, value.preferredSize.width + 2)
        value.setBounds(x, 0, valueWidth, height)
        val size = after.preferredSize
        after.setBounds(x + valueWidth + VALUE_GAP, (height - size.height) / 2, size.width, size.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 8.0, 8.0)
            g2.color = ScoringUi.PLAYER_ROW
            g2.fill(shape)
            // The color strip at the left edge. The rounded row clips it.
            val clip = g2.clip
            g2.clip(shape)
            g2.color = players.color(player)
            g2.fillRect(0, 0, STRIP, height)
            g2.clip = clip
            UiKit.paintBox(g2, 0, 0, width, height, 4, null, UiKit.LINE)
            val x = STRIP + NAME_GAP
            val name = UiKit.ellipsize(players.name(player), nameFont, (columns.nameRight - x).toFloat())
            UiKit.drawText(g2, name, nameFont, UiKit.FG, x.toFloat(), 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }

    override fun getToolTipText(event: MouseEvent): String? =
        if (event.x < columns.nameRight) players.name(player) else null

    companion object {
        const val HEIGHT = 38
        const val STRIP = 4
        const val NAME_GAP = 8
        const val VALUE_GAP = 5
        const val MIN_VALUE_WIDTH = 12
    }
}

/** The serve button: a pill with a racket. It is dim when the player does not serve, and has a lime ring and a pin when the serve is marked on this point. */
internal class ServeButton : JButton() {
    var serving = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }
    var marked = false
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }
    private val icons = HashMap<Pair<Ikon, Color>, Icon>()

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() = Dimension(PAD * 2 + ICON + if (marked) GAP + PIN else 0, HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    private fun icon(ikon: Ikon, size: Int, color: Color) = icons.getOrPut(ikon to color) { UiKit.icon(ikon, size, color) }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ScoringUi.DISABLED_ALPHA)
            val hover = model.isRollover && isEnabled
            val radius = HEIGHT / 2
            val color = when {
                serving -> ScoringUi.RACKET
                hover -> UiKit.FG_2
                else -> UiKit.FG_3
            }
            when {
                marked -> {
                    UiKit.paintBox(g2, 0, 0, width, height, radius, ScoringUi.SERVE_FILL, null)
                    g2.color = UiKit.LIME
                    g2.stroke = BasicStroke(2f)
                    g2.draw(RoundRectangle2D.Double(1.0, 1.0, width - 2.0, height - 2.0, radius * 2.0 - 2, radius * 2.0 - 2))
                }
                serving -> UiKit.paintBox(g2, 0, 0, width, height, radius, ScoringUi.SERVE_FILL, ScoringUi.SERVE_LINE)
                else -> UiKit.paintBox(g2, 0, 0, width, height, radius, null, if (hover) Color(0x555555) else UiKit.LINE_2)
            }
            val racket = icon(Material2MZ.SPORTS_TENNIS, ICON, color)
            racket.paintIcon(this, g2, PAD, (height - racket.iconHeight) / 2)
            if (marked) {
                val pin = icon(Material2MZ.PUSH_PIN, PIN, UiKit.LIME)
                pin.paintIcon(this, g2, PAD + ICON + GAP, (height - pin.iconHeight) / 2)
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 24
        const val PAD = 6
        const val ICON = 16
        const val PIN = 13
        const val GAP = 5
    }
}

/** An icon tag that shows a game or a set that this point won (automatic scoring). It keeps its space when it is off. */
internal class WonTag(private val ikon: Ikon) : JComponent() {
    var on = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }
    var playerColor: Color = UiKit.FG
        set(value) {
            field = value
            repaint()
        }
    private val icons = HashMap<Color, Icon>()

    override fun getPreferredSize() = Dimension(WIDTH_PX, HEIGHT_PX)

    // Only a visible tag shows the tooltip.
    override fun contains(x: Int, y: Int) = on && super.contains(x, y)

    override fun paintComponent(g: Graphics) {
        if (!on) return
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, HEIGHT_PX / 2, playerColor, null)
            val iconColor = ScoringUi.onPlayer(playerColor)
            val icon = icons.getOrPut(iconColor) { UiKit.icon(ikon, 13, iconColor) }
            icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val WIDTH_PX = 23
        const val HEIGHT_PX = 18
    }
}

/** A "+" toggle button of manual scoring: dashed when off, filled with the player color when on. */
internal class PlusButton : JButton() {
    var on = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }
    var playerColor: Color = UiKit.FG
        set(value) {
            field = value
            repaint()
        }
    private val icons = HashMap<Color, Icon>()
    private val dash = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(3f, 2f), 0f)

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() = Dimension(SIZE, SIZE)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ScoringUi.DISABLED_ALPHA)
            val hover = model.isRollover && isEnabled
            val iconColor = if (on) {
                UiKit.paintBox(g2, 0, 0, width, height, 4, playerColor, null)
                ScoringUi.onPlayer(playerColor)
            } else {
                g2.color = if (hover) UiKit.FG_2 else Color(0x555555)
                g2.stroke = dash
                g2.draw(RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, 7.0, 7.0))
                if (hover) UiKit.FG else UiKit.FG_2
            }
            val icon = icons.getOrPut(iconColor) { UiKit.icon(Material2AL.ADD, 16, iconColor) }
            icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val SIZE = 22
    }
}

/**
 * An outcome button: a color strip, the player name (or "No point") and the key chip.
 * The selected outcome fills the button with the player color. When the point has no outcome yet,
 * the buttons have a lime outline: choosing the winner is the next step.
 */
internal class OutcomeButton(private val player: Int, private val key: String) : JButton() {
    private var color: Color = ScoringUi.NO_POINT
    private var on = false
    private var next = false
    private val labelFont get() = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        // Space must stay the play hotkey, so the buttons never keep the focus.
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    fun setPlayer(name: String, color: Color) {
        text = name
        this.color = color
        toolTipText = if (player == 0) "$key — No point" else "$key — Point for $name"
        repaint()
    }

    fun update(enabled: Boolean, on: Boolean, next: Boolean) {
        isEnabled = enabled
        this.on = on
        this.next = next && !on
        model.isSelected = on
        repaint()
    }

    /** True when this outcome is the outcome of the selected point. */
    val isOn: Boolean get() = on

    override fun getPreferredSize() = Dimension(0, HEIGHT)
    override fun getMinimumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ScoringUi.DISABLED_ALPHA)
            val hover = model.isRollover && isEnabled
            val noPoint = player == 0
            val (fill, border) = when {
                on && noPoint -> Color(0x5A5A5A) to Color(0x6A6A6A)
                on -> color to color
                next -> (if (hover) UiKit.RAISED_2 else UiKit.RAISED) to UiKit.LIME_LINE
                hover -> UiKit.RAISED_2 to Color(0x4A4A4A)
                else -> UiKit.RAISED to UiKit.LINE_2
            }
            val shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 8.0, 8.0)
            g2.color = fill
            g2.fill(shape)
            val clip = g2.clip
            g2.clip(shape)
            g2.color = if (on) Color(0, 0, 0, 46) else color
            g2.fillRect(0, 0, STRIP, height)
            g2.clip = clip
            UiKit.paintBox(g2, 0, 0, width, height, 4, null, border)
            if (next) {
                // The inset glow of the next step.
                UiKit.paintBox(g2, 1, 1, width - 2, height - 2, 3, null, Color(161, 254, 0, 46))
            }

            val textColor = when {
                on && noPoint -> Color.WHITE
                on -> ScoringUi.onPlayer(color)
                else -> UiKit.FG
            }
            val chipStyle = when {
                on && noPoint -> ON_NO_POINT_KEY
                on -> ScoringUi.onPlayerKeyStyle(color)
                else -> KeyChipStyle.DEFAULT
            }
            val chipWidth = KeyChips.width(key, chipStyle)
            val chipX = width - PAD_RIGHT - chipWidth
            val x = STRIP + GAP
            val label = UiKit.ellipsize(text.orEmpty(), labelFont, (chipX - LABEL_GAP - x).toFloat())
            UiKit.drawText(g2, label, labelFont, textColor, x.toFloat(), 0f, height.toFloat())
            KeyChips.paint(g2, key, chipX, (height - chipStyle.height) / 2, chipStyle)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 40
        const val STRIP = 6
        const val GAP = 9
        const val LABEL_GAP = 6
        const val PAD_RIGHT = 10

        val ON_NO_POINT_KEY = KeyChipStyle(fill = Color(0, 0, 0, 38), border = Color(0, 0, 0, 89), text = Color.WHITE)
    }
}

/** A star button: yellow and filled for a favorite point, a gray outline otherwise. */
internal class FavoriteButton(private val side: Int) : JButton() {
    var favorite = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }
    private val icons = HashMap<Pair<Boolean, Color>, Icon>()

    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() = Dimension(side, side)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover && isEnabled
            if (hover) UiKit.paintBox(g2, 0, 0, width, height, 4, UiKit.RAISED_2, UiKit.LINE_2)
            val color = when {
                favorite -> UiKit.YELLOW
                hover -> UiKit.FG
                else -> UiKit.FG_3
            }
            val icon = icons.getOrPut(favorite to color) {
                UiKit.icon(if (favorite) Material2MZ.STAR else Material2MZ.STAR_BORDER, 17, color)
            }
            icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }
}
