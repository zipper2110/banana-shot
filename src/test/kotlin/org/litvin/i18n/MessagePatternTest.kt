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
        assertEquals("1 punto", format(pattern, "count" to 1, language = AppLanguage.SPANISH_SPAIN))
        assertEquals("5 puntos", format(pattern, "count" to 5, language = AppLanguage.SPANISH_SPAIN))
    }

    /** B-11 E7-T2: Brazil uses `one` for 0 to 1.99. Portugal uses `one` only for 1, as English. */
    @Test
    fun `the Portuguese plural of Brazil and of Portugal differ for 0 and decimals`() {
        val pattern = "{count, plural, one {# ponto} other {# pontos}}"
        assertEquals("0 ponto", format(pattern, "count" to 0, language = AppLanguage.PORTUGUESE_BRAZIL))
        assertEquals("1 ponto", format(pattern, "count" to 1, language = AppLanguage.PORTUGUESE_BRAZIL))
        assertEquals("2 pontos", format(pattern, "count" to 2, language = AppLanguage.PORTUGUESE_BRAZIL))
        assertEquals(PluralRules.ONE, PluralRules.category(AppLanguage.PORTUGUESE_BRAZIL, 1.5))
        assertEquals("0 pontos", format(pattern, "count" to 0, language = AppLanguage.PORTUGUESE_PORTUGAL))
        assertEquals("1 ponto", format(pattern, "count" to 1, language = AppLanguage.PORTUGUESE_PORTUGAL))
        assertEquals(MessagePattern.OTHER, PluralRules.category(AppLanguage.PORTUGUESE_PORTUGAL, 1.5))
    }

    /** B-11 E7-T2: French uses `one` for 0, 1, and the numbers with decimals below 2 ("0 point", "1,5 seconde"). */
    @Test
    fun `a French plural uses one for 0 and for decimals below 2`() {
        val pattern = "{count, plural, one {# point} other {# points}}"
        assertEquals("0 point", format(pattern, "count" to 0, language = AppLanguage.FRENCH))
        assertEquals("1 point", format(pattern, "count" to 1, language = AppLanguage.FRENCH))
        assertEquals("2 points", format(pattern, "count" to 2, language = AppLanguage.FRENCH))
        assertEquals(PluralRules.ONE, PluralRules.category(AppLanguage.FRENCH, 1.5))
        assertEquals(MessagePattern.OTHER, PluralRules.category(AppLanguage.FRENCH, 2.5))
    }

    /** B-11 E7-T2: German uses `one` only for 1 without decimals, as English ("0 Punkte", "1,5 Sekunden"). */
    @Test
    fun `a German plural uses one only for 1`() {
        val pattern = "{count, plural, one {# Punkt} other {# Punkte}}"
        assertEquals("0 Punkte", format(pattern, "count" to 0, language = AppLanguage.GERMAN))
        assertEquals("1 Punkt", format(pattern, "count" to 1, language = AppLanguage.GERMAN))
        assertEquals("2 Punkte", format(pattern, "count" to 2, language = AppLanguage.GERMAN))
        assertEquals(MessagePattern.OTHER, PluralRules.category(AppLanguage.GERMAN, 1.5))
        assertEquals(setOf(PluralRules.ONE, MessagePattern.OTHER), PluralRules.categories(AppLanguage.GERMAN))
    }

    /** B-11 E8-L-ja: a Japanese noun has no plural form, so Japanese has only `other` (CLDR). */
    @Test
    fun `a Japanese plural uses only other`() {
        val pattern = "{count, plural, other {# ポイント}}"
        listOf(0, 1, 2, 21).forEach { count ->
            assertEquals("$count ポイント", format(pattern, "count" to count, language = AppLanguage.JAPANESE))
        }
        assertEquals(MessagePattern.OTHER, PluralRules.category(AppLanguage.JAPANESE, 1.0))
        assertEquals(MessagePattern.OTHER, PluralRules.category(AppLanguage.JAPANESE, 1.5))
        assertEquals(setOf(MessagePattern.OTHER), PluralRules.categories(AppLanguage.JAPANESE))
    }

    /** B-11 E8-L-zh-Hans and E8-L-zh-Hant: a Chinese noun has no plural form, so Chinese has only `other` (CLDR). */
    @Test
    fun `a Chinese plural uses only other`() {
        listOf(AppLanguage.CHINESE_SIMPLIFIED to "个回合", AppLanguage.CHINESE_TRADITIONAL to "個回合").forEach { (language, noun) ->
            listOf(0, 1, 2, 21).forEach { count ->
                assertEquals("$count $noun", format("{count, plural, other {# $noun}}", "count" to count, language = language))
            }
            assertEquals(MessagePattern.OTHER, PluralRules.category(language, 1.0))
            assertEquals(setOf(MessagePattern.OTHER), PluralRules.categories(language))
        }
    }

    /** B-11 E7-T2: the CLDR rule of Russian, also for 0, the teens, and a number with decimals. */
    @Test
    fun `a Russian plural uses the four Russian categories`() {
        val pattern = "{count, plural, one {# очко} few {# очка} many {# очков} other {# очка}}"
        val expected = mapOf(
            1 to "1 очко", 2 to "2 очка", 5 to "5 очков", 21 to "21 очко", 0 to "0 очков", 11 to "11 очков",
            12 to "12 очков", 22 to "22 очка", 25 to "25 очков", 101 to "101 очко", 111 to "111 очков",
        )
        expected.forEach { (count, text) -> assertEquals(text, format(pattern, "count" to count, language = AppLanguage.RUSSIAN)) }
        assertEquals(MessagePattern.OTHER, PluralRules.category(AppLanguage.RUSSIAN, 1.5))
        assertEquals(PluralRules.MANY, PluralRules.category(AppLanguage.RUSSIAN, 1_000_000.0))
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
