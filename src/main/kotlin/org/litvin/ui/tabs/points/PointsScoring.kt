package org.litvin.ui.tabs.points

import org.litvin.ui.commons.ErrorTexts
import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardTimelineBuilder
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringEngine
import org.litvin.analytics.Analytics
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.DisabledAnalytics
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.ScoreOverrideV1
import org.litvin.scoring.ScorePairV1
import org.litvin.scoring.SetGamesV1
import org.litvin.scoring.Sport
import org.litvin.ui.commons.HintBalloon
import org.litvin.ui.commons.HintController
import org.litvin.ui.commons.HintId
import org.litvin.ui.tabs.points.ui.ComputedScore
import org.litvin.ui.tabs.points.ui.OverrideScoreDialog
import org.litvin.ui.tabs.points.ui.OverrideScoreInput
import org.litvin.ui.tabs.points.ui.PointMarks
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.commons.Palette
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.ui.tabs.points.ui.ScoreSettings
import org.litvin.ui.tabs.points.ui.ScoreSettingsDialog
import org.litvin.ui.tabs.points.ui.ScoreboardSettingsDialog
import org.litvin.ui.tabs.points.ui.ScoringButton
import org.litvin.ui.tabs.points.ui.ScorePlayers
import org.litvin.ui.tabs.points.ui.ScoringUi
import java.awt.Color
import java.awt.EventQueue
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JPanel
import java.awt.event.ActionEvent
import java.awt.geom.Rectangle2D
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/**
 * The scoring of the Points tab.
 *
 * - [controls] (under the video and at the top of the side column) show the active point of the Points tab. A playhead between points shows no point.
 * - Each change reads score.json, changes the outcomes or marks, and writes the file at once.
 * - [winnerColor] gives the color strip of a point row, and [pointMarks] its game, set and serve marks.
 * - [scoreboardEvents] draws the scoreboard over the video at the playhead (see [moveTo]).
 */
internal class PointsScoring(
    /** Previous (-1) or Next (+1): the Points tab goes to the start of that point and plays it. */
    private val onStep: (delta: Int) -> Unit,
    private val onToggleFavorite: (pointId: String) -> Unit,
    /** The score changed: the point rows paint their color strips again. */
    private val onScoreChanged: () -> Unit,
    private val onError: (String) -> Unit,
    /** The scoreboard over the video changed. */
    private val onOverlayChanged: () -> Unit,
    /** Q, W, E or an outcome button. The Points tab ends a pending point first, then calls [score]. */
    private val onOutcomeRequest: (Outcome) -> Unit,
    /** Loads the video frame at the playhead for the preview of the scoreboard style dialog. */
    private val loadFrame: ((BufferedImage?) -> Unit) -> Unit,
    private val hints: HintController = HintController.NONE,
    private val analytics: Analytics = DisabledAnalytics,
    /** The user's default scoreboard style: a new project starts with it, and each saved style updates it. */
    private val styleDefaults: ScoreboardStyleDefaults = ScoreboardStyleDefaults.NONE,
    private val appCreditRequest: AppCreditRequest = AppCreditRequest.NONE,
    /** True while the Points tab is on the screen. The hints show only then. */
    private val isActive: () -> Boolean = { true },
) {
    /**
     * The serve button and the outcome buttons, in the point bar under the video.
     * Its navigator shows the current point with Previous and Next at the top of the side column.
     */
    val controls = PointsScoringControls(
        onPrevious = { onStep(-1) },
        onNext = { onStep(+1) },
        onToggleFavorite = { selectedId?.let(onToggleFavorite) },
        onOutcome = {
            onOutcomeRequest(it)
            showScoringKeysHint()
        },
        onSwitchServe = { switchServer() },
        onOverrideScore = { openOverride() },
    )

    private val scoreSettingsButton = ScoringButton(tr("points.scoringSettings"), Material2MZ.TUNE, alignLeft = true).apply {
        name = "points-score-settings"
        toolTipText = tr("points.scoringSettings.tooltip")
        addActionListener { openScoreSettings() }
    }

    /** The Scoring settings and Scoreboard style buttons, under the points list. */
    val settingsButtons: JComponent = SettingsButtonRows().apply {
        isOpaque = true
        background = Palette.BG
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(10, 12, 10, 12),
        )
        add(scoreSettingsButton)
        add(ScoringButton(tr("points.scoreboardStyle"), alignLeft = true, glyph = ScoringUi::scoreboardIcon).apply {
            name = "points-scoreboard-style"
            toolTipText = tr("points.scoreboardStyle.tooltip")
            addActionListener { openScoreboardStyle() }
        })
    }

    // The style that the open Scoreboard style dialog shows on the video before the user saves it.
    private var previewStyle: ScoreboardSettingsV1? = null
    private fun style(): ScoreboardSettingsV1 = previewStyle ?: score.scoreboard

    private var projectDir: String? = null

    // The language of the texts in the video. The scoreboard on the video shows it (B-11 E5-T4).
    private var videoLanguage: AppLanguage = I18n.language
    private var score = ScoreV1()
    private var points: List<PointV1> = emptyList()
    private var selectedId: String? = null
    private var pending = false

    // The score after each point, in the order of [points], and the server of each point.
    private var timeline: ScoringEngine.Timeline? = null
    private val states: List<ScoringEngine.MatchState> get() = timeline?.statesAfterPoint.orEmpty()
    private var scene: ScoreboardScene? = null

    // The scoreboard spans in source time: each span goes from the end of the previous point to the end of its point
    // and shows the score before its point, as in a full export. The last span goes from the end of the last point to
    // the end of the video and shows the final score.
    private var spans: List<OverlaySpan> = emptyList()
    private var firstScoredStartMs: Long? = null
    private var shownSpan: OverlaySpan? = null
    private var playheadMs: Long = 0L

    /**
     * Shows the scoreboard of the playhead. The scoreboard shows from the start of the first scored point to the end
     * of the video. Before that point, or without a scored point, the video has no scoreboard.
     */
    fun moveTo(timeMs: Long) {
        playheadMs = timeMs
        val first = firstScoredStartMs
        val span = if (first == null || timeMs < first) null else spans.lastOrNull { it.startMs <= timeMs }
        if (span == shownSpan && (span == null) == (scene == null)) return
        shownSpan = span
        scene = span?.let {
            runCatching { I18n.inVideo(videoLanguage) { ScoreboardLayouts.scene(ScoreboardComponent.display(it), style()) } }.getOrNull()
        }
        onOverlayChanged()
    }

    /** The ASS events of the scoreboard in [area], or nothing when no point is selected. */
    fun scoreboardEvents(area: Rectangle2D.Double): List<String> {
        val current = scene ?: return emptyList()
        return try {
            ScoreboardAss.events(current, ScoreboardAss.place(current, style(), area.x, area.y, area.width, area.height))
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Reads score.json of the project again, for example when the tab opens. A new project gets the scoreboard style
     * that the user saved last. The scoreboard on the video shows the texts in [videoLanguage].
     */
    fun load(dir: String?, videoLanguage: AppLanguage = I18n.language) {
        projectDir = dir
        this.videoLanguage = videoLanguage
        val exists = dir != null && ScoreIO.existsForProjectDir(dir)
        score = readScore() ?: ScoreV1()
        refresh()
        if (dir != null && (!exists || score.useDefaultScoreboard)) {
            val style = runCatching { styleDefaults.load() }.getOrNull()
            change { it.copy(scoreboard = style ?: it.scoreboard, useDefaultScoreboard = false) }
        }
    }

    /**
     * The tab is on the screen: the serve hint can show. The first time the tab opens for a project,
     * the Scoring settings open, so that the user sets the names and the format.
     */
    fun onActivated() {
        showServeMarkHint()
        // Open the dialog after the tab is on the screen.
        EventQueue.invokeLater { if (isActive()) promptScoreSettings() }
    }

    /** Removes the hints of the scoring controls from the screen. They show again at their next trigger. */
    fun onDeactivated() {
        hints.hide(HintId.SCORE_SETTINGS)
        hints.hide(HintId.SCORING_KEYS)
        hints.hide(HintId.SERVE_MARK)
    }

    fun setPoints(newPoints: List<PointV1>) {
        points = newPoints.sortedBy { it.startMs }
        if (points.none { it.id == selectedId }) selectedId = null
        refresh()
    }

    /** A null id shows no point. */
    fun select(pointId: String?) {
        if (pointId == selectedId) return
        selectedId = pointId
        render()
    }

    /** True while a point waits for its end: the outcome buttons then end the point. */
    fun setPending(value: Boolean) {
        if (value == pending) return
        pending = value
        render()
    }

    fun winnerColor(pointId: String): Color? = when (score.outcomes[pointId]) {
        Outcome.P1 -> players().p1Color
        Outcome.P2 -> players().p2Color
        Outcome.NONE -> Palette.NEUTRAL_LIGHT
        null -> null
    }

    /** The GAME or SET badge and the serve mark of a point. */
    fun pointMarks(pointId: String): PointMarks? {
        val index = points.indexOfFirst { it.id == pointId }
        if (index < 0) return null
        val players = players()
        val state = states.getOrNull(index)
        // A point that wins the set also wins a game, so it shows SET only. The Boolean is true for a set.
        val milestone = when {
            state?.lastSetWonBy != null -> true to state.lastSetWonBy
            state?.lastGameWonBy != null -> false to state.lastGameWonBy
            else -> null
        }
        val server = when (score.serverMarks[pointId]) {
            Outcome.P1 -> 1
            Outcome.P2 -> 2
            else -> null
        }
        val overridden = score.scoreOverrides[pointId]?.isEmpty() == false
        if (milestone == null && server == null && !overridden) return null
        return PointMarks(
            milestone = milestone?.let { (set, _) ->
                DisplayFormats.upper(if (set) tr("points.badge.set") else tr("points.badge.game"))
            },
            milestoneIsSet = milestone?.first == true,
            milestoneColor = milestone?.let { players.color(it.second) } ?: Palette.FG,
            milestoneOnColor = milestone?.let { ScoringUi.onPlayer(players.color(it.second)) } ?: Palette.BG,
            milestoneTip = milestone?.let { (set, player) ->
                if (set) tr("points.badge.set.tooltip", "name" to players.name(player))
                else tr("points.badge.game.tooltip", "name" to players.name(player))
            }.orEmpty(),
            serveColor = server?.let(players::color),
            serveOnBadge = server?.let { ScoringUi.needsBadge(players.color(it)) } ?: false,
            serveTip = server?.let { tr("points.serveMark.tooltip", "name" to players.name(it)) }.orEmpty(),
            overridden = overridden,
        )
    }

    /** Q, W, E give the score. R and Shift+R go to the next and the previous point and play it. S switches the server. */
    fun installKeys(target: JComponent, isTextEditingFocus: () -> Boolean) {
        fun bind(key: String, name: String, action: () -> Unit) {
            val stroke = KeyStroke.getKeyStroke(key)
            listOf(JComponent.WHEN_IN_FOCUSED_WINDOW, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .forEach { target.getInputMap(it).put(stroke, name) }
            target.actionMap.put(name, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    if (!isTextEditingFocus()) action()
                }
            })
        }
        bind(AppShortcuts.SCORE_PLAYER_1.keyStroke, "points.scoreP1") { onOutcomeRequest(Outcome.P1) }
        bind(AppShortcuts.SCORE_NO_POINT.keyStroke, "points.scoreNone") { onOutcomeRequest(Outcome.NONE) }
        bind(AppShortcuts.SCORE_PLAYER_2.keyStroke, "points.scoreP2") { onOutcomeRequest(Outcome.P2) }
        bind(AppShortcuts.NEXT_POINT.keyStroke, "points.nextPoint") {
            onStep(+1)
            hints.dismiss(HintId.SCORING_KEYS)
        }
        bind(AppShortcuts.PREVIOUS_POINT.keyStroke, "points.previousPoint") {
            onStep(-1)
            hints.dismiss(HintId.SCORING_KEYS)
        }
        bind(AppShortcuts.SWITCH_SERVE.keyStroke, "points.switchServe") { switchServer() }
    }

    /**
     * Gives [outcome] to the point with [pointId]. The point becomes the selected point.
     * When the user did not see the Scoring settings of the project yet, they open before the score.
     */
    fun score(outcome: Outcome, pointId: String) {
        if (points.none { it.id == pointId }) return
        selectedId = pointId
        promptScoreSettings()
        setOutcome(outcome)
        showServeMarkHint()
    }

    /**
     * Opens the Scoring settings one time for each project: when the tab first opens, or before the first score.
     * When the dialog closes, a balloon points at the Scoring settings button.
     */
    private fun promptScoreSettings() {
        if (projectDir == null || score.scoreSettingsReviewed) return
        // Set the flag first, so that a Cancel does not open the dialog again.
        change { it.copy(scoreSettingsReviewed = true) }
        openScoreSettings()
        if (isActive()) hints.show(HintId.SCORE_SETTINGS, scoreSettingsButton, SCORE_SETTINGS_HINT, HintBalloon.Placement.ABOVE)
    }

    /** After a click on a winner button: R and Shift+R move to the next and the previous point. */
    private fun showScoringKeysHint() {
        if (!isActive()) return
        hints.show(HintId.SCORING_KEYS, controls.navigator, SCORING_KEYS_HINT, HintBalloon.Placement.LEFT)
    }

    /** When 3 points have a winner and no point has a serve mark: one mark gives the serve statistics. */
    private fun showServeMarkHint() {
        if (!isActive() || score.serverMarks.isNotEmpty()) return
        val won = points.count { score.outcomes[it.id] == Outcome.P1 || score.outcomes[it.id] == Outcome.P2 }
        if (won < SERVE_HINT_POINTS) return
        hints.show(HintId.SERVE_MARK, controls.serveButton, SERVE_MARK_HINT, HintBalloon.Placement.ABOVE)
    }

    /** S or the serve button: the other side serves the selected point. Without a known server, side 1 serves. */
    private fun switchServer() {
        val index = selectedIndex()
        val point = points.getOrNull(index) ?: return
        if (point.endMs <= point.startMs) return
        val next = if (timeline?.serverOfPoint?.getOrNull(index) == 1) Outcome.P2 else Outcome.P1
        markServer(next)
    }

    /** A mark that only repeats the computed server is not kept. */
    private fun markServer(server: Outcome) {
        val index = selectedIndex()
        val point = points.getOrNull(index) ?: return
        if (server == Outcome.NONE) return
        val serverNumber = if (server == Outcome.P1) 1 else 2
        change { current ->
            when {
                current.serverMarks[point.id] == server -> current.copy(serverMarks = current.serverMarks - point.id)
                timeline?.serverOfPoint?.getOrNull(index) == serverNumber -> current
                else -> {
                    val without = current.serverMarks - point.id
                    val computed = timelineOf(current.copy(serverMarks = without)).serverOfPoint.getOrNull(index)
                    current.copy(serverMarks = if (computed != serverNumber) without + (point.id to server) else without)
                }
            }
        }
        if (score.serverMarks.isNotEmpty()) hints.dismiss(HintId.SERVE_MARK)
    }

    /**
     * Opens Override score for the selected point. The dialog shows the score after the point without its override.
     * Save keeps the typed parts; an empty override removes the override of the point.
     */
    private fun openOverride() {
        val index = selectedIndex()
        val point = points.getOrNull(index) ?: return
        val current = score.scoreOverrides[point.id] ?: ScoreOverrideV1()
        val withoutThis = score.copy(scoreOverrides = score.scoreOverrides - point.id)
        val computedLine = runCatching { timelineOf(withoutThis) }.getOrNull() ?: return
        val state = computedLine.statesAfterPoint.getOrNull(index) ?: return
        val sets = computedLine.setsAfterPoint.getOrNull(index).orEmpty().map { set ->
            if (set.tiebreak) SetGamesV1(set.p1, set.p2, set.tiebreakP1, set.tiebreakP2) else SetGamesV1(set.p1, set.p2)
        }
        val rules = score.rules.normalized()
        val players = players()
        val input = OverrideScoreInput(
            pointNumber = index + 1,
            computed = ComputedScore(sets, ScorePairV1(state.gamesP1, state.gamesP2), ScorePairV1(state.p1Pts, state.p2Pts)),
            current = current,
            p1Name = players.p1Name,
            p2Name = players.p2Name,
            p1Color = players.p1Color,
            p2Color = players.p2Color,
            showSets = rules.manualScoring || rules.structure == MatchStructure.SETS,
            showGames = rules.manualScoring || rules.structure == MatchStructure.SETS || rules.structure == MatchStructure.GAMES_ONLY,
            maxSets = if (rules.manualScoring) MAX_SETS else rules.bestOfSets,
            tiebreakOf = { candidate ->
                val trial = withoutThis.copy(scoreOverrides = withoutThis.scoreOverrides + (point.id to candidate))
                runCatching { timelineOf(trial).statesAfterPoint.getOrNull(index)?.isTiebreak }.getOrNull() ?: state.isTiebreak
            },
        )
        val result = OverrideScoreDialog.show(controls, input) ?: return
        change {
            val overrides = if (result.isEmpty()) it.scoreOverrides - point.id else it.scoreOverrides + (point.id to result)
            it.copy(scoreOverrides = overrides)
        }
    }

    private fun timelineOf(s: ScoreV1) =
        ScoringEngine.timeline(points, s.outcomes, s.rules.normalized(), s.manualMarks(), s.serverMarks)

    private fun openScoreSettings() {
        val current = ScoreSettings(
            player1Name = score.player1Name,
            player2Name = score.player2Name,
            player1ColorHex = score.player1ColorHex,
            player2ColorHex = score.player2ColorHex,
            rules = score.rules,
            sport = score.sport,
        )
        val result = ScoreSettingsDialog.edit(controls, current) ?: return
        if (result.sport != score.sport) analytics.record(AnalyticsEvent.SportUsed(result.sport.analyticsSport()))
        change {
            it.copy(
                player1Name = result.player1Name,
                player2Name = result.player2Name,
                player1ColorHex = result.player1ColorHex,
                player2ColorHex = result.player2ColorHex,
                rules = result.rules.normalized(),
                sport = result.sport,
                scoreSettingsReviewed = true,
            )
        }
    }

    /**
     * The video shows each change of the dialog at once; Cancel shows the saved style again.
     * A saved style also becomes the user's default style for new projects.
     */
    private fun openScoreboardStyle() {
        val sample = I18n.inVideo(videoLanguage) {
            ScoreboardComponent.display(
                shownSpan ?: OverlaySpan(
                    startMs = 0L, endMs = 1L, text = "",
                    p1Name = score.sport.videoSideName(score.player1Name, 1), p2Name = score.sport.videoSideName(score.player2Name, 2),
                    p1ColorHex = score.player1ColorHex, p2ColorHex = score.player2ColorHex,
                    p1Pts = 3, p2Pts = 1, gamesP1 = 4, gamesP2 = 3, completedSets = listOf(6 to 4),
                ),
            )
        }
        val takeCreditRequest = { appCreditRequest.shouldAsk().also { ask -> if (ask) appCreditRequest.markAsked() } }
        val result = try {
            ScoreboardSettingsDialog.show(controls, score.scoreboard, sample, loadFrame, takeCreditRequest, videoLanguage = videoLanguage) { preview ->
                previewStyle = preview
                redrawScoreboard()
            }
        } finally {
            previewStyle = null
        }
        if (result == null) {
            redrawScoreboard()
            return
        }
        change { it.copy(scoreboard = result) }
        try {
            styleDefaults.save(result)
        } catch (_: Throwable) {
            // The default style is a convenience; the project keeps its own style.
        }
    }

    private fun redrawScoreboard() {
        shownSpan = null
        scene = null
        moveTo(playheadMs)
    }

    private fun selectedIndex(): Int = points.indexOfFirst { it.id == selectedId }

    private fun players() = ScorePlayers(
        p1Name = score.player1Name.ifBlank { score.sport.defaultSideName(1) },
        p2Name = score.player2Name.ifBlank { score.sport.defaultSideName(2) },
        p1Color = ScoringUi.parseColor(score.player1ColorHex, Color(ScoreboardComponent.DEFAULT_PLAYER1_RGB)),
        p2Color = ScoringUi.parseColor(score.player2ColorHex, Color(ScoreboardComponent.DEFAULT_PLAYER2_RGB)),
    )

    private fun setOutcome(outcome: Outcome) {
        val point = points.getOrNull(selectedIndex()) ?: return
        if (point.endMs <= point.startMs) return
        if (score.outcomes[point.id] != outcome) analytics.record(AnalyticsEvent.ScoreRecorded)
        change { it.copy(outcomes = it.outcomes + (point.id to outcome)) }
    }

    /** Reads score.json, applies [update], and writes the file, so that the other fields stay as they are. */
    private fun change(update: (ScoreV1) -> ScoreV1) {
        val dir = projectDir ?: return
        val current = readScore() ?: return
        val updated = update(current)
        if (updated != current) {
            try {
                ScoreIO.writeForProjectDir(dir, updated)
            } catch (t: Throwable) {
                onError(ErrorTexts.of(t))
                return
            }
        }
        score = updated
        refresh()
        onScoreChanged()
    }

    private fun readScore(): ScoreV1? {
        val dir = projectDir ?: return null
        return try {
            ScoreIO.readForProjectDir(dir)
        } catch (t: Throwable) {
            onError(ErrorTexts.of(t))
            null
        }
    }

    private fun refresh() {
        timeline = runCatching { timelineOf(score) }.getOrNull()
        controls.setPlayers(players())
        render()
        rebuildSpans()
    }

    private fun render() = renderPanel()

    /** Builds [spans] again after a change of the points or the score, and shows the scoreboard of the playhead again. */
    private fun rebuildSpans() {
        firstScoredStartMs = points.firstOrNull { it.id in score.outcomes }?.startMs?.toLong()
        spans = runCatching {
            val last = points.lastOrNull() ?: return@runCatching emptyList()
            // A point after the last point gives the span with the final score.
            val finalScore = PointV1(id = FINAL_SCORE_ID, startMs = last.endMs, endMs = Int.MAX_VALUE)
            val players = players()
            I18n.inVideo(videoLanguage) {
                ScoreboardTimelineBuilder.build(
                    points = points + finalScore,
                    outcomes = score.outcomes,
                    idleTrim = false,
                    player1Name = players.p1Name,
                    player2Name = players.p2Name,
                    player1ColorHex = score.player1ColorHex,
                    player2ColorHex = score.player2ColorHex,
                    rules = score.rules.normalized(),
                    manualMarks = score.manualMarks(),
                    serverMarks = score.serverMarks,
                    sport = score.sport,
                )
            }
        }.getOrDefault(emptyList())
        redrawScoreboard()
    }

    private fun renderPanel() {
        val index = selectedIndex()
        val point = points.getOrNull(index)
        if (point == null) {
            controls.render(PointControlsState(total = points.size, pending = pending))
            return
        }
        controls.render(
            PointControlsState(
                index = index,
                total = points.size,
                favorite = point.favorite,
                hasPrevious = index > 0,
                hasNext = index + 1 < points.size,
                outcome = score.outcomes[point.id],
                canScore = point.endMs > point.startMs,
                pending = pending,
                server = timeline?.serverOfPoint?.getOrNull(index),
                serverMarked = point.id in score.serverMarks,
                overridden = score.scoreOverrides[point.id]?.isEmpty() == false,
            ),
        )
    }

    private companion object {
        const val FINAL_SCORE_ID = "final-score"

        /** The most sets that Override score offers in manual scoring. */
        const val MAX_SETS = 5

        val SCORE_SETTINGS_HINT: String get() = tr("points.hint.scoreSettings")
        val SCORING_KEYS_HINT: String get() = tr("points.hint.scoringKeys")
        val SERVE_MARK_HINT: String get() = tr("points.hint.serveMark")

        /** The serve hint shows when this number of points has a winner. */
        const val SERVE_HINT_POINTS = 3
    }
}

/** The sport for the analytics. */
private fun Sport.analyticsSport(): AnalyticsEvent.Sport = when (this) {
    Sport.TENNIS -> AnalyticsEvent.Sport.TENNIS
    Sport.PADEL -> AnalyticsEvent.Sport.PADEL
    Sport.BEACH_TENNIS -> AnalyticsEvent.Sport.BEACH_TENNIS
}

/**
 * The two settings buttons under the points list: in one row, each in one half of the width, or in two rows when one
 * text does not fit in its half (B-11 E4-T8). The side column has a fixed width, so the rows change only with a
 * language or with the screen scale.
 */
private class SettingsButtonRows : JPanel(null) {
    private val buttons get() = components.filter { it.isVisible }

    /** The rows that the last preferred size used. */
    private var preferredTwoRows = false

    private fun twoRows(w: Int): Boolean {
        val half = (w - insets.left - insets.right - GAP) / 2
        return w > 0 && buttons.any { it.preferredSize.width > half }
    }

    private fun rowHeight() = buttons.maxOfOrNull { it.preferredSize.height } ?: 0

    override fun getPreferredSize(): Dimension {
        preferredTwoRows = twoRows(width)
        val rows = if (preferredTwoRows) buttons.size else 1
        return Dimension(0, insets.top + insets.bottom + rowHeight() * rows + GAP * (rows - 1))
    }

    override fun doLayout() {
        val twoRows = twoRows(width)
        val x = insets.left
        val w = width - insets.left - insets.right
        val h = rowHeight()
        if (twoRows) {
            buttons.forEachIndexed { i, button -> button.setBounds(x, insets.top + i * (h + GAP), w, h) }
        } else {
            val half = (w - GAP) / 2
            buttons.forEachIndexed { i, button ->
                button.setBounds(x + i * (half + GAP), insets.top, if (i == buttons.lastIndex) w - i * (half + GAP) else half, h)
            }
        }
        // The first layout comes after the first preferred size, which did not know the width.
        if (twoRows != preferredTwoRows) javax.swing.SwingUtilities.invokeLater { revalidate() }
    }

    private companion object {
        const val GAP = 8
    }
}
