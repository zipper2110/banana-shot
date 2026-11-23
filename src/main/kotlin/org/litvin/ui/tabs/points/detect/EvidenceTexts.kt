package org.litvin.ui.tabs.points.detect

import org.litvin.autodetect.DetectedRally
import org.litvin.autodetect.LastShot
import org.litvin.shared.util.Timecode
import org.litvin.ui.tabs.points.ui.ScorePlayers
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The texts that tell what the detection found in a rally and why it gave the winner (decision 29 of
 * docs/auto-analysis/app-integration.md). They are for the developer of the detection, not for a normal user.
 */
internal object EvidenceTexts {
    /** One line for each fact. [near] is the player (1 or 2) at the near end in the clip, or null when it is not known. */
    fun lines(rally: DetectedRally, near: Int?, players: ScorePlayers): List<String> {
        val nearName = near?.let { players.name(it) } ?: "the near player"
        val farName = near?.let { players.name(3 - it) } ?: "the far player"
        val lines = ArrayList<String>()
        lines += if (rally.kind == "short") {
            "Found as: a short point, from a serve event"
        } else {
            "Found as: a rally" + (rally.prob?.let { " (rally model ${percent(it)})" } ?: "") + (rally.shots?.let { ", $it shots" } ?: "")
        }
        rally.pNotPoint?.takeIf { it >= 0.1 }?.let { lines += "Possibly not a point: ${percent(it)}" }
        lines += when (rally.serverNear) {
            true -> "Server: $nearName (near end)"
            false -> "Server: $farName (far end)"
            null -> "Server: not known"
        } + (rally.serveSpeedKmh?.let { ", serve ${it.roundToInt()} km/h" } ?: "")
        rally.fault?.let { fault ->
            lines += "First-serve fault before: at ${time(fault.t)}" + (fault.speed?.let { ", ${it.roundToInt()} km/h" } ?: "")
        }
        if (rally.endChangeBefore == true) lines += "The players changed ends before this point"
        lines += lastShot(rally.lastShot, nearName, farName)
        lines += netHit(rally, nearName, farName)
        lines += winner(rally, nearName, farName)
        return lines
    }

    /** The texts of the winner decision: the last shot, the ball rule, and the score model. */
    fun outcomeLines(rally: DetectedRally, near: Int?, players: ScorePlayers): List<String> {
        val nearName = near?.let { players.name(it) } ?: "the near player"
        val farName = near?.let { players.name(3 - it) } ?: "the far player"
        return lastShot(rally.lastShot, nearName, farName) + netHit(rally, nearName, farName) + winner(rally, nearName, farName)
    }

    /** The ball into the net at the end of the rally. A warning when the winner is the player who hit it. */
    private fun netHit(rally: DetectedRally, nearName: String, farName: String): List<String> {
        val hit = rally.netHit ?: return emptyList()
        val hitter = if (hit.hitterNear) nearName else farName
        val lines = ArrayList<String>()
        lines += "Net: $hitter hit the ball into the net at ${time(hit.t)}" + (if (hit.serve) " (serve)" else "") +
            (hit.conf?.let { ", touch ${percent(it)}" } ?: "") + (hit.dirConf?.let { ", hitter ${percent(it)}" } ?: "")
        val model = rally.pNearWins
        if (model != null && abs(model - 0.5) >= 0.02 && (model > 0.5) == hit.hitterNear) {
            lines += "Warning: the score model gives the point to the player who hit the net."
        }
        return lines
    }

    private fun lastShot(shot: LastShot?, nearName: String, farName: String): List<String> {
        if (shot == null) return listOf("Last shot: not found. The ball gives no winner.")
        val hitter = if (shot.toFar) nearName else farName
        val lines = ArrayList<String>()
        lines += "Last shot" + (shot.t?.let { " at ${time(it)}" } ?: "") + ": $hitter, to the ${if (shot.toFar) "far" else "near"} end" +
            (shot.speed?.let { ", ${it.roundToInt()} km/h" } ?: "")
        val bounce = when (shot.src) {
            2 -> "bounce seen"
            1 -> "bounce from the flight"
            else -> "no bounce"
        }
        val case = shot.case.orEmpty()
        lines += when {
            shot.nearWins == null -> "Ball rule: no decision ($bounce)"
            case.startsWith("net") -> "Ball rule: the ball stopped at the net. $hitter loses."
            case.startsWith("in") -> "Ball rule: in" + (shot.margin?.let { ", ${meters(it)} inside the lines" } ?: "") + " ($bounce), no shot back. $hitter wins."
            shot.netZ != null && shot.netZ <= LOW_NET_M && (shot.margin ?: 0.0) > 0 ->
                "Ball rule: too low at the net (${meters(shot.netZ)}). $hitter loses."
            else -> "Ball rule: out" + (shot.margin?.let { margin ->
                if (margin < 0) " by ${meters(-margin)}" else ", only ${meters(margin)} inside the lines"
            } ?: "") + " ($bounce). $hitter loses."
        }
        if (case.endsWith("_early")) {
            lines += "Warning: this shot is ${seconds(abs(shot.tRel ?: 0.0))} before the end of the rally. A later shot was probably missed."
        }
        return lines
    }

    private fun winner(rally: DetectedRally, nearName: String, farName: String): List<String> {
        fun side(pNear: Double) = if (pNear >= 0.5) "${percent(pNear)} $nearName" else "${percent(1 - pNear)} $farName"
        val ball = rally.pBall
        val model = rally.pNearWins ?: return emptyList()
        val lines = ArrayList<String>()
        lines += "Ball: " + (if (ball == null || abs(ball - 0.5) < 0.005) "no evidence" else side(ball)) + " → score model: " + side(model)
        if (ball != null && abs(ball - 0.5) >= 0.005 && (ball > 0.5) != (model > 0.5)) {
            lines += "Warning: the score model changed the winner of the ball rule."
        }
        return lines
    }

    private fun percent(value: Double) = "${(value * 100).roundToInt()} %"

    private fun meters(value: Double) = String.format(Locale.ROOT, "%.2f m", value)

    private fun seconds(value: Double) = String.format(Locale.ROOT, "%.1f s", value)

    private fun time(seconds: Double) = Timecode.format((seconds * 1000).toLong())

    /** The ball rule calls a ball that is lower than this over the net line "not over the net". */
    private const val LOW_NET_M = 0.85
}
