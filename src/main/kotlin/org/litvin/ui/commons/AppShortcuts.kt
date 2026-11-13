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

    /**
     * The ending keys of advanced mode (B-68). The keys 1 to 4 give the point to player 1, and the keys 0 to 7 give it
     * to player 2. The outer keys are the winners, so the keys mirror the two sides. [alsoBound] adds the numpad digit
     * and the Shift form: on an AZERTY keyboard, the digits of the top row need Shift.
     */
    val ENDING_P1_WINNER = AppShortcut("1")
    val ENDING_P2_FORCED_ERROR = AppShortcut("2")
    val ENDING_P2_UNFORCED_ERROR = AppShortcut("3")
    val ENDING_P1_ACE = AppShortcut("4")
    val ENDING_P2_ACE = AppShortcut("7")
    val ENDING_P1_UNFORCED_ERROR = AppShortcut("8")
    val ENDING_P1_FORCED_ERROR = AppShortcut("9")
    val ENDING_P2_WINNER = AppShortcut("0")
    private val ENDING_ALSO_BOUND = mapOf(
        ENDING_P1_WINNER to listOf("NUMPAD1", "shift 1"),
        ENDING_P2_FORCED_ERROR to listOf("NUMPAD2", "shift 2"),
        ENDING_P2_UNFORCED_ERROR to listOf("NUMPAD3", "shift 3"),
        ENDING_P1_ACE to listOf("NUMPAD4", "shift 4"),
        ENDING_P2_ACE to listOf("NUMPAD7", "shift 7"),
        ENDING_P1_UNFORCED_ERROR to listOf("NUMPAD8", "shift 8"),
        ENDING_P1_FORCED_ERROR to listOf("NUMPAD9", "shift 9"),
        ENDING_P2_WINNER to listOf("NUMPAD0", "shift 0"),
    )

    /** The other key strokes of [shortcut] that the app also binds. */
    fun alsoBound(shortcut: AppShortcut): List<String> = when {
        shortcut == DELETE && isMac -> listOf("DELETE")
        else -> ENDING_ALSO_BOUND[shortcut].orEmpty()
    }

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
