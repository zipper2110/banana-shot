package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.app.PreferencesProvider
import org.litvin.license.check.PreferencesExpiredFlagStore
import org.litvin.license.time.PreferencesSavedTimeStore
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** E7-S4: the app in a UI-flow test uses the fake fetcher and the in-memory preferences for the build expiry. */
class ExpiryIsolationUiFlowIT {
    @Test
    fun `the start uses the fake fetcher, and the saved time and the flag stay in memory`(initial: UiFlowContext) {
        val application = ApplicationScreen(initial)
        application.assertProjectsOnlyNavigation()

        application.eventually("the online check of the start to read the fake fetcher") {
            if (initial.rulesFetcher.calls < 1) throw AssertionError("The fake fetcher got no call")
        }
        val license = initial.preferences.node(PreferencesProvider.LICENSE)
        assertNotNull(license.get(PreferencesSavedTimeStore.KEY, null), "The start writes the saved time")

        // The build is not expired. Thus, the next start clears the flag in the same in-memory node.
        license.putBoolean(PreferencesExpiredFlagStore.KEY, true)
        val restarted = initial.restartApplication()
        ApplicationScreen(restarted).assertProjectsOnlyNavigation()

        assertSame(initial.preferences, restarted.preferences)
        assertNull(license.get(PreferencesExpiredFlagStore.KEY, null), "The start in normal mode clears the flag")
        ApplicationScreen(restarted).eventually("the online check of the new start") {
            if (restarted.rulesFetcher.calls < 1) throw AssertionError("The fake fetcher of the new start got no call")
        }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
