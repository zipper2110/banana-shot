package org.litvin.app

import org.litvin.AppInfo
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.export.FileCompletedRendersRepository
import org.litvin.export.ProductionRenderService
import org.litvin.export.RenderService
import org.litvin.export.SavedRenderQueue
import org.litvin.license.NewWorkGate
import org.litvin.license.SavedVersionRules
import org.litvin.license.check.ExecutorExpiryScheduler
import org.litvin.license.check.ExpiryController
import org.litvin.license.check.PreferencesExpiredFlagStore
import org.litvin.license.check.SystemRetryTiming
import org.litvin.feedback.FeedbackBuildConfig
import org.litvin.feedback.FeedbackSender
import org.litvin.feedback.HttpFeedbackSender
import org.litvin.license.online.HttpRulesFetcher
import org.litvin.license.online.UpdateTrust
import java.net.http.HttpClient
import java.time.Duration
import org.litvin.license.time.DataFolderFileTimeProbe
import org.litvin.license.time.PreferencesSavedTimeStore
import org.litvin.license.time.RunTimeCounter
import org.litvin.license.time.SystemTime
import org.litvin.license.time.TimeEngine
import org.litvin.media.MediaPlayerFactory
import org.litvin.media.productionMediaPlayerFactory
import org.litvin.projects.FileProjectsRepository
import org.litvin.projects.ProjectsRepository
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.SystemFilePicker
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.VideoErrorPanel
import org.litvin.ui.commons.UserDialogService
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsPreferences
import org.litvin.analytics.AnalyticsTransport
import org.litvin.analytics.JdkAnalyticsTransport
import org.litvin.license.BuildInfo
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

internal data class AppServicesProductionFactory(
    val paths: () -> AppDataPaths = AppDataPaths::production,
    val preferences: () -> PreferencesProvider = PreferencesProvider::production,
    val executors: () -> ExecutorProvider = { TrackedExecutorProvider() },
    val mediaPlayers: () -> MediaPlayerFactory = { productionMediaPlayerFactory(VideoErrorPanel) },
    val filePicker: () -> FilePicker = { SystemFilePicker() },
    val dialogs: () -> UserDialogService = { SwingUserDialogService() },
    val expiry: (AppDataPaths, PreferencesProvider, ExecutorProvider) -> ExpiryController = ::productionExpiryController,
    val projectsRepository: (AppDataPaths, NewWorkGate) -> ProjectsRepository = { paths, newWork ->
        FileProjectsRepository(paths.projects, newWork)
    },
    val completedRenders: (AppDataPaths) -> CompletedRendersRepository = { FileCompletedRendersRepository(it.completedRenders) },
    val adjustments: (ExecutorProvider) -> AdjustmentsSession = {
        AdjustmentsSession(it.createScheduledExecutor("adjustments-autosave"))
    },
    val renderService: (AppDataPaths, CompletedRendersRepository, NewWorkGate) -> RenderService = { paths, completed, newWork ->
        ProductionRenderService(completed, newWork, SavedRenderQueue.claim(paths.renderQueue, paths.renderQueueLock))
            .also { it.restoreSavedQueue() }
    },
    val encoderCapabilities: () -> EncoderCapabilities = EncoderCapabilities::production,
    val feedbackSender: () -> FeedbackSender? = ::productionFeedbackSender,
    val afterConstruction: (AppServices) -> Unit = { },
)

data class AppServices(
    val paths: AppDataPaths,
    val preferences: PreferencesProvider,
    val executors: ExecutorProvider,
    val mediaPlayers: MediaPlayerFactory,
    val renderService: RenderService,
    val filePicker: FilePicker,
    val dialogs: UserDialogService,
    val projectsRepository: ProjectsRepository,
    val completedRenders: CompletedRendersRepository,
    val adjustments: AdjustmentsSession,
    /** The build expiry, the version rules, and the update check (`build-expiry-spec.md`). `main` starts it. */
    val expiry: ExpiryController,
    // The detection runs test encodes and takes some seconds, so the export panel waits for it in the background.
    val encoderCapabilities: CompletableFuture<EncoderCapabilities> = CompletableFuture.completedFuture(EncoderCapabilities.NONE),
    val analyticsConfig: AnalyticsBuildConfig = AnalyticsBuildConfig.Disabled("not_configured"),
    val analyticsPreferences: AnalyticsPreferences? = null,
    val analyticsController: AnalyticsController? = null,
    /** Sends the feedback reports (B-8). Null when the build has no endpoint: then the form offers only copy and email. */
    val feedbackSender: FeedbackSender? = null,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val resourcesInConstructionOrder = listOf(
            paths,
            preferences,
            executors,
            expiry,
            mediaPlayers,
            filePicker,
            dialogs,
            projectsRepository,
            completedRenders,
            adjustments,
            renderService,
            analyticsController,
        )
        var firstFailure: Throwable? = null
        resourcesInConstructionOrder.asReversed().forEach { resource ->
            if (resource is AutoCloseable) {
                try {
                    resource.close()
                } catch (failure: Throwable) {
                    if (firstFailure == null) firstFailure = failure
                }
            }
        }
        firstFailure?.let { throw it }
    }

    companion object {
        fun production(): AppServices = production(AppServicesProductionFactory())

        internal fun production(factory: AppServicesProductionFactory): AppServices {
            val constructedResources = mutableListOf<AutoCloseable>()

            fun <T> construct(create: () -> T): T = create().also { resource ->
                if (resource is AutoCloseable) constructedResources += resource
            }

            try {
                val paths = construct(factory.paths)
                val preferences = construct(factory.preferences)
                val executors = construct(factory.executors)
                val expiry = construct { factory.expiry(paths, preferences, executors) }
                val mediaPlayers = construct(factory.mediaPlayers)
                val filePicker = construct(factory.filePicker)
                val dialogs = construct(factory.dialogs)
                val projectsRepository = construct { factory.projectsRepository(paths, expiry) }
                val completedRenders = construct { factory.completedRenders(paths) }
                val adjustments = construct { factory.adjustments(executors) }
                val renderService = construct { factory.renderService(paths, completedRenders, expiry) }
                val encoderCapabilities = CompletableFuture.supplyAsync { factory.encoderCapabilities() }
                val analyticsConfig = AnalyticsBuildConfig.fromSystemProperties()
                val analyticsPreferences = if (analyticsConfig is AnalyticsBuildConfig.Enabled) {
                    AnalyticsPreferences(preferences.node(PreferencesProvider.ANALYTICS))
                } else null
                val analyticsController = analyticsPreferences?.let { preferencesForAnalytics ->
                    construct { productionAnalyticsController(analyticsConfig, preferencesForAnalytics) }.also { it.start() }
                }
                val services = AppServices(
                    paths = paths,
                    preferences = preferences,
                    executors = executors,
                    mediaPlayers = mediaPlayers,
                    renderService = renderService,
                    filePicker = filePicker,
                    dialogs = dialogs,
                    projectsRepository = projectsRepository,
                    completedRenders = completedRenders,
                    adjustments = adjustments,
                    expiry = expiry,
                    encoderCapabilities = encoderCapabilities,
                    analyticsConfig = analyticsConfig,
                    analyticsPreferences = analyticsPreferences,
                    analyticsController = analyticsController,
                    feedbackSender = factory.feedbackSender(),
                )
                factory.afterConstruction(services)
                return services
            } catch (failure: Throwable) {
                constructedResources.asReversed().forEach { resource ->
                    try {
                        resource.close()
                    } catch (cleanupFailure: Throwable) {
                        failure.addSuppressed(cleanupFailure)
                    }
                }
                throw failure
            }
        }
    }
}

/**
 * The real parts of the build expiry (E7-S1): the read of the rules file, its URL, the saved time and the flag in the
 * preferences, and the data folder. Only this function makes them. All other code gets them through constructor
 * parameters, and the tests give fakes. No property or environment variable turns off the expiry.
 */
/**
 * The analytics controller of the app (B-9). The summaries contain [BuildInfo.VERSION]. The transport uses the proxy
 * of Windows and the trust of E5-S4, as the feedback sender does. It follows no redirect.
 */
internal fun productionAnalyticsController(
    config: AnalyticsBuildConfig,
    preferences: AnalyticsPreferences,
    transport: (AnalyticsBuildConfig.Enabled) -> AnalyticsTransport = ::productionAnalyticsTransport,
): AnalyticsController = AnalyticsController.create(config, preferences, BuildInfo.VERSION, transport)

private fun productionAnalyticsTransport(config: AnalyticsBuildConfig.Enabled): AnalyticsTransport =
    JdkAnalyticsTransport(config.endpoint) {
        UpdateTrust.production.client(HttpClient.Redirect.NEVER, JdkAnalyticsTransport.CONNECT_TIMEOUT)
    }

/**
 * The sender of the feedback reports, or null without a valid `bananashot.feedback.endpoint` (T2 of B-8). It uses the
 * proxy of Windows and the trust of E5-S4. The trust stores load at the first send, not at the start.
 */
internal fun productionFeedbackSender(): FeedbackSender? {
    val endpoint = FeedbackBuildConfig.endpointFromSystemProperties() ?: return null
    val sender by lazy {
        HttpFeedbackSender(endpoint, UpdateTrust.production.client(HttpClient.Redirect.NEVER, Duration.ofSeconds(10)))
    }
    return FeedbackSender { report -> sender.send(report) }
}

internal fun productionExpiryController(
    paths: AppDataPaths,
    preferences: PreferencesProvider,
    executors: ExecutorProvider,
): ExpiryController {
    val node = preferences.node(PreferencesProvider.LICENSE)
    val counter = RunTimeCounter.production()
    return ExpiryController(
        appVersion = AppInfo.version,
        engine = TimeEngine(counter, SystemTime(DataFolderFileTimeProbe.inDataFolder(paths.root)), PreferencesSavedTimeStore(node)),
        counter = counter,
        savedRules = SavedVersionRules(paths.root),
        fetcher = HttpRulesFetcher(URI(HttpRulesFetcher.RULES_URL), counter),
        flag = PreferencesExpiredFlagStore(node),
        scheduler = ExecutorExpiryScheduler(executors.createScheduledExecutor("expiry-check")),
        retryTiming = SystemRetryTiming,
    )
}
