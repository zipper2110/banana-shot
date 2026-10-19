package org.litvin.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MessagePatternTest {
    private fun format(pattern: String, vararg args: Pair<String, Any?>, language: AppLanguage = AppLanguage.ENGLISH): String =
        MessagePattern.parse(pattern).format(args.toMap(), pluralCategory = { PluralRules.category(language, it) })

    @Test
    fun `a named argument shows its value`() {
        assertEquals("Export started: a.mp4", format("Export started: {name}", "name" to "a.mp4"))
    }

    @Test
    fun `a number argument has grouping`() {
        assertEquals("12,345 points", format("{count} points", "count" to 12345))
    }

    @Test
    fun `a plural selects the case and shows the number`() {
        val pattern = "{count, plural, one {# point} other {# points}}"
        assertEquals("1 point", format(pattern, "count" to 1))
        assertEquals("0 points", format(pattern, "count" to 0))
        assertEquals("2 points", format(pattern, "count" to 2))
    }

    @Test
    fun `an exact case has priority`() {
        val pattern = "{count, plural, =0 {none} one {# point} other {# points}}"
        assertEquals("none", format(pattern, "count" to 0))
        assertEquals("1 point", format(pattern, "count" to 1))
    }

    @Test
    fun `a Spanish plural uses the Spanish categories`() {
        val pattern = "{count, plural, one {# punto} other {# puntos}}"
        assertEquals("1 punto", format(pattern, "count" to 1, language = AppLanguage.SPANISH))
        assertEquals("5 puntos", format(pattern, "count" to 5, language = AppLanguage.SPANISH))
    }

    @Test
    fun `a select uses the case of the value or other`() {
        val pattern = "{sport, select, padel {Team} other {Player}} {number}"
        assertEquals("Team 1", format(pattern, "sport" to "padel", "number" to 1))
        assertEquals("Player 2", format(pattern, "sport" to "tennis", "number" to 2))
    }

    @Test
    fun `apostrophes work as in ICU`() {
        assertEquals("don't", format("don't"))
        assertEquals("it''s", format("it''''s"))
        assertEquals("{name}", format("'{name}'"))
    }

    @Test
    fun `a missing argument shows the placeholder and reports the name`() {
        val missing = mutableListOf<String>()
        val text = MessagePattern.parse("Hello {name}").format(emptyMap(), pluralCategory = { "other" }, missing = { missing += it })
        assertEquals("Hello {name}", text)
        assertEquals(listOf("name"), missing)
    }

    @Test
    fun `the argument names include the names in the cases`() {
        val pattern = MessagePattern.parse("{count, plural, one {# of {total}} other {# of {total}}} {name}")
        assertEquals(setOf("count", "total", "name"), pattern.argumentNames)
    }

    @Test
    fun `a plural without other does not parse`() {
        assertFailsWith<IllegalArgumentException> { MessagePattern.parse("{count, plural, one {# point}}") }
    }

    @Test
    fun `a pattern without its closing brace does not parse`() {
        assertFailsWith<IllegalArgumentException> { MessagePattern.parse("Hello {name") }
    }
}
