package org.litvin.app

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsCrash
import org.litvin.crash.CrashKind
import org.litvin.crash.CrashLibrary
import org.litvin.crash.CrashMonitor
import org.litvin.crash.FreezeWatchdog
import org.litvin.crash.PreviousCrash
import org.litvin.crash.SessionMarker
import org.litvin.i18n.tr
import org.litvin.shared.util.AppActivities
import org.litvin.shared.util.AppActivity
import org.litvin.ui.commons.UserDialogService
import java.awt.Component
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.desktop.ScreenSleepEvent
import java.awt.desktop.ScreenSleepListener
import java.awt.desktop.SystemSleepEvent
import java.awt.desktop.SystemSleepListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The wiring of the crash evidence (B-67, `docs/crashes/b-67-crash-evidence.md`): the crash marker, the freeze
 * watchdog, the sleep activity, the crash popup, and the crash facts for the analytics.
 */
internal object CrashReporting {
    private val logger = KotlinLogging.logger {}

    /** The time after a wake that still counts as the activity "sleep_wake". */
    private const val WAKE_MILLIS = 60_000L

    @Volatile private var monitor: CrashMonitor? = null
    @Volatile private var watchdog: FreezeWatchdog? = null

    /** Starts the crash marker. Call it after the instance lock. Returns the crash of the previous session, or null. */
    fun start(paths: AppDataPaths = AppDataPaths.production()): PreviousCrash? = runCatching {
        val created = CrashMonitor(SessionMarker(paths.sessionMarker), paths.crashes, AppInfo.version)
        monitor = created
        val crash = created.start()
        installSleepListener()
        crash
    }.onFailure { logger.warn(it) { "The crash marker cannot start." } }.getOrNull()

    /** Starts the freeze watchdog. Call it after the main window exists. */
    fun startWatchdog() {
        val current = monitor ?: return
        if (watchdog != null) return
        watchdog = FreezeWatchdog(postToUi = EventQueue::invokeLater, onFreeze = current::setFreeze).also { it.start() }
    }

    /**
     * Shows the crash popup when the previous session crashed with evidence (a crash file or a freeze). "Report this
     * problem" opens the feedback form with the crash summary and the log ([report]).
     */
    fun showPopup(crash: PreviousCrash?, parent: Component?, dialogs: UserDialogService, report: ((String, String) -> Unit)?) {
        if (crash == null || !crash.hasEvidence) return
        val title = tr("crash.title", "app" to AppInfo.NAME)
        val onReport = report?.let { { it(title, crash.summary()) } }
        dialogs.showReportableError(parent, tr("crash.message", "app" to AppInfo.NAME), title, onReport)
    }

    /** The facts for the analytics: only values of closed lists. */
    fun analyticsCrash(crash: PreviousCrash?): AnalyticsCrash? = crash?.let {
        AnalyticsCrash(
            kind = when (it.kind) {
                CrashKind.NATIVE -> AnalyticsCrash.Kind.NATIVE
                CrashKind.MEMORY -> AnalyticsCrash.Kind.MEMORY
                CrashKind.FREEZE -> AnalyticsCrash.Kind.FREEZE
                CrashKind.UNKNOWN -> AnalyticsCrash.Kind.UNKNOWN
            },
            library = it.library?.let { library ->
                when (library) {
                    CrashLibrary.COREVIDEO -> AnalyticsCrash.Library.COREVIDEO
                    CrashLibrary.GRAPHICS -> AnalyticsCrash.Library.GRAPHICS
                    CrashLibrary.MPV -> AnalyticsCrash.Library.MPV
                    CrashLibrary.FFMPEG -> AnalyticsCrash.Library.FFMPEG
                    CrashLibrary.JVM -> AnalyticsCrash.Library.JVM
                    CrashLibrary.SYSTEM -> AnalyticsCrash.Library.SYSTEM
                    CrashLibrary.OTHER -> AnalyticsCrash.Library.OTHER
                }
            },
            activities = it.activities.map { activity ->
                when (activity) {
                    AppActivity.EXPORT -> AnalyticsCrash.Activity.EXPORT
                    AppActivity.PLAYBACK -> AnalyticsCrash.Activity.PLAYBACK
                    AppActivity.SLEEP_WAKE -> AnalyticsCrash.Activity.SLEEP_WAKE
                }
            }.toSet(),
        )
    }

    /**
     * Marks the activity "sleep_wake" from a sleep until [WAKE_MILLIS] after the wake. Only macOS gives sleep events to
     * Java. The listener writes the marker on the event thread at once, before the sleep.
     */
    private fun installSleepListener() {
        val desktop = runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null }.getOrNull() ?: return
        if (!desktop.isSupported(Desktop.Action.APP_EVENT_SYSTEM_SLEEP)) return
        val timer = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "crash-wake-timer").apply { isDaemon = true } }
        val owner = Any()
        var clear: ScheduledFuture<*>? = null
        fun sleep() = synchronized(owner) {
            clear?.cancel(false)
            AppActivities.set(AppActivity.SLEEP_WAKE, owner, true)
        }
        fun wake() = synchronized(owner) {
            clear?.cancel(false)
            clear = timer.schedule({ AppActivities.set(AppActivity.SLEEP_WAKE, owner, false) }, WAKE_MILLIS, TimeUnit.MILLISECONDS)
        }
        desktop.addAppEventListener(object : SystemSleepListener, ScreenSleepListener {
            override fun systemAboutToSleep(e: SystemSleepEvent) = sleep()
            override fun systemAwoke(e: SystemSleepEvent) = wake()
            override fun screenAboutToSleep(e: ScreenSleepEvent) = sleep()
            override fun screenAwoke(e: ScreenSleepEvent) = wake()
        })
    }
}
