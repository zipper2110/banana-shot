package org.litvin.export

import org.junit.jupiter.api.extension.ExtendWith
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.ErrorKind
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetailV1
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringMode
import org.litvin.shared.util.PaidEdition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-69: the point filter of the Export tab keeps only the points whose details match. */
@ExtendWith(PaidEdition::class)
class PointFilterTest {
    private val edl = EdlV1(
        points = listOf(
            PointV1("ace1", 1_000, 2_000),
            PointV1("win2", 3_000, 4_000, favorite = true),
            PointV1("ue1", 5_000, 6_000, favorite = true),
            PointV1("fe2", 7_000, 8_000),
            PointV1("ue2", 9_000, 10_000),
            PointV1("q", 11_000, 12_000),
            // A double fault of player 1: a fault clip, then the second fault. The receiver (player 2) wins.
            PointV1("fault", 13_000, 14_000),
            PointV1("df", 15_000, 16_000),
            PointV1("w", 17_000, 18_000),
        ),
    )
    private val score = ScoreV1(
        scoringMode = ScoringMode.ADVANCED,
        outcomes = mapOf(
            "ace1" to Outcome.P1,
            "win2" to Outcome.P2,
            "ue1" to Outcome.P2,
            "fe2" to Outcome.P1,
            "ue2" to Outcome.P1,
            "q" to Outcome.P1,
            "fault" to Outcome.NONE,
            "df" to Outcome.P2,
            "w" to Outcome.NONE,
        ),
        details = mapOf(
            "ace1" to PointDetailV1(Ending.ACE),
            "win2" to PointDetailV1(Ending.WINNER),
            "ue1" to PointDetailV1(Ending.UNFORCED_ERROR, ErrorKind.NET),
            "fe2" to PointDetailV1(Ending.FORCED_ERROR, ErrorKind.LONG),
            "ue2" to PointDetailV1(Ending.UNFORCED_ERROR, ErrorKind.WIDE),
            "fault" to PointDetailV1(Ending.SERVE_FAULT),
            "df" to PointDetailV1(Ending.SERVE_FAULT),
        ),
    )

    private fun kept(
        filter: PointFilter,
        score: ScoreV1 = this.score,
        include: Boolean = false,
        favoriteOnly: Boolean = false,
    ): List<String> = ExportPlanner.selectedKeepPoints(
        ExportPlanner.validateEdl(edl), idleTrim = true, favoriteOnly = favoriteOnly,
        clips = ExportClips.of(edl, score, include, filter),
    ).map { it.id }

    @Test
    fun `a filter with no selection keeps all points`() {
        assertEquals(listOf("ace1", "win2", "ue1", "fe2", "ue2", "q", "df", "w"), kept(PointFilter.NONE))
        assertFalse(ExportClips.of(edl, score, includeServeClips = false).filtered)
    }

    @Test
    fun `the unforced errors of player 1`() {
        assertEquals(listOf("ue1"), kept(PointFilter(players = setOf(1), endings = setOf(FilterEnding.UNFORCED_ERROR))))
    }

    @Test
    fun `values in one group add points`() {
        val errors = PointFilter(endings = setOf(FilterEnding.FORCED_ERROR, FilterEnding.UNFORCED_ERROR))
        assertEquals(listOf("ue1", "fe2", "ue2"), kept(errors))
        assertEquals(listOf("ace1", "win2"), kept(PointFilter(endings = setOf(FilterEnding.ACE, FilterEnding.WINNER))))
    }

    @Test
    fun `the player is the player who made the ending`() {
        assertEquals(listOf("win2", "fe2", "ue2"), kept(PointFilter(players = setOf(2))))
        assertEquals(listOf("ace1", "ue1", "df"), kept(PointFilter(players = setOf(1))))
    }

    @Test
    fun `the error kind group keeps only errors with that kind`() {
        assertEquals(listOf("ue1", "ue2"), kept(PointFilter(errorKinds = setOf(ErrorKind.NET, ErrorKind.WIDE))))
        assertEquals(emptyList(), kept(PointFilter(endings = setOf(FilterEnding.WINNER), errorKinds = setOf(ErrorKind.NET))))
    }

    @Test
    fun `a double fault is an ending of the server, and its fault clip follows the option`() {
        val doubleFaults = PointFilter(players = setOf(1), endings = setOf(FilterEnding.DOUBLE_FAULT))
        assertEquals(listOf("df"), kept(doubleFaults))
        assertEquals(listOf("fault", "df"), kept(doubleFaults, include = true))
    }

    @Test
    fun `a point with no ending and a W clip do not match a selection`() {
        val all = PointFilter(endings = FilterEnding.entries.toSet())
        assertEquals(listOf("ace1", "win2", "ue1", "fe2", "ue2", "df"), kept(all))
    }

    @Test
    fun `the filter works with Only favorites`() {
        assertEquals(listOf("ue1"), kept(PointFilter(endings = setOf(FilterEnding.UNFORCED_ERROR)), favoriteOnly = true))
    }

    @Test
    fun `basic mode and the free version ignore the filter`() {
        val filter = PointFilter(endings = setOf(FilterEnding.ACE))
        assertEquals(9, kept(filter, score = score.copy(scoringMode = ScoringMode.BASIC)).size)
        val free = PaidEdition.with(paid = false) { ExportClips.of(edl, score, includeServeClips = false, filter = filter) }
        assertFalse(free.filtered)
    }

    @Test
    fun `the summary, the readiness, and the file name follow the filter`() {
        val clips = ExportClips.of(edl, score, includeServeClips = false, filter = PointFilter(endings = setOf(FilterEnding.UNFORCED_ERROR)))
        assertTrue(clips.filtered)
        val summary = ExportPlanner.summarize(edl, clips)
        assertEquals(2, summary.validPointCount)
        assertEquals(1, summary.favoriteCount)
        val kept = ExportPlanner.selectedKeepPoints(ExportPlanner.validateEdl(edl), idleTrim = true, favoriteOnly = false, clips = clips)
        assertEquals("filtered", ExportPlanner.contentLabel(kept, favoriteOnly = false, filtered = true))

        val none = ExportClips.of(edl, score, includeServeClips = false, filter = PointFilter(players = setOf(2), endings = setOf(FilterEnding.ACE)))
        val readiness = ExportPlanner.initializationReadiness(
            hasProject = true, sourceVideoExists = true, idleTrim = true, favoriteOnly = false,
            validPoints = ExportPlanner.validateEdl(edl), clips = none,
        )
        assertFalse(readiness.enabled)
    }
}
