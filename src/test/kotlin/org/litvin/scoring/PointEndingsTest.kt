package org.litvin.scoring

import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-68 E2: the ending keys, Q, W, and E, and the confirmation of a server change. */
class PointEndingsTest {
    @Test
    fun `each ending button gives the point to its group and names the player who made the ending`() {
        assertEquals(Outcome.P1, EndingChoice.P2_UNFORCED_ERROR.outcome)
        assertEquals(2, EndingChoice.P2_UNFORCED_ERROR.player)
        assertEquals(Outcome.P2, EndingChoice.P1_FORCED_ERROR.outcome)
        assertEquals(1, EndingChoice.P1_FORCED_ERROR.player)
        assertEquals(1, EndingChoice.P1_WINNER.player)
        assertEquals(2, EndingChoice.P2_ACE.player)
        assertEquals(EndingChoice.P2_FORCED_ERROR, EndingChoice.of(Outcome.P1, Ending.FORCED_ERROR))
        assertNull(EndingChoice.of(Outcome.P1, null))
        assertNull(EndingChoice.of(Outcome.NONE, Ending.WINNER))
        assertNull(EndingChoice.of(Outcome.P1, Ending.SERVE_FAULT))
    }

    @Test
    fun `only the group of the server has the ace`() {
        assertTrue(PointEndings.isAvailable(EndingChoice.P1_ACE, server = 1))
        assertFalse(PointEndings.isAvailable(EndingChoice.P2_ACE, server = 1))
        assertFalse(PointEndings.isAvailable(EndingChoice.P1_ACE, server = null))
        assertTrue(PointEndings.isAvailable(EndingChoice.P1_UNFORCED_ERROR, server = null))
    }

    @Test
    fun `an ending key gives the winner and the ending, and a fault clip becomes a point clip`() {
        val score = advanced().copy(details = mapOf("p1" to PointDetailV1(Ending.SERVE_FAULT)))

        val updated = PointEndings.withEnding(score, "p1", EndingChoice.P1_WINNER)

        assertEquals(Outcome.P1, updated.outcomeOf("p1"))
        assertEquals(PointDetailV1(Ending.WINNER), updated.detailOf("p1"))
    }

    @Test
    fun `the error kind stays only when the ending stays an error of the same player`() {
        val score = advanced().copy(
            outcomes = mapOf("p1" to Outcome.P2),
            details = mapOf("p1" to PointDetailV1(Ending.FORCED_ERROR, ErrorKind.NET)),
        )

        assertEquals(ErrorKind.NET, PointEndings.withEnding(score, "p1", EndingChoice.P1_UNFORCED_ERROR).detailOf("p1").errorKind)
        assertNull(PointEndings.withEnding(score, "p1", EndingChoice.P2_UNFORCED_ERROR).detailOf("p1").errorKind)
        assertNull(PointEndings.withEnding(score, "p1", EndingChoice.P2_WINNER).detailOf("p1").errorKind)
    }

    @Test
    fun `in advanced mode Q, W, and E always remove the ending`() {
        val score = advanced().copy(outcomes = mapOf("p1" to Outcome.P1), details = mapOf("p1" to PointDetailV1(Ending.WINNER)))

        val same = PointEndings.withWinnerOnly(score, "p1", Outcome.P1)
        val none = PointEndings.withWinnerOnly(score, "p1", Outcome.NONE)

        assertEquals(Outcome.P1, same.outcomeOf("p1"))
        assertTrue(same.details.isEmpty())
        assertEquals(Outcome.NONE, none.outcomeOf("p1"))
        assertTrue(none.details.isEmpty())
    }

    @Test
    fun `in basic mode Q, W, and E remove the ending only when they change the winner`() {
        val score = ScoreV1(outcomes = mapOf("p1" to Outcome.P1), details = mapOf("p1" to PointDetailV1(Ending.WINNER)))

        assertEquals(PointDetailV1(Ending.WINNER), PointEndings.withWinnerOnly(score, "p1", Outcome.P1).detailOf("p1"))
        assertTrue(PointEndings.withWinnerOnly(score, "p1", Outcome.P2).details.isEmpty())
    }

    @Test
    fun `a server change that changes no winner needs no confirmation`() {
        val points = points(4)
        val before = advanced().copy(outcomes = points.associate { it.id to Outcome.P1 }, serverMarks = mapOf("p1" to Outcome.P1))
        val after = before.copy(serverMarks = mapOf("p1" to Outcome.P2))

        assertNull(ServerChanges.preview(points, before, after))
    }

    @Test
    fun `a server change shows the ace with a new winner and the game that changes`() {
        val points = points(4)
        val before = advanced().copy(
            outcomes = points.associate { it.id to Outcome.P1 },
            details = mapOf("p1" to PointDetailV1(Ending.ACE)),
            serverMarks = mapOf("p1" to Outcome.P1),
        )
        val after = before.copy(serverMarks = mapOf("p1" to Outcome.P2))

        val preview = ServerChanges.preview(points, before, after)!!

        assertEquals(listOf(WinnerChange("p1", 1, doubleFault = false, from = Outcome.P1, to = Outcome.P2)), preview.winners)
        // Player 1 now has 3 points and player 2 has 1 point, so the first game has no winner yet.
        assertEquals(listOf(ScoreChange(set = 1, game = 1, from = 1, to = null)), preview.scoreChanges)
    }

    @Test
    fun `advanced mode numbers the packs, and basic mode numbers the clips`() {
        val points = points(2)
        val advanced = advanced().copy(
            outcomes = mapOf("p2" to Outcome.P1),
            details = mapOf("p1" to PointDetailV1(Ending.SERVE_FAULT), "p2" to PointDetailV1(Ending.ACE)),
            serverMarks = mapOf("p2" to Outcome.P1),
        )
        val basic = advanced.copy(scoringMode = ScoringMode.BASIC)

        val advancedPreview = ServerChanges.preview(points, advanced, advanced.copy(serverMarks = mapOf("p2" to Outcome.P2)))!!
        val basicPreview = ServerChanges.preview(points, basic, basic.copy(serverMarks = mapOf("p2" to Outcome.P2)))!!

        assertEquals(listOf(1), advancedPreview.winners.map { it.number })
        assertEquals(listOf(2), basicPreview.winners.map { it.number })
    }

    @Test
    fun `a double fault shows as a double fault`() {
        val points = points(3)
        val before = advanced().copy(
            outcomes = mapOf("p2" to Outcome.P2, "p3" to Outcome.P1),
            details = mapOf("p1" to PointDetailV1(Ending.SERVE_FAULT), "p2" to PointDetailV1(Ending.SERVE_FAULT)),
            serverMarks = mapOf("p1" to Outcome.P1),
        )

        val preview = ServerChanges.preview(points, before, before.copy(serverMarks = mapOf("p1" to Outcome.P2)))!!

        assertEquals(listOf(WinnerChange("p2", 1, doubleFault = true, from = Outcome.P2, to = Outcome.P1)), preview.winners)
    }

    private fun advanced() = ScoreV1(scoringMode = ScoringMode.ADVANCED)

    private fun points(count: Int): List<PointV1> =
        (1..count).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
}
