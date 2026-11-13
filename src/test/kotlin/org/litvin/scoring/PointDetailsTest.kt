package org.litvin.scoring

import org.litvin.points.PointV1
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PointDetailsTest {
    @Test
    fun aFaultThenAPointMakeOnePackOnTheSecondServe() {
        val points = points(2)
        val outcomes = mapOf("p2" to Outcome.P1)
        val details = mapOf("p1" to PointDetailV1(Ending.SERVE_FAULT))

        val packs = ServePacks.of(points, outcomes, details)

        assertEquals(listOf(ClipKind.FAULT, ClipKind.POINT), packs.clips.map { it.kind })
        assertEquals(listOf(1, 2), packs.clips.map { it.serve })
        assertEquals(listOf(1, 1), packs.clips.map { it.number })
        assertEquals(listOf(ServePack(listOf("p1", "p2"), complete = true)), packs.packs)
    }

    @Test
    fun theSecondFaultIsADoubleFaultAndEndsThePack() {
        val points = points(3)
        val details = mapOf("p1" to PointDetailV1(Ending.SERVE_FAULT), "p2" to PointDetailV1(Ending.SERVE_FAULT))

        val packs = ServePacks.of(points, mapOf("p3" to Outcome.P2), details)

        assertEquals(listOf(ClipKind.FAULT, ClipKind.DOUBLE_FAULT, ClipKind.POINT), packs.clips.map { it.kind })
        assertEquals(listOf(1, 2, 1), packs.clips.map { it.serve })
        assertEquals(listOf(1, 1, 2), packs.clips.map { it.number })
        assertTrue(packs.clips[1].lastOfPack)
    }

    @Test
    fun aLetDoesNotChangeTheServeNumber() {
        val points = points(4)
        val details = mapOf(
            "p1" to PointDetailV1(Ending.LET),
            "p2" to PointDetailV1(Ending.SERVE_FAULT),
            "p3" to PointDetailV1(Ending.LET),
        )

        val packs = ServePacks.of(points, mapOf("p4" to Outcome.P1), details)

        assertEquals(listOf(1, 1, 2, 2), packs.clips.map { it.serve })
        assertEquals(1, packs.packs.size)
    }

    @Test
    fun aNoPointClipStandsAloneAndGetsANumber() {
        val points = points(4)
        val outcomes = mapOf("p1" to Outcome.P1, "p2" to Outcome.NONE, "p4" to Outcome.P2)
        val details = mapOf("p3" to PointDetailV1(Ending.SERVE_FAULT))

        val packs = ServePacks.of(points, outcomes, details)

        assertEquals(listOf(ClipKind.POINT, ClipKind.NO_POINT, ClipKind.FAULT, ClipKind.POINT), packs.clips.map { it.kind })
        assertEquals(listOf(1, 2, 3, 3), packs.clips.map { it.number })
        assertNull(packs.clips[1].serve)
        assertNull(packs.clips[1].pack)
        assertEquals(3, packs.count)
    }

    @Test
    fun aNoPointClipInsideAPackKeepsThePackTogether() {
        val points = points(3)
        val outcomes = mapOf("p2" to Outcome.NONE, "p3" to Outcome.P1)
        val details = mapOf("p1" to PointDetailV1(Ending.SERVE_FAULT))

        val packs = ServePacks.of(points, outcomes, details)

        assertEquals(listOf(1, 2, 1), packs.clips.map { it.number })
        assertEquals(2, packs.clips[2].serve)
        assertEquals(listOf("p1", "p3"), packs.packOf("p3")?.clipIds)
    }

    @Test
    fun aPackAtTheEndCanHaveNoPointClip() {
        val points = points(2)
        val details = mapOf("p2" to PointDetailV1(Ending.SERVE_FAULT))

        val packs = ServePacks.of(points, mapOf("p1" to Outcome.P1), details)

        assertFalse(packs.packs.last().complete)
        assertEquals(2, packs.serveAt(100_000))
    }

    @Test
    fun theServeAtTheTimeComesFromTheOpenPackBeforeIt() {
        val points = points(3)
        val details = mapOf("p2" to PointDetailV1(Ending.SERVE_FAULT))
        val packs = ServePacks.of(points, mapOf("p1" to Outcome.P1, "p3" to Outcome.P2), details)

        assertEquals(1, packs.serveAt(0))
        assertEquals(1, packs.serveAt(17_000))
        // After the fault of point 2 and before point 3.
        assertEquals(2, packs.serveAt(27_000))
        assertEquals(1, packs.serveAt(37_000))
    }

    @Test
    fun aClipOfZeroLengthCountsAtItsTime() {
        val points = listOf(PointV1(id = "f", startMs = 5_000, endMs = 5_000))
        val packs = ServePacks.of(points, emptyMap(), mapOf("f" to PointDetailV1(Ending.SERVE_FAULT)))

        assertEquals(1, packs.serveAt(4_990))
        assertEquals(2, packs.serveAt(5_000))
    }

    @Test
    fun aClipOfZeroLengthComesBeforeAClipThatStartsAtTheSameTime() {
        val points = listOf(PointV1(id = "p", startMs = 5_000, endMs = 9_000), PointV1(id = "f", startMs = 5_000, endMs = 5_000))
        val packs = ServePacks.of(points, mapOf("p" to Outcome.P1), mapOf("f" to PointDetailV1(Ending.SERVE_FAULT)))

        assertEquals(listOf("f", "p"), packs.clips.map { it.pointId })
        assertEquals(2, packs.of("p")?.serve)
    }

    @Test
    fun thePackAtTheTimeHasAServeClipBeforeOrAfterIt() {
        val points = points(4)
        val details = mapOf("p2" to PointDetailV1(Ending.LET))
        val packs = ServePacks.of(points, mapOf("p1" to Outcome.P1, "p3" to Outcome.P2, "p4" to Outcome.P1), details)

        assertFalse(packs.packAtHasServeClip(5_000))
        // Between the let of point 2 and point 3.
        assertTrue(packs.packAtHasServeClip(27_000))
        // Before the let: the let belongs to the pack of the new clip.
        assertTrue(packs.packAtHasServeClip(19_000))
        assertFalse(packs.packAtHasServeClip(37_000))
    }

    @Test
    fun aDoubleFaultGoesToTheReceiver() {
        val points = points(2)
        val score = ScoreV1(
            serverMarks = mapOf("p1" to Outcome.P1),
            details = mapOf("p1" to PointDetailV1(Ending.SERVE_FAULT), "p2" to PointDetailV1(Ending.SERVE_FAULT)),
        )

        val outcomes = PointDetails.resolveOutcomes(points, score)

        assertEquals(Outcome.NONE, outcomes["p1"])
        assertEquals(Outcome.P2, outcomes["p2"])
    }

    @Test
    fun anAceGoesToTheServer() {
        val points = points(1)
        val score = ScoreV1(
            outcomes = mapOf("p1" to Outcome.P1),
            serverMarks = mapOf("p1" to Outcome.P2),
            details = mapOf("p1" to PointDetailV1(Ending.ACE)),
        )

        assertEquals(Outcome.P2, PointDetails.resolveOutcomes(points, score)["p1"])
    }

    @Test
    fun withoutAServerAnAceKeepsItsOutcome() {
        val points = points(1)
        val score = ScoreV1(outcomes = mapOf("p1" to Outcome.P2), details = mapOf("p1" to PointDetailV1(Ending.ACE)))

        assertEquals(Outcome.P2, PointDetails.resolveOutcomes(points, score)["p1"])
    }

    @Test
    fun aNewWinnerMovesTheServerAndTheLaterAces() {
        // Player 1 serves the first game. Four aces of player 1 win the game, then player 2 serves an ace.
        val points = points(5)
        val aces = (1..5).associate { "p$it" to PointDetailV1(Ending.ACE) }
        val score = ScoreV1(
            outcomes = (1..5).associate { "p$it" to Outcome.P1 },
            serverMarks = mapOf("p1" to Outcome.P1),
            details = aces,
        )

        val outcomes = PointDetails.resolveOutcomes(points, score)

        assertEquals(listOf(Outcome.P1, Outcome.P1, Outcome.P1, Outcome.P1, Outcome.P2), (1..5).map { outcomes["p$it"] })
    }

    @Test
    fun aSingleFaultAndALetHaveNoWinner() {
        val points = points(2)
        val score = ScoreV1(
            outcomes = mapOf("p1" to Outcome.P1, "p2" to Outcome.P2),
            details = mapOf("p1" to PointDetailV1(Ending.SERVE_FAULT), "p2" to PointDetailV1(Ending.LET)),
        )

        val outcomes = PointDetails.resolveOutcomes(points, score)

        assertEquals(Outcome.NONE, outcomes["p1"])
        assertEquals(Outcome.NONE, outcomes["p2"])
    }

    @Test
    fun theChangedWinnersTreatAMissingOutcomeAsNoWinner() {
        val before = mapOf("a" to Outcome.P1, "b" to Outcome.NONE)
        val after = mapOf("a" to Outcome.P2, "c" to Outcome.NONE)

        assertEquals(setOf("a"), PointDetails.changedWinners(before, after))
    }

    @Test
    fun thePlayerOfTheEndingComesFromTheWinner() {
        assertEquals(1, PointDetails.playerOfEnding(Ending.WINNER, Outcome.P1))
        assertEquals(2, PointDetails.playerOfEnding(Ending.ACE, Outcome.P2))
        assertEquals(2, PointDetails.playerOfEnding(Ending.UNFORCED_ERROR, Outcome.P1))
        assertEquals(1, PointDetails.playerOfEnding(Ending.FORCED_ERROR, Outcome.P2))
        assertNull(PointDetails.playerOfEnding(null, Outcome.P1))
        assertNull(PointDetails.playerOfEnding(Ending.WINNER, Outcome.NONE))
    }

    @Test
    fun anOldScoreFileOpensInBasicModeWithNoDetails() {
        val file = tempScoreFile("""{"outcomes":{"p1":"P1"},"version":1}""")

        val score = ScoreIO.read(file.path)

        assertEquals(ScoringMode.BASIC, score.scoringMode)
        assertTrue(score.details.isEmpty())
        assertFalse(score.askErrorKind)
    }

    @Test
    fun theDetailsAndTheModeSurviveAWriteAndARead() {
        val file = tempScoreFile("{}")
        val score = ScoreV1(
            scoringMode = ScoringMode.ADVANCED,
            details = mapOf("p1" to PointDetailV1(Ending.UNFORCED_ERROR, ErrorKind.NET)),
            askErrorKind = true,
        )

        ScoreIO.write(file.path, score)
        val text = file.readText()

        assertEquals(score.details, ScoreIO.read(file.path).details)
        assertEquals(ScoringMode.ADVANCED, ScoreIO.read(file.path).scoringMode)
        assertFalse("\"advanced\"" in text, text)
        assertFalse("\"empty\"" in text, text)
    }

    @Test
    fun anEmptyDetailRemovesTheEntry() {
        val score = ScoreV1().withDetail("p1", PointDetailV1(Ending.LET)).withDetail("p1", PointDetailV1())

        assertTrue(score.details.isEmpty())
    }

    private fun tempScoreFile(text: String): File {
        val dir = Files.createTempDirectory("point-details").toFile().apply { deleteOnExit() }
        return File(dir, "score.json").apply {
            writeText(text)
            deleteOnExit()
        }
    }

    private fun points(count: Int): List<PointV1> =
        (1..count).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
}
