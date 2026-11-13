package org.litvin.ui.tabs.points

import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.ErrorKind
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetailV1
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringMode
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.tabs.points.ui.PackPosition
import java.awt.event.ActionEvent
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-68 E3b: the packs in the points list, the row labels, the navigator numbers, and Ctrl+R. */
class PointPacksTest {
    // Pack 1: a 1st serve fault of zero length, a let, then a 2nd serve point with an unforced error into the net.
    // Then a W clip (number 2). Pack 3: a double fault (two fault clips). Pack 4: an ace on the 1st serve.
    private val points = listOf(
        PointV1("F1", 1_000, 1_000),
        PointV1("L1", 2_000, 4_000),
        PointV1("P1", 5_000, 9_000),
        PointV1("W", 10_000, 12_000),
        PointV1("F2", 13_000, 14_000),
        PointV1("D2", 15_000, 16_000),
        PointV1("A3", 17_000, 18_000),
    )
    private val score = ScoreV1(
        scoringMode = ScoringMode.ADVANCED,
        outcomes = mapOf("P1" to Outcome.P2, "W" to Outcome.NONE, "D2" to Outcome.P2, "A3" to Outcome.P1),
        details = mapOf(
            "F1" to PointDetailV1(Ending.SERVE_FAULT),
            "L1" to PointDetailV1(Ending.LET),
            "P1" to PointDetailV1(Ending.UNFORCED_ERROR, ErrorKind.NET),
            "F2" to PointDetailV1(Ending.SERVE_FAULT),
            "D2" to PointDetailV1(Ending.SERVE_FAULT),
            "A3" to PointDetailV1(Ending.ACE),
        ),
        serverMarks = mapOf("F1" to Outcome.P1),
    )
    private val packs = PointPacks(points, score)

    @Test
    fun `each row has the label of its serve and its ending`() {
        assertEquals(
            listOf(
                "1st serve · Fault", "Let", "2nd serve · UE net", "No point",
                "1st serve · Fault", "2nd serve · Double fault", "1st serve · Ace",
            ),
            points.map { packs.row(it.id)!!.label.text() },
        )
    }

    @Test
    fun `a point clip with no ending shows only its serve, and an error kind needs an error ending`() {
        val plain = PointPacks(
            listOf(PointV1("Q", 1_000, 2_000), PointV1("E", 3_000, 4_000), PointV1("X", 5_000, 6_000)),
            ScoreV1(
                scoringMode = ScoringMode.ADVANCED,
                outcomes = mapOf("Q" to Outcome.P1, "E" to Outcome.P2, "X" to Outcome.P1),
                details = mapOf(
                    "E" to PointDetailV1(Ending.FORCED_ERROR, ErrorKind.LONG),
                    "X" to PointDetailV1(Ending.WINNER, ErrorKind.WIDE),
                ),
            ),
        )
        assertEquals("1st serve", plain.row("Q")!!.label.text())
        assertEquals("1st serve · FE long", plain.row("E")!!.label.text())
        assertEquals("1st serve · Winner", plain.row("X")!!.label.text())
    }

    @Test
    fun `the number shows on the first row of each pack, and the numbers go over the packs and the W clips`() {
        val rows = points.map { packs.row(it.id)!! }
        assertEquals(listOf(1, 1, 1, 2, 3, 3, 4), rows.map { it.number })
        assertEquals(listOf(true, false, false, true, true, false, true), rows.map { it.showNumber })
        assertEquals(
            listOf(
                PackPosition.FIRST, PackPosition.MIDDLE, PackPosition.LAST, PackPosition.SINGLE,
                PackPosition.FIRST, PackPosition.LAST, PackPosition.SINGLE,
            ),
            rows.map { it.position },
        )
        assertEquals(listOf(true, true, false, false, true, false, false), rows.map { it.dimmed })
    }

    @Test
    fun `the navigator shows the pack number and the pack count`() {
        assertEquals(PointNumber(1, 4), packs.number("P1"))
        assertEquals(PointNumber(2, 4), packs.number("W"))
        assertEquals(PointNumber(3, 4), packs.number("F2"))
        assertNull(packs.number("unknown"))
    }

    @Test
    fun `the pack keys go to the first clip of the next and the previous pack`() {
        assertEquals("W", packs.packTarget("L1", 0, +1))
        assertEquals("F2", packs.packTarget("W", 0, +1))
        assertEquals("F1", packs.packTarget("W", 0, -1))
        // From the middle of a pack, the previous pack is the pack before it.
        assertEquals("W", packs.packTarget("D2", 0, -1))
        assertNull(packs.packTarget("A3", 0, +1))
        assertNull(packs.packTarget("P1", 0, -1))
        // With no selected clip, the playhead decides. The clip of zero length at 1 000 ms is the first pack.
        assertEquals("F1", packs.packTarget(null, 500, +1))
        assertEquals("W", packs.packTarget(null, 9_500, +1))
        assertEquals("W", packs.packTarget(null, 12_500, -1))
    }

    @Test
    fun `basic mode has no packs, no labels, and no dimmed clips`() {
        val basic = PointPacks(points, score.copy(scoringMode = ScoringMode.BASIC))
        assertFalse(basic.advanced)
        assertNull(basic.row("F1"))
        assertNull(basic.number("F1"))
        assertNull(basic.packTarget("F1", 0, +1))
        assertFalse(basic.isDimmed("F1"))
        assertTrue(packs.isDimmed("F1"))
        assertFalse(packs.isDimmed("D2"))
    }

    @Test
    fun `the cache computes the packs again only after a change`() {
        val cache = PointPacksCache()
        val first = cache.of(points, score)
        assertTrue(first === cache.of(points, score))
        assertFalse(first === cache.of(points, score.copy()))
    }

    @Test
    fun `Ctrl+R goes to the first clip of the next pack, and R stays the next clip`() {
        val dir = Files.createTempDirectory("b68-packs").toFile()
        try {
            ScoreIO.writeForProjectDir(dir.path, score.copy(scoreSettingsReviewed = true))
            SwingUtilities.invokeAndWait {
                val steps = mutableListOf<Int>()
                val played = mutableListOf<String>()
                val scoring = PointsScoring(
                    onStep = { steps += it }, onToggleFavorite = {}, onScoreChanged = {}, onError = {}, onOverlayChanged = {},
                    onScoreRequest = {}, loadFrame = {}, onPlayPoint = { played += it },
                )
                scoring.load(dir.path)
                scoring.setPoints(points)
                scoring.select("L1")
                val target = JPanel()
                scoring.installKeys(target) { false }
                fun press(stroke: String) {
                    val name = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(stroke))
                    target.actionMap.get(name).actionPerformed(ActionEvent(target, ActionEvent.ACTION_PERFORMED, stroke))
                }
                // The R binding does not have the Ctrl+R stroke (Cmd+R on macOS), and the other way round.
                val map = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                val next = AppShortcuts.NEXT_PACK.keyStroke
                assertTrue(map.get(KeyStroke.getKeyStroke(next)) != null)
                assertTrue(map.get(KeyStroke.getKeyStroke("R")) != map.get(KeyStroke.getKeyStroke(next)))

                press(next)
                scoring.select("W")
                press(next)
                press(AppShortcuts.PREVIOUS_PACK.keyStroke)
                assertEquals(listOf("W", "F2", "F1"), played)
                scoring.select("L1")
                press("R")
                assertEquals(listOf(1), steps)

                // The navigator shows the pack number and the pack count.
                assertEquals("Point 1 / 4", scoring.controls.pointLabel.text)
                assertEquals("1st serve · Fault", scoring.pointRowInfo("F1")!!.label.text())
                assertTrue(scoring.isDimmedClip("L1"))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
