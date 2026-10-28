package org.litvin.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-11 E3-T4: a text that the user can see comes from the catalog. The scan reads the main sources and finds:
 * - a text with words for a component, a tooltip, a title, a label, or a dialog (`text = "Save"`, `JLabel("Size:")`);
 * - in the UI code, a text with words in a string template or a concatenation (`"Set $n"`, `"Team " + n`);
 * - a `const val` text with words outside of the UI code;
 * - `tr` with a free text in place of a key;
 * - a comparison of a display text with a text (`text == "SET"`);
 * - a component name made from a display text (`"export-resolution-" + choice.title`).
 *
 * The scan ignores comments, log and exception messages, and the developer test tab (`ui/tabs/test`). The texts that
 * must stay as they are go into the allowlist `i18n/hard-coded-texts.txt`, one "path | text" line with a reason.
 */
class HardCodedTextScanTest {
    @Test
    fun `the app has no hard-coded text that is not in the allowlist`() {
        val root = File("src/main/kotlin/org/litvin")
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantPath(root).startsWith("ui/tabs/test/") }
            .flatMap { file ->
                val path = file.invariantPath(root)
                findings(file.readText(Charsets.UTF_8), isUiCode = path.startsWith("ui/")).map { Entry(path, it.text) to it }
            }
            .toList()
        val allowed = allowlist()

        val newTexts = found.filter { (entry, _) -> entry !in allowed }.map { (entry, finding) -> "${entry.path}:${finding.line}: ${finding.kind} \"${entry.text}\"" }
        assertEquals(emptyList(), newTexts, "Put these texts into the catalog, or into the allowlist with a reason")
        val stale = allowed - found.map { it.first }.toSet()
        assertEquals(emptySet(), stale, "Remove these entries from the allowlist: the code has no such text now")
    }

    @Test
    fun `the scan finds each kind of hard-coded text`() {
        val source = """
            label.text = "Save"
            button.toolTipText = "SPACE · Play"
            add(JLabel("Size:"))
            JOptionPane.showMessageDialog(this, "Copied: ${'$'}sel")
            val s = "Set ${'$'}n"
            val t = "Team " + n
            val ok = tr("Save the file")
            if (label.text == "SET") run()
            name = "export-resolution-" + choice.title.lowercase()
            val n = "point-${'$'}{choice.displayName}"
            log.info("Saved the file")
            logger.warn { "Cannot read the logo" }
            throw IllegalStateException(
                "No video",
            )
            label.text = "Arial Black"
            css = "arc: 8; borderColor: ${'$'}{hex(LINE)};"
            uri = URI("mailto:${'$'}email")
            val font = "number"
            // label.text = "A comment"
            label.text = tr("common.save")
            name = "${'$'}prefix-title"
            UIManager.put("TextField.font", font)
            label.text = "<html><div style='width:${'$'}{w}px'>${'$'}{escape(text)}</div></html>"
            label.text = "${'$'}{value}%"
            label.text = tr("a.b", "number" to n)
            label.text = "key frames"
        """.trimIndent()

        assertEquals(
            listOf(
                Finding(1, "text", "Save"),
                Finding(2, "text", "SPACE · Play"),
                Finding(3, "text", "Size:"),
                Finding(4, "text", "Copied: ${'$'}sel"),
                Finding(5, "template", "Set ${'$'}n"),
                Finding(6, "concatenation", "Team "),
                Finding(7, "free text in tr", "Save the file"),
                Finding(8, "comparison", "SET"),
                Finding(9, "name from a display text", "export-resolution-"),
                Finding(10, "name from a display text", "point-${'$'}{choice.displayName}"),
                Finding(27, "text", "key frames"),
            ),
            findings(source, isUiCode = true),
        )
        // Outside of the UI code, a concatenation is often a file name or a command. Only the sinks count.
        assertEquals(listOf(Finding(1, "text", "Save")), findings("label.text = \"Save\"\nval path = \"file '\" + name + \"'\"", isUiCode = false))
        assertEquals(listOf(Finding(1, "const val", "Bitrate adjusts")), findings("const val NOTE = \"Bitrate adjusts\"", isUiCode = false))
    }

    data class Entry(val path: String, val text: String)

    data class Finding(val line: Int, val kind: String, val text: String)

    private fun allowlist(): Set<Entry> {
        val text = javaClass.getResource("/i18n/hard-coded-texts.txt")!!.readText(Charsets.UTF_8)
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split(" | ")
                require(parts.size == 3) { "An allowlist line is \"path | text | reason\": $line" }
                Entry(parts[0], parts[1])
            }
            .toSet()
    }

    private fun File.invariantPath(root: File): String = relativeTo(root).invariantSeparatorsPath

    private fun findings(source: String, isUiCode: Boolean): List<Finding> {
        // The depth of the brackets of a log or exception call that goes over more than one line.
        var ignoredDepth = 0
        return source.lines().flatMapIndexed { index, raw ->
            val line = raw.trim()
            val code = line.replace(LITERAL, "\"\"")
            val depth = code.count { it == '(' || it == '{' } - code.count { it == ')' || it == '}' }
            when {
                ignoredDepth > 0 -> {
                    ignoredDepth = maxOf(0, ignoredDepth + depth)
                    emptyList()
                }
                line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") -> emptyList()
                IGNORED_LINE.containsMatchIn(line) -> {
                    ignoredDepth = maxOf(0, depth)
                    emptyList()
                }
                else -> lineFindings(index + 1, line, isUiCode)
            }
        }
    }

    private fun lineFindings(number: Int, line: String, isUiCode: Boolean): List<Finding> {
        val literals = LITERAL.findAll(line).map { it.groupValues[1] }.toList()
        val display = literals.filter { hasDisplayWord(it) }
        return buildList {
            when {
                display.isNotEmpty() && SINK.containsMatchIn(line) -> add(Finding(number, "text", display.first()))
                display.isNotEmpty() && CONST_TEXT.containsMatchIn(line) -> add(Finding(number, "const val", display.first()))
                isUiCode -> {
                    display.firstOrNull { '$' in it }?.let { add(Finding(number, "template", it)) }
                        ?: CONCATENATION.find(line)?.let { match ->
                            val literal = match.groupValues[1].ifEmpty { match.groupValues[2] }
                            if (hasDisplayWord(literal)) add(Finding(number, "concatenation", literal))
                        }
                }
            }
            FREE_KEY.findAll(line).map { it.groupValues[1] }.filterNot { KEY.matches(it) }
                .forEach { add(Finding(number, "free text in tr", it)) }
            COMPARISON.find(line)?.let { add(Finding(number, "comparison", it.groupValues[1].ifEmpty { it.groupValues[2] })) }
            NAME_FROM_DISPLAY.find(line)?.let { add(Finding(number, "name from a display text", it.groupValues[1].ifEmpty { it.groupValues[2] })) }
        }
    }

    /** True when [literal] has a word for the user after the template parts, the HTML tags, and the ASS tags are removed. */
    private fun hasDisplayWord(literal: String): Boolean {
        if (literal in FONT_FAMILIES || STYLE_DECLARATION.containsMatchIn(literal)) return false
        val residue = literal
            .replace(TEMPLATE_BLOCK, " ")
            .replace(TEMPLATE_NAME, " ")
            .replace(URI, " ")
            .replace(HTML_TAG, " ")
            .replace(ESCAPE, " ")
            .replace(HTML_ENTITY, " ")
        // A word with a capital letter ("Save", "PNG"), or two words ("key frames"). One lowercase word is an id.
        return CAPITAL_WORD.containsMatchIn(residue) || TWO_WORDS.containsMatchIn(residue)
    }

    private companion object {
        val LITERAL = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val IGNORED_LINE = Regex(
            """\b(log|logger|LOG|Log)\.\w+\s*[({]|println\(|\berror\(|\brequire\(|\bcheck\(|\brequireNotNull\(|\bcheckNotNull\(|throw |Exception\(|@Suppress\(|^import |^package """,
        )
        val SINK = Regex(
            """(\b(text|toolTipText|tooltip|title|label|accessibleName|accessibleDescription|message|description|subtitle|caption|placeholder|hint)\s*=[^=]|\b(JLabel|JButton|JCheckBox|JRadioButton|JToggleButton|JMenuItem|JMenu|TitledBorder|UiButton)\(|\.(setText|setToolTipText|setTitle)\(|JOptionPane\.show\w+\()""",
        )
        val CONST_TEXT = Regex("""\bconst val \w+(\s*:\s*String)?\s*=\s*"""")
        val CONCATENATION = Regex(""""((?:[^"\\]|\\.)*)"\s*\+\s*[\w(]|[\w)]\s*\+\s*"((?:[^"\\]|\\.)*)"""")
        val FREE_KEY = Regex("""\btr\(\s*"((?:[^"\\]|\\.)*)"""")
        val KEY = Regex("[A-Za-z0-9_.]+")
        val COMPARISON = Regex(
            """\.(?:text|title|label|displayName|toolTipText)\s*[!=]=\s*"([^"]+)"|"([^"]+)"\s*[!=]=\s*[\w.]*\.(?:text|title|label|displayName|toolTipText)\b""",
        )
        val NAME_FROM_DISPLAY = Regex(
            """"([\w$-]*-)"\s*\+.*\.(?:title|text|displayName|label)\b|"([^"]*-\$\{[^}]*\.(?:title|text|displayName|label)\b[^"]*)"""",
        )
        val TEMPLATE_BLOCK = Regex("""\$\{[^}]*\}""")
        val TEMPLATE_NAME = Regex("""\$[A-Za-z_]\w*""")
        val HTML_TAG = Regex("<[^>]*>")
        val ESCAPE = Regex("""\\\\[A-Za-z]+\d*|\\[nrtu][0-9A-Fa-f]{0,4}""")
        val HTML_ENTITY = Regex("&\\w+;")

        val URI = Regex("""(?<![\w])[a-z][a-z0-9+.-]*:\S*""")

        /** A style text of FlatLaf or of HTML, for example "arc: 8; borderColor: #333333;". */
        val STYLE_DECLARATION = Regex("""[\w-]+:\s*[^;"]*;""")

        /** The font families of the app. A family name is not a text for the user. */
        val FONT_FAMILIES = setOf(
            "Segoe UI", "Segoe UI Black", "Segoe UI Semibold", "Segoe UI Variable", "Arial", "Arial Black", "Georgia",
            "Trebuchet MS", "Tahoma", "Consolas", "Cascadia Mono", "Ink Free", "Yu Gothic", "Yu Gothic UI",
        )

        /** Letters that are not part of an id, a key, a path, or a call. */
        private const val WORD_START = """(?<![\w\-./$#@:'])"""
        private const val WORD_END = """(?![\w\-./(@#']|:\w)"""
        val CAPITAL_WORD = Regex("""$WORD_START\p{Lu}\p{L}+$WORD_END""")
        val TWO_WORDS = Regex("""$WORD_START\p{L}{2,}[  ]+\p{L}{2,}$WORD_END""")
    }
}
