package org.litvin.stats

import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsCardTest {
    @Test
    fun theContentHasOnlySelectedRowsWithAValue() {
        // No server marks: the serve rows have no value.
        val settings = StatsSettingsV1(videoMomentum = false, videoStats = listOf("points_won", "service_points_won", "duration"))

        val content = StatsCard.content(report("11112222"), settings)

        assertEquals(listOf(MatchStat.POINTS_WON, MatchStat.DURATION), content.rows.map { it.stat })
        assertEquals("Match statistics", content.title)
        assertEquals(PerPlayer("Alex", "Sam"), content.names)
        assertEquals(PerPlayer(0x4DA3FF, 0xFF6B6B), content.colors)
    }

    @Test
    fun hiddenPlayerColorsGiveNeutralColors() {
        val score = ScoreV1(scoreboard = ScoreboardSettingsV1(showPlayerColors = false))

        val content = StatsCard.content(report("1111", score), StatsSettingsV1(videoMomentum = false))

        assertEquals(PerPlayer(StatsCard.NEUTRAL_P1, StatsCard.NEUTRAL_P2), content.colors)
    }

    @Test
    fun aDarkPlayerColorGetsLighterOnTheCard() {
        val score = ScoreV1(player1ColorHex = "#000000", player2ColorHex = "#4DA3FF")

        val content = StatsCard.content(report("1111", score), StatsSettingsV1(videoMomentum = false))

        // Black becomes a gray that shows on the dark panel. A bright color does not change.
        assertTrue(StatsCard.luminance(content.colors.p1) >= 0.2, Integer.toHexString(content.colors.p1))
        assertTrue(StatsCard.luminance(content.colors.p1) < 0.22, Integer.toHexString(content.colors.p1))
        assertEquals(0x4DA3FF, content.colors.p2)
    }

    @Test
    fun aDarkColorKeepsItsHue() {
        val lighter = StatsCard.onPanel(0x0D1B6E)

        val red = (lighter shr 16) and 0xFF
        val blue = lighter and 0xFF
        assertTrue(blue > red, Integer.toHexString(lighter))
        assertTrue(StatsCard.luminance(lighter) >= 0.2, Integer.toHexString(lighter))
    }

    @Test
    fun theCardTransparencySetsThePanelOpacity() {
        val content = StatsCard.content(report("1111"), StatsSettingsV1(cardTransparencyPercent = 40, videoMomentum = false))

        val panel = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Box>()[1]

        assertEquals(0.6, panel.opacity, 1e-9)
    }

    @Test
    fun theCardTransparencyStaysInItsRange() {
        assertEquals(StatsSettingsV1.MAX_CARD_TRANSPARENCY_PERCENT, StatsSettingsV1(cardTransparencyPercent = 100).normalized().cardTransparencyPercent)
        assertEquals(0, StatsSettingsV1(cardTransparencyPercent = -5).normalized().cardTransparencyPercent)
    }

    @Test
    fun aSetScopeHasTheSetTitleAndScore() {
        val rules = MatchRulesV1(gamesPerSet = 1, setTiebreak = false)

        val content = StatsCard.content(report("11111111" + "2222", ScoreV1(rules = rules)), StatsSettingsV1(videoMomentum = false), scope = 2)

        assertEquals("Set 2 statistics", content.title)
        assertEquals("0-1", content.score)
    }

    @Test
    fun eachPageHasAtMostSevenRows() {
        val settings = StatsSettingsV1(videoMomentum = false, videoStats = MatchStat.entries.map { it.key })
        val content = StatsCard.content(report("1111" + "2222"), settings)

        val pages = StatsCard.pages(content)

        assertTrue(content.rows.size > StatsCard.ROWS_PER_PAGE)
        assertEquals((content.rows.size + StatsCard.ROWS_PER_PAGE - 1) / StatsCard.ROWS_PER_PAGE, pages.size)
        assertTrue(pages.first().labels().contains("1 / ${pages.size}"))
    }

    @Test
    fun thePagesShareTheRowsEqually() {
        val keys = listOf("points_won", "games_won", "sets_won", "deuce_games_won", "longest_point_run", "largest_point_lead", "duration", "average_point")
        val content = StatsCard.content(report("1111" + "2222"), StatsSettingsV1(videoMomentum = false, videoStats = keys))

        val rowsOnPages = StatsCard.pages(content).map { page -> page.labels().count { it in keys.map { key -> MatchStat.ofKey(key)!!.label } } }

        assertEquals(listOf(4, 4), rowsOnPages)
    }

    @Test
    fun aGroupStaysOnOnePageWhenThePageCountDoesNotChange() {
        // 3 overview rows, 4 serve rows and 1 pressure row. An equal split (4 + 4) breaks the serve group.
        val rows = rowsOf(StatGroup.OVERVIEW, 3) + rowsOf(StatGroup.SERVE, 4) + rowsOf(StatGroup.PRESSURE, 1)

        val pages = StatsCard.splitRows(rows)

        assertEquals(listOf(3, 5), pages.map { it.size })
    }

    @Test
    fun theRowsShareEquallyWhenTheGroupsDoNotFitOnThePages() {
        // 4 + 5 + 3 rows. Each split between the groups gives a page with more than 7 rows.
        val rows = rowsOf(StatGroup.SERVE, 4) + rowsOf(StatGroup.PRESSURE, 5) + rowsOf(StatGroup.POINT_LENGTH, 3)

        val pages = StatsCard.splitRows(rows)

        assertEquals(listOf(6, 6), pages.map { it.size })
    }

    @Test
    fun aCompactPageHasMoreRows() {
        val rows = rowsOf(StatGroup.OVERVIEW, 3) + rowsOf(StatGroup.SERVE, 9)

        assertEquals(listOf(6, 6), StatsCard.splitRows(rows).map { it.size })
        assertEquals(listOf(12), StatsCard.splitRows(rows, StatsCard.RowLayout.COMPACT).map { it.size })
    }

    @Test
    fun groupTitlesUseTheSpaceOfSomeRows() {
        // 7 rows fit on one page without titles. With two titles, the serve group goes to a second page.
        val rows = rowsOf(StatGroup.OVERVIEW, 3) + rowsOf(StatGroup.SERVE, 4)

        assertEquals(listOf(7), StatsCard.splitRows(rows).map { it.size })
        assertEquals(listOf(3, 4), StatsCard.splitRows(rows, groupTitles = true).map { it.size })
    }

    @Test
    fun aGroupThatGoesOnTheNextPageShowsItsTitleAgain() {
        val rows = rowsOf(StatGroup.SERVE, 9)

        val pages = StatsCard.splitRows(rows, groupTitles = true)

        assertEquals(listOf(5, 4), pages.map { it.size })
        assertTrue(pages.all { StatsCard.RowLayout.NORMAL.height(it, groupTitles = true) <= StatsCard.RowLayout.NORMAL.bodyHeight })
    }

    @Test
    fun theCompactCardShowsAllRowsOnFewerPages() {
        val all = StatsSettingsV1(videoMomentum = false, videoStats = MatchStat.entries.map { it.key })
        val normal = StatsCard.content(report("1111" + "2222"), all)
        val compact = StatsCard.content(report("1111" + "2222"), all.copy(compactCard = true))

        val normalPages = StatsCard.pages(normal)
        val compactPages = StatsCard.pages(compact)

        assertTrue(compactPages.size < normalPages.size, "${compactPages.size} < ${normalPages.size}")
        val labels = compactPages.flatMap { it.labels() }
        assertTrue(compact.rows.all { it.label in labels })
        // The panel stays in the frame.
        val panel = compactPages.first().items.filterIsInstance<SceneItem.Box>()[1]
        assertTrue(panel.y >= 0 && panel.y + panel.height <= StatsCard.HEIGHT)
    }

    @Test
    fun theCardShowsTheGroupTitlesWhenTheSettingIsOn() {
        val keys = listOf("points_won", "games_won", "duration")
        val settings = StatsSettingsV1(videoMomentum = false, videoStats = keys)

        val without = StatsCard.pages(StatsCard.content(report("11112211"), settings)).single().labels()
        val with = StatsCard.pages(StatsCard.content(report("11112211"), settings.copy(cardGroupTitles = true))).single().labels()

        assertTrue("OVERVIEW" !in without)
        assertTrue(with.containsAll(listOf("OVERVIEW", "TIME")), with.toString())
    }

    private fun rowsOf(group: StatGroup, count: Int): List<StatRow> =
        MatchStat.entries.filter { it.group == group }.take(count).map { StatRow(it) }.also { assertEquals(count, it.size) }

    @Test
    fun noRowsGiveNoPages() {
        val content = StatsCard.content(report("1111"), StatsSettingsV1(videoMomentum = false, videoStats = emptyList()))

        assertEquals(emptyList(), StatsCard.pages(content))
    }

    @Test
    fun theSceneCoversTheFrame() {
        val content = StatsCard.content(report("1111"), StatsSettingsV1(videoMomentum = false))

        val scene = StatsCard.pages(content, frameWidth = 1440.0).single()
        val dim = scene.items.first() as SceneItem.Box

        assertEquals(1440.0, scene.width)
        assertEquals(StatsCard.HEIGHT, scene.height)
        assertEquals(listOf(0.0, 0.0, 1440.0, StatsCard.HEIGHT), listOf(dim.x, dim.y, dim.width, dim.height))
    }

    @Test
    fun theBarSplitsByTheValues() {
        // Player 1 wins 6 of 8 points.
        val content = StatsCard.content(report("11112211"), StatsSettingsV1(videoMomentum = false, videoStats = listOf("points_won")))

        val bars = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Box>()
            .filter { it.rgb == 0x4DA3FF || it.rgb == 0xFF6B6B }

        assertEquals(2, bars.size)
        assertEquals(3.0, bars[0].width / bars[1].width, 0.001)
    }

    @Test
    fun thePointsAndGamesRowsShowTheShareOfEachPlayer() {
        // Player 1 wins 6 of 8 points.
        val settings = StatsSettingsV1(videoMomentum = false, videoStats = listOf("points_won", "games_won", "duration"))
        val content = StatsCard.content(report("11112211"), settings)

        val labels = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Label>()

        val p1Value = labels.single { it.text == "6" }
        val p1Share = labels.single { it.text == content.rows[0].values!!.p1.detail }
        val p2Share = labels.single { it.text == content.rows[0].values!!.p2.detail }
        assertTrue(p1Share.x > p1Value.x, "The share of player 1 is after the value")
        assertTrue(p1Share.size < p1Value.size)
        assertTrue(p2Share.x < labels.single { it.text == "2" }.x, "The share of player 2 is before the value")
        // The games row has a detail for each player.
        val games = content.rows.single { it.stat == MatchStat.GAMES_WON }.values!!
        assertTrue(labels.any { it.text == games.p1.detail })
    }

    @Test
    fun aLongNameGetsSmallerAndShorter() {
        val name = "Maximilian Alexander von Longname-Featherstonehaugh"
        val content = StatsCard.content(report("1111", ScoreV1(player1Name = name)), StatsSettingsV1(videoMomentum = false))

        val label = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Label>().first { it.x < 500 }

        assertTrue(label.text.endsWith("…"))
        assertTrue(ScoreboardFonts.textWidth(label.text, label.font, label.bold, label.size) <= 1240 * 0.3)
    }

    @Test
    fun theMomentumChartIsTheLastPage() {
        val content = StatsCard.content(report("11" + "2222"), StatsSettingsV1(videoMomentum = true))

        val pages = StatsCard.pages(content)

        assertEquals(2, pages.size)
        val chart = pages.last()
        assertTrue(chart.labels().containsAll(listOf("Point difference", "Alex +2", "Sam +2", "2 / 2")), chart.labels().toString())
        // One area for each player, and the line.
        assertEquals(listOf(content.colors.p1, content.colors.p2), chart.items.filterIsInstance<SceneItem.Polygon>().map { it.rgb })
        assertEquals(7, chart.items.filterIsInstance<SceneItem.Polyline>().single().points.size)
    }

    @Test
    fun aCardWithOnlyTheChartHasOnePageWithTheHeightOfFourRows() {
        val content = StatsCard.content(report("1122"), StatsSettingsV1(videoStats = emptyList(), videoMomentum = true))

        val page = StatsCard.pages(content).single()

        val panel = page.items.filterIsInstance<SceneItem.Box>()[1]
        assertEquals(170.0 + 4 * 104.0 + 70.0, panel.height)
    }

    @Test
    fun theChartMarksTheStartOfEachSet() {
        // Sets to 1 game without a tiebreak: set 2 starts at point 9.
        val rules = MatchRulesV1(gamesPerSet = 1, setTiebreak = false)
        val content = StatsCard.content(report("11111111" + "2222", ScoreV1(player1Name = "Alex", player2Name = "Sam", rules = rules)), StatsSettingsV1(videoStats = emptyList(), videoMomentum = true))

        val chart = StatsCard.pages(content).single()

        assertTrue("Set 2" in chart.labels(), chart.labels().toString())
    }

    @Test
    fun eachStyleShowsAllRowsInBothLayouts() {
        val keys = listOf("points_won", "games_won", "deuce_games_won", "longest_point_run", "duration")
        for (style in CardStyle.entries) for (compact in listOf(false, true)) for (titles in listOf(false, true)) for (light in listOf(false, true)) {
            val settings = StatsSettingsV1(videoMomentum = true, videoStats = keys, cardStyle = style.key, compactCard = compact,
                cardGroupTitles = titles, lightCard = light)
            val content = StatsCard.content(report("11112211" + "2121"), settings)

            val pages = StatsCard.pages(content)
            val labels = pages.flatMap { it.labels() }.map { it.lowercase() }

            val case = "$style compact=$compact titles=$titles light=$light"
            assertTrue(content.rows.all { it.label.lowercase() in labels }, "$case: $labels")
            assertTrue("point difference" in labels, case)
            for (page in pages) {
                val panel = page.items.filterIsInstance<SceneItem.Box>()[1]
                assertTrue(panel.y >= 0 && panel.y + panel.height <= StatsCard.HEIGHT, case)
            }
        }
    }

    @Test
    fun anUnknownStyleKeyGivesTheClassicStyle() {
        assertEquals(CardStyle.CLASSIC, CardStyle.ofKey("future-style"))
        assertEquals(CardStyle.SHEET, CardStyle.ofKey("sheet"))
    }

    @Test
    fun theLightCardHasALightPanelAndDarkerPlayerColors() {
        val score = ScoreV1(player1Name = "Alex", player2Name = "Sam", player1ColorHex = "#F2F2F2")
        val content = StatsCard.content(report("1122", score), StatsSettingsV1(videoMomentum = false, lightCard = true))

        val page = StatsCard.pages(content).single()

        val panel = page.items.filterIsInstance<SceneItem.Box>()[1]
        assertTrue(StatsCard.luminance(panel.rgb) > 0.8, Integer.toHexString(panel.rgb))
        assertTrue(StatsCard.luminance(content.cardColors.p1) <= 0.2, Integer.toHexString(content.cardColors.p1))
        assertEquals(content.lightColors, content.cardColors)
    }

    @Test
    fun theMirrorBarsOfAPercentageUseAScaleOf100() {
        val row = StatRow(MatchStat.SERVICE_POINTS_WON, values = PerPlayer(StatValue("50%"), StatValue("25%")),
            bar = PerPlayer(50.0, 25.0), barMax = 100.0)
        val content = StatsCardContent(PerPlayer("Alex", "Sam"), PerPlayer(0x4DA3FF, 0xFF6B6B), "Match statistics", "6-4",
            listOf(row), style = CardStyle.MIRROR)

        val boxes = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Box>()

        val p1 = boxes.single { it.rgb == 0x4DA3FF }
        val p2 = boxes.single { it.rgb == 0xFF6B6B }
        assertEquals(2.0, p1.width / p2.width, 0.001)
        // Each bar starts at the center of the card.
        assertEquals(StatsCard.WIDTH_16_9 / 2, p1.x + p1.width + 4.0, 0.001)
        assertEquals(StatsCard.WIDTH_16_9 / 2, p2.x - 4.0, 0.001)
        // 50% is half of the full length.
        val track = boxes.filter { it.rgb == 0xFFFFFF && it.width > p1.width && it.height == p1.height }
        assertEquals(p1.width * 2, track.first().width, 0.001)
    }

    @Test
    fun theStripesShowTheBetterValueInThePlayerColor() {
        val won = StatRow(MatchStat.WINNERS, values = PerPlayer(StatValue("9"), StatValue("4")), bar = PerPlayer(9.0, 4.0))
        val errors = StatRow(MatchStat.UNFORCED_ERRORS, values = PerPlayer(StatValue("12"), StatValue("5")), bar = PerPlayer(12.0, 5.0))
        val content = StatsCardContent(PerPlayer("Alex", "Sam"), PerPlayer(0x4DA3FF, 0xFF6B6B), "Match statistics", "6-4",
            listOf(won, errors), style = CardStyle.STRIPES)

        val labels = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Label>()

        assertEquals(0x4DA3FF, labels.single { it.text == "9" }.rgb)
        assertEquals(0xFFFFFF, labels.single { it.text == "4" }.rgb)
        // Fewer unforced errors is better.
        assertEquals(0xFFFFFF, labels.single { it.text == "12" }.rgb)
        assertEquals(0xFF6B6B, labels.single { it.text == "5" }.rgb)
    }

    private fun org.litvin.export.scoreboard.ScoreboardScene.labels(): List<String> =
        items.filterIsInstance<SceneItem.Label>().map { it.text }

    private fun report(winners: String, score: ScoreV1 = ScoreV1(player1Name = "Alex", player2Name = "Sam")): StatsReport {
        val points = (1..winners.length).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
        val outcomes = points.zip(winners.toList()).associate { (point, c) -> point.id to if (c == '1') Outcome.P1 else Outcome.P2 }
        return StatsReport.build(EdlV1(points = points), score.copy(outcomes = outcomes))
    }
}
