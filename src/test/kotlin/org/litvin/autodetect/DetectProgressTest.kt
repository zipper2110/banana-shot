package org.litvin.autodetect

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectProgressTest {
    private val dir = File("project")

    private fun running(stage: DetectStage?, progress: Double?, stageSeconds: Long, videoSeconds: Double? = 6_000.0, ballPass: Boolean = true) =
        DetectState.Running(dir, stage, progress, startedAtMs = 0, stageStartedAtMs = 1_000_000 - stageSeconds * 1000, videoSeconds, ballPass)

    private fun of(state: DetectState.Running) = DetectProgress.of(state, nowMs = 1_000_000)

    @Test
    fun `before the first stage, the estimate is the reference time of all stages`() {
        val progress = of(running(null, null, 0))

        assertEquals(1 to 9, progress.step to progress.steps)
        assertEquals(0.0, progress.totalPart)
        // All weights are 0.653 of the video length.
        assertEquals(3_918L, progress.leftSeconds)
    }

    @Test
    fun `a stage with progress reports gives the time left from its measured speed`() {
        // Half of the ball stage in 1,000 s: 1,000 s more for it. The reference is 0.345 x 6,000 s = 2,070 s for the
        // stage, so this PC is about 2,000 / 2,070 as fast, and the later stages (0.141) get 0.141 x 5,797 s.
        val progress = of(running(DetectStage.BALL, 0.5, 1_000))

        assertEquals(5, progress.step)
        assertEquals(0.5, progress.stepPart)
        assertEquals(1_817.0, progress.leftSeconds!!.toDouble(), 2.0)
        assertEquals((0.167 + 0.345 * 0.5) / 0.653, progress.totalPart, 0.001)
    }

    @Test
    fun `a stage with no progress reports gets its part from the time`() {
        // The pose stage: the reference is 0.127 x 6,000 s = 762 s.
        val progress = of(running(DetectStage.POSE, null, 381))

        assertEquals(0.5, progress.stepPart!!, 0.001)
        assertEquals(381.0 + 0.002 * 6_000, progress.leftSeconds!!.toDouble(), 2.0)

        val late = of(running(DetectStage.POSE, null, 5_000))
        assertEquals(0.95, late.stepPart!!, 0.001)
        assertTrue(late.totalPart < 1.0)
    }

    @Test
    fun `a run with rallies only has four steps`() {
        val progress = of(running(DetectStage.RALLIES, null, 0, ballPass = false))

        assertEquals(4 to 4, progress.step to progress.steps)
        assertTrue(progress.totalPart > 0.98)
    }

    @Test
    fun `with no video length, the estimate starts with the first progress report`() {
        assertNull(of(running(DetectStage.MEDIA, null, 30, videoSeconds = null)).leftSeconds)
        assertNull(of(running(DetectStage.MEDIA, null, 30, videoSeconds = null)).stepPart)
        assertNotNull(of(running(DetectStage.BALL, 0.2, 400, videoSeconds = null)).leftSeconds)
    }
}
