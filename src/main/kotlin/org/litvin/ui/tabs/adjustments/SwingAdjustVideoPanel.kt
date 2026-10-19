package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.ColorTimeline
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.commons.CardColumn
import org.litvin.ui.commons.ChangePointsCard
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SIDE_PANEL_WIDTH
import org.litvin.ui.commons.SegmentedChoice
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
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * The Colors & Transform tab: the video with the playback bar on the left, and the right panel with two sub-tabs,
 * Colors and Transform. Each sub-tab has its own header with "Reset all". The change points card is under the
 * sub-tabs, because a change point has the colors and the transform (B-47).
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

    private val changePointsCard = ChangePointsCard(
        namePrefix = "adjust",
        hint = "Did the light change or did the phone move?<br>" +
            "Add a change point there. It blends over ${ColorTimeline.BLEND_MS / 1000} second.",
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
    )

    private val sectionChoice = SegmentedChoice(
        "adjust-section",
        listOf(
            SegmentedChoice.Option(AdjustSection.COLORS, "Colors"),
            SegmentedChoice.Option(AdjustSection.TRANSFORM, "Transform"),
        ),
    ).apply {
        selected = AdjustSection.COLORS
        // Space must stay the play hotkey, so the segments never keep the focus.
        components.forEach { it.isFocusable = false }
        onChange { section -> presenter.onIntent(AdjustVideoIntent.SelectSection(section)) }
    }

    private val sectionLayout = CardLayout()
    private val sectionCards = JPanel(sectionLayout).apply {
        isOpaque = true
        background = Palette.BG
        add(gradePanel, AdjustSection.COLORS.name)
        add(transformControls, AdjustSection.TRANSFORM.name)
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
        add(playbackBar, BorderLayout.SOUTH)
    }

    private val rightPanel = JPanel(BorderLayout()).apply {
        name = "adjust-right"
        isOpaque = true
        background = Palette.BG
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)
        preferredSize = Dimension(SIDE_PANEL_WIDTH, 600)
        minimumSize = Dimension(SIDE_PANEL_WIDTH, 360)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(14, 16, 0, 16)
                add(sectionChoice, BorderLayout.CENTER)
            },
            BorderLayout.NORTH,
        )
        add(sectionCards, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
                    BorderFactory.createEmptyBorder(12, 0, 0, 0),
                )
                add(CardColumn().apply { add(changePointsCard) }, BorderLayout.CENTER)
                add(SavedFooter(), BorderLayout.SOUTH)
            },
            BorderLayout.SOUTH,
        )
    }

    init {
        name = "adjust-root"
        isOpaque = true
        background = Palette.BG
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

        // The chip on the video shows if the arrow keys move the crop rectangle.
        player.component.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                editor.videoFocused = true
            }

            override fun focusLost(e: FocusEvent) {
                editor.videoFocused = false
            }
        })
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
            is AdjustVideoViewEffect.SeekTo -> {
                player.seek(effect.ms)
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
        val anywhere = intArrayOf(JComponent.WHEN_IN_FOCUSED_WINDOW, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        bind(this, AppShortcuts.PLAY_PAUSE.keyStroke, "adjustTogglePlayPause", anywhere) { togglePlayPause() }

        // On the Transform sub-tab, the arrow keys move the crop rectangle while the video has the focus
        // (a click on the video gives it). The editor ignores them on the Colors sub-tab.
        val video = player.component as? JComponent ?: return
        val focused = intArrayOf(JComponent.WHEN_FOCUSED)
        bind(video, AppShortcuts.LEFT.keyStroke, "cropNudgeLeft", focused) { editor.nudge(-1, 0) }
        bind(video, AppShortcuts.RIGHT.keyStroke, "cropNudgeRight", focused) { editor.nudge(1, 0) }
        bind(video, AppShortcuts.UP.keyStroke, "cropNudgeUp", focused) { editor.nudge(0, -1) }
        bind(video, AppShortcuts.DOWN.keyStroke, "cropNudgeDown", focused) { editor.nudge(0, 1) }
        bind(video, AppShortcuts.SHIFT_LEFT.keyStroke, "cropNudgeLeftFast", focused) { editor.nudge(-10, 0) }
        bind(video, AppShortcuts.SHIFT_RIGHT.keyStroke, "cropNudgeRightFast", focused) { editor.nudge(10, 0) }
        bind(video, AppShortcuts.SHIFT_UP.keyStroke, "cropNudgeUpFast", focused) { editor.nudge(0, -10) }
        bind(video, AppShortcuts.SHIFT_DOWN.keyStroke, "cropNudgeDownFast", focused) { editor.nudge(0, 10) }
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
        const val FOOTER_TEXT = "Saved to the project. Export uses these values."

        private const val PAD_X = 16
        private const val FOOTER_HEIGHT = 37
    }
}
