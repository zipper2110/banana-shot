package org.litvin.autodetect

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The contract between the lab script and the app on real output (chain-inventory G14, journal E56). The fixture
 * comes from `detect.py` on `m1020_sample.mp4` (3 min): `result.json` and `overlay.json` of the run with code
 * c524c1b9 (`overlay.json` keeps only the first 4 items of each list), and the "@" lines of the first run with code
 * 9f0c87b4 (each stage starts) and of the run with c524c1b9 ("@overlay", "@result"). When `detect.py` changes the
 * format, make the fixture again (see E56) and update this test.
 */
class DetectFixtureTest {
    private fun fixture(name: String): File = File(javaClass.getResource("/autodetect/m1020_sample/$name")!!.toURI())

    /** The stage names of the script that are not a [DetectStage]: the app counts them in the stage before them. */
    private val subStages = setOf("court2")

    @Test
    fun `the result of the lab script imports`() {
        val result = DetectResult.read(fixture("result.json"))
        assertEquals(DetectResult.CONTRACT_VERSION, result.version)
        assertNull(DetectImport.check(result, null))
        assertTrue(result.videoId.orEmpty().matches(Regex("[0-9a-f]{40}")), "videoId: ${result.videoId}")
        assertEquals(181, result.videoSeconds)
        assertEquals(false, result.partial)
        assertEquals(6, result.rallies.size)
        var last = 0.0
        for (r in result.rallies) {
            assertTrue(r.start >= last && r.end > r.start && r.end <= result.videoSeconds, "rally ${r.start} to ${r.end}")
            assertTrue(r.winner in setOf("P1", "P2"), "winner ${r.winner}")
            last = r.end
        }
        for (stage in result.stageSeconds.keys) {
            assertTrue(DetectStage.of(stage) != null || stage in subStages, "unknown stage $stage")
        }
    }

    @Test
    fun `each line of the lab script names a known stage`() {
        val lines = fixture("run_lines.txt").readLines().filter { it.isNotBlank() }
        val parsed = lines.map { assertNotNull(ScriptLine.parse(it), it) }
        val started = mutableListOf<DetectStage>()
        for (line in parsed.filter { it.kind == "@stage" || it.kind == "@progress" }) {
            val name = line.args[0]
            val stage = DetectStage.of(name)
            assertTrue(stage != null || name in subStages, "unknown stage $name")
            if (line.kind == "@stage" && line.args[1] == "start" && stage != null) started += stage
            if (line.kind == "@progress") assertNotNull(line.args[1].toDoubleOrNull(), line.text)
        }
        assertEquals(DetectProgress.stages(ballPass = true), started)       // all stages, in run order
        assertTrue(parsed.single { it.kind == "@result" }.text.endsWith("result.json"))
        assertTrue(parsed.single { it.kind == "@overlay" }.text.endsWith("overlay.json"))
    }

    @Test
    fun `the overlay of the lab script reads`() {
        val overlay = DetectOverlayData.read(fixture("overlay.json"))
        assertTrue(overlay.court.isNotEmpty() && overlay.court.all { it.lines.isNotEmpty() && it.t1 > it.t0 })
        assertTrue(overlay.flights.isNotEmpty() && overlay.flights.all { it.t1 >= it.t0 })
        assertTrue(overlay.events.isNotEmpty() && overlay.events.all { it.kind.isNotEmpty() })
        assertTrue(overlay.ball.all { it.size == 3 })
        assertTrue(overlay.poses.all { it.size == 1 + 2 * 17 })
    }
}
