package org.litvin.ui.flow.harness

import org.litvin.app.AppDataPaths
import org.litvin.app.AppServices
import org.litvin.app.SwingApplicationHandle
import org.litvin.ui.flow.driver.SwingUiDriver
import org.litvin.ui.flow.fakes.FakeMediaPlayerFactory
import org.litvin.ui.flow.fakes.FakeRenderService
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import org.litvin.ui.flow.fakes.RecordingRulesFetcher
import org.litvin.ui.flow.fakes.ScriptedDialogService
import org.litvin.ui.flow.fakes.ScriptedFilePicker
import org.litvin.ui.flow.fixtures.UiFlowFixtureBuilder
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

class UiFlowContext internal constructor(
    val workspace: Path,
    val artifactDirectory: Path,
    val paths: AppDataPaths,
    val preferences: InMemoryPreferencesProvider,
    /** The fake read of the rules file (E7-S4). */
    val rulesFetcher: RecordingRulesFetcher,
    internal val fakeMediaPlayers: FakeMediaPlayerFactory?,
    internal val fakeRenderService: FakeRenderService?,
    internal val nativeMediaPlayers: NativeMediaPlayers?,
    val filePicker: ScriptedFilePicker,
    val dialogs: ScriptedDialogService,
    val fixtures: UiFlowFixtureBuilder,
    val services: AppServices,
    val application: SwingApplicationHandle,
    val driver: SwingUiDriver,
    internal val threadPrefix: String,
    internal val asynchronousFailures: CopyOnWriteArrayList<Throwable>,
    /** True when the "Checking the date…" window showed during the expiry check at start (path A). */
    val checkingDateWindowShown: Boolean = false,
) {
    val mediaPlayers: FakeMediaPlayerFactory
        get() = checkNotNull(fakeMediaPlayers) { "This UI flow uses the native media players" }

    val renderService: FakeRenderService
        get() = checkNotNull(fakeRenderService) { "This UI flow uses the production render service" }

    val nativePlayers: NativeMediaPlayers
        get() = checkNotNull(nativeMediaPlayers) { "This UI flow uses the fake media players" }

    private var restartAction: (() -> UiFlowContext)? = null

    fun restartApplication(): UiFlowContext =
        checkNotNull(restartAction) { "UI flow restart is not available" }.invoke()

    internal fun onRestart(action: () -> UiFlowContext) {
        check(restartAction == null) { "UI flow restart is already configured" }
        restartAction = action
    }
}
