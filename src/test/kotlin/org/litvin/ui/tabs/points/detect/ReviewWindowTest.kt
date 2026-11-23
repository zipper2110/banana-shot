package org.litvin.ui.tabs.points.detect

import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.points.AutoKind
import org.litvin.points.AutoMarkV1
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.ui.tabs.points.ui.ScorePlayers
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewWindowTest {
    private class Host(var list: List<PointV1>) : ReviewHost {
        val winners = mutableMapOf<String, Outcome>()
        override val players = ScorePlayers("Anna", "Bea")
        override fun points() = list.sortedBy { it.startMs }
        override fun winnerOf(pointId: String) = winners[pointId] ?: Outcome.NONE

        override fun setWinner(pointId: String, winner: Outcome) {
            winners[pointId] = winner
        }

        override fun setBounds(pointId: String, startMs: Long, endMs: Long): Boolean {
            if (endMs - startMs < 200) return false
            if (list.any { it.id != pointId && startMs < it.endMs && it.startMs < endMs }) return false
            list = list.map { if (it.id == pointId) it.copy(startMs = startMs.toInt(), endMs = endMs.toInt()) else it }
            return true
        }

        override fun deletePoint(pointId: String) {
            list = list.filter { it.id != pointId }
        }

        override fun addPoint(startMs: Long, endMs: Long): String? {
            list = list + PointV1(id = "new", startMs = startMs.toInt(), endMs = endMs.toInt())
            return "new"
        }

        override fun markChecked(pointIds: Collection<String>) {
            list = list.map { if (it.id in pointIds) it.copy(auto = it.auto?.copy(checked = true)) else it }
        }

        override fun swapWinners(pointIds: Set<String>) {
            pointIds.forEach { id ->
                winners[id] = when (winners[id]) {
                    Outcome.P1 -> Outcome.P2
                    Outcome.P2 -> Outcome.P1
                    else -> Outcome.NONE
                }
            }
        }

        fun open() = list.filter { it.auto?.checked == false }.map { it.id }
    }

    private class Player : SwingMediaPlayer {
        override val component: Component = JPanel()
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        var timeMs = 0L
        var playing = false
        var closed = false
        override fun load(file: File) = Unit
        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun seek(ms: Long) { timeMs = ms }
        override fun setRate(rate: Float) = Unit
        override fun status() = if (playing) PlayerStatus.PLAYING else PlayerStatus.PAUSED
        override fun currentTimeMs() = timeMs
        override fun totalDurationMs() = 600_000L
        override fun isAdjustSupported() = true
        override fun applyColorAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyGeometryAdjustments(adj: AdjustmentsV1) = true
        override fun applyPreviewAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyPreviewRotation(rotationDeg: Float, reason: String) = Unit
        override fun setPreviewOverlay(overlay: VideoOverlay?) = Unit
        override fun stepFrameForward(maximumTimeMs: Long) = 0L
        override fun stepFrameBackward(minimumTimeMs: Long) = 0L
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File) = true
        override fun activatePreview(reason: String) = Unit
        override fun deactivatePreview(reason: String) = Unit
        override fun close() { closed = true }
    }

    private fun auto(id: String, startMs: Int, checked: Boolean = false, percent: Int? = 80) =
        PointV1(id = id, startMs = startMs, endMs = startMs + 10_000, auto = AutoMarkV1(AutoKind.RALLY, percent, checked))

    private class Opened(val window: ReviewWindow, val host: Host, val player: Player) {
        var closedCalls = 0
        fun click(name: String) = (window.contentPane.find(name) as JButton).doClick()
        fun key(key: String) = window.rootPane.actionMap.get("review.$key").actionPerformed(null)
        fun text(name: String) = (window.contentPane.find(name) as JLabel).text
    }

    private fun review(vararg points: PointV1, block: Opened.() -> Unit) {
        SwingUtilities.invokeAndWait {
            val host = Host(points.toList())
            val player = Player()
            lateinit var opened: Opened
            val window = ReviewWindow(null, player, File("video.mp4"), AdjustmentsV1(), host, null, null) { opened.closedCalls++ }
            opened = Opened(window, host, player)
            try {
                opened.block()
            } finally {
                window.close()
            }
        }
    }

    @Test
    fun `the window opens at the first point to review`() = review(auto("a", 10_000, checked = true), auto("b", 30_000), auto("c", 50_000)) {
        assertEquals("To review: 2 of 3 points", text("review-count"))
        assertTrue(text("review-point-title").startsWith("Point 2 "))
        assertEquals(30_000L, player.timeMs)
        assertFalse(player.playing)
        snapshot(window, "review-window")
    }

    @Test
    fun `Accept, next accepts the selected point and plays the next point to review`() = review(auto("a", 10_000), auto("b", 30_000)) {
        click("review-accept")

        assertEquals(listOf("b"), host.open())
        assertTrue(text("review-point-title").startsWith("Point 2 "))
        assertTrue(player.playing)

        key("T")
        assertTrue(host.open().isEmpty())
        assertEquals("All 2 points are reviewed", text("review-count"))
    }

    @Test
    fun `a winner key gives the winner and accepts the point`() = review(auto("a", 10_000), auto("b", 30_000)) {
        key("E")

        assertEquals(Outcome.P2, host.winners["a"])
        assertEquals(listOf("b"), host.open())
    }

    @Test
    fun `I and O put the start and the end of the point at the playhead`() = review(auto("a", 10_000), auto("b", 30_000)) {
        player.timeMs = 8_500
        key("I")
        player.timeMs = 24_000
        key("O")

        val point = host.list.first { it.id == "a" }
        assertEquals(8_500 to 24_000, point.startMs to point.endMs)
    }

    @Test
    fun `an end over the next point is refused with a message`() = review(auto("a", 10_000), auto("b", 30_000)) {
        player.timeMs = 35_000
        key("O")

        assertEquals(20_000, host.list.first { it.id == "a" }.endMs)
        assertTrue(text("review-message").startsWith("The point cannot have these times"))
    }

    @Test
    fun `Add point adds a point at the playhead up to the next point`() = review(auto("a", 10_000), auto("b", 30_000)) {
        player.timeMs = 27_000
        key("N")

        val added = host.list.first { it.id == "new" }
        assertEquals(27_000 to 30_000, added.startMs to added.endMs)

        player.timeMs = 12_000
        key("N")
        assertEquals("No room for a new point at the playhead.", text("review-message"))
    }

    @Test
    fun `Accept all points and close accepts each point and closes the window`() = review(auto("a", 10_000), auto("b", 30_000)) {
        click("review-accept-all")

        assertTrue(host.open().isEmpty())
        assertTrue(player.closed)
        assertEquals(1, closedCalls)
    }

    @Test
    fun `Continue later closes the window and keeps the review open`() = review(auto("a", 10_000), auto("b", 30_000)) {
        click("review-later")

        assertEquals(listOf("a", "b"), host.open())
        assertEquals(1, closedCalls)
    }

    @Test
    fun `a drag of an edge of the bounds bar gives the new times`() {
        SwingUtilities.invokeAndWait {
            val commits = mutableListOf<Pair<Long, Long>>()
            val bar = ReviewBoundsBar(onPreview = {}, onCommit = { start, end -> commits += start to end }, onSeek = {})
            // 1020 px wide with a pad of 10 px: 1000 px for 20 s, so 50 px for each second.
            bar.setSize(1020, 62)
            bar.setState(0, 20_000, 8_000, 12_000, minStart = 5_000, maxEnd = 20_000, others = listOf(0L..5_000L))
            fun mouse(id: Int, x: Int) = bar.dispatchEvent(MouseEvent(bar, id, 0L, 0, x, 30, 1, false, MouseEvent.BUTTON1))

            mouse(MouseEvent.MOUSE_PRESSED, 610)
            mouse(MouseEvent.MOUSE_DRAGGED, 760)
            mouse(MouseEvent.MOUSE_RELEASED, 760)
            assertEquals(listOf(8_000L to 15_000L), commits)

            // The start edge stops at the end of the point before it.
            bar.setState(0, 20_000, 8_000, 15_000, minStart = 5_000, maxEnd = 20_000, others = listOf(0L..5_000L))
            mouse(MouseEvent.MOUSE_PRESSED, 410)
            mouse(MouseEvent.MOUSE_DRAGGED, 100)
            mouse(MouseEvent.MOUSE_RELEASED, 100)
            assertEquals(5_000L to 15_000L, commits.last())
        }
    }

    /** Writes a picture of [window] when the property `detect.snapshot` names a folder. For a check by eye. */
    private fun snapshot(window: ReviewWindow, name: String) {
        val folder = System.getProperty("detect.snapshot") ?: System.getenv("DETECT_SNAPSHOT") ?: return
        window.setSize(1300, 780)
        window.addNotify()
        window.validate()
        val pane = window.contentPane
        val image = BufferedImage(pane.width.coerceAtLeast(1), pane.height.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        pane.printAll(g)
        g.dispose()
        ImageIO.write(image, "png", File(folder, "$name.png"))
    }

}

private fun Container.find(name: String): Component? {
    for (child in components) {
        if (child.name == name) return child
        if (child is Container) child.find(name)?.let { return it }
    }
    return null
}
