package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.ColorTimeline
import org.litvin.adjustments.TrimRange
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.relativeSeekDeltaMs
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.commons.CardColumn
import org.litvin.ui.commons.ChangePointsCard
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SIDE_PANEL_WIDTH
import org.litvin.ui.commons.HeaderTabs
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.VideoPlaybackBar
import org.litvin.ui.tabs.adjustments.presenter.AdjustSection
import org.litvin.ui.tabs.adjustments.presenter.AdjustVideoIntent
import org.litvin.ui.tabs.adjustments.presenter.AdjustVideoPresenter
import org.litvin.ui.tabs.adjustments.presenter.AdjustVideoView
import org.litvin.ui.tabs.adjustments.presenter.AdjustVideoViewEffect
import org.litvin.ui.tabs.adjustments.presenter.AdjustVideoViewState
import org.litvin.ui.tabs.adjustments.presenter.DefaultAdjustVideoPresenter
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import org.litvin.i18n.tr

/**
 * The Colors & Transform tab: a strip of large sub-tabs across the top (Trim, Colors and Transform), the video with
 * the playback bar on the left, and the right panel with the controls of the open sub-tab. Each sub-tab has its own
 * header with "Reset all". The change points card is under the
 * sub-tabs. It shows the change points of the open sub-tab, because each sub-tab has its own change points (B-47).
 * The trim has no change points, so the Trim sub-tab hides the card. On the Trim sub-tab, the [TrimTimeline] under
 * the video replaces the seek bar: it shows the whole source with drag handles for the trim and the points (B-62).
 *
 * On the Colors sub-tab, the video shows the result. While the user holds the left mouse button on the video,
 * the preview shows the original colors. On the Transform sub-tab, the player shows the rotated full frame, and
 * [CropEditorOverlay] draws the crop rectangle and the handles over it.
 * The controls edit the last change point at or before the playhead. The preview shows its values without the blend.
 * Component IDs: adjust-root, adjust-left, adjust-right, adjust-viewport, adjust-transport, adjust-section
 */
class SwingAdjustVideoPanel(
    private val player: SwingMediaPlayer,
    private val presenter: AdjustVideoPresenter = DefaultAdjustVideoPresenter(),
) : JPanel(BorderLayout()), AdjustVideoView, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private var pendingMediaFile: File? = null
    private var loadedMediaFile: File? = null
    private var state = AdjustVideoViewState()

    /** The sub-tab that the right panel shows, or null before the first render. */
    private var shownSection: AdjustSection? = null

    /** The open sub-tab. */
    val section: AdjustSection get() = shownSection ?: state.section

    // True while the user holds the left mouse button on the video to compare with the original colors.
    private var showingOriginal = false

    // The video size that the presenter knows. A new project resets it.
    private var reportedVideoSize: Dimension? = null

    // True while the view sets the sliders from the state, so the slider listeners do not send it back.
    private var updatingFromState = false

    private val editor = CropEditorOverlay(player, onVideoBoundsChanged = { reportVideoSize() }) { adjustments ->
        presenter.onIntent(AdjustVideoIntent.ChangeTransform(adjustments))
    }

    private val gradePanel = ColorGradePanel(
        liveSupported = try {
            player.isAdjustSupported()
        } catch (_: Throwable) {
            false
        },
    )

    private val transformControls = CropTransformControls(
        onChanged = { adjustments -> presenter.onIntent(AdjustVideoIntent.ChangeTransform(adjustments)) },
        onResetTransform = { presenter.onIntent(AdjustVideoIntent.ResetTransform) },
        onBackgroundChanged = { color -> presenter.onIntent(AdjustVideoIntent.ChangeBackground(color)) },
    )

    private val trimControls = TrimControls(
        onSet = { end -> presenter.onIntent(AdjustVideoIntent.SetTrim(end, player.totalDurationMs())) },
        onGoTo = { end -> presenter.onIntent(AdjustVideoIntent.GoToTrim(end, player.totalDurationMs())) },
        onReset = { presenter.onIntent(AdjustVideoIntent.ResetTrim) },
    )

    private val trimTimeline = TrimTimeline(
        onSeek = { ms ->
            player.seek(ms)
            val duration = player.totalDurationMs()
            if (duration > 0) playbackBar.setTime(ms, duration)
        },
        onCommit = { end, ms -> presenter.onIntent(AdjustVideoIntent.SetTrimAt(end, ms, player.totalDurationMs())) },
        onPreview = { end, ms ->
            dragTrim = ms?.let { end to it }
            renderTrimControls()
        },
    ).apply { isVisible = false }

    private val changePointsCard = ChangePointsCard(
        namePrefix = "adjust",
        hint = changePointsHint(AdjustSection.TRIM),
        onAdd = { presenter.onIntent(AdjustVideoIntent.AddChangePoint) },
        onRemove = { presenter.onIntent(AdjustVideoIntent.RemoveChangePoint) },
        onPrevious = { presenter.onIntent(AdjustVideoIntent.GoToPreviousChangePoint) },
        onNext = { presenter.onIntent(AdjustVideoIntent.GoToNextChangePoint) },
    )

    private val playbackBar = VideoPlaybackBar(
        namePrefix = "adjust",
        barName = "adjust-transport",
        onTogglePlay = { togglePlayPause() },
        onSeek = { target -> player.seek(target) },
        onNudge = { delta -> nudge(delta) },
    )

    /** The sub-tabs, as headings across the top of the tab. Trim is first: the user trims the video first. */
    private val sectionChoice = HeaderTabs(
        "adjust-section",
        listOf(
            HeaderTabs.Option(AdjustSection.TRIM, tr("adjust.trim"), "trim", tr("adjust.trim.sub"), Material2OutlinedAL.CONTENT_CUT),
            HeaderTabs.Option(AdjustSection.COLORS, tr("adjust.colors"), "colors", tr("adjust.colors.sub"), Material2OutlinedMZ.PALETTE),
            HeaderTabs.Option(AdjustSection.TRANSFORM, tr("adjust.transform"), "transform", tr("adjust.transform.sub"), Material2OutlinedAL.CROP),
        ),
    ).apply {
        selected = AdjustSection.TRIM
        onChange { section -> presenter.onIntent(AdjustVideoIntent.SelectSection(section)) }
    }

    private val sectionLayout = CardLayout()
    private val sectionCards = JPanel(sectionLayout).apply {
        isOpaque = true
        background = Palette.BG
        add(gradePanel, AdjustSection.COLORS.name)
        add(transformControls, AdjustSection.TRANSFORM.name)
        add(trimControls, AdjustSection.TRIM.name)
    }

    private val viewportPanel = JPanel(BorderLayout()).apply {
        name = "adjust-viewport"
        isOpaque = true
        background = Palette.VIDEO_BG
        minimumSize = Dimension(640, 360)
        add(player.component, BorderLayout.CENTER)
    }

    private val leftPanel = JPanel(BorderLayout()).apply {
        name = "adjust-left"
        minimumSize = Dimension(640, 360)
        isOpaque = true
        background = Palette.BG
        add(viewportPanel, BorderLayout.CENTER)
        add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(trimTimeline)
                add(playbackBar)
            },
            BorderLayout.SOUTH,
        )
    }

    private val changePointsColumn = CardColumn().apply { add(changePointsCard) }

    /** The change points card with a line above it. The Trim sub-tab hides it, because the trim has no change points. */
    private val changePointsArea = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(12, 0, 0, 0),
        )
        add(changePointsColumn, BorderLayout.CENTER)
    }

    /** The end of the trim that the user drags on the trim timeline, and its time. Null while no drag. */
    private var dragTrim: Pair<TrimEnd, Long>? = null

    private val rightPanel = JPanel(BorderLayout()).apply {
        name = "adjust-right"
        isOpaque = true
        background = Palette.BG
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)
        preferredSize = Dimension(SIDE_PANEL_WIDTH, 600)
        minimumSize = Dimension(SIDE_PANEL_WIDTH, 360)
        add(sectionCards, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(changePointsArea, BorderLayout.CENTER)
                add(SavedFooter(), BorderLayout.SOUTH)
            },
            BorderLayout.SOUTH,
        )
    }

    init {
        name = "adjust-root"
        isOpaque = true
        background = Palette.BG
        add(sectionChoice, BorderLayout.NORTH)
        add(leftPanel, BorderLayout.CENTER)
        add(rightPanel, BorderLayout.EAST)

        ColorControl.entries.forEach { control ->
            val slider = gradePanel.slider(control)
            slider.addChangeListener {
                if (!updatingFromState) {
                    presenter.onIntent(AdjustVideoIntent.ChangeColors(slidersToColors(), final = !slider.valueIsAdjusting))
                }
            }
        }
        gradePanel.resetButton.addActionListener { presenter.onIntent(AdjustVideoIntent.ResetColors) }

        installCompareOnHold()
        installPlayerCallbacks()
        installKeyBindings()
    }

    override fun addNotify() {
        super.addNotify()
        presenter.attach(this)
        SwingUtilities.invokeLater { ensurePlayerLoaded() }
    }

    override fun removeNotify() {
        presenter.detach()
        super.removeNotify()
    }

    fun setProjectManifest(path: String) {
        reportedVideoSize = null
        presenter.onIntent(AdjustVideoIntent.LoadProject(path))
    }

    fun onActivated() {
        presenter.onActivated()
        ensurePlayerLoaded()
        player.activatePreview("adjust video activated")
        // The nav button keeps the focus after the tab switch and takes Space. The video must have the focus.
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
    }

    fun onDeactivated() {
        setShowingOriginal(false)
        player.pause()
        player.deactivatePreview("adjust video deactivated")
        presenter.onDeactivated()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        onDeactivated()
        if (presenter is DefaultAdjustVideoPresenter) presenter.dispose() else presenter.detach()
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.onReady = null
        player.onVideoBoundsChanged = null
        player.close()
    }

    override fun render(state: AdjustVideoViewState) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { render(state) }
            return
        }
        this.state = state
        showSection(state.section)
        // The controls and the preview show the values of the active change point, without the blend (B-47).
        val editing = state.editing
        renderColors(editing)
        transformControls.render(editing)
        editor.render(editing)
        applyPreview()
        changePointsCard.render(state.changePoints)
        playbackBar.setMarkers(state.changePointTimes)
        renderTrim()
        reportVideoSize()
        val source = state.sourceVideo
        if (source != null && source != loadedMediaFile && source != pendingMediaFile) {
            pendingMediaFile = source
            ensurePlayerLoaded()
        }
    }

    override fun renderEffect(effect: AdjustVideoViewEffect) {
        when (effect) {
            // The message is short, so it is the title. The tab behind the box tells where it comes from.
            is AdjustVideoViewEffect.ShowError -> MessageDialog.show(this, MessageKind.WARNING, effect.message, "")
            is AdjustVideoViewEffect.TrimCutsPoints -> MessageDialog.show(
                this,
                MessageKind.WARNING,
                tr("adjust.trim.dialog.title"),
                tr("adjust.trim.dialog.message", "count" to effect.count),
            )
            is AdjustVideoViewEffect.SeekTo -> {
                player.seek(effect.ms)
                trimTimeline.setPlayhead(effect.ms)
                val duration = player.totalDurationMs()
                if (duration > 0) playbackBar.setTime(effect.ms, duration)
            }
        }
    }

    /** Shows the controls of [section]. The Transform sub-tab shows the full frame with the crop editor. */
    private fun showSection(section: AdjustSection) {
        if (section == shownSection) return
        shownSection = section
        sectionChoice.selected = section
        sectionLayout.show(sectionCards, section.name)
        val transform = section == AdjustSection.TRANSFORM
        if (transform) showingOriginal = false
        player.setCropEditing(transform)
        editor.active = transform
        changePointsCard.setHint(changePointsHint(section))
        val trim = section == AdjustSection.TRIM
        changePointsArea.isVisible = !trim
        trimTimeline.isVisible = trim
        playbackBar.seekRowVisible = !trim
        rightPanel.revalidate()
        leftPanel.revalidate()
        renderTrim()
    }

    /**
     * Shows the trim on the Trim sub-tab and on the trim timeline. The source length comes from the player, so the
     * player calls this again when it knows it.
     */
    private fun renderTrim() {
        val duration = player.totalDurationMs()
        renderTrimControls()
        val range = if (duration > 0) TrimRange.of(state.adjustments, duration) else null
        trimTimeline.render(duration.coerceAtLeast(0L), range, state.points.map { it.startMs.toLong() to it.endMs.toLong() })
    }

    /**
     * Shows the trim on the right panel. While the user drags a handle, it shows the trim with the dragged time, so
     * the length, the points and the warning change during the drag.
     */
    private fun renderTrimControls() {
        val drag = dragTrim
        val adjustments = when (drag?.first) {
            null -> state.adjustments
            TrimEnd.START -> state.adjustments.copy(trimStartMs = drag.second)
            TrimEnd.END -> state.adjustments.copy(trimEndMs = drag.second)
        }
        val outside = TrimRange.outside(adjustments, state.points, { it.startMs.toLong() }, { it.endMs.toLong() }).size
        trimControls.render(adjustments, player.totalDurationMs(), state.points.size, outside)
    }

    /** The hint of the change points card. Each sub-tab has its own change points and its own question. */
    private fun changePointsHint(section: AdjustSection): String {
        val question = when (section) {
            AdjustSection.COLORS -> tr("adjust.colors.changePoints.question")
            AdjustSection.TRANSFORM -> tr("adjust.transform.changePoints.question")
            AdjustSection.TRIM -> return ""
        }
        return question + "\n" + tr("adjust.changePoints.hint", "seconds" to ColorTimeline.BLEND_MS / 1000)
    }

    private fun renderColors(editing: AdjustmentsV1) {
        updatingFromState = true
        try {
            val values = AdjustmentsUiConverter.modelToSliderValues(editing)
            gradePanel.slider(ColorControl.BRIGHTNESS).value = values.brightness
            gradePanel.slider(ColorControl.CONTRAST).value = values.contrast
            gradePanel.slider(ColorControl.SATURATION).value = values.saturation
            gradePanel.slider(ColorControl.SHADOWS).value = values.shadows
            gradePanel.slider(ColorControl.HIGHLIGHTS).value = values.highlights
            gradePanel.slider(ColorControl.TEMPERATURE).value = values.temperature
        } finally {
            updatingFromState = false
        }
    }

    private fun slidersToColors(): AdjustmentsV1 {
        fun value(control: ColorControl) = gradePanel.slider(control).value
        return AdjustmentsUiConverter.slidersToModel(
            value(ColorControl.BRIGHTNESS),
            value(ColorControl.CONTRAST),
            value(ColorControl.SATURATION),
            value(ColorControl.SHADOWS),
            value(ColorControl.HIGHLIGHTS),
            value(ColorControl.TEMPERATURE),
        )
    }

    /** Shows the values of the active change point. While the user compares, the colors are the original colors. */
    private fun applyPreview() {
        val editing = state.editing
        val defaults = AdjustmentsV1()
        val shown = if (!showingOriginal) editing else editing.copy(
            brightness = defaults.brightness,
            contrast = defaults.contrast,
            saturation = defaults.saturation,
            shadows = defaults.shadows,
            highlights = defaults.highlights,
            whiteBalance = defaults.whiteBalance,
        )
        player.applyPreviewAdjustments(shown)
    }

    private fun ensurePlayerLoaded() {
        val file = pendingMediaFile ?: return
        val window = SwingUtilities.getWindowAncestor(player.component)
        if (!player.component.isDisplayable || window == null || !window.isShowing) return
        player.load(file)
        player.pause()
        pendingMediaFile = null
        loadedMediaFile = file
        applyPreview()
    }

    private var lastTimeUiUpdateAt = 0L

    private fun installPlayerCallbacks() {
        player.onTimeChanged = { ms ->
            EventQueue.invokeLater {
                presenter.onIntent(AdjustVideoIntent.PlayheadMoved(ms))
                trimTimeline.setPlayhead(ms)
                val duration = player.totalDurationMs()
                if (duration > 0) {
                    val now = System.currentTimeMillis()
                    if (now - lastTimeUiUpdateAt >= 80L) {
                        playbackBar.setTime(ms, duration)
                        lastTimeUiUpdateAt = now
                    }
                } else {
                    playbackBar.reset()
                }
                updatePlayPauseUi()
            }
        }
        player.onStatusChanged = { EventQueue.invokeLater { updatePlayPauseUi() } }
        player.onReady = {
            EventQueue.invokeLater {
                val duration = player.totalDurationMs()
                if (duration > 0) playbackBar.setTime(player.currentTimeMs(), duration)
                applyPreview()
                updatePlayPauseUi()
                reportVideoSize()
                renderTrim()
            }
        }
    }

    /** Tells the presenter the video size, after the current event, when it changed. */
    private fun reportVideoSize() {
        val size = player.videoSize() ?: return
        if (size == reportedVideoSize) return
        reportedVideoSize = size
        EventQueue.invokeLater { presenter.onIntent(AdjustVideoIntent.VideoSizeKnown(size.width, size.height)) }
    }

    /** On the Colors sub-tab, the original colors show while the left mouse button is down on the video. */
    private fun installCompareOnHold() {
        player.component.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && section == AdjustSection.COLORS) setShowingOriginal(true)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) setShowingOriginal(false)
            }
        })
    }

    private fun setShowingOriginal(value: Boolean) {
        if (showingOriginal == value) return
        showingOriginal = value
        applyPreview()
    }

    private fun togglePlayPause() {
        if (player.status() == PlayerStatus.PLAYING) player.pause() else player.play()
        updatePlayPauseUi()
        player.component.requestFocusInWindow()
    }

    /** Seeks [deltaMs] from the playhead, as the seek buttons of the Points tab do. */
    private fun nudge(deltaMs: Long) {
        val playing = player.status() == PlayerStatus.PLAYING
        val duration = player.totalDurationMs()
        var target = maxOf(0L, player.currentTimeMs() + relativeSeekDeltaMs(deltaMs, playing))
        if (duration > 0) target = target.coerceAtMost(duration - 1)
        player.seek(target)
        if (duration > 0) playbackBar.setTime(target, duration)
    }

    /** A seek hotkey seeks and briefly marks the matching button. */
    private fun nudgeFromKey(deltaMs: Long) {
        playbackBar.flashSeek(deltaMs)
        nudge(deltaMs)
    }

    private fun updatePlayPauseUi() {
        playbackBar.setPlaying(player.status() == PlayerStatus.PLAYING)
    }

    private fun installKeyBindings() {
        fun bind(target: JComponent, keyStroke: String, actionName: String, conditions: IntArray, action: () -> Unit) {
            conditions.forEach { condition ->
                target.getInputMap(condition).put(KeyStroke.getKeyStroke(keyStroke), actionName)
            }
            target.actionMap.put(actionName, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) = action()
            })
        }
        val anywhere = intArrayOf(WHEN_IN_FOCUSED_WINDOW, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        bind(this, AppShortcuts.PLAY_PAUSE.keyStroke, "adjustTogglePlayPause", anywhere) { togglePlayPause() }
        // Left and Right seek 1 second, and with Shift 5 seconds, as on the Points tab. This is the same on both sub-tabs.
        bind(this, AppShortcuts.LEFT.keyStroke, "adjustSeekBack", anywhere) { nudgeFromKey(-1_000) }
        bind(this, AppShortcuts.RIGHT.keyStroke, "adjustSeekForward", anywhere) { nudgeFromKey(1_000) }
        bind(this, AppShortcuts.SHIFT_LEFT.keyStroke, "adjustSeekBackFast", anywhere) { nudgeFromKey(-5_000) }
        bind(this, AppShortcuts.SHIFT_RIGHT.keyStroke, "adjustSeekForwardFast", anywhere) { nudgeFromKey(5_000) }
    }

    /** "Saved to the project. Export uses these values." with a save icon, under a line. */
    private class SavedFooter : JComponent() {
        private val saveIcon = UiKit.icon(Material2OutlinedMZ.SAVE, 15, Palette.FG_3)
        private val textFont = UiKit.font(11.5f)

        init {
            name = "adjust-footer"
            toolTipText = FOOTER_TEXT
        }

        override fun getPreferredSize() = Dimension(SIDE_PANEL_WIDTH, FOOTER_HEIGHT)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                g2.color = Palette.LINE
                g2.fillRect(0, 0, width, 1)
                val top = 1
                val inner = height - top
                saveIcon.paintIcon(this, g2, PAD_X, top + (inner - saveIcon.iconHeight) / 2)
                UiKit.drawText(g2, FOOTER_TEXT, textFont, Palette.FG_3, (PAD_X + saveIcon.iconWidth + 8).toFloat(), top.toFloat(), inner.toFloat())
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        val FOOTER_TEXT: String get() = tr("adjust.footer")

        private const val PAD_X = 16
        private const val FOOTER_HEIGHT = 37
    }
}
