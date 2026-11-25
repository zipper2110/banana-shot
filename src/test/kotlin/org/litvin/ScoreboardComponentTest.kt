package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreboardComponentTest {
    @Test
    fun classicDisplayNormalizesNamesColorsPointsAndSets() {
        val display = ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                p1Name = "Alexandria Very Long Surname",
                p2Name = "Bob",
                p1ColorHex = "#112233",
                p2ColorHex = "#445566",
                p1Pts = 4,
                p2Pts = 3,
                gamesP1 = 5,
                gamesP2 = 4,
                completedSets = listOf(6 to 2, 3 to 6, 7 to 5),
            )
        )

        assertEquals("ALEXANDRIA VERY LONG SU…", display.player1Name)
        assertEquals("BOB", display.player2Name)
        assertEquals(0x112233, display.player1Rgb)
        assertEquals(0x445566, display.player2Rgb)
        assertEquals("Ad", display.player1PointText)
        assertEquals("40", display.player2PointText)
        assertEquals(listOf(6 to 2, 3 to 6, 7 to 5), display.completedSets)
        assertTrue(display.player1Leading)
        assertEquals(1, display.pointLeader)
    }

    @Test
    fun nameOfMaximumSettingsLengthIsNotShortened() {
        // 24 characters: the scoring settings accept this length.
        val team = "Lebrón / Galán Ortega Ab"
        assertEquals(ScoreboardComponent.PLAYER_NAME_MAX_CHARS, team.length)

        val display = ScoreboardComponent.display(OverlaySpan(startMs = 0L, endMs = 1_000L, text = "", p1Name = team))

        assertEquals("LEBRÓN / GALÁN ORTEGA AB", display.player1Name)
    }

    @Test
    fun tiedFortyIsNotLeading() {
        val display = ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                p1Pts = 3,
                p2Pts = 3,
            )
        )

        assertEquals("40", display.player1PointText)
        assertEquals("40", display.player2PointText)
        assertFalse(display.player1Leading)
        assertEquals(0, display.pointLeader)
    }

    @Test
    fun advantageAfterDeuceLeadsForTheAdvantagePlayer() {
        val display = ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                p1Pts = 5,
                p2Pts = 6,
            )
        )

        assertEquals("40", display.player1PointText)
        assertEquals("Ad", display.player2PointText)
        assertEquals(2, display.pointLeader)
    }

    @Test
    fun finishedBestOfThreeShowsAllSetsAndNoNewSet() {
        // The sets of a user report: 6–3, 6–7 (5–7), 4–6. The board showed 6–7, 4–6 and an empty 0–0 set.
        val display = ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                setsP1 = 1,
                setsP2 = 2,
                completedSets = listOf(6 to 3, 6 to 7, 4 to 6),
                completedSetTiebreaks = listOf(null, 5 to 7, null),
                server = 1,
                matchEnded = true,
            )
        )

        assertEquals(listOf(6 to 3, 6 to 7), display.completedSets)
        assertEquals(listOf(null, 5 to 7), display.completedSetTiebreaks)
        assertEquals(4 to 6, display.player1Games to display.player2Games)
        assertEquals("" to "", display.player1PointText to display.player2PointText)
        assertEquals(0, display.server)
        assertEquals(0, display.pointLeader)
    }

    @Test
    fun matchWithNoEndShowsTheFirstSetInTheFourthSet() {
        // A user report: doubles, 3 sets, "no match end". After the third set the board showed 0–6, 2–6 and 0–0.
        // The first set (1–6) was not shown.
        val display = ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                setsP1 = 0,
                setsP2 = 3,
                completedSets = listOf(1 to 6, 0 to 6, 2 to 6),
                matchEnded = false,
            )
        )

        assertEquals(listOf(1 to 6, 0 to 6, 2 to 6), display.completedSets)
        assertEquals(0 to 0, display.player1Games to display.player2Games)
    }

    @Test
    fun theBoardShowsEveryFinishedSetForAnyNumberOfSets() {
        // Guard: the board never selects or drops finished sets, during the match and after the match end.
        for (count in 0..9) {
            val sets = (1..count).map { if (it % 2 == 0) it to 6 else 6 to it }
            val tiebreaks = (1..count).map { if (it % 3 == 0) 7 to it else null }
            val span = OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                gamesP1 = 3,
                gamesP2 = 1,
                completedSets = sets,
                completedSetTiebreaks = tiebreaks,
            )

            val live = ScoreboardComponent.display(span)
            assertEquals(sets, live.completedSets, "live, $count sets")
            assertEquals(tiebreaks, live.completedSetTiebreaks, "live, $count sets")

            val final = ScoreboardComponent.display(span.copy(matchEnded = true))
            // After the match end, the last set is in the games column.
            val shown = if (final.showsPoints) final.completedSets else final.completedSets + (final.player1Games to final.player2Games)
            assertEquals(sets, shown, "final, $count sets")
        }
    }
}
