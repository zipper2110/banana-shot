package org.litvin.ui.tabs.stats

import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.stats.MatchStat
import org.litvin.stats.StatRows
import org.litvin.stats.StatsIO
import org.litvin.stats.StatsSettingsV1
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.UserDialogService
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import javax.swing.AbstractButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwingStatsPanelTest {
    private val projectDir: File = Files.createTempDirectory("stats-panel").toFile()

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun showsTheStatisticsOfTheScoredPoints() {
        writeProject("1111" + "2222" + "1111")

        onEdt {
            val panel = openPanel()

            assertEquals("Alex", panel.label("stats-player-1").text)
            assertEquals("Sam", panel.label("stats-player-2").text)
            assertEquals("8", panel.label("stats-value-points_won-p1-text").text)
            assertEquals("4", panel.label("stats-value-points_won-p2-text").text)
            assertFalse(panel.find<CoverageNote>("stats-coverage")!!.isVisible)
        }
    }

    @Test
    fun withoutScoredPointsTheTabOffersTheScoringTab() {
        writeProject("")
        var opened = 0

        onEdt {
            val panel = openPanel(onOpenScoring = { opened++ })

            assertEquals("No scored points", panel.label("stats-empty").text)
            panel.find<AbstractButton>("stats-open-scoring")!!.doClick()
            assertEquals(1, opened)
        }
    }

    @Test
    fun theInVideoCheckboxSavesTheSelection() {
        writeProject("1111")

        onEdt {
            val panel = openPanel()
            val check = panel.find<JCheckBox>("stats-in-video-${MatchStat.GAMES_WON.key}")!!
            assertFalse(check.isSelected)

            check.doClick()
        }

        assertTrue(MatchStat.GAMES_WON.key in StatsIO.readForProjectDir(projectDir.path).videoStats)
    }

    @Test
    fun thePreviewFollowsTheInVideoSelection() {
        writeProject("1111")

        onEdt {
            val panel = openPanel()
            val preview = panel.find<StatsCardPreview>("stats-preview")!!
            // The default rows fit on one page. The card has no momentum chart by default.
            assertEquals(1, preview.pages.size)
            // Without a server mark, the serve rows have no value. No point lasts 13 s or more.
            assertEquals("Match statistics · 3 rows", panel.label("stats-card-summary").text)
            assertFalse(panel.find<AbstractButton>("stats-preview-next")!!.isVisible)

            MatchStat.defaultVideoKeys.forEach { key -> panel.find<JCheckBox>("stats-in-video-$key")!!.doClick() }
            assertEquals(0, preview.pages.size)

            panel.find<JCheckBox>("stats-in-video-momentum")!!.doClick()
            assertEquals(1, preview.pages.size)
            assertEquals("Match statistics · 0 rows and the momentum chart", panel.label("stats-card-summary").text)
        }

        assertTrue(StatsIO.readForProjectDir(projectDir.path).videoMomentum)
    }

    @Test
    fun eachGroupTitleCountsItsRowsInTheVideo() {
        writeProject("1111")

        onEdt {
            val panel = openPanel()
            val overview = panel.find<GroupHeaderRow>("stats-group-overview")!!
            assertEquals("1 of 3", overview.countText)
            assertEquals("0 of 3", panel.find<GroupHeaderRow>("stats-group-momentum")!!.countText)

            panel.find<JCheckBox>("stats-in-video-${MatchStat.GAMES_WON.key}")!!.doClick()

            assertEquals("2 of 3", overview.countText)
        }
    }

    @Test
    fun theValueOfTheLeadingPlayerIsBrighter() {
        writeProject("1111" + "2222" + "1111")

        onEdt {
            val panel = openPanel()

            assertEquals(StatsColors.LEAD, panel.label("stats-value-points_won-p1-text").foreground)
            assertEquals(UiKit.FG_2, panel.label("stats-value-points_won-p2-text").foreground)
        }
    }

    @Test
    fun aRowWithoutAValueShowsTheReasonUnderItsLabel() {
        writeProject("1111")

        onEdt {
            val panel = openPanel()
            val reason = panel.find<WrapText>("stats-reason-${MatchStat.SERVICE_POINTS_WON.key}")!!

            assertTrue(reason.isVisible)
            assertEquals(StatRows.NEEDS_SERVER, reason.text)
            assertEquals("—", panel.label("stats-value-${MatchStat.SERVICE_POINTS_WON.key}-p1-text").text)
            assertFalse(panel.find<WrapText>("stats-reason-${MatchStat.POINTS_WON.key}")!!.isVisible)
        }
    }

    @Test
    fun theMatchDurationHasItsExplanationInTheTooltip() {
        writeProject("1111")

        onEdt {
            val label = openPanel().label("stats-label-${MatchStat.DURATION.key}")

            assertEquals("Match duration", label.text)
            assertTrue(label.toolTipText.startsWith("The time from the start of the first point with a winner"))
        }
    }

    @Test
    fun theScopeSelectorShowsTheStatisticsOfOneSet() {
        // Sets to 1 game without a tiebreak: 2–0 for player 1, then player 2 leads 1–0 in set 2.
        writeProject("1111" + "1111" + "2222", MatchRulesV1(gamesPerSet = 1, setTiebreak = false))

        onEdt {
            val panel = openPanel()
            assertEquals("8", panel.label("stats-value-points_won-p1-text").text)

            // The control stays at the top: it is not in the scrolled table.
            val table = panel.find<StatsTable>("stats-table")!!
            assertFalse(SwingUtilities.isDescendingFrom(panel.find<AbstractButton>("stats-scope-2"), table))

            panel.find<AbstractButton>("stats-scope-2")!!.doClick()

            assertEquals("0", panel.label("stats-value-points_won-p1-text").text)
            assertEquals("4", panel.label("stats-value-points_won-p2-text").text)
        }
    }

    @Test
    fun oneSetHasNoScopeSelector() {
        writeProject("1111")

        onEdt {
            assertNull(openPanel().find<AbstractButton>("stats-scope-0"))
        }
    }

    @Test
    fun unscoredPointsShowTheCoverage() {
        writeProject("11.1")

        onEdt {
            val coverage = openPanel().find<CoverageNote>("stats-coverage")!!

            assertTrue(coverage.isVisible)
            assertEquals(listOf("Based on 3 of 4 points. The other points have no winner."), coverage.lines)
        }
    }

    @Test
    fun aClickOnAValueFromOnePointOpensThePoint() {
        writeProject("1222")
        val opened = mutableListOf<String>()

        onEdt {
            val panel = openPanel(onOpenPoint = { opened += it })

            // Player 2 wins the longest run, from point 2. All points are 5 s long, so the first point is the longest.
            panel.find<JComponent>("stats-value-longest_point_run-p2")!!.click(0, 0)
            panel.find<JComponent>("stats-value-longest_point")!!.click(0, 0)
            assertTrue(panel.find<JComponent>("stats-value-points_won-p1")!!.mouseListeners.isEmpty())
        }

        assertEquals(listOf("p2", "p1"), opened)
    }

    @Test
    fun theMomentumChartShowsTheScoredPointsAndOpensThePointUnderTheMouse() {
        writeProject("12.22")
        val opened = mutableListOf<String>()

        onEdt {
            val chart = openPanel(onOpenPoint = { opened += it }).find<MomentumChart>("stats-momentum")!!
            assertEquals(listOf(1, 0, -1, -2), chart.momentum.points.map { it.difference })

            chart.setSize(560, 170)
            chart.click(555, 80)
        }

        assertEquals(listOf("p5"), opened)
    }

    @Test
    fun theLongPointLimitSavesTheSettingAndChangesTheLabel() {
        writeProject("1111")

        onEdt {
            val panel = openPanel()
            val row = panel.find<StatRowView>("stats-row-${MatchStat.LONG_POINTS_WON.key}")!!
            panel.find<LimitStepper>("stats-long-point-limit")!!.value = 20

            assertEquals("Long points won (\u2265 20 s)", panel.label("stats-label-${MatchStat.LONG_POINTS_WON.key}").text)
            // The rows change in place, so the stepper keeps the focus.
            assertTrue(row === panel.find<StatRowView>("stats-row-${MatchStat.LONG_POINTS_WON.key}"))
        }

        assertEquals(20, StatsIO.readForProjectDir(projectDir.path).longPointMinSeconds)
    }

    @Test
    fun aShortPointLimitAtTheLongPointLimitMovesTheLongPointLimit() {
        writeProject("1111")

        onEdt {
            val panel = openPanel()
            panel.find<LimitStepper>("stats-short-point-limit")!!.value = 13

            assertEquals(14, panel.find<LimitStepper>("stats-long-point-limit")!!.value)
        }

        assertEquals(14, StatsIO.readForProjectDir(projectDir.path).longPointMinSeconds)
    }

    @Test
    fun theTransparencySliderSavesTheSettingWhenTheUserReleasesIt() {
        writeProject("1111")

        onEdt {
            val panel = openPanel()
            val slider = assertNotNull(panel.find<JSlider>("stats-card-transparency"))
            assertEquals(StatsSettingsV1.DEFAULT_CARD_TRANSPARENCY_PERCENT, slider.value)

            slider.valueIsAdjusting = true
            slider.value = 50
            assertEquals("50 %", panel.label("stats-card-transparency-value").text)
            assertEquals(StatsSettingsV1.DEFAULT_CARD_TRANSPARENCY_PERCENT, StatsIO.readForProjectDir(projectDir.path).cardTransparencyPercent)

            slider.valueIsAdjusting = false
        }

        assertEquals(50, StatsIO.readForProjectDir(projectDir.path).cardTransparencyPercent)
    }

    private fun JComponent.click(x: Int, y: Int) {
        val event = MouseEvent(this, MouseEvent.MOUSE_CLICKED, 0L, 0, x, y, 1, false, MouseEvent.BUTTON1)
        mouseListeners.forEach { it.mouseClicked(event) }
    }

    private fun openPanel(onOpenScoring: () -> Unit = {}, onOpenPoint: (String) -> Unit = {}): SwingStatsPanel =
        SwingStatsPanel(noDialogs(), onOpenScoring, onOpenPoint).apply {
            setProjectManifest(File(projectDir, "match.trproj").path)
            onActivated()
        }

    /** '1' and '2' are the point winners. Any other character leaves the point unscored. */
    private fun writeProject(winners: String, rules: MatchRulesV1 = MatchRulesV1()) {
        val points = (1..winners.length).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
        val outcomes = points.zip(winners.toList()).mapNotNull { (point, c) ->
            when (c) {
                '1' -> point.id to Outcome.P1
                '2' -> point.id to Outcome.P2
                else -> null
            }
        }.toMap()
        EdlIO.writeForProjectDir(projectDir.path, EdlV1(points = points))
        ScoreIO.writeForProjectDir(
            projectDir.path,
            ScoreV1(outcomes = outcomes, player1Name = "Alex", player2Name = "Sam", rules = rules),
        )
    }

    private fun noDialogs(): UserDialogService =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(UserDialogService::class.java)) { _, method, _ ->
            error("Unexpected dialog: ${method.name}")
        } as UserDialogService

    private fun onEdt(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { failure = runCatching(block).exceptionOrNull() }
        failure?.let { throw it }
    }

    private fun SwingStatsPanel.label(name: String): JLabel = assertNotNull(find<JLabel>(name), "No label $name")

    private inline fun <reified T : Component> Container.find(name: String): T? =
        allComponents(this).filterIsInstance<T>().firstOrNull { it.name == name }

    private fun allComponents(container: Container): Sequence<Component> = container.components.asSequence().flatMap { child ->
        sequenceOf(child) + if (child is Container) allComponents(child) else emptySequence()
    }
}
