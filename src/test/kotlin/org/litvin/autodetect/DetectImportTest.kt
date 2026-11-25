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
    fun `swapped players give each point the other winner`() {
        val detected = result(rally(10.0, 20.0, "P1"), rally(30.0, 40.0, "P2"), rally(50.0, 60.0, winner = null, confidence = null))
        val plan = DetectImport.plan(detected, emptyList(), advanced = false, DetectSettings(), ids, swapped = true)

        assertEquals(listOf(Outcome.P2, Outcome.P1, Outcome.NONE), plan.points.map { plan.outcomes[it.id] })
    }

    @Test
    fun `a rally that is not fully inside the trim is skipped`() {
        val detected = result(rally(10.0, 20.0), rally(30.0, 40.0), rally(55.0, 65.0))
        val plan = DetectImport.plan(detected, emptyList(), advanced = false, DetectSettings(), ids, rangeMs = 25_000L..60_000L)

        assertEquals(listOf(30_000), plan.points.map { it.startMs })
        assertEquals(listOf(SkipReason.OUTSIDE_TRIM, null, SkipReason.OUTSIDE_TRIM), plan.rows.map { it.skipped })
    }

    @Test
    fun `the player question uses the first rally inside the trim that knows the ends`() {
        val detected = result(
            rally(10.0, 20.0).copy(p1Near = true),
            rally(30.0, 31.0, kind = "short").copy(p1Near = false),
            rally(40.0, 50.0).copy(p1Near = false),
        )

        assertEquals(10.0, DetectImport.questionRally(detected, null)?.start)
        assertEquals(40.0, DetectImport.questionRally(detected, 25_000L..60_000L)?.start)
        assertNull(DetectImport.questionRally(result(rally(10.0, 20.0)), null))
    }

    @Test
    fun `the player question prefers a rally with a picture of the two players`() {
        val view = RallyView(45, near = listOf(800.0, 500.0, 1000.0, 1000.0), far = listOf(900.0, 300.0, 950.0, 400.0))
        val detected = result(rally(10.0, 20.0).copy(p1Near = true), rally(40.0, 50.0).copy(p1Near = false, view = view))

        assertEquals(40.0, DetectImport.questionRally(detected, null)?.start)
    }

    @Test
    fun `the players are swapped when player 1 of the detection is not player 1 of the project`() {
        val near = rally(10.0, 20.0).copy(p1Near = true)
        val far = rally(10.0, 20.0).copy(p1Near = false)

        assertEquals(false, DetectImport.isSwapped(near, nearPlayer = 1))
        assertEquals(true, DetectImport.isSwapped(near, nearPlayer = 2))
        assertEquals(true, DetectImport.isSwapped(far, nearPlayer = 1))
        assertEquals(false, DetectImport.isSwapped(far, nearPlayer = 2))
    }

    @Test
    fun `a clip gets the player of the project at the near end`() {
        val detected = result(rally(10.0, 20.0).copy(p1Near = true), rally(30.0, 40.0).copy(p1Near = false), rally(50.0, 60.0))

        val plain = DetectImport.plan(detected, emptyList(), advanced = false, DetectSettings(), ids)
        assertEquals(listOf(1, 2, null), plain.points.map { it.auto?.nearPlayer })
        val swapped = DetectImport.plan(detected, emptyList(), advanced = false, DetectSettings(), ids, swapped = true)
        assertEquals(listOf(2, 1, null), swapped.points.map { it.auto?.nearPlayer })
    }

    @Test
    fun `the import records give the player at the near end for the clips of an older import`() {
        val dir = Files.createTempDirectory("detect-near").toFile()
        try {
            val folder = File(dir, DetectImport.FOLDER).apply { mkdirs() }
            DetectJson.mapper.writeValue(File(folder, "result-1.json"), result(rally(10.0, 20.0).copy(p1Near = true), rally(30.0, 40.0).copy(p1Near = false)))
            val rows = listOf(ImportRow(0, pointId = "a"), ImportRow(1, pointId = "b"))
            DetectJson.mapper.writeValue(File(folder, "import-1.json"), ImportRecord("1", "result-1.json", DetectSettings(), rows, swapped = true))

            assertEquals(mapOf("a" to 2, "b" to 1), DetectImport.nearPlayersOfRecords(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the warnings come from the result of the newest import`() {
        val dir = Files.createTempDirectory("detect-warn").toFile()
        try {
            assertEquals(emptyList(), DetectImport.latestImportWarnings(dir))
            val folder = File(dir, DetectImport.FOLDER).apply { mkdirs() }
            DetectJson.mapper.writeValue(File(folder, "result-1.json"), result(rally(10.0, 20.0)).copy(warnings = listOf("old")))
            DetectJson.mapper.writeValue(File(folder, "result-2.json"), result(rally(10.0, 20.0)).copy(warnings = listOf("No court")))
            DetectJson.mapper.writeValue(File(folder, "import-1.json"), ImportRecord("1", "result-1.json", DetectSettings(), emptyList()))
            DetectJson.mapper.writeValue(File(folder, "import-2.json"), ImportRecord("2", "result-2.json", DetectSettings(), emptyList()))

            assertEquals(listOf("No court"), DetectImport.latestImportWarnings(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a fault clip has the near player of its point and the shots of the fault candidate`() {
        val dir = Files.createTempDirectory("detect-fault").toFile()
        try {
            val folder = File(dir, DetectImport.FOLDER).apply { mkdirs() }
            val serve = ChainShot(t = 19.8, near = true, how = "first", end = "net", kmh = 120.0)
            val point = rally(30.0, 40.0, fault = 20.0).let {
                it.copy(p1Near = true, server = "P1", serverNear = true, fault = it.fault?.copy(speed = 120.0, chain = ShotChain(shots = listOf(serve))))
            }
            DetectJson.mapper.writeValue(File(folder, "result-1.json"), result(point))
            val rows = listOf(ImportRow(0, pointId = "a", faultPointId = "f"))
            DetectJson.mapper.writeValue(File(folder, "import-1.json"), ImportRecord("1", "result-1.json", DetectSettings(), rows))

            assertEquals(mapOf("a" to 1, "f" to 1), DetectImport.nearPlayersOfRecords(dir))
            val fault = DetectImport.detectionsOfRecords(dir).getValue("f").rally
            assertEquals(DetectImport.FAULT_KIND, fault.kind)
            assertEquals(19.0 to 22.5, fault.start to fault.end)
            assertEquals(true, fault.serverNear)
            assertEquals(120.0, fault.serveSpeedKmh)
            assertEquals(listOf(serve), fault.chain?.shots)
            assertEquals(null, fault.winner)
            // A new import writes the near player into the mark of the fault clip.
            val plan = DetectImport.plan(result(point), emptyList(), advanced = true, DetectSettings(), ids)
            assertEquals(1, plan.points.first { it.auto?.kind == AutoKind.FAULT }.auto?.nearPlayer)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `apply writes the clips, the winners, and the record, and a second apply adds nothing`() {
        val dir = Files.createTempDirectory("detect-import").toFile()
        try {
            EdlIO.writeForProjectDir(dir.absolutePath, EdlV1(points = listOf(PointV1(id = "mine", startMs = 100_000, endMs = 110_000))))
            val file = File(File(dir, DetectImport.FOLDER).apply { mkdirs() }, "result-1.json")    // as the app keeps it
            DetectJson.mapper.writeValue(file, result(rally(10.0, 20.0, "P1"), rally(30.0, 40.0, "P2"), rally(101.0, 108.0)))

            val plan = DetectImport.apply(dir, file, DetectSettings())

            assertEquals(2, plan.rallyCount)
            assertEquals(1, plan.skippedCount)
            val points = EdlIO.readForProjectDir(dir.absolutePath).points
            assertEquals(listOf(10_000, 30_000, 100_000), points.map { it.startMs })
            val score = ScoreIO.readForProjectDir(dir.absolutePath)
            assertEquals(listOf(Outcome.P1, Outcome.P2), points.take(2).map { score.outcomeOf(it.id) })
            val records = File(dir, DetectImport.FOLDER).listFiles { f -> f.name.startsWith("import-") }!!
            assertEquals(1, records.size)
            // G11: the record names the clips that were there before the import, and each clip names its result.
            assertEquals(listOf("mine"), DetectJson.mapper.readValue(records[0], ImportRecord::class.java).existingPointIds)
            val detection = DetectImport.detectionsOfRecords(dir).getValue(points[0].id)
            assertEquals("result-1.json" to records[0].name, detection.resultFile to detection.importFile)

            assertEquals(0, DetectImport.apply(dir, file, DetectSettings()).points.size)
            assertEquals(3, EdlIO.readForProjectDir(dir.absolutePath).points.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
