package org.litvin.ui.tabs.points

import org.litvin.export.ExportReplays
import org.litvin.export.PassFade
import org.litvin.media.ReplaySound
import org.litvin.media.ReplayView
import org.litvin.points.PointV1
import org.litvin.points.ReplayTimeline
import org.litvin.points.Replays
import kotlin.math.abs

/**
 * Plays the replays of the points in a live player (B-50 T3). It has no Swing code: it gets the source times of the
 * player and gives the commands for the player.
 *
 * When the video plays past the end of a point with replays, the director seeks to each replay part and plays it at
 * the replay speed, with the zoom and the dips of the export. After the last play, it seeks to the point end, and
 * the video continues. Each seek waits for [onRestart], so that the old times of the player before the seek do
 * not count.
 *
 * A time far outside the part during a replay is a seek of the user. Then the replay stops, and the video plays
 * from that time.
 */
class ReplayDirector {
    sealed interface Command {
        data class Seek(val sourceMs: Long) : Command
        data class Look(val view: ReplayView?) : Command
        data class Sound(val sound: ReplaySound?) : Command
    }

    private var timeline = ReplayTimeline.EMPTY
    private var insert: ReplayTimeline.Insert? = null
    private var playIndex = 0
    private var lastMs: Long? = null

    /** The target of the last seek of the director, until the player starts to play there. */
    private var seekTarget: Long? = null
    private var ignoredTimes = 0

    /** The point end where the video continues after the replays, with the dip of that cut. */
    private var fadeInAt: Pair<Long, PassFade>? = null
    private var shownView: ReplayView? = null

    /** True while a replay plays. */
    val isActive: Boolean get() = insert != null

    /** The play that the player shows now, or null outside a replay. */
    val activePlay: ReplayTimeline.Play? get() = insert?.plays?.getOrNull(playIndex)

    /** Sets the points. A replay that plays now stops when its point changes. */
    fun setPoints(points: List<PointV1>): List<Command> {
        val next = ReplayTimeline(points)
        val active = insert
        timeline = next
        if (active != null && next.insertOf(active.point.id) != active) return stop()
        return emptyList()
    }

    /** The player shows the frame at [sourceMs]. [playing] is false while the video is paused. */
    fun onTime(sourceMs: Long, playing: Boolean): List<Command> {
        val target = seekTarget
        if (target != null) {
            // The player can send some times of the old position after the seek command.
            if (abs(sourceMs - target) > SEEK_TOLERANCE_MS && ++ignoredTimes < MAX_IGNORED_TIMES) return emptyList()
            seekTarget = null
        }
        val previous = lastMs
        lastMs = sourceMs
        val active = insert
        if (active != null) {
            val play = active.plays[playIndex]
            val part = play.replay
            if (sourceMs < part.startMs - JUMP_MS || sourceMs > part.endMs + JUMP_MS) return stop()
            if (sourceMs >= part.endMs - END_TOLERANCE_MS) return next(active)
            return look(viewIn(play, sourceMs))
        }
        if (playing && previous != null && sourceMs > previous && sourceMs - previous <= MAX_STEP_MS) {
            timeline.insertCrossed(previous, sourceMs)?.let { return enter(it) }
        }
        return look(viewOutside(sourceMs, playing))
    }

    /** The player plays again after a seek. */
    fun onRestart() {
        seekTarget = null
    }

    /** Stops a replay where the player is now, and shows the normal picture and sound. */
    fun stop(): List<Command> {
        val wasActive = insert != null
        insert = null
        playIndex = 0
        seekTarget = null
        fadeInAt = null
        val commands = mutableListOf<Command>()
        if (shownView != null) commands += Command.Look(null)
        shownView = null
        if (wasActive) commands += Command.Sound(null)
        return commands
    }

    private fun enter(next: ReplayTimeline.Insert): List<Command> {
        insert = next
        playIndex = 0
        fadeInAt = null
        return startPlay(next.plays[0])
    }

    private fun startPlay(play: ReplayTimeline.Play): List<Command> {
        val part = play.replay
        seekTarget = part.startMs.toLong()
        ignoredTimes = 0
        lastMs = part.startMs.toLong()
        return look(viewIn(play, part.startMs.toLong())) + listOf(
            Command.Sound(ReplaySound(part.speed, part.muted)),
            Command.Seek(part.startMs.toLong()),
        )
    }

    private fun next(active: ReplayTimeline.Insert): List<Command> {
        if (playIndex + 1 < active.plays.size) {
            playIndex++
            return startPlay(active.plays[playIndex])
        }
        val last = active.plays.last()
        val at = active.atSourceMs
        insert = null
        playIndex = 0
        seekTarget = at
        ignoredTimes = 0
        lastMs = at
        val fade = ExportReplays.cutFade(last.replay.effect)
        fadeInAt = fade?.let { at to it }
        val view = fade?.let { ReplayView(dip = 1.0, dipWhite = it.white) }
        return look(view) + listOf(Command.Sound(null), Command.Seek(at))
    }

    /** The picture at [sourceMs] of the part of [play]: the zoom and the dips (decisions 2, 7, and 8). */
    private fun viewIn(play: ReplayTimeline.Play, sourceMs: Long): ReplayView {
        val part = play.replay
        val length = Replays.playMs(part).toDouble()
        val offset = ((sourceMs - part.startMs) / part.speed).coerceIn(0.0, length)
        val fadeIn = if (play.isFirst) ExportReplays.cutFade(part.effect) else ExportReplays.repeatFade
        val fadeOut = if (play.isLast) ExportReplays.cutFade(part.effect) else ExportReplays.repeatFade
        val dipIn = fadeIn?.let { 1.0 - offset / it.durationMs } ?: 0.0
        val dipOut = fadeOut?.let { 1.0 - (length - offset) / it.durationMs } ?: 0.0
        val dip = maxOf(dipIn, dipOut, 0.0).coerceAtMost(1.0)
        val white = (if (dipIn >= dipOut) fadeIn else fadeOut)?.white ?: false
        val zoom = part.zoom ?: return ReplayView(dip = dip, dipWhite = white)
        var progress = 1.0
        if (play.isFirst) progress = minOf(progress, smooth(offset / Replays.ZOOM_MS))
        if (play.isLast) progress = minOf(progress, smooth((length - offset) / Replays.ZOOM_MS))
        return ReplayView(
            zoomX = zoom.x * progress,
            zoomY = zoom.y * progress,
            zoomWidth = 1.0 - (1.0 - zoom.width) * progress,
            dip = dip,
            dipWhite = white,
        )
    }

    /** The picture outside the replays: the dip before the cut into a replay, and after the cut back. */
    private fun viewOutside(sourceMs: Long, playing: Boolean): ReplayView? {
        fadeInAt?.let { (at, fade) ->
            val after = sourceMs - at
            if (after in 0 until fade.durationMs) return ReplayView(dip = 1.0 - after.toDouble() / fade.durationMs, dipWhite = fade.white)
            fadeInAt = null
        }
        if (!playing) return null
        val ahead = timeline.inserts.firstOrNull { it.atSourceMs > sourceMs } ?: return null
        val fade = ExportReplays.cutFade(ahead.plays.first().replay.effect) ?: return null
        val before = ahead.atSourceMs - sourceMs
        if (before > fade.durationMs) return null
        return ReplayView(dip = 1.0 - before.toDouble() / fade.durationMs, dipWhite = fade.white)
    }

    private fun look(view: ReplayView?): List<Command> {
        val shown = view?.takeIf { !it.isNormal }
        if (shown == shownView) return emptyList()
        shownView = shown
        return listOf(Command.Look(shown))
    }

    private fun smooth(value: Double): Double {
        val s = value.coerceIn(0.0, 1.0)
        return s * s * (3 - 2 * s)
    }

    private companion object {
        /** A time this far from the seek target is an old time of the player. */
        const val SEEK_TOLERANCE_MS = 150L

        /** After this many old times, the director stops waiting for the restart of the player. */
        const val MAX_IGNORED_TIMES = 60

        /** A time this far outside the part is a seek of the user. */
        const val JUMP_MS = 400L

        /** The play ends at this time before the end of the part, because the last frame starts before the end. */
        const val END_TOLERANCE_MS = 20L

        /** A larger step between two times is a seek, not a play over the point end. */
        const val MAX_STEP_MS = 1_000L
    }
}
