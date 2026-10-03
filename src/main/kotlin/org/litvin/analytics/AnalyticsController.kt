package org.litvin.analytics

interface ManagedAnalytics : Analytics, AutoCloseable {
    /** Starts the last summary of the session. [close] waits for it for a short time. */
    fun beginFinalSend() = Unit
}

/** Runs an action at JVM shutdown. Tests give a fake. */
fun interface ShutdownHooks {
    /** Adds [action]. Closing the result removes it. */
    fun add(action: () -> Unit): AutoCloseable

    object Jvm : ShutdownHooks {
        override fun add(action: () -> Unit): AutoCloseable {
            val thread = Thread({ runCatching(action) }, "analytics-shutdown")
            Runtime.getRuntime().addShutdownHook(thread)
            return AutoCloseable { runCatching { Runtime.getRuntime().removeShutdownHook(thread) } }
        }
    }
}

/**
 * The stable analytics facade for the features. It swaps delivery on consent changes.
 *
 * - [disable]: the user turned off analytics. Stops delivery without a send, deletes the counters, and saves
 *   `DISABLED`.
 * - [close]: app shutdown. Stops delivery and does not change the choice. Thus it must never call [disable].
 *
 * The controller keeps the last tab and window state. A new session gets them, so that the user can turn on
 * analytics at any time.
 */
class AnalyticsController(
    private val config: AnalyticsBuildConfig,
    private val preferences: AnalyticsPreferences,
    private val enabledFactory: (AnalyticsBuildConfig.Enabled, AnalyticsPreferences.SessionStart) -> ManagedAnalytics,
    private val shutdownHooks: ShutdownHooks = ShutdownHooks.Jvm,
) : Analytics, AutoCloseable {
    @Volatile private var delegate: Analytics = DisabledAnalytics
    private var managed: ManagedAnalytics? = null
    private var sessionHook: AutoCloseable? = null
    @Volatile private var lastTab = AnalyticsEvent.TabShown(null, byUser = false)
    @Volatile private var lastWindow = AnalyticsEvent.WindowActive(false)

    override fun record(event: AnalyticsEvent) {
        when (event) {
            is AnalyticsEvent.TabShown -> lastTab = event.copy(byUser = false)
            is AnalyticsEvent.WindowActive -> lastWindow = event
            else -> Unit
        }
        runCatching { delegate.record(event) }
    }

    fun startIfConsented() {
        if (preferences.resolve().isEnabled) enable(recordConsent = false)
    }

    fun enable() = enable(recordConsent = true)

    @Synchronized
    private fun enable(recordConsent: Boolean) {
        val enabled = config as? AnalyticsBuildConfig.Enabled ?: return
        if (managed != null) return
        val start = runCatching { preferences.startSession() }.getOrNull() ?: return
        val created = runCatching { enabledFactory(enabled, start) }.getOrNull()
        if (created == null) {
            runCatching { preferences.endSession() }
            return
        }
        if (recordConsent) preferences.record(AnalyticsPreferences.Choice.ENABLED)
        sessionHook = runCatching { shutdownHooks.add { preferences.endSession() } }.getOrNull()
        managed = created
        runCatching { created.record(lastTab) }
        runCatching { created.record(lastWindow) }
        delegate = created
    }

    /** The app is about to exit: starts the last summary. See "Exit sequence" in the design. */
    @Synchronized
    fun beginFinalSend() {
        runCatching { managed?.beginFinalSend() }
    }

    /** The user turned off analytics. Stops delivery and saves the choice. */
    @Synchronized
    fun disable() {
        stopDelivery()
        runCatching { sessionHook?.close() }
        sessionHook = null
        runCatching { preferences.endSession() }
        preferences.record(AnalyticsPreferences.Choice.DISABLED)
    }

    /** App shutdown. Stops delivery and keeps the saved choice. The shutdown hook ends the session. */
    @Synchronized
    override fun close() = stopDelivery()

    private fun stopDelivery() {
        delegate = DisabledAnalytics
        val active = managed
        managed = null
        runCatching { active?.close() }
    }

    companion object {
        /** The production controller: each session sends with [transport]. */
        fun create(
            config: AnalyticsBuildConfig,
            preferences: AnalyticsPreferences,
            appVersion: String,
            transport: (AnalyticsBuildConfig.Enabled) -> AnalyticsTransport,
        ): AnalyticsController = AnalyticsController(config, preferences, { enabled, start ->
            EnabledAnalytics(transport(enabled), appVersion, enabled.osFamily, start)
        })
    }
}
