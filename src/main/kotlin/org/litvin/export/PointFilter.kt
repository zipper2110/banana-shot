package org.litvin.export

import org.litvin.scoring.Ending
import org.litvin.scoring.ErrorKind

/** An ending that the point filter can select (B-69). A double fault is a serve fault, so [Ending] does not have it. */
enum class FilterEnding {
    ACE,
    WINNER,
    FORCED_ERROR,
    UNFORCED_ERROR,
    DOUBLE_FAULT,
    ;

    companion object {
        /** The filter ending of a point clip with [ending], or null for a serve fault, a let, or no ending. */
        fun of(ending: Ending?): FilterEnding? = when (ending) {
            Ending.ACE -> ACE
            Ending.WINNER -> WINNER
            Ending.FORCED_ERROR -> FORCED_ERROR
            Ending.UNFORCED_ERROR -> UNFORCED_ERROR
            Ending.SERVE_FAULT, Ending.LET, null -> null
        }
    }
}

/**
 * The point filter of the Export tab (B-69, in docs/backlog-paid.md). It uses the details of advanced scoring.
 * - [players] are the players (1 or 2) who made the ending: the winner of an ace or a winner, and the other player
 *   for an error or a double fault.
 * - In one group, a point matches when it has one of the selected values. A point must match each group that has a
 *   selection. A group with no selection keeps all points.
 */
data class PointFilter(
    val players: Set<Int> = emptySet(),
    val endings: Set<FilterEnding> = emptySet(),
    val errorKinds: Set<ErrorKind> = emptySet(),
) {
    /** True when a group has a selection. A filter with no selection keeps all points. */
    val isActive: Boolean get() = players.isNotEmpty() || endings.isNotEmpty() || errorKinds.isNotEmpty()

    /**
     * True when a point with [ending], made by [player], with [errorKind], matches. With a selection, a point with no
     * ending or no player does not match.
     */
    fun matches(ending: FilterEnding?, player: Int?, errorKind: ErrorKind?): Boolean {
        if (!isActive) return true
        if (ending == null || player == null) return false
        if (players.isNotEmpty() && player !in players) return false
        if (endings.isNotEmpty() && ending !in endings) return false
        if (errorKinds.isNotEmpty() && errorKind !in errorKinds) return false
        return true
    }

    companion object {
        /** No selection: the filter keeps all points. */
        val NONE = PointFilter()
    }
}
