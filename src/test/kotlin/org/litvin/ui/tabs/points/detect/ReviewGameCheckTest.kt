package org.litvin.ui.tabs.points.detect

import org.junit.jupiter.api.Test
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoringEngine
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewGameCheckTest {
    /**
     * A synthetic match: 6 games of 4 points, and the server wins each point. Player 1 serves game 1. Each point is
     * 10 s, with a gap of 20 s. [missing] is the index of a real point that the detection did not find.
     */
    private class Match(missing: Int? = null, wrongDetected: Int? = null) {
        val realServers = (0 until 24).map { if ((it / 4) % 2 == 0) 1 else 2 }
        val points = (0 until 24).filter { it != missing }.map { PointV1(id = "r$it", startMs = 10_000 + it * 30_000, endMs = 20_000 + it * 30_000) }
        private val outcomes = points.associate { it.id to if (realServer(it) == 1) Outcome.P1 else Outcome.P2 }

        // The import mark: the first clip, with its detected server.
        private val timeline = ScoringEngine.timeline(points, outcomes, serverMarks = mapOf(points[0].id to Outcome.P1))
        val gameEnds = points.indices.filter { index ->
            val before = timeline.stateBefore(index)
            val after = timeline.statesAfterPoint[index]
            before.gamesP1 + before.gamesP2 + 100 * (before.setsP1 + before.setsP2) != after.gamesP1 + after.gamesP2 + 100 * (after.setsP1 + after.setsP2)
        }.map { points[it].id }.toSet()
        private val serverOf = points.indices.associate { points[it].id to timeline.serverOfPoint[it] }
        private val wrongId = wrongDetected?.let { "r$it" }

        fun realServer(point: PointV1) = realServers[point.id.drop(1).toInt()]
        fun items() = ReviewGameCheck.items(points, gameEnds, { serverOf[it] }) { id ->
            val real = realServers[id.drop(1).toInt()]
            if (id == wrongId) 3 - real else real
        }
    }

    @Test
    fun `a match with no missed point has no item`() {
        assertEquals(emptyList(), Match().items())
    }

    @Test
    fun `one wrong detected server gives no item`() {
        assertEquals(emptyList(), Match(wrongDetected = 13).items())
    }

    @Test
    fun `a match with one missed point gives an item that points at the gap of the missed point`() {
        // The last point of game 3 (r11) is missing.
        val match = Match(missing = 11)
        val items = match.items()

        assertEquals(1, items.size)
        val item = items.single()
        // The score counts the 4 points of game 4 in game 3: from the first point of game 4 the servers do not agree.
        assertEquals("r12", item.key)
        assertEquals(3, item.firstGame)
        // The longest gap is the gap of the missed point, before r12.
        assertEquals("r12", match.points[item.gapIndex].id)
    }

    @Test
    fun `a missed first point of a game also gives an item before the place`() {
        // The first point of game 3 (r8) is missing.
        val match = Match(missing = 8)
        val items = match.items()

        assertTrue(items.isNotEmpty())
        assertEquals("r9", match.points[items.first().gapIndex].id)
    }

    @Test
    fun `with no server in the score, the check has no item`() {
        val points = (0 until 8).map { PointV1(id = "p$it", startMs = it * 30_000, endMs = it * 30_000 + 10_000) }
        assertEquals(emptyList(), ReviewGameCheck.items(points, setOf("p3"), { null }) { 1 })
    }

    @Test
    fun `the games of the list end at the game ends, and the last game has the rest of the points`() {
        val points = (0 until 7).map { PointV1(id = "p$it", startMs = it * 30_000, endMs = it * 30_000 + 10_000) }
        assertEquals(
            listOf(ReviewGameCheck.Game(1, 0, 3), ReviewGameCheck.Game(2, 4, 6)),
            ReviewGameCheck.games(points, setOf("p3")),
        )
    }
}
