package org.litvin.ui.tabs.points.ui

import org.litvin.points.PointV1
import org.litvin.points.ReplayTimeline
import org.litvin.points.ReplayV1
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineScaleTest {
    // A point from 2 s to 4 s with one replay of 1 s at half speed, twice: the replay adds 4 s.
    private val point = PointV1("p1", 2_000, 4_000, replays = listOf(ReplayV1(3_000, 4_000, speed = 0.5, repeats = 2)))
    private val scale = TimelineScale(ReplayTimeline(listOf(point)), 10_000)

    @Test
    fun thePlayTotalIncludesTheReplays() {
        assertEquals(14_000, scale.playTotalMs)
        assertEquals(listOf(TimelineScale.Block("p1", 0, 4_000, 8_000)), scale.blocks)
    }

    @Test
    fun aPointEndMapsToTheStartOfItsBlock() {
        assertEquals(3_000, scale.playAt(3_000))
        assertEquals(4_000, scale.playAt(4_000))
        assertEquals(9_000, scale.playAt(5_000))
    }

    @Test
    fun aTimeInsideABlockMapsToThePointEnd() {
        assertEquals(3_000, scale.sourceAt(3_000))
        assertEquals(4_000, scale.sourceAt(6_000))
        assertEquals(5_000, scale.sourceAt(9_000))
        assertEquals(10_000, scale.sourceAt(20_000))
    }

    @Test
    fun theXOfASourceTimeSkipsTheBlock() {
        // 140 px for 14 s: 10 px for each second.
        assertEquals(30, scale.xAtSource(3_000, 0, 140))
        assertEquals(90, scale.xAtSource(5_000, 0, 140))
        assertEquals(6_000, scale.playAtX(60, 0, 140))
    }

    @Test
    fun withoutReplaysTheScaleIsLinear() {
        val plain = TimelineScale(ReplayTimeline(listOf(PointV1("p1", 2_000, 4_000))), 10_000)
        assertEquals(10_000, plain.playTotalMs)
        assertEquals(emptyList(), plain.blocks)
        assertEquals(50, plain.xAtSource(5_000, 0, 100))
        assertEquals(5_000, plain.sourceAt(plain.playAtX(50, 0, 100)))
    }
}
