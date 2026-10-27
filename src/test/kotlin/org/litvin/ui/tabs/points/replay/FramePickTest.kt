package org.litvin.ui.tabs.points.replay

import kotlin.test.Test
import kotlin.test.assertEquals

class FramePickTest {
    @Test
    fun thePartCountsTheFrameAtEachEnd() {
        // 2 s at 30 fps: frames 0 to 60.
        assertEquals(61, FramePick.count(10_000, 12_000, 30.0))
        assertEquals(1, FramePick.count(10_000, 10_000, 30.0))
        // 1 s at 29.97 fps: 29.97 frame times, so 30 frames.
        assertEquals(30, FramePick.count(0, 1_000, 30_000.0 / 1001))
    }

    @Test
    fun theFrameNumberStartsAtOneAndStaysInsideThePart() {
        assertEquals(1, FramePick.index(10_000, 10_000, 12_000, 30.0))
        assertEquals(2, FramePick.index(10_033, 10_000, 12_000, 30.0))
        assertEquals(31, FramePick.index(11_000, 10_000, 12_000, 30.0))
        assertEquals(61, FramePick.index(12_000, 10_000, 12_000, 30.0))
        assertEquals(1, FramePick.index(9_000, 10_000, 12_000, 30.0))
        assertEquals(61, FramePick.index(13_000, 10_000, 12_000, 30.0))
    }

    @Test
    fun aLongStepMovesFiveFramesAndStopsAtThePartEdges() {
        assertEquals(10_167, FramePick.step(10_000, FramePick.LONG_STEP_FRAMES, 10_000, 12_000, 30.0))
        assertEquals(11_833, FramePick.step(12_000, -FramePick.LONG_STEP_FRAMES, 10_000, 12_000, 30.0))
        assertEquals(12_000, FramePick.step(11_950, 5, 10_000, 12_000, 30.0))
        assertEquals(10_000, FramePick.step(10_050, -5, 10_000, 12_000, 30.0))
    }
}
