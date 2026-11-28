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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewWindowTest {
    private class Host(var list: List<PointV1>) : ReviewHost {
        val winners = mutableMapOf<String, Outcome>()
        var warningList = emptyList<String>()
        var ends = emptySet<String>()
        override fun gameEnds() = ends
        val servers = mutableMapOf<String, Int>()
        override fun serverOf(pointId: String) = servers[pointId]
        override fun switchServer(pointId: String) {
            servers[pointId] = if (servers[pointId] == 1) 2 else 1
        }
        override val players = ScorePlayers("Anna", "Bea")
        override fun warnings() = warningList
        override fun points() = list.sortedBy { it.startMs }
        override fun winnerOf(pointId: String) = winners[pointId] ?: Outcome.NONE
        override fun nearPlayerOf(pointId: String) = list.firstOrNull { it.id == pointId }?.auto?.nearPlayer

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
            list = list.map { point ->
                val near = point.auto?.nearPlayer
                if (point.id in pointIds && near != null) point.copy(auto = point.auto?.copy(nearPlayer = 3 - near)) else point
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
        override fun stepFrameForward(maximumTimeMs: Long): Long { timeMs += 33; return timeMs }
        override fun stepFrameBackward(minimumTimeMs: Long): Long { timeMs -= 33; return timeMs }
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File) = true
        override fun activatePreview(reason: String) = Unit
        override fun deactivatePreview(reason: String) = Unit
        override fun close() { closed = true }
    }

    private fun auto(id: String, startMs: Int, checked: Boolean = false, percent: Int? = 80, near: Int? = 1) =
        PointV1(id = id, startMs = startMs, endMs = startMs + 10_000, auto = AutoMarkV1(AutoKind.RALLY, percent, checked, near))

    private class Opened(val window: ReviewWindow, val host: Host, val player: Player) {
        var closedCalls = 0
        fun click(name: String) = (window.contentPane.find(name) as JButton).doClick()
        fun key(key: String) = window.rootPane.actionMap.get("review.$key").actionPerformed(null)
        fun text(name: String) = (window.contentPane.find(name) as JLabel).text
    }

    private fun review(vararg points: PointV1, warnings: List<String> = emptyList(), block: Opened.() -> Unit) {
        SwingUtilities.invokeAndWait {
            val host = Host(points.toList()).apply { warningList = warnings }
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
    fun `the window shows the warning of the detection result`() =
        review(auto("a", 10_000, near = null), warnings = listOf("No tennis court found in the video.")) {
            assertTrue(text("review-warning").contains("No tennis court found in the video."))
        }

    @Test
    fun `the window shows all warnings of the detection result`() =
        review(auto("a", 10_000, near = null), warnings = listOf("No refined court model.", "The shot chain failed on 2 rallies.")) {
            assertTrue(text("review-warning").contains("No refined court model."))
            assertTrue(text("review-warning").contains("The shot chain failed on 2 rallies."))
        }

    @Test
    fun `the window has no warning line when the result has no warning`() = review(auto("a", 10_000)) {
        assertNull(window.contentPane.find("review-warning"))
    }

    @Test
    fun `the overlay settings window is not modal and has a switch for each overlay part`() = review(auto("a", 10_000)) {
        click("review-overlay-settings")
        val dialog = window.ownedWindows.filterIsInstance<javax.swing.JDialog>().single { it.name == "review-overlay-dialog" }
        assertTrue(dialog.isVisible)
        assertFalse(dialog.isModal)
        OverlayLayer.entries.forEach { layer ->
            assertNotNull(dialog.contentPane.find("review-overlay-${layer.name.lowercase()}"), layer.name)
        }
        // The switch of all parts stays in the review window.
        assertNotNull(window.contentPane.find("review-overlay-all"))
        assertNull(window.contentPane.find("review-overlay-ball"))
        window.close()
        assertFalse(dialog.isDisplayable)
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
    fun `the window names the player at the near end, and Swap players changes all automatic points`() =
        review(auto("a", 10_000, near = 1), auto("b", 30_000, near = 2), auto("c", 50_000, near = null)) {
            host.winners["a"] = Outcome.P1
            assertEquals("Near end: Anna", text("review-near"))

            window.swapPlayers(SwapScope.ALL)
            assertEquals("Near end: Bea", text("review-near"))
            assertEquals(Outcome.P2, host.winners["a"])
            assertEquals(listOf(2, 1, null), host.list.map { it.auto?.nearPlayer })
            // A swap is not a review of the points.
            assertEquals(3, host.open().size)

            key("DOWN")
            key("DOWN")
            assertEquals("Near end: not known", text("review-near"))
        }

    @Test
    fun `Swap players can change one point, or the point and the next points`() =
        review(auto("a", 10_000, near = 1), auto("b", 30_000, near = 1), auto("c", 50_000, near = 1)) {
            key("DOWN")
            window.swapPlayers(SwapScope.POINT)
            assertEquals(listOf(1, 2, 1), host.list.map { it.auto?.nearPlayer })

            window.swapPlayers(SwapScope.FROM_POINT)
            assertEquals(listOf(1, 1, 2), host.list.map { it.auto?.nearPlayer })
            assertEquals("Swapped the players of 2 points.", text("review-message"))
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
    fun `G plays the next long gap and stops at the point after it`() {
        // Points of 10 s with gaps of 20 s, and one gap of 60 s before point 6.
        val starts = listOf(10_000, 40_000, 70_000, 100_000, 130_000, 200_000, 230_000, 260_000, 290_000)
        review(*starts.mapIndexed { index, start -> auto("p$index", start) }.toTypedArray()) {
            player.timeMs = 0
            key("G")
            assertEquals(140_000L, player.timeMs)
            assertTrue(player.playing)
            assertEquals("Long gap 1 of 1: +60 s before point 6. N adds a point.", text("review-message"))
            assertTrue((window.contentPane.find("review-next-gap") as JButton).isEnabled)

            // After the last long gap, G goes to the first one again.
            player.timeMs = 250_000
            key("G")
            assertEquals(140_000L, player.timeMs)
        }
    }

    @Test
    fun `the window shows the server of the score, and S gives the serve to the other player`() = review(auto("a", 10_000), auto("b", 30_000)) {
        assertEquals("Serves: not known (S sets the server)", text("review-server"))
        key("S")
        assertEquals("Serves: Anna", text("review-server"))
        click("review-switch-server")
        assertEquals("Serves: Bea", text("review-server"))
        assertEquals(mapOf("a" to 2), host.servers)
    }

    @Test
    fun `with no long gap, G says so and the button is off`() = review(auto("a", 10_000), auto("b", 30_000), auto("c", 50_000)) {
        assertFalse((window.contentPane.find("review-next-gap") as JButton).isEnabled)
        key("G")
        assertEquals("No long gap between the points.", text("review-message"))
    }

    @Test
    fun `Accept all points and close accepts each point and closes the window`() = review(auto("a", 10_000), auto("b", 30_000)) {
        click("review-accept-all")

        assertTrue(host.open().isEmpty())
        assertTrue(player.closed)
        assertEquals(1, closedCalls)
    }

    @Test
    fun `the detection note field shows four rows`() = review(auto("a", 10_000), auto("b", 30_000)) {
        window.setSize(1300, 780)
        window.addNotify()
        window.validate()
        snapshot(window, "review-window-note")
        val note = window.contentPane.find("review-note") as javax.swing.JTextArea
        val rows = note.getFontMetrics(note.font).height * 4

        assertTrue(note.visibleRect.height >= rows, "visible ${note.visibleRect.height} px, 4 rows are $rows px")
        assertEquals(null, window.contentPane.find("review-last-shot"))
    }

    @Test
    fun `with Frame step on, Left and Right step one frame while the video is paused`() = review(auto("a", 10_000), auto("b", 30_000)) {
        val before = org.litvin.SessionSettings.frameStepWhenPaused
        try {
            org.litvin.SessionSettings.frameStepWhenPaused = false
            key("F")
            assertTrue(org.litvin.SessionSettings.frameStepWhenPaused)
            player.pause()
            val start = player.timeMs

            key("RIGHT")
            assertEquals(start + 33, player.timeMs)
            key("LEFT")
            assertEquals(start, player.timeMs)

            key("F")
            key("RIGHT")
            assertEquals(start + 1_000, player.timeMs)
        } finally {
            org.litvin.SessionSettings.frameStepWhenPaused = before
        }
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
