package org.litvin.adjustments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GeometryTimelineTest {
    private val twoKeys = AdjustmentsV1(
        rotationDeg = 1.0f,
        geometryKeys = listOf(
            GeometryKeyV1(atMs = 10_000, rotationDeg = 3.0f),
            GeometryKeyV1(atMs = 20_000, zoom = 1.5f, rotationDeg = -2.0f),
        ),
    )

    @Test
    fun `the active change point is the last one at or before the time`() {
        assertEquals(-1, GeometryTimeline.activeIndex(twoKeys, 9_999))
        assertEquals(0, GeometryTimeline.activeIndex(twoKeys, 10_000))
        assertEquals(0, GeometryTimeline.activeIndex(twoKeys, 19_999))
        assertEquals(1, GeometryTimeline.activeIndex(twoKeys, 20_000))
    }

    @Test
    fun `the editor values are those of the active change point without change points`() {
        val editing = GeometryTimeline.editingAt(twoKeys, 25_000)

        assertEquals(1.5f, editing.zoom)
        assertEquals(-2.0f, editing.rotationDeg)
        assertTrue(editing.geometryKeys.isEmpty())
        assertEquals(1.0f, GeometryTimeline.editingAt(twoKeys, 0).rotationDeg)
    }

    @Test
    fun `an edit changes only the active change point`() {
        val edited = GeometryTimeline.withEdited(twoKeys, 12_000, AdjustmentsV1(rotationDeg = 4.0f, panX = 0.5f))

        assertEquals(4.0f, edited.geometryKeys[0].rotationDeg)
        assertEquals(0.5f, edited.geometryKeys[0].panX)
        assertEquals(twoKeys.geometryKeys[1], edited.geometryKeys[1])
        assertEquals(1.0f, edited.rotationDeg)
    }

    @Test
    fun `an edit before the first change point changes the usual values`() {
        val edited = GeometryTimeline.withEdited(twoKeys, 5_000, AdjustmentsV1(rotationDeg = 0.5f))

        assertEquals(0.5f, edited.rotationDeg)
        assertEquals(twoKeys.geometryKeys, edited.geometryKeys)
    }

    @Test
    fun `a new change point copies the active values and keeps the time order`() {
        val added = GeometryTimeline.withKeyAdded(twoKeys, 15_000)

        assertEquals(listOf(10_000L, 15_000L, 20_000L), added.geometryKeys.map { it.atMs })
        assertEquals(3.0f, added.geometryKeys[1].rotationDeg)
        assertSame(twoKeys, GeometryTimeline.withKeyAdded(twoKeys, 10_000))
    }

    @Test
    fun `remove deletes the active change point and nothing before the first one`() {
        val removed = GeometryTimeline.withKeyRemoved(twoKeys, 21_000)

        assertEquals(listOf(10_000L), removed.geometryKeys.map { it.atMs })
        assertSame(twoKeys, GeometryTimeline.withKeyRemoved(twoKeys, 5_000))
    }

    @Test
    fun `the blend takes one second and ends at the change point`() {
        val blends = GeometryTimeline.blends(twoKeys, 1920, 1080)

        assertEquals(listOf(9_000L to 10_000L, 19_000L to 20_000L), blends.map { it.startMs to it.endMs })
        assertEquals(1.0, GeometryTimeline.planAt(twoKeys, 9_000, 1920, 1080).rotationDeg, 1e-9)
        assertEquals(2.0, GeometryTimeline.planAt(twoKeys, 9_500, 1920, 1080).rotationDeg, 1e-9)
        assertEquals(3.0, GeometryTimeline.planAt(twoKeys, 10_000, 1920, 1080).rotationDeg, 1e-9)
    }

    @Test
    fun `a change point near the previous one blends from the previous one`() {
        val near = AdjustmentsV1(
            geometryKeys = listOf(GeometryKeyV1(atMs = 5_000, rotationDeg = 1.0f), GeometryKeyV1(atMs = 5_400, rotationDeg = 2.0f)),
        )

        assertEquals(5_000L, GeometryTimeline.blends(near, 1920, 1080)[1].startMs)
    }

    @Test
    fun `a change point at the start has no blend`() {
        val atStart = AdjustmentsV1(geometryKeys = listOf(GeometryKeyV1(atMs = 0, rotationDeg = 2.0f)))

        assertTrue(GeometryTimeline.blends(atStart, 1920, 1080).isEmpty())
        assertEquals(2.0, GeometryTimeline.planAt(atStart, 0, 1920, 1080).rotationDeg, 1e-9)
    }

    @Test
    fun `the angle takes the shorter direction across 180 degrees`() {
        val from = GeometryPlan(170.0, GeometryPlan.FULL_FRAME)
        val to = GeometryPlan(-170.0, GeometryPlan.FULL_FRAME)

        assertEquals(180.0, GeometryTimeline.lerp(from, to, 0.5).rotationDeg, 1e-9)
    }

    @Test
    fun `the geometry exists when only a change point has it`() {
        val keyOnly = AdjustmentsV1(geometryKeys = listOf(GeometryKeyV1(atMs = 1_000, rotationDeg = 2.0f)))

        assertTrue(GeometryTimeline.hasGeometry(keyOnly, 1920, 1080))
        assertFalse(GeometryTimeline.hasGeometry(AdjustmentsV1(), 1920, 1080))
    }
}
