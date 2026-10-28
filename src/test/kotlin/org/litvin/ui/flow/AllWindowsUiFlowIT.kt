package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.scoring.Outcome
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen
import org.litvin.ui.help.HelpPage
import org.litvin.ui.more.AboutPage
import org.litvin.ui.more.ContactPage
import org.litvin.ui.more.MorePanel
import org.litvin.ui.more.SettingsPage
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

/**
 * Opens each tab and each window of the sidebar. With the property `bananashot.uiFlow.screenshots=true`, it saves a
 * named screenshot of each, for the translators (B-11 E4-T4). Without the property, it checks that each tab and each
 * window opens in the app language.
 */
class AllWindowsUiFlowIT {
    @Test
    fun `each tab and each window of the sidebar opens`(context: UiFlowContext) {
        val shots = context.screenshots.apply { automatic = false }
        val application = ApplicationScreen(context)
        val project = context.fixtures.emptyProject()

        application.projects.assertReady()
        shots.capture("projects-import-match", "tab-projects-no-project")
        application.projects.open().assertRecent(project.manifest.id).openRecent(project.manifest.id)

        application.points.assertReady()
        application.points.markScoredPoint(1_000, 2_000, Outcome.P1)
            .markScoredPoint(3_000, 4_500, Outcome.P2)
            .addComment(1_250, "2", "Good shot")
        shots.capture("points-point-start", "tab-points")

        openTab(application, "nav-stats", "stats-card-column")
        shots.capture("stats-card-column", "tab-statistics")

        application.colors.open()
        shots.capture("colors-brightness", "tab-adjust-colors")
        application.crop.open()
        shots.capture("crop-rotation", "tab-adjust-transform")

        openTab(application, "nav-share", "share-texts")
        shots.capture("share-texts", "tab-thumbnail")

        application.export.open()
        shots.capture("export-initialize", "tab-export")

        application.projects.open().assertCurrentProject(project.manifest.name)
        shots.capture("projects-import-match", "tab-projects")

        openWindow(application, "nav-help", "help-content")
        HelpPage.entries.forEach { page ->
            openSection(application, "help-navigation-${page.name.lowercase()}", "help-content")
            shots.capture("help-content", "help-${page.name.lowercase()}")
        }
        closeWindowOf(application, "help-content")

        openWindow(application, "nav-more", "more-content")
        listOf(SettingsPage.ID, AboutPage.ID, MorePanel.PRIVACY_ID, ContactPage.ID).forEach { id ->
            openSection(application, "more-navigation-$id", "more-content")
            shots.capture("more-content", "more-$id")
        }
        closeWindowOf(application, "more-content")

        openWindow(application, "nav-feedback", "feedback-panel")
        shots.capture("feedback-panel", "feedback")
        context.driver.click("feedback-close")
        assertClosed(application, "feedback-panel")
    }

    private fun openTab(application: ApplicationScreen, navigationName: String, visibleName: String) =
        openWindow(application, navigationName, visibleName)

    private fun openWindow(application: ApplicationScreen, buttonName: String, visibleName: String) {
        val driver = application.context.driver
        application.eventually("$buttonName to be visible") { driver.requireShowing(buttonName) }
        driver.click(buttonName)
        application.eventually("$visibleName to be visible") { driver.requireShowing(visibleName) }
    }

    private fun openSection(application: ApplicationScreen, buttonName: String, visibleName: String) {
        val driver = application.context.driver
        driver.click(buttonName)
        application.eventually("$buttonName to be selected") { driver.requireSelected(buttonName, true) }
        application.eventually("$visibleName to be visible") { driver.requireShowing(visibleName) }
    }

    /** Closes the window of [componentName] as its close button does. */
    private fun closeWindowOf(application: ApplicationScreen, componentName: String) {
        onEdt {
            val window = Window.getWindows().filter(Window::isShowing).firstNotNullOf { window ->
                SwingUtilities.getWindowAncestor(findShowing(window, componentName) ?: return@firstNotNullOf null)
            }
            window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
        }
        assertClosed(application, componentName)
    }

    private fun assertClosed(application: ApplicationScreen, componentName: String) {
        application.eventually("the window of $componentName to close") {
            var showing = false
            onEdt { showing = Window.getWindows().any { window -> findShowing(window, componentName) != null } }
            if (showing) throw AssertionError("$componentName still shows")
        }
    }

    private fun findShowing(component: java.awt.Component, name: String): java.awt.Component? {
        if (component.name == name && component.isShowing) return component
        if (component !is java.awt.Container) return null
        return component.components.firstNotNullOfOrNull { findShowing(it, name) }
    }

    private fun onEdt(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action() else EventQueue.invokeAndWait(action)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
