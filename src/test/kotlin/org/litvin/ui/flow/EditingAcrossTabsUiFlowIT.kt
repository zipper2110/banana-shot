package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.scoring.Outcome
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen

class EditingAcrossTabsUiFlowIT {
    @Test
    fun `editing a point across tabs persists without native media or processes`(context: UiFlowContext) {
        val project = context.fixtures.emptyProject()
        val application = ApplicationScreen(context)

        application.projects.open()
            .assertRecent(project.manifest.id)
            .openRecent(project.manifest.id)

        application.points.assertReady()
        application.points.markScoredPoint(1_000, 2_000, Outcome.P1)
            .addComment(1_250, "2", "Ball was in")
        val pointId = application.points.assertSinglePointPersisted(project.directory, 1_000, 2_000)
        application.points.assertCommentPersisted(project.directory, 1, 1_250, 2_000, "Ball was in", "#FFFFFF")
        application.points.assertOutcomePersisted(project.directory, pointId, Outcome.P1)

        application.colors.open()
            .setBrightness(20)
            .togglePlayback()
            .assertColorEditPersisted(project.directory, brightness = 1.2f)

        application.crop.open()
            .setZoomPercent(125)
            .setRotationDegrees(15f)

        application.points.open()
            .assertCommentPersisted(project.directory, 1, 1_250, 2_000, "Ball was in", "#FFFFFF")

        application.crop.assertTransformPersisted(
            project.directory,
            brightness = 1.2f,
            zoom = 1.25f,
            rotationDegrees = 15f,
        )
        application.points.assertOutcomePersisted(project.directory, pointId, Outcome.P1)

        context.mediaPlayers.assertEditingFlowEvents()
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
