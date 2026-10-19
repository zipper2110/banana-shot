package org.litvin.i18n

/**
 * A parsed catalog message in the ICU MessageFormat syntax (B-11 plan, section 2, decision of E3-T1).
 *
 * The app does not bundle ICU4J (about 14 MB). This class reads the subset of the ICU syntax that the
 * catalog uses, so a catalog file stays valid ICU:
 * - `{name}`: a named argument. A number argument shows in [DisplayFormats].
 * - `{name, plural, =0 {…} one {…} other {…}}`: plural cases. `#` in a case is the number.
 * - `{name, select, a {…} other {…}}`: select cases.
 * - Apostrophes as in ICU (`ApostropheMode.DOUBLE_OPTIONAL`): `''` is one apostrophe. An apostrophe
 *   before `{`, `}`, `#`, or `|` starts a quoted literal part. Any other apostrophe shows as written, so a
 *   translator writes "don't" and "l'export" with one apostrophe.
 *
 * A plural or a select without the case `other` does not parse.
 */
class MessagePattern private constructor(internal val parts: List<Part>) {

    internal sealed interface Part {
        data class Literal(val text: String) : Part
        data class Argument(val name: String) : Part
        data class Plural(val name: String, val cases: Map<String, List<Part>>) : Part
        data class Select(val name: String, val cases: Map<String, List<Part>>) : Part

        /** `#` in a plural case. */
        data object Number : Part
    }

    /** The names of all arguments, also in the plural and the select cases. */
    val argumentNames: Set<String> by lazy { buildSet { collectNames(parts, this) } }

    /** True when the pattern has a plural or a select part. */
    val hasCases: Boolean get() = parts.any { it is Part.Plural || it is Part.Select }

    /**
     * Formats the pattern. [missing] gets the name of each argument that [args] does not have. The text then
     * shows `{name}`.
     */
    fun format(
        args: Map<String, Any?>,
        pluralCategory: (Double) -> String,
        literal: (String) -> String = { it },
        missing: (String) -> Unit = {},
    ): String = buildString { append(parts, args, null, pluralCategory, literal, missing) }

    private fun StringBuilder.append(
        parts: List<Part>,
        args: Map<String, Any?>,
        number: Any?,
        pluralCategory: (Double) -> String,
        literal: (String) -> String,
        missing: (String) -> Unit,
    ) {
        for (part in parts) {
            when (part) {
                is Part.Literal -> append(literal(part.text))
                is Part.Number -> append(if (number != null) formatValue(number) else "#")
                is Part.Argument ->
                    if (args.containsKey(part.name)) append(formatValue(args[part.name])) else {
                        missing(part.name)
                        append('{').append(part.name).append('}')
                    }
                is Part.Plural -> {
                    if (!args.containsKey(part.name)) {
                        missing(part.name)
                        append('{').append(part.name).append('}')
                        continue
                    }
                    val value = args[part.name]
                    val amount = (value as? Number)?.toDouble() ?: value?.toString()?.toDoubleOrNull() ?: 0.0
                    val exact = part.cases["=" + exactKey(amount)]
                    val chosen = exact ?: part.cases[pluralCategory(amount)] ?: part.cases.getValue(OTHER)
                    append(chosen, args, value, pluralCategory, literal, missing)
                }
                is Part.Select -> {
                    if (!args.containsKey(part.name)) {
                        missing(part.name)
                        append('{').append(part.name).append('}')
                        continue
                    }
                    val chosen = part.cases[args[part.name]?.toString()] ?: part.cases.getValue(OTHER)
                    append(chosen, args, number, pluralCategory, literal, missing)
                }
            }
        }
    }

    companion object {
        const val OTHER = "other"

        /** Parses [pattern]. Throws [IllegalArgumentException] with the position when the pattern is not valid. */
        fun parse(pattern: String): MessagePattern = MessagePattern(Parser(pattern).parseAll())

        /** A number shows in the display format. Any other value shows as given: no markup, no syntax. */
        internal fun formatValue(value: Any?): String = when (value) {
            null -> ""
            is Int, is Long, is Short, is Byte -> DisplayFormats.integer((value as Number).toLong())
            is Double, is Float -> {
                val number = (value as Number).toDouble()
                if (number == Math.rint(number) && !number.isInfinite()) {
                    DisplayFormats.integer(number.toLong())
                } else {
                    DisplayFormats.decimal(number, 0, 2)
                }
            }
            else -> value.toString()
        }

        private fun exactKey(amount: Double): String =
            if (amount == Math.rint(amount)) amount.toLong().toString() else amount.toString()

        private fun collectNames(parts: List<Part>, into: MutableSet<String>) {
            parts.forEach { part ->
                when (part) {
                    is Part.Argument -> into += part.name
                    is Part.Plural -> {
                        into += part.name
                        part.cases.values.forEach { collectNames(it, into) }
                    }
                    is Part.Select -> {
                        into += part.name
                        part.cases.values.forEach { collectNames(it, into) }
                    }
                    else -> Unit
                }
            }
        }
    }

    private class Parser(private val text: String) {
        private var position = 0

        fun parseAll(): List<Part> {
            val parts = parseMessage(inPlural = false)
            if (position < text.length) fail("Unexpected '}'")
            return parts
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("$message at position $position in: $text")

        /** Reads text up to a '}' that closes the current case, or to the end. */
        private fun parseMessage(inPlural: Boolean): List<Part> {
            val parts = mutableListOf<Part>()
            val literal = StringBuilder()
            fun flush() {
                if (literal.isNotEmpty()) {
                    parts += Part.Literal(literal.toString())
                    literal.clear()
                }
            }
            while (position < text.length) {
                val c = text[position]
                when {
                    c == '\'' -> readApostrophe(literal, inPlural)
                    c == '{' -> {
                        flush()
                        parts += parseArgument()
                    }
                    c == '}' -> break
                    c == '#' && inPlural -> {
                        flush()
                        parts += Part.Number
                        position++
                    }
                    else -> {
                        literal.append(c)
                        position++
                    }
                }
            }
            flush()
            return parts
        }

        private fun readApostrophe(literal: StringBuilder, inPlural: Boolean) {
            val next = text.getOrNull(position + 1)
            when {
                next == '\'' -> {
                    literal.append('\'')
                    position += 2
                }
                next == '{' || next == '}' || next == '|' || (next == '#' && inPlural) -> {
                    // A quoted part up to the next single apostrophe.
                    position++
                    while (position < text.length) {
                        val c = text[position]
                        if (c == '\'') {
                            if (text.getOrNull(position + 1) == '\'') {
                                literal.append('\'')
                                position += 2
                                continue
                            }
                            position++
                            return
                        }
                        literal.append(c)
                        position++
                    }
                }
                else -> {
                    literal.append('\'')
                    position++
                }
            }
        }

        private fun skipSpaces() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        private fun readIdentifier(): String {
            skipSpaces()
            val start = position
            while (position < text.length && (text[position].isLetterOrDigit() || text[position] == '_' || text[position] == '=' || text[position] == '-')) {
                position++
            }
            if (start == position) fail("Expected a name")
            return text.substring(start, position)
        }

        private fun expect(c: Char) {
            skipSpaces()
            if (position >= text.length || text[position] != c) fail("Expected '$c'")
            position++
        }

        private fun parseArgument(): Part {
            position++ // '{'
            val name = readIdentifier()
            skipSpaces()
            if (position < text.length && text[position] == '}') {
                position++
                return Part.Argument(name)
            }
            expect(',')
            val type = readIdentifier()
            return when (type) {
                "plural" -> {
                    expect(',')
                    Part.Plural(name, parseCases(inPlural = true))
                }
                "select" -> {
                    expect(',')
                    Part.Select(name, parseCases(inPlural = false))
                }
                else -> fail("Unknown argument type '$type'")
            }
        }

        private fun parseCases(inPlural: Boolean): Map<String, List<Part>> {
            val cases = LinkedHashMap<String, List<Part>>()
            while (true) {
                skipSpaces()
                if (position >= text.length) fail("Missing '}'")
                if (text[position] == '}') {
                    position++
                    break
                }
                val key = readIdentifier()
                if (key in cases) fail("Duplicate case '$key'")
                expect('{')
                cases[key] = parseMessage(inPlural)
                if (position >= text.length) fail("Missing '}'")
                position++ // '}'
            }
            if (OTHER !in cases) fail("The case 'other' is missing")
            return cases
        }
    }
}
