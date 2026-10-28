package org.litvin.export

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.export.scoreboard.TextAnchor
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.TextFit
import org.litvin.scoring.PerPlayer
import org.litvin.stats.MatchStat
import org.litvin.stats.Momentum
import org.litvin.stats.MomentumPoint
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
    fun `the qps video texts stay in their boxes`() {
        val problems = VideoTextFit.checkPseudo()

        assertTrue(problems.isEmpty(), "${problems.size} qps video texts do not stay in their boxes:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `a text that the fit made smaller is a problem`() {
        val scene = ScoreboardScene(400.0, 40.0, listOf(label(10.0, "Text", 18.0)))

        val problems = VideoTextFit.problems("board", scene, english = null, fits = listOf(TextFit.Fitted("Text", 18.0, 20.0)))

        assertEquals(listOf("needs a smaller font"), problems.map { it.reason.substringBefore(" (") })
    }

    @Test
    fun `a long name on the momentum chart keeps the lead`() {
        val name = "Maximilian Alexander von Featherstonehaugh"
        val chart = StatsCard.pages(
            StatsCardContent(
                names = PerPlayer(name, "Bea"),
                colors = PerPlayer(0x2E7DFF, 0xFF4D4D),
                title = "Match",
                score = "6-4",
                rows = emptyList(),
                momentum = Momentum((1..12).map { MomentumPoint("p$it", number = it, set = 1, winner = 1, difference = it) }, setStarts = emptyList()),
            ),
        ).last()

        val label = chart.items.filterIsInstance<SceneItem.Label>().single { it.text.endsWith(" +12") }
        assertTrue(label.text.startsWith("Maximilian") && "…" in label.text, label.text)
    }

    @Test
    fun `a text that runs into another text is a problem`() {
        val scene = ScoreboardScene(600.0, 40.0, listOf(label(10.0, "A long label text", 20.0), label(80.0, "188/188", 20.0)))

        val problems = VideoTextFit.problems("card", scene, english = null)

        assertTrue(problems.any { it.text == "A long label text" && "covers" in it.reason }, problems.toString())
    }

    @Test
    fun `a statistics label that is wider than the space between the values is cut`() {
        val label = "A very long name of a statistic that no translation can have, because it is much too long".repeat(2)
        val problems = VideoTextFit.problems("card", card(label = label, name = "Ana"), english = null)

        assertTrue(problems.any { it.text.endsWith("…") && it.reason == "is cut" }, problems.toString())
        assertTrue(problems.none { "covers" in it.reason }, problems.toString())
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
