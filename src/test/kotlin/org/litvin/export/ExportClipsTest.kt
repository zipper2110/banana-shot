package org.litvin.export

import org.litvin.ExportPreset
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetailV1
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-68 E3: which clips "Only points" and "Only favorites" keep, and the score of a fault clip of zero length. */
class ExportClipsTest {
    // Pack 1: a fault, then a favorite point on the 2nd serve. Pack 2: a let, then a point. Then a W clip.
    private val edl = EdlV1(
        points = listOf(
            PointV1("f1", 1_000, 2_000),
            PointV1("p1", 3_000, 6_000, favorite = true),
            PointV1("l2", 8_000, 9_000),
            PointV1("p2", 10_000, 14_000),
            PointV1("w", 16_000, 17_000, favorite = true),
        ),
    )
    private val score = ScoreV1(
        scoringMode = ScoringMode.ADVANCED,
        outcomes = mapOf("p1" to Outcome.P1, "p2" to Outcome.P2),
        details = mapOf("f1" to PointDetailV1(Ending.SERVE_FAULT), "l2" to PointDetailV1(Ending.LET)),
        serverMarks = mapOf("f1" to Outcome.P1),
    )

    private fun kept(score: ScoreV1, include: Boolean, favoriteOnly: Boolean): List<String> =
        ExportPlanner.selectedKeepPoints(
            ExportPlanner.validateEdl(edl), idleTrim = true, favoriteOnly = favoriteOnly,
            clips = ExportClips.of(edl, score, include),
        ).map { it.id }

    @Test
    fun `without the option, advanced mode skips the fault clips and the let clips`() {
        assertEquals(listOf("p1", "p2", "w"), kept(score, include = false, favoriteOnly = false))
        assertEquals(listOf("p1", "w"), kept(score, include = false, favoriteOnly = true))
    }

    @Test
    fun `with the option, the clips of a favorite pack go in with its point clip`() {
        assertEquals(listOf("f1", "p1", "l2", "p2", "w"), kept(score, include = true, favoriteOnly = false))
        assertEquals(listOf("f1", "p1", "w"), kept(score, include = true, favoriteOnly = true))
    }

    @Test
    fun `basic mode keeps all clips and has no option`() {
        val basic = score.copy(scoringMode = ScoringMode.BASIC)
        assertEquals(listOf("f1", "p1", "l2", "p2", "w"), kept(basic, include = false, favoriteOnly = false))
        assertEquals(listOf("p1", "w"), kept(basic, include = false, favoriteOnly = true))
    }

    @Test
    fun `a double fault is the serve that counts, so it stays without the option`() {
        val points = listOf(PointV1("f1", 1_000, 2_000), PointV1("df", 3_000, 4_000))
        val faults = score.copy(
            outcomes = emptyMap(),
            details = mapOf("f1" to PointDetailV1(Ending.SERVE_FAULT), "df" to PointDetailV1(Ending.SERVE_FAULT)),
        )
        val kept = ExportClips.of(EdlV1(points = points), faults, includeServeClips = false).keep(points, favoriteOnly = false)
        assertEquals(listOf("df"), kept.map { it.id })
    }

    @Test
    fun `the summary counts only the clips that the export keeps`() {
        val summary = ExportPlanner.summarize(edl, ExportClips.of(edl, score, includeServeClips = false))
        assertEquals(5, summary.pointCount)
        assertEquals(3, summary.validPointCount)
        assertEquals(2, summary.favoriteCount)
    }

    @Test
    fun `the export skips a fault clip of zero length, but the scoreboard keeps its score`() {
        // A fault with video, then a double fault of zero length: the receiver (player 2) wins the first point.
        val points = listOf(
            PointV1("f1", 1_000, 2_000),
            PointV1("df", 3_000, 3_000),
            PointV1("p2", 5_000, 8_000),
        )
        val zeroScore = ScoreV1(
            scoringMode = ScoringMode.ADVANCED,
            outcomes = mapOf("df" to Outcome.P2, "p2" to Outcome.P1),
            details = mapOf("f1" to PointDetailV1(Ending.SERVE_FAULT), "df" to PointDetailV1(Ending.SERVE_FAULT)),
            serverMarks = mapOf("f1" to Outcome.P1),
        )
        val plan = ExportPlanner.buildRenderPlan(
            ExportRenderPlanRequest(
                manifest = null,
                sourcePath = "input.mp4",
                edl = EdlV1(points = points),
                score = zeroScore,
                preset = ExportPreset(id = "balanced", label = "Balanced"),
                resolution = ExportResolution("1080p", 1920, 1080),
                encoderLabel = "H.264 (libx264)",
                idleTrim = true,
                favoriteOnly = false,
                includeScoreboard = true,
                outputPath = "out.mp4",
                includeServeClips = true,
            ),
        )
        assertEquals(listOf("f1", "p2"), plan.keptPoints.map { it.id })
        assertEquals(listOf("f1", "df", "p2"), ExportPlanner.scoringPoints(EdlV1(points = points)).map { it.id })
        // The span of the last point shows the score after the double fault: 0-15 for player 2.
        val last = plan.overlayTimeline.last()
        assertEquals(0, last.p1Pts)
        assertEquals(1, last.p2Pts)
    }
}
