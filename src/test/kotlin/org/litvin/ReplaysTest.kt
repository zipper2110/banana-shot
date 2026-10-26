package org.litvin

import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.points.ReplayZoom
import org.litvin.points.Replays
import org.litvin.points.components.PointsDispatcher
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReplaysTest {
    private val point = PointV1(id = "P", startMs = 10_000, endMs = 20_000)

    @Test
    fun `a new replay is 1,5 s before and after the playhead`() {
        assertEquals(ReplayV1(startMs = 13_500, endMs = 16_500), Replays.newAt(point, 15_000))
    }

    @Test
    fun `a new replay near the window edge keeps its length inside the window`() {
        assertEquals(ReplayV1(startMs = 7_000, endMs = 10_000), Replays.newAt(point, 7_200))
        assertEquals(ReplayV1(startMs = 20_000, endMs = 23_000), Replays.newAt(point, 22_900))
    }

    @Test
    fun `the window does not start before the video`() {
        val early = PointV1(id = "E", startMs = 1_000, endMs = 5_000)
        assertEquals(0, Replays.windowStartMs(early))
        assertEquals(ReplayV1(startMs = 0, endMs = 3_000), Replays.newAt(early, 500))
    }

    @Test
    fun `a changed point cuts its replays to the new window`() {
        val replays = listOf(
            ReplayV1(startMs = 8_000, endMs = 12_000),
            ReplayV1(startMs = 18_000, endMs = 22_000),
        )
        val moved = point.copy(startMs = 12_000, endMs = 18_000, replays = replays)
        assertEquals(
            listOf(ReplayV1(startMs = 9_000, endMs = 12_000), ReplayV1(startMs = 18_000, endMs = 21_000)),
            Replays.fitTo(moved),
        )
    }

    @Test
    fun `a replay shorter than 0,5 s after the cut is removed`() {
        val moved = point.copy(startMs = 16_000, replays = listOf(ReplayV1(startMs = 10_000, endMs = 13_400)))
        assertEquals(emptyList(), Replays.fitTo(moved))
    }

    @Test
    fun `the replays keep the play order of the user`() {
        val replays = listOf(ReplayV1(startMs = 15_000, endMs = 17_000), ReplayV1(startMs = 11_000, endMs = 12_000))
        assertEquals(listOf(15_000, 11_000), Replays.fitTo(point.copy(replays = replays)).map { it.startMs })
    }

    @Test
    fun `the editor speeds are inside the limits and include the default`() {
        assertEquals(listOf(0.1, 0.2, 0.3, 0.5, 1.0), Replays.SPEEDS)
        assertTrue(Replays.SPEEDS.all { it in Replays.MIN_SPEED..Replays.MAX_SPEED })
        assertTrue(Replays.DEFAULT_SPEED in Replays.SPEEDS)
    }

    @Test
    fun `speed and repeats go into their limits`() {
        val fitted = Replays.fitTo(
            point.copy(
                replays = listOf(
                    ReplayV1(startMs = 11_000, endMs = 12_000, speed = 0.01, repeats = 0),
                    ReplayV1(startMs = 12_000, endMs = 13_000, speed = 3.0, repeats = 9),
                    ReplayV1(startMs = 13_000, endMs = 14_000, speed = Double.NaN),
                ),
            ),
        )
        assertEquals(listOf(0.1, 1.0, 0.5), fitted.map { it.speed })
        assertEquals(listOf(1, 5, 1), fitted.map { it.repeats })
    }

    @Test
    fun `a zoom frame stays inside the frame and is not smaller than 4x`() {
        assertEquals(ReplayZoom(0.75, 0.0, 0.25), Replays.normalizeZoom(ReplayZoom(0.9, -0.2, 0.25)))
        // A frame that is too small grows around its center.
        assertEquals(ReplayZoom(0.375, 0.375, 0.25), Replays.normalizeZoom(ReplayZoom(0.45, 0.45, 0.1)))
        assertNull(Replays.normalizeZoom(ReplayZoom(0.0, 0.0, 1.0)))
        assertNull(Replays.normalizeZoom(ReplayZoom(Double.NaN, 0.0, 0.5)))
    }

    @Test
    fun `a point without changes stays the same object`() {
        val withReplay = point.copy(replays = listOf(ReplayV1(startMs = 11_000, endMs = 12_000)))
        assertSame(withReplay, Replays.fitted(withReplay))
    }

    @Test
    fun `the added time is the part length divided by the speed for each repeat`() {
        assertEquals(12_000L, Replays.addedMs(ReplayV1(startMs = 0, endMs = 2_000, speed = 0.5, repeats = 3)))
    }

    @Test
    fun `edl json keeps the replays`() {
        val path = Files.createTempDirectory("edl_replay_").resolve("edl.json").toFile().absolutePath
        val replay = ReplayV1(
            startMs = 12_000,
            endMs = 14_000,
            zoom = ReplayZoom(0.25, 0.3, 0.5),
            speed = 0.25,
            repeats = 2,
            muted = true,
            effect = ReplayEffect.FLASH,
            showBadge = false,
        )
        val edl = EdlV1(points = listOf(point.copy(replays = listOf(replay))))
        EdlIO.write(path, edl)
        assertEquals(edl, EdlIO.read(path))
    }

    @Test
    fun `a point without replays writes no replays key`() {
        val file = Files.createTempDirectory("edl_no_replay_").resolve("edl.json").toFile()
        EdlIO.write(file.absolutePath, EdlV1(points = listOf(point)))
        assertFalse(file.readText().contains("replays"))
        assertEquals(emptyList(), EdlIO.read(file.absolutePath).points.single().replays)
    }

    @Test
    fun `read fits replays and reads an unknown effect as fade`() {
        val file = Files.createTempDirectory("edl_bad_replay_").resolve("edl.json").toFile()
        file.writeText(
            """
            {"version": 1, "points": [{"id": "P", "startMs": 10000, "endMs": 20000, "replays": [
              {"startMs": 1000, "endMs": 12000, "speed": 0.5, "effect": "SPIN"},
              {"startMs": 30000, "endMs": 31000}
            ]}]}
            """.trimIndent(),
        )
        val replays = EdlIO.read(file.absolutePath).points.single().replays
        assertEquals(listOf(ReplayV1(startMs = 7_000, endMs = 12_000, effect = ReplayEffect.FADE)), replays)
    }

    @Test
    fun `a point edit in the dispatcher fits the replays`() {
        val dispatcher = PointsDispatcher()
        dispatcher.setPoints(listOf(point.copy(replays = listOf(ReplayV1(startMs = 8_000, endMs = 12_000)))))
        assertTrue(dispatcher.updatePoint("P", 12_000, 20_000, null))
        assertEquals(
            listOf(ReplayV1(startMs = 9_000, endMs = 12_000)),
            dispatcher.getCompletedPoints().single().replays,
        )
    }

    @Test
    fun `delete removes the replays with the point`() {
        val dispatcher = PointsDispatcher()
        dispatcher.setPoints(listOf(point.copy(replays = listOf(ReplayV1(startMs = 11_000, endMs = 12_000)))))
        assertTrue(dispatcher.deletePoint("P"))
        assertTrue(dispatcher.getCompletedPoints().isEmpty())
    }

    @Test
    fun `only the first play of the first replay of a point zooms in`() {
        assertTrue(Replays.zoomsIn(replayIndex = 0, repeat = 0))
        assertFalse(Replays.zoomsIn(replayIndex = 0, repeat = 1))
        assertFalse(Replays.zoomsIn(replayIndex = 1, repeat = 0))
    }

    @Test
    fun `the export replays know their place in the point`() {
        val withTwo = point.copy(replays = listOf(ReplayV1(startMs = 9_000, endMs = 12_000), ReplayV1(startMs = 10_000, endMs = 12_000)))
        val replays = org.litvin.export.ExportReplays.of(listOf(withTwo), emptyList(), "REPLAY")
        assertEquals(listOf(0, 1), replays.map { it.replayIndex })
    }
}
