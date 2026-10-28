package org.litvin.ui.tabs.points

import org.litvin.GeometryViewportPanel
import org.litvin.analytics.Analytics
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.DisabledAnalytics
import org.litvin.SessionSettings
import org.litvin.projects.ManifestIO
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.points.CommentPosition
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.points.Replays
import org.litvin.points.components.CommentDispatcher
import org.litvin.points.components.CommentPatch as DispatcherCommentPatch
import org.litvin.points.components.CommentState
import org.litvin.points.components.PointsDispatcher
import org.litvin.scoring.Outcome
import org.litvin.export.comments.CommentAss
import org.litvin.export.comments.CommentPreview
import org.litvin.media.PlayerStatus
import org.litvin.media.VideoOverlay
import org.litvin.media.relativeSeekDeltaMs
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import org.litvin.media.SwingMediaPlayer
import org.litvin.ui.commons.CommentDialog
import org.litvin.ui.commons.CommentInput
import org.litvin.ui.commons.CommentLook
import org.litvin.ui.commons.CommentStyleDefaults
import org.litvin.ui.commons.HintBalloon
import org.litvin.ui.commons.HintController
import org.litvin.ui.commons.HintId
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.VideoFrameLoader
import org.litvin.ui.commons.VideoFrameRequest
import org.litvin.ui.tabs.points.ui.SwingTimelineComponent
import org.litvin.ui.tabs.points.components.Keybindings
import org.litvin.ui.tabs.points.components.PointsKeyActions
import org.litvin.ui.tabs.points.ui.EventsHeader
import org.litvin.ui.tabs.points.ui.PointStartButton
import org.litvin.ui.tabs.points.ui.PlaybackBar
import org.litvin.ui.tabs.points.ui.PointsTableView
import java.awt.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import kotlin.math.abs
import kotlin.math.max
import org.litvin.projects.ProjectVideoLanguage
import org.litvin.i18n.tr

/**
 * Swing Points editor panel. The layout comes from design/points-redesign/final.html:
 * - the video with the playback bar under it on the left,
 * - the side column on the right: the "Mark a point" panel, the counts and the points table,
 * - the timeline with its label column along the full width at the bottom.
 *
 * Behavior:
 * - EDL interactions via PointsDispatcher: Start(C)/End(V) auto-create, delete, edit in a dialog
 * - Keyboard mappings: Space, C, V, A, Delete, Left/Right, Shift+Left/Right, and Up/Down for the speed
 * - Autosave of the EDL (edl.json) with a 300 ms debounce
 */
class SwingPointsPanel(
    private val player: SwingMediaPlayer,
    private val adjustments: AdjustmentsSession,
    autosaveExecutor: ExecutorService,
    private val dialogs: UserDialogService,
    private val hints: HintController = HintController.NONE,
    private val analytics: Analytics = DisabledAnalytics,
    private val commentStyles: CommentStyleDefaults = CommentStyleDefaults.inMemory(),
    // The scoreboard style defaults and the app credit request of the scoring.
    styleDefaults: ScoreboardStyleDefaults = ScoreboardStyleDefaults.NONE,
    appCreditRequest: AppCreditRequest = AppCreditRequest.NONE,
    // Creates the player of the replay editor (B-50). The editor closes it.
    private val replayPlayers: () -> SwingMediaPlayer = { MpvSwingMediaPlayerAdapter() },
) : JPanel(BorderLayout()), AutoCloseable {

    constructor() : this(
        MpvSwingMediaPlayerAdapter(),
        AdjustmentsStore.legacySession(),
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "points-autosave") },
        SwingUserDialogService(),
    )

    // Geometry viewport wrapper for the video component
    private var geometryViewport: GeometryViewportPanel

    // Media
    private val closed = AtomicBoolean(false)
    private var unsubscribeAdjustments: (() -> Unit)? = null

    // Deferred media loading
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false
    private var loadErrorShown: Boolean = false

    // EDL Dispatcher
    private val dispatcher = PointsDispatcher()
    private val commentDispatcher = CommentDispatcher()

    // The comments on the video at the playhead
    private val commentPreview = CommentPreview()

    // Plays the replays of the points after their ends (B-50)
    private val replays = ReplayDirector()
    private var replayShown = false

    /** Ends the still frame of a replay (decision 22) after its time. */
    private val replayHold = javax.swing.Timer(0) { endReplayHold() }.apply { isRepeats = false }

    // The comments of the replay that plays now (decision 24). During a still frame, the ticker shows their changes.
    private val replayCommentClock = ReplayCommentClock()
    private var replayCommentsShown: List<CommentV1> = emptyList()
    private var pointCommentsHidden = false
    private val replayCommentTicker = javax.swing.Timer(REPLAY_COMMENT_TICK_MS) {
        if (!replays.isHolding) (it.source as javax.swing.Timer).stop()
        if (updateReplayComments(player.currentTimeMs())) refreshOverlay()
    }

    /** The replay badge text over the video, or null when the video shows no badge. */
    private var badgeShown: String? = null

    // The open replay editor, or null. While it is open, it uses the video, and this player shows a note.
    private var replayEditor: org.litvin.ui.tabs.points.replay.ReplayEditorWindow? = null
    private val videoCards = java.awt.CardLayout()
    private val videoArea = JPanel(videoCards)

    // The comment that the comment dialog shows on the video before Save, or null when no dialog is open.
    private var dialogComment: CommentV1? = null

    // Drags a comment on the video (B-51)
    private val commentDrag = CommentDragController(
        player = player,
        enabled = { isMediaLoaded },
        shownComments = { visiblePointComments() },
        onPreview = { refreshOverlay() },
        onChanged = { id, position, sizePercent, rotationDeg -> placeComment(id, position, sizePercent, rotationDeg) },
        onClicked = { id -> scrollToEvent("comment:$id") },
    )

    // Current project manifest path (projectDir derived from it)
    private var manifestPath: String? = null
    private var projectDir: String? = null

    // UI controls
    private var playbackBar: PlaybackBar
    private var markPanel: PointStartButton
    private val eventsHeader = EventsHeader()

    // The playback speed of this tab: an index into SessionSettings.speedPresets.
    private var speedIndex = SessionSettings.speedPresets.indexOfFirst { it == 1.0f }

    // The points table of the side column
    private var cardsView: PointsTableView
    private var selectedVisualIndex: Int = -1 // visual index within composed list (pending at 0 when present)
    private var reloadingProjectFromDisk: Boolean = false

    // Consolidated keybindings helper
    private var keybindings: Keybindings? = null

    // The width of the side column
    private val SIDE_COLUMN_WIDTH = 360
    private val POINT_BAR_GAP = 16

    // Idle UI refresher to keep time label and timeline handle in sync when paused/seeking
    private val idleUiTimer = Timer(100) { _ ->
        if (player.status() != PlayerStatus.PLAYING) {
            refreshUiAtCurrentTime()
        }
    }.apply { isRepeats = true }

    // The scoring of the points: the point controls, the score file, and the scoreboard over the video.
    private val scoring: PointsScoring = PointsScoring(
        onStep = { delta -> goToNeighborPoint(delta) },
        onToggleFavorite = { id -> toggleFavorite(id) },
        onScoreChanged = {
            cardsView.repaint()
            timeline.repaint()
        },
        onError = { message -> dialogs.showError(this, message, tr("points.error.scoring")) },
        onOverlayChanged = { refreshOverlay() },
        onOutcomeRequest = { outcome -> endPointWithWinner(outcome) },
        loadFrame = { onLoaded ->
            pendingMediaFile?.let { file ->
                frameLoader.load(VideoFrameRequest(file.absolutePath, player.currentTimeMs(), adjustments.get()), onLoaded)
            }
        },
        hints = hints,
        analytics = analytics,
        styleDefaults = styleDefaults,
        appCreditRequest = appCreditRequest,
        isActive = { active },
    )
    private val frameLoader = VideoFrameLoader()

    // True while the tab is open
    private var active = false

    // Lifecycle hooks controlled by navigation
    fun onActivated() {
        active = true
        playbackBar.frameStep.on = SessionSettings.frameStepWhenPaused
        refreshPointsFromProject()
        scoring.load(projectDir, ProjectVideoLanguage.read(manifestPath))
        scoring.onActivated()
        ensurePlayerLoaded()
        player.activatePreview("points activated")
        player.pause()

        // Do not auto-play; optionally restore focus to player area
        EventQueue.invokeLater {
            player.component.requestFocusInWindow()
        }
    }

    /**
     * Selects the point with [pointId], brings its row into view and seeks to its start,
     * for example after "Open point" in the Stats tab.
     * Call it after [onActivated], because the activation reads the points again.
     */
    fun selectPoint(pointId: String) {
        val index = buildEvents().indexOfFirst { it is PointEventDto && it.point.id == pointId }
        if (index < 0) return
        setSelectedVisualAndScroll(index)
        jumpToSelected()
    }

    fun onDeactivated() {
        active = false
        replayEditor?.cancel()
        hints.hide(HintId.POINT_ROW)
        scoring.onDeactivated()
        saveNow()
        player.pause()
        applyReplay(replays.stop())
        player.deactivatePreview("points deactivated")
    }

    private fun ensurePlayerLoaded() {
        try {
            if (isMediaLoaded) return
            val f = pendingMediaFile ?: return
            val wnd = SwingUtilities.getWindowAncestor(player.component)

            if (!player.component.isDisplayable || wnd == null || !wnd.isShowing) return
            player.load(f)
            player.pause()
            isMediaLoaded = true
            applySpeed()
            // Re-apply current adjustments after media is loaded so the player picks them up
            try {
                val current = adjustments.get()
                player.applyPreviewAdjustments(current)
                geometryViewport.refreshGeometry()
            } catch (_: Throwable) { /* ignore */ }
        } catch (t: Throwable) {
            if (!loadErrorShown) {
                loadErrorShown = true
                dialogs.showError(this, t.message ?: t.toString(), tr("points.error.load"))

            }
        }
    }

    // Build a view snapshot for leaf components
    private fun buildViewState(): PointsViewState {
        val playing = player.status() == PlayerStatus.PLAYING
        val time = player.currentTimeMs()
        val pending = dispatcher.getPendingStart()?.toLong()
        val events = buildEvents()
        val autos = AutosaveState(pending = autosave.isPending(), lastSavedAtMs = autosave.lastSavedAtMs)

        val sel = selectedVisualIndex.takeIf { it >= 0 }
        return PointsViewState(
            isPlaying = playing,
            currentTimeMs = time,
            selectedVisualIndex = sel,
            pendingDraftStartMs = pending,
            events = events,
            autosave = autos,
        )
    }

    private fun buildEvents(): List<TimelineEventDto> {
        val pointEvents = dispatcher.getCompletedPoints().map { point ->
            PointEventDto(PointDto(
                id = point.id,
                startMs = point.startMs.toLong(),
                endMs = point.endMs.toLong(),
                label = point.label,
                flags = emptySet(),
                favorite = point.favorite,
                replays = point.replays.mapIndexed { index, replay ->
                    ReplayRowDto(
                        index = index,
                        speed = replay.speed,
                        zoomFactor = replay.zoom?.let { 1.0 / it.width },
                        muted = replay.muted,
                        freezeMs = replay.freeze?.durationMs?.toLong(),
                        commentCount = replay.comments.size,
                        addedMs = Replays.addedMs(replay),
                    )
                },
            ))
        }
        val comments = commentDispatcher.state().comments.map { comment ->
            CommentDto(
                id = comment.id,
                startMs = comment.startMs.toLong(),
                durationMs = comment.durationMs.toLong(),
                text = comment.text,
                colorHex = comment.colorHex,
                style = comment.style,
                sizePercent = comment.sizePercent,
                opacityPercent = comment.opacityPercent,
                rotationDeg = comment.rotationDeg,
            )
        }
        return (pointEvents + comments).sortedWith(compareBy<TimelineEventDto> { it.startMs }.thenBy { it.stableKey })
    }

    private fun pushCardsState() {
        cardsView.setState(buildViewState())
    }

    private val timeline: SwingTimelineComponent = SwingTimelineComponent(
        timeProvider = { player.currentTimeMs() },
        durationProvider = { player.totalDurationMs() },
        pointsProvider = { dispatcher.getCompletedPoints() },
        onSeekRequested = { t ->
            player.seek(t)
            // Immediate UI refresh so the handle moves even when paused
            EventQueue.invokeLater {
                refreshUiAtCurrentTime()
                // If user clicked on a point interval on the timeline, select and scroll to it

                val point = dispatcher.getCompletedPoints().firstOrNull { it.startMs <= t && t < it.endMs }
                if (point != null) {
                    selectEventAndScroll("point:${point.id}")
                }

                player.component.requestFocusInWindow()

            }
        },
        commentsProvider = { commentDispatcher.state().comments },
        onCommentSelected = { id -> EventQueue.invokeLater { scrollToEvent("comment:$id") } },
        onScrubRequested = { t -> player.scrub(t); updateTimeUI(t) },
        selectedPointIdProvider = { (selectedEvent() as? PointEventDto)?.point?.id },
        pendingStartProvider = { dispatcher.getPendingStart()?.toLong() },
        winnerColorProvider = { id -> scoring.winnerColor(id) },
        replayPlayheadProvider = { replays.activePlay?.playAt(player.currentTimeMs()) },
        onReplaySelected = { id, index -> EventQueue.invokeLater { openReplayEditor(pointId = id, replayIndex = index) } },
    ).apply { name = "points-seek" }

    // Autosave controller (debounced, off-EDT persistence). The snapshot is taken on the EDT,
    // so the save thread does not read the dispatchers, and the project folder is that of the snapshot.
    private val autosave = AutosaveController(
        debounceMs = 300, executor = autosaveExecutor,
        snapshot = {
            projectDir?.let { dir ->
                val comments = commentDispatcher.state()
                EdlSnapshot(
                    dir,
                    EdlV1(
                        points = dispatcher.getCompletedPoints(),
                        comments = comments.comments,
                        commentDefaults = comments.defaults,
                        nextCommentId = comments.nextCommentId,
                        version = 1,
                    ),
                )
            }
        },
        saver = { snapshot ->
            try {
                EdlIO.writeForProjectDir(snapshot.projectDir, snapshot.edl)
            } catch (t: Throwable) {
                // Surface error on EDT
                EventQueue.invokeLater { dialogs.showError(this, t.message ?: t.toString(), tr("points.error.autosave")) }
            }
        })


    /** The actions of the leaf components: the points table and the dialogs. */
    private val panelActions: PointsActions = object : PointsActions {
        override fun togglePlayPause() = this@SwingPointsPanel.togglePlayPause()
        override fun seekTo(ms: Long) {
            player.seek(ms)
            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
        }
        override fun jumpToSelected() = this@SwingPointsPanel.jumpToSelected()
        override fun setStartAtPlayhead() = this@SwingPointsPanel.onStartAtPlayhead()
        override fun setEndAtPlayhead() = this@SwingPointsPanel.onEndAtPlayhead()
        override fun createPointAt(ms: Long) = dispatcher.onPointStart(ms)
        override fun editPoint(id: String, patch: PointPatch) = this@SwingPointsPanel.editPoint(id, patch)
        override fun deletePoint(id: String) = this@SwingPointsPanel.deletePoint(id)
        override fun toggleFavorite(id: String) = this@SwingPointsPanel.toggleFavorite(id)
        override fun selectByVisualIndex(index: Int) = this@SwingPointsPanel.setSelectedVisualAndScroll(index)
        override fun saveNow() = this@SwingPointsPanel.saveNow()
        override fun addCommentAtPlayhead() = this@SwingPointsPanel.addCommentAtPlayhead()
        override fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) {
            val look = commentStyles.load()
            this@SwingPointsPanel.createComment(startMs, durationMs, text, colorHex, look.style, look.sizePercent, look.opacityPercent)
        }
        override fun editComment(id: Int, patch: CommentPatch) = this@SwingPointsPanel.editComment(id, patch)
        override fun previewComment(id: Int?, comment: CommentInput?) = this@SwingPointsPanel.previewComment(id, comment)
        override fun deleteComment(id: Int) = this@SwingPointsPanel.deleteComment(id)
        override fun openReplays(id: String, replayIndex: Int?) = openReplayEditor(pointId = id, replayIndex = replayIndex)
        override fun deleteReplay(id: String, replayIndex: Int) = this@SwingPointsPanel.deleteReplay(id, replayIndex)
        override fun moveReplay(id: String, replayIndex: Int, delta: Int) = this@SwingPointsPanel.moveReplay(id, replayIndex, delta)
    }

    override fun addNotify() {
        super.addNotify()
        // Keybindings are installed in init via helper; no global KeyEventDispatcher needed anymore
        SwingUtilities.invokeLater { ensurePlayerLoaded() }
    }

    override fun removeNotify() {
        // Uninstall keybindings
        keybindings?.uninstall()
        super.removeNotify()
    }

    init {
        isOpaque = true
        background = Palette.PANEL

        playbackBar = PlaybackBar(
            onTogglePlay = { togglePlayPause() },
            onNudge = { delta -> nudge(delta) },
            onAddComment = { addCommentAtPlayhead() },
            onSpeedIndex = { index -> setSpeedIndex(index); player.component.requestFocusInWindow() },
            onToggleFrameStep = { toggleFrameStep() },
            onReplay = { openReplayEditor(addNew = true) },
        )
        playbackBar.speed.setSpeedIndex(speedIndex)
        markPanel = PointStartButton(onStart = { onStartAtPlayhead() })

        // Left column: the video with the playback bar under it
        val leftColumn = JPanel(BorderLayout())
        leftColumn.isOpaque = true
        leftColumn.background = Palette.VIDEO_BG
        // Wrap the video component with the geometry viewport for live zoom/pan (Task 5.4)
        geometryViewport = GeometryViewportPanel(player.component)
        geometryViewport.name = "points-video"
        // A left click on the video plays or pauses it. A click on a comment selects the comment instead.
        player.component.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && isMediaLoaded && !commentDrag.pressedOnComment) togglePlayPause()
            }
        })
        videoArea.add(geometryViewport, VIDEO_CARD)
        videoArea.add(JPanel(java.awt.GridBagLayout()).apply {
            name = "points-replay-busy"
            background = Palette.VIDEO_BG
            add(javax.swing.JLabel(tr("replay.editor.busy")).apply {
                font = org.litvin.ui.commons.UiKit.font(14f)
                foreground = Palette.FG_2
            })
        }, BUSY_CARD)
        leftColumn.add(videoArea, BorderLayout.CENTER)
        leftColumn.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(buildPointBar(), BorderLayout.NORTH)
            add(playbackBar, BorderLayout.CENTER)
        }, BorderLayout.SOUTH)
        leftColumn.minimumSize = Dimension(320, 0)
        // Subscribe to central adjustments store to live-apply color and geometry
        unsubscribeAdjustments = adjustments.subscribe { adj ->
            player.applyPreviewAdjustments(adj)
            geometryViewport.refreshGeometry()
        }
        // Apply current state immediately
        val currentAdjustments = adjustments.get()
        player.applyPreviewAdjustments(currentAdjustments)
        geometryViewport.refreshGeometry()

        cardsView = PointsTableView(panelActions)
        cardsView.winnerColor = scoring::winnerColor
        cardsView.pointMarks = scoring::pointMarks
        add(buildMainArea(leftColumn, buildSideColumn()), BorderLayout.CENTER)
        // Push initial state to views
        pushCardsState()

        // Timeline spans full width at the bottom
        add(timeline, BorderLayout.SOUTH)

        // Start idle UI refresher
        idleUiTimer.start()

        // Player callbacks → update UI and repaint timeline on EDT
        player.onTimeChanged = { t ->
            EventQueue.invokeLater {
                applyReplay(replays.onTime(t, player.status() == PlayerStatus.PLAYING))
                updateTimeUI(t)
                timeline.repaint()
                autoActivatePoint(t)
            }
        }
        player.onPlaybackRestart = { EventQueue.invokeLater { replays.onRestart() } }
        player.onReady =
            { EventQueue.invokeLater { updateTimeUI(player.currentTimeMs()); timeline.repaint(); updatePlayPauseButton() } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseButton(); refreshUiAtCurrentTime() } }

        // Dispatcher callback to refresh UI for all leaf components
        dispatcher.onPointsChanged = ::onPointsChanged
        commentDispatcher.onCommentsChanged = ::onPointsChanged

        // Consolidated keybindings helper
        keybindings = Keybindings(this, { isTextEditingFocus() }, object : PointsKeyActions {
            override fun toggle() {
                togglePlayPause()
            }

            override fun startAtPlayhead() {
                onStartAtPlayhead()
            }

            override fun endAtPlayhead() {
                onEndAtPlayhead()
            }

            override fun deleteSelected() {
                this@SwingPointsPanel.deleteSelected()
            }

            override fun nudge(deltaMs: Long) {
                // With Frame step on, Left and Right step one frame while paused.
                if (abs(deltaMs) == 1_000L && SessionSettings.frameStepWhenPaused && player.status() != PlayerStatus.PLAYING) {
                    if (deltaMs > 0) player.stepFrameForward() else player.stepFrameBackward()
                    EventQueue.invokeLater { refreshUiAtCurrentTime() }
                    return
                }
                playbackBar.flashSeek(deltaMs)
                this@SwingPointsPanel.nudge(deltaMs)
            }

            override fun toggleFavoriteSelected() {
                toggleFavoriteSelectedPoint()
            }

            // Presets go from high to low, so Up goes to a lower index.
            override fun speedUp() {
                if (isPlayerAreaFocus()) changeSpeedBy(-1)
            }

            override fun speedDown() {
                if (isPlayerAreaFocus()) changeSpeedBy(1)
            }
        })

        scoring.installKeys(this) { isTextEditingFocus() }
        // F turns Frame step on and off.
        listOf(WHEN_IN_FOCUSED_WINDOW, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .forEach { getInputMap(it).put(KeyStroke.getKeyStroke(AppShortcuts.TOGGLE_FRAME_STEP.keyStroke), "points.toggleFrameStep") }
        actionMap.put("points.toggleFrameStep", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (!isTextEditingFocus()) toggleFrameStep()
            }
        })
        // Enter starts a point, as C does.
        listOf(WHEN_IN_FOCUSED_WINDOW, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .forEach { getInputMap(it).put(KeyStroke.getKeyStroke("ENTER"), "points.pointStartEnter") }
        actionMap.put("points.pointStartEnter", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (!isTextEditingFocus()) onStartAtPlayhead()
            }
        })

        rebuildCards()
    }

    /** The video column on the left and the side column on the right, with a fixed width. */
    private fun buildMainArea(leftColumn: JComponent, side: JComponent): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(leftColumn, BorderLayout.CENTER)
        add(side, BorderLayout.EAST)
    }

    /**
     * The point bar under the video: the serve button, Point start, and the outcome buttons. A narrow bar shows the
     * compact Point start, so that the outcome buttons have space for the names (B-11 E4-T8).
     */
    private fun buildPointBar(): JComponent = object : JPanel(BorderLayout(POINT_BAR_GAP, 0)) {
        override fun doLayout() {
            val inner = width - insets.left - insets.right
            val fullStart = scoring.controls.serveButton.preferredSize.width + POINT_BAR_GAP + markPanel.fullWidth() + POINT_BAR_GAP
            markPanel.compact = inner - fullStart < scoring.controls.fullNamesWidth()
            super.doLayout()
        }
    }.apply {
        name = "points-point-bar"
        isOpaque = true
        background = Palette.OVERLAY
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(10, 16, 10, 16),
        )
        add(JPanel(BorderLayout(POINT_BAR_GAP, 0)).apply {
            isOpaque = false
            add(scoring.controls.serveButton, BorderLayout.WEST)
            add(markPanel, BorderLayout.CENTER)
        }, BorderLayout.WEST)
        add(scoring.controls, BorderLayout.CENTER)
    }

    /**
     * The side column: the current point with Previous and Next, the "Points & events" header, the points table,
     * and the Scoring settings and Scoreboard style buttons under the table.
     */
    private fun buildSideColumn(): JComponent {
        val top = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(scoring.controls.navigator)
            add(eventsHeader)
        }
        return JPanel(BorderLayout()).apply {
            name = "points-side-column"
            isOpaque = true
            background = Palette.BG
            border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)
            preferredSize = Dimension(SIDE_COLUMN_WIDTH, 0)
            minimumSize = Dimension(SIDE_COLUMN_WIDTH, 0)
            add(top, BorderLayout.NORTH)
            add(cardsView, BorderLayout.CENTER)
            add(scoring.settingsButtons, BorderLayout.SOUTH)
        }
    }

    private fun nudge(deltaMs: Long) {
        val playing = player.status() == PlayerStatus.PLAYING
        val newTime = max(0L, player.currentTimeMs() + relativeSeekDeltaMs(deltaMs, playing))
        player.seek(newTime)
        EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
    }

    fun setProjectManifest(path: String) {
        // Write the pending changes to the current project before the project folder changes.
        autosave.flush()
        manifestPath = path
        projectDir = File(path).parentFile.absolutePath
        // Load adjustments for this project into the central store
        adjustments.load(projectDir!!)

        // Load manifest and media
        try {
            val manifest = ManifestIO.read(path)
            val src = manifest.sourceVideo
            if (src.isNullOrBlank() || !File(src).exists()) {
                dialogs.showError(
                    this,
                    tr("points.error.videoMissing", "name" to manifest.name),
                    tr("points.error.videoMissing.title"),
                )
                return
            }
            // Load EDL if present
            loadEdl(EdlIO.readForProjectDir(projectDir!!))
            scoring.load(projectDir, ProjectVideoLanguage.read(manifestPath))
            // Defer the media load until the component becomes displayable
            pendingMediaFile = File(src)
            isMediaLoaded = false
            loadErrorShown = false
            ensurePlayerLoaded()
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), tr("points.error.load"))
        }
    }


    // Build cards list based on dispatcher state (pending + completed)
    private fun rebuildCards() {
        // Delegated to leaf components now
        updateCountBadge(dispatcher.getCompletedPoints())
        pushCardsState()
    }

    private fun updateCountBadge(points: List<PointV1>) {
        scoring.setPoints(points)
        eventsHeader.setCounts(
            marked = points.size,
            favorites = points.count { it.favorite },
            comments = commentDispatcher.state().comments.size,
        )
        showPendingStart()
        if (active) {
            // The rows are built again after this call, so the hint waits for the new rows.
            if (points.isNotEmpty()) EventQueue.invokeLater(::showPointRowHint)
        }
    }

    /** After the first point: a click on a row goes to the point start, and the actions of a row show only on hover. */
    private fun showPointRowHint() {
        if (!active) return
        val row = cardsView.firstPointRowInView() ?: return
        hints.show(HintId.POINT_ROW, row, POINT_ROW_HINT, HintBalloon.Placement.LEFT) {
            player.component.requestFocusInWindow()
        }
    }

    private fun refreshPointsFromProject() {
        val dir = projectDir ?: return
        val selectedKey = selectedEvent()?.stableKey
        val wasPendingSelected = dispatcher.getPendingStart() != null && selectedVisualIndex == buildEvents().size
        try {
            loadEdl(EdlIO.readForProjectDir(dir))
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), tr("points.error.refresh"))
            return
        }

        val refreshedEvents = buildEvents()
        val restoredIndex = selectedKey?.let { key -> refreshedEvents.indexOfFirst { it.stableKey == key } } ?: -1
        when {
            restoredIndex >= 0 -> setSelectedVisualAndScroll(restoredIndex)
            wasPendingSelected && dispatcher.getPendingStart() != null -> setSelectedVisualAndScroll(refreshedEvents.size)
            selectedVisualIndex in refreshedEvents.indices -> setSelectedVisual(selectedVisualIndex)
            else -> setSelectedVisual(-1)
        }
        updateCountBadge(dispatcher.getCompletedPoints())
        pushCardsState()
        timeline.revalidate()
        timeline.repaint()
    }


    private fun setSelectedVisual(visualIndex: Int) {
        if (selectedVisualIndex != visualIndex) timeline.repaint()
        selectedVisualIndex = visualIndex
        cardsView.updateSelection(visualIndex)
        scoring.select((selectedEvent() as? PointEventDto)?.point?.id)
    }

    private fun setSelectedVisualAndScroll(visualIndex: Int) {
        val changed = visualIndex != selectedVisualIndex
        setSelectedVisual(visualIndex)
//        if (!changed) return
        // Defer scroll to ensure any pending layout updates don’t reset viewport
//        try {
//            EventQueue.invokeLater { scrollCardIntoView(visualIndex) }
//        } catch (_: Throwable) {
            scrollCardIntoView(visualIndex)
//        }
    }

    private fun scrollCardIntoView(visualIndex: Int) {
        cardsView.scrollToVisualIndex(visualIndex)
    }

    private fun selectedEvent(): TimelineEventDto? = buildEvents().getOrNull(selectedVisualIndex)

    private fun selectEventAndScroll(stableKey: String) {
        val index = buildEvents().indexOfFirst { it.stableKey == stableKey }
        if (index >= 0) setSelectedVisualAndScroll(index)
    }

    /** Brings an event card into view without giving it an active state. */
    private fun scrollToEvent(stableKey: String) {
        val index = buildEvents().indexOfFirst { it.stableKey == stableKey }
        if (index >= 0) scrollCardIntoView(index)
    }

    private fun loadEdl(edl: EdlV1) {
        reloadingProjectFromDisk = true
        try {
            dispatcher.setPoints(edl.points)
            commentDispatcher.load(CommentState(edl.comments, edl.commentDefaults, edl.nextCommentId))
        } finally {
            reloadingProjectFromDisk = false
        }
    }

    private fun onPointsChanged() {
        val skipAutosave = reloadingProjectFromDisk
        EventQueue.invokeLater {
            applyReplay(replays.setPoints(dispatcher.getCompletedPoints()))
            showComments(commentPreview.setComments(commentDispatcher.state().comments))
            updateCountBadge(dispatcher.getCompletedPoints())
            pushCardsState()
            timeline.revalidate()
            timeline.repaint()
            if (!skipAutosave) scheduleAutosave()
        }
    }

    private fun addCommentAtPlayhead() {
        val look = commentStyles.load()
        CommentDialog.showCreate(
            parent = this,
            namePrefix = "points",
            initialStartMs = player.currentTimeMs(),
            defaultColor = commentDispatcher.state().defaults.colorHex,
            defaultStyle = look.style,
            defaultSizePercent = look.sizePercent,
            defaultOpacityPercent = look.opacityPercent,
            onPreview = { previewComment(null, it) },
        ) { comment ->
            createComment(
                comment.startMs, comment.durationMs, comment.text, comment.colorHex, comment.style, comment.sizePercent,
                comment.opacityPercent, comment.rotationDeg,
            )
        }
    }

    private fun createComment(
        startMs: Long,
        durationMs: Long,
        text: String,
        colorHex: String,
        style: CommentStyle,
        sizePercent: Int = CommentV1.DEFAULT_SIZE_PERCENT,
        opacityPercent: Int = 100,
        rotationDeg: Int = 0,
    ) {
        val comment = commentDispatcher.create(
            startMs = startMs.toCommentIntOrNull() ?: return showCommentRangeError(),
            durationMs = durationMs.toCommentIntOrNull() ?: return showCommentRangeError(),
            text = text,
            colorHex = colorHex,
            style = style,
            sizePercent = sizePercent,
            opacityPercent = opacityPercent,
            rotationDeg = rotationDeg,
        )
        maybeShowCommentHint()
        if (comment != null) {
            commentStyles.save(CommentLook(comment.style, comment.sizePercent, comment.opacityPercent))
            analytics.record(AnalyticsEvent.CommentAdded)
        }
        comment?.let { EventQueue.invokeLater { scrollToEvent("comment:${it.id}") } }
    }

    /**
     * Shows [input] on the video while the comment dialog is open. [id] is the changed comment, or null for a new one.
     * The comment shows also when the playhead is outside its duration. A null [input] stops the preview.
     */
    private fun previewComment(id: Int?, input: CommentInput?) {
        if (input == null) {
            // After Save, the saved comments come earlier in the event queue. Stop the preview after them,
            // so that the old comment does not show for a moment.
            EventQueue.invokeLater {
                dialogComment = null
                refreshOverlay()
            }
            return
        }
        val base = id?.let { changed -> commentDispatcher.state().comments.firstOrNull { it.id == changed } }
            ?: CommentV1(id = NEW_COMMENT_PREVIEW_ID, startMs = 0, durationMs = 0, text = "")
        dialogComment = base.copy(
            text = input.text,
            colorHex = input.colorHex,
            style = input.style,
            sizePercent = input.sizePercent,
            opacityPercent = input.opacityPercent,
            rotationDeg = input.rotationDeg,
        )
        refreshOverlay()
    }

    private fun editComment(id: Int, patch: CommentPatch) {
        val updated = commentDispatcher.update(
            id,
            DispatcherCommentPatch(
                startMs = patch.startMs?.toCommentIntOrNull() ?: patch.startMs?.let { return showCommentRangeError() },
                durationMs = patch.durationMs?.toCommentIntOrNull() ?: patch.durationMs?.let { return showCommentRangeError() },
                text = patch.text,
                colorHex = patch.colorHex,
                style = patch.style,
                sizePercent = patch.sizePercent,
                opacityPercent = patch.opacityPercent,
                rotationDeg = patch.rotationDeg,
            ),
        )
        maybeShowCommentHint()
        if (updated) rememberLook(patch.style, patch.sizePercent, patch.opacityPercent)
        if (updated) EventQueue.invokeLater { scrollToEvent("comment:$id") }
    }

    private fun placeComment(id: Int, position: CommentPosition, sizePercent: Int, rotationDeg: Int) {
        val oldSize = commentDispatcher.state().comments.firstOrNull { it.id == id }?.sizePercent
        val updated = commentDispatcher.update(
            id, DispatcherCommentPatch(position = position, sizePercent = sizePercent, rotationDeg = rotationDeg),
        )
        maybeShowCommentHint()
        // A corner drag is a size choice. A move or a rotation is not.
        if (updated && oldSize != sizePercent) rememberLook(sizePercent = sizePercent)
    }

    /** Remembers the parts of the look that the user changed, for the next new comment. Null parts do not change. */
    private fun rememberLook(style: CommentStyle? = null, sizePercent: Int? = null, opacityPercent: Int? = null) {
        if (style == null && sizePercent == null && opacityPercent == null) return
        val look = commentStyles.load()
        commentStyles.save(CommentLook(style ?: look.style, sizePercent ?: look.sizePercent, opacityPercent ?: look.opacityPercent))
    }

    /** Removes one replay of a point. While the replay editor is open, it owns the replays, so the editor comes to the front. */
    private fun deleteReplay(id: String, replayIndex: Int) {
        replayEditor?.let { it.toFront(); return }
        val point = dispatcher.getCompletedPoints().firstOrNull { it.id == id } ?: return
        if (replayIndex !in point.replays.indices) return
        dispatcher.setReplays(id, point.replays.filterIndexed { index, _ -> index != replayIndex })
    }

    /** Moves one replay of a point. The replays of a point play in the order of the list. */
    private fun moveReplay(id: String, replayIndex: Int, delta: Int) {
        replayEditor?.let { it.toFront(); return }
        val point = dispatcher.getCompletedPoints().firstOrNull { it.id == id } ?: return
        val target = replayIndex + delta
        if (replayIndex !in point.replays.indices || target !in point.replays.indices) return
        val list = point.replays.toMutableList()
        list.add(target, list.removeAt(replayIndex))
        dispatcher.setReplays(id, list)
    }

    private fun deleteComment(id: Int) {
        commentDispatcher.delete(id)
        maybeShowCommentHint()
    }

    private fun editPoint(id: String, patch: PointPatch) {
        val existing = dispatcher.getCompletedPoints().firstOrNull { it.id == id } ?: return
        val updated = dispatcher.updatePoint(
            id,
            patch.startMs ?: existing.startMs.toLong(),
            patch.endMs ?: existing.endMs.toLong(),
            patch.label ?: existing.label,
        )
        maybeShowDispatcherHint()
        hints.dismiss(HintId.POINT_ROW)
        if (updated) EventQueue.invokeLater { selectEventAndScroll("point:$id") }
    }

    private fun deletePoint(id: String) {
        hints.dismiss(HintId.POINT_ROW)
        if (dispatcher.deletePoint(id)) {
            analytics.record(AnalyticsEvent.PointDeleted)
            setSelectedVisual(-1)
        }
    }

    /** Deletes the selected point. Comments have no active state, so they are deleted from their card. */
    private fun deleteSelected() {
        (selectedEvent() as? PointEventDto)?.let { deletePoint(it.point.id) }
    }

    private fun Long.toCommentIntOrNull(): Int? = takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    private fun showCommentRangeError() {
        dialogs.showWarning(this, tr("points.error.commentRange"), tr("points.error.commentRange.title"))
    }

    private fun jumpToSelected() {
        val event = selectedEvent() ?: return
        player.seek(event.startMs)
        EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
    }

    private fun toggleFavoriteSelectedPoint() {
        val selected = selectedEvent() as? PointEventDto ?: return
        toggleFavorite(selected.point.id)
    }

    private fun toggleFavorite(id: String) {
        hints.dismiss(HintId.POINT_ROW)
        if (dispatcher.toggleFavorite(id)) {
            if (dispatcher.getCompletedPoints().any { it.id == id && it.favorite }) analytics.record(AnalyticsEvent.PointFavorited)
            scoring.setPoints(dispatcher.getCompletedPoints())
            pushCardsState()
            timeline.repaint()
            scheduleAutosave()
        }
    }

    /**
     * The point of the playhead: the point that contains [t] (half-open interval [start, end)).
     * Outside all points, the closest point within [NEAR_POINT_MS] of [t].
     */
    private fun pointNear(t: Long): PointV1? {
        val points = dispatcher.getCompletedPoints()
        points.firstOrNull { it.startMs <= t && t < it.endMs }?.let { return it }
        fun distance(point: PointV1): Long = if (t < point.startMs) point.startMs - t else t - point.endMs
        return points.filter { distance(it) <= NEAR_POINT_MS }.minByOrNull(::distance)
    }

    private fun autoActivatePoint(t: Long) {
        val hasPending = dispatcher.getPendingStart() != null
        val point = pointNear(t)
        // Keep the selected point while it stays the point of the playhead, to avoid a scroll on every tick.
        if (point != null && (selectedEvent() as? PointEventDto)?.point?.id == point.id) return
        if (point != null) {
            selectEventAndScroll("point:${point.id}")
            return
        }
        // Keep pending selected if present; otherwise clear selection
        if (hasPending) {
            val pendingIndex = buildEvents().size // pending is visually last
            setSelectedVisual(pendingIndex)
        } else {
            setSelectedVisual(-1)
        }
    }

    /** The Frame step setting is in SessionSettings. */
    private fun toggleFrameStep() {
        SessionSettings.frameStepWhenPaused = !SessionSettings.frameStepWhenPaused
        playbackBar.frameStep.on = SessionSettings.frameStepWhenPaused
        player.component.requestFocusInWindow()
    }

    private fun setSpeedIndex(index: Int) {
        speedIndex = SessionSettings.clampIndex(index)
        applySpeed()
    }

    private fun changeSpeedBy(delta: Int) {
        val next = SessionSettings.clampIndex(speedIndex + delta)
        if (next != speedIndex) setSpeedIndex(next)
    }

    /** Gives the speed of this tab to the player and to the speed list. */
    private fun applySpeed() {
        player.setRate(SessionSettings.toRate(speedIndex))
        playbackBar.speed.setSpeedIndex(speedIndex)
    }

    private fun updatePlayPauseButton() {
        playbackBar.setPlaying(player.status() == PlayerStatus.PLAYING)
    }

    private fun updateTimeUI(ms: Long) {
        // During a drag, keyframe seeks report times near the drag position. Show the drag position.
        val shown = max(0, timeline.scrubTimeMs ?: ms)
        playbackBar.setTime(shown, player.totalDurationMs())
        showPendingStart()
        scoring.moveTo(shown)
        val replayChanged = updateReplayComments(ms)
        showComments(commentPreview.moveTo(shown) || replayChanged)
    }

    /**
     * Finds the replay comments on screen at [sourceMs], and whether the point comments hide (decision 24). Returns true
     * when the video must show a change.
     */
    private fun updateReplayComments(sourceMs: Long): Boolean {
        val play = replays.activePlay
        val now = play?.let { replayCommentClock.comments(it.replay, sourceMs, replays.isHolding) }.orEmpty()
        val hidden = play != null && !play.replay.showPointComments
        if (now == replayCommentsShown && hidden == pointCommentsHidden) return false
        replayCommentsShown = now
        pointCommentsHidden = hidden
        return true
    }

    /** The point comments on the video now. A replay with Show the point comments off hides them. */
    private fun visiblePointComments(): List<CommentV1> = if (pointCommentsHidden) emptyList() else commentPreview.shown

    /** Point start shows the start of the pending point, and the outcome buttons end a pending point. */
    private fun showPendingStart() {
        val pendingStart = dispatcher.getPendingStart()?.toLong()
        markPanel.setState(pendingStart)
        scoring.setPending(pendingStart != null)
    }

    /** Draws the comments at the playhead over the video, after [changed] comments on screen. */
    private fun showComments(changed: Boolean) {
        if (!changed) return
        refreshOverlay()
    }

    /** The scoreboard and the comments at the playhead over the video. */
    private fun refreshOverlay() {
        // The replay comments draw over the point comments. Only the point comments can be dragged on this tab.
        val shown = withDialogComment(commentDrag.displayed(visiblePointComments())) + replayCommentsShown
        // The comments draw after the scoreboard, so they are on top of it. A replay hides the scoreboard (B-50).
        val showBoard = !replays.isActive
        val badgeText = activeBadgeText()
        badgeShown = badgeText
        player.setPreviewOverlay(VideoOverlay { area ->
            (if (showBoard) scoring.scoreboardEvents(area) else emptyList()) + CommentAss.previewEvents(shown, area) +
                (badgeText?.let { org.litvin.export.ReplayBadgeAss.events(it, area.width, area.height, area.x, area.y) } ?: emptyList())
        })
        commentDrag.refresh()
    }

    /** The badge text of the replay that plays now, or null when no replay with a badge plays. */
    private fun activeBadgeText(): String? =
        replays.activePlay?.takeIf { it.replay.showBadge }?.let { Replays.badgeText(it.replay, it.replayIndex) }

    /** [comments] with the comment of the open comment dialog in place of its saved version, or added to the end. */
    private fun withDialogComment(comments: List<CommentV1>): List<CommentV1> {
        val changed = dialogComment ?: return comments
        return if (comments.any { it.id == changed.id }) {
            comments.map { if (it.id == changed.id) changed else it }
        } else {
            comments + changed
        }
    }

    private fun refreshUiAtCurrentTime() {
        val t = player.currentTimeMs()
        updateTimeUI(t)
        timeline.repaint()
        autoActivatePoint(t)
    }

    /**
     * Opens the replay editor for the point with [pointId], or for the point at the playhead (B-50 T4). With
     * [addNew], the editor adds a replay at the playhead. Else it selects the replay [replayIndex], or adds one when
     * the point has no replay. While the editor is open, it uses the video, and this player shows a note.
     */
    fun openReplayEditor(pointId: String? = null, replayIndex: Int? = null, addNew: Boolean = false) {
        replayEditor?.let { it.toFront(); return }
        val playhead = player.currentTimeMs()
        val points = dispatcher.getCompletedPoints()
        val point = (pointId?.let { id -> points.firstOrNull { it.id == id } } ?: pointNear(playhead)) ?: run {
            dialogs.showHint(this, tr("points.replay.noPoint"))
            return
        }
        val file = pendingMediaFile ?: return
        player.pause()
        applyReplay(replays.stop())
        player.deactivatePreview("replay editor")
        videoCards.show(videoArea, BUSY_CARD)
        val number = points.sortedBy { it.startMs }.indexOfFirst { it.id == point.id } + 1
        val select = when {
            addNew -> null
            point.replays.isEmpty() -> null
            else -> (replayIndex ?: 0).coerceIn(0, point.replays.lastIndex)
        }
        // A replay from a row or a block starts at the point end when the playhead is not near the point.
        val newAt = if (playhead in Replays.windowStartMs(point)..Replays.windowEndMs(point)) playhead else point.endMs.toLong()
        replayEditor = org.litvin.ui.tabs.points.replay.ReplayEditorWindow(
            owner = SwingUtilities.getWindowAncestor(this),
            player = replayPlayers(),
            videoFile = file,
            adjustments = adjustments.get(),
            point = point,
            pointTitle = tr("replay.editor.pointName", "number" to number),
            playheadMs = newAt,
            selectIndex = select,
            onSave = { list -> dispatcher.setReplays(point.id, list) },
            pointComments = commentDispatcher.state().comments,
            newCommentLook = commentStyles.load().let { look ->
                CommentInput(0, 0, "", commentDispatcher.state().defaults.colorHex, look.style, look.sizePercent, look.opacityPercent)
            },
            onCommentLook = { look -> commentStyles.save(look) },
            onClosed = {
                replayEditor = null
                videoCards.show(videoArea, VIDEO_CARD)
                if (active) {
                    player.activatePreview("replay editor closed")
                    player.seek(playhead)
                    // The busy card hid the video, so the main window gave the focus to another button (the
                    // Projects nav button). Space would press that button. Give the focus back to the video.
                    EventQueue.invokeLater { player.component.requestFocusInWindow() }
                }
                refreshUiAtCurrentTime()
            },
        ).also { it.isVisible = true }
    }

    private fun togglePlayPause() {
        // The replay editor uses the video (B-50 decision 5).
        if (replayEditor != null) return
        // Play during a still frame ends the still frame at once.
        if (replays.isHolding) {
            endReplayHold()
            return
        }
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) player.pause() else player.play()
        updatePlayPauseButton()
    }

    /** Sends the commands of the replay director to the player, and shows when a replay plays. */
    private fun applyReplay(commands: List<ReplayDirector.Command>) {
        for (command in commands) {
            when (command) {
                is ReplayDirector.Command.Seek -> player.seek(command.sourceMs)
                is ReplayDirector.Command.Look -> player.setReplayView(command.view)
                is ReplayDirector.Command.Sound -> player.setReplaySound(command.sound)
                is ReplayDirector.Command.Hold -> {
                    player.pause()
                    player.seek(command.sourceMs)
                    replayHold.initialDelay = command.durationMs.toInt()
                    replayHold.restart()
                    replayCommentClock.holdStarted()
                    if (replays.activePlay?.replay?.comments?.isNotEmpty() == true) replayCommentTicker.restart()
                }
            }
        }
        // The next replay of the same point can have a different badge, so a change of the badge also redraws.
        val active = replays.isActive
        if (active == replayShown && activeBadgeText() == badgeShown) return
        if (active != replayShown) {
            replayShown = active
            playbackBar.setReplay(if (active) tr("replay.clock") else null)
        }
        refreshOverlay()
    }

    /** The still frame of a replay is over: the video plays on. After a seek or a stop, nothing happens. */
    private fun endReplayHold() {
        replayHold.stop()
        replayCommentTicker.stop()
        replayCommentClock.holdEnded()
        if (!replays.endHold()) return
        player.play()
        updatePlayPauseButton()
    }

    private fun onStartAtPlayhead() {
        // The marking keys do not work during a replay (B-50).
        if (replays.isActive) return
        dispatcher.onPointStart(player.currentTimeMs())
        maybeShowDispatcherHint()
        // Refresh cards and select the pending visual row (0)
        EventQueue.invokeLater {
            rebuildCards()
            if (dispatcher.getPendingStart() != null) {
                val pendingIndex = buildEvents().size // pending is visually last
                setSelectedVisual(pendingIndex)
            }
        }
    }

    private fun onEndAtPlayhead() {
        if (replays.isActive) return
        // Capture pending Start before finalization to select the created row afterwards
        val prevPending = dispatcher.getPendingStart()
        if (dispatcher.onPointEnd(player.currentTimeMs())) analytics.record(AnalyticsEvent.PointAdded)
        maybeShowDispatcherHint()
        EventQueue.invokeLater {
            val nowPending = dispatcher.getPendingStart()
            if (prevPending != null && nowPending == null) {
                // A point was likely created; select the row matching prevPending start
                val pts = dispatcher.getCompletedPoints()
                pts.firstOrNull { it.startMs == prevPending }?.let { selectEventAndScroll("point:${it.id}") }
            }
        }
    }

    /**
     * Q, W and E. With a pending point, the key ends the point at the playhead
     * (as V does) and gives the winner to the new point. Without a pending point, the key gives the winner
     * to the active point. Between points, the key does nothing.
     */
    private fun endPointWithWinner(outcome: Outcome) {
        if (replays.isActive) return
        val pendingStart = dispatcher.getPendingStart()
        if (pendingStart == null) {
            (selectedEvent() as? PointEventDto)?.let { scoring.score(outcome, it.point.id) }
            return
        }
        val created = dispatcher.onPointEnd(player.currentTimeMs())
        maybeShowDispatcherHint()
        if (!created) return
        analytics.record(AnalyticsEvent.PointAdded)
        val points = dispatcher.getCompletedPoints()
        val point = points.firstOrNull { it.startMs == pendingStart } ?: return
        // The dispatcher tells the panel about the new point later, so the scoring gets the points now.
        scoring.setPoints(points)
        scoring.score(outcome, point.id)
        EventQueue.invokeLater { selectEventAndScroll("point:${point.id}") }
    }

    /**
     * R (+1) and Shift+R (-1) go to the start of the next or the previous point and play it.
     * Between points, the playhead decides which point is next.
     */
    private fun goToNeighborPoint(delta: Int) {
        val points = dispatcher.getCompletedPoints().sortedBy { it.startMs }
        val from = (selectedEvent() as? PointEventDto)?.point?.startMs ?: player.currentTimeMs()
        val target = if (delta > 0) points.firstOrNull { it.startMs > from } else points.lastOrNull { it.startMs < from }
        target ?: return
        selectPoint(target.id)
        player.play()
        updatePlayPauseButton()
    }

    private fun maybeShowDispatcherHint() {
        val m = dispatcher.consumeUserMessage() ?: return
        dialogs.showHint(this, m)
    }

    private fun maybeShowCommentHint() {
        val message = commentDispatcher.consumeUserMessage() ?: return
        dialogs.showHint(this, message)
    }

    private fun scheduleAutosave() {
        autosave.schedule()
    }

    private fun autosaveNow() {
        // Wait for the write: leaving the tab hands the project files to whichever view opens next.
        autosave.flush()
    }

    private fun isTextEditingFocus(): Boolean {
        val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        return (fo is javax.swing.text.JTextComponent) && fo.isEnabled && fo.isVisible && fo.isEditable
    }

    /** Up and Down change the speed only in the video area. */
    private fun isPlayerAreaFocus(): Boolean {
        val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        val comp = player.component
        return fo === comp || SwingUtilities.isDescendingFrom(fo, comp)
    }

    // Expose manual save for File -> Save All integration
    fun saveNow() {
        autosaveNow()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        replayEditor?.cancel()
        idleUiTimer.stop()
        keybindings?.uninstall()
        keybindings = null
        dispatcher.onPointsChanged = null
        commentDispatcher.onCommentsChanged = null
        commentDrag.dispose()
        replayCommentTicker.stop()
        unsubscribeAdjustments?.invoke()
        unsubscribeAdjustments = null
        autosave.flushAndClose()
        adjustments.flush()
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.onReady = null
        player.onPlaybackRestart = null
        player.setPreviewOverlay(null)
        player.close()
    }

    /** The EDL to write and the project folder that it belongs to. */
    private class EdlSnapshot(val projectDir: String, val edl: EdlV1)

    private companion object {
        const val VIDEO_CARD = "video"
        const val BUSY_CARD = "busy"

        /** A point stays active while the playhead is this close to it. */
        const val NEAR_POINT_MS = 2_000L

        /** The id of a new comment in the dialog preview. Saved comments have positive ids. */
        const val NEW_COMMENT_PREVIEW_ID = -1

        /** How often the video shows the changes of the replay comments during a still frame. */
        const val REPLAY_COMMENT_TICK_MS = 40

        val POINT_ROW_HINT: String get() = tr("points.hint.pointRow")
    }
}
