package org.litvin.ui.tabs.points

import org.litvin.points.CommentAnimation
import org.litvin.points.CommentStyle
import org.litvin.scoring.ClipOutcome
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

    fun createPointAt(ms: Long)
    fun editPoint(id: String, patch: PointPatch)
    fun deletePoint(id: String)
    fun toggleFavorite(id: String)

    /** The outcome of the point with [id] for the Edit dialog in advanced mode (B-68), or null in basic mode. */
    fun outcomeForm(id: String): PointOutcomeForm? = null

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

    /** Moves the replay [replayIndex] of the point with [id] by [delta] places: -1 plays it earlier, 1 later (B-50). */
    fun moveReplay(id: String, replayIndex: Int, delta: Int) {}

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
    val animation: CommentAnimation = CommentAnimation.NONE,
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
    /** The zoom as a factor, for example 2.0 for 2×, or null for no zoom. */
    val zoomFactor: Double?,
    val muted: Boolean,
    /** The time of the still frame, or null for no still frame (decision 22). */
    val freezeMs: Long? = null,
    /** The number of the comments of the replay (decision 24). */
    val commentCount: Int = 0,
    /** The time that the replay adds to the video. */
    val addedMs: Long,
)

/** Partial update to a point. Fields set to null are not modified. */
data class PointPatch(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val label: String? = null,
    val flags: Set<String>? = null,
    val favorite: Boolean? = null,
    /** The new outcome from the Edit dialog in advanced mode (B-68), or null for no change. */
    val outcome: ClipOutcome? = null,
)

/**
 * The outcome of one clip for the Edit dialog in advanced mode (B-68, "Corrections").
 * - [serve] is the serve number (1 or 2) from the pack, or null for a W clip. The dialog shows it, read only.
 * - [doubleFault] is true for the second fault of a pack.
 * - [server] (1, 2, or null) is the server of the clip. An ace goes to the server.
 * - [zeroLength] is true for a fault clip of zero length. Only a fault clip can have zero length.
 */
data class PointOutcomeForm(
    val current: ClipOutcome,
    val serve: Int?,
    val doubleFault: Boolean,
    val server: Int?,
    val p1Name: String,
    val p2Name: String,
    val zeroLength: Boolean,
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
    val animation: CommentAnimation? = null,
)

/** Autosave status surfaced to the toolbar and other indicators. */
data class AutosaveState(
    val pending: Boolean,
    val lastSavedAtMs: Long?,
)
