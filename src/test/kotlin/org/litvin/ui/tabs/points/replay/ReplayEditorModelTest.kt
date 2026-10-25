package org.litvin.ui.tabs.points.replay

import org.litvin.points.PointV1
import org.litvin.points.ReplayV1
import org.litvin.points.ReplayZoom
import org.litvin.points.Replays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayEditorModelTest {
    private val point = PointV1("p1", 10_000, 14_000)

    @Test
    fun aNewReplayIsAroundThePlayheadAndSelected() {
        val model = ReplayEditorModel(point)
        assertEquals(-1, model.selectedIndex)
        assertFalse(model.changed)

        assertTrue(model.add(12_000))

        assertEquals(0, model.selectedIndex)
        assertEquals(ReplayV1(10_500, 13_500), model.selected)
        assertTrue(model.changed)
    }

    @Test
    fun theEdgesStayInsideTheWindowAndKeepTheMinimumLength() {
        val model = ReplayEditorModel(point)
        model.add(12_000)

        model.setStart(1_000)
        assertEquals(Replays.windowStartMs(point), model.selected?.startMs)

        model.setStart(13_400)
        assertEquals(13_500 - Replays.MIN_PART_MS, model.selected?.startMs)

        model.setEnd(99_000)
        assertEquals(Replays.windowEndMs(point), model.selected?.endMs)

        model.setWhole()
        assertEquals(10_000, model.selected?.startMs)
        assertEquals(14_000, model.selected?.endMs)
    }

    @Test
    fun theValuesStayInsideTheLimits() {
        val model = ReplayEditorModel(point)
        model.add(12_000)

        model.setSpeed(5.0)
        model.setRepeats(99)

        assertEquals(Replays.MAX_SPEED, model.selected?.speed)
        assertEquals(Replays.MAX_REPEATS, model.selected?.repeats)
    }

    @Test
    fun theZoomFactorKeepsTheCenterOfTheFrame() {
        val model = ReplayEditorModel(point)
        model.add(12_000)

        model.setZoomFactor(2.0)
        assertEquals(ReplayZoom(0.25, 0.25, 0.5), model.selected?.zoom)
        assertEquals(2.0, model.zoomFactor)

        model.setZoom(ReplayZoom(0.1, 0.2, 0.5))
        model.setZoomFactor(4.0)
        val zoom = model.selected?.zoom!!
        assertEquals(0.225, zoom.x, 1e-9)
        assertEquals(0.325, zoom.y, 1e-9)
        assertEquals(0.25, zoom.width, 1e-9)

        model.setZoomFactor(1.0)
        assertNull(model.selected?.zoom)
        assertEquals(1.0, model.zoomFactor)
    }

    @Test
    fun deleteSelectsTheReplayBefore() {
        val saved = point.copy(replays = listOf(ReplayV1(10_000, 11_000), ReplayV1(12_000, 13_000)))
        val model = ReplayEditorModel(saved, selectedIndex = 1)
        assertFalse(model.changed)

        model.deleteSelected()
        assertEquals(0, model.selectedIndex)
        assertEquals(listOf(ReplayV1(10_000, 11_000)), model.replays)

        model.deleteSelected()
        assertEquals(-1, model.selectedIndex)
        assertTrue(model.replays.isEmpty())
        assertTrue(model.changed)
    }

    @Test
    fun theAddedTimeCountsEachRepeat() {
        val saved = point.copy(replays = listOf(ReplayV1(10_000, 11_000, speed = 0.5, repeats = 3)))
        assertEquals(6_000, ReplayEditorModel(saved).addedMs)
    }
}
