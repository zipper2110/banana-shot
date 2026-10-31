package org.litvin.ui.tabs.adjustments.presenter

import org.litvin.ui.commons.ErrorTexts
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.ColorTimeline
import org.litvin.adjustments.CropGeometryMath
import org.litvin.adjustments.GeometryTimeline
import org.litvin.adjustments.TrimRange
import org.litvin.points.EdlIO
import org.litvin.points.PointV1
import org.litvin.ui.tabs.adjustments.TrimEnd
import org.litvin.analytics.Analytics
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.DisabledAnalytics
import org.litvin.projects.ManifestIO
import org.litvin.ui.commons.ChangePointsState
import java.awt.EventQueue
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.litvin.i18n.tr

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
        // The Points tab can change the points between two visits, so read them again.
        updateState { it.copy(adjustments = adjustments.get(), points = readPoints()) }
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
            is AdjustVideoIntent.SetTrim -> setTrim(intent.end, state.playheadMs, intent.durationMs)
            is AdjustVideoIntent.SetTrimAt -> setTrim(intent.end, intent.ms, intent.durationMs)
            is AdjustVideoIntent.GoToTrim -> {
                if (intent.durationMs <= 0) return
                val range = TrimRange.of(adjustments.get(), intent.durationMs)
                // The end is just after the last frame of the trimmed video, so the seek goes just before it.
                seekTo(if (intent.end == TrimEnd.START) range.startMs else (range.endMs - 1).coerceAtLeast(range.startMs))
            }
            AdjustVideoIntent.ResetTrim -> adjustments.set { it.copy(trimStartMs = null, trimEndMs = null) }
            AdjustVideoIntent.AddChangePoint -> {
                if (!state.canAddChangePoint) return
                val at = state.playheadMs
                when (state.section) {
                    AdjustSection.COLORS -> changeKeys { previous -> ColorTimeline.withKeyAdded(previous, at) }
                    AdjustSection.TRANSFORM -> changeKeys { previous -> GeometryTimeline.withKeyAdded(previous, at) }
                    AdjustSection.TRIM -> Unit
                }
            }
            AdjustVideoIntent.RemoveChangePoint -> {
                val at = state.playheadMs
                when (state.section) {
                    AdjustSection.COLORS -> changeKeys { previous -> ColorTimeline.withKeyRemoved(previous, at) }
                    AdjustSection.TRANSFORM -> changeKeys { previous -> GeometryTimeline.withKeyRemoved(previous, at) }
                    AdjustSection.TRIM -> Unit
                }
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
        analyticsTab(section)?.let { analytics.record(AnalyticsEvent.TabShown(it, byUser = true)) }
    }

    /**
     * Sets [end] of the trim to [ms]. A time near the source start or end clears that end. The trimmed
     * video keeps at least [TrimRange.MIN_LENGTH_MS]. The trim can cut off points: the user decides, and the app
     * keeps the points. The first time in a project, a dialog tells it (B-62).
     */
    private fun setTrim(end: TrimEnd, ms: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val at = ms.coerceIn(0L, durationMs)
        val range = TrimRange.of(adjustments.get(), durationMs)
        val fits = when (end) {
            TrimEnd.START -> range.endMs - at >= TrimRange.MIN_LENGTH_MS
            TrimEnd.END -> at - range.startMs >= TrimRange.MIN_LENGTH_MS
        }
        if (!fits) {
            postEffect(AdjustVideoViewEffect.ShowError(tr("adjust.trim.error.order")))
            return
        }
        adjustments.set { previous ->
            when (end) {
                TrimEnd.START -> previous.copy(trimStartMs = at.takeIf { it >= SNAP_MS })
                TrimEnd.END -> previous.copy(trimEndMs = at.takeIf { durationMs - it >= SNAP_MS })
            }
        }
        val trimmed = adjustments.get()
        updateState { it.copy(adjustments = trimmed) }
        val outside = state.pointsOutsideTrim.size
        if (outside > 0 && !trimmed.trimWarningShown) {
            adjustments.set { it.copy(trimWarningShown = true) }
            postEffect(AdjustVideoViewEffect.TrimCutsPoints(outside))
        }
    }

    /** The points of the open project, or none when the file cannot be read. */
    private fun readPoints(): List<PointV1> {
        val dir = projectDir ?: return emptyList()
        return try {
            EdlIO.readForProjectDir(dir).points
        } catch (_: Throwable) {
            emptyList()
        }
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

    /**
     * Keeps the playhead. The view renders again only when the controls must show other values or buttons. The
     * change points of the two sub-tabs count: the preview shows the colors and the transform together.
     */
    private fun movePlayhead(ms: Long) {
        val before = state
        val after = before.copy(playheadMs = ms.coerceAtLeast(0L))
        state = after
        val changed = changed(before.colorChangePoints, after.colorChangePoints) ||
            changed(before.transformChangePoints, after.transformChangePoints)
        if (changed) {
            countedColors = colorsOf(after.editing)
            render()
        }
    }

    private fun changed(before: ChangePointsState, after: ChangePointsState): Boolean =
        after.activeIndex != before.activeIndex ||
            after.canAdd != before.canAdd ||
            after.previousMs != before.previousMs ||
            after.nextMs != before.nextMs

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
            postEffect(AdjustVideoViewEffect.ShowError(ErrorTexts.of(t, tr("adjust.error.read"))))
            null
        }
        if (source != null && !source.exists()) {
            postEffect(AdjustVideoViewEffect.ShowError(tr("adjust.error.videoMissing")))
        }
        updateState {
            it.copy(
                adjustments = adjustments.get(),
                sourceVideo = source?.takeIf { file -> file.exists() },
                points = readPoints(),
            )
        }
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
        /**
         * The analytics keep the keys of the two older tabs, so the counts of the sub-tabs stay comparable.
         * The analytics contract has no key for the Trim sub-tab, so it gives null (B-62).
         */
        fun analyticsTab(section: AdjustSection): AnalyticsEvent.Tab? = when (section) {
            AdjustSection.COLORS -> AnalyticsEvent.Tab.COLORS
            AdjustSection.TRANSFORM -> AnalyticsEvent.Tab.CROP_ROTATE
            AdjustSection.TRIM -> null
        }

        /** A trim time nearer than this to the source start or end is the source start or end. */
        private const val SNAP_MS = 100L

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
