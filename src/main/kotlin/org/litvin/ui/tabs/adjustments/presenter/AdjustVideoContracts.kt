package org.litvin.ui.tabs.adjustments.presenter

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.ColorTimeline
import org.litvin.adjustments.GeometryTimeline
import org.litvin.adjustments.TrimRange
import org.litvin.points.PointV1
import org.litvin.ui.tabs.adjustments.TrimEnd
import org.litvin.ui.commons.ChangePointsState
import java.io.File

interface AdjustVideoView {
    fun render(state: AdjustVideoViewState)
    fun renderEffect(effect: AdjustVideoViewEffect) {}
}

interface AdjustVideoPresenter {
    fun attach(view: AdjustVideoView)
    fun detach()
    fun onActivated()
    fun onDeactivated()
    fun onIntent(intent: AdjustVideoIntent)
}

/** The sub-tabs of the Colors & Transform tab, in their order. The tab opens on the first one. */
enum class AdjustSection { TRIM, COLORS, TRANSFORM }

/**
 * [adjustments] are the stored adjustments with all change points (B-47). [playheadMs] selects the active
 * color change point and the active transform change point: the controls, the crop editor, and the preview show
 * their values without the blend. [section] is the open sub-tab of the right panel.
 */
data class AdjustVideoViewState(
    val adjustments: AdjustmentsV1 = AdjustmentsV1(),
    val sourceVideo: File? = null,
    val playheadMs: Long = 0L,
    val section: AdjustSection = AdjustSection.TRIM,
    /** The points of the project. The Trim sub-tab shows them and warns about the points outside the trim (B-62). */
    val points: List<PointV1> = emptyList(),
) {
    /** The points that the trim cuts off fully or in part. */
    val pointsOutsideTrim: List<PointV1>
        get() = TrimRange.outside(adjustments, points, { it.startMs.toLong() }, { it.endMs.toLong() })

    /** The colors and the transform that the controls edit, without change points. */
    val editing: AdjustmentsV1
        get() = GeometryTimeline.editingAt(ColorTimeline.editingAt(adjustments, playheadMs), playheadMs)

    val colorChangePoints: ChangePointsState
        get() = ChangePointsState(adjustments.colorKeys.map { it.atMs }, playheadMs)

    val transformChangePoints: ChangePointsState
        get() = ChangePointsState(adjustments.geometryKeys.map { it.atMs }, playheadMs)

    /**
     * The change points of the open sub-tab. The Colors and Transform sub-tabs have separate change points.
     * The trim has no change points.
     */
    val changePoints: ChangePointsState
        get() = when (section) {
            AdjustSection.COLORS -> colorChangePoints
            AdjustSection.TRANSFORM -> transformChangePoints
            AdjustSection.TRIM -> ChangePointsState(emptyList(), playheadMs)
        }

    /** The marks of the playback bar: the change points, or on the Trim sub-tab the trim start and end. */
    val changePointTimes: List<Long>
        get() = if (section == AdjustSection.TRIM) {
            listOfNotNull(adjustments.trimStartMs, adjustments.trimEndMs)
        } else {
            changePoints.times
        }

    val canAddChangePoint: Boolean get() = changePoints.canAdd
    val canRemoveChangePoint: Boolean get() = changePoints.canRemove
    val previousChangePointMs: Long? get() = changePoints.previousMs
    val nextChangePointMs: Long? get() = changePoints.nextMs
}

sealed class AdjustVideoIntent {
    data class LoadProject(val manifestPath: String) : AdjustVideoIntent()
    data class SelectSection(val section: AdjustSection) : AdjustVideoIntent()

    /** New color values from the sliders. [final] is false while the user drags a slider. */
    data class ChangeColors(val colors: AdjustmentsV1, val final: Boolean = true) : AdjustVideoIntent()
    data object ResetColors : AdjustVideoIntent()
    data class ChangeTransform(val adjustments: AdjustmentsV1) : AdjustVideoIntent()
    data object ResetTransform : AdjustVideoIntent()

    /**
     * The size of the source video in pixels. The presenter converts the geometry of an older project with it,
     * and keeps a rotation free of black corners ([org.litvin.adjustments.CropGeometryMath.keepCornersOut]).
     */
    data class VideoSizeKnown(val width: Int, val height: Int) : AdjustVideoIntent()

    /** The fill color outside the rotated video, as "#RRGGBB", or null for black. It applies to the whole video. */
    data class ChangeBackground(val color: String?) : AdjustVideoIntent()
    data class PlayheadMoved(val ms: Long) : AdjustVideoIntent()

    /** Sets [end] of the trim (B-62) to the playhead. [durationMs] is the length of the source. */
    data class SetTrim(val end: TrimEnd, val durationMs: Long) : AdjustVideoIntent()

    /** Seeks to [end] of the trim. [durationMs] is the length of the source. */
    data class GoToTrim(val end: TrimEnd, val durationMs: Long) : AdjustVideoIntent()
    data object ResetTrim : AdjustVideoIntent()

    // The change point intents work on the change points of the open sub-tab.
    data object AddChangePoint : AdjustVideoIntent()
    data object RemoveChangePoint : AdjustVideoIntent()
    data object GoToPreviousChangePoint : AdjustVideoIntent()
    data object GoToNextChangePoint : AdjustVideoIntent()
}

sealed class AdjustVideoViewEffect {
    data class ShowError(val message: String) : AdjustVideoViewEffect()
    data class SeekTo(val ms: Long) : AdjustVideoViewEffect()

    /** The first time in a project that the trim cuts off points: a dialog tells it (B-62). */
    data class TrimCutsPoints(val count: Int) : AdjustVideoViewEffect()
}
