package org.litvin.points

import org.litvin.i18n.tr

/**
 * The limits of the instant replays of a point (B-50), and the rules that keep a replay inside them.
 * See `docs/backlog.md` (B-50) and `docs/replay/b-50-tasks.md`.
 */
object Replays {
    /** A replay part can start this much before the point start and end this much after the point end. */
    const val MARGIN_MS = 3_000

    /** A shorter part is removed. */
    const val MIN_PART_MS = 500

    const val MIN_SPEED = 0.1
    const val MAX_SPEED = 1.0
    const val DEFAULT_SPEED = 0.5

    /** The speeds that the replay editor offers (decision 15). */
    val SPEEDS = listOf(0.1, 0.2, 0.3, 0.5, 1.0)

    /** The narrowest zoom frame, a zoom of 4×. */
    const val MIN_ZOOM_WIDTH = 0.25

    /** A zoom frame this wide or wider is no zoom. */
    private const val NO_ZOOM_WIDTH = 0.999

    /** The still frame durations that the replay editor offers (decision 22). */
    val FREEZE_DURATIONS_MS = listOf(500, 800, 1_000, 1_500, 2_000, 3_000)
    const val DEFAULT_FREEZE_MS = 1_000
    const val MIN_FREEZE_MS = 100
    const val MAX_FREEZE_MS = 10_000

    /** The longest replay label. */
    const val MAX_LABEL_LENGTH = 40

    /** A new replay starts this much before the playhead and ends this much after it (decision of 2026-10-26). */
    const val NEW_PART_LEAD_MS = 1_500

    /** The earliest start and the latest end of a replay part of [point]. */
    fun windowStartMs(point: PointV1): Int = (point.startMs - MARGIN_MS).coerceAtLeast(0)

    fun windowEndMs(point: PointV1): Int = point.endMs + MARGIN_MS

    /**
     * A new replay of [point] around [playheadMs], with the default values. The part keeps its length of 3 s
     * when the playhead is near the edge of the window. Returns null when the window has no room for a part.
     */
    fun newAt(point: PointV1, playheadMs: Long): ReplayV1? {
        val windowStart = windowStartMs(point)
        val windowEnd = windowEndMs(point)
        val length = NEW_PART_LEAD_MS * 2
        val latestStart = (windowEnd - length).coerceAtLeast(windowStart)
        val start = (playheadMs - NEW_PART_LEAD_MS).coerceIn(windowStart.toLong(), latestStart.toLong()).toInt()
        val end = (start + length).coerceAtMost(windowEnd)
        if (end - start < MIN_PART_MS) return null
        return ReplayV1(startMs = start, endMs = end)
    }

    /**
     * The replays of [point] after a change of the point (decision 6): each part is cut to the window of the point,
     * a part shorter than [MIN_PART_MS] is removed, and the values go into their limits. The replays keep their
     * order, because the user sets the play order (decision 14).
     */
    fun fitTo(point: PointV1): List<ReplayV1> {
        if (point.replays.isEmpty()) return point.replays
        val windowStart = windowStartMs(point)
        val windowEnd = windowEndMs(point)
        return point.replays
            .mapNotNull { replay ->
                val start = replay.startMs.coerceAtLeast(windowStart)
                val end = replay.endMs.coerceAtMost(windowEnd)
                if (end - start < MIN_PART_MS) null else normalizeValues(replay.copy(startMs = start, endMs = end))
            }
    }

    /** [point] with its replays fitted to it. The same object when nothing changes. */
    fun fitted(point: PointV1): PointV1 {
        val replays = fitTo(point)
        return if (replays == point.replays) point else point.copy(replays = replays)
    }

    /** Puts the speed, the zoom frame, and the still frame into their limits. The still frame stays inside the part. */
    internal fun normalizeValues(replay: ReplayV1): ReplayV1 {
        val speed = if (replay.speed.isFinite()) replay.speed.coerceIn(MIN_SPEED, MAX_SPEED) else DEFAULT_SPEED
        val zoom = normalizeZoom(replay.zoom)
        val freeze = replay.freeze?.let {
            ReplayFreeze(
                atMs = it.atMs.coerceIn(replay.startMs, replay.endMs.coerceAtLeast(replay.startMs)),
                durationMs = it.durationMs.coerceIn(MIN_FREEZE_MS, MAX_FREEZE_MS),
            )
        }
        val comments = normalizeComments(replay)
        return if (speed == replay.speed && zoom == replay.zoom && freeze == replay.freeze && comments == replay.comments) {
            replay
        } else {
            replay.copy(speed = speed, zoom = zoom, freeze = freeze, comments = comments)
        }
    }

    /** The comments of [replay] with each start inside the part and each time in the limits (decision 24). */
    private fun normalizeComments(replay: ReplayV1): List<CommentV1> {
        if (replay.comments.isEmpty()) return replay.comments
        val end = replay.endMs.coerceAtLeast(replay.startMs)
        val result = replay.comments.map { comment ->
            val start = comment.startMs.coerceIn(replay.startMs, end)
            val duration = comment.durationMs.coerceIn(MIN_COMMENT_MS, MAX_COMMENT_MS)
            if (start == comment.startMs && duration == comment.durationMs) comment else comment.copy(startMs = start, durationMs = duration)
        }
        return if (result == replay.comments) replay.comments else result
    }

    // ---- Replay comments (decision 24) ----

    /** The least time of a replay comment. */
    const val MIN_COMMENT_MS = 100

    /** The most time of a replay comment. */
    const val MAX_COMMENT_MS = 60_000

    /** The time from the play start when [comment] of [replay] starts to show. */
    fun commentPlayStartMs(replay: ReplayV1, comment: CommentV1): Long = playOffsetAt(replay, comment.startMs.toLong())

    /** The time from the play start when [comment] of [replay] stops to show. The comment stops at the replay end. */
    fun commentPlayEndMs(replay: ReplayV1, comment: CommentV1): Long =
        (commentPlayStartMs(replay, comment) + comment.durationMs).coerceAtMost(playMs(replay))

    /** The source time of the frame when [comment] of [replay] stops to show. The editor bar uses it. */
    fun commentSourceEndMs(replay: ReplayV1, comment: CommentV1): Long = sourceAt(replay, commentPlayEndMs(replay, comment))

    /** The comments of [replay] on screen at [playOffsetMs] from the play start. */
    fun commentsAt(replay: ReplayV1, playOffsetMs: Long): List<CommentV1> = replay.comments.filter { comment ->
        comment.text.isNotBlank() &&
            commentPlayStartMs(replay, comment) <= playOffsetMs && playOffsetMs < commentPlayEndMs(replay, comment)
    }

    /**
     * The comments of [replay] on screen at the frame at [sourceMs], for an editor that is paused on that frame. On the
     * frame of the still frame, it uses the start of the still frame.
     */
    fun commentsAtSource(replay: ReplayV1, sourceMs: Long): List<CommentV1> =
        if (sourceMs < replay.startMs || sourceMs > replay.endMs) emptyList() else commentsAt(replay, playOffsetAt(replay, sourceMs))

    /** The id for a new comment of [replay]. */
    fun nextCommentId(replay: ReplayV1): Int = (replay.comments.maxOfOrNull { it.id } ?: 0) + 1

    /**
     * A zoom frame inside the frame. A frame that is too small gets [MIN_ZOOM_WIDTH] around its center.
     * A frame that is not a number, or that is as wide as the frame, gives null (no zoom).
     */
    internal fun normalizeZoom(zoom: ReplayZoom?): ReplayZoom? {
        if (zoom == null || !zoom.x.isFinite() || !zoom.y.isFinite() || !zoom.width.isFinite()) return null
        if (zoom.width >= NO_ZOOM_WIDTH) return null
        val width = zoom.width.coerceAtLeast(MIN_ZOOM_WIDTH)
        val grow = (width - zoom.width) / 2
        val x = (zoom.x - grow).coerceIn(0.0, 1.0 - width)
        val y = (zoom.y - grow).coerceIn(0.0, 1.0 - width)
        val result = ReplayZoom(x, y, width)
        return if (result == zoom) zoom else result
    }

    /** The default label of the replay at [index] of its point: "Replay", "Replay 2", "Replay 3", and so on. */
    fun defaultLabel(index: Int): String = tr("replay.label", "number" to index + 1)

    /** The label of the replay at [index] of its point: the label of the user, or the default label for the index. */
    fun label(replay: ReplayV1, index: Int, defaults: (Int) -> String = ::defaultLabel): String =
        replay.label?.takeIf { it.isNotBlank() } ?: defaults(index)

    /** The badge text of the replay at [index] of its point: the label in capital letters, as the badge style. */
    fun badgeText(replay: ReplayV1, index: Int, defaults: (Int) -> String = ::defaultLabel): String =
        label(replay, index, defaults).uppercase()

    /**
     * The label to keep for the [text] that the user typed for the replay at [index]. A blank text and the default
     * label give null, so that the label follows the place of the replay. The text is cut to [MAX_LABEL_LENGTH].
     */
    fun labelOf(text: String, index: Int, defaults: (Int) -> String = ::defaultLabel): String? {
        val label = text.trim().take(MAX_LABEL_LENGTH).trim()
        return label.takeUnless { it.isEmpty() || it == defaults(index) }
    }

    /** The time that the part moves: the part length divided by the speed, without the still frame. */
    fun motionMs(replay: ReplayV1): Long = Math.round((replay.endMs - replay.startMs).coerceAtLeast(0) / replay.speed)

    /** The time of the still frame of the replay, or 0 without a still frame. */
    fun freezeMs(replay: ReplayV1): Long = replay.freeze?.durationMs?.toLong() ?: 0L

    /** The play time of the replay: the [motionMs] and the still frame. */
    fun playMs(replay: ReplayV1): Long = motionMs(replay) + freezeMs(replay)

    /** The time that the replay adds to the video: its [playMs], because the replay plays once. */
    fun addedMs(replay: ReplayV1): Long = playMs(replay)

    /** The time from the play start to the start of the still frame, or null without a still frame. */
    fun freezeOffsetMs(replay: ReplayV1): Long? =
        replay.freeze?.let { Math.round((it.atMs - replay.startMs).coerceAtLeast(0) / replay.speed).coerceAtMost(motionMs(replay)) }

    /**
     * The time from the play start of the frame at [sourceMs] of the part. A frame after the still frame comes later by
     * the still frame. The frame of the still frame is at the start of the still frame.
     */
    fun playOffsetAt(replay: ReplayV1, sourceMs: Long): Long {
        val offset = (sourceMs - replay.startMs).coerceIn(0L, (replay.endMs - replay.startMs).coerceAtLeast(0).toLong())
        val motion = Math.round(offset / replay.speed).coerceAtMost(motionMs(replay))
        val freezeAt = freezeOffsetMs(replay) ?: return motion
        return if (motion > freezeAt) motion + freezeMs(replay) else motion
    }

    /** The source time of the frame at [playOffsetMs] from the play start. During the still frame, it is the still frame. */
    fun sourceAt(replay: ReplayV1, playOffsetMs: Long): Long {
        val offset = playOffsetMs.coerceIn(0L, playMs(replay))
        val freezeAt = freezeOffsetMs(replay)
        val motion = when {
            freezeAt == null || offset <= freezeAt -> offset
            offset < freezeAt + freezeMs(replay) -> freezeAt
            else -> offset - freezeMs(replay)
        }
        return Math.round(replay.startMs + motion * replay.speed).coerceAtMost(replay.endMs.toLong())
    }

    /** The dip to black or white at the cut into a replay and out of it: this time out, then this time in (decision 2). */
    const val CUT_FADE_MS = 250L

    /**
     * The zoom in at the start of the first replay of a point (decisions 8 and 18). The replay has no zoom out
     * (decision 12).
     */
    const val ZOOM_MS = 400L

    /**
     * True when the replay at [replayIndex] of its point zooms in. Only the first replay zooms in. The next replays
     * start with the zoom already in (decision 18).
     */
    fun zoomsIn(replayIndex: Int): Boolean = replayIndex == 0
}
