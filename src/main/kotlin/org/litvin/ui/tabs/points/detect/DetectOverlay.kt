package org.litvin.ui.tabs.points.detect

import org.litvin.adjustments.GeometryPlan
import org.litvin.autodetect.DetectOverlayData
import org.litvin.autodetect.OverlayCourt
import org.litvin.media.OverlayShape
import org.litvin.media.mpv.MpvAssOverlay
import org.litvin.ui.commons.Palette
import java.awt.geom.Rectangle2D
import kotlin.math.roundToInt

/**
 * Draws the data of a detection run on the preview (decision 19 of docs/auto-analysis/app-integration.md): the court
 * model, the ball with a trail, the landing points of the flights, the speed of the serves, and the body poses. It is
 * a debug view.
 *
 * The pose stage reads one picture each second. A skeleton stays on the video until the next picture has poses.
 * So a skeleton can be behind a person who moves.
 *
 * [layers] are the parts that the overlay draws. Each part has its own switch in the review window.
 *
 * The data has points in px of the source frame. [plan] gives the crop of the preview. The view does not follow a
 * rotation of the frame: with a rotation, the marks are near their objects but not on them.
 */
internal class DetectOverlay(private val data: DetectOverlayData, private val plan: () -> GeometryPlan?) {
    var layers: Set<OverlayLayer> = OverlayLayer.entries.toSet()

    /** The outcome texts for a video time, or null when no rally decides its winner near that time. */
    var outcome: (Double) -> OutcomeMark? = { null }

    private val ballTimes = DoubleArray(data.ball.size) { data.ball[it][0] }
    private val poseTimes = DoubleArray(data.poses.size) { data.poses[it][0] }

    /** The ASS events for the video time [timeMs], for a video that shows in [area] (overlay pixels). */
    fun events(timeMs: Long, area: Rectangle2D.Double): List<String> {
        val shapes = shapes(timeMs / 1000.0, area)
        if (shapes.isEmpty()) return emptyList()
        return MpvAssOverlay.events(shapes, 1.0).split("\n").filter { it.isNotEmpty() }
    }

    internal fun shapes(t: Double, area: Rectangle2D.Double): List<OverlayShape> {
        val crop = plan()?.crop ?: GeometryPlan.FULL_FRAME
        val unit = area.width / REFERENCE_WIDTH

        fun x(px: Double) = area.x + ((px / data.width - crop.x) / crop.width) * area.width
        fun y(px: Double) = area.y + ((px / data.height - crop.y) / crop.height) * area.height

        val shapes = ArrayList<OverlayShape>()
        if (OverlayLayer.COURT in layers) courtAt(t)?.let { court ->
            (court.lines + listOf(court.net)).filter { it.size == 4 }.forEachIndexed { index, line ->
                val color = if (index == court.lines.size) Palette.YELLOW else Palette.BLUE
                shapes += OverlayShape.Line(x(line[0]), y(line[1]), x(line[2]), y(line[3]), color, 1.5 * unit)
            }
        }
        // The body poses of the last picture with poses at or before this time.
        var pose = if (OverlayLayer.POSES in layers) firstIndexFrom(poseTimes, Math.nextUp(t)) - 1 else -1
        val poseTime = if (pose >= 0) poseTimes[pose] else 0.0
        while (pose >= 0 && poseTimes[pose] == poseTime) {
            val row = data.poses[pose--]
            if (row.size < 1 + 2 * BODY_POINTS) continue
            fun seen(point: Int) = row[1 + 2 * point] >= 0
            fun px(point: Int) = x(row[1 + 2 * point])
            fun py(point: Int) = y(row[2 + 2 * point])
            BONES.forEach { (a, b) ->
                if (seen(a) && seen(b)) shapes += OverlayShape.Line(px(a), py(a), px(b), py(b), Palette.FG, 1.5 * unit)
            }
            (0 until BODY_POINTS).filter { it == NOSE || it >= FIRST_BODY_JOINT }.forEach { point ->
                if (seen(point)) shapes += OverlayShape.Circle(px(point), py(point), 2.5 * unit, fill = Palette.YELLOW)
            }
        }
        // The landing points of the flights that ended a short time ago.
        if (OverlayLayer.LANDINGS in layers) data.flights.forEach { flight ->
            val landT = flight.landT?.takeUnless(Double::isNaN) ?: return@forEach
            val land = flight.land ?: return@forEach
            if (flight.good && t >= landT && t - landT <= LAND_SHOWN_S) {
                shapes += OverlayShape.Circle(x(land[0]), y(land[1]), 7.0 * unit, stroke = Palette.RED, strokeWidth = 2.0 * unit)
            }
        }
        // The ball: a trail of the last detections, and a ring at the newest one.
        val last = if (OverlayLayer.BALL in layers) lastBallIndex(t) else -1
        if (last >= 0) {
            var index = last
            while (index >= 0 && t - ballTimes[index] <= TRAIL_S) {
                val age = (t - ballTimes[index]) / TRAIL_S
                val row = data.ball[index]
                shapes += OverlayShape.Circle(x(row[1]), y(row[2]), (3.5 - 2.0 * age) * unit, fill = Palette.LIME)
                index--
            }
            if (t - ballTimes[last] <= BALL_FRESH_S) {
                val row = data.ball[last]
                shapes += OverlayShape.Circle(x(row[1]), y(row[2]), 9.0 * unit, stroke = Palette.LIME, strokeWidth = 2.0 * unit)
            }
        }
        // The speed of a serve, for a short time after the serve.
        if (OverlayLayer.SERVES in layers) data.serves.lastOrNull { it.t <= t && t - it.t <= SERVE_SHOWN_S && it.kind != "nearToss" }?.let { serve ->
            val text = "Serve ${serve.speedKmh.roundToInt()} km/h · ${if (serve.near) "near end" else "far end"}"
            shapes += OverlayShape.Text(area.x + 16.0 * unit, area.y + area.height - 44.0 * unit, text, 26.0 * unit, Palette.FG, FONT, bold = true)
        }
        // The shot (a fitted flight) of this time: its data as a text, and the landing point of the fit before the bounce.
        var line = 0
        fun note(text: String, color: java.awt.Color = Palette.FG) {
            shapes += OverlayShape.Text(area.x + 16.0 * unit, area.y + (14.0 + 24.0 * line++) * unit, text, 17.0 * unit, color, FONT, bold = true)
        }
        if (OverlayLayer.SHOTS in layers) {
            data.flights.lastOrNull { it.good && t >= it.t0 && t <= it.t1 + SHOT_SHOWN_S }?.let { flight ->
                val parts = ArrayList<String>()
                parts += "Shot ${time(flight.t0)} to ${time(flight.t1)}"
                flight.toFar?.let { parts += if (it) "to the far end" else "to the near end" }
                parts += "${flight.speedKmh.roundToInt()} km/h"
                flight.start?.takeIf { it.size >= 3 }?.let { parts += "from ${meters(it[0])}, ${meters(it[1])}, height ${meters(it[2])}" }
                flight.netZ?.let { parts += "net ${meters(it)}" }
                flight.landM?.takeIf { it.size >= 2 }?.let { parts += "lands ${meters(it[0])}, ${meters(it[1])}" }
                note(parts.joinToString(" · "))
                val land = flight.land
                val landT = flight.landT?.takeUnless(Double::isNaN)
                if (land != null && landT != null && t < landT) {
                    shapes += OverlayShape.Circle(x(land[0]), y(land[1]), 7.0 * unit, stroke = Palette.FG, strokeWidth = 1.0 * unit)
                }
            }
        }
        // The decision of the winner: its texts, and a ring at the landing point of the last shot.
        if (OverlayLayer.OUTCOME in layers) {
            outcome(t)?.let { mark ->
                mark.lines.forEach { note(it, if (it.startsWith("Warning")) Palette.YELLOW else Palette.FG) }
                data.flights.firstOrNull { mark.shotT != null && kotlin.math.abs(it.t0 - mark.shotT) < 0.05 }?.land?.let { land ->
                    shapes += OverlayShape.Circle(x(land[0]), y(land[1]), 13.0 * unit, stroke = Palette.YELLOW, strokeWidth = 2.5 * unit)
                }
            }
        }
        return shapes
    }

    private fun time(seconds: Double) = String.format(java.util.Locale.ROOT, "%.2f", seconds % 60)

    private fun meters(value: Double) = String.format(java.util.Locale.ROOT, "%.1f", value)

    private fun courtAt(t: Double): OverlayCourt? =
        data.court.firstOrNull { t >= it.t0 && t < it.t1 } ?: data.court.minByOrNull { kotlin.math.abs((it.t0 + it.t1) / 2 - t) }

    /** The index of the first of the sorted [times] at or after [t]. */
    private fun firstIndexFrom(times: DoubleArray, t: Double): Int {
        var low = 0
        var high = times.size
        while (low < high) {
            val middle = (low + high) / 2
            if (times[middle] < t) low = middle + 1 else high = middle
        }
        return low
    }

    /** The index of the last ball row at or before [t], or -1. */
    private fun lastBallIndex(t: Double): Int {
        val found = ballTimes.binarySearch(t)
        return if (found >= 0) found else -found - 2
    }

    companion object {
        /** The sizes of the marks are for a video that shows this wide, and they scale with the video. */
        const val REFERENCE_WIDTH = 1280.0
        const val TRAIL_S = 0.5
        const val BALL_FRESH_S = 0.08
        const val LAND_SHOWN_S = 1.0
        const val SERVE_SHOWN_S = 2.5

        /** The text of a shot stays this long after the end of its flight. */
        const val SHOT_SHOWN_S = 0.4
        const val FONT = "Arial"

        const val BODY_POINTS = 17
        const val NOSE = 0

        /** The body points 1 to 4 are the eyes and the ears: the overlay draws no dot for them. */
        const val FIRST_BODY_JOINT = 5

        /** The lines of a skeleton, as pairs of COCO body points: the arms, the body, and the legs. */
        val BONES = listOf(
            5 to 7, 7 to 9, 6 to 8, 8 to 10, 5 to 6, 5 to 11, 6 to 12, 11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
        )
    }
}

/** A part of the detection overlay. [text] is the text of its switch. */
internal enum class OverlayLayer(val text: String) {
    COURT("Court"),
    BALL("Ball"),
    LANDINGS("Landing points"),
    SERVES("Serve speed"),
    POSES("Players"),
    SHOTS("Shots"),
    OUTCOME("Outcome"),
}

/** The outcome texts of a rally for the overlay. [shotT] is the start time of its last shot in seconds, or null. */
internal data class OutcomeMark(val lines: List<String>, val shotT: Double?)
