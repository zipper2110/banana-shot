package org.litvin.export.preview

import org.litvin.FFmpegCommandBuilder
import org.litvin.export.PassFade
import org.litvin.media.ReplaySound
import org.litvin.media.ReplayView
import org.litvin.points.PointV1
import org.litvin.points.Replays
import kotlin.math.abs

/**
 * Plays an [ExportPreviewTimeline] in one live player (B-75). It has no Swing code: it gets the source times of the
 * player and the ticks of a timer, and gives the commands for the player.
 *
 * - A video item plays the source. At its end, the director seeks to the next item. While the player seeks, the
 *   picture stays on the last frame, with no black (decision 9). Two video items that touch in the source play on
 *   with no seek.
 * - A replay item plays with [ReplayDirector], as on the Points tab: the speed, the zoom, the still frame, the dips,
 *   and the sound of the export.
 * - A card item shows the frozen frame of the export and counts its time with [now].
 *
 * The director keeps the output time ([outMs]) and the overlay of that moment ([overlay]).
 */
class ExportPreviewDirector(
    val timeline: ExportPreviewTimeline,
    /** The duration of the source video. A card at the end of the source shows its last frame. */
    private val sourceDurationMs: Long?,
    /** The clock of the cards and the still frames, in milliseconds. */
    private val now: () -> Long = System::currentTimeMillis,
    frameMs: () -> Double = { DEFAULT_FRAME_MS },
) {
    sealed interface Command {
        data class Seek(val sourceMs: Long) : Command
        data object Play : Command
        data object Pause : Command
        data class Look(val view: ReplayView?) : Command
        data class Sound(val sound: ReplaySound?) : Command

        /** Pause the player and show the first frame at or after the source time [frameFromMs]. */
        data class ShowFrame(val frameFromMs: Double) : Command
    }

    private val replays = ReplayDirector(frameMs)

    /** The fitted replays of each point, in play order, as the export plays them. */
    private val replayPoints: Map<String, PointV1> = timeline.items
        .filterIsInstance<ExportPreviewTimeline.Replay>()
        .groupBy { it.replay.pointId }
        .mapValues { (id, plays) ->
            val ordered = plays.sortedBy { it.replay.replayIndex }
            PointV1(id = id, startMs = 0, endMs = ordered.first().replay.pointEndMs, replays = ordered.map { it.replay.replay })
        }

    /** True while the user wants the preview to play. */
    var playing: Boolean = false
        private set

    /** The index of the item that the preview shows now. */
    var index: Int = 0
        private set

    /** The output time that the preview shows now. */
    var outMs: Long = 0L
        private set

    /** True when the preview is at the end of the output. */
    val atEnd: Boolean get() = timeline.items.isEmpty() || outMs >= timeline.durationMs

    val item: ExportPreviewTimeline.Item? get() = timeline.items.getOrNull(index)

    /** The last source time of the player in the current item. */
    private var sourceMs: Long = 0L

    /** The target of the last seek, until the player shows a time near it. */
    private var seekTarget: Long? = null
    private var ignoredTimes = 0

    /** The time in the card item. While the card plays, it is the time since [cardStartedAt]. */
    private var cardElapsedMs = 0L
    private var cardStartedAt = 0L

    /** The still frame of a replay: its time so far, and the clock time of its start while it plays. */
    private var holdDurationMs: Long? = null
    private var holdElapsedMs = 0L
    private var holdStartedAt = 0L

    private var shownView: ReplayView? = null

    /** What the preview draws over the video now. */
    val overlay: PreviewOverlay
        get() = when (val current = item) {
            is ExportPreviewTimeline.Video -> PreviewOverlay.Video(current.overlayMsAt(sourceMs))
            is ExportPreviewTimeline.Replay -> PreviewOverlay.Replay(current, outMs - current.outStartMs)
            is ExportPreviewTimeline.Card -> PreviewOverlay.Card(current, cardElapsedMs)
            null -> PreviewOverlay.None
        }

    /** Shows the first frame of the output, paused. */
    fun open(): List<Command> = if (timeline.items.isEmpty()) emptyList() else enter(0, 0L)

    fun play(): List<Command> {
        if (timeline.items.isEmpty()) return emptyList()
        if (atEnd) {
            playing = true
            return enter(0, 0L)
        }
        playing = true
        return when (item) {
            is ExportPreviewTimeline.Card -> {
                cardStartedAt = now() - cardElapsedMs
                emptyList()
            }
            is ExportPreviewTimeline.Replay -> if (holdDurationMs != null) {
                holdStartedAt = now() - holdElapsedMs
                emptyList()
            } else {
                listOf(Command.Play)
            }
            else -> listOf(Command.Play)
        }
    }

    fun pause(): List<Command> {
        if (!playing) return emptyList()
        updateClocks()
        playing = false
        return listOf(Command.Pause)
    }

    /** Goes to [targetMs] of the output time. A time inside a replay starts that play from its start. */
    fun seek(targetMs: Long): List<Command> {
        if (timeline.items.isEmpty()) return emptyList()
        val target = targetMs.coerceIn(0L, timeline.durationMs)
        val at = timeline.indexAt(target)
        return enter(at, target - timeline.items[at].outStartMs)
    }

    /** Goes to the start of the next item. */
    fun nextItem(): List<Command> = timeline.nextStartMs(outMs)?.let(::seek) ?: emptyList()

    /** Goes to the start of the current item, or of the item before it (see [ExportPreviewTimeline.previousStartMs]). */
    fun previousItem(): List<Command> = timeline.previousStartMs(outMs)?.let(::seek) ?: emptyList()

    /** The player plays again after a seek. */
    fun onRestart() {
        seekTarget = null
        replays.onRestart()
    }

    /** The player shows the frame at [source]. [playerPlaying] is true while the player plays. */
    fun onTime(source: Long, playerPlaying: Boolean): List<Command> {
        val target = seekTarget
        if (target != null) {
            // The player can send some times of the old position after the seek command.
            if (abs(source - target) > SEEK_TOLERANCE_MS && ++ignoredTimes < MAX_IGNORED_TIMES) return emptyList()
            seekTarget = null
        }
        return when (val current = item) {
            is ExportPreviewTimeline.Video -> onVideoTime(current, source)
            is ExportPreviewTimeline.Replay -> onReplayTime(current, source, playerPlaying)
            else -> emptyList()
        }
    }

    /** The timer: the time of a card and of a still frame. */
    fun tick(): List<Command> {
        updateClocks()
        val current = item
        if (current is ExportPreviewTimeline.Card) {
            outMs = current.outStartMs + cardElapsedMs.coerceAtMost(current.durationMs)
            if (playing && cardElapsedMs >= current.durationMs) return next()
        }
        if (current is ExportPreviewTimeline.Replay) {
            val hold = holdDurationMs ?: return emptyList()
            outMs = current.outStartMs + (Replays.freezeOffsetMs(current.replay.replay) ?: 0L) + holdElapsedMs.coerceAtMost(hold)
            if (playing && holdElapsedMs >= hold) {
                holdDurationMs = null
                holdElapsedMs = 0L
                return if (replays.endHold()) listOf(Command.Play) else emptyList()
            }
        }
        return emptyList()
    }

    private fun updateClocks() {
        if (!playing) return
        when (item) {
            is ExportPreviewTimeline.Card -> cardElapsedMs = now() - cardStartedAt
            is ExportPreviewTimeline.Replay -> if (holdDurationMs != null) holdElapsedMs = now() - holdStartedAt
            else -> {}
        }
    }

    private fun onVideoTime(current: ExportPreviewTimeline.Video, source: Long): List<Command> {
        sourceMs = source.coerceIn(current.sourceStartMs, current.sourceEndMs)
        outMs = current.outMsAt(sourceMs)
        if (playing && source >= current.sourceEndMs - END_TOLERANCE_MS) return next()
        return look(videoView(current, sourceMs))
    }

    private fun onReplayTime(current: ExportPreviewTimeline.Replay, source: Long, playerPlaying: Boolean): List<Command> {
        if (holdDurationMs != null) return emptyList()
        val commands = replays.onTime(source, playerPlaying)
        if (!replays.isActive) {
            // The last play of the point is over. The video goes on with the next item, not at the point end.
            return commands.mapNotNull(::fromReplay).filter { it !is Command.Seek } + after(lastReplayIndexOf(current))
        }
        val play = replays.activePlay
        if (play != null && play.replayIndex != current.replay.replayIndex) {
            // The director goes on with the next replay of the point.
            replayItemIndex(current.replay.pointId, play.replayIndex)?.let { index = it }
        }
        val active = item as? ExportPreviewTimeline.Replay ?: current
        sourceMs = source
        val result = mutableListOf<Command>()
        for (command in commands) {
            if (command is ReplayDirector.Command.Hold) {
                holdDurationMs = command.durationMs
                holdElapsedMs = 0L
                holdStartedAt = now()
                result += Command.ShowFrame(command.frameFromMs)
            } else {
                fromReplay(command)?.let { result += it }
            }
        }
        if (holdDurationMs == null) {
            outMs = active.outStartMs + Replays.playOffsetAt(active.replay.replay, source).coerceIn(0L, active.durationMs)
        }
        return result
    }

    private fun fromReplay(command: ReplayDirector.Command): Command? = when (command) {
        is ReplayDirector.Command.Seek -> Command.Seek(command.sourceMs)
        is ReplayDirector.Command.Look -> {
            shownView = command.view
            Command.Look(command.view)
        }
        is ReplayDirector.Command.Sound -> Command.Sound(command.sound)
        is ReplayDirector.Command.Hold -> null
    }

    /** The next item, or the end of the output. */
    private fun next(): List<Command> = after(index)

    private fun after(last: Int): List<Command> {
        val nextIndex = last + 1
        if (nextIndex > timeline.items.lastIndex) return end()
        return enter(nextIndex, 0L, playsOn = true)
    }

    private fun end(): List<Command> {
        val wasPlaying = playing
        playing = false
        outMs = timeline.durationMs
        cardElapsedMs = (item as? ExportPreviewTimeline.Card)?.durationMs ?: cardElapsedMs
        return if (wasPlaying) listOf(Command.Pause) else emptyList()
    }

    /**
     * Shows the item at [at], [offsetMs] after its start. [playsOn] is true when the play goes from one item to the next,
     * not for a seek of the user.
     */
    private fun enter(at: Int, offsetMs: Long, playsOn: Boolean = false): List<Command> {
        val previous = item
        val reset = replays.stop().mapNotNull(::fromReplay).filter { it !is Command.Seek }
        index = at
        holdDurationMs = null
        holdElapsedMs = 0L
        val target = timeline.items[at]
        val offset = offsetMs.coerceIn(0L, target.durationMs)
        outMs = target.outStartMs + offset
        return reset + when (target) {
            is ExportPreviewTimeline.Video -> enterVideo(target, offset, previous.takeIf { playsOn })
            is ExportPreviewTimeline.Replay -> enterReplay(target)
            is ExportPreviewTimeline.Card -> enterCard(target, offset)
        }
    }

    /** [previous] is the item that played before, or null after a seek of the user. */
    private fun enterVideo(target: ExportPreviewTimeline.Video, offset: Long, previous: ExportPreviewTimeline.Item?): List<Command> {
        val source = target.sourceStartMs + offset
        // A video item that starts where the last one ends plays on with no seek.
        val goesOn = offset == 0L && previous is ExportPreviewTimeline.Video && previous.sourceEndMs == target.sourceStartMs
        sourceMs = source
        val commands = mutableListOf<Command>(Command.Sound(null))
        commands += look(videoView(target, source))
        if (!goesOn) {
            seekTo(source)
            commands += Command.Seek(source)
        }
        commands += if (playing) Command.Play else Command.Pause
        return commands
    }

    private fun enterReplay(target: ExportPreviewTimeline.Replay): List<Command> {
        val point = replayPoints[target.replay.pointId] ?: return after(index)
        replays.setPoints(listOf(point))
        val commands = replays.start(point.id, target.replay.replayIndex).mapNotNull(::fromReplay)
        commands.filterIsInstance<Command.Seek>().lastOrNull()?.let { seekTo(it.sourceMs) }
        sourceMs = target.replay.replay.startMs.toLong()
        // A replay always starts at its start (decision 14), also after a seek of the user into its middle.
        outMs = target.outStartMs
        return commands + if (playing) Command.Play else Command.Pause
    }

    private fun enterCard(target: ExportPreviewTimeline.Card, offset: Long): List<Command> {
        cardElapsedMs = offset
        cardStartedAt = now() - offset
        val freezeAt = target.freezeAtMs ?: sourceDurationMs ?: 0L
        val frameFrom = FFmpegCommandBuilder.stillFrameSeekMs(freezeAt).toDouble()
        seekTarget = null
        return listOf(Command.Sound(null)) + look(null) + Command.ShowFrame(frameFrom)
    }

    private fun seekTo(source: Long) {
        seekTarget = source
        ignoredTimes = 0
    }

    /** The dip at a cut from a replay ([ExportPreviewTimeline.Video.fadeIn]) and into a replay (`fadeOut`). */
    private fun videoView(item: ExportPreviewTimeline.Video, source: Long): ReplayView? {
        val fromStart = source - item.sourceStartMs
        val toEnd = item.sourceEndMs - source
        val dipIn = item.fadeIn?.let { dip(fromStart, it) } ?: 0.0
        val dipOut = item.fadeOut?.let { dip(toEnd, it) } ?: 0.0
        val dip = maxOf(dipIn, dipOut)
        if (dip <= 0.0) return null
        val white = (if (dipIn >= dipOut) item.fadeIn else item.fadeOut)?.white ?: false
        return ReplayView(dip = dip, dipWhite = white)
    }

    private fun dip(distanceMs: Long, fade: PassFade): Double =
        if (fade.durationMs <= 0) 0.0 else (1.0 - distanceMs.toDouble() / fade.durationMs).coerceIn(0.0, 1.0)

    private fun look(view: ReplayView?): List<Command> {
        val shown = view?.takeIf { !it.isNormal }
        if (shown == shownView) return emptyList()
        shownView = shown
        return listOf(Command.Look(shown))
    }

    /** The index of the replay item of [pointId] with [replayIndex], or null. */
    private fun replayItemIndex(pointId: String, replayIndex: Int): Int? =
        timeline.items.indexOfFirst { it is ExportPreviewTimeline.Replay && it.replay.pointId == pointId && it.replay.replayIndex == replayIndex }
            .takeIf { it >= 0 }

    /** The index of the last replay item of the point of [current]. */
    private fun lastReplayIndexOf(current: ExportPreviewTimeline.Replay): Int =
        timeline.items.indexOfLast { it is ExportPreviewTimeline.Replay && it.replay.pointId == current.replay.pointId }
            .coerceAtLeast(index)

    private companion object {
        const val DEFAULT_FRAME_MS = 1000.0 / 30

        /** A time this far from the seek target is an old time of the player. */
        const val SEEK_TOLERANCE_MS = 150L

        /** After this many old times, the director stops waiting for the restart of the player. */
        const val MAX_IGNORED_TIMES = 60

        /** The item ends at this time before its end, because the last frame starts before the end. */
        const val END_TOLERANCE_MS = 20L
    }
}
