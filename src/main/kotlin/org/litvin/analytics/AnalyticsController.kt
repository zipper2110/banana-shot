package org.litvin.analytics

interface ManagedAnalytics : Analytics, AutoCloseable {
    /** Starts the last summary of the session. [close] waits for it for a short time. */
    fun beginFinalSend() = Unit

    /** Changes the level of the running session. */
    fun setLevel(level: AnalyticsLevel) = Unit
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
 * The stable analytics facade for the features (`docs/analytics/design.md`, "Levels").
 *
 * - [start]: app start. Starts the session with the saved level. Without a choice, the level is essential.
 * - [choose]: the user made a choice. Saves it and changes the level of the running session. The choice
 *   [AnalyticsPreferences.Choice.OFF] stops the session without a send and deletes its counters.
 * - [close]: app shutdown. Stops delivery and does not change the choice.
 *
 * The controller keeps the last tab and window state. A session that starts later (the user turns the statistics
 * on again, or the start failed) gets them.
 */
class AnalyticsController(
    private val config: AnalyticsBuildConfig,
    private val preferences: AnalyticsPreferences,
    private val sessionFactory: (AnalyticsBuildConfig.Enabled, AnalyticsPreferences.SessionStart, AnalyticsLevel) -> ManagedAnalytics,
    private val shutdownHooks: ShutdownHooks = ShutdownHooks.Jvm,
) : Analytics, AutoCloseable {
    @Volatile private var delegate: Analytics = DisabledAnalytics
    private var managed: ManagedAnalytics? = null
    private var sessionHook: AutoCloseable? = null
    private var closed = false
    @Volatile private var lastTab = AnalyticsEvent.TabShown(null, byUser = false)
    @Volatile private var lastWindow = AnalyticsEvent.WindowActive(false)
    @Volatile private var lastSettings: AnalyticsEvent.Settings? = null

    override fun record(event: AnalyticsEvent) {
        when (event) {
            is AnalyticsEvent.TabShown -> lastTab = event.copy(byUser = false)
            is AnalyticsEvent.WindowActive -> lastWindow = event
            is AnalyticsEvent.Settings -> lastSettings = event
            else -> Unit
        }
        runCatching { delegate.record(event) }
    }

    /** App start: starts the session with the saved level. The choice [AnalyticsPreferences.Choice.OFF] starts nothing. */
    @Synchronized
    fun start() {
        preferences.resolve().level?.let(::startSession)
    }

    /** The user made [choice]. Saves it and changes the level of the running session, or stops the session. */
    @Synchronized
    fun choose(choice: AnalyticsPreferences.Choice) {
        require(choice != AnalyticsPreferences.Choice.UNDECIDED)
        runCatching { preferences.record(choice) }
        val level = choice.level ?: return stopSession()
        val running = managed
        if (running != null) runCatching { running.setLevel(level) } else startSession(level)
    }

    /** Call only while holding the lock. */
    private fun startSession(level: AnalyticsLevel) {
        val enabled = config as? AnalyticsBuildConfig.Enabled ?: return
        if (managed != null || closed) return
        val start = runCatching { preferences.startSession() }.getOrNull() ?: return
        val created = runCatching { sessionFactory(enabled, start, level) }.getOrNull()
        if (created == null) {
            runCatching { preferences.endSession() }
            return
        }
        sessionHook = runCatching { shutdownHooks.add { preferences.endSession() } }.getOrNull()
        managed = created
        runCatching { created.record(lastTab) }
        runCatching { created.record(lastWindow) }
        // A new session after "off" gets the settings that the app recorded at its start.
        lastSettings?.let { settings -> runCatching { created.record(settings) } }
        delegate = created
    }

    /** The app is about to exit: starts the last summary. See "Exit sequence" in the design. */
    @Synchronized
    fun beginFinalSend() {
        runCatching { managed?.beginFinalSend() }
    }

    /** The user turned off the statistics. Stops delivery without a send and ends the session. */
    private fun stopSession() {
        if (managed == null) return
        stopDelivery()
        runCatching { sessionHook?.close() }
        sessionHook = null
        runCatching { preferences.endSession() }
    }

    /** App shutdown. Stops delivery and keeps the saved choice. The shutdown hook ends the session. */
    @Synchronized
    override fun close() {
        closed = true
        stopDelivery()
    }

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
        ): AnalyticsController = AnalyticsController(config, preferences, { enabled, start, level ->
            EnabledAnalytics(transport(enabled), appVersion, enabled.osFamily, start, level)
        })
    }
}
