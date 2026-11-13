package org.litvin.ui.tabs.points

import org.litvin.scoring.EndingChoice
import org.litvin.scoring.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** B-68 E3: what X, L, and the other scoring keys do with and without a pending point and an active clip. */
class ScoreKeysTest {
    private val requests = listOf(
        ScoreRequest.Fault,
        ScoreRequest.Let,
        ScoreRequest.Winner(Outcome.P1),
        ScoreRequest.WithEnding(EndingChoice.P1_WINNER),
    )

    private fun decide(request: ScoreRequest, pending: Boolean, active: Boolean, packHasServeClip: Boolean = false) =
        ScoreKeys.decide(request, pending, active) { packHasServeClip }

    @Test
    fun `with a pending point, each key ends it`() {
        requests.forEach { request ->
            assertEquals(ScoreKeyDecision(ScoreKeyAction.END_PENDING), decide(request, pending = true, active = true))
            assertEquals(ScoreKeyDecision(ScoreKeyAction.END_PENDING), decide(request, pending = true, active = false))
        }
    }

    @Test
    fun `with no pending point and an active clip, each key changes the clip`() {
        requests.forEach { request ->
            assertEquals(ScoreKeyDecision(ScoreKeyAction.CHANGE_ACTIVE), decide(request, pending = false, active = true, packHasServeClip = true))
        }
    }

    @Test
    fun `X with no point adds a fault clip of zero length, unless the pack already has a serve clip`() {
        assertEquals(ScoreKeyDecision(ScoreKeyAction.ADD_ZERO_LENGTH_FAULT), decide(ScoreRequest.Fault, pending = false, active = false))
        assertEquals(
            ScoreKeyDecision(ScoreKeyAction.HINT, KeyHint.FAULT_IN_SERVE_PACK),
            decide(ScoreRequest.Fault, pending = false, active = false, packHasServeClip = true),
        )
    }

    @Test
    fun `L with no point shows a hint, and the other keys do nothing`() {
        assertEquals(ScoreKeyDecision(ScoreKeyAction.HINT, KeyHint.LET_WITHOUT_POINT), decide(ScoreRequest.Let, pending = false, active = false))
        assertEquals(ScoreKeyDecision(ScoreKeyAction.NONE), decide(ScoreRequest.Winner(Outcome.P2), pending = false, active = false))
        assertEquals(
            ScoreKeyDecision(ScoreKeyAction.NONE),
            decide(ScoreRequest.WithEnding(EndingChoice.P2_ACE), pending = false, active = false),
        )
    }

    @Test
    fun `the check of the pack runs only for X with no point`() {
        val failing = { fail("the pack check must not run") }
        ScoreKeys.decide(ScoreRequest.Fault, pending = true, hasActiveClip = false, packHasServeClip = failing)
        ScoreKeys.decide(ScoreRequest.Fault, pending = false, hasActiveClip = true, packHasServeClip = failing)
        ScoreKeys.decide(ScoreRequest.Let, pending = false, hasActiveClip = false, packHasServeClip = failing)
    }

    @Test
    fun `each key hint has its own hint id`() {
        assertEquals(KeyHint.entries.size, KeyHint.entries.map { it.id }.toSet().size)
    }
}
