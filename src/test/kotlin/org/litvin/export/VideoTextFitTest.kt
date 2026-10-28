package org.litvin.export

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.export.scoreboard.TextAnchor
import org.litvin.i18n.AppLanguage
import org.litvin.scoring.PerPlayer
import org.litvin.stats.MatchStat
import org.litvin.stats.StatRow
import org.litvin.stats.StatValue
import org.litvin.stats.StatsCard
import org.litvin.stats.StatsCardContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-11 E3-T19 and E4-T8: all video texts of each language fit in each scoreboard style and on the statistics card. */
class VideoTextFitTest {
    @ParameterizedTest
    @EnumSource(AppLanguage::class)
    fun `the video texts fit`(language: AppLanguage) {
        val problems = VideoTextFit.check(language)

        assertTrue(problems.isEmpty(), "${problems.size} video texts of ${language.code} do not fit:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `a statistics label that runs into the values is a problem`() {
        val label = "A very long name of a statistic that no translation can have, because it is much too long"
        val problems = VideoTextFit.problems("card", card(label = label, name = "Ana"), english = null)

        assertTrue(problems.any { it.text == label && "covers" in it.reason }, problems.toString())
    }

    @Test
    fun `a cut name is a problem`() {
        val problems = VideoTextFit.problems("card", card(label = "Points won", name = "Maximilian Alexander von Featherstonehaugh"), english = null)

        assertTrue(problems.any { it.text.endsWith("…") && it.reason == "is cut" }, problems.toString())
    }

    @Test
    fun `a text that is wider than its box is a problem`() {
        val scene = ScoreboardScene(
            600.0, 40.0,
            listOf(SceneItem.Box(0.0, 0.0, 100.0, 40.0, 0x000000), label(10.0, "A text that is wider than its box", 20.0)),
        )

        val problems = VideoTextFit.problems("board", scene, english = null)

        assertEquals(listOf("goes outside its box"), problems.map { it.reason.substringBefore(" (") })
    }

    @Test
    fun `a smaller font than in English is a problem`() {
        fun scene(size: Double) = ScoreboardScene(400.0, 40.0, listOf(label(10.0, "Text", size)))

        val problems = VideoTextFit.problems("board", scene(16.0), english = scene(20.0))

        assertEquals(listOf("has a smaller font than in English"), problems.map { it.reason.substringBefore(" (") })
    }

    private fun label(x: Double, text: String, size: Double) =
        SceneItem.Label(x, 20.0, text, "Segoe UI", size, 0xFFFFFF, anchor = TextAnchor.MIDDLE_LEFT)

    private fun card(label: String, name: String): ScoreboardScene = StatsCard.pages(
        StatsCardContent(
            names = PerPlayer(name, "Bea"),
            colors = PerPlayer(0x2E7DFF, 0xFF4D4D),
            title = "Match",
            score = "6-4",
            rows = listOf(
                StatRow(MatchStat.POINTS_WON, values = PerPlayer(StatValue("188/188"), StatValue("188/188")), label = label),
            ),
        ),
    ).single()
}
