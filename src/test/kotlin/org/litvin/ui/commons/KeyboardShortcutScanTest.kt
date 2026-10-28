package org.litvin.ui.commons

import java.io.File
import javax.swing.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * B-11 E7-T3: each shortcut binds a physical key, so that it works with all keyboard layouts (Russian, Greek, and
 * others). The scan reads the main sources and finds:
 * - a shortcut that binds a typed character (`getKeyStroke('q')`, `"typed q"`, `keyChar`, `keyTyped`);
 * - a shortcut with Ctrl+Alt. Windows sends AltGr as Ctrl+Alt, and many layouts type letters with AltGr;
 * - a shortcut with a punctuation key ("[", "]", "+", "/", ";"). Many layouts have other characters on these keys.
 *
 * A key stroke text is the first text argument of `getKeyStroke`, `AppShortcut`, `bind`, or `bindStepKey`.
 */
class KeyboardShortcutScanTest {
    @Test
    fun `no shortcut in the app binds a typed character, Ctrl+Alt, or a punctuation key`() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val problems = sources.flatMap { file -> problems(file.readText(Charsets.UTF_8)).map { "${file.name}: $it" } }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `the scan finds the key strokes of the app`() {
        val shortcuts = File("src/main/kotlin/org/litvin/ui/commons/AppShortcuts.kt").readText(Charsets.UTF_8)
        val strokes = keyStrokes(shortcuts)
        listOf("F1", "SPACE", "Q", "W", "E", "shift R").forEach { assertTrue(it in strokes, "$it not in $strokes") }
        strokes.forEach { assertNotNull(KeyStroke.getKeyStroke(it), it) }
    }

    @Test
    fun `the scan finds each bad shortcut`() {
        val source = """
            im.put(KeyStroke.getKeyStroke('q'), "a")
            val b = AppShortcut("typed q")
            if (e.keyChar == 'q') run()
            override fun keyTyped(e: KeyEvent) = Unit
            bind("ctrl alt P", "c") {}
            bind("alt ctrl P", "c") {}
            bind("altGraph P", "c") {}
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK), "d")
            im.put(KeyStroke.getKeyStroke("OPEN_BRACKET"), "e")
            bindStepKey("shift SLASH", 1)
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SEMICOLON, 0), "f")
            im.put(KeyStroke.getKeyStroke("NOT_A_KEY"), "g")
            val good = AppShortcut("shift R")
            bind("ctrl P", "h") {}
            bind("LEFT", "i") {}
        """.trimIndent()
        assertEquals(
            listOf(
                "line 1: a typed character (getKeyStroke with a Char)",
                "line 2: a typed character (\"typed q\")",
                "line 3: a typed character (keyChar)",
                "line 4: a typed character (keyTyped)",
                "line 5: Ctrl+Alt (\"ctrl alt P\")",
                "line 6: Ctrl+Alt (\"alt ctrl P\")",
                "line 7: Ctrl+Alt (\"altGraph P\")",
                "line 8: Ctrl+Alt (masks)",
                "line 9: a punctuation key (\"OPEN_BRACKET\")",
                "line 10: a punctuation key (\"shift SLASH\")",
                "line 11: a punctuation key (VK_SEMICOLON)",
                "line 12: not a key stroke (\"NOT_A_KEY\")",
            ),
            problems(source),
        )
    }

    private fun problems(source: String): List<String> = source.lines().flatMapIndexed { index, line ->
        val at = "line ${index + 1}"
        buildList {
            if (CHAR_STROKE.containsMatchIn(line)) add("$at: a typed character (getKeyStroke with a Char)")
            TYPED_WORDS.find(line)?.let { add("$at: a typed character (${it.value})") }
            if (CTRL_MASK.containsMatchIn(line) && ALT_MASK.containsMatchIn(line)) add("$at: Ctrl+Alt (masks)")
            PUNCTUATION_CODE.find(line)?.let { add("$at: a punctuation key (${it.value})") }
            keyStrokes(line).forEach { stroke ->
                val words = stroke.split(' ').filter { it.isNotEmpty() }
                when {
                    words.firstOrNull() == "typed" -> add("$at: a typed character (\"$stroke\")")
                    "altGraph" in words || ("ctrl" in words && "alt" in words) -> add("$at: Ctrl+Alt (\"$stroke\")")
                    words.lastOrNull() in PUNCTUATION_KEYS -> add("$at: a punctuation key (\"$stroke\")")
                    KeyStroke.getKeyStroke(stroke) == null -> add("$at: not a key stroke (\"$stroke\")")
                }
            }
        }
    }

    private fun keyStrokes(source: String): List<String> = STROKE_TEXT.findAll(source).map { it.groupValues[1] }.toList()

    private companion object {
        val STROKE_TEXT = Regex("""\b(?:getKeyStroke|AppShortcut|bind|bindStepKey)\(\s*"([^"$]+)"""")
        val CHAR_STROKE = Regex("""\bgetKeyStroke\(\s*'""")
        val TYPED_WORDS = Regex("""\b(?:keyChar|getKeyChar|KEY_TYPED|keyTyped)\b""")
        val CTRL_MASK = Regex("""\bCTRL_(?:DOWN_)?MASK\b""")
        val ALT_MASK = Regex("""\bALT_(?:DOWN_)?MASK\b|\bALT_GRAPH_(?:DOWN_)?MASK\b""")

        /** The key names of `KeyStroke.getKeyStroke` for the punctuation keys of the main keyboard. */
        val PUNCTUATION_KEYS = setOf(
            "OPEN_BRACKET", "CLOSE_BRACKET", "PLUS", "MINUS", "EQUALS", "SLASH", "BACK_SLASH", "SEMICOLON", "COLON",
            "QUOTE", "BACK_QUOTE", "COMMA", "PERIOD", "NUMBER_SIGN", "EXCLAMATION_MARK", "AT", "DOLLAR", "EURO_SIGN",
            "CIRCUMFLEX", "ASTERISK", "LEFT_PARENTHESIS", "RIGHT_PARENTHESIS", "UNDERSCORE", "QUOTEDBL", "LESS",
            "GREATER", "BRACELEFT", "BRACERIGHT", "AMPERSAND", "INVERTED_EXCLAMATION_MARK",
        )
        val PUNCTUATION_CODE = Regex("""\bVK_(?:${PUNCTUATION_KEYS.joinToString("|")})\b""")
    }
}
