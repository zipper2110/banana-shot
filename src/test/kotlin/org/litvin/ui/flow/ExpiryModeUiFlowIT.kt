package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.AppInfo
import org.litvin.app.PreferencesProvider
import org.litvin.license.ExpiredVersionException
import org.litvin.license.check.PreferencesExpiredFlagStore
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.harness.UiFlowExpiry
import org.litvin.ui.flow.screens.ApplicationScreen
import java.awt.EventQueue
import java.awt.Window
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/*
 * E8-S9: the main scenarios of build-expiry-spec.md, "Tests", in the real main window. Each scenario needs other
 * expiry options, so each class has its own extension.
 */

private fun requireExpiredWindow(context: UiFlowContext) {
    context.driver.requireShowing("expired-dialog")
    context.driver.requireShowing("expiry-banner")
    context.driver.requireShowing("nav-export")
    context.driver.requireShowing("nav-projects", false)
    context.driver.requireShowing("nav-points", false)
    context.driver.requireShowing("nav-scoring", false)
}

private fun requireNormalWindow(context: UiFlowContext) {
    // The driver does not find a dialog that never showed. Thus, this check reads the windows.
    context.driver.waitUntil("the expired dialog to be hidden") {
        onEdt { Window.getWindows().none { it.name == "expired-dialog" && it.isShowing } }
    }
    context.driver.requireShowing("expiry-banner", false)
    context.driver.requireShowing("nav-projects")
}

/** A rules file with a rule that stops this version today. */
private fun stopRuleFile(): String {
    val today = LocalDate.now(ZoneOffset.UTC)
    return """{"schema":1,"rules":[{"id":"ui-flow-stop","toVersion":"${AppInfo.version}","stopsOn":"$today"}]}"""
}

/** An expired build at start, with the flag of the last session: path B. */
class ExpiredStartUiFlowIT {
    @Test
    fun `an expired build at start shows only the Export tab, the banner, and the dialog, and opens no project`(context: UiFlowContext) {
        requireExpiredWindow(context)
        ApplicationScreen(context).eventually("the online check of the start") {
            if (context.rulesFetcher.calls < 1) throw AssertionError("The fake fetcher got no call")
        }

        context.driver.click("expired-dialog-close")
        context.driver.requireShowing("expired-dialog", false)
        context.driver.requireShowing("expiry-banner")

        assertFailsWith<ExpiredVersionException> {
            context.services.projectsRepository.createProject(context.fixtures.sourceVideo.toString(), "Match")
        }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension(
            expiryOptions = UiFlowExpiry(buildExpiry = LocalDate.of(2020, 1, 1)) { _, preferences ->
                preferences.node(PreferencesProvider.LICENSE).putBoolean(PreferencesExpiredFlagStore.KEY, true)
            },
        )
    }
}

/** A rule stops the version during a session. */
class ExpiryInSessionUiFlowIT {
    @Test
    fun `expiry during a session leaves the project tab, hides the tabs, and shows the banner and the dialog`(context: UiFlowContext) {
        try {
            val application = ApplicationScreen(context)
            application.projects.importMatch(projectName = "Club final")
            application.points.assertReady()

            val pausesBefore = pointsPauses(context)
            context.rulesFetcher.online(stopRuleFile())
            context.rulesFetcher.release()

            requireExpiredWindow(context)
            assertTrue(pointsPauses(context) > pausesBefore, "Expired mode stops the player of the Points tab")
            context.driver.requireShowing("export-initialize")
            context.driver.click("expired-dialog-close")
            context.driver.requireShowing("expired-dialog", false)

            // A click on the gray start button shows the dialog again and adds no export.
            context.driver.click("export-initialize")
            context.driver.requireShowing("expired-dialog")
            context.driver.click("expired-dialog-close")
            assertTrue(context.renderService.jobs.isEmpty(), "Expired mode adds no export")
        } finally {
            context.rulesFetcher.release()
        }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension(
            // The online check of the start waits until the test has opened a project.
            expiryOptions = UiFlowExpiry { fetcher, _ -> fetcher.hold() },
        )
    }
}

/** The clock is 400 days forward, and the server is available: path A gives normal mode and the wrong clock notice. */
class ClockForwardOnlineUiFlowIT {
    @Test
    fun `the normal window opens after Checking the date, with the wrong clock notice`(context: UiFlowContext) {
        val checkingWindowShown = onEdt { Window.getWindows().any { it.name == "checking-date-window" } }
        assertTrue(checkingWindowShown, "The 'Checking the date…' window showed before the main window")

        requireNormalWindow(context)
        context.driver.requireShowing("wrong-clock")
        val text = onEdt { (Window.getWindows().flatMap { all(it) }.first { it.name == "wrong-clock" } as org.litvin.ui.expiry.NoticeRow).shownText }
        assertTrue(text.contains("400 days ahead"), text)

        context.driver.click("wrong-clock-close")
        context.driver.requireShowing("wrong-clock", false)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension(
            expiryOptions = UiFlowExpiry(clockOffset = Duration.ofDays(400)) { fetcher, _ -> fetcher.online() },
        )
    }
}

/** The clock is 400 days forward, and there is no network: expired mode, until "Check now" reaches the server. */
class ClockForwardOfflineUiFlowIT {
    @Test
    fun `expired mode opens, and leaves when the server is available`(context: UiFlowContext) {
        requireExpiredWindow(context)

        // The automatic check runs again after 1 minute (OnlineCheckScheduleTest). "Check now" does the same at once.
        context.rulesFetcher.online()
        context.driver.click("expired-dialog-check-now")

        requireNormalWindow(context)
        val application = ApplicationScreen(context)
        application.projects.importMatch(projectName = "After the fix")
        application.points.assertReady()
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension(expiryOptions = UiFlowExpiry(clockOffset = Duration.ofDays(400)))
    }
}

private fun pointsPauses(context: UiFlowContext): Int =
    context.mediaPlayers.calls.count { it.screen == org.litvin.media.MediaScreen.POINTS && it.action == "pause" }

private fun all(component: java.awt.Component): List<java.awt.Component> =
    listOf(component) + ((component as? java.awt.Container)?.components?.flatMap { all(it) } ?: emptyList())

private fun <T> onEdt(block: () -> T): T {
    var result: Result<T>? = null
    EventQueue.invokeAndWait { result = runCatching(block) }
    return result!!.getOrThrow()
}
