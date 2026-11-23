package org.litvin.ui.tabs.points.detect

import org.litvin.autodetect.DetectedRally
import org.litvin.ui.commons.Palette
import org.litvin.ui.tabs.points.ui.ScorePlayers
import java.awt.Color
import java.util.Locale
import kotlin.math.abs

/**
 * One shot of the shot chain of a rally, as the review window shows it: in the shot panel on the video and on the
 * shot tracks of the timeline.
 *
 * The shot is from [startMs] to [untilMs] (the start of the next shot). The ball was seen until [trackedMs].
 * [endMs] is the time of the bounce or of the net touch, or null when the end was not seen.
 * With [predicted], the end was not seen: [end], [endMs], and [outcome] come from the fit of the flight.
 */
internal data class ShotView(
    val number: Int,
    val startMs: Long,
    val untilMs: Long,
    val trackedMs: Long,
    val endMs: Long?,
    val who: String,
    val color: Color,
    /** How the hitter is known: "first", "hit", "back", "gap", or "net". */
    val how: String?,
    /** The end of the shot: "in", "out", "own", "net", or "none". A predicted end can also be "line": too near to a line for in or out. */
    val end: String?,
    val outcome: String,
    val predicted: Boolean = false,
    /** The shot starts after the end of the rally: the players handle the ball. It is not a shot of the point. */
    val afterPoint: Boolean = false,
) {
    /** The color of the end of the shot: green for in, red for out, yellow for net. Null: the end was not seen. */
    val endColor: Color?
        get() = if (afterPoint) null else when (end) {
            "in" -> Palette.GREEN
            "out", "own" -> Palette.RED
            "net" -> Palette.YELLOW
            "line" -> Palette.FG_2
            else -> null
        }

    /** The color of the track that shows how the hitter is known. */
    val howColor: Color
        get() = if (afterPoint) Palette.FG_3 else when (how) {
            "hit", "first" -> Palette.FG
            "back" -> Palette.BLUE
            else -> Palette.FG_3
        }

    val howText: String
        get() = when (how) {
            "first" -> "first shot"
            "hit" -> "hit seen"
            "back" -> "the ball came back"
            "gap" -> "hit not seen"
            "net" -> "from the net event"
            else -> "hitter not known"
        }
}

/** The texts of the shot panel on the video. The panel has the same size for each shot. */
internal data class ShotPanel(
    val number: String,
    val who: String,
    val whoColor: Color,
    val time: String,
    val outcome: String,
    val outcomeColor: Color,
)

internal object ShotViews {
    /** The shots of [rally]. [near] is the player (1 or 2) at the near end, or null when it is not known. */
    fun of(rally: DetectedRally, near: Int?, players: ScorePlayers): List<ShotView> {
        val shots = rally.chain?.shots.orEmpty()
        return shots.mapIndexed { index, shot ->
            val start = ms(shot.t)
            val tracked = shot.tLast?.let(::ms)?.coerceAtLeast(start) ?: start
            // An end that was not seen can have a predicted end: the landing of the fit of the flight.
            val predicted = shot.predicted?.takeIf { shot.end == null || shot.end == "none" }
            val kind = predicted?.end ?: shot.end
            val court = predicted?.court ?: shot.court
            val margin = if (predicted != null) predicted.margin else shot.margin
            val end = (predicted?.t ?: shot.tEnd)?.let(::ms)
            val next = shots.getOrNull(index + 1)?.let { ms(it.t) }
            val after = start > ms(rally.end) + AFTER_POINT_MS
            val player = near?.takeIf { shot.near != null }?.let { if (shot.near == false) 3 - it else it }
            val place = court?.takeIf { it.size >= 2 }?.let { String.format(Locale.ROOT, " at %.1f, %.1f m", it[0], it[1]) }.orEmpty()
            ShotView(
                number = index + 1,
                startMs = start,
                untilMs = next ?: maxOf(tracked, end ?: start, start + MIN_SHOWN_MS),
                trackedMs = tracked,
                endMs = end,
                who = when {
                    player != null -> players.name(player)
                    shot.near == true -> "Near player"
                    shot.near == false -> "Far player"
                    else -> "Not known"
                },
                color = if (after) Palette.FG_3 else player?.let { players.color(it) } ?: Palette.FG_2,
                how = shot.how,
                end = kind,
                outcome = when (kind) {
                    "in" -> "In$place"
                    "out" -> "Out" + (margin?.let { String.format(Locale.ROOT, " by %.1f m", abs(it)) } ?: place)
                    "own" -> "Bounce on own side"
                    "net" -> "Net"
                    "line" -> "Near a line$place"
                    else -> "End not seen"
                }.let { if (predicted != null) "Predicted: ${it.replaceFirstChar(Char::lowercaseChar)}" else it }
                    .let { if (after) "After the point" else it },
                predicted = predicted != null,
                afterPoint = after,
            )
        }
    }

    /** The panel for the video time [timeMs]: the shot that is in progress, or an empty panel before the first shot. */
    fun panelAt(shots: List<ShotView>, timeMs: Long): ShotPanel {
        val shot = shots.lastOrNull { it.startMs <= timeMs } ?: return ShotPanel("–", "", Palette.FG_2, "", "", Palette.FG_2)
        // The outcome shows from the end of the shot. Before that, the ball is in the air.
        val decided = shot.endMs == null || timeMs >= shot.endMs
        return ShotPanel(
            number = shot.number.toString(),
            who = shot.who,
            whoColor = shot.color,
            time = "${clock(shot.startMs)} to ${clock(shot.untilMs)}",
            outcome = if (decided) shot.outcome else "In the air",
            outcomeColor = if (decided) shot.endColor ?: Palette.FG_2 else Palette.FG_2,
        )
    }

    /** One line about a shot, for the tooltip of the timeline. */
    fun tip(shot: ShotView): String = "Shot ${shot.number} · ${shot.who} · ${shot.howText} · ${shot.outcome}"

    /** Minutes, seconds, and tenths of the video time. */
    fun clock(ms: Long): String = String.format(Locale.ROOT, "%d:%04.1f", ms / 60_000, (ms % 60_000) / 100 / 10.0)

    private fun ms(seconds: Double) = (seconds * 1000).toLong()

    /** A shot that starts later than this after the end of the rally is not a shot of the point. */
    private const val AFTER_POINT_MS = 1000L

    /** The last shot shows this long on the timeline at least. */
    private const val MIN_SHOWN_MS = 300L
}
