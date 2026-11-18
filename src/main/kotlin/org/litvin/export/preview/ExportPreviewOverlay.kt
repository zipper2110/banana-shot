package org.litvin.export.preview

import org.litvin.AssOverlayWriter
import org.litvin.CommentOverlaySpan
import org.litvin.OverlaySpan
import org.litvin.RenderJob
import org.litvin.export.ReplayBadgeAss
import org.litvin.export.comments.CommentAss
import org.litvin.export.comments.CommentClock
import org.litvin.export.comments.CommentMotion
import org.litvin.export.scoreboard.BoardPlacement
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.i18n.I18n
import org.litvin.points.CommentAnimation
import org.litvin.stats.StatsCard
import org.litvin.videoTextLanguage
import java.awt.geom.Rectangle2D
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the export preview (B-75) draws over the video at one moment. The preview sends it to the mpv `osd-overlay`
 * as ASS events. The events come from the same code as the ASS files of the export, so the preview shows the same
 * scoreboard, comments, replay badge, and cards.
 */
sealed interface PreviewOverlay {
    /** A video item at [overlayMs] of the overlay time: the scoreboard and the comments. */
    data class Video(val overlayMs: Long) : PreviewOverlay

    /** A play of a replay at [playOffsetMs] from its start: the comments of the replay and the badge, no scoreboard. */
    data class Replay(val item: ExportPreviewTimeline.Replay, val playOffsetMs: Long) : PreviewOverlay

    /** A card at [elapsedMs] from its start: the page of the card at that time. */
    data class Card(val item: ExportPreviewTimeline.Card, val elapsedMs: Long) : PreviewOverlay

    data object None : PreviewOverlay
}

object ExportPreviewOverlay {
    /**
     * The ASS events of [overlay] in the video area [area], in drawing order. The texts of the scoreboard use the video
     * language of [job].
     */
    fun events(job: RenderJob, overlay: PreviewOverlay, area: Rectangle2D.Double): List<String> =
        I18n.inVideo(job.videoTextLanguage) {
            when (overlay) {
                is PreviewOverlay.Video -> videoEvents(job, overlay.overlayMs, area)
                is PreviewOverlay.Replay -> replayEvents(overlay, area)
                is PreviewOverlay.Card -> cardEvents(overlay, area)
                PreviewOverlay.None -> emptyList()
            }
        }

    private fun videoEvents(job: RenderJob, overlayMs: Long, area: Rectangle2D.Double): List<String> {
        val board = if (job.includeScoreboard) {
            job.overlayTimeline.filter { overlayMs >= it.startMs && overlayMs < it.endMs }.flatMap { span ->
                val fade = fadeTag(span, overlayMs)
                AssOverlayWriter.scoreboardEvents(span, job.scoreboardSettings, area.x, area.y, area.width, area.height)
                    .map { fade + it }
            }
        } else {
            emptyList()
        }
        val comments = if (job.includeComments) commentEvents(job.commentOverlayTimeline, overlayMs, area) else emptyList()
        return board + comments
    }

    private fun replayEvents(overlay: PreviewOverlay.Replay, area: Rectangle2D.Double): List<String> {
        val replay = overlay.item.replay
        val comments = commentEvents(replay.comments, overlay.playOffsetMs, area)
        val badge = replay.badgeText?.let { ReplayBadgeAss.events(it, area.width, area.height, area.x, area.y) }.orEmpty()
        return comments + badge
    }

    /** The card pages fill the frame. The export places them in the same way (`AssOverlayWriter.writeStatsCard`). */
    private fun cardEvents(overlay: PreviewOverlay.Card, area: Rectangle2D.Double): List<String> {
        val card = overlay.item.card
        if (card.pages.isEmpty() || card.pageDurationMs <= 0) return emptyList()
        val page = (overlay.elapsedMs / card.pageDurationMs).toInt().coerceIn(0, card.pages.lastIndex)
        val placement = BoardPlacement(area.x, area.y, area.height / StatsCard.HEIGHT)
        return ScoreboardAss.events(card.pages[page], placement)
    }

    /** The events of the comments in [spans] at [timeMs], with the animation state of the export at that time. */
    private fun commentEvents(spans: List<CommentOverlaySpan>, timeMs: Long, area: Rectangle2D.Double): List<String> =
        spans.filter { timeMs >= it.startMs && timeMs < it.endMs && it.text.isNotBlank() }.flatMap { span ->
            val motion = if (span.animation == CommentAnimation.NONE) {
                CommentMotion.Still
            } else {
                CommentMotion.Frozen(span.animation, CommentClock(timeMs - span.startMs, span.endMs - span.startMs))
            }
            CommentAss.events(
                span.text, span.colorHex, area.x, area.y, area.width, area.height, span.style, span.position,
                span.sizePercent, span.opacityPercent, span.rotationDeg, motion,
            )
        }

    /**
     * The fade of the board at [timeMs], as the `\fad` tag of the export gives it. An `osd-overlay` has no event time,
     * so the tag sets the alpha of that moment: a `\fade` with the same alpha at all times.
     */
    internal fun fadeTag(span: OverlaySpan, timeMs: Long): String {
        val opacity = boardOpacity(span, timeMs)
        if (opacity >= 1.0) return ""
        val alpha = ((1.0 - opacity) * 255).roundToInt().coerceIn(0, 255)
        return String.format(Locale.ROOT, "{\\fade(%d,%d,%d,0,0,0,0)}", alpha, alpha, alpha)
    }

    /** The opacity of the board at [timeMs]: 0 at the start of a fade in and at the end of a fade out. */
    internal fun boardOpacity(span: OverlaySpan, timeMs: Long): Double {
        val sinceStart = (timeMs - span.startMs).toDouble()
        val toEnd = (span.endMs - timeMs).toDouble()
        val fadeIn = if (span.fadeInMs > 0) sinceStart / span.fadeInMs else 1.0
        val fadeOut = if (span.fadeOutMs > 0) toEnd / span.fadeOutMs else 1.0
        return minOf(fadeIn, fadeOut, 1.0).coerceAtLeast(0.0)
    }
}
