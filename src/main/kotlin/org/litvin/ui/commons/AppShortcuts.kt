package org.litvin.ui.commons

import org.litvin.i18n.tr

/**
 * A keyboard shortcut. [keyStroke] is the form of `KeyStroke.getKeyStroke`. [display] is the key name for the user,
 * in the app language ("Space", "Espacio"). The letter keys stay the same in all languages (B-11 plan, section 8).
 */
data class AppShortcut(
    val keyStroke: String,
) {
    val display: String get() = AppShortcuts.displayName(keyStroke)
}

object AppShortcuts {
    private val isMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

    val HELP = AppShortcut("F1")

    /** The Help key of macOS apps, in the Help menu of the menu bar. F1 also works. */
    const val MAC_HELP = "⌘?"

    /** The key of the Help texts: [MAC_HELP] on macOS, else F1. */
    val helpKey: String get() = if (isMac) MAC_HELP else "F1"
    val PLAY_PAUSE = AppShortcut("SPACE")
    val POINT_START = AppShortcut("C")
    val CANCEL_PENDING = AppShortcut("ESCAPE")
    val TOGGLE_FAVORITE = AppShortcut("A")
    /**
     * The key labeled "delete" on a Mac keyboard sends Backspace. A Mac keyboard has no forward Delete key, or only
     * Fn+delete. Thus on macOS the shortcut is Backspace, and [alsoBound] keeps the forward Delete key (B-13).
     */
    val DELETE = AppShortcut(if (isMac) "BACK_SPACE" else "DELETE")
    val LEFT = AppShortcut("LEFT")
    val RIGHT = AppShortcut("RIGHT")
    val UP = AppShortcut("UP")
    val DOWN = AppShortcut("DOWN")
    val SHIFT_LEFT = AppShortcut("shift LEFT")
    val SHIFT_RIGHT = AppShortcut("shift RIGHT")
    val SHIFT_UP = AppShortcut("shift UP")
    val SHIFT_DOWN = AppShortcut("shift DOWN")
    val SCORE_PLAYER_1 = AppShortcut("Q")
    val SCORE_NO_POINT = AppShortcut("W")
    val SCORE_PLAYER_2 = AppShortcut("E")
    val NEXT_POINT = AppShortcut("R")
    val SWITCH_SERVE = AppShortcut("S")
    val PREVIOUS_POINT = AppShortcut("shift R")
    val TOGGLE_FRAME_STEP = AppShortcut("F")

    /** The other key strokes of [shortcut] that the app also binds. */
    fun alsoBound(shortcut: AppShortcut): List<String> =
        if (shortcut == DELETE && isMac) listOf("DELETE") else emptyList()

    /**
     * The key name of [keyStroke] from the catalog. The app does not use `KeyEvent.getKeyText`, because the JDK gives
     * that name in the language of Windows, not in the app language.
     */
    fun displayName(keyStroke: String): String {
        if (keyStroke.startsWith("shift ")) return tr("key.withShift", "key" to displayName(keyStroke.removePrefix("shift ")))
        return when (keyStroke) {
            "SPACE" -> tr("key.space")
            "DELETE", "BACK_SPACE" -> tr("key.delete")
            // Most Mac keyboards need Fn for F1. On macOS, the Help menu has Cmd+? (B-13, MacIntegration).
            "F1" -> if (isMac) MAC_HELP else "F1"
            "LEFT" -> tr("key.left")
            "RIGHT" -> tr("key.right")
            "UP" -> tr("key.up")
            "DOWN" -> tr("key.down")
            "ENTER" -> tr("key.enter")
            "ESCAPE" -> tr("key.escape")
            else -> keyStroke
        }
    }
}
