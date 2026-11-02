package org.litvin.app

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Desktop
import java.awt.Image
import java.awt.Taskbar
import java.awt.Window
import java.awt.event.WindowEvent

/**
 * The macOS behavior that a Swing app does not get automatically (B-13, section 7 of docs/macos/b-13-macos.md).
 * It does nothing on other systems.
 *
 * - Quit: Cmd+Q and "Quit BananaShot" call System.exit by default. Then the app does not save the open project and
 *   the export queue. The quit handler sends the close event of the main window, so a quit runs the same close
 *   sequence as the close button. That sequence ends the process.
 * - About: "About BananaShot" in the app menu opens the About page of More.
 * - Dock icon: a source run has the icon of Java. The app bundle has its own .icns, so it keeps it.
 */
object MacIntegration {
    private val logger = KotlinLogging.logger {}

    fun isMac(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
        osName.startsWith("Mac", ignoreCase = true)

    /** True when the jpackage launcher started the app, so the bundle has its own Dock icon. */
    private fun isPackaged(): Boolean = System.getProperty("jpackage.app-path") != null

    fun install(mainWindow: Window, openAbout: () -> Unit, dockIcon: () -> Image?) {
        if (!isMac() || !Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler { _, response ->
                logger.info { "macOS quit request: the app runs the close sequence of the main window." }
                // The handler runs on the event dispatch thread, so the close sequence runs now.
                mainWindow.dispatchEvent(WindowEvent(mainWindow, WindowEvent.WINDOW_CLOSING))
                // The close sequence ends the process. If it did not, macOS quits the app now.
                response.performQuit()
            }
        }
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler { openAbout() }
        }
        if (!isPackaged() && Taskbar.isTaskbarSupported() &&
            Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)
        ) {
            runCatching { dockIcon()?.let { Taskbar.getTaskbar().iconImage = it } }
                .onFailure { logger.warn(it) { "Cannot set the Dock icon." } }
        }
    }
}
