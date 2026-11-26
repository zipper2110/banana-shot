package org.litvin.ui.tabs.points.detect

import org.litvin.autodetect.DetectedRally
import org.litvin.autodetect.NetHit
import org.litvin.autodetect.ShotChain
import org.litvin.ui.tabs.points.ui.ScorePlayers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The winner texts after E142: the winner is the evidence of the rally; only a score decoder result names the decoder. */
class EvidenceTextsTest {
    private val players = ScorePlayers(p1Name = "Ann", p2Name = "Bob")

    private fun outcome(rally: DetectedRally) = EvidenceTexts.outcomeLines(rally, near = 1, players = players)

    @Test
    fun `with no decoder the winner line names the rally evidence`() {
        val lines = outcome(DetectedRally(pBall = 0.8, pNearWins = 0.8))
        assertTrue("Rally evidence: 80 % Ann" in lines, lines.toString())
        assertFalse(lines.any { "score" in it }, lines.toString())
    }

    @Test
    fun `a result of the score decoder names the decoder and warns when it changed the winner`() {
        val lines = outcome(DetectedRally(pBall = 0.7, pNearWins = 0.4))
        assertTrue("Rally evidence: 70 % Ann → score decoder: 60 % Bob" in lines, lines.toString())
        assertTrue("Warning: the score decoder changed the winner of the rally evidence." in lines, lines.toString())
    }

    @Test
    fun `the chain and net warnings name the winner of the rally`() {
        val chain = outcome(DetectedRally(pBall = 0.8, pNearWins = 0.8, chain = ShotChain(nearWins = false)))
        assertTrue("Warning: the winner of the rally and the shot chain are different." in chain, chain.toString())
        val net = outcome(DetectedRally(pBall = 0.8, pNearWins = 0.8, netHit = NetHit(t = 10.0, hitterNear = true)))
        assertTrue("Warning: the winner of the rally is the player who hit the net." in net, net.toString())
        assertEquals(0, (chain + net).count { "score model" in it })
    }
}
