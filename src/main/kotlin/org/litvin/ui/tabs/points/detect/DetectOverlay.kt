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
 * model, the ball with a trail, the landing points of the flights, and the speed of the serves. It is a debug view.
 *
 * The data has points in px of the source frame. [plan] gives the crop of the preview. The view does not follow a
 * rotation of the frame: with a rotation, the marks are near their objects but not on them.
 */
internal class DetectOverlay(private val data: DetectOverlayData, private val plan: () -> GeometryPlan?) {
    private val ballTimes = DoubleArray(data.ball.size) { data.ball[it][0] }

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
        courtAt(t)?.let { court ->
            (court.lines + listOf(court.net)).filter { it.size == 4 }.forEachIndexed { index, line ->
                val color = if (index == court.lines.size) Palette.YELLOW else Palette.BLUE
                shapes += OverlayShape.Line(x(line[0]), y(line[1]), x(line[2]), y(line[3]), color, 1.5 * unit)
            }
        }
        // The landing points of the flights that ended a short time ago.
        data.flights.forEach { flight ->
            val landT = flight.landT?.takeUnless(Double::isNaN) ?: return@forEach
            val land = flight.land ?: return@forEach
            if (flight.good && t >= landT && t - landT <= LAND_SHOWN_S) {
                shapes += OverlayShape.Circle(x(land[0]), y(land[1]), 7.0 * unit, stroke = Palette.RED, strokeWidth = 2.0 * unit)
            }
        }
        // The ball: a trail of the last detections, and a ring at the newest one.
        val last = lastBallIndex(t)
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
        data.serves.lastOrNull { it.t <= t && t - it.t <= SERVE_SHOWN_S && it.kind != "nearToss" }?.let { serve ->
            val text = "Serve ${serve.speedKmh.roundToInt()} km/h · ${if (serve.near) "near end" else "far end"}"
            shapes += OverlayShape.Text(area.x + 16.0 * unit, area.y + area.height - 44.0 * unit, text, 26.0 * unit, Palette.FG, FONT, bold = true)
        }
        return shapes
    }

    private fun courtAt(t: Double): OverlayCourt? =
        data.court.firstOrNull { t >= it.t0 && t < it.t1 } ?: data.court.minByOrNull { kotlin.math.abs((it.t0 + it.t1) / 2 - t) }

    /** The index of the last ball row at or before [t], or -1. */
    private fun lastBallIndex(t: Double): Int {
        val found = ballTimes.binarySearch(t)
        return if (found >= 0) found else -found - 2
    }

    private companion object {
        /** The sizes of the marks are for a video that shows this wide, and they scale with the video. */
        const val REFERENCE_WIDTH = 1280.0
        const val TRAIL_S = 0.5
        const val BALL_FRESH_S = 0.08
        const val LAND_SHOWN_S = 1.0
        const val SERVE_SHOWN_S = 2.5
        const val FONT = "Arial"
    }
}
