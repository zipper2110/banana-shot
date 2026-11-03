package org.litvin.app

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit

/**
 * Keeps the saved main window on a connected screen (B-13). The app saves the place of the window at the close. If
 * the user then disconnects the screen of that place, for example a second display of a laptop, the window opens
 * where nobody can see it.
 */
internal object WindowPlacement {
    /** The part of the title bar that must be on a screen, so that the user can drag the window. */
    private const val MIN_VISIBLE_WIDTH = 120
    private const val MIN_VISIBLE_HEIGHT = 20
    private const val TITLE_BAR_HEIGHT = 32

    /**
     * [saved] when enough of its title bar is on one of [screens]. Else the size of [saved], made to fit, in the
     * middle of [fallback]. All rectangles are in the coordinates of the virtual desktop.
     */
    fun onScreen(saved: Rectangle, screens: List<Rectangle>, fallback: Rectangle): Rectangle {
        val titleBar = Rectangle(saved.x, saved.y, saved.width, TITLE_BAR_HEIGHT)
        val visible = screens.any { screen ->
            val part = screen.intersection(titleBar)
            !part.isEmpty && part.width >= MIN_VISIBLE_WIDTH && part.height >= MIN_VISIBLE_HEIGHT
        }
        if (visible) return saved
        val width = saved.width.coerceAtMost(fallback.width)
        val height = saved.height.coerceAtMost(fallback.height)
        return Rectangle(fallback.x + (fallback.width - width) / 2, fallback.y + (fallback.height - height) / 2, width, height)
    }

    /** [saved] on the screens of this computer. The usable area of the main screen is the fallback. */
    fun onScreen(saved: Rectangle): Rectangle {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val screens = environment.screenDevices.map { usableBounds(it.defaultConfiguration) }
        val fallback = usableBounds(environment.defaultScreenDevice.defaultConfiguration)
        return onScreen(saved, screens, fallback)
    }

    /** The bounds of a screen without the menu bar, the Dock, or the taskbar. */
    private fun usableBounds(configuration: java.awt.GraphicsConfiguration): Rectangle {
        val bounds = configuration.bounds
        val insets = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(configuration) }.getOrNull() ?: return bounds
        return Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
    }
}
