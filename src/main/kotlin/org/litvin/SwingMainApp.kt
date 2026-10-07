package org.litvin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.analytics.AnalyticsEvent
import org.litvin.app.AppDataPaths
import org.litvin.app.AppServices
import org.litvin.app.InstanceLock
import org.litvin.app.PreferencesProvider
import org.litvin.app.SwingApplicationFactory
import org.litvin.app.SwingApplicationHandle
import org.litvin.license.check.ExpiryController
import org.litvin.ui.commons.AppTheme
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.Theme
import org.litvin.ui.commons.ThemePreferences
import org.litvin.ui.expiry.CheckingDateWindow
import java.awt.EventQueue
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import javax.swing.UIManager

object SwingMainApp {
    private val logger = KotlinLogging.logger {}

    private val ALREADY_RUNNING_MESSAGE =
        "${AppInfo.NAME} is already running. Use the open window. If you cannot see it, look in the taskbar."

    private const val UNEXPECTED_ERROR = "Unexpected error"

    @Volatile
    private var instanceLock: InstanceLock? = null

    @JvmStatic
    fun main(args: Array<String>) {
        // The order of the first steps is in build-expiry-spec.md, "Velopack hook processes".
        // 1. Use the proxy settings of Windows. Java reads this property only one time, at the first
        //    network request of the process, so it must come before all other code ("Proxy").
        System.setProperty("java.net.useSystemProxies", "true")
        // 2. The Velopack setup starts the app with a hook argument when it installs, updates, or
        //    removes the app. The app has no task for a hook: exit at once, with no UI, no lock,
        //    and no read or write of the saved time.
        if (VelopackHooks.isHookProcess(args)) {
            kotlin.system.exitProcess(0)
        }
        // 3. The lock of B-19 and the time steps come after these steps (takeInstanceLock below).
        if (args.contains("--diagnostics")) {
            kotlin.system.exitProcess(DistributionDiagnostics.run())
        }
        logger.info {
            "${AppInfo.NAME} ${AppInfo.version} started on ${System.getProperty("os.name")} ${System.getProperty("os.version")}, " +
                "Java ${System.getProperty("java.version")}."
        }
        try {
            // The saved theme sets the seed colors of the Palette before the app makes a component.
            val (theme, accent) = runCatching {
                val themePreferences = ThemePreferences(PreferencesProvider.production().node(PreferencesProvider.APPLICATION))
                val theme = themePreferences.load()
                theme to themePreferences.accentFor(theme)
            }.getOrDefault(AppTheme.DEFAULT to AppTheme.DEFAULT.accent)
            Theme.start(theme, accent)
            UIManager.put("defaultFont", Font("Segoe UI", Font.PLAIN, 14))
        } catch (failure: Exception) {
            logger.warn(failure) { "Failed to initialize FlatLaf look and feel." }
        }

        configureDisplayScaling()
        configureTextRendering()

        if (!takeInstanceLock()) {
            kotlin.system.exitProcess(0)
        }

        val services = try {
            AppServices.production()
        } catch (failure: Throwable) {
            logger.error(failure) { "Application startup failed." }
            SwingUserDialogService().showError(null, failure.message ?: failure.toString(), "Startup error")
            return
        }

        // 4. The time steps and the expiry check, after the lock and before the main window and a project
        //    ("Time and the clock", "Expiry check").
        try {
            if (services.expiry.start() == ExpiryController.StartPath.DATE_CHECK) {
                // Path A of "Start of a build that looks expired": a maximum of 15 seconds before the main window.
                CheckingDateWindow.during { services.expiry.runDateCheck() }
            }
        } catch (failure: Throwable) {
            logger.error(failure) { "The expiry check at start failed." }
            SwingUserDialogService().showError(null, failure.message ?: failure.toString(), "Startup error")
            runCatching { services.close() }
            return
        }

        EventQueue.invokeLater {
            var handle: SwingApplicationHandle? = null
            Thread.setDefaultUncaughtExceptionHandler { _, failure ->
                logger.error(failure) { "Unexpected uncaught Swing error." }
                // The analytics send only the count, not the exception (B-9).
                services.analyticsController?.record(AnalyticsEvent.UncaughtError)
                val message = failure.message ?: failure.toString()
                // "Report this problem" opens the feedback form after the main window exists (T3 of B-8).
                val onReport = handle?.reportProblem?.let { report -> { EventQueue.invokeLater { report(UNEXPECTED_ERROR, message) } } }
                services.dialogs.showReportableError(null, message, UNEXPECTED_ERROR, onReport)
            }

            try {
                WindowsGpuPreference.ensureHighPerformancePreference()
                applyBaseTheme()
                handle = SwingApplicationFactory.create(
                    services = services,
                    onWindowClosed = { kotlin.system.exitProcess(0) },
                )
            } catch (failure: Throwable) {
                logger.error(failure) { "Application startup failed." }
                services.dialogs.showError(null, failure.message ?: failure.toString(), "Startup error")
                try {
                    services.close()
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
            }
        }
    }

    /**
     * Takes the lock of B-19 before the app reads or writes its state. Returns false when another
     * instance runs: then the user sees a message, and the process must quit.
     */
    private fun takeInstanceLock(): Boolean {
        val lockFile = AppDataPaths.production().instanceLock
        return when (val result = InstanceLock.acquire(lockFile)) {
            is InstanceLock.Result.Acquired -> {
                // Keep the lock reachable. If the channel is collected, Windows releases the lock.
                instanceLock = result.lock
                true
            }
            InstanceLock.Result.HeldByOtherInstance -> {
                logger.info { "Another instance has the lock $lockFile. This start quits." }
                SwingUserDialogService().showInfo(null, ALREADY_RUNNING_MESSAGE, AppInfo.NAME)
                false
            }
            is InstanceLock.Result.Failed -> {
                logger.warn(result.cause) { "Cannot lock $lockFile. The app starts without the instance lock." }
                true
            }
        }
    }

    private fun configureDisplayScaling() {
        val javaSpec = (System.getProperty("java.specification.version") ?: "11").trim()
        val major = javaSpec.toDoubleOrNull() ?: 11.0

        if (System.getProperty("sun.java2d.uiScale.enabled") == null) {
            System.setProperty("sun.java2d.uiScale.enabled", "true")
        }

        if (major < 9) {
            if (System.getProperty("sun.java2d.dpiaware") == null) {
                System.setProperty("sun.java2d.dpiaware", "true")
            }
            if (System.getProperty("sun.java2d.uiScale") == null) {
                val scale = Toolkit.getDefaultToolkit().screenResolution.toDouble() / 96.0
                if (scale >= 1.25) {
                    val value = String.format(java.util.Locale.US, "%.2f", scale)
                    System.setProperty("sun.java2d.uiScale", value)
                }
            }
        }
    }

    private fun configureTextRendering() {
        try {
            val override = System.getProperty("bananashot.textAA")
                ?: System.getenv("BANANASHOT_TEXT_AA")
            val isWindows = (System.getProperty("os.name") ?: "").lowercase().contains("win")
            val value = when (override?.lowercase()?.trim()) {
                null, "", "auto" -> if (isWindows) "lcd_hrgb" else "on"
                "off" -> "off"
                "on" -> "on"
                "lcd" -> "lcd"
                "lcd-hrgb", "lcd_hrgb" -> "lcd_hrgb"
                "lcd-hbgr", "lcd_hbgr" -> "lcd_hbgr"
                "lcd-vrgb", "lcd_vrgb" -> "lcd_vrgb"
                "lcd-vbgr", "lcd_vbgr" -> "lcd_vbgr"
                else -> if (isWindows) "lcd_hrgb" else "on"
            }
            System.setProperty("swing.aatext", if (value == "off") "false" else "true")
            System.setProperty("awt.useSystemAAFontSettings", value)
            System.setProperty("sun.java2d.fractionalmetrics", "on")
        } catch (_: Throwable) {
            System.setProperty("swing.aatext", "true")
            System.setProperty("awt.useSystemAAFontSettings", "on")
        }
    }

    private fun applyBaseTheme() {
        val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val family = when {
            families.contains("Segoe UI Variable") -> "Segoe UI Variable"
            families.contains("Segoe UI") -> "Segoe UI"
            else -> "Tahoma"
        }
        val baseSize = (UIManager.getFont("Label.font")?.size2D ?: 13f).coerceAtLeast(13f)
        val baseFont = Font(family, Font.PLAIN, baseSize.toInt())

        val keys = UIManager.getDefaults().keys()
        while (keys.hasMoreElements()) {
            val key = keys.nextElement()
            if (key.toString().endsWith(".font")) UIManager.put(key, baseFont)
        }
        UIManager.put("defaultFont", baseFont)
        UIManager.put("ToolTip.hideAccelerator", true)
    }
}
