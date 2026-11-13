package org.litvin.ui.tabs.points

import org.litvin.ui.commons.ErrorTexts
import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardTimelineBuilder
import org.litvin.ScoreboardTiming
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.points.PointV1
import org.litvin.scoring.ClipKind
import org.litvin.scoring.ClipOutcome
import org.litvin.scoring.ClipOutcomes
import org.litvin.scoring.Ending
import org.litvin.scoring.EndingChoice
import org.litvin.scoring.ErrorKind
import org.litvin.scoring.ServePacks
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetails
import org.litvin.scoring.PointEndings
import org.litvin.scoring.ScoringMode
import org.litvin.scoring.ServerChanges
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
import org.litvin.ui.tabs.points.ui.EndingButtons
import org.litvin.ui.tabs.points.ui.ErrorKindStrip
import org.litvin.ui.tabs.points.ui.ErrorKindStripState
import org.litvin.ui.tabs.points.ui.MatchEndNotice
import org.litvin.ui.tabs.points.ui.OverrideScoreDialog
import org.litvin.ui.tabs.points.ui.OverrideScoreInput
import org.litvin.ui.tabs.points.ui.PointMarks
import org.litvin.ui.tabs.points.ui.PointRowInfo
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.commons.Palette
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.ui.tabs.points.ui.ScoreSettings
import org.litvin.ui.tabs.points.ui.ScoreSettingsDialog
import org.litvin.ui.tabs.points.ui.ScoreboardSettingsDialog
import org.litvin.ui.tabs.points.ui.TimingContext
import org.litvin.ui.tabs.points.ui.ScoringButton
import org.litvin.ui.tabs.points.ui.ScorePlayers
import org.litvin.ui.tabs.points.ui.ScoringUi
import java.awt.Color
import java.awt.EventQueue
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
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
 * - [modeBar] switches the scoring mode of a tennis project (B-68). In advanced mode, the point bar shows the ending
 *   buttons, and asks "Who serves?" when the project has no serve mark (see [ServeQuestion]).
 * - [errorStrip], under the point bar, asks the error kind after an error ending when the project option is on
 *   (B-68, see [ErrorKindQuestion]).
 * - After each change of the score file, [PointDetails.resolve] writes the winners of the aces and the double faults
 *   into the outcomes, in all modes. Thus basic mode and the scoreboard show the same score.
 *
 * The file is longer than 500 lines. The scoring keeps one state (the score file, the points, the selected point, and
 * the pending point), and each action reads and writes it through [change]. The pure rules are in other files:
 * `scoring.PointEndings`, `scoring.ServerChanges`, `scoring.ClipOutcomes`, [ScoreKeys], [ServeQuestion], [ErrorKindQuestion], and
 * [ServerChangeText]. The next split can move
 * [pointMarks] and the dialogs (Override score, Scoring rules, Scoreboard style) into their own classes.
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
    /** Q, W, E, an ending key, or a button. The Points tab ends a pending point first, then calls [score]. */
    private val onScoreRequest: (ScoreRequest) -> Unit,
    /** Loads the video frame at the playhead for the preview of the scoreboard style dialog. */
    private val loadFrame: ((BufferedImage?) -> Unit) -> Unit,
    private val hints: HintController = HintController.NONE,
    private val analytics: Analytics = DisabledAnalytics,
    /** The user's default scoreboard style: a new project starts with it, and each saved style updates it. */
    private val styleDefaults: ScoreboardStyleDefaults = ScoreboardStyleDefaults.NONE,
    private val appCreditRequest: AppCreditRequest = AppCreditRequest.NONE,
    /** True while the Points tab is on the screen. The hints show only then. */
    private val isActive: () -> Boolean = { true },
    /** Selects the point with this id and seeks to its start. */
    private val onGoToPoint: (pointId: String) -> Unit = {},
    /** Selects the point with this id, seeks to its start, and plays it: Ctrl+R and Ctrl+Shift+R (B-68). */
    private val onPlayPoint: (pointId: String) -> Unit = onGoToPoint,
    /** The user's default scoring mode: a new project starts with it (B-68). */
    private val modeDefaults: ScoringModeDefaults = ScoringModeDefaults.NONE,
    /** Asks the user to confirm a server change with this title and message. Returns true for "Change server". */
    private val confirmServerChange: (title: String, message: String) -> Boolean = { _, _ -> true },
    /** The serve number (1 or 2) of the start button in advanced mode, or null in basic mode (B-68). */
    private val onServeNumber: (Int?) -> Unit = {},
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
            onScoreRequest(ScoreRequest.Winner(it))
            showScoringKeysHint()
        },
        onSwitchServe = { switchServer() },
        onOverrideScore = { openOverride() },
        onEnding = {
            requestEnding(it, fromKey = false)
            showScoringKeysHint()
        },
        onServeAnswer = { answerServer(it) },
        onFault = { requestServeEnding(ScoreRequest.Fault) },
        onLet = { requestServeEnding(ScoreRequest.Let) },
    )

    /** The scoring mode switch above the point bar. Only tennis projects show it. */
    val modeBar = ScoringModeBar(onSelect = { selectMode(it) }, onAskErrorKind = { setAskErrorKind(it) })
    private val serveQuestion = ServeQuestion()
    private val errorQuestion = ErrorKindQuestion()

    /**
     * The error kind strip under the point bar (B-68, "Follow-up questions"). It shows in advanced mode with the option
     * "Ask error kind" on, for the open question and for the active clip with an error ending.
     */
    val errorStrip = ErrorKindStrip(
        answers = ErrorKindQuestion.ANSWERS,
        keyOf = { ErrorKindQuestion.keyOf(it).display },
        closeKeys = Triple(AppShortcuts.POINT_START.display, AppShortcuts.NEXT_POINT.display, AppShortcuts.CANCEL_PENDING.display),
        onAnswer = { answerErrorKind(it) },
        onOpen = { openErrorQuestion() },
    )

    private val scoreSettingsButton = ScoringButton(tr("points.scoringRules"), Material2MZ.TUNE, alignLeft = true).apply {
        name = "points-score-settings"
        toolTipText = tr("points.scoringRules.tooltip")
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
        add(ScoringButton(tr("points.scoreboardSettings"), alignLeft = true, glyph = ScoringUi::scoreboardIcon).apply {
            name = "points-scoreboard-style"
            toolTipText = tr("points.scoreboardSettings.tooltip")
            addActionListener { openScoreboardStyle() }
        })
    }

    // The id of the point that ends the match, or null when the match did not end.
    private var matchPointId: String? = null

    /** The notice under the points list when points after the end of the match have a winner. */
    val matchEndNotice = MatchEndNotice(
        onChangeFormat = { openScoreSettings() },
        onGoToMatchPoint = { matchPointId?.let(onGoToPoint) },
    )

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

    // The start and the id of the pending point. The id is the id of the point that the pending point becomes.
    private var pendingStart: Long? = null
    private var pendingId: String? = null

    // The score after each point, in the order of [points], and the server of each point.
    private var timeline: ScoringEngine.Timeline? = null

    // The packs of the clips (B-68): the serve number of each clip and of a new clip.
    private var packs: ServePacks = ServePacks.EMPTY

    // The pack rows of the points list and the navigator numbers (B-68).
    private val packRows = PointPacksCache()
    private val states: List<ScoringEngine.MatchState> get() = timeline?.statesAfterPoint.orEmpty()
    private var scene: ScoreboardScene? = null

    // The scoreboard spans in source time, the same as in a full export: each span goes from the end of the previous
    // point to the end of its point and shows the score before its point. The start and the end settings of the
    // board apply (B-63). While the match has not ended, the board stays until the video ends.
    private var spans: List<OverlaySpan> = emptyList()
    private var shownSpan: OverlaySpan? = null
    private var playheadMs: Long = 0L

    /**
     * Shows the scoreboard of the playhead, as the full export shows it. The preview shows no fade: the board comes
     * and goes at the start and the end of the fades.
     */
    fun moveTo(timeMs: Long) {
        showScoreboardAt(timeMs, redraw = false)
        // The serve labels come from the pack at the playhead (B-68).
        if (score.isAdvanced && serveLabels() != shownServeLabels) render()
    }

    /**
     * Shows the scoreboard at [timeMs]. Without [redraw], the overlay does not change when the span stays the same.
     * A [redraw] always sends [onOverlayChanged], because a change of the spans or the style can remove the board
     * that the video shows now.
     */
    private fun showScoreboardAt(timeMs: Long, redraw: Boolean) {
        playheadMs = timeMs
        val span = spans.lastOrNull { it.startMs <= timeMs && timeMs < it.endMs }
        if (!redraw && span == shownSpan && (span == null) == (scene == null)) return
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
        serveQuestion.close()
        errorQuestion.close()
        score = readScore() ?: ScoreV1()
        refresh()
        if (dir != null && (!exists || score.useDefaultScoreboard)) {
            val style = runCatching { styleDefaults.load() }.getOrNull()
            val mode = runCatching { modeDefaults.load() }.getOrDefault(ScoringMode.BASIC)
            val askErrorKind = runCatching { modeDefaults.loadAskErrorKind() }.getOrDefault(false)
            change {
                it.copy(
                    scoreboard = style ?: it.scoreboard, scoringMode = mode, askErrorKind = askErrorKind, useDefaultScoreboard = false,
                )
            }
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
        KeyHint.entries.forEach { hints.hide(it.id) }
    }

    fun setPoints(newPoints: List<PointV1>) {
        // A clip of zero length comes before a clip that starts at the same time, as in the packs (B-68).
        points = newPoints.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        if (points.none { it.id == selectedId }) selectedId = null
        refresh()
        // A deleted or a moved point can move the server, and thus the winner of an ace or a double fault (B-68).
        if (projectDir != null && PointDetails.resolveOutcomes(points, score) != score.outcomes) change { it }
    }

    /** A null id shows no point. */
    fun select(pointId: String?) {
        if (pointId == selectedId) return
        selectedId = pointId
        syncErrorQuestion()
        render()
    }

    /**
     * A point waits for its end while [start] is not null: the outcome buttons then end the point. [id] is the id of
     * the point that the pending point becomes. When the user cancels the pending point, its serve mark goes away.
     */
    fun setPending(start: Long?, id: String?) {
        if ((start != null) == pending && start == pendingStart && id == pendingId) return
        val old = pendingId
        pending = start != null
        pendingStart = start
        pendingId = id
        if (old != null && old != id && points.none { it.id == old } && old in score.serverMarks) {
            change { it.copy(serverMarks = it.serverMarks - old) }
        } else {
            render()
        }
    }

    /** C started the pending point (B-68). In advanced mode with no known server, the point bar asks "Who serves?". */
    fun onPointStart() {
        // C closes the error kind question without an answer (B-68).
        errorQuestion.close()
        val id = pendingId ?: return
        val answer = serveQuestion.onPointStart(score.isAdvanced, serverKnown())
        if (answer != null) markNewServer(id, answer) else render()
    }

    fun winnerColor(pointId: String): Color = when (score.outcomeOf(pointId)) {
        Outcome.P1 -> players().p1Color
        Outcome.P2 -> players().p2Color
        Outcome.NONE -> Palette.NEUTRAL_LIGHT
    }

    /** The pack number, the pack position, and the label of a point row in advanced mode (B-68), or null. */
    fun pointRowInfo(pointId: String): PointRowInfo? = packRows.of(points, score).row(pointId)

    /** True for a fault clip and a let clip in advanced mode. The timeline shows them dimmed (B-68). */
    fun isDimmedClip(pointId: String): Boolean = packRows.of(points, score).isDimmed(pointId)

    /** The MATCH, SET or GAME badge, the serve mark and the after-match state of a point. */
    fun pointMarks(pointId: String): PointMarks? {
        val index = points.indexOfFirst { it.id == pointId }
        if (index < 0) return null
        val players = players()
        val state = states.getOrNull(index)
        val afterMatch = timeline?.stateBefore(index)?.matchEnded == true
        val matchPoint = state?.matchEnded == true && !afterMatch
        // The match point also wins a set and a game, so it shows MATCH only. A point that wins the set shows SET only.
        val milestone = when {
            matchPoint -> Milestone.MATCH to matchWinner(state)
            state?.lastSetWonBy != null -> Milestone.SET to state.lastSetWonBy
            state?.lastGameWonBy != null -> Milestone.GAME to state.lastGameWonBy
            else -> null
        }
        val server = when (score.serverMarks[pointId]) {
            Outcome.P1 -> 1
            Outcome.P2 -> 2
            else -> null
        }
        val overridden = score.scoreOverrides[pointId]?.isEmpty() == false
        if (milestone == null && server == null && !overridden && !afterMatch) return null
        // A match that ends in a draw (a total points match) has no winner color.
        val milestoneColor = milestone?.second?.let(players::color) ?: Palette.FG
        return PointMarks(
            milestone = milestone?.let { (kind, _) ->
                DisplayFormats.upper(
                    when (kind) {
                        Milestone.MATCH -> tr("points.badge.match")
                        Milestone.SET -> tr("points.badge.set")
                        Milestone.GAME -> tr("points.badge.game")
                    },
                )
            },
            milestoneIsSet = milestone != null && milestone.first != Milestone.GAME,
            milestoneColor = milestoneColor,
            milestoneOnColor = if (milestone != null) ScoringUi.onPlayer(milestoneColor) else Palette.BG,
            milestoneTip = milestone?.let { (kind, player) ->
                when {
                    player == null -> tr("points.badge.match.draw.tooltip")
                    kind == Milestone.MATCH -> tr("points.badge.match.tooltip", "name" to players.name(player))
                    kind == Milestone.SET -> tr("points.badge.set.tooltip", "name" to players.name(player))
                    else -> tr("points.badge.game.tooltip", "name" to players.name(player))
                }
            }.orEmpty(),
            serveColor = server?.let(players::color),
            serveOnBadge = server?.let { ScoringUi.needsBadge(players.color(it)) } ?: false,
            serveTip = server?.let { tr("points.serveMark.tooltip", "name" to players.name(it)) }.orEmpty(),
            overridden = overridden,
            afterMatch = afterMatch,
            matchPoint = matchPoint,
        )
    }

    private enum class Milestone { GAME, SET, MATCH }

    /** The side that won the match, or null for a draw. The sets decide; without sets, the points decide. */
    private fun matchWinner(state: ScoringEngine.MatchState): Int? = when {
        state.setsP1 != state.setsP2 -> if (state.setsP1 > state.setsP2) 1 else 2
        state.p1Pts != state.p2Pts -> if (state.p1Pts > state.p2Pts) 1 else 2
        else -> null
    }

    /** Q, W, E and the ending keys give the score. R and Shift+R go to the next and the previous point and play it. S switches the server. */
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
        bind(AppShortcuts.SCORE_PLAYER_1.keyStroke, "points.scoreP1") { onScoreRequest(ScoreRequest.Winner(Outcome.P1)) }
        bind(AppShortcuts.SCORE_NO_POINT.keyStroke, "points.scoreNone") { onScoreRequest(ScoreRequest.Winner(Outcome.NONE)) }
        bind(AppShortcuts.SCORE_PLAYER_2.keyStroke, "points.scoreP2") { onScoreRequest(ScoreRequest.Winner(Outcome.P2)) }
        // The ending keys of advanced mode: the top-row digit, the numpad digit, and the Shift form (B-68).
        EndingChoice.entries.forEach { choice ->
            val shortcut = EndingButtons.keyOf(choice)
            val name = "points.ending.${choice.name}"
            (listOf(shortcut.keyStroke) + AppShortcuts.alsoBound(shortcut)).forEachIndexed { index, stroke ->
                bind(stroke, "$name.$index") { requestEnding(choice, fromKey = true) }
            }
        }
        bind(AppShortcuts.NEXT_POINT.keyStroke, "points.nextPoint") {
            // R closes the error kind question without an answer (B-68).
            closeErrorQuestion()
            onStep(+1)
            hints.dismiss(HintId.SCORING_KEYS)
        }
        bind(AppShortcuts.PREVIOUS_POINT.keyStroke, "points.previousPoint") {
            closeErrorQuestion()
            onStep(-1)
            hints.dismiss(HintId.SCORING_KEYS)
        }
        bind(AppShortcuts.SWITCH_SERVE.keyStroke, "points.switchServe") { switchServer() }
        bind(AppShortcuts.SERVE_FAULT.keyStroke, "points.serveFault") { requestServeEnding(ScoreRequest.Fault) }
        bind(AppShortcuts.LET.keyStroke, "points.let") { requestServeEnding(ScoreRequest.Let) }
        bind(AppShortcuts.NEXT_PACK.keyStroke, "points.nextPack") { goToPack(+1) }
        bind(AppShortcuts.PREVIOUS_PACK.keyStroke, "points.previousPack") { goToPack(-1) }
    }

    /** Ctrl+R and Ctrl+Shift+R (B-68): the first clip of the next or the previous pack. Basic mode does as R and Shift+R. */
    private fun goToPack(delta: Int) {
        hints.dismiss(HintId.SCORING_KEYS)
        closeErrorQuestion()
        val current = packRows.of(points, score)
        if (!current.advanced) return onStep(delta)
        current.packTarget(selectedId, playheadMs, delta)?.let(onPlayPoint)
    }

    /**
     * Gives [outcome] to the point with [pointId]. The point becomes the selected point.
     * When the user did not see the Scoring settings of the project yet, they open before the score.
     */
    fun score(request: ScoreRequest, pointId: String) {
        if (points.none { it.id == pointId }) return
        selectedId = pointId
        promptScoreSettings()
        when (request) {
            is ScoreRequest.Winner -> setOutcome(request.outcome)
            is ScoreRequest.WithEnding -> setEnding(request.choice)
            ScoreRequest.Fault -> setServeEnding(Ending.SERVE_FAULT)
            ScoreRequest.Let -> setServeEnding(Ending.LET)
        }
        // An error ending opens the error kind question when the option is on (B-68, "Follow-up questions").
        if (request is ScoreRequest.WithEnding) {
            errorQuestion.onEnding(asksErrorKind(), pointId, score.detailOf(pointId).ending)
            render()
        }
        showServeMarkHint()
    }

    /**
     * X or L (B-68). They work only in advanced mode. X does not work while the point bar asks "Who serves?", because
     * the double fault needs the server.
     */
    private fun requestServeEnding(request: ScoreRequest) {
        if (!score.isAdvanced) return
        if (request == ScoreRequest.Fault && serveQuestion.isOpen) return
        onScoreRequest(request)
    }

    /** True when the pack at [timeMs] already has a fault clip or a let clip. Then X with no point adds no clip (B-68). */
    fun packAtHasServeClip(timeMs: Long): Boolean = packs.packAtHasServeClip(timeMs)

    /** Shows the toast of a key that did nothing (B-68, "Hints"), above the point bar. */
    fun showKeyHint(hint: KeyHint) {
        if (!isActive()) return
        hints.toast(hint.id, controls, hint.message)
    }

    /**
     * The outcome of the clip with [pointId] for the Edit dialog, or null in basic mode: then the dialog shows no
     * outcome (B-68, "Corrections").
     */
    fun outcomeForm(pointId: String): PointOutcomeForm? {
        if (!score.isAdvanced) return null
        val index = points.indexOfFirst { it.id == pointId }
        val point = points.getOrNull(index) ?: return null
        val clip = packs.of(pointId)
        return PointOutcomeForm(
            current = ClipOutcomes.current(score, pointId),
            serve = clip?.serve,
            doubleFault = clip?.kind == ClipKind.DOUBLE_FAULT,
            server = timeline?.serverOfPoint?.getOrNull(index),
            p1Name = players().p1Name,
            p2Name = players().p2Name,
            zeroLength = PointEndings.isZeroLength(point),
        )
    }

    /** The Edit dialog of advanced mode gave the clip with [pointId] a new outcome (B-68). */
    fun editOutcome(pointId: String, outcome: ClipOutcome) {
        val point = points.firstOrNull { it.id == pointId } ?: return
        if (!score.isAdvanced || !ClipOutcomes.allows(point, outcome)) return
        if (ClipOutcomes.current(score, pointId) == ClipOutcomes.normalized(outcome)) return
        change { ClipOutcomes.apply(it, pointId, outcome) }
    }

    /**
     * An ending key or button (B-68). It works only in advanced mode and after the answer to "Who serves?". The ace
     * works only for the server of the pending point or of the selected point.
     * While the error kind question is open, a digit key ([fromKey]) answers the question and selects no ending. A
     * click on an ending button closes the question and selects the ending.
     */
    private fun requestEnding(choice: EndingChoice, fromKey: Boolean) {
        if (errorQuestion.isOpen) {
            if (fromKey) {
                when (val result = errorQuestion.onKey(EndingButtons.keyOf(choice))) {
                    is ErrorKindKey.Answer -> setErrorKind(result.pointId, result.kind)
                    ErrorKindKey.Ignored -> Unit
                    ErrorKindKey.SelectsEnding -> Unit
                }
                return
            }
            closeErrorQuestion()
        }
        if (!score.isAdvanced || serveQuestion.isOpen) return
        if (!PointEndings.isAvailable(choice, endingServer())) return
        onScoreRequest(ScoreRequest.WithEnding(choice))
    }

    /** The server of the clip that an ending key changes: the pending point, or else the selected point. */
    private fun endingServer(): Int? = if (pending) pendingServer() else timeline?.serverOfPoint?.getOrNull(selectedIndex())

    /** The server of the pending point: the engine computes it with a point at the pending start. */
    private fun pendingServer(): Int? {
        val id = pendingId ?: return null
        val start = pendingStart?.toInt() ?: return null
        val virtual = PointV1(id = id, startMs = start, endMs = start + 1)
        val all = (points + virtual).sortedBy { it.startMs }
        return runCatching { timelineOf(score, all).serverOfPoint.getOrNull(all.indexOf(virtual)) }.getOrNull()
    }

    /** True when a point or the pending point has a serve mark. Then the engine knows the server of each point. */
    private fun serverKnown(): Boolean = score.serverMarks.keys.any { id -> id == pendingId || points.any { it.id == id } }

    /** True when advanced mode asks the error kind after an error ending: the project option is on (B-68). */
    private fun asksErrorKind(): Boolean = score.isAdvanced && score.askErrorKind

    /** True when the clip with [pointId] has an error ending: a forced or an unforced error. */
    private fun isErrorClip(pointId: String?): Boolean =
        pointId != null && points.any { it.id == pointId } && score.detailOf(pointId).ending?.isError == true

    /** Closes the error kind question when it does not apply any more. See [ErrorKindQuestion.sync]. */
    private fun syncErrorQuestion() = errorQuestion.sync(asksErrorKind(), isErrorClip(errorQuestion.target), selectedId)

    /**
     * Esc (B-68): closes the open error kind question. Returns true when Esc closed it. Then Esc does nothing more, and
     * the pending point stays.
     */
    fun onEscape(): Boolean {
        if (!errorQuestion.onEscape()) return false
        render()
        return true
    }

    /** C, R, Shift+R, Ctrl+R, or a click on an ending button closes the question without an answer. */
    private fun closeErrorQuestion() {
        if (!errorQuestion.isOpen) return
        errorQuestion.close()
        render()
    }

    /** A click on the strip: the question opens again for the clip of the strip. Then the digit keys answer it. */
    private fun openErrorQuestion() {
        val pointId = errorStripClip() ?: return
        errorQuestion.open(pointId)
        render()
    }

    /** A click on an answer of the strip: sets or changes the error kind of the clip of the strip. */
    private fun answerErrorKind(kind: ErrorKind) {
        val pointId = errorStripClip() ?: return
        errorQuestion.close()
        setErrorKind(pointId, kind)
    }

    /** Gives [kind] to the error of the clip with [pointId]. The question is closed. */
    private fun setErrorKind(pointId: String, kind: ErrorKind) {
        if (!isErrorClip(pointId)) return render()
        val detail = score.detailOf(pointId)
        if (detail.errorKind == kind) return render()
        change { it.withDetail(pointId, it.detailOf(pointId).copy(errorKind = kind)) }
    }

    /** The clip of the error kind strip, or null when the strip does not show. */
    private fun errorStripClip(): String? = errorQuestion.stripClip(asksErrorKind(), selectedId, isErrorClip(selectedId))

    /** The option "Ask error kind" of the project (B-68). Off closes the question. */
    private fun setAskErrorKind(ask: Boolean) {
        if (ask == score.askErrorKind) return
        change { it.copy(askErrorKind = ask) }
    }

    /** The user selected a mode in the mode switch. A mode change deletes no data. */
    private fun selectMode(mode: ScoringMode) {
        if (mode == score.scoringMode) return
        change { it.copy(scoringMode = mode) }
        serveQuestion.onModeSelected(score.isAdvanced, serverKnown())
        render()
    }

    /** The answer to "Who serves?": the serve mark of the pending point, or of the selected point. */
    private fun answerServer(player: Int) {
        val mark = serveQuestion.answer(player, pendingId ?: selectedId)
        if (mark != null) markNewServer(mark.first, mark.second) else render()
    }

    private fun markNewServer(pointId: String, player: Int) {
        change { it.copy(serverMarks = it.serverMarks + (pointId to if (player == 1) Outcome.P1 else Outcome.P2)) }
        hints.dismiss(HintId.SERVE_MARK)
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

    /**
     * S or the serve button: the other side serves the selected point. Without a known server, side 1 serves.
     * When the change gives a new winner to an ace or a double fault, the user confirms it first (B-68).
     */
    private fun switchServer() {
        val index = selectedIndex()
        val point = points.getOrNull(index) ?: return
        if (!PointEndings.canScore(point, score)) return
        val next = if (timeline?.serverOfPoint?.getOrNull(index) == 1) Outcome.P2 else Outcome.P1
        val preview = runCatching { ServerChanges.preview(points, score, withServer(score, index, point, next)) }.getOrNull()
        if (preview != null) {
            val message = ServerChangeText.message(preview, players())
            if (!confirmServerChange(tr("points.serverChange.title"), message)) return
        }
        change { withServer(it, index, point, next) }
        if (score.serverMarks.isNotEmpty()) hints.dismiss(HintId.SERVE_MARK)
    }

    /** [current] with [server] on the point at [index]. A mark that only repeats the computed server is not kept. */
    private fun withServer(current: ScoreV1, index: Int, point: PointV1, server: Outcome): ScoreV1 {
        val serverNumber = if (server == Outcome.P1) 1 else 2
        return when {
            current.serverMarks[point.id] == server -> current.copy(serverMarks = current.serverMarks - point.id)
            timelineOf(current).serverOfPoint.getOrNull(index) == serverNumber -> current
            else -> {
                val without = current.serverMarks - point.id
                val computed = timelineOf(current.copy(serverMarks = without)).serverOfPoint.getOrNull(index)
                current.copy(serverMarks = if (computed != serverNumber) without + (point.id to server) else without)
            }
        }
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
            maxSets = if (rules.manualScoring || !rules.hasMatchEnd()) MAX_SETS else rules.bestOfSets,
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

    private fun timelineOf(s: ScoreV1, of: List<PointV1> = points) =
        ScoringEngine.timeline(of, s.outcomes, s.rules.normalized(), s.manualMarks(), s.serverMarks)

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
            val timing = TimingContext(
                positionMs = playheadMs,
                firstPointStartMs = points.minOfOrNull { it.startMs }?.toLong(),
                lastPointEndMs = points.maxOfOrNull { it.endMs }?.toLong(),
            )
            ScoreboardSettingsDialog.show(
                controls, score.scoreboard, sample, loadFrame, takeCreditRequest, videoLanguage = videoLanguage, timing = timing,
            ) { preview ->
                previewStyle = preview
                // The start and the end settings change the spans, so the preview builds them again.
                rebuildSpans()
            }
        } finally {
            previewStyle = null
        }
        if (result == null) {
            rebuildSpans()
            return
        }
        change { it.copy(scoreboard = result) }
        try {
            styleDefaults.save(result)
        } catch (_: Throwable) {
            // The default style is a convenience; the project keeps its own style.
        }
    }

    private fun redrawScoreboard() = showScoreboardAt(playheadMs, redraw = true)

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
        change { PointEndings.withWinnerOnly(it, point.id, outcome) }
    }

    /** An ending key of advanced mode: the new winner and the new ending of the selected point (B-68). */
    private fun setEnding(choice: EndingChoice) {
        val point = points.getOrNull(selectedIndex()) ?: return
        if (point.endMs <= point.startMs) return
        if (score.outcomes[point.id] != choice.outcome) analytics.record(AnalyticsEvent.ScoreRecorded)
        change { PointEndings.withEnding(it, point.id, choice) }
    }

    /**
     * X or L on the selected point (B-68): the clip becomes a fault clip or a let clip. Only a fault clip can have zero
     * length, so L does nothing on a clip of zero length.
     */
    private fun setServeEnding(ending: Ending) {
        val point = points.getOrNull(selectedIndex()) ?: return
        if (PointEndings.isZeroLength(point) && !PointEndings.allowsZeroLength(ending)) return
        change { PointEndings.withServeEnding(it, point.id, ending) }
    }

    /**
     * Reads score.json, applies [update], and writes the file, so that the other fields stay as they are. The winners
     * of the aces and the double faults then come from the details (B-68).
     */
    private fun change(update: (ScoreV1) -> ScoreV1) {
        val dir = projectDir ?: return
        val current = readScore() ?: return
        val updated = PointDetails.resolve(points, update(current))
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
        packs = ServePacks.of(points, score.outcomes, score.details)
        serveQuestion.sync(score.isAdvanced, serverKnown())
        syncErrorQuestion()
        controls.setPlayers(players())
        render()
        rebuildSpans()
        refreshMatchEndNotice()
    }

    /** Shows the notice when points after the match point have a winner, and hides it when none have. */
    private fun refreshMatchEndNotice() {
        val matchIndex = timeline?.statesAfterPoint?.indexOfFirst { it.matchEnded } ?: -1
        matchPointId = points.getOrNull(matchIndex)?.id
        val scoredAfter = if (matchIndex < 0) {
            0
        } else {
            points.drop(matchIndex + 1).count { score.outcomes[it.id] == Outcome.P1 || score.outcomes[it.id] == Outcome.P2 }
        }
        matchEndNotice.show(matchIndex + 1, scoredAfter)
    }

    private fun render() = renderPanel()

    /** Builds [spans] again after a change of the points or the score, and shows the scoreboard of the playhead again. */
    private fun rebuildSpans() {
        spans = runCatching {
            val players = players()
            I18n.inVideo(videoLanguage) {
                ScoreboardTimelineBuilder.build(
                    points = points,
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
                    fullVideo = true,
                    timing = ScoreboardTiming.of(style()),
                    preview = true,
                    secondServeIds = ScoreboardTimelineBuilder.secondServeIds(points, score),
                )
            }
        }.getOrDefault(emptyList())
        redrawScoreboard()
    }

    /**
     * The serve labels (B-68): the serve number of the start button (null in basic mode), and the serve number of the
     * clip that X changes or ends.
     * - The start button uses the pack at the pending start or at the playhead.
     * - X uses the pending point, else the selected clip, else the playhead.
     */
    private fun serveLabels(): Pair<Int?, Int> {
        if (!score.isAdvanced) return null to 1
        val start = packs.serveAt(pendingStart ?: playheadMs)
        val point = points.getOrNull(selectedIndex())
        val fault = when {
            pending -> start
            point != null -> packs.of(point.id)?.serve ?: packs.serveAt(point.startMs.toLong())
            else -> start
        }
        return start to fault
    }

    // The serve labels that the point bar shows now.
    private var shownServeLabels: Pair<Int?, Int>? = null

    private fun renderPanel() {
        modeBar.render(visible = projectDir != null && score.sport == Sport.TENNIS, mode = score.scoringMode, askErrorKind = score.askErrorKind)
        renderErrorStrip()
        val labels = serveLabels()
        shownServeLabels = labels
        onServeNumber(labels.first)
        val index = selectedIndex()
        val point = points.getOrNull(index)
        val advanced = score.isAdvanced
        val asking = serveQuestion.isOpen
        if (point == null) {
            controls.render(
                PointControlsState(
                    total = points.size, pending = pending, advanced = advanced, askServer = asking, endingServer = endingServer(),
                    serve = labels.second,
                ),
            )
            return
        }
        controls.render(
            PointControlsState(
                index = index,
                total = points.size,
                number = packRows.of(points, score).number(point.id),
                favorite = point.favorite,
                hasPrevious = index > 0,
                hasNext = index + 1 < points.size,
                outcome = score.outcomeOf(point.id),
                canScore = PointEndings.canScore(point, score),
                pending = pending,
                server = timeline?.serverOfPoint?.getOrNull(index),
                serverMarked = point.id in score.serverMarks,
                overridden = score.scoreOverrides[point.id]?.isEmpty() == false,
                advanced = advanced,
                askServer = asking,
                endingServer = endingServer(),
                ending = EndingChoice.of(score.outcomeOf(point.id), score.detailOf(point.id).ending),
                serve = labels.second,
                serveEnding = score.detailOf(point.id).ending?.takeIf { it == Ending.SERVE_FAULT || it == Ending.LET },
                zeroLength = PointEndings.isZeroLength(point),
            ),
        )
    }

    /** The error kind strip: the open question, or the active clip with an error ending (B-68). */
    private fun renderErrorStrip() {
        val pointId = errorStripClip()
        val choice = pointId?.let { EndingChoice.of(score.outcomeOf(it), score.detailOf(it).ending) }
        errorStrip.render(
            ErrorKindStripState(
                visible = pointId != null && choice != null,
                open = errorQuestion.isOpen,
                answer = pointId?.let { score.detailOf(it).errorKind },
                ending = choice?.let(controls::endingLabel).orEmpty(),
            ),
        )
    }

    private companion object {
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

/** A scoring key or button (B-68). */
internal sealed interface ScoreRequest {
    /** Q, W, or E: the winner only, with no ending. */
    data class Winner(val outcome: Outcome) : ScoreRequest

    /** An ending key of advanced mode: the winner and the ending. */
    data class WithEnding(val choice: EndingChoice) : ScoreRequest

    /** X in advanced mode: a serve fault. The second fault of a pack is the double fault. */
    data object Fault : ScoreRequest

    /** L in advanced mode: a let. */
    data object Let : ScoreRequest
}
