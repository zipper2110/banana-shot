package org.litvin.ui.tabs.points.detect

import org.junit.jupiter.api.Test
import org.litvin.autodetect.ChainShot
import org.litvin.autodetect.DetectOverlayData
import org.litvin.autodetect.DetectedRally
import org.litvin.autodetect.PredictedEnd
import org.litvin.autodetect.ShotChain
import org.litvin.media.OverlayShape
import org.litvin.ui.commons.Palette
import org.litvin.ui.tabs.points.ui.ScorePlayers
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShotViewsTest {
    private val players = ScorePlayers("Dima", "Alex", Color(0x4DA3FF), Color(0xFF6B6B))

    /** The far player serves, the ball comes back, the far player hits, and the last end is not seen. */
    private val rally = DetectedRally(
        start = 1067.0,
        end = 1077.0,
        chain = ShotChain(
            case = "in",
            nearWins = false,
            shots = listOf(
                ChainShot(t = 1066.767, near = false, how = "first", end = "in", court = listOf(-2.22, -4.23), margin = 1.89, tLast = 1069.434, tEnd = 1068.834),
                ChainShot(t = 1069.934, near = true, how = "back", end = "out", court = listOf(-3.53, 12.4), margin = -0.52, tLast = 1071.483, tEnd = 1070.765),
                ChainShot(t = 1071.525, near = false, how = "hit", end = "net", tLast = 1073.083, tEnd = 1072.806),
                ChainShot(t = 1073.616, near = true, how = "gap", end = "none", tLast = 1074.416),
            ),
        ),
    )

    @Test
    fun `each shot has its hitter, its times, and its outcome`() {
        val shots = ShotViews.of(rally, near = 1, players)

        assertEquals(listOf("Alex", "Dima", "Alex", "Dima"), shots.map { it.who })
        assertEquals(listOf(players.p2Color, players.p1Color, players.p2Color, players.p1Color), shots.map { it.color })
        assertEquals(1_066_767L, shots[0].startMs)
        // A shot is on the timeline until the next shot starts. The last shot is there until its last ball sample.
        assertEquals(1_069_934L, shots[0].untilMs)
        assertEquals(1_074_416L, shots[3].untilMs)
        assertEquals("In at -2.2, -4.2 m", shots[0].outcome)
        assertEquals("Out by 0.5 m", shots[1].outcome)
        assertEquals("Net", shots[2].outcome)
        assertEquals("End not seen", shots[3].outcome)
        assertEquals(listOf(Palette.GREEN, Palette.RED, Palette.YELLOW, null), shots.map { it.endColor })
        assertNull(shots[3].endMs)
    }

    @Test
    fun `a shot with no seen end shows the predicted end of its flight`() {
        val shot = ChainShot(
            t = 13.71, near = true, how = "hit", end = "none", tLast = 14.595,
            predicted = PredictedEnd(t = 14.562, end = "out", court = listOf(-4.53, 9.59), margin = -0.42, sigma = 0.35),
        )
        val view = ShotViews.of(DetectedRally(start = 10.0, end = 19.0, chain = ShotChain(shots = listOf(shot))), near = 1, players).single()

        assertTrue(view.predicted)
        assertEquals("Predicted: out by 0.4 m", view.outcome)
        assertEquals(Palette.RED, view.endColor)
        assertEquals(14_562L, view.endMs)
    }

    @Test
    fun `a shot that starts after the end of the rally is not a shot of the point`() {
        val shots = listOf(
            ChainShot(t = 106.36, near = true, how = "hit", end = "net", tEnd = 106.88, tLast = 106.88),
            ChainShot(t = 110.88, near = true, how = "gap", end = "out", tEnd = 111.54, tLast = 111.53, margin = -10.5),
        )
        val views = ShotViews.of(DetectedRally(start = 88.0, end = 109.0, chain = ShotChain(shots = shots)), near = 1, players)

        assertEquals(listOf(false, true), views.map { it.afterPoint })
        assertEquals("After the point", views[1].outcome)
        assertNull(views[1].endColor)
        assertEquals(Palette.FG_3, views[1].color)
    }

    @Test
    fun `with no known near player the shots name the ends`() {
        assertEquals(listOf("Far player", "Near player"), ShotViews.of(rally, near = null, players).take(2).map { it.who })
    }

    @Test
    fun `the panel shows the shot in progress, and its outcome from the end of the shot`() {
        val shots = ShotViews.of(rally, near = 1, players)

        assertEquals("–", ShotViews.panelAt(shots, 1_066_000).number)
        val flying = ShotViews.panelAt(shots, 1_068_000)
        assertEquals("1", flying.number)
        assertEquals("Alex", flying.who)
        assertEquals("17:46.7 to 17:49.9", flying.time)
        assertEquals("In the air", flying.outcome)
        val landed = ShotViews.panelAt(shots, 1_069_000)
        assertEquals("In at -2.2, -4.2 m", landed.outcome)
        assertEquals(Palette.GREEN, landed.outcomeColor)
        assertEquals("4", ShotViews.panelAt(shots, 1_080_000).number)
    }

    @Test
    fun `the overlay draws the panel with the same box for each shot`() {
        val shots = ShotViews.of(rally, near = 1, players)
        val overlay = DetectOverlay(DetectOverlayData(width = 1920, height = 1080)) { null }
        overlay.layers = setOf(OverlayLayer.SHOTS)
        overlay.shotPanel = { t -> ShotViews.panelAt(shots, (t * 1000).toLong()) }
        val area = Rectangle2D.Double(0.0, 0.0, 1280.0, 720.0)

        val boxes = listOf(1066.0, 1068.0, 1070.9, 1076.0).map { t -> overlay.shapes(t, area).filterIsInstance<OverlayShape.Rect>().single() }

        assertEquals(1, boxes.toSet().size)
        assertTrue(boxes[0].x + boxes[0].width <= area.width - 10)
        snapshot(overlay.shapes(1070.9, area), area)
    }

    @Test
    fun `the overlay marks the start of a shot at the ball`() {
        val shots = ShotViews.of(rally, near = 1, players)
        val ball = listOf(listOf(1069.90, 400.0, 300.0), listOf(1069.95, 410.0, 310.0), listOf(1070.00, 420.0, 320.0))
        val overlay = DetectOverlay(DetectOverlayData(width = 1920, height = 1080, ball = ball)) { null }
        overlay.layers = setOf(OverlayLayer.SHOTS)
        overlay.shotStarts = { shots }
        val area = Rectangle2D.Double(0.0, 0.0, 1920.0, 1080.0)

        // Shot 2 starts at 1069.934 s: the mark is at the first ball position from that time.
        val ring = overlay.shapes(1070.2, area).filterIsInstance<OverlayShape.Circle>().single()
        assertEquals(410.0, ring.centerX, 0.01)
        assertEquals(players.p1Color, ring.stroke)
        assertTrue(overlay.shapes(1070.2, area).filterIsInstance<OverlayShape.Text>().any { it.text == "Shot 2" })
        assertTrue(overlay.shapes(1071.0, area).isEmpty())
    }

    @Test
    fun `the path of a flight stops where the fit goes far out of the picture`() {
        // A bad fit passes the plane of the camera: its path goes to infinity and comes back from the other side.
        val curve = listOf(
            listOf(10.0, 875.0, 617.0), listOf(10.1, 620.0, 605.0), listOf(10.2, -37.0, -484.0),
            listOf(10.3, -4270.0, -8532.0), listOf(10.4, 17404.0, 33123.0), listOf(10.5, 1618.0, 2000.0),
        )
        val flight = org.litvin.autodetect.OverlayFlight(t0 = 10.0, t1 = 10.2, good = true, curve = curve, curveObsEnd = 10.2)
        val overlay = DetectOverlay(DetectOverlayData(width = 1920, height = 1080, flights = listOf(flight))) { null }
        overlay.layers = setOf(OverlayLayer.SHOTS)
        val area = Rectangle2D.Double(0.0, 0.0, 1920.0, 1080.0)
        val lines = overlay.shapes(10.1, area).filterIsInstance<OverlayShape.Line>()

        assertEquals(2, lines.size)
        assertEquals(-484.0, lines.last().y2, 0.5)
    }

    @Test
    fun `the court diagram shows the end of each shot up to the video time`() {
        val shots = ShotViews.of(rally, near = 1, players)
        val overlay = DetectOverlay(DetectOverlayData(width = 1920, height = 1080)) { null }
        overlay.layers = setOf(OverlayLayer.DIAGRAM)
        overlay.shotStarts = { shots }
        val area = Rectangle2D.Double(0.0, 0.0, 1280.0, 720.0)
        fun dots(t: Double) = overlay.shapes(t, area).filterIsInstance<OverlayShape.Circle>().mapNotNull { it.fill }

        // The court is there before the first bounce. The bounces come one after the other: in, out, net.
        assertTrue(dots(1067.0).isEmpty())
        assertEquals(10, overlay.shapes(1067.0, area).filterIsInstance<OverlayShape.Line>().size)
        assertEquals(listOf(Palette.GREEN), dots(1069.0))
        assertEquals(listOf(Palette.GREEN, Palette.RED), dots(1071.0))
        assertEquals(listOf(Palette.GREEN, Palette.RED, Palette.YELLOW), dots(1076.0))
        // The first shot is from the far player and ends at x = -2.22 m, y = -4.23 m: its dot is at the lower left.
        val box = overlay.shapes(1069.0, area).filterIsInstance<OverlayShape.Rect>().single()
        val dot = overlay.shapes(1069.0, area).filterIsInstance<OverlayShape.Circle>().first { it.fill == Palette.GREEN }
        assertTrue(dot.centerY > box.y + box.height / 2)
        assertTrue(dot.centerX < box.x + box.width / 2)
        snapshot(overlay.shapes(1076.0, area), area, "court-diagram.png")
    }

    @Test
    fun `the bar draws the shot tracks`() {
        val folder = System.getProperty("detect.snapshot") ?: return
        val bar = ReviewBoundsBar({}, { _, _ -> }, {})
        bar.setSize(1100, bar.preferredSize.height)
        bar.setState(1_062_000, 1_082_000, 1_066_000, 1_078_000, 0, 2_000_000, emptyList())
        bar.setShots(ShotViews.of(rally, near = 1, players))
        bar.setPlayhead(1_070_900)
        val image = BufferedImage(bar.width, bar.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Palette.OVERLAY
        g.fillRect(0, 0, bar.width, bar.height)
        bar.paint(g)
        g.dispose()
        ImageIO.write(image, "png", File(folder, "shot-tracks.png"))
    }

    /** Writes a picture of the panel when the property `detect.snapshot` names a folder. For a check by eye. */
    private fun snapshot(shapes: List<OverlayShape>, area: Rectangle2D.Double, file: String = "shot-panel.png") {
        val folder = System.getProperty("detect.snapshot") ?: return
        val image = BufferedImage(area.width.toInt(), area.height.toInt(), BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0x6E7F6A)
        g.fillRect(0, 0, image.width, image.height)
        shapes.forEach { shape ->
            when (shape) {
                is OverlayShape.Rect -> {
                    g.color = shape.fill ?: return@forEach
                    g.fillRoundRect(shape.x.toInt(), shape.y.toInt(), shape.width.toInt(), shape.height.toInt(), (2 * shape.radius).toInt(), (2 * shape.radius).toInt())
                }
                is OverlayShape.Text -> {
                    g.color = shape.color
                    g.font = Font(shape.font, if (shape.bold) Font.BOLD else Font.PLAIN, shape.size.toInt())
                    g.drawString(shape.text, shape.x.toInt(), (shape.y + g.fontMetrics.ascent).toInt())
                }
                is OverlayShape.Line -> {
                    g.color = shape.color
                    g.stroke = java.awt.BasicStroke(shape.width.toFloat())
                    g.draw(java.awt.geom.Line2D.Double(shape.x1, shape.y1, shape.x2, shape.y2))
                }
                is OverlayShape.Circle -> {
                    val circle = java.awt.geom.Ellipse2D.Double(shape.centerX - shape.radius, shape.centerY - shape.radius, 2 * shape.radius, 2 * shape.radius)
                    shape.fill?.let { g.color = it; g.fill(circle) }
                    shape.stroke?.let { g.color = it; g.stroke = java.awt.BasicStroke(shape.strokeWidth.toFloat()); g.draw(circle) }
                }
                else -> Unit
            }
        }
        g.dispose()
        ImageIO.write(image, "png", File(folder, file))
    }
}
