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
    val HELP = AppShortcut("F1")
    val PLAY_PAUSE = AppShortcut("SPACE")
    val POINT_START = AppShortcut("C")
    val POINT_END = AppShortcut("V")
    val TOGGLE_FAVORITE = AppShortcut("A")
    val DELETE = AppShortcut("DELETE")
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

    /**
     * The key name of [keyStroke] from the catalog. The app does not use `KeyEvent.getKeyText`, because the JDK gives
     * that name in the language of Windows, not in the app language.
     */
    fun displayName(keyStroke: String): String {
        if (keyStroke.startsWith("shift ")) return tr("key.withShift", "key" to displayName(keyStroke.removePrefix("shift ")))
        return when (keyStroke) {
            "SPACE" -> tr("key.space")
            "DELETE" -> tr("key.delete")
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
