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

    /** The shot panel for a video time, or null when the time is not in a point with a shot chain. */
    var shotPanel: (Double) -> ShotPanel? = { null }

    /** The shots of the selected point. The overlay marks the start of each shot at the ball. */
    var shotStarts: () -> List<ShotView> = { emptyList() }

    private val ballTimes = DoubleArray(data.ball.size) { data.ball[it][0] }
    private val otherTimes = DoubleArray(data.ballOther.size) { data.ballOther[it][0] }
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

        // A text on a dark box, so the text is readable on a bright video.
        fun label(left: Double, top: Double, text: String, size: Double, color: java.awt.Color) {
            val width = org.litvin.ui.commons.UiKit.textWidth(text, java.awt.Font(FONT, java.awt.Font.BOLD, 100)) / 100.0 * size
            shapes += OverlayShape.Rect(
                left - 0.45 * size, top - 0.2 * size, width + 0.9 * size, 1.4 * size,
                fill = Palette.withAlpha(Palette.SCRIM, LABEL_BOX_ALPHA), radius = 0.25 * size,
            )
            shapes += OverlayShape.Text(left, top, text, size, color, FONT, bold = true)
        }
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
        // The candidates of the detector that are not the ball of a flight: a red ring, for a short time.
        if (OverlayLayer.BALL_OTHER in layers) {
            var index = firstIndexFrom(otherTimes, t - OTHER_SHOWN_S)
            while (index < otherTimes.size && otherTimes[index] <= t) {
                val row = data.ballOther[index++]
                if (row.size >= 3) shapes += OverlayShape.Circle(x(row[1]), y(row[2]), 6.0 * unit, stroke = Palette.RED, strokeWidth = 1.5 * unit)
            }
        }
        // The speed of a serve, for a short time after the serve.
        if (OverlayLayer.SERVES in layers) data.serves.lastOrNull { it.t <= t && t - it.t <= SERVE_SHOWN_S && it.kind != "nearToss" }?.let { serve ->
            val text = "Serve ${serve.speedKmh.roundToInt()} km/h · ${if (serve.near) "near end" else "far end"}"
            label(area.x + 16.0 * unit, area.y + area.height - 44.0 * unit, text, 26.0 * unit, Palette.FG)
        }
        // The shot panel: a box of a constant size at the top right, with the number of the shot, the hitter, the
        // times, and the outcome.
        if (OverlayLayer.SHOTS in layers) shotPanel(t)?.let { panel ->
            val width = PANEL_WIDTH * unit
            val left = area.x + area.width - 16.0 * unit - width
            val top = area.y + 14.0 * unit
            shapes += OverlayShape.Rect(left, top, width, PANEL_HEIGHT * unit, fill = Palette.withAlpha(Palette.SCRIM, LABEL_BOX_ALPHA), radius = 8.0 * unit)
            val x0 = left + 16.0 * unit
            shapes += OverlayShape.Text(x0, top + 8.0 * unit, panel.number, 56.0 * unit, Palette.FG, FONT, bold = true)
            if (panel.who.isNotEmpty()) shapes += OverlayShape.Text(x0, top + 74.0 * unit, panel.who.take(PANEL_NAME_CHARS), 22.0 * unit, panel.whoColor, FONT, bold = true)
            if (panel.time.isNotEmpty()) shapes += OverlayShape.Text(x0, top + 104.0 * unit, panel.time, 16.0 * unit, Palette.FG_2, FONT)
            if (panel.outcome.isNotEmpty()) shapes += OverlayShape.Text(x0, top + 128.0 * unit, panel.outcome, 19.0 * unit, panel.outcomeColor, FONT, bold = true)
        }
        // The start of a shot: a ring in the color of the hitter at the first ball position of the shot, with its number.
        if (OverlayLayer.SHOTS in layers) shotStarts().forEach { shot ->
            val start = shot.startMs / 1000.0
            if (t < start || t - start > SHOT_START_SHOWN_S) return@forEach
            // The first ball position from the start of the shot. With none near that time: the last one before it.
            val index = firstIndexFrom(ballTimes, start)
            val row = listOfNotNull(data.ball.getOrNull(index), data.ball.getOrNull(index - 1))
                .firstOrNull { it.size >= 3 && kotlin.math.abs(it[0] - start) <= SHOT_START_NEAR_S } ?: return@forEach
            val cx = x(row[1])
            val cy = y(row[2])
            shapes += OverlayShape.Circle(cx, cy, 15.0 * unit, stroke = shot.color, strokeWidth = 3.0 * unit)
            label(cx + 22.0 * unit, cy - 10.0 * unit, "Shot ${shot.number}", 15.0 * unit, shot.color)
        }
        // The court diagram: a top view of the court with the end of each shot of the selected point up to this time.
        if (OverlayLayer.DIAGRAM in layers) diagram(t, area, unit, shapes)
        // The flight of this time: the path of its fit, and the landing point of the fit before the bounce.
        var line = 0
        fun note(text: String, color: java.awt.Color = Palette.FG) {
            label(area.x + 16.0 * unit, area.y + (14.0 + 24.0 * line++) * unit, text, 17.0 * unit, color)
        }
        if (OverlayLayer.SHOTS in layers) {
            data.flights.lastOrNull { it.good && t >= it.t0 && t <= it.t1 + SHOT_SHOWN_S }?.let { flight ->
                // The path of the fit: white for the measured part, yellow and thin for the predicted part.
                flight.curve?.let { curve ->
                    val obsEnd = flight.curveObsEnd ?: flight.t1
                    // The path stops at the first sample far out of the picture: a bad fit can pass the plane of the
                    // camera, and then its path goes to infinity and comes back from the other side.
                    fun far(p: List<Double>) = p.size < 3 || p[1] !in -data.width.toDouble()..2.0 * data.width ||
                        p[2] !in -data.height.toDouble()..2.0 * data.height
                    val end = curve.indexOfFirst(::far).let { if (it < 0) curve.size else it }
                    curve.subList(0, end).zipWithNext().forEach { (a, b) ->
                        val measured = b[0] <= obsEnd + 1e-6
                        shapes += OverlayShape.Line(
                            x(a[1]), y(a[2]), x(b[1]), y(b[2]),
                            if (measured) Palette.FG else Palette.YELLOW, (if (measured) 1.5 else 1.0) * unit,
                        )
                    }
                }
                val land = flight.land
                val landT = flight.landT?.takeUnless(Double::isNaN)
                if (land != null && landT != null && t < landT) {
                    shapes += OverlayShape.Circle(x(land[0]), y(land[1]), 7.0 * unit, stroke = Palette.FG, strokeWidth = 1.0 * unit)
                }
            }
        }
        // The events of the ball of the last second: bounce, net, hit.
        if (OverlayLayer.EVENTS in layers) {
            data.events.forEach { event ->
                val px = event.px?.takeIf { it.size >= 2 } ?: return@forEach
                if (t < event.t || t - event.t > EVENT_SHOWN_S) return@forEach
                val cx = x(px[0])
                val cy = y(px[1])
                val size = 9.0 * unit
                when (event.kind) {
                    "bounce" -> {
                        // A crosshair with the color of the court diagram: green for in, red for out.
                        val color = bounceColor(event)
                        shapes += OverlayShape.Line(cx - size, cy, cx + size, cy, color, 2.0 * unit)
                        shapes += OverlayShape.Line(cx, cy - size, cx, cy + size, color, 2.0 * unit)
                    }
                    "net", "cord" -> shapes += OverlayShape.Rect(
                        cx - size, cy - size, 2 * size, 2 * size, stroke = Palette.RED, strokeWidth = 2.5 * unit, rotationDeg = 45.0,
                    )
                    "hit" -> shapes += OverlayShape.Circle(cx, cy, 6.0 * unit, stroke = Palette.FG, strokeWidth = 1.5 * unit)
                    else -> return@forEach
                }
                val text = when (event.kind) {
                    "bounce" -> "bounce" + (event.court?.takeIf { it.size >= 2 }?.let { " ${meters(it[0])}, ${meters(it[1])}" } ?: "")
                    "net" -> "net"
                    "cord" -> "net cord"
                    else -> null
                }
                if (text != null) label(cx + 14.0 * unit, cy - 22.0 * unit, text, 13.0 * unit, Palette.FG)
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

    /**
     * The court diagram, below the shot panel. The near end is at the bottom, as on the video. A dot is the end of a
     * shot: green for in, red for out or a bounce on the side of the hitter, yellow for net (on the net line, on the
     * side of the hitter). A ring with no fill is a predicted end. The newest end has a white ring and its shot number.
     */
    private fun diagram(t: Double, area: Rectangle2D.Double, unit: Double, shapes: MutableList<OverlayShape>) {
        val scale = DIAGRAM_WIDTH / (2 * DIAGRAM_HALF_X) * unit
        val width = DIAGRAM_WIDTH * unit
        val height = 2 * DIAGRAM_HALF_Y * scale
        val left = area.x + area.width - 16.0 * unit - width
        val top = area.y + (14.0 + PANEL_HEIGHT + 10.0) * unit

        fun dx(m: Double) = left + (m.coerceIn(-DIAGRAM_HALF_X + 0.4, DIAGRAM_HALF_X - 0.4) + DIAGRAM_HALF_X) * scale
        fun dy(m: Double) = top + (DIAGRAM_HALF_Y - m.coerceIn(-DIAGRAM_HALF_Y + 0.4, DIAGRAM_HALF_Y - 0.4)) * scale
        shapes += OverlayShape.Rect(left, top, width, height, fill = Palette.withAlpha(Palette.SCRIM, LABEL_BOX_ALPHA), radius = 8.0 * unit)
        COURT_LINES.forEach { (x1, y1, x2, y2) ->
            shapes += OverlayShape.Line(dx(x1), dy(y1), dx(x2), dy(y2), Palette.FG_2, 1.0 * unit)
        }
        shapes += OverlayShape.Line(dx(-COURT_DOUBLES - 0.9), dy(0.0), dx(COURT_DOUBLES + 0.9), dy(0.0), Palette.FG, 2.0 * unit)
        val ended = shotStarts().filter { shot -> !shot.afterPoint && shot.endColor != null && shot.endMs != null && shot.endMs / 1000.0 <= t }
        ended.forEachIndexed { index, shot ->
            val color = shot.endColor ?: return@forEachIndexed
            // A net touch has no place on the court: the dot is on the net line, a little on the side of the hitter.
            val place = shot.court ?: if (shot.end == "net") listOf(0.0, if (shot.near == false) 0.5 else -0.5) else return@forEachIndexed
            val cx = dx(place[0])
            val cy = dy(place[1])
            val newest = index == ended.lastIndex
            val radius = (if (newest) 4.5 else 3.5) * unit
            shapes += if (shot.predicted) {
                OverlayShape.Circle(cx, cy, radius, stroke = color, strokeWidth = 1.5 * unit)
            } else {
                OverlayShape.Circle(cx, cy, radius, fill = color)
            }
            if (newest) {
                shapes += OverlayShape.Circle(cx, cy, radius + 0.75 * unit, stroke = Palette.FG, strokeWidth = 1.5 * unit)
                shapes += OverlayShape.Text(cx + 8.0 * unit, cy - 8.0 * unit, shot.number.toString(), 13.0 * unit, Palette.FG, FONT, bold = true)
            }
        }
        // The ball of this time: a small white dot at the place of the fitted flight over the court.
        if (OverlayLayer.DIAGRAM_BALL in layers) {
            ballOnCourt(t)?.takeIf { (bx, by) -> kotlin.math.abs(bx) <= DIAGRAM_HALF_X && kotlin.math.abs(by) <= DIAGRAM_HALF_Y }?.let { (bx, by) ->
                shapes += OverlayShape.Circle(dx(bx), dy(by), 2.5 * unit, fill = Palette.FG)
            }
        }
    }

    /**
     * The place of the ball over the court at [t] in m (x across, y along), from the path of the fitted flight of that
     * time. Null: no flight at that time, or an overlay file of an older detection (its paths have no court places).
     */
    private fun ballOnCourt(t: Double): Pair<Double, Double>? {
        val rows = data.flights.lastOrNull { flight ->
            flight.good && flight.curve?.let { it.isNotEmpty() && t >= it.first()[0] && t <= it.last()[0] } == true
        }?.curve ?: return null
        val next = rows.indexOfFirst { it[0] >= t }.takeIf { it >= 0 } ?: return null
        val b = rows[next]
        val a = rows[maxOf(0, next - 1)]
        if (a.size < 5 || b.size < 5) return null
        val part = if (b[0] > a[0]) (t - a[0]) / (b[0] - a[0]) else 0.0
        return (a[3] + part * (b[3] - a[3])) to (a[4] + part * (b[4] - a[4]))
    }

    /**
     * The color of a bounce, as on the court diagram. The bounce that ends a shot of the selected point has the color
     * of that end (red also for a bounce on the side of the hitter). Another bounce is green in the singles court and
     * red out of it. Yellow: the place on the court is not known.
     */
    private fun bounceColor(event: org.litvin.autodetect.OverlayEvent): java.awt.Color {
        val ms = event.t * 1000.0
        shotStarts().firstOrNull { !it.predicted && it.endMs != null && kotlin.math.abs(it.endMs - ms) <= BOUNCE_MATCH_MS }?.endColor?.let { return it }
        val court = event.court?.takeIf { it.size >= 2 } ?: return Palette.YELLOW
        return if (kotlin.math.abs(court[0]) <= COURT_SINGLES && kotlin.math.abs(court[1]) <= COURT_BASE) Palette.GREEN else Palette.RED
    }

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

        /** The mark of a shot start stays this long. It is at a ball position not farther in time than SHOT_START_NEAR_S. */
        const val SHOT_START_SHOWN_S = 0.8
        const val SHOT_START_NEAR_S = 0.2

        /** The size of the shot panel, and the longest player name in it. */
        const val PANEL_WIDTH = 230.0
        const val PANEL_HEIGHT = 160.0
        const val PANEL_NAME_CHARS = 16

        /** The court diagram: its width, and the half sizes of its view in m (the court is 10.97 x 23.77 m). */
        const val DIAGRAM_WIDTH = 150.0
        const val DIAGRAM_HALF_X = 8.0
        const val DIAGRAM_HALF_Y = 15.5
        const val COURT_DOUBLES = 5.485
        private const val COURT_SINGLES = 4.115
        private const val COURT_BASE = 11.885
        private const val COURT_SERVICE = 6.40

        /** The lines of a tennis court in m: x1, y1, x2, y2. */
        val COURT_LINES = listOf(
            listOf(-COURT_DOUBLES, -COURT_BASE, COURT_DOUBLES, -COURT_BASE), listOf(-COURT_DOUBLES, COURT_BASE, COURT_DOUBLES, COURT_BASE),
            listOf(-COURT_DOUBLES, -COURT_BASE, -COURT_DOUBLES, COURT_BASE), listOf(COURT_DOUBLES, -COURT_BASE, COURT_DOUBLES, COURT_BASE),
            listOf(-COURT_SINGLES, -COURT_BASE, -COURT_SINGLES, COURT_BASE), listOf(COURT_SINGLES, -COURT_BASE, COURT_SINGLES, COURT_BASE),
            listOf(-COURT_SINGLES, -COURT_SERVICE, COURT_SINGLES, -COURT_SERVICE), listOf(-COURT_SINGLES, COURT_SERVICE, COURT_SINGLES, COURT_SERVICE),
            listOf(0.0, -COURT_SERVICE, 0.0, COURT_SERVICE),
        )

        /** A rejected ball candidate stays this long: 3 frames of a 60 fps video. */
        const val OTHER_SHOWN_S = 0.05

        /** A bounce event is the end of a shot if their times are not farther apart than this. */
        const val BOUNCE_MATCH_MS = 60.0

        /** The mark of an event stays this long. */
        const val EVENT_SHOWN_S = 1.0

        /** The box behind a text is black with this opacity (0 to 255): 90 %. */
        const val LABEL_BOX_ALPHA = 230
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
    BALL_OTHER("Rejected ball candidates"),
    LANDINGS("Fit landing"),
    EVENTS("Bounces, net"),
    SERVES("Serve speed"),
    POSES("Players"),
    SHOTS("Shots"),
    OUTCOME("Outcome"),
    DIAGRAM("Court diagram"),
    DIAGRAM_BALL("Ball on the court diagram"),
}

/** The outcome texts of a rally for the overlay. [shotT] is the start time of its last shot in seconds, or null. */
internal data class OutcomeMark(val lines: List<String>, val shotT: Double?)
