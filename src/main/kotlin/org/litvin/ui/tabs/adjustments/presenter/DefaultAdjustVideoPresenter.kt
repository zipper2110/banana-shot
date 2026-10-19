package org.litvin.ui.tabs.adjustments.presenter

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.ChangePoints
import org.litvin.adjustments.ColorTimeline
import org.litvin.adjustments.CropGeometryMath
import org.litvin.adjustments.GeometryTimeline
import org.litvin.analytics.Analytics
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.DisabledAnalytics
import org.litvin.projects.ManifestIO
import java.awt.EventQueue
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The presenter of the Colors & Transform tab. It keeps the colors, the transform, and the change points in the shared
 * adjustments session and tells the view which source video to show. The view owns the live video player.
 */
class DefaultAdjustVideoPresenter(
    private val adjustments: AdjustmentsSession = AdjustmentsStore.legacySession(),
    private val analytics: Analytics = DisabledAnalytics,
) : AdjustVideoPresenter {
    private val disposed = AtomicBoolean(false)

    /**
     * True when the user changed the transform during this visit of the tab. A drag sends many changes, so the
     * analytics count one change for each visit (B-9 decision 12).
     */
    private var transformChangedByUser = false

    /**
     * The colors of the last counted change, of the last load, or of the change point that the sliders started to
     * edit. A change back to them is not a new color change.
     */
    private var countedColors: List<Float>? = null

    private var view: AdjustVideoView? = null
    private var unsubscribeStore: (() -> Unit)? = null
    private var projectDir: String? = null
    private var state = AdjustVideoViewState()

    /** The size of the source video, or null before the player knows it. */
    private var videoSize: Pair<Int, Int>? = null

    /** The open sub-tab. The app reports it as the shown tab to the analytics. */
    val section: AdjustSection get() = state.section

    override fun attach(view: AdjustVideoView) {
        this.view = view
        render()
    }

    override fun detach() {
        unsubscribeStore?.invoke()
        unsubscribeStore = null
        view = null
    }

    override fun onActivated() {
        unsubscribeStore?.invoke()
        unsubscribeStore = adjustments.subscribe { newAdjustments ->
            updateState { it.copy(adjustments = newAdjustments) }
        }
        updateState { it.copy(adjustments = adjustments.get()) }
        countedColors = colorsOf(state.editing)
        convertOlderGeometry()
    }

    override fun onDeactivated() {
        if (transformChangedByUser) {
            transformChangedByUser = false
            analytics.record(AnalyticsEvent.CropRotateChanged)
        }
        adjustments.save(projectDir)
    }

    override fun onIntent(intent: AdjustVideoIntent) {
        when (intent) {
            is AdjustVideoIntent.LoadProject -> loadProject(intent.manifestPath)
            is AdjustVideoIntent.SelectSection -> selectSection(intent.section)
            is AdjustVideoIntent.ChangeColors -> changeColors(intent.colors, intent.final)
            AdjustVideoIntent.ResetColors -> {
                val at = state.playheadMs
                val before = adjustments.get()
                adjustments.set { previous -> ColorTimeline.withEdited(previous, at, AdjustmentsV1()) }
                if (adjustments.get() != before) {
                    analytics.record(AnalyticsEvent.ColorChanged)
                    countedColors = colorsOf(AdjustmentsV1())
                }
            }
            is AdjustVideoIntent.ChangeTransform -> {
                val incoming = intent.adjustments
                val edited = AdjustmentsV1(
                    zoom = incoming.zoom.coerceIn(AdjustmentsV1.MIN_ZOOM, AdjustmentsV1.MAX_ZOOM),
                    panX = incoming.panX.coerceIn(-1.0f, 1.0f),
                    panY = incoming.panY.coerceIn(-1.0f, 1.0f),
                    rotationDeg = incoming.rotationDeg.coerceIn(-180.0f, 180.0f),
                ).let { next -> keepCornersOut(state.editing, next) }
                val at = state.playheadMs
                updateState { it.copy(adjustments = GeometryTimeline.withEdited(it.adjustments, at, edited)) }
                noteTransformChange { previous -> GeometryTimeline.withEdited(previous, at, edited) }
            }
            AdjustVideoIntent.ResetTransform -> {
                val at = state.playheadMs
                noteTransformChange { previous -> GeometryTimeline.withEdited(previous, at, AdjustmentsV1()) }
            }
            is AdjustVideoIntent.VideoSizeKnown -> {
                if (intent.width <= 0 || intent.height <= 0) return
                videoSize = intent.width to intent.height
                convertOlderGeometry()
            }
            is AdjustVideoIntent.ChangeBackground -> {
                val color = intent.color
                updateState { it.copy(adjustments = it.adjustments.copy(backgroundColor = color)) }
                noteTransformChange { previous -> previous.copy(backgroundColor = color) }
            }
            is AdjustVideoIntent.PlayheadMoved -> movePlayhead(intent.ms)
            AdjustVideoIntent.AddChangePoint -> {
                if (!state.canAddChangePoint) return
                val at = state.playheadMs
                changeKeys { previous -> ChangePoints.withAdded(previous, at) }
            }
            AdjustVideoIntent.RemoveChangePoint -> {
                val at = state.playheadMs
                changeKeys { previous -> ChangePoints.withRemoved(previous, at) }
            }
            AdjustVideoIntent.GoToPreviousChangePoint -> state.previousChangePointMs?.let { seekTo(it) }
            AdjustVideoIntent.GoToNextChangePoint -> state.nextChangePointMs?.let { seekTo(it) }
        }
    }

    /** The zoom of a transform change, so that a rotation of an auto-fitted crop shows no black corners. */
    private fun keepCornersOut(previous: AdjustmentsV1, next: AdjustmentsV1): AdjustmentsV1 {
        val (width, height) = videoSize ?: return next
        return CropGeometryMath.keepCornersOut(previous, next, width.toDouble(), height.toDouble())
    }

    /**
     * Converts the geometry of an older project to the current version, when the video size is known.
     * The crop rectangles stay the same, so this is not a change of the user.
     */
    private fun convertOlderGeometry() {
        val (width, height) = videoSize ?: return
        if (adjustments.get().version >= AdjustmentsV1.CURRENT_VERSION) return
        adjustments.set { previous -> CropGeometryMath.withCurrentVersion(previous, width.toDouble(), height.toDouble()) }
        updateState { it.copy(adjustments = adjustments.get()) }
    }

    private fun selectSection(section: AdjustSection) {
        if (section == state.section) return
        updateState { it.copy(section = section) }
        analytics.record(AnalyticsEvent.TabShown(analyticsTab(section), byUser = true))
    }

    /** The preview shows the new colors at once. A drag counts one time, when the user releases the slider. */
    private fun changeColors(colors: AdjustmentsV1, final: Boolean) {
        val at = state.playheadMs
        updateState { it.copy(adjustments = ColorTimeline.withEdited(it.adjustments, at, colors)) }
        adjustments.set { previous -> ColorTimeline.withEdited(previous, at, colors) }
        if (!final) return
        val counted = colorsOf(colors)
        if (counted == countedColors) return
        countedColors = counted
        analytics.record(AnalyticsEvent.ColorChanged)
    }

    /** Keeps the playhead. The view renders again only when the controls must show other values or buttons. */
    private fun movePlayhead(ms: Long) {
        val before = state
        val after = before.copy(playheadMs = ms.coerceAtLeast(0L))
        state = after
        val beforePoints = before.changePoints
        val afterPoints = after.changePoints
        val changed = afterPoints.activeIndex != beforePoints.activeIndex ||
            afterPoints.canAdd != beforePoints.canAdd ||
            afterPoints.previousMs != beforePoints.previousMs ||
            afterPoints.nextMs != beforePoints.nextMs
        if (changed) {
            countedColors = colorsOf(after.editing)
            render()
        }
    }

    private fun seekTo(ms: Long) {
        movePlayhead(ms)
        postEffect(AdjustVideoViewEffect.SeekTo(ms))
    }

    /** Adds or removes a change point. The store subscription shows the result. */
    private fun changeKeys(change: (AdjustmentsV1) -> AdjustmentsV1) {
        updateState { it.copy(adjustments = change(it.adjustments)) }
        adjustments.set(change)
        countedColors = colorsOf(state.editing)
    }

    /**
     * Applies a transform change of the user. A change that has no effect does not count. The store clamps each
     * value, so only the stored result shows if the change had an effect.
     */
    private fun noteTransformChange(change: (AdjustmentsV1) -> AdjustmentsV1) {
        val before = adjustments.get()
        adjustments.set(change)
        if (adjustments.get() != before) transformChangedByUser = true
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        onDeactivated()
        detach()
    }

    private fun loadProject(manifestPath: String) {
        projectDir = File(manifestPath).parentFile?.absolutePath
        videoSize = null
        projectDir?.let { adjustments.load(it) }
        val source = try {
            ManifestIO.read(manifestPath).sourceVideo?.let { File(it) }
        } catch (t: Throwable) {
            postEffect(AdjustVideoViewEffect.ShowError(t.message ?: "Could not read the project."))
            null
        }
        if (source != null && !source.exists()) {
            postEffect(AdjustVideoViewEffect.ShowError("Project source video is missing."))
        }
        updateState { it.copy(adjustments = adjustments.get(), sourceVideo = source?.takeIf { file -> file.exists() }) }
        countedColors = colorsOf(state.editing)
    }

    private fun updateState(reducer: (AdjustVideoViewState) -> AdjustVideoViewState) {
        state = reducer(state)
        render()
    }

    private fun render() {
        val snapshot = state
        val target = view ?: return
        if (EventQueue.isDispatchThread()) {
            target.render(snapshot)
        } else {
            EventQueue.invokeLater { view?.render(snapshot) }
        }
    }

    private fun postEffect(effect: AdjustVideoViewEffect) {
        if (EventQueue.isDispatchThread()) {
            view?.renderEffect(effect)
        } else {
            EventQueue.invokeLater { view?.renderEffect(effect) }
        }
    }

    companion object {
        /** The analytics keep the keys of the two older tabs, so the counts of the sub-tabs stay comparable. */
        fun analyticsTab(section: AdjustSection): AnalyticsEvent.Tab = when (section) {
            AdjustSection.COLORS -> AnalyticsEvent.Tab.COLORS
            AdjustSection.TRANSFORM -> AnalyticsEvent.Tab.CROP_ROTATE
        }

        private fun colorsOf(adjustments: AdjustmentsV1): List<Float> = listOf(
            adjustments.brightness,
            adjustments.contrast,
            adjustments.saturation,
            adjustments.shadows,
            adjustments.highlights,
            adjustments.whiteBalance?.temperature ?: 0.0f,
        )
    }
}
