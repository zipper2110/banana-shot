package org.litvin.ui.tabs.points

import org.litvin.points.CommentStyle
import org.litvin.ui.commons.CommentInput

/**
 * Contracts for the Points tab UI and its container.
 *
 * These APIs are intentionally narrow and decoupled from domain types.
 * Leaf Swing components should depend only on these contracts and UI commons.
 */
interface PointsActions {
    fun togglePlayPause()
    fun seekTo(ms: Long)
    fun jumpToSelected()
    fun setStartAtPlayhead()
    fun setEndAtPlayhead()

    fun createPointAt(ms: Long)
    fun editPoint(id: String, patch: PointPatch)
    fun deletePoint(id: String)
    fun toggleFavorite(id: String)

    fun addCommentAtPlayhead() {}
    fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) {}
    fun editComment(id: Int, patch: CommentPatch) {}

    /**
     * Shows [comment] on the video while the comment dialog is open, before Save.
     * [id] is the comment that the dialog changes, or null for a new comment. A null [comment] stops the preview.
     */
    fun previewComment(id: Int?, comment: CommentInput?) {}
    fun deleteComment(id: Int) {}

    /** Opens the replay editor for the point with [id] (B-50), on the replay [replayIndex] when it is not null. */
    fun openReplays(id: String, replayIndex: Int? = null) {}

    /** Removes the replay [replayIndex] of the point with [id] (B-50). */
    fun deleteReplay(id: String, replayIndex: Int) {}

    /**
     * Selects an item by its visual index (index within the composed view list).
     */
    fun selectByVisualIndex(index: Int)

    fun saveNow()
}

/** Immutable snapshot of the Points view state consumed by leaf components. */
data class PointsViewState(
    val isPlaying: Boolean,
    val currentTimeMs: Long,
    val selectedVisualIndex: Int?,
    val pendingDraftStartMs: Long?,
    val events: List<TimelineEventDto>,
    val autosave: AutosaveState,
)

sealed interface TimelineEventDto {
    val startMs: Long
    val stableKey: String
}

data class PointEventDto(
    val point: PointDto,
) : TimelineEventDto {
    override val startMs: Long get() = point.startMs
    override val stableKey: String get() = "point:${point.id}"
}

data class CommentDto(
    val id: Int,
    override val startMs: Long,
    val durationMs: Long,
    val text: String,
    val colorHex: String,
    val style: CommentStyle = CommentStyle.OUTLINE,
    val sizePercent: Int = 100,
    val opacityPercent: Int = 100,
    val rotationDeg: Int = 0,
) : TimelineEventDto {
    override val stableKey: String get() = "comment:$id"
}

/** Lightweight DTO for a point shown in UI components. */
data class PointDto(
    val id: String,
    val startMs: Long,
    val endMs: Long?,
    val label: String?,
    val flags: Set<String> = emptySet(),
    val favorite: Boolean = false,
    /** The instant replays of the point (B-50). The list shows each one as a sub-row under the point. */
    val replays: List<ReplayRowDto> = emptyList(),
)

/** One instant replay of a point, for its sub-row in the list (B-50). */
data class ReplayRowDto(
    /** The index of the replay in its point, from 0. */
    val index: Int,
    val speed: Double,
    val repeats: Int,
    /** The zoom as a factor, for example 2.0 for 2×, or null for no zoom. */
    val zoomFactor: Double?,
    val muted: Boolean,
    /** The time that the replay adds to the video, with all its repeats. */
    val addedMs: Long,
)

/** Partial update to a point. Fields set to null are not modified. */
data class PointPatch(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val label: String? = null,
    val flags: Set<String>? = null,
    val favorite: Boolean? = null,
)

/** Partial update to a comment. Fields set to null are not modified. */
data class CommentPatch(
    val startMs: Long? = null,
    val durationMs: Long? = null,
    val text: String? = null,
    val colorHex: String? = null,
    val style: CommentStyle? = null,
    val sizePercent: Int? = null,
    val opacityPercent: Int? = null,
    val rotationDeg: Int? = null,
)

/** Autosave status surfaced to the toolbar and other indicators. */
data class AutosaveState(
    val pending: Boolean,
    val lastSavedAtMs: Long?,
)
