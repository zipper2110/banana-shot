package org.litvin.export

import com.fasterxml.jackson.annotation.JsonIgnore
import org.litvin.CommentOverlaySpan
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.points.Replays

/** One replay of an export (B-50 T6). The export plays it once after the end of its point. */
data class ExportReplay(
    val pointId: String,
    val pointEndMs: Int,
    val replay: ReplayV1,
    /** The comments over one play, in milliseconds from the start of the play (decision 9). */
    val comments: List<CommentOverlaySpan> = emptyList(),
    /** The text of the "REPLAY" badge in the language of the video. Null shows no badge. */
    val badgeText: String? = null,
    /** The place of the replay among the replays of its point, from 0. Only the first one zooms in (decision 18). */
    val replayIndex: Int = 0,
) {
    /** The time of one play. */
    @get:JsonIgnore
    val playMs: Long get() = Replays.playMs(replay)

    /** The time of all plays. */
    @get:JsonIgnore
    val totalMs: Long get() = Replays.addedMs(replay)
}

/** A dip to black or to white at the start or the end of a pass. */
data class PassFade(val white: Boolean, val durationMs: Long)

object ExportReplays {
    /**
     * The replays of [points], in output order. [comments] are the comments of the project. [includeComments] false is
     * an export without comments: no point comments and no replay comments. [defaultLabel] gives the default badge label
     * for the place of a replay in its point.
     */
    fun of(
        points: List<PointV1>,
        comments: List<CommentV1>,
        includeComments: Boolean = true,
        defaultLabel: (Int) -> String = Replays::defaultLabel,
    ): List<ExportReplay> =
        points
            .filter { it.replays.isNotEmpty() }
            .sortedBy { it.endMs }
            .flatMap { point ->
                Replays.fitTo(point).mapIndexed { index, replay ->
                    ExportReplay(
                        pointId = point.id,
                        pointEndMs = point.endMs,
                        replay = replay,
                        replayIndex = index,
                        comments = if (includeComments) commentSpans(replay, comments) else emptyList(),
                        badgeText = Replays.badgeText(replay, index, defaultLabel).takeIf { replay.showBadge },
                    )
                }
            }

    /**
     * The comments over one play of [replay], in milliseconds from the play start.
     *
     * The point comments ([comments]) show when [ReplayV1.showPointComments] is on. They are the comments that are
     * visible during the part in the source video. Each one shows at the same moment of the play, and its time gets
     * longer by the slow speed (decision 9). A comment that shows at the still frame also shows during the still frame
     * (decision 22).
     *
     * The replay comments always show (decision 24). Each one starts at the moment of play of its source time and
     * shows for its time, until the replay end. They draw over the point comments.
     */
    fun commentSpans(replay: ReplayV1, comments: List<CommentV1>): List<CommentOverlaySpan> {
        val point = if (replay.showPointComments) pointCommentSpans(replay, comments) else emptyList()
        return (point + replayCommentSpans(replay))
            .sortedWith(compareBy<CommentOverlaySpan> { it.startMs }.thenBy { it.id })
    }

    /** The ids of the replay comments in the spans start here, so that they sort after the point comments. */
    const val REPLAY_COMMENT_ID_BASE = 1_000_000

    private fun replayCommentSpans(replay: ReplayV1): List<CommentOverlaySpan> = replay.comments
        .filter { it.id > 0 && it.durationMs > 0 && it.text.isNotBlank() }
        .mapNotNull { comment ->
            val color = EdlIO.normalizeColorHex(comment.colorHex) ?: return@mapNotNull null
            val start = Replays.commentPlayStartMs(replay, comment)
            val end = Replays.commentPlayEndMs(replay, comment)
            if (end <= start) return@mapNotNull null
            CommentOverlaySpan(
                id = REPLAY_COMMENT_ID_BASE + comment.id,
                startMs = start,
                endMs = end,
                text = comment.text,
                colorHex = color,
                style = comment.style,
                position = comment.position,
                sizePercent = comment.sizePercent,
                opacityPercent = comment.opacityPercent,
                rotationDeg = comment.rotationDeg,
            )
        }

    private fun pointCommentSpans(replay: ReplayV1, comments: List<CommentV1>): List<CommentOverlaySpan> {
        val partStart = replay.startMs.toLong()
        val partEnd = replay.endMs.toLong()
        return comments
            .asSequence()
            .filter { it.id > 0 && it.durationMs > 0 && it.text.isNotBlank() }
            .mapNotNull { comment ->
                val color = EdlIO.normalizeColorHex(comment.colorHex) ?: return@mapNotNull null
                val start = maxOf(comment.startMs.toLong(), partStart)
                val end = minOf(comment.startMs.toLong() + comment.durationMs, partEnd)
                if (end <= start) return@mapNotNull null
                CommentOverlaySpan(
                    id = comment.id,
                    startMs = Replays.playOffsetAt(replay, start),
                    endMs = Replays.playOffsetAt(replay, end),
                    text = comment.text,
                    colorHex = color,
                    style = comment.style,
                    position = comment.position,
                    sizePercent = comment.sizePercent,
                    opacityPercent = comment.opacityPercent,
                    rotationDeg = comment.rotationDeg,
                )
            }
            .sortedWith(compareBy<CommentOverlaySpan> { it.startMs }.thenBy { it.id })
            .toList()
    }

    /** The time that [replays] add to the video. */
    fun addedMs(replays: List<ExportReplay>): Long = replays.sumOf { it.totalMs }

    /** The dip of a cut into a replay or out of it (decision 2). Cut has no dip. */
    fun cutFade(effect: ReplayEffect): PassFade? = when (effect) {
        ReplayEffect.FADE -> PassFade(white = false, durationMs = Replays.CUT_FADE_MS)
        ReplayEffect.FLASH -> PassFade(white = true, durationMs = Replays.CUT_FADE_MS)
        ReplayEffect.CUT -> null
    }

}
