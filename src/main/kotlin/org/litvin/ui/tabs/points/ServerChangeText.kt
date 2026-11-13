package org.litvin.ui.tabs.points

import org.litvin.i18n.tr
import org.litvin.scoring.Outcome
import org.litvin.scoring.ServerChangePreview
import org.litvin.ui.tabs.points.ui.ScorePlayers

/**
 * The message of the confirmation before a server change (B-68, "The server"): the points that get a new winner, for
 * example "Point 14: ace, Ana → Ben", and the games and sets that then go to the other player.
 */
internal object ServerChangeText {
    /** The most lines of each list. A longer list ends with a line that gives the number of the other changes. */
    const val MAX_LINES = 6

    fun message(preview: ServerChangePreview, players: ScorePlayers): String {
        fun name(outcome: Outcome) = when (outcome) {
            Outcome.P1 -> players.p1Name
            Outcome.P2 -> players.p2Name
            Outcome.NONE -> tr("points.outcome.noWinner")
        }
        fun name(player: Int?) = player?.let(players::name) ?: tr("points.outcome.noWinner")
        val winners = preview.winners.map { change ->
            val args = arrayOf("number" to change.number, "from" to name(change.from), "to" to name(change.to))
            if (change.doubleFault) tr("points.serverChange.doubleFault", *args) else tr("points.serverChange.ace", *args)
        }
        val scores = preview.scoreChanges.map { change ->
            if (change.game == null) {
                tr("points.serverChange.set", "set" to change.set, "from" to name(change.from), "to" to name(change.to))
            } else {
                tr("points.serverChange.game", "set" to change.set, "game" to change.game, "from" to name(change.from), "to" to name(change.to))
            }
        }
        return buildString {
            append(tr("points.serverChange.message"))
            append("\n\n").append(limited(winners).joinToString("\n"))
            if (scores.isNotEmpty()) {
                append("\n\n").append(tr("points.serverChange.score"))
                append("\n").append(limited(scores).joinToString("\n"))
            }
        }
    }

    private fun limited(lines: List<String>): List<String> {
        if (lines.size <= MAX_LINES) return lines
        val shown = MAX_LINES - 1
        return lines.take(shown) + tr("points.serverChange.more", "count" to lines.size - shown)
    }
}
