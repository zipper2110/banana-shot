package org.litvin.ui.tabs.points.ui

import org.junit.jupiter.api.Test
import org.litvin.scoring.ScoreOverrideV1
import org.litvin.scoring.ScorePairV1
import org.litvin.scoring.SetGamesV1
import java.awt.Color
import java.awt.Container
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverrideScoreDialogTest {
    private val computed = ComputedScore(
        sets = listOf(SetGamesV1(7, 6, 7, 4)),
        games = ScorePairV1(2, 1),
        points = ScorePairV1(2, 1),
    )

    private fun dialog(
        current: ScoreOverrideV1 = ScoreOverrideV1(),
        tiebreakOf: (ScoreOverrideV1) -> Boolean = { false },
        computed: ComputedScore = this.computed,
    ) =
        OverrideScoreDialog(
            null,
            OverrideScoreInput(
                pointNumber = 12,
                computed = computed,
                current = current,
                p1Name = "Vika",
                p2Name = "Dima",
                p1Color = Color.BLUE,
                p2Color = Color.GREEN,
                tiebreakOf = tiebreakOf,
            ),
        )

    @Test
    fun withoutChangesTheOverrideIsEmpty() {
        SwingUtilities.invokeAndWait {
            val dialog = dialog()
            assertTrue(dialog.buildOverride().isEmpty())
            assertFalse(dialog.gamesPart.typed)
            dialog.dispose()
        }
    }

    @Test
    fun aChangedPartIsTypedAndTheOtherPartsStayAuto() {
        SwingUtilities.invokeAndWait {
            val dialog = dialog()
            dialog.gamesFields.first.text = "5"

            val override = dialog.buildOverride()
            assertEquals(ScorePairV1(5, 1), override.games)
            assertEquals(null, override.sets)
            assertEquals(null, override.points)
            dialog.dispose()
        }
    }

    @Test
    fun aSetKeepsItsTiebreakWhileItsGamesStayTheSame() {
        SwingUtilities.invokeAndWait {
            val dialog = dialog()
            dialog.setFields[0].first.text = "7"
            assertEquals(listOf(SetGamesV1(7, 6, 7, 4)), dialog.buildOverride().sets)

            dialog.setFields[0].second.text = "5"
            assertEquals(listOf(SetGamesV1(7, 5)), dialog.buildOverride().sets)
            dialog.dispose()
        }
    }

    @Test
    fun theDialogShowsTheCurrentOverride() {
        SwingUtilities.invokeAndWait {
            val dialog = dialog(current = ScoreOverrideV1(games = ScorePairV1(4, 4)))
            assertTrue(dialog.gamesPart.typed)
            assertEquals("4", dialog.gamesFields.first.text)
            assertEquals(ScoreOverrideV1(games = ScorePairV1(4, 4)), dialog.buildOverride())
            dialog.dispose()
        }
    }

    @Test
    fun gamesThatStartATiebreakGiveNumberPoints() {
        SwingUtilities.invokeAndWait {
            val dialog = dialog(tiebreakOf = { it.games == ScorePairV1(6, 6) })
            assertFalse(dialog.tiebreakPoints)
            dialog.gamesFields.first.text = "6"
            dialog.gamesFields.second.text = "6"
            assertTrue(dialog.tiebreakPoints)
            dialog.dispose()
        }
    }

    @Test
    fun theTeamNamesStayVisibleWithFiveSets() {
        SwingUtilities.invokeAndWait {
            val fiveSets = computed.copy(sets = List(5) { SetGamesV1(6, 4) })
            val dialog = dialog(computed = fiveSets)
            val names = sideLabels(dialog)
            assertEquals(2, names.size)
            names.forEach { assertTrue(it.width >= 70, "A team name has a width of ${it.width} px") }
            dialog.dispose()
        }
    }

    private fun sideLabels(root: Container): List<java.awt.Component> = root.components.flatMap { child ->
        val own = if (child.javaClass.simpleName == "SideLabel") listOf(child) else emptyList()
        own + if (child is Container) sideLabels(child) else emptyList()
    }
}
