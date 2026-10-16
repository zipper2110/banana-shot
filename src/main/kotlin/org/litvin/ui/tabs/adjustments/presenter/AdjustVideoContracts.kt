package org.litvin.ui.tabs.adjustments.presenter

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.ChangePoints
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

/** The sub-tabs of the right panel of the Colors & Transform tab. */
enum class AdjustSection { COLORS, TRANSFORM }

/**
 * [adjustments] are the stored adjustments with all change points (B-47). [playheadMs] selects the active
 * change point: the controls, the crop editor, and the preview show its values without the blend.
 * [section] is the open sub-tab of the right panel.
 */
data class AdjustVideoViewState(
    val adjustments: AdjustmentsV1 = AdjustmentsV1(),
    val sourceVideo: File? = null,
    val playheadMs: Long = 0L,
    val section: AdjustSection = AdjustSection.COLORS,
) {
    /** The colors and the transform that the controls edit, without change points. */
    val editing: AdjustmentsV1 get() = ChangePoints.editingAt(adjustments, playheadMs)

    /** The change points do not depend on the sub-tab: each one has the colors and the transform. */
    val changePointTimes: List<Long> get() = ChangePoints.times(adjustments)

    val changePoints: ChangePointsState get() = ChangePointsState(changePointTimes, playheadMs)

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
    data class PlayheadMoved(val ms: Long) : AdjustVideoIntent()
    data object AddChangePoint : AdjustVideoIntent()
    data object RemoveChangePoint : AdjustVideoIntent()
    data object GoToPreviousChangePoint : AdjustVideoIntent()
    data object GoToNextChangePoint : AdjustVideoIntent()
}

sealed class AdjustVideoViewEffect {
    data class ShowError(val message: String) : AdjustVideoViewEffect()
    data class SeekTo(val ms: Long) : AdjustVideoViewEffect()
}
