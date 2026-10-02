package org.litvin.ui.tabs.scoring

import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.GeometryViewportPanel
import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardDisplay
import org.litvin.ScoreboardTimelineBuilder
import org.litvin.projects.ManifestIO
import org.litvin.SessionSettings
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.media.PlayerStatus
import org.litvin.media.relativeSeekDeltaMs
import org.litvin.media.VideoOverlay
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import org.litvin.media.SwingMediaPlayer
import org.litvin.scoring.ManualScoreMarks
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoringEngine
import org.litvin.scoring.ScoringEngine.MatchState
import org.litvin.ui.commons.HintBalloon
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.VideoFrameLoader
import org.litvin.ui.commons.VideoFrameRequest
import org.litvin.ui.commons.uiSafe
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.tabs.scoring.ui.PointsListData
import org.litvin.ui.tabs.scoring.ui.ScorePanel
import org.litvin.ui.tabs.scoring.ui.ScorePanelState
import org.litvin.ui.tabs.scoring.ui.ScorePlayers
import org.litvin.ui.tabs.scoring.ui.ScoreSettings
import org.litvin.ui.tabs.scoring.ui.ScoreSettingsDialog
import org.litvin.ui.tabs.scoring.ui.ScoreSettingsEditor
import org.litvin.ui.tabs.scoring.ui.ScoreboardSettingsDialog
import org.litvin.ui.tabs.scoring.ui.ScoringButton
import org.litvin.ui.tabs.scoring.ui.ScoringPlaybackBar
import org.litvin.ui.tabs.scoring.ui.ScoringPointsList
import org.litvin.ui.tabs.scoring.ui.ScoringUi
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GridLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * The Scoring tab (design/scoring-redesign/final.html):
 * - the video with the scoreboard preview, and the playback bar of the selected point under it,
 * - the side column: the score panel (Previous, Point x / y, Next, the score after the point, and the outcome
 *   buttons), the points list, and the Scoring settings and Scoreboard style buttons.
 *
 * The tab keeps the scoring data of the project (score.json) and saves each change at once.
 */
class SwingScoringPanel(
    private val player: SwingMediaPlayer,
    private val adjustments: AdjustmentsSession,
    private val dialogs: UserDialogService,
    private val styleDefaults: ScoreboardStyleDefaults = ScoreboardStyleDefaults.NONE,
    private val scoreSettingsEditor: ScoreSettingsEditor = ScoreSettingsDialog,
    private val scoreSettingsHint: ScoreSettingsHint = ScoreSettingsHint.NONE,
    private val frameLoader: VideoFrameLoader = VideoFrameLoader(),
) : JPanel(BorderLayout()), AutoCloseable {
    constructor() : this(
        MpvSwingMediaPlayerAdapter(),
        AdjustmentsStore.legacySession(),
        SwingUserDialogService(),
    )

    /** Opens the Points tab at the point with this id ("Go to point" in the list). */
    var onGoToPoint: ((pointId: String) -> Unit)? = null

    // Active state controlled by navigation
    private var isActive: Boolean = false
    private var disposed: Boolean = false

    fun onActivated(): Unit = uiSafe {
        isActive = true
        ensurePlayerLoaded()
        player.activatePreview("scoring activated")
        player.pause()
        // Refresh points every time the tab is opened to reflect latest Points tab changes
        refreshPointsFromProject()
        // Do not auto-play; optionally restore focus
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
        promptScoreSettingsOnFirstVisit()
    }

    /**
     * Selects the point with [pointId] and shows it in the player, for example after a click in the Stats tab.
     * Call it after [onActivated], because the activation reads the points again.
     */
    fun selectPoint(pointId: String): Unit = uiSafe {
        val index = points.indexOfFirst { it.id == pointId }
        if (index >= 0) setSelectedIndex(index, userInitiated = false)
    }

    fun onDeactivated(): Unit = uiSafe {
        isActive = false
        hideScoreSettingsHint()
        player.pause()
        player.deactivatePreview("scoring deactivated")
        // Flush pending autosave when leaving the tab
        saveNow()
    }

    /** Release Swing and native-player resources; safe to call more than once. */
    override fun close() {
        if (disposed) return
        disposed = true
        onDeactivated()
        unsubscribeAdjustments?.invoke()
        unsubscribeAdjustments = null
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.onReady = null
        player.setPreviewOverlay(null)
        adjustments.flush()
        player.close()
    }

    fun dispose() = close()

    private var unsubscribeAdjustments: (() -> Unit)? = null

    // Deferred media loading
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false

    override fun addNotify(): Unit = uiSafe {
        super.addNotify()
        SwingUtilities.invokeLater { ensurePlayerLoaded() }
    }

    private fun ensurePlayerLoaded(): Unit = uiSafe {
        try {
            if (isMediaLoaded) return@uiSafe
            val videoFile = pendingMediaFile ?: return@uiSafe
            val wnd = SwingUtilities.getWindowAncestor(player.component)
            if (!player.component.isDisplayable || wnd == null || !wnd.isShowing) return@uiSafe
            player.load(videoFile)
            player.pause()
            isMediaLoaded = true
            refreshVideoScoreboardOverlay()
            // Re-apply current adjustments after media is loaded so the player picks them up
            try {
                player.applyPreviewAdjustments(adjustments.get())
                geometryViewport.refreshGeometry()
            } catch (_: Throwable) { /* ignore */ }
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), "Failed to load project")
        }
    }

    private var projectDir: String? = null

    // Data
    private var points: List<PointV1> = emptyList()

    private var scoreboardSettings = ScoreboardSettingsV1()
    private var rules = MatchRulesV1()
    private val manualGameWins: MutableMap<String, Outcome> = LinkedHashMap()
    private val manualSetWins: MutableMap<String, Outcome> = LinkedHashMap()
    private val manualMarks: ManualScoreMarks
        get() = ManualScoreMarks(LinkedHashMap(manualGameWins), LinkedHashMap(manualSetWins))

    // The servers that the user marked (see ScoringEngine.timeline), and the computed server of each point
    private val serverMarks: MutableMap<String, Outcome> = LinkedHashMap()
    private var serverOfPoint: List<Int?> = emptyList()

    // The score after each point, computed by ScoringEngine
    private var statesAfterPoint: List<MatchState> = emptyList()

    // False until the score settings open once for this project (see promptScoreSettingsOnFirstVisit)
    private var scoreSettingsReviewed = true
    private var scoreSettingsBalloon: HintBalloon? = null

    // Settings that the open settings dialog shows on the video before the user saves them.
    private var scoreboardPreviewSettings: ScoreboardSettingsV1? = null
    private var lastScoreboardDisplay: ScoreboardDisplay? = null

    private var player1ColorHex: String = ScoreboardComponent.DEFAULT_PLAYER1_HEX
    private var player2ColorHex: String = ScoreboardComponent.DEFAULT_PLAYER2_HEX

    // Outcomes persistence (ScoreV1). "Scored" includes NONE.
    private val outcomesByPointId: MutableMap<String, Outcome> = LinkedHashMap()
    private val scoredPointIds: Set<String>
        get() = outcomesByPointId.keys

    private var selectedPointIndex: Int = -1

    private var player1Name: String = "Player 1"
    private var player2Name: String = "Player 2"
    private fun displayNameP1(): String = player1Name.ifBlank { "Player 1" }
    private fun displayNameP2(): String = player2Name.ifBlank { "Player 2" }

    private fun players() = ScorePlayers(
        p1Name = displayNameP1(),
        p2Name = displayNameP2(),
        p1Color = ScoringUi.parseColor(player1ColorHex, Color(ScoreboardComponent.DEFAULT_PLAYER1_RGB)),
        p2Color = ScoringUi.parseColor(player2ColorHex, Color(ScoreboardComponent.DEFAULT_PLAYER2_RGB)),
    )

    // Current selected segment bounds [startMs, endMs)
    private var segmentStartMs: Long = 0L
    private var segmentEndMs: Long = 0L

    private lateinit var geometryViewport: GeometryViewportPanel

    private val playbackBar = ScoringPlaybackBar(
        onTogglePlay = { togglePlayPause() },
        onNudge = { delta -> seekBy(delta); focusPlayer() },
        onScrub = { target ->
            player.pause() // seeking pauses; the tab does not advance by itself
            player.seek(target)
            updateScrubUi(target)
            updateVideoControls()
        },
        onSpeedIndex = { index -> setSpeedIndex(index); focusPlayer() },
        onToggleFrameStep = { toggleFrameStep() },
    )

    private val scorePanel = ScorePanel(
        onPrevious = { goToPreviousPoint() },
        onNext = { advanceToNextPoint() },
        onToggleFavorite = { toggleFavorite(selectedPointIndex); focusPlayer() },
        onOutcome = { outcome -> setOutcomeForSelectedPoint(outcome) },
        onServe = { server -> markServerForSelectedPoint(server) },
        onManualGame = { winner -> toggleManualMark(manualGameWins, winner) },
        onManualSet = { winner -> toggleManualMark(manualSetWins, winner) },
    )

    private val pointsList = ScoringPointsList(
        onSelect = { index -> if (index in points.indices) setSelectedIndex(index, userInitiated = true) },
        onToggleFavorite = { index -> toggleFavorite(index); focusPlayer() },
        onGoToPoint = { index -> goToPointInPointsTab(index) },
    )

    private val scoreSettingsButton = ScoringButton("Scoring settings", Material2MZ.TUNE, alignLeft = true).apply {
        name = "score-settings"
        toolTipText = "Set the player names and colors, the match format, and manual scoring"
        addActionListener { openScoreSettings() }
    }
    private val scoreboardStyleButton = ScoringButton("Scoreboard style", alignLeft = true, glyph = ScoringUi::scoreboardIcon).apply {
        name = "scoreboard-style"
        toolTipText = "Set the scoreboard style, title, position, and size"
        addActionListener { openScoreboardStyle() }
    }

    // Wire project and media
    fun setProjectManifest(path: String): Unit = uiSafe {
        this.projectDir = File(path).parentFile.absolutePath
        // Load adjustments for this project into the central store
        adjustments.load(projectDir!!)
        // Load points from EDL (sorted by startMs)
        val edl = EdlIO.readForProjectDir(projectDir!!)
        points = edl.points.sortedBy { it.startMs }
        // Load outcomes and player names from score.json (ignore orphans)
        outcomesByPointId.clear()
        manualGameWins.clear()
        manualSetWins.clear()
        serverMarks.clear()

        val isNewScore = !ScoreIO.existsForProjectDir(projectDir!!)
        val score = ScoreIO.readForProjectDir(projectDir!!)
        player1Name = score.player1Name
        player2Name = score.player2Name
        player1ColorHex = score.player1ColorHex
        player2ColorHex = score.player2ColorHex
        // A new project starts with the scoreboard style that the user saved last
        scoreboardSettings = if (isNewScore) styleDefaults.load() ?: score.scoreboard else score.scoreboard
        rules = score.rules.normalized()
        scoreSettingsReviewed = score.scoreSettingsReviewed
        // Outcomes and manual game/set marks
        val validIds = points.map { it.id }.toSet()
        score.outcomes.forEach { (id, out) -> if (id in validIds) outcomesByPointId[id] = out }
        score.manualGameWins.forEach { (id, winner) -> if (id in validIds) manualGameWins[id] = winner }
        score.manualSetWins.forEach { (id, winner) -> if (id in validIds) manualSetWins[id] = winner }
        score.serverMarks.forEach { (id, server) -> if (id in validIds && server != Outcome.NONE) serverMarks[id] = server }
        // Keep the default style in the project, so that the Export tab uses it too
        if (isNewScore) saveNow()

        selectedPointIndex = -1
        scorePanel.setPlayers(players())
        refreshScoring()
        autoSelectInitial()
        // Load media from manifest (deferred until component is displayable)
        val manifest = ManifestIO.read(path)
        val src = manifest.sourceVideo
        if (!src.isNullOrBlank() && File(src).exists()) {
            pendingMediaFile = File(src)
            isMediaLoaded = false
            ensurePlayerLoaded()
        }
        refreshVideoScoreboardOverlay()
    }

    // Reload points from the project's EDL and refresh UI; invoked on tab activation
    private fun refreshPointsFromProject(): Unit = uiSafe {
        val dir = projectDir ?: return@uiSafe
        // Remember currently selected point id (if any) to restore selection after reload
        val prevSelectedId = points.getOrNull(selectedPointIndex)?.id
        val newPoints = EdlIO.readForProjectDir(dir).points.sortedBy { it.startMs }
        points = newPoints
        // Remove outcomes for orphaned point ids (keep existing outcomes for still-valid ids)
        val validIds = newPoints.map { it.id }.toSet()
        outcomesByPointId.keys.retainAll(validIds)
        manualGameWins.keys.retainAll(validIds)
        manualSetWins.keys.retainAll(validIds)
        serverMarks.keys.retainAll(validIds)
        // The selected point stays selected when it is still there. Otherwise the first point without a score is selected.
        val newIndex = prevSelectedId?.let { id -> newPoints.indexOfFirst { it.id == id } } ?: -1
        selectedPointIndex = newIndex
        refreshScoring()
        if (newIndex >= 0) keepSelection() else autoSelectInitial()
    }

    /**
     * Shows the selected point again after the points were read again. The Points tab can change its times,
     * so the scrub bar gets the new bounds. The player seeks only when the playhead is outside the point.
     */
    private fun keepSelection(): Unit = uiSafe {
        val point = points.getOrNull(selectedPointIndex) ?: return@uiSafe
        pointsList.setSelectedIndex(selectedPointIndex, scroll = true)
        segmentStartMs = point.startMs.toLong()
        segmentEndMs = point.endMs.toLong()
        playbackBar.scrub.setSegment(segmentStartMs, segmentEndMs)
        val now = player.currentTimeMs()
        if (now < segmentStartMs || now >= segmentEndMs) {
            player.seek(segmentStartMs)
            updateScrubUi(segmentStartMs)
        } else {
            updateScrubUi(now)
        }
        refreshVideoScoreboardOverlay()
    }

    init {
        installKeyBindings()
        isOpaque = true
        background = Palette.PANEL

        // Left column: the video, and the playback bar of the selected point under it
        geometryViewport = GeometryViewportPanel(player.component)
        geometryViewport.name = "video"
        val videoColumn = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = Palette.VIDEO_BG
            minimumSize = Dimension(320, 0)
            add(geometryViewport, BorderLayout.CENTER)
            add(playbackBar, BorderLayout.SOUTH)
        }

        // Side column: the score panel, the points list, and the settings buttons
        val footer = JPanel(GridLayout(1, 2, 8, 0)).apply {
            isOpaque = true
            background = Palette.BG
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
                BorderFactory.createEmptyBorder(10, 12, 12, 12),
            )
            add(scoreSettingsButton)
            add(scoreboardStyleButton)
        }
        val sideColumn = JPanel(BorderLayout()).apply {
            name = "scoring-side"
            isOpaque = true
            background = Palette.BG
            border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)
            preferredSize = Dimension(SIDE_COLUMN_WIDTH, 0)
            add(scorePanel, BorderLayout.NORTH)
            add(pointsList, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }

        add(videoColumn, BorderLayout.CENTER)
        add(sideColumn, BorderLayout.EAST)

        // Apply adjustments from the central store (parity with Points/Color tabs)
        unsubscribeAdjustments = adjustments.subscribe { adj ->
            player.applyPreviewAdjustments(adj)
            geometryViewport.refreshGeometry()
        }
        try {
            player.applyPreviewAdjustments(adjustments.get())
            geometryViewport.refreshGeometry()
        } catch (_: Throwable) { /* ignore */ }

        // Wire media callbacks for segment clamping and UI updates
        player.onTimeChanged = { t ->
            EventQueue.invokeLater { onPlayerTimeChanged(t) }
        }
        player.onStatusChanged = {
            EventQueue.invokeLater { updateVideoControls() }
        }
        player.onReady = {
            EventQueue.invokeLater {
                // Apply the session speed to the player
                player.setRate(SessionSettings.toRate(SessionSettings.playbackSpeedIndex))
                if (selectedPointIndex in points.indices) {
                    // The canvas stays black until the first decoded frame is shown.
                    // Nudge by seeking a millisecond forward and back while paused to force a frame render.
                    uiSafe {
                        player.pause()
                        player.seek(segmentStartMs)
                        val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
                        val nudge = (segmentStartMs + 1).coerceAtMost(maxPlayable)
                        if (nudge != segmentStartMs) player.seek(nudge)
                        player.seek(segmentStartMs)
                    }
                }
                updateVideoControls()
                refreshVideoScoreboardOverlay()
            }
        }
        scorePanel.setPlayers(players())
        onSelectionChanged()
        updateVideoControls()
    }

    /** Computes the score of all points again and shows it in the list and the score panel. */
    private fun refreshScoring(): Unit = uiSafe {
        val timeline = ScoringEngine.timeline(points, outcomesByPointId, rules, manualMarks, serverMarks)
        statesAfterPoint = timeline.statesAfterPoint
        serverOfPoint = timeline.serverOfPoint
        pointsList.setData(
            PointsListData(
                points = points,
                outcomes = LinkedHashMap(outcomesByPointId),
                states = statesAfterPoint,
                serverMarks = LinkedHashMap(serverMarks),
                players = players(),
            ),
        )
        pointsList.setSelectedIndex(selectedPointIndex, scroll = false)
        renderScorePanel()
    }

    private fun renderScorePanel() {
        val index = selectedPointIndex
        val point = points.getOrNull(index)
        if (point == null) {
            scorePanel.render(ScorePanelState(total = points.size, manual = rules.manualScoring))
            return
        }
        scorePanel.render(
            ScorePanelState(
                index = index,
                total = points.size,
                favorite = point.favorite,
                hasPrevious = index > 0,
                hasNext = index + 1 < points.size,
                outcome = outcomesByPointId[point.id],
                canScore = point.endMs > point.startMs,
                after = statesAfterPoint.getOrNull(index) ?: MatchState.INITIAL,
                server = serverOfPoint.getOrNull(index),
                serverMarked = point.id in serverMarks,
                manual = rules.manualScoring,
                manualGame = manualGameWins[point.id],
                manualSet = manualSetWins[point.id],
            ),
        )
    }

    private fun autoSelectInitial() {
        if (points.isEmpty()) {
            selectedPointIndex = -1
            pointsList.setSelectedIndex(-1, scroll = false)
            onSelectionChanged()
            return
        }
        val firstUnscored = points.indexOfFirst { !scoredPointIds.contains(it.id) }
        setSelectedIndex(if (firstUnscored >= 0) firstUnscored else 0, userInitiated = false)
    }

    private fun setSelectedIndex(index: Int, userInitiated: Boolean, autoPlay: Boolean = false) {
        // A click on the selected row does nothing, so the list does not jump.
        if (index == selectedPointIndex) return
        selectedPointIndex = index
        // A click selects a visible row. Keys and automatic moves bring the row into view.
        pointsList.setSelectedIndex(index, scroll = !userInitiated)
        onSelectionChanged(autoPlay)
    }

    private fun onSelectionChanged(autoPlay: Boolean = false): Unit = uiSafe {
        val point = points.getOrNull(selectedPointIndex)
        if (point == null) {
            segmentStartMs = 0L
            segmentEndMs = 0L
            playbackBar.scrub.reset()
            player.setPreviewOverlay(null)
            renderScorePanel()
            return@uiSafe
        }
        segmentStartMs = point.startMs.toLong()
        segmentEndMs = point.endMs.toLong()
        playbackBar.scrub.setSegment(segmentStartMs, segmentEndMs)
        renderScorePanel()
        refreshVideoScoreboardOverlay()
        // Jump playback to the point start; callers can ask for playback.
        player.pause()
        player.seek(segmentStartMs)
        if (autoPlay) player.play()
        if (isActive) player.component.requestFocusInWindow()
        updateVideoControls()
    }

    // R: the next point, with playback
    private fun advanceToNextPoint(): Unit = uiSafe {
        if (selectedPointIndex !in points.indices) return@uiSafe
        val next = selectedPointIndex + 1
        if (next !in points.indices) return@uiSafe
        setSelectedIndex(next, userInitiated = false, autoPlay = true)
        focusPlayer()
    }

    // Shift+R: the previous point, with playback
    private fun goToPreviousPoint(): Unit = uiSafe {
        if (selectedPointIndex !in points.indices) return@uiSafe
        val previous = selectedPointIndex - 1
        if (previous !in points.indices) return@uiSafe
        setSelectedIndex(previous, userInitiated = false, autoPlay = true)
        focusPlayer()
    }

    /** "Go to point": selects the point and opens the Points tab at it. */
    private fun goToPointInPointsTab(index: Int): Unit = uiSafe {
        val point = points.getOrNull(index) ?: return@uiSafe
        setSelectedIndex(index, userInitiated = true)
        player.pause()
        onGoToPoint?.invoke(point.id)
    }

    private fun onPlayerTimeChanged(absMs: Long) {
        // If we have a valid segment, clamp playback to [start, end)
        if (segmentEndMs > segmentStartMs) {
            val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
            when {
                absMs >= segmentEndMs -> {
                    // Pause at end and clamp to last playable millisecond
                    player.pause()
                    player.seek(maxPlayable)
                    updateScrubUi(maxPlayable)
                    return
                }

                absMs < segmentStartMs -> {
                    // Clamp to start if an external seek went before the segment
                    player.seek(segmentStartMs)
                    updateScrubUi(segmentStartMs)
                    return
                }
            }
        }
        updateScrubUi(absMs)
    }

    private fun updateScrubUi(absMs: Long) {
        playbackBar.scrub.setPosition(absMs)
    }

    private fun focusPlayer() {
        EventQueue.invokeLater { uiSafe { player.component.requestFocusInWindow() } }
    }

    private fun togglePlayPause() {
        if (selectedPointIndex !in points.indices) return
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) {
            player.pause()
        } else {
            if (shouldRestartSegmentForPlay()) {
                player.seek(segmentStartMs)
                updateScrubUi(segmentStartMs)
            }
            player.play()
        }
        updateVideoControls()
        player.component.requestFocusInWindow()
    }

    private fun shouldRestartSegmentForPlay(): Boolean {
        if (segmentEndMs <= segmentStartMs) return false
        val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
        return player.currentTimeMs() >= maxPlayable
    }

    private fun updateVideoControls() {
        playbackBar.setPlaying(player.status() == PlayerStatus.PLAYING)
        playbackBar.speed.setSpeedIndex(SessionSettings.clampIndex(SessionSettings.playbackSpeedIndex))
        playbackBar.frameStep.on = SessionSettings.frameStepWhenPaused
    }

    private fun seekBy(deltaMs: Long) {
        // Compute target and clamp to current segment when available
        val playing = player.status() == PlayerStatus.PLAYING
        var target = (player.currentTimeMs() + relativeSeekDeltaMs(deltaMs, playing))
        if (segmentEndMs > segmentStartMs) {
            val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
            if (target < segmentStartMs) target = segmentStartMs
            if (target > maxPlayable) target = maxPlayable
        } else {
            if (target < 0L) target = 0L
        }
        player.seek(target)
        updateScrubUi(target)
    }

    private fun setSpeedIndex(index: Int): Unit = uiSafe {
        SessionSettings.playbackSpeedIndex = SessionSettings.clampIndex(index)
        player.setRate(SessionSettings.toRate(SessionSettings.playbackSpeedIndex))
        updateVideoControls()
    }

    private fun toggleFrameStep(): Unit = uiSafe {
        SessionSettings.frameStepWhenPaused = !SessionSettings.frameStepWhenPaused
        updateVideoControls()
        focusPlayer()
    }

    private fun installKeyBindings() {
        fun bind(key: String, actionName: String, runnable: () -> Unit) {
            listOf(WHEN_IN_FOCUSED_WINDOW, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).forEach { condition ->
                getInputMap(condition).put(KeyStroke.getKeyStroke(key), actionName)
            }
            actionMap.put(actionName, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    if (isTextEditingFocus()) return
                    runnable()
                    EventQueue.invokeLater { player.component.requestFocusInWindow() }
                }
            })
        }
        // Space toggles play/pause
        bind(AppShortcuts.PLAY_PAUSE.keyStroke, "togglePlayPause") { togglePlayPause() }
        // Arrow keys:
        // Default (frameStepWhenPaused == false): Left/Right = ±1s regardless of paused state; Shift = ±5s.
        // When frameStepWhenPaused == true: if paused → Left/Right step 1 frame; if playing → ±1s.
        bind(AppShortcuts.LEFT.keyStroke, "seekLeftOrPrevFrame") {
            val paused = player.status() == PlayerStatus.PAUSED
            if (SessionSettings.frameStepWhenPaused && paused) {
                updateScrubUi(player.stepFrameBackward(segmentStartMs))
            } else {
                seekBy(-1_000)
                playbackBar.flashSeek(-1_000)
            }
        }
        bind(AppShortcuts.RIGHT.keyStroke, "seekRightOrNextFrame") {
            val paused = player.status() == PlayerStatus.PAUSED
            if (SessionSettings.frameStepWhenPaused && paused) {
                val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
                updateScrubUi(player.stepFrameForward(maxPlayable))
            } else {
                seekBy(1_000)
                playbackBar.flashSeek(1_000)
            }
        }
        bind(AppShortcuts.SHIFT_LEFT.keyStroke, "seekLeft5s") { seekBy(-5_000); playbackBar.flashSeek(-5_000) }
        bind(AppShortcuts.SHIFT_RIGHT.keyStroke, "seekRight5s") { seekBy(5_000); playbackBar.flashSeek(5_000) }
        // Speed control via keyboard: Up/Down when player area has focus
        bind(AppShortcuts.UP.keyStroke, "speedUp") {
            if (!isPlayerAreaFocus()) return@bind
            changeSpeedBy(-1) // presets ordered high→low; Up means go to higher preset → lower index
        }
        bind(AppShortcuts.DOWN.keyStroke, "speedDown") {
            if (!isPlayerAreaFocus()) return@bind
            changeSpeedBy(1)
        }
        // Scoring hotkeys: Q = P1, W = No Point, E = P2
        bind(AppShortcuts.SCORE_PLAYER_1.keyStroke, "scoreP1") { setOutcomeForSelectedPoint(Outcome.P1) }
        bind(AppShortcuts.SCORE_NO_POINT.keyStroke, "scoreNone") { setOutcomeForSelectedPoint(Outcome.NONE) }
        bind(AppShortcuts.SCORE_PLAYER_2.keyStroke, "scoreP2") { setOutcomeForSelectedPoint(Outcome.P2) }
        // R advances to the next point and starts playback; Shift+R steps back
        bind(AppShortcuts.NEXT_POINT.keyStroke, "nextPoint") { advanceToNextPoint() }
        bind(AppShortcuts.PREVIOUS_POINT.keyStroke, "previousPoint") { goToPreviousPoint() }
        bind(AppShortcuts.TOGGLE_FAVORITE.keyStroke, "toggleFavorite") { toggleFavorite(selectedPointIndex) }
        // S switches the server of the selected point
        bind(AppShortcuts.SWITCH_SERVE.keyStroke, "switchServe") { switchServerForSelectedPoint() }
        // Frame-by-frame toggle: F
        bind(AppShortcuts.TOGGLE_FRAME_STEP.keyStroke, "toggleFrameStep") { toggleFrameStep() }
    }

    private fun isTextEditingFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            (fo is javax.swing.text.JTextComponent) && fo.isEnabled && fo.isVisible && fo.isEditable
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPlayerAreaFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            val comp = player.component
            fo != null && (fo === comp || SwingUtilities.isDescendingFrom(fo, comp))
        } catch (_: Throwable) {
            false
        }
    }

    private fun changeSpeedBy(delta: Int) {
        val current = SessionSettings.playbackSpeedIndex
        val next = SessionSettings.clampIndex(current + delta)
        if (next != current) setSpeedIndex(next)
    }

    private fun toggleFavorite(index: Int): Unit = uiSafe {
        if (index !in points.indices) return@uiSafe
        val dir = projectDir ?: return@uiSafe
        val pointId = points[index].id
        val updated = points.map { p ->
            if (p.id == pointId) p.copy(favorite = !p.favorite) else p
        }.sortedBy { it.startMs }
        try {
            EdlIO.writeForProjectDir(dir, EdlV1(points = updated, version = 1))
            points = updated
            refreshScoring()
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), "Failed to save favorite")
        }
    }

    private fun setOutcomeForSelectedPoint(outcome: Outcome) {
        if (selectedPointIndex !in points.indices) return
        // A point without a valid length gets no outcome
        if (segmentEndMs <= segmentStartMs) return
        val p = points[selectedPointIndex]
        outcomesByPointId[p.id] = outcome
        saveNow()
        refreshScoring()
        refreshVideoScoreboardOverlay()
        focusPlayer()
    }

    /** Manual scoring: marks [winner] in [marks] for the selected point, or clears the mark when it is already there. */
    private fun toggleManualMark(marks: MutableMap<String, Outcome>, winner: Outcome): Unit = uiSafe {
        if (!rules.manualScoring) return@uiSafe
        val point = points.getOrNull(selectedPointIndex) ?: return@uiSafe
        if (marks[point.id] == winner) marks.remove(point.id) else marks[point.id] = winner
        saveNow()
        refreshScoring()
        refreshVideoScoreboardOverlay()
        focusPlayer()
    }

    /**
     * Marks [server] as the server of the selected point. The next points continue from the mark.
     * A click on the player with a mark on this point clears the mark. A mark that only repeats
     * the computed server is not kept.
     */
    private fun markServerForSelectedPoint(server: Outcome): Unit = uiSafe {
        val point = points.getOrNull(selectedPointIndex) ?: return@uiSafe
        if (server == Outcome.NONE) return@uiSafe
        val serverNumber = if (server == Outcome.P1) 1 else 2
        when {
            serverMarks[point.id] == server -> serverMarks.remove(point.id)
            serverOfPoint.getOrNull(selectedPointIndex) == serverNumber -> return@uiSafe
            else -> {
                serverMarks.remove(point.id)
                val computed = ScoringEngine.timeline(points, outcomesByPointId, rules, manualMarks, serverMarks)
                    .serverOfPoint.getOrNull(selectedPointIndex)
                if (computed != serverNumber) serverMarks[point.id] = server
            }
        }
        saveNow()
        refreshScoring()
        refreshVideoScoreboardOverlay()
        focusPlayer()
    }

    /** Makes the other player the server of the selected point. Without a known server, player 1 serves. */
    private fun switchServerForSelectedPoint() {
        if (selectedPointIndex !in points.indices || segmentEndMs <= segmentStartMs) return
        val next = if (serverOfPoint.getOrNull(selectedPointIndex) == 1) Outcome.P2 else Outcome.P1
        markServerForSelectedPoint(next)
    }

    fun saveNow() {
        try {
            val dir = projectDir ?: return
            ScoreIO.writeForProjectDir(
                dir,
                ScoreV1(
                    outcomes = LinkedHashMap(outcomesByPointId),
                    version = 1,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    player1ColorHex = player1ColorHex,
                    player2ColorHex = player2ColorHex,
                    scoreboard = scoreboardSettings,
                    rules = rules,
                    manualGameWins = LinkedHashMap(manualGameWins),
                    manualSetWins = LinkedHashMap(manualSetWins),
                    scoreSettingsReviewed = scoreSettingsReviewed,
                    serverMarks = LinkedHashMap(serverMarks),
                ),
            )
        } catch (t: Throwable) {
            // Non-fatal; show error similarly to Points tab autosave
            dialogs.showError(this, t.message ?: t.toString(), "Autosave failed")
        }
    }

    /**
     * Opens the score settings automatically the first time the user opens this tab for a project.
     * When the dialog closes, a balloon points at the Scoring settings button until the user closes the balloon once.
     */
    private fun promptScoreSettingsOnFirstVisit() {
        if (projectDir == null || scoreSettingsReviewed) return
        EventQueue.invokeLater {
            if (isActive && projectDir != null && !scoreSettingsReviewed) {
                openScoreSettings()
                showScoreSettingsHint()
            }
        }
    }

    private fun showScoreSettingsHint(): Unit = uiSafe {
        if (!isActive || scoreSettingsHint.isDismissed()) return@uiSafe
        hideScoreSettingsHint()
        val balloon = HintBalloon(
            "You can change the scoring settings at any time with this button.",
            HintBalloon.Placement.ABOVE,
        ) {
            scoreSettingsBalloon = null
            uiSafe { scoreSettingsHint.dismiss() }
            focusPlayer()
        }
        scoreSettingsBalloon = balloon
        balloon.showAt(scoreSettingsButton)
    }

    /** Removes the score settings balloon from the screen. The hint stays for the next automatic dialog. */
    private fun hideScoreSettingsHint() {
        scoreSettingsBalloon?.hideBalloon()
        scoreSettingsBalloon = null
    }

    /** Opens the score settings: player names and colors, the match format, and manual scoring. */
    private fun openScoreSettings(): Unit = uiSafe {
        if (projectDir == null) return@uiSafe
        // Set the flag first, so that a second activation does not open the dialog again
        scoreSettingsReviewed = true
        val current = ScoreSettings(
            player1Name = player1Name,
            player2Name = player2Name,
            player1ColorHex = player1ColorHex,
            player2ColorHex = player2ColorHex,
            rules = rules,
        )
        val result = scoreSettingsEditor.edit(this, current)
        if (result != null) {
            player1Name = result.player1Name
            player2Name = result.player2Name
            player1ColorHex = result.player1ColorHex
            player2ColorHex = result.player2ColorHex
            rules = result.rules.normalized()
            scorePanel.setPlayers(players())
            refreshScoring()
            refreshVideoScoreboardOverlay()
        }
        saveNow()
        focusPlayer()
    }

    /**
     * Opens the scoreboard style. The video shows each change at once; Cancel restores the saved style.
     * A saved style also becomes the user's default style for new projects.
     */
    private fun openScoreboardStyle(): Unit = uiSafe {
        val sample = lastScoreboardDisplay ?: ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1L,
                text = "",
                p1Name = displayNameP1(),
                p2Name = displayNameP2(),
                p1ColorHex = player1ColorHex,
                p2ColorHex = player2ColorHex,
                p1Pts = 3,
                p2Pts = 1,
                gamesP1 = 4,
                gamesP2 = 3,
                completedSets = listOf(6 to 4),
            ),
        )
        // The preview of the window shows the video frame at the playhead, with the color and crop of the project.
        val frameRequest = pendingMediaFile?.let { VideoFrameRequest(it.absolutePath, player.currentTimeMs(), adjustments.get()) }
        val loadFrame: ((BufferedImage?) -> Unit) -> Unit = { onLoaded ->
            if (frameRequest != null) frameLoader.load(frameRequest, onLoaded)
        }
        val result = try {
            ScoreboardSettingsDialog.show(this, scoreboardSettings, sample, loadFrame) { preview ->
                scoreboardPreviewSettings = preview
                refreshVideoScoreboardOverlay()
            }
        } finally {
            scoreboardPreviewSettings = null
        }
        if (result != null) {
            scoreboardSettings = result
            saveNow()
            try {
                styleDefaults.save(result)
            } catch (_: Throwable) {
                // The default style is a convenience; the project keeps its own style.
            }
        }
        refreshVideoScoreboardOverlay()
        focusPlayer()
    }

    private fun refreshVideoScoreboardOverlay() {
        if (points.isEmpty() || selectedPointIndex !in points.indices) {
            player.setPreviewOverlay(null)
            return
        }

        try {
            val spans = ScoreboardTimelineBuilder.buildSourcePointSpans(
                points = points,
                outcomes = outcomesByPointId,
                player1Name = displayNameP1(),
                player2Name = displayNameP2(),
                player1ColorHex = player1ColorHex,
                player2ColorHex = player2ColorHex,
                rules = rules,
                manualMarks = manualMarks,
                serverMarks = LinkedHashMap(serverMarks),
            )
            val span = spans.getOrNull(selectedPointIndex)
            if (span == null) {
                player.setPreviewOverlay(null)
                return
            }
            val display = ScoreboardComponent.display(span)
            lastScoreboardDisplay = display
            val settings = scoreboardPreviewSettings ?: scoreboardSettings
            val scene = ScoreboardLayouts.scene(display, settings)
            player.setPreviewOverlay(VideoOverlay { area ->
                val placement = ScoreboardAss.place(scene, settings, area.x, area.y, area.width, area.height)
                ScoreboardAss.events(scene, placement)
            })
        } catch (_: Throwable) {
            // Preview overlay is best-effort; scoring/export data remains authoritative.
        }
    }

    private companion object {
        /** The width of the side column in the design (380 px). */
        const val SIDE_COLUMN_WIDTH = 380
    }
}
