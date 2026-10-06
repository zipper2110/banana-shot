package org.litvin.ui.commons

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.litvin.media.VideoProblem
import java.awt.Component
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoErrorPanelTest {
    @AfterEach
    fun clearReportHandler() {
        VideoErrorPanel.onReportProblem = null
    }

    @Test
    fun aReportableProblemOpensTheFeedbackFormWithTheDetails() {
        val reports = mutableListOf<Pair<String, String>>()
        VideoErrorPanel.onReportProblem = { title, message -> reports += title to message }
        val problem = VideoProblem.previewFailure(UnsatisfiedLinkError("blocked"), 4551)

        onEdt {
            val panel = VideoErrorPanel.create(onRetry = {}).component as Container
            (panel as VideoErrorPanel).showProblem(problem)
            val report = panel.button("video-error-report")
            assertTrue(report.isVisible)
            report.doClick()
        }

        assertEquals(listOf(problem.title to problem.reportText()), reports)
    }

    @Test
    fun theReportButtonIsHiddenWhenTheUserCanFixTheProblem() {
        VideoErrorPanel.onReportProblem = { _, _ -> }

        onEdt {
            val panel = VideoErrorPanel.create(onRetry = {}).component as VideoErrorPanel
            panel.showProblem(VideoProblem.UNREADABLE)
            assertTrue(panel.button("video-error-report").isVisible)
            panel.showProblem(VideoProblem.NOT_FOUND)
            assertFalse(panel.button("video-error-report").isVisible)
        }
    }

    @Test
    fun theReportButtonIsHiddenWithoutAFeedbackForm() {
        onEdt {
            val panel = VideoErrorPanel.create(onRetry = {}).component as VideoErrorPanel
            panel.showProblem(VideoProblem.PREVIEW_FAILED)
            assertFalse(panel.button("video-error-report").isVisible)
        }
    }

    private fun onEdt(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { runCatching(block).onFailure { failure = it } }
        failure?.let { throw it }
    }

    private fun Container.button(name: String): AbstractButton =
        components().filterIsInstance<AbstractButton>().single { it.name == name }

    private fun Container.components(): List<Component> =
        components.flatMap { child -> listOf(child) + ((child as? Container)?.components() ?: emptyList()) }
}
