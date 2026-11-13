package org.litvin.scoring

import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-68 E3: X and L, the clips of zero length, and the outcome of the Edit dialog. */
class ClipOutcomesTest {
    private val points = listOf(PointV1("a", 1_000, 2_000), PointV1("b", 3_000, 4_000))
    private val advanced = ScoreV1(scoringMode = ScoringMode.ADVANCED, serverMarks = mapOf("a" to Outcome.P1))

    @Test
    fun `X makes a fault clip, and the second fault of the pack gives the receiver the point`() {
        val once = PointDetails.resolve(points, PointEndings.withServeEnding(advanced, "a", Ending.SERVE_FAULT))
        assertEquals(Outcome.NONE, once.outcomeOf("a"))
        val twice = PointDetails.resolve(points, PointEndings.withServeEnding(once, "b", Ending.SERVE_FAULT))
        assertEquals(Outcome.P2, twice.outcomeOf("b"))
        assertEquals(ClipKind.DOUBLE_FAULT, ServePacks.of(points, twice.outcomes, twice.details).of("b")?.kind)
    }

    @Test
    fun `L makes a let clip with no winner and no error kind`() {
        val point = advanced.copy(outcomes = mapOf("a" to Outcome.P2), details = mapOf("a" to PointDetailV1(Ending.UNFORCED_ERROR, ErrorKind.NET)))
        val let = PointEndings.withServeEnding(point, "a", Ending.LET)
        assertEquals(Outcome.NONE, let.outcomeOf("a"))
        assertEquals(PointDetailV1(Ending.LET), let.detailOf("a"))
    }

    @Test
    fun `only a fault clip can have zero length`() {
        val zero = PointV1("z", 5_000, 5_000)
        assertTrue(PointEndings.isZeroLength(zero))
        assertFalse(PointEndings.canScore(zero, advanced))
        assertTrue(PointEndings.canScore(zero, advanced.withDetail("z", PointDetailV1(Ending.SERVE_FAULT))))
        assertTrue(PointEndings.allowsZeroLength(Ending.SERVE_FAULT))
        assertFalse(PointEndings.allowsZeroLength(Ending.LET))
        assertTrue(ClipOutcomes.allows(zero, ClipOutcome(ClipChoice.FAULT)))
        assertFalse(ClipOutcomes.allows(zero, ClipOutcome(ClipChoice.POINT, 1)))
        assertTrue(ClipOutcomes.allows(points[0], ClipOutcome(ClipChoice.LET)))
    }

    @Test
    fun `the dialog reads the kind, the winner, the ending, and the error kind`() {
        val score = advanced.copy(
            outcomes = mapOf("a" to Outcome.P1, "b" to Outcome.NONE),
            details = mapOf("a" to PointDetailV1(Ending.FORCED_ERROR, ErrorKind.WIDE)),
        )
        assertEquals(ClipOutcome(ClipChoice.POINT, 1, Ending.FORCED_ERROR, ErrorKind.WIDE), ClipOutcomes.current(score, "a"))
        assertEquals(ClipOutcome(ClipChoice.NO_POINT), ClipOutcomes.current(score, "b"))
        val fault = score.withDetail("b", PointDetailV1(Ending.SERVE_FAULT))
        assertEquals(ClipOutcome(ClipChoice.FAULT), ClipOutcomes.current(fault, "b"))
    }

    @Test
    fun `normalized keeps only the fields of the kind and gives an ace to the server`() {
        assertEquals(ClipOutcome(ClipChoice.LET), ClipOutcomes.normalized(ClipOutcome(ClipChoice.LET, 2, Ending.ACE, ErrorKind.NET)))
        assertEquals(
            ClipOutcome(ClipChoice.POINT, 2, Ending.WINNER),
            ClipOutcomes.normalized(ClipOutcome(ClipChoice.POINT, 2, Ending.WINNER, ErrorKind.LONG)),
        )
        assertEquals(ClipOutcome(ClipChoice.POINT, 1, Ending.ACE), ClipOutcomes.normalized(ClipOutcome(ClipChoice.POINT, 2, Ending.ACE), server = 1))
        assertEquals(ClipOutcome(ClipChoice.POINT, 1), ClipOutcomes.normalized(ClipOutcome(ClipChoice.POINT, null, Ending.LET)))
    }

    @Test
    fun `apply writes the outcome, and resolve applies the rules of the ace`() {
        val point = ClipOutcomes.apply(advanced, "a", ClipOutcome(ClipChoice.POINT, 2, Ending.UNFORCED_ERROR, ErrorKind.NET))
        assertEquals(Outcome.P2, point.outcomeOf("a"))
        assertEquals(PointDetailV1(Ending.UNFORCED_ERROR, ErrorKind.NET), point.detailOf("a"))

        // An ace of player 2 on a serve of player 1 goes to the server.
        val ace = PointDetails.resolve(points, ClipOutcomes.apply(advanced, "a", ClipOutcome(ClipChoice.POINT, 2, Ending.ACE)))
        assertEquals(Outcome.P1, ace.outcomeOf("a"))

        val none = ClipOutcomes.apply(point, "a", ClipOutcome(ClipChoice.NO_POINT))
        assertEquals(Outcome.NONE, none.outcomeOf("a"))
        assertTrue(none.details.isEmpty())

        val fault = ClipOutcomes.apply(point, "a", ClipOutcome(ClipChoice.FAULT))
        assertEquals(PointDetailV1(Ending.SERVE_FAULT), fault.detailOf("a"))
        assertEquals(Outcome.NONE, fault.outcomeOf("a"))
    }
}
