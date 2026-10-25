package org.litvin

import org.litvin.points.PointV1
import org.litvin.points.ReplayTimeline
import org.litvin.points.ReplayV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayTimelineTest {
    // Point A: 10 s to 20 s with one replay of 2 s at 0.5 (4 s) played twice (8 s).
    // Point B: 30 s to 40 s with two replays: 1 s at 1.0 (1 s), and 1 s at 0.25 (4 s).
    private val a = PointV1(
        id = "A", startMs = 10_000, endMs = 20_000,
        replays = listOf(ReplayV1(startMs = 15_000, endMs = 17_000, speed = 0.5, repeats = 2)),
    )
    private val b = PointV1(
        id = "B", startMs = 30_000, endMs = 40_000,
        replays = listOf(
            ReplayV1(startMs = 31_000, endMs = 32_000, speed = 1.0),
            ReplayV1(startMs = 35_000, endMs = 36_000, speed = 0.25),
        ),
    )
    private val plain = PointV1(id = "C", startMs = 50_000, endMs = 55_000)
    private val timeline = ReplayTimeline(listOf(plain, b, a))

    @Test
    fun `inserts follow the point ends and skip points without replays`() {
        assertEquals(listOf("A", "B"), timeline.inserts.map { it.point.id })
        assertEquals(listOf(20_000L, 48_000L), timeline.inserts.map { it.startMs })
        assertEquals(listOf(8_000L, 5_000L), timeline.inserts.map { it.durationMs })
        assertEquals(13_000L, timeline.addedMs)
        assertEquals(73_000L, timeline.totalMs(60_000))
    }

    @Test
    fun `each repeat is one play`() {
        val plays = timeline.inserts.first().plays
        assertEquals(listOf(0, 1), plays.map { it.repeat })
        assertEquals(listOf(20_000L, 24_000L), plays.map { it.startMs })
        assertTrue(plays[0].isFirst && !plays[0].isLast && plays[1].isLast)
    }

    @Test
    fun `play time of a source time counts the inserts before it`() {
        assertEquals(5_000L, timeline.playMsAtSource(5_000))
        // A point end is after its replays.
        assertEquals(28_000L, timeline.playMsAtSource(20_000))
        assertEquals(53_000L, timeline.playMsAtSource(40_000))
    }

    @Test
    fun `a position in a replay gives the source frame at the replay speed`() {
        val position = timeline.positionAt(21_000)
        assertIs<ReplayTimeline.Position.InReplay>(position)
        assertEquals(0, position.play.repeat)
        assertEquals(15_500L, position.sourceMs)
        val second = timeline.positionAt(26_000)
        assertIs<ReplayTimeline.Position.InReplay>(second)
        assertEquals(1, second.play.repeat)
        assertEquals(16_000L, second.sourceMs)
    }

    @Test
    fun `a position after the inserts is in source time`() {
        assertEquals(ReplayTimeline.Position.Source(19_999), timeline.positionAt(19_999))
        assertEquals(ReplayTimeline.Position.Source(20_000), timeline.positionAt(28_000))
        assertEquals(ReplayTimeline.Position.Source(45_000), timeline.positionAt(58_000))
    }

    @Test
    fun `position and play time agree`() {
        for (play in listOf(0L, 19_000L, 22_500L, 27_999L, 48_400L, 51_000L, 60_000L)) {
            val position = timeline.positionAt(play)
            if (position is ReplayTimeline.Position.Source) assertEquals(play, timeline.playMsAtSource(position.sourceMs))
            if (position is ReplayTimeline.Position.InReplay) {
                // A source millisecond lasts 1 / speed milliseconds of play time.
                val tolerance = kotlin.math.ceil(1 / position.play.replay.speed).toLong()
                val back = position.play.playAt(position.sourceMs)
                assertTrue(kotlin.math.abs(back - play) <= tolerance, "play $play gave $back")
            }
        }
    }

    @Test
    fun `a player finds the point end that it crossed`() {
        assertEquals("A", timeline.insertCrossed(19_990, 20_005)?.point?.id)
        assertNull(timeline.insertCrossed(20_000, 20_040))
        assertNull(timeline.insertCrossed(25_000, 26_000))
    }
}
