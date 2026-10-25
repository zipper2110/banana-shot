package org.litvin.export

import com.fasterxml.jackson.annotation.JsonIgnore
import org.litvin.CommentOverlaySpan
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.points.Replays
import kotlin.math.roundToLong

/** One replay of an export (B-50 T6). The export plays it [ReplayV1.repeats] times after the end of its point. */
data class ExportReplay(
    val pointId: String,
    val pointEndMs: Int,
    val replay: ReplayV1,
    /** The comments over one play, in milliseconds from the start of the play (decision 9). */
    val comments: List<CommentOverlaySpan> = emptyList(),
    /** The text of the "REPLAY" badge in the language of the video. Null shows no badge. */
    val badgeText: String? = null,
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
     * The replays of [points], in output order. [comments] are the comments of the project, or empty when the
     * export has no comments. [badgeText] is the text of the badge in the video language.
     */
    fun of(points: List<PointV1>, comments: List<CommentV1>, badgeText: String): List<ExportReplay> =
        points
            .filter { it.replays.isNotEmpty() }
            .sortedBy { it.endMs }
            .flatMap { point ->
                Replays.fitTo(point).map { replay ->
                    ExportReplay(
                        pointId = point.id,
                        pointEndMs = point.endMs,
                        replay = replay,
                        comments = commentSpans(replay, comments),
                        badgeText = badgeText.takeIf { replay.showBadge },
                    )
                }
            }

    /**
     * The comments that are visible during the part of [replay] in the source video. Each one shows at the same
     * moment of the play, and its time gets longer by the slow speed (decision 9).
     */
    fun commentSpans(replay: ReplayV1, comments: List<CommentV1>): List<CommentOverlaySpan> {
        val partStart = replay.startMs.toLong()
        val partEnd = replay.endMs.toLong()
        val playEnd = Replays.playMs(replay)
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
                    startMs = ((start - partStart) / replay.speed).roundToLong(),
                    endMs = ((end - partStart) / replay.speed).roundToLong().coerceAtMost(playEnd),
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

    /** The dip to black between two repeats (decision 7). */
    val repeatFade: PassFade = PassFade(white = false, durationMs = Replays.REPEAT_FADE_MS)
}
