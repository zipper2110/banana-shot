package org.litvin.ui.tabs.points.detect

import org.junit.jupiter.api.Test
import org.litvin.points.PointV1
import kotlin.test.assertEquals

class ReviewGapsTest {
    /** Points of [lengthMs] with the gaps [gapsMs] between them. The first point starts at 10 s. */
    private fun points(gapsMs: List<Long>, lengthMs: Int = 8_000): List<PointV1> {
        var start = 10_000L
        val result = mutableListOf(PointV1(id = "p0", startMs = start.toInt(), endMs = (start + lengthMs).toInt()))
        gapsMs.forEachIndexed { index, gap ->
            start += lengthMs + gap
            result += PointV1(id = "p${index + 1}", startMs = start.toInt(), endMs = (start + lengthMs).toInt())
        }
        return result
    }

    @Test
    fun `the gap of a point is the time from the end of the point before it`() {
        val list = points(listOf(20_000, 31_400))
        assertEquals(listOf(null, 20_000L, 31_400L), ReviewGaps.gaps(list))
        assertEquals("+31 s", ReviewGaps.text(31_400))
    }

    @Test
    fun `a gap of more than twice the median of its neighbors and more than 25 s has a mark`() {
        // Normal gaps of 20 s. A missed point gives a gap of about 2 x 20 s + the rally.
        val gaps = List(12) { 20_000L }.toMutableList().apply { this[6] = 50_000 }
        assertEquals(setOf("p7"), ReviewGaps.longGaps(points(gaps), emptySet()))
    }

    @Test
    fun `a gap of twice the median or less has no mark`() {
        val gaps = List(12) { 20_000L }.toMutableList().apply { this[6] = 40_000 }
        assertEquals(emptySet(), ReviewGaps.longGaps(points(gaps), emptySet()))
    }

    @Test
    fun `a gap of 25 s or less has no mark, also when it is long for its neighbors`() {
        val gaps = List(12) { 8_000L }.toMutableList().apply { this[6] = 25_000 }
        assertEquals(emptySet(), ReviewGaps.longGaps(points(gaps), emptySet()))
    }

    @Test
    fun `a gap after a game end is a changeover with no mark, and it is not in the median`() {
        // p3 ends a game: the gap before p4 is a changeover of 90 s.
        val gaps = List(12) { 20_000L }.toMutableList().apply { this[3] = 90_000 }
        assertEquals(setOf("p4"), ReviewGaps.longGaps(points(gaps), emptySet()))
        assertEquals(emptySet(), ReviewGaps.longGaps(points(gaps), setOf("p3")))
    }

    @Test
    fun `a long gap at the start of the list has a mark`() {
        val gaps = List(12) { 20_000L }.toMutableList().apply { this[0] = 60_000 }
        assertEquals(setOf("p1"), ReviewGaps.longGaps(points(gaps), emptySet()))
    }

    @Test
    fun `one point has no gap, and two points have no neighbors for a median`() {
        assertEquals(emptySet(), ReviewGaps.longGaps(points(emptyList()), emptySet()))
        assertEquals(emptySet(), ReviewGaps.longGaps(points(listOf(90_000)), emptySet()))
    }
}
