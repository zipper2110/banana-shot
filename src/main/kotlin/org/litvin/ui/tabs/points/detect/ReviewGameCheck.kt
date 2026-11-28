package org.litvin.ui.tabs.points.detect

import org.litvin.points.PointV1

/**
 * The game check of the review (A57 step 3, option A). The score gives the server of each point from the server marks
 * and the game ends. The detection sees the server of each rally. One missed point (or one false point) moves the
 * game ends of the score, so after it the server of the score and the detected server do not agree on a run of points.
 *
 * A game of the score does not agree when [MIN_RUN] points of the game in a row (of the points with a server in the
 * score and in the detection) do not agree. One wrong detected server is thus no clue. Each run of games that do not
 * agree is one [Item]: the point is most probably missing before the first point that does not agree, in that game
 * or in the game before it. [Item.gapIndex] is the point after the longest gap there: the first place to look.
 */
internal object ReviewGameCheck {
    const val MIN_RUN = 2

    /** A game of the score: the indexes of its points in the list, and its number in the match (1 is the first game). */
    data class Game(val number: Int, val first: Int, val last: Int)

    /**
     * Games [firstGame] to [lastGame] (numbers) do not agree. [disagree]: the count of points that do not agree in
     * them. [firstDisagree]: the index of the first point that does not agree. [gapIndex]: the index of the point
     * after the longest gap from the start of the game before [firstGame] to [firstDisagree]. [key]: the id of the
     * first point that does not agree; it names the item.
     */
    data class Item(
        val firstGame: Int,
        val lastGame: Int,
        val disagree: Int,
        val firstDisagree: Int,
        val gapIndex: Int,
        val key: String,
    )

    /** The games of [points] (in time order). [gameEnds] are the ids of the points that end a game. */
    fun games(points: List<PointV1>, gameEnds: Set<String>): List<Game> {
        val games = ArrayList<Game>()
        var first = 0
        points.forEachIndexed { index, point ->
            if (point.id in gameEnds || index == points.lastIndex) {
                games += Game(games.size + 1, first, index)
                first = index + 1
            }
        }
        return games
    }

    /**
     * The items of the check. [server] is the server (1 or 2) of a point in the score, [detected] the detected
     * server; null when it is not known. With no server in the score, the check has no items.
     */
    fun items(
        points: List<PointV1>,
        gameEnds: Set<String>,
        server: (String) -> Int?,
        detected: (String) -> Int?,
    ): List<Item> {
        // True: the servers agree; false: they do not; null: one of them is not known.
        val agree = points.map { point -> server(point.id)?.let { s -> detected(point.id)?.let { it == s } } }
        val games = games(points, gameEnds)
        val bad = games.filter { game -> hasRun(agree.subList(game.first, game.last + 1)) }.map { it.number }.toSet()
        val gaps = ReviewGaps.gaps(points)
        val items = ArrayList<Item>()
        var index = 0
        while (index < games.size) {
            if (games[index].number !in bad) {
                index++
                continue
            }
            var end = index
            while (end + 1 < games.size && games[end + 1].number in bad) end++
            val run = games.subList(index, end + 1)
            val count = run.sumOf { game -> (game.first..game.last).count { agree[it] == false } }
            val firstDisagree = (run.first().first..run.first().last).first { agree[it] == false }
            val from = games.getOrNull(index - 1)?.first ?: run.first().first
            // The gap before the first point of the list does not exist. The first point that does not agree is a
            // place to look when the range has no gap.
            val gapIndex = ((from + 1).coerceAtLeast(1)..firstDisagree).maxByOrNull { gaps[it] ?: -1L } ?: firstDisagree
            items += Item(run.first().number, run.last().number, count, firstDisagree, gapIndex, points[firstDisagree].id)
            index = end + 1
        }
        return items
    }

    /** True when [agree] has [MIN_RUN] values false in a row, with no true between them (null values are skipped). */
    private fun hasRun(agree: List<Boolean?>): Boolean {
        var run = 0
        agree.forEach { value ->
            when (value) {
                false -> if (++run >= MIN_RUN) return true
                true -> run = 0
                null -> Unit
            }
        }
        return false
    }
}
