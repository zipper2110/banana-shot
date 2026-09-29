package org.litvin.ui.tabs.adjustments

import org.litvin.GeometryViewportPanel
import org.litvin.projects.ManifestIO
import org.litvin.media.PlayerStatus
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import org.litvin.media.SwingMediaPlayer
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.app.PreferencesProvider
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.VideoPlaybackBar
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.ActionEvent
import java.io.File
import java.util.prefs.Preferences
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

/**
 * The Colors tab: the video preview with the playback bar on the left, and the Color grade panel on the right.
 * The layout comes from design/colors-redesign/option-a.html.
 * Component IDs: adj-color-root, adj-color-left, adj-color-right, adj-color-viewport, adj-color-transport
 */
class SwingColorAdjustmentsPanel(
    private val player: SwingMediaPlayer,
    private val adjustments: AdjustmentsSession,
    private val prefs: Preferences,
) : JPanel(BorderLayout()), AutoCloseable {
    constructor() : this(
        MpvSwingMediaPlayerAdapter(),
        AdjustmentsStore.legacySession(),
        PreferencesProvider.production().node(PreferencesProvider.COLOR_ADJUSTMENTS),
    )

    private var projectManifestPath: String? = null
    private val closed = AtomicBoolean(false)

    // Adjustments store subscription and feedback guard
    private var unsubscribeStore: (() -> Unit)? = null
    private var updatingFromModel: Boolean = false

    // Media player and media loading state
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false

    private val viewportPanel = JPanel(BorderLayout()).apply {
        name = "adj-color-viewport"
        isOpaque = true
        background = Color.BLACK
        minimumSize = Dimension(640, 360)
    }
    private val geometryViewport = GeometryViewportPanel(player.component)

    private val playbackBar = VideoPlaybackBar(
        namePrefix = "colors",
        barName = "adj-color-transport",
        onTogglePlay = { togglePlayPause() },
        onSeek = { target -> player.seek(target) },
    )

    private val leftPanel = JPanel(BorderLayout()).apply {
        name = "adj-color-left"
        minimumSize = Dimension(640, 360)
        isOpaque = true
        background = UiKit.BG
        add(viewportPanel, BorderLayout.CENTER)
        add(playbackBar, BorderLayout.SOUTH)
    }

    private val gradePanel = ColorGradePanel(
        liveSupported = try {
            player.isAdjustSupported()
        } catch (_: Throwable) {
            false
        },
    )

    init {
        name = "adj-color-root"
        isOpaque = true
        background = UiKit.BG
        viewportPanel.add(geometryViewport, BorderLayout.CENTER)

        gradePanel.resetButton.addActionListener {
            adjustments.set { prev -> mergeColorInto(prev, AdjustmentsUiConverter.DEFAULTS) }
        }
        ColorControl.entries.forEach { control ->
            gradePanel.slider(control).addChangeListener {
                if (!updatingFromModel) {
                    val color = uiToModel()
                    val nextAdjustments = mergeColorInto(adjustments.get(), color)
                    applyPreview(nextAdjustments)
                    adjustments.set { prev -> mergeColorInto(prev, color) }
                }
            }
        }

        installKeyBindings()
        installPlayerCallbacks()

        add(leftPanel, BorderLayout.CENTER)
        add(gradePanel, BorderLayout.EAST)
    }

    fun uiToModel(): AdjustmentsV1 {
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

    fun applyPreview(adjustments: AdjustmentsV1) {
        player.applyPreviewAdjustments(adjustments)
        geometryViewport.refreshGeometry()
    }

    private fun modelToUi(adjustments: AdjustmentsV1) {
        updatingFromModel = true
        try {
            val sliderValues = AdjustmentsUiConverter.modelToSliderValues(adjustments)
            gradePanel.slider(ColorControl.BRIGHTNESS).value = sliderValues.brightness
            gradePanel.slider(ColorControl.CONTRAST).value = sliderValues.contrast
            gradePanel.slider(ColorControl.SATURATION).value = sliderValues.saturation
            gradePanel.slider(ColorControl.SHADOWS).value = sliderValues.shadows
            gradePanel.slider(ColorControl.HIGHLIGHTS).value = sliderValues.highlights
            gradePanel.slider(ColorControl.TEMPERATURE).value = sliderValues.temperature
            // Also update live preview, preserving geometry from the shared model.
            applyPreview(adjustments)
        } finally {
            updatingFromModel = false
        }
    }

    override fun addNotify() {
        super.addNotify()
        SwingUtilities.invokeLater { ensurePlayerLoaded() }
        // Ensure UI reflects current adjustments on first show
        modelToUi(adjustments.get())
    }

    private fun ensurePlayerLoaded() {
        if (isMediaLoaded) return
        val videoFile = pendingMediaFile ?: return
        val wnd = SwingUtilities.getWindowAncestor(player.component)
        if (!player.component.isDisplayable || wnd == null || !wnd.isShowing) return
        player.load(videoFile)
        player.pause()
        isMediaLoaded = true
        // Re-apply current adjustments after media is loaded so the player picks them up
        try {
            applyPreview(adjustments.get())
        } catch (_: Throwable) { /* ignore */ }
    }

    private var lastTimeUiUpdateAt: Long = 0L
    private val timeUiCadenceMs: Long = 80L

    private fun installPlayerCallbacks() {
        player.onTimeChanged = { ms ->
            EventQueue.invokeLater {
                val dur = player.totalDurationMs()
                if (dur > 0) {
                    val now = System.currentTimeMillis()
                    if ((now - lastTimeUiUpdateAt) >= timeUiCadenceMs) {
                        playbackBar.setTime(ms, dur)
                        lastTimeUiUpdateAt = now
                    }
                } else {
                    playbackBar.reset()
                }
                updatePlayPauseUi()
            }
        }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseUi() } }
        player.onReady = {
            EventQueue.invokeLater {
                val dur = player.totalDurationMs()
                if (dur > 0) playbackBar.setTime(player.currentTimeMs(), dur)
                applyPreview(adjustments.get())
                updatePlayPauseUi()
            }
        }
    }

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) player.pause() else player.play()
        updatePlayPauseUi()
        player.component.requestFocusInWindow()
    }

    private fun updatePlayPauseUi() {
        playbackBar.setPlaying(player.status() == PlayerStatus.PLAYING)
    }

    private fun installKeyBindings() {
        fun bind(key: String, actionName: String, runnable: () -> Unit) {
            val am = this.actionMap
            val ims = arrayOf(
                JComponent.WHEN_IN_FOCUSED_WINDOW,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            )
            ims.forEach { cond ->
                val im = this.getInputMap(cond)
                im.put(KeyStroke.getKeyStroke(key), actionName)
            }
            am.put(actionName, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    runnable()
                    EventQueue.invokeLater { player.component.requestFocusInWindow() }
                }
            })
        }
        // Space toggles play/pause
        bind(AppShortcuts.PLAY_PAUSE.keyStroke, "adjTogglePlayPause") { togglePlayPause() }
    }

    fun setProjectManifest(path: String) {
        projectManifestPath = path
        // Load adjustments for this project directory
        val projectDir = File(path).parentFile?.absolutePath
        if (!projectDir.isNullOrBlank()) {
            adjustments.load(projectDir)
        }

        val manifest = ManifestIO.read(path)
        val src = manifest.sourceVideo
        if (!src.isNullOrBlank()) {
            val f = File(src)
            if (f.exists()) {
                pendingMediaFile = f
                isMediaLoaded = false
                ensurePlayerLoaded()
            }
        }
        // Ensure UI reflects the loaded adjustments
        modelToUi(adjustments.get())
    }

    fun onActivated() {
        ensurePlayerLoaded()
        player.activatePreview("color adjustments activated")
        // Subscribe to adjustments changes to reflect external updates and live-apply preview
        unsubscribeStore?.invoke()
        unsubscribeStore = adjustments.subscribe { adj ->
            EventQueue.invokeLater { modelToUi(adj) }
        }
        // Push current state immediately
        modelToUi(adjustments.get())
    }

    fun onDeactivated() {
        player.pause()
        player.deactivatePreview("color adjustments deactivated")
        unsubscribeStore?.invoke(); unsubscribeStore = null
        // Persist current adjustments immediately when leaving the tab
        val dir = projectManifestPath?.let { File(it).parentFile?.absolutePath }
        adjustments.save(dir)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        onDeactivated()
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.onReady = null
        adjustments.flush()
        player.close()
    }

    private fun mergeColorInto(base: AdjustmentsV1, color: AdjustmentsV1): AdjustmentsV1 {
        return base.copy(
            brightness = color.brightness,
            contrast = color.contrast,
            saturation = color.saturation,
            shadows = color.shadows,
            highlights = color.highlights,
            whiteBalance = color.whiteBalance,
        )
    }

}
