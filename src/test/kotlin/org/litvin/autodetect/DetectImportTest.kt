package org.litvin.autodetect

import org.litvin.points.AutoKind
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectImportTest {
    private var next = 0
    private val ids: () -> String = { "id${next++}" }

    private fun rally(start: Double, end: Double, winner: String? = "P1", confidence: Double? = 0.9, fault: Double? = null, kind: String = "rally") =
        DetectedRally(start = start, end = end, kind = kind, winner = winner, confidence = confidence, fault = fault?.let { DetectedFault(it) })

    private fun result(vararg rallies: DetectedRally) = DetectResult(videoSeconds = 600, rallies = rallies.toList())

    @Test
    fun `a rally becomes a clip with the detected winner and the automatic mark`() {
        val plan = DetectImport.plan(result(rally(10.0, 20.0, "P2", 0.83)), emptyList(), advanced = false, DetectSettings(), ids)

        val point = plan.points.single()
        assertEquals(10_000 to 20_000, point.startMs to point.endMs)
        assertEquals(Outcome.P2, plan.outcomes[point.id])
        assertEquals(AutoKind.RALLY, point.auto?.kind)
        assertEquals(83, point.auto?.winnerPercent)
        assertEquals(listOf(ImportRow(0, pointId = point.id)), plan.rows)
    }

    @Test
    fun `the padding stops at the middle of the gap to the next rally`() {
        val settings = DetectSettings(padBeforeMs = 3_000, padAfterMs = 3_000)
        val plan = DetectImport.plan(result(rally(10.0, 20.0), rally(22.0, 30.0)), emptyList(), advanced = false, settings, ids)

        assertEquals(listOf(7_000 to 21_000, 21_000 to 33_000), plan.points.map { it.startMs to it.endMs })
    }

    @Test
    fun `a negative padding makes the clip shorter`() {
        val settings = DetectSettings(padBeforeMs = -500, padAfterMs = -1_000)
        val plan = DetectImport.plan(result(rally(10.0, 20.0)), emptyList(), advanced = false, settings, ids)

        assertEquals(10_500 to 19_000, plan.points.single().let { it.startMs to it.endMs })
    }

    @Test
    fun `a rally at the time of a clip of the project is skipped`() {
        val existing = listOf(PointV1(id = "mine", startMs = 12_000, endMs = 18_000))
        val plan = DetectImport.plan(result(rally(10.0, 20.0), rally(30.0, 40.0)), existing, advanced = false, DetectSettings(), ids)

        assertEquals(1, plan.points.size)
        assertEquals(SkipReason.OVERLAP, plan.rows[0].skipped)
        assertNotNull(plan.rows[1].pointId)
    }

    @Test
    fun `a winner under the minimum confidence is not set`() {
        val settings = DetectSettings(minWinnerPercent = 70)
        val plan = DetectImport.plan(result(rally(10.0, 20.0, "P1", 0.62)), emptyList(), advanced = false, settings, ids)

        val point = plan.points.single()
        assertEquals(Outcome.NONE, plan.outcomes[point.id])
        assertNull(point.auto?.winnerPercent)
    }

    @Test
    fun `winners off gives clips with no winner`() {
        val plan = DetectImport.plan(result(rally(10.0, 20.0)), emptyList(), advanced = false, DetectSettings(addWinners = false), ids)

        assertEquals(Outcome.NONE, plan.outcomes.values.single())
    }

    @Test
    fun `a fault candidate becomes a fault clip only in advanced mode`() {
        val detected = result(rally(30.0, 40.0, fault = 20.0))
        val basic = DetectImport.plan(detected, emptyList(), advanced = false, DetectSettings(), ids)
        val advanced = DetectImport.plan(detected, emptyList(), advanced = true, DetectSettings(), ids)

        assertEquals(1, basic.points.size)
        assertEquals(2, advanced.points.size)
        val fault = advanced.points.first { it.auto?.kind == AutoKind.FAULT }
        assertEquals(19_000 to 22_500, fault.startMs to fault.endMs)
        assertEquals(Ending.SERVE_FAULT, advanced.details[fault.id]?.ending)
        assertEquals(fault.id, advanced.rows.single().faultPointId)
    }

    @Test
    fun `a fault clip ends at the start of its point`() {
        val plan = DetectImport.plan(result(rally(22.0, 30.0, fault = 20.0)), emptyList(), advanced = true, DetectSettings(), ids)

        val fault = plan.points.first { it.auto?.kind == AutoKind.FAULT }
        assertEquals(22_000, fault.endMs)
    }

    @Test
    fun `the clips never overlap`() {
        val settings = DetectSettings(padBeforeMs = 5_000, padAfterMs = 5_000)
        val detected = result(rally(10.0, 20.0, fault = 8.0), rally(21.0, 30.0, fault = 20.5), rally(30.0, 31.0, kind = "short"))
        val plan = DetectImport.plan(detected, emptyList(), advanced = true, settings, ids)

        val sorted = plan.points.sortedBy { it.startMs }
        assertTrue(sorted.zipWithNext().all { (a, b) -> a.endMs <= b.startMs }, sorted.joinToString { "${it.startMs}-${it.endMs}" })
        assertTrue(sorted.all { it.endMs - it.startMs >= 200 })
    }

    @Test
    fun `apply writes the clips, the winners, and the record, and a second apply adds nothing`() {
        val dir = Files.createTempDirectory("detect-import").toFile()
        try {
            EdlIO.writeForProjectDir(dir.absolutePath, EdlV1(points = listOf(PointV1(id = "mine", startMs = 100_000, endMs = 110_000))))
            val file = File(dir, "result.json")
            DetectJson.mapper.writeValue(file, result(rally(10.0, 20.0, "P1"), rally(30.0, 40.0, "P2"), rally(101.0, 108.0)))

            val plan = DetectImport.apply(dir, file, DetectSettings())

            assertEquals(2, plan.rallyCount)
            assertEquals(1, plan.skippedCount)
            val points = EdlIO.readForProjectDir(dir.absolutePath).points
            assertEquals(listOf(10_000, 30_000, 100_000), points.map { it.startMs })
            val score = ScoreIO.readForProjectDir(dir.absolutePath)
            assertEquals(listOf(Outcome.P1, Outcome.P2), points.take(2).map { score.outcomeOf(it.id) })
            assertEquals(1, File(dir, DetectImport.FOLDER).listFiles { f -> f.name.startsWith("import-") }!!.size)

            assertEquals(0, DetectImport.apply(dir, file, DetectSettings()).points.size)
            assertEquals(3, EdlIO.readForProjectDir(dir.absolutePath).points.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
