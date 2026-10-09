package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.Sport
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen
import java.nio.file.Path
import kotlin.test.assertEquals

class ApplicationShellUiFlowIT {
    @Test
    fun `a new match survives application restart and reopens from recents`(initial: UiFlowContext) {
        var application = ApplicationScreen(initial)
        application.assertProjectsOnlyNavigation()

        application.projects.importMatch(projectName = "Club final")
        application.points.assertReady()
        application.assertProjectNavigation()

        val created = initial.fixtures.onlyProject()
        assertEquals("Club final", created.manifest.name)
        assertEquals(
            initial.fixtures.sourceVideo.toAbsolutePath().normalize(),
            Path.of(requireNotNull(created.manifest.sourceVideo)).toAbsolutePath().normalize(),
        )

        val restarted = initial.restartApplication()
        application = ApplicationScreen(restarted)
        application.assertProjectsOnlyNavigation()
        application.projects.assertRecent(created.manifest.id).openRecent(created.manifest.id)
        application.points.assertReady()
        application.projects.open().assertCurrentProject(created.manifest.name)
    }

    @Test
    fun `the open project can be renamed on the Projects tab`(context: UiFlowContext) {
        val application = ApplicationScreen(context)
        application.assertProjectsOnlyNavigation()
        application.projects.importMatch(projectName = "Club final")
        application.points.assertReady()
        application.assertTitle("BananaShot — Points — Club final")

        application.projects.open()
            .renameCurrent("Semi final")
            .assertCurrentProject("Semi final")
        application.assertTitle("BananaShot — Projects — Semi final")
        assertEquals("Semi final", context.fixtures.onlyProject().manifest.name)
    }

    @Test
    fun `a padel match keeps the sport and the deuce rule of the New project dialog`(context: UiFlowContext) {
        val application = ApplicationScreen(context)
        application.projects.importPadelMatch(projectName = "Padel final", deuce = DeuceRule.NO_AD)
        application.points.assertReady()

        val created = context.fixtures.onlyProject()
        val score = ScoreIO.readForProjectDir(created.directory.toString())
        assertEquals(Sport.PADEL, score.sport)
        assertEquals(DeuceRule.NO_AD, score.rules.deuce)
        assertEquals("Team 1", score.player1Name)
        assertEquals("Team 2", score.player2Name)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
