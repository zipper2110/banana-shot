package org.litvin.ui.tabs.stats

import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.stats.CardStyle
import org.litvin.stats.MatchStat
import org.litvin.stats.StatsIO
import org.litvin.stats.StatsSettingsV1
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.UserDialogService
import java.awt.Component
import java.awt.Container
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The "Card style" choice and the "Compact card", "Group titles" and "Light card" switches of the Stats tab. */
class StatsCardLayoutSwitchTest {
    private val projectDir: File = Files.createTempDirectory("stats-card-layout").toFile()

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun theCompactSwitchSavesTheSettingAndGivesFewerPages() {
        writeProject(StatsSettingsV1(videoMomentum = false, videoStats = MatchStat.entries.map { it.key }))
        var normalPages = 0

        onEdt {
            val panel = openPanel()
            val compact = panel.switch("stats-card-compact")
            assertFalse(compact.isSelected)
            normalPages = pageCount(panel)

            compact.doClick()

            assertTrue(pageCount(panel) < normalPages, "${pageCount(panel)} < $normalPages")
        }

        assertTrue(StatsIO.readForProjectDir(projectDir.path).compactCard)
        onEdt {
            // A panel that opens the project again shows the saved setting.
            assertTrue(openPanel().switch("stats-card-compact").isSelected)
        }
    }

    @Test
    fun theGroupTitlesSwitchSavesTheSetting() {
        writeProject(StatsSettingsV1(videoMomentum = false))

        onEdt {
            val panel = openPanel()
            val titles = panel.switch("stats-card-group-titles")
            assertFalse(titles.isSelected)

            titles.doClick()
        }

        assertTrue(StatsIO.readForProjectDir(projectDir.path).cardGroupTitles)
        assertFalse(StatsIO.readForProjectDir(projectDir.path).compactCard)
    }

    @Test
    fun theStyleChoiceSavesTheStyle() {
        writeProject(StatsSettingsV1(videoMomentum = false))

        onEdt {
            val panel = openPanel()
            val choice = assertNotNull(panel.find<SegmentedChoice<CardStyle>>("stats-card-style"))
            assertEquals(CardStyle.CLASSIC, choice.selected)

            assertNotNull(panel.find<AbstractButton>("stats-card-style-${CardStyle.SHEET.ordinal}")).doClick()
        }

        assertEquals(CardStyle.SHEET.key, StatsIO.readForProjectDir(projectDir.path).cardStyle)
        onEdt {
            assertEquals(CardStyle.SHEET, openPanel().find<SegmentedChoice<CardStyle>>("stats-card-style")?.selected)
        }
    }

    @Test
    fun theLightSwitchSavesTheSetting() {
        writeProject(StatsSettingsV1(videoMomentum = false, cardStyle = CardStyle.STRIPES.key))

        onEdt {
            val light = openPanel().switch("stats-card-light")
            assertFalse(light.isSelected)

            light.doClick()
        }

        val saved = StatsIO.readForProjectDir(projectDir.path)
        assertTrue(saved.lightCard)
        assertEquals(CardStyle.STRIPES.key, saved.cardStyle)
    }

    /** The number of pages from the page label, for example 3 in "Page 1 of 3". */
    private fun pageCount(panel: SwingStatsPanel): Int {
        val label = assertNotNull(panel.find<JLabel>("stats-preview-page"))
        if (!label.isVisible) return 1
        return label.text.substringAfterLast(' ').toInt()
    }

    private fun openPanel(): SwingStatsPanel =
        SwingStatsPanel(noDialogs(), {}, {}).apply {
            setProjectManifest(File(projectDir, "match.trproj").path)
            onActivated()
        }

    private fun writeProject(settings: StatsSettingsV1) {
        val winners = "1111" + "2222" + "1111"
        val points = (1..winners.length).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
        val outcomes = points.zip(winners.toList()).associate { (point, c) -> point.id to if (c == '1') Outcome.P1 else Outcome.P2 }
        EdlIO.writeForProjectDir(projectDir.path, EdlV1(points = points))
        ScoreIO.writeForProjectDir(projectDir.path, ScoreV1(outcomes = outcomes, player1Name = "Alex", player2Name = "Sam"))
        StatsIO.writeForProjectDir(projectDir.path, settings)
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

    private fun SwingStatsPanel.switch(name: String): SwitchBox = assertNotNull(find<SwitchBox>(name), "No switch $name")

    private inline fun <reified T : Component> Container.find(name: String): T? =
        allComponents(this).filterIsInstance<T>().firstOrNull { it.name == name }

    private fun allComponents(container: Container): Sequence<Component> = container.components.asSequence().flatMap { child ->
        sequenceOf(child) + if (child is Container) allComponents(child) else emptySequence()
    }
}
