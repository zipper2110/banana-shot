package org.litvin.ui.tabs.points.ui

import org.litvin.i18n.tr
import org.litvin.scoring.Ending
import org.litvin.scoring.EndingChoice
import org.litvin.ui.commons.AppShortcut
import org.litvin.ui.commons.AppShortcuts

/**
 * The ending buttons of advanced mode (B-68, "Ending buttons"). Each button has the color strip of the player who gets
 * the point, so that the two groups show the two players. The label names the player who made the ending, for example
 * "Ben unforced error". A narrow point bar shows the short labels ("Unforced"). The tooltips give the decision rules.
 */
internal class EndingButtons(onEnding: (EndingChoice) -> Unit) {
    /** The buttons in the order of the point bar: the left group, then the right group. The winners are the outer buttons. */
    val buttons: Map<EndingChoice, OutcomeButton> = ORDER.associateWith { choice ->
        OutcomeButton(choice.winner, keyOf(choice).display).apply {
            name = "points-ending-${choice.name.lowercase().replace('_', '-')}"
            addActionListener { onEnding(choice) }
        }
    }

    private var players = ScorePlayers()

    fun setPlayers(players: ScorePlayers) {
        this.players = players
        buttons.forEach { (choice, button) ->
            button.setPlayer(fullLabel(choice), players.color(choice.winner))
            button.toolTipText = tooltip(choice)
        }
    }

    /** The full label, with the name of the player who made the ending. */
    fun fullLabel(choice: EndingChoice): String {
        val name = players.name(choice.player)
        return when (choice.ending) {
            Ending.ACE -> tr("points.ending.ace", "name" to name)
            Ending.WINNER -> tr("points.ending.winner", "name" to name)
            Ending.FORCED_ERROR -> tr("points.ending.forcedError", "name" to name)
            else -> tr("points.ending.unforcedError", "name" to name)
        }
    }

    /** The short label of a narrow point bar. The color strip shows the player who gets the point. */
    fun shortLabel(choice: EndingChoice): String = when (choice.ending) {
        Ending.ACE -> tr("points.ending.short.ace")
        Ending.WINNER -> tr("points.ending.short.winner")
        Ending.FORCED_ERROR -> tr("points.ending.short.forcedError")
        else -> tr("points.ending.short.unforcedError")
    }

    private fun tooltip(choice: EndingChoice): String {
        val name = players.name(choice.player)
        val rule = when (choice.ending) {
            Ending.ACE -> tr("points.ending.ace.tooltip", "name" to name)
            Ending.WINNER -> tr("points.ending.winner.tooltip", "name" to name)
            Ending.FORCED_ERROR -> tr("points.ending.forcedError.tooltip", "name" to name)
            else -> tr("points.ending.unforcedError.tooltip", "name" to name)
        }
        val key = keyOf(choice).display
        val won = tr("points.outcome.won.tooltip", "name" to players.name(choice.winner))
        val winnerOnly = tr("points.ending.winnerOnly.tooltip", "name1" to players.p1Name, "name2" to players.p2Name)
        return "<html>$key — $rule<br>$won.<br>${tr("points.outcome.pending.tooltip")}<br>$winnerOnly</html>"
    }

    companion object {
        /** The order of the buttons in the point bar. The right group mirrors the left group. */
        val ORDER = listOf(
            EndingChoice.P1_WINNER, EndingChoice.P2_FORCED_ERROR, EndingChoice.P2_UNFORCED_ERROR, EndingChoice.P1_ACE,
            EndingChoice.P2_ACE, EndingChoice.P1_UNFORCED_ERROR, EndingChoice.P1_FORCED_ERROR, EndingChoice.P2_WINNER,
        )

        /** The key of each ending button. */
        fun keyOf(choice: EndingChoice): AppShortcut = when (choice) {
            EndingChoice.P1_WINNER -> AppShortcuts.ENDING_P1_WINNER
            EndingChoice.P2_FORCED_ERROR -> AppShortcuts.ENDING_P2_FORCED_ERROR
            EndingChoice.P2_UNFORCED_ERROR -> AppShortcuts.ENDING_P2_UNFORCED_ERROR
            EndingChoice.P1_ACE -> AppShortcuts.ENDING_P1_ACE
            EndingChoice.P2_ACE -> AppShortcuts.ENDING_P2_ACE
            EndingChoice.P1_UNFORCED_ERROR -> AppShortcuts.ENDING_P1_UNFORCED_ERROR
            EndingChoice.P1_FORCED_ERROR -> AppShortcuts.ENDING_P1_FORCED_ERROR
            EndingChoice.P2_WINNER -> AppShortcuts.ENDING_P2_WINNER
        }
    }
}
