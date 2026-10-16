package org.litvin.adjustments

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangePointsTest {
    private val usual = AdjustmentsV1(brightness = 1.2f, rotationDeg = 1.0f)

    @Test
    fun `add puts a change point with the active values into the two lists`() {
        val added = ChangePoints.withAdded(usual, 10_000)

        assertEquals(listOf(10_000L), added.colorKeys.map { it.atMs })
        assertEquals(listOf(10_000L), added.geometryKeys.map { it.atMs })
        assertEquals(1.2f, added.colorKeys.single().brightness)
        assertEquals(1.0f, added.geometryKeys.single().rotationDeg)
        assertEquals(listOf(10_000L), ChangePoints.times(added))
    }

    @Test
    fun `the editing values have the colors and the transform of the active change point without blends`() {
        val keyed = ChangePoints.withAdded(usual, 10_000).let {
            it.copy(
                colorKeys = listOf(it.colorKeys.single().copy(brightness = 1.5f)),
                geometryKeys = listOf(it.geometryKeys.single().copy(rotationDeg = 3.0f)),
            )
        }

        val before = ChangePoints.editingAt(keyed, 9_500)
        assertEquals(1.2f, before.brightness)
        assertEquals(1.0f, before.rotationDeg)
        val after = ChangePoints.editingAt(keyed, 10_000)
        assertEquals(1.5f, after.brightness)
        assertEquals(3.0f, after.rotationDeg)
        assertEquals(emptyList(), after.colorKeys)
        assertEquals(emptyList(), after.geometryKeys)
    }

    @Test
    fun `remove deletes the active change point from the two lists`() {
        val two = ChangePoints.withAdded(ChangePoints.withAdded(usual, 10_000), 20_000)

        val removed = ChangePoints.withRemoved(two, 15_000)

        assertEquals(listOf(20_000L), removed.colorKeys.map { it.atMs })
        assertEquals(listOf(20_000L), removed.geometryKeys.map { it.atMs })
        assertEquals(two, ChangePoints.withRemoved(two, 5_000))
    }

    @Test
    fun `a time in one list only is a change point of the tab`() {
        val older = usual.copy(
            colorKeys = listOf(ColorKeyV1(atMs = 5_000)),
            geometryKeys = listOf(GeometryKeyV1(atMs = 8_000)),
        )

        assertEquals(listOf(5_000L, 8_000L), ChangePoints.times(older))
        val removed = ChangePoints.withRemoved(older, 9_000)
        assertEquals(listOf(5_000L), removed.colorKeys.map { it.atMs })
        assertEquals(emptyList(), removed.geometryKeys)
    }
}
