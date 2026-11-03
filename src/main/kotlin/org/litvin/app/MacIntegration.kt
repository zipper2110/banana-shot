package org.litvin.app

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.AppInfo
import org.litvin.i18n.tr
import java.awt.Desktop
import java.awt.Image
import java.awt.KeyboardFocusManager
import java.awt.Taskbar
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.KeyStroke

/**
 * The macOS behavior that a Swing app does not get automatically (B-13, section 7 of docs/macos/b-13-macos.md).
 * It does nothing on other systems.
 *
 * - Quit: Cmd+Q and "Quit BananaShot" call System.exit by default. Then the app does not save the open project and
 *   the export queue. The quit handler sends the close event of the main window, so a quit runs the same close
 *   sequence as the close button. That sequence ends the process.
 * - About: "About BananaShot" in the app menu opens the About page of More.
 * - Dock icon: a source run has the icon of Java. The app bundle has its own .icns, so it keeps it.
 * - Help menu: a Help menu in the menu bar of macOS with "BananaShot Help" and Cmd+?, the Help key of macOS apps.
 *   F1 needs the Fn key on most Mac keyboards. `SwingMainApp` puts the menu bar of the window into the menu bar of
 *   macOS (`apple.laf.useScreenMenuBar`).
 */
object MacIntegration {
    private val logger = KotlinLogging.logger {}

    fun isMac(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
        osName.startsWith("Mac", ignoreCase = true)

    /** True when the jpackage launcher started the app, so the bundle has its own Dock icon. */
    private fun isPackaged(): Boolean = System.getProperty("jpackage.app-path") != null

    /** The property that puts the menu bar of a window into the menu bar of macOS. Set it before the first window. */
    const val SCREEN_MENU_BAR = "apple.laf.useScreenMenuBar"

    fun install(mainWindow: Window, openAbout: () -> Unit, dockIcon: () -> Image?, openHelp: () -> Unit = {}) {
        if (!isMac()) return
        if (mainWindow is JFrame) mainWindow.jMenuBar = helpMenuBar(openHelp)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { event ->
            if (isHelpKey(event)) {
                event.consume()
                openHelp()
                true
            } else {
                false
            }
        }
        if (!Desktop.isDesktopSupported()) return
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

    /**
     * True for the key press of Cmd+?. The menu bar shows Cmd+?, but Java does not run a menu item for a key
     * equivalent while one of its windows is active: Swing must handle the key. Swing gets no typed "?" while Cmd is
     * down, so this check reads the key press. On a US keyboard, Cmd+? is Shift+Cmd+/.
     */
    internal fun isHelpKey(event: KeyEvent): Boolean =
        event.id == KeyEvent.KEY_PRESSED && event.isMetaDown && !event.isControlDown && !event.isAltDown &&
            (event.keyChar == '?' || (event.keyCode == KeyEvent.VK_SLASH && event.isShiftDown))

    /**
     * The menu bar with the Help menu. Cmd+? is Shift+Cmd+/ on a US keyboard, and other layouts have "?" on other
     * keys. The shortcut is the character "?": the menu bar of macOS matches a key equivalent by its character, so
     * it works on each layout. Thus this is the one shortcut of the app that is a character (KeyboardShortcutScanTest).
     */
    internal fun helpMenuBar(openHelp: () -> Unit): JMenuBar = JMenuBar().apply {
        add(JMenu(tr("nav.help")).apply {
            add(JMenuItem(tr("menu.help.app", "app" to AppInfo.NAME)).apply {
                name = "mac-help-item"
                accelerator = KeyStroke.getKeyStroke(Character.valueOf('?'), InputEvent.META_DOWN_MASK)
                addActionListener { openHelp() }
            })
        })
    }
}
