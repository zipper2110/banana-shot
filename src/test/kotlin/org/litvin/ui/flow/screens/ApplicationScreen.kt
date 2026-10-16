package org.litvin.ui.flow.screens

import org.litvin.ui.flow.harness.UiFlowContext
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import java.util.concurrent.FutureTask

internal class ApplicationScreen(
    internal val context: UiFlowContext,
) {
    val projects = ProjectsScreen(this)
    val points = PointsScreen(this)
    val colors = ColorsScreen(this)
    val crop = CropScreen(this)
    val export = ExportScreen(this)

    fun assertProjectsOnlyNavigation() {
        assertNavigation(projectNavigationVisible = false)
        projects.assertReady()
    }

    fun assertProjectNavigation() {
        assertNavigation(projectNavigationVisible = true)
    }

    fun assertTitle(title: String) {
        eventually("window title to be '$title'") {
            val actual = onEdt { Window.getWindows().filterIsInstance<Frame>().single { it.name == "app-frame" }.title }
            if (actual != title) throw AssertionError("Window title was '$actual'")
        }
    }

    fun eventually(description: String, assertion: () -> Unit) {
        var lastFailure: Throwable? = null
        try {
            context.driver.waitUntil(description) {
                try {
                    assertion()
                    true
                } catch (failure: AssertionError) {
                    lastFailure = failure
                    false
                } catch (failure: IllegalStateException) {
                    lastFailure = failure
                    false
                }
            }
        } catch (timeout: AssertionError) {
            val diagnostic = buildString {
                appendLine(timeout.message ?: "Timed out waiting for $description")
                lastFailure?.message?.let { appendLine("Last observed failure: $it") }
                appendLine("Windows:")
                appendLine(windowInventory())
                appendLine("Fake-service calls:")
                appendLine(fakeServiceCalls())
            }
            throw AssertionError(diagnostic.trimEnd(), lastFailure ?: timeout)
        }
    }

    private fun windowInventory(): String = onEdt {
        Window.getWindows().mapIndexed { index, window ->
            buildString {
                append("[$index] ${window.javaClass.simpleName}")
                when (window) {
                    is Frame -> append(" title='${window.title}'")
                    is Dialog -> append(" title='${window.title}' modal=${window.isModal}")
                }
                append(" name='${window.name}'")
                append(" showing=${window.isShowing}")
                append(" active=${window.isActive}")
                append(" focused=${window.isFocused}")
            }
        }.joinToString("\n").ifBlank { "<none>" }
    }

    private fun fakeServiceCalls(): String = buildString {
        appendLine("mediaPlayers=${context.fakeMediaPlayers?.calls ?: "native"}")
        appendLine("renderService=${context.fakeRenderService?.calls ?: "production"}")
        appendLine("filePicker=${context.filePicker.calls}")
        append("dialogs=${context.dialogs.calls}")
    }

    private fun assertNavigation(projectNavigationVisible: Boolean) {
        eventually("project navigation visibility to be $projectNavigationVisible") {
            // The export queue does not depend on the project, so Export always shows (B-32).
            context.driver.requireShowing("nav-projects")
            context.driver.requireShowing("nav-export")
            // Feedback also shows with no open project (T3 of B-8).
            context.driver.requireShowing("nav-feedback")
            listOf("nav-points", "nav-adjust-video", "nav-stats")
                .forEach { name -> context.driver.requireShowing(name, projectNavigationVisible) }
        }
    }

    private fun <T> onEdt(action: () -> T): T {
        if (EventQueue.isDispatchThread()) return action()
        val task = FutureTask(action)
        EventQueue.invokeAndWait(task)
        return task.get()
    }
}

internal abstract class UserFlowScreen(
    protected val application: ApplicationScreen,
) {
    protected val context: UiFlowContext get() = application.context

    /** Opens a tab. [sectionName] is the button of a sub-tab to click after the tab shows, for example on Colors & Transform. */
    protected fun open(navigationName: String, visibleComponentName: String, sectionName: String? = null) {
        // The project tabs show only after a project opens, and the project opens after the click on Open.
        application.eventually("$navigationName to become visible") {
            context.driver.requireShowing(navigationName)
        }
        context.driver.click(navigationName)
        if (sectionName != null) {
            application.eventually("$sectionName to become visible") {
                context.driver.requireShowing(sectionName)
            }
            context.driver.click(sectionName)
        }
        application.eventually("$visibleComponentName to become visible") {
            context.driver.requireShowing(visibleComponentName)
        }
    }

    protected fun assertVisible(componentName: String) {
        application.eventually("$componentName to be visible") {
            context.driver.requireShowing(componentName)
        }
    }
}
