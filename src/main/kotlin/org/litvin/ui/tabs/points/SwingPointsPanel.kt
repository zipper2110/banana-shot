package org.litvin.ui.tabs.points

import org.litvin.GeometryViewportPanel
import org.litvin.SessionSettings
import org.litvin.projects.ManifestIO
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.points.components.CommentDispatcher
import org.litvin.points.components.CommentPatch as DispatcherCommentPatch
import org.litvin.points.components.CommentState
import org.litvin.points.components.PointsDispatcher
import org.litvin.media.PlayerStatus
import org.litvin.media.relativeSeekDeltaMs
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import org.litvin.media.SwingMediaPlayer
import org.litvin.ui.commons.HintBalloon
import org.litvin.ui.commons.HintController
import org.litvin.ui.commons.HintId
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.tabs.points.ui.SwingTimelineComponent
import org.litvin.ui.tabs.points.ui.EditCommentDialog
import org.litvin.ui.tabs.points.components.Keybindings
import org.litvin.ui.tabs.points.components.PointsKeyActions
import org.litvin.ui.tabs.points.ui.EventsHeader
import org.litvin.ui.tabs.points.ui.MarkPanel
import org.litvin.ui.tabs.points.ui.PlaybackBar
import org.litvin.ui.tabs.points.ui.PointsTableView
import java.awt.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import kotlin.math.max

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

    // Current project manifest path (projectDir derived from it)
    private var manifestPath: String? = null
    private var projectDir: String? = null

    // UI controls
    private var playbackBar: PlaybackBar
    private var markPanel: MarkPanel
    private val eventsHeader = EventsHeader()

    // The playback speed of this tab: an index into SessionSettings.speedPresets. The Scoring tab has its own speed.
    private var speedIndex = SessionSettings.speedPresets.indexOfFirst { it == 1.0f }

    // The points table of the side column
    private var cardsView: PointsTableView
    private var selectedVisualIndex: Int = -1 // visual index within composed list (pending at 0 when present)
    private var reloadingProjectFromDisk: Boolean = false

    // Consolidated keybindings helper
    private var keybindings: Keybindings? = null

    // The width of the side column
    private val SIDE_COLUMN_WIDTH = 360

    // Idle UI refresher to keep time label and timeline handle in sync when paused/seeking
    private val idleUiTimer = Timer(100) { _ ->
        if (player.status() != PlayerStatus.PLAYING) {
            refreshUiAtCurrentTime()
        }
    }.apply { isRepeats = true }

    /** Called on the EDT with the number of marked points, after each change while the tab is active. */
    var onMarkedPointCount: ((Int) -> Unit)? = null

    // True while the tab is open
    private var active = false

    // Lifecycle hooks controlled by navigation
    fun onActivated() {
        active = true
        refreshPointsFromProject()
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
     * for example after "Go to point" in the Scoring tab. Call it after [onActivated], because the activation reads the points again.
     */
    fun selectPoint(pointId: String) {
        val index = buildEvents().indexOfFirst { it is PointEventDto && it.point.id == pointId }
        if (index < 0) return
        setSelectedVisualAndScroll(index)
        jumpToSelected()
    }

    fun onDeactivated() {
        active = false
        hints.hide(HintId.POINT_ROW)
        saveNow()
        player.pause()
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
                dialogs.showError(this, t.message ?: t.toString(), "Failed to load project")

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
            ))
        }
        val comments = commentDispatcher.state().comments.map { comment ->
            CommentDto(
                id = comment.id,
                startMs = comment.startMs.toLong(),
                durationMs = comment.durationMs.toLong(),
                text = comment.text,
                colorHex = comment.colorHex,
            )
        }
        return (pointEvents + comments).sortedWith(compareBy<TimelineEventDto> { it.startMs }.thenBy { it.stableKey })
    }

    private fun pushCardsState() {
        cardsView.setState(buildViewState())
    }

    private val timeline = SwingTimelineComponent(
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
    ).apply { name = "points-seek" }

    // Autosave controller (debounced, off-EDT persistence)
    private val autosave = AutosaveController(
        debounceMs = 300, executor = autosaveExecutor, saver = {
            try {
                val dir = projectDir ?: return@AutosaveController
                val comments = commentDispatcher.state()
                EdlIO.writeForProjectDir(
                    dir,
                    EdlV1(
                        points = dispatcher.getCompletedPoints(),
                        comments = comments.comments,
                        commentDefaults = comments.defaults,
                        nextCommentId = comments.nextCommentId,
                        version = 1,
                    ),
                )
            } catch (t: Throwable) {
                // Surface error on EDT
                EventQueue.invokeLater { dialogs.showError(this, t.message ?: t.toString(), "Autosave failed") }
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
        override fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) =
            this@SwingPointsPanel.createComment(startMs, durationMs, text, colorHex)
        override fun editComment(id: Int, patch: CommentPatch) = this@SwingPointsPanel.editComment(id, patch)
        override fun deleteComment(id: Int) = this@SwingPointsPanel.deleteComment(id)
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
        )
        playbackBar.speed.setSpeedIndex(speedIndex)
        markPanel = MarkPanel(onStart = { onStartAtPlayhead() }, onEnd = { onEndAtPlayhead() })

        // Left column: the video with the playback bar under it
        val leftColumn = JPanel(BorderLayout())
        leftColumn.isOpaque = true
        leftColumn.background = Palette.VIDEO_BG
        // Wrap the video component with the geometry viewport for live zoom/pan (Task 5.4)
        geometryViewport = GeometryViewportPanel(player.component)
        geometryViewport.name = "points-video"
        leftColumn.add(geometryViewport, BorderLayout.CENTER)
        leftColumn.add(playbackBar, BorderLayout.SOUTH)
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
        add(buildMainArea(leftColumn, buildSideColumn()), BorderLayout.CENTER)
        // Push initial state to views
        pushCardsState()

        // Timeline spans full width at the bottom
        add(timeline, BorderLayout.SOUTH)

        // Start idle UI refresher
        idleUiTimer.start()

        // Player callbacks → update UI and repaint timeline on EDT
        player.onTimeChanged =
            { t -> EventQueue.invokeLater { updateTimeUI(t); timeline.repaint(); autoActivatePoint(t) } }
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

        rebuildCards()
    }

    /** The video column on the left and the side column on the right, with a fixed width. */
    private fun buildMainArea(leftColumn: JComponent, side: JComponent): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(leftColumn, BorderLayout.CENTER)
        add(side, BorderLayout.EAST)
    }

    /** The side column: the mark panel, the "Points & events" header, and the points table. */
    private fun buildSideColumn(): JComponent {
        val top = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(12, 12, 0, 12)
                add(markPanel, BorderLayout.CENTER)
            })
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
        }
    }

    private fun nudge(deltaMs: Long) {
        val playing = player.status() == PlayerStatus.PLAYING
        val newTime = max(0L, player.currentTimeMs() + relativeSeekDeltaMs(deltaMs, playing))
        player.seek(newTime)
        EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
    }

    fun setProjectManifest(path: String) {
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
                    "Select source video for project: **${manifest.name}**",
                    "Source video missing",
                )
                return
            }
            // Load EDL if present
            loadEdl(EdlIO.readForProjectDir(projectDir!!))
            // Defer the media load until the component becomes displayable
            pendingMediaFile = File(src)
            isMediaLoaded = false
            loadErrorShown = false
            ensurePlayerLoaded()
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), "Failed to load project")
        }
    }


    // Build cards list based on dispatcher state (pending + completed)
    private fun rebuildCards() {
        // Delegated to leaf components now
        updateCountBadge(dispatcher.getCompletedPoints())
        pushCardsState()
    }

    private fun updateCountBadge(points: List<PointV1>) {
        eventsHeader.setCounts(
            marked = points.size,
            favorites = points.count { it.favorite },
            comments = commentDispatcher.state().comments.size,
        )
        markPanel.setState(dispatcher.getPendingStart()?.toLong(), player.currentTimeMs())
        if (active) {
            onMarkedPointCount?.invoke(points.size)
            // The rows are built again after this call, so the hint waits for the new rows.
            if (points.isNotEmpty()) EventQueue.invokeLater(::showPointRowHint)
        }
    }

    /** After the first point: the actions of a row show only on hover, and A makes the selected point a favorite. */
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
            dialogs.showError(this, t.message ?: t.toString(), "Failed to refresh points and events")
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
            updateCountBadge(dispatcher.getCompletedPoints())
            pushCardsState()
            timeline.revalidate()
            timeline.repaint()
            if (!skipAutosave) scheduleAutosave()
        }
    }

    private fun addCommentAtPlayhead() {
        EditCommentDialog.showCreate(
            parent = this,
            initialStartMs = player.currentTimeMs(),
            defaultColor = commentDispatcher.state().defaults.colorHex,
            actions = panelActions,
        )
    }

    private fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) {
        val comment = commentDispatcher.create(
            startMs = startMs.toCommentIntOrNull() ?: return showCommentRangeError(),
            durationMs = durationMs.toCommentIntOrNull() ?: return showCommentRangeError(),
            text = text,
            colorHex = colorHex,
        )
        maybeShowCommentHint()
        comment?.let { EventQueue.invokeLater { scrollToEvent("comment:${it.id}") } }
    }

    private fun editComment(id: Int, patch: CommentPatch) {
        val updated = commentDispatcher.update(
            id,
            DispatcherCommentPatch(
                startMs = patch.startMs?.toCommentIntOrNull() ?: patch.startMs?.let { return showCommentRangeError() },
                durationMs = patch.durationMs?.toCommentIntOrNull() ?: patch.durationMs?.let { return showCommentRangeError() },
                text = patch.text,
                colorHex = patch.colorHex,
            ),
        )
        maybeShowCommentHint()
        if (updated) EventQueue.invokeLater { scrollToEvent("comment:$id") }
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
        if (dispatcher.deletePoint(id)) setSelectedVisual(-1)
    }

    /** Deletes the selected point. Comments have no active state, so they are deleted from their card. */
    private fun deleteSelected() {
        (selectedEvent() as? PointEventDto)?.let { deletePoint(it.point.id) }
    }

    private fun Long.toCommentIntOrNull(): Int? = takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    private fun showCommentRangeError() {
        dialogs.showWarning(this, "Comment times must fit within the supported video timeline.", "Invalid comment")
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
            pushCardsState()
            timeline.repaint()
            scheduleAutosave()
        }
    }

    private fun autoActivatePoint(t: Long) {
        // Keep the selected point while the playhead stays inside it, to avoid a scroll on every tick.
        (selectedEvent() as? PointEventDto)?.let { selected ->
            val end = selected.point.endMs
            if (end != null && selected.point.startMs <= t && t < end) return
        }

        val hasPending = dispatcher.getPendingStart() != null
        // half-open interval [start, end)
        val point = dispatcher.getCompletedPoints().firstOrNull { it.startMs <= t && t < it.endMs }
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
        markPanel.setState(dispatcher.getPendingStart()?.toLong(), shown)
    }

    private fun refreshUiAtCurrentTime() {
        val t = player.currentTimeMs()
        updateTimeUI(t)
        timeline.repaint()
        autoActivatePoint(t)
    }

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) player.pause() else player.play()
        updatePlayPauseButton()
    }

    private fun onStartAtPlayhead() {
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
        // Capture pending Start before finalization to select the created row afterwards
        val prevPending = dispatcher.getPendingStart()
        dispatcher.onPointEnd(player.currentTimeMs())
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

    /** Up and Down change the speed only in the video area, as in the Scoring tab. */
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
        idleUiTimer.stop()
        keybindings?.uninstall()
        keybindings = null
        dispatcher.onPointsChanged = null
        commentDispatcher.onCommentsChanged = null
        unsubscribeAdjustments?.invoke()
        unsubscribeAdjustments = null
        autosave.flushAndClose()
        adjustments.flush()
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.onReady = null
        player.close()
    }

    private companion object {
        const val POINT_ROW_HINT = "Put the pointer on a row to show Favorite, Edit, and Delete. " +
            "You can mark this point as a favorite. Click this row to go to point start."
    }
}
