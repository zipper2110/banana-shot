package org.litvin.ui.tabs.stats

import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.scoring.Sport
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.ScoreboardSceneImage
import org.litvin.ui.commons.UiKit
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.roundToInt

/**
 * Shows one page of the statistics card in a 16:9 frame, as the exported video shows it.
 * The export puts the card on a frozen frame of the video. The preview shows the same frame when it has it,
 * and a drawn court of the project sport until then.
 */
class StatsCardPreview : JComponent() {
    var pages: List<ScoreboardScene> = emptyList()
        set(value) {
            field = value
            page = page.coerceIn(0, (value.size - 1).coerceAtLeast(0))
            repaint()
        }

    var page: Int = 0
        set(value) {
            field = value
            repaint()
        }

    /** The video frame under the card, or null for the drawn court. */
    var background: BufferedImage? = null
        set(value) {
            field = value
            repaint()
        }

    /** The sport of the project. It selects the drawn court. */
    var sport: Sport = Sport.TENNIS
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    /** The text that shows when the card has no pages. */
    var emptyText: String = "Select at least one row with a value."

    init {
        name = "stats-preview"
        preferredSize = Dimension(512, 288)
        minimumSize = Dimension(320, 180)
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            // The largest 16:9 frame that fits, centered.
            val frameWidth = minOf(width.toDouble(), height * 16.0 / 9.0)
            val frameHeight = frameWidth * 9.0 / 16.0
            val x = (width - frameWidth) / 2
            val y = (height - frameHeight) / 2
            val frame = background
            if (frame == null) {
                when (sport) {
                    Sport.TENNIS -> drawCourt(g, x, y, frameWidth, frameHeight)
                    Sport.PADEL -> drawPadelCourt(g, x, y, frameWidth, frameHeight)
                }
            } else drawFrame(g, frame, x, y, frameWidth, frameHeight)
            val scene = pages.getOrNull(page)
            if (scene == null) {
                g.color = Palette.SCRIM
                g.fill(java.awt.geom.Rectangle2D.Double(x, y, frameWidth, frameHeight))
                g.color = Palette.FG_2
                val metrics = g.fontMetrics
                g.drawString(emptyText, (width - metrics.stringWidth(emptyText)) / 2, height / 2 + metrics.ascent / 2)
            } else {
                ScoreboardSceneImage.draw(g, scene, x, y, frameHeight / scene.height)
            }
            g.color = Palette.LINE
            g.stroke = BasicStroke(1f)
            g.draw(java.awt.geom.RoundRectangle2D.Double(x + 0.5, y + 0.5, frameWidth - 1, frameHeight - 1, 8.0, 8.0))
        } finally {
            g.dispose()
        }
    }

    /** The video frame, centered and scaled to fill the 16:9 frame. The parts outside the frame are cut off. */
    private fun drawFrame(g: Graphics2D, image: BufferedImage, x: Double, y: Double, w: Double, h: Double) {
        val scale = maxOf(w / image.width, h / image.height)
        val drawWidth = image.width * scale
        val drawHeight = image.height * scale
        val savedClip = g.clip
        g.clip(Rectangle2D.Double(x, y, w, h))
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(
            image,
            (x + (w - drawWidth) / 2).roundToInt(),
            (y + (h - drawHeight) / 2).roundToInt(),
            drawWidth.roundToInt(),
            drawHeight.roundToInt(),
            null,
        )
        g.clip = savedClip
    }

    /** A simple tennis court in perspective, in place of the video frame. */
    private fun drawCourt(g: Graphics2D, x: Double, y: Double, w: Double, h: Double) {
        g.color = Palette.COURT_SURROUND
        g.fill(java.awt.geom.Rectangle2D.Double(x, y, w, h))
        g.color = Palette.COURT_HARD
        val court = Path2D.Double().apply {
            moveTo(x + w * 0.33, y + h * 0.22)
            lineTo(x + w * 0.67, y + h * 0.22)
            lineTo(x + w * 0.86, y + h * 0.94)
            lineTo(x + w * 0.14, y + h * 0.94)
            closePath()
        }
        g.fill(court)
        g.color = Palette.COURT_LINE_2
        g.stroke = BasicStroke((h / 180).toFloat().coerceAtLeast(1f))
        g.draw(court)
        val netY = y + h * 0.5
        g.draw(java.awt.geom.Line2D.Double(x + w * 0.24, netY, x + w * 0.76, netY))
        g.draw(java.awt.geom.Line2D.Double(x + w * 0.5, y + h * 0.33, x + w * 0.5, y + h * 0.75))
    }

    /**
     * A simple padel court in perspective: 20 m × 10 m, the service lines 3 m from the back walls,
     * and glass walls at the far end and at the ends of the sides. The camera is behind the near back wall,
     * so that wall does not show.
     */
    private fun drawPadelCourt(g: Graphics2D, x: Double, y: Double, w: Double, h: Double) {
        g.color = Palette.COURT_SURROUND
        g.fill(Rectangle2D.Double(x, y, w, h))
        val view = PadelView(x, y, w, h)
        g.color = Palette.COURT_PADEL
        val court = view.area(0.0, 1.0, 0.0, 1.0)
        g.fill(court)

        // The glass: the far back wall, then the side panels (4 m from each back wall).
        val glassHeight = PADEL_GLASS_HEIGHT / PADEL_WIDTH
        val walls = listOf(
            view.wall(0.0, 0.0, 0.0, 1.0, glassHeight),
            view.wall(0.0, PADEL_SIDE_GLASS, 0.0, 0.0, glassHeight),
            view.wall(0.0, PADEL_SIDE_GLASS, 1.0, 1.0, glassHeight),
            view.wall(1.0 - PADEL_SIDE_GLASS, 1.0, 0.0, 0.0, glassHeight),
            view.wall(1.0 - PADEL_SIDE_GLASS, 1.0, 1.0, 1.0, glassHeight),
        )
        g.stroke = BasicStroke((h / 240).toFloat().coerceAtLeast(1f))
        for (wall in walls) {
            g.color = Palette.COURT_GLASS
            g.fill(wall)
            g.color = Palette.COURT_GLASS_EDGE
            g.draw(wall)
        }

        g.color = Palette.COURT_LINE_2
        g.stroke = BasicStroke((h / 180).toFloat().coerceAtLeast(1f))
        g.draw(court)
        val service = PADEL_SERVICE_LINE
        g.draw(view.line(service, 0.0, service, 1.0))
        g.draw(view.line(1.0 - service, 0.0, 1.0 - service, 1.0))
        g.draw(view.line(service, 0.5, 1.0 - service, 0.5))
        // The net goes from one side wall to the other.
        g.stroke = BasicStroke((h / 120).toFloat().coerceAtLeast(1.5f))
        g.draw(view.line(0.5, 0.0, 0.5, 1.0))
    }

    /**
     * The perspective of the padel court in the frame. A court point has a depth (0 at the far back wall,
     * 1 at the near back wall) and a side position (0 at the left wall, 1 at the right wall).
     * The court has the same outline in the frame as the tennis court.
     */
    private class PadelView(val x: Double, val y: Double, val w: Double, val h: Double) {
        /** The screen y of a depth. The far half of the court is shorter on the screen than the near half. */
        fun screenY(depth: Double): Double {
            val t = depth / (depth + FAR_SHRINK * (1 - depth))
            return y + h * (TOP + (BOTTOM - TOP) * t)
        }

        /** The court width in pixels at a screen y. */
        private fun widthAt(sy: Double): Double {
            val t = (sy - y - h * TOP) / (h * (BOTTOM - TOP))
            return w * ((FAR_RIGHT - FAR_LEFT) + ((NEAR_RIGHT - NEAR_LEFT) - (FAR_RIGHT - FAR_LEFT)) * t)
        }

        fun point(depth: Double, side: Double): java.awt.geom.Point2D.Double {
            val sy = screenY(depth)
            val t = (sy - y - h * TOP) / (h * (BOTTOM - TOP))
            val left = x + w * (FAR_LEFT + (NEAR_LEFT - FAR_LEFT) * t)
            return java.awt.geom.Point2D.Double(left + widthAt(sy) * side, sy)
        }

        fun line(depth1: Double, side1: Double, depth2: Double, side2: Double): java.awt.geom.Line2D.Double =
            java.awt.geom.Line2D.Double(point(depth1, side1), point(depth2, side2))

        fun area(depth1: Double, depth2: Double, side1: Double, side2: Double): Path2D.Double = Path2D.Double().apply {
            val corners = listOf(point(depth1, side1), point(depth1, side2), point(depth2, side2), point(depth2, side1))
            moveTo(corners[0].x, corners[0].y)
            corners.drop(1).forEach { lineTo(it.x, it.y) }
            closePath()
        }

        /**
         * A vertical wall on the court line from (depth1, side1) to (depth2, side2). [height] is a part of the court
         * width, so that the wall gets smaller with the distance.
         */
        fun wall(depth1: Double, depth2: Double, side1: Double, side2: Double, height: Double): Path2D.Double {
            val a = point(depth1, side1)
            val b = point(depth2, side2)
            val ha = widthAt(a.y) * height
            val hb = widthAt(b.y) * height
            return Path2D.Double().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(b.x, b.y - hb)
                lineTo(a.x, a.y - ha)
                closePath()
            }
        }

        private companion object {
            const val TOP = 0.22
            const val BOTTOM = 0.94
            const val FAR_LEFT = 0.33
            const val FAR_RIGHT = 0.67
            const val NEAR_LEFT = 0.14
            const val NEAR_RIGHT = 0.86
            /** Puts the net at the same screen height as the net of the tennis court (0.5 of the frame). */
            const val FAR_SHRINK = 1.571
        }
    }

    private companion object {
        const val PADEL_WIDTH = 10.0
        const val PADEL_GLASS_HEIGHT = 3.0
        /** The service lines are 3 m from the back walls, on a court of 20 m. */
        const val PADEL_SERVICE_LINE = 3.0 / 20.0
        /** The side glass is 4 m long at each end. */
        const val PADEL_SIDE_GLASS = 4.0 / 20.0
    }
}
