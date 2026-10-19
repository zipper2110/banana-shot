package org.litvin.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * The checks of the catalog files (B-11 plan, section 12). They find a missing translation, a broken placeholder,
 * a key that the code uses but the catalog does not have, and a key that no code uses.
 */
class MessageCatalogTest {
    private val english = MessageCatalog.load(AppLanguage.SOURCE)
    private val translations = AppLanguage.entries.filter { it != AppLanguage.SOURCE }.associateWith { MessageCatalog.loadWithParents(it) }

    @Test
    fun `each language has the same keys as English`() {
        translations.forEach { (language, texts) ->
            assertEquals(emptySet<String>(), english.keys - texts.keys, "Keys without a ${language.code} text")
            assertEquals(emptySet<String>(), texts.keys - english.keys, "${language.code} keys that English does not have")
        }
    }

    /** A regional variant has only the texts that are different from its parent. */
    @Test
    fun `a regional variant changes only some texts of its parent`() {
        AppLanguage.entries.forEach { language ->
            val parent = language.parent ?: return@forEach
            val own = MessageCatalog.load(language)
            val parentTexts = MessageCatalog.loadWithParents(parent)
            assertTrue(own.isNotEmpty(), "${language.code} has no own texts")
            assertEquals(emptySet<String>(), own.keys - english.keys, "${language.code} keys that English does not have")
            assertEquals(emptyList<String>(), own.filter { (key, text) -> parentTexts[key] == text }.keys.sorted(), "${language.code} texts equal to the parent")
        }
    }

    @Test
    fun `each text parses`() {
        val failures = (listOf(AppLanguage.SOURCE to english) + translations.toList()).flatMap { (language, texts) ->
            texts.mapNotNull { (key, text) ->
                runCatching { MessagePattern.parse(text) }.exceptionOrNull()?.let { "${language.code} $key: ${it.message}" }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `a translation has the same placeholders as English`() {
        val failures = translations.flatMap { (language, texts) ->
            texts.mapNotNull { (key, text) ->
                val expected = MessagePattern.parse(english.getValue(key)).argumentNames
                val actual = MessagePattern.parse(text).argumentNames
                if (expected == actual) null else "${language.code} $key: $actual, English: $expected"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    /** The "Do not translate" list of `docs/localization/glossary.md`. */
    @Test
    fun `a translation keeps the terms that are not translated`() {
        val failures = translations.flatMap { (language, texts) ->
            texts.flatMap { (key, text) ->
                KEPT_TERMS.filter { term -> term.containsMatchIn(english.getValue(key)) && !term.containsMatchIn(text) }
                    .map { term -> "${language.code} $key: ${term.pattern}" }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `a translation keeps the markup`() {
        val failures = translations.flatMap { (language, texts) ->
            texts.mapNotNull { (key, text) ->
                val expected = MARKUP.findAll(english.getValue(key)).map { it.value }.sorted().toList()
                val actual = MARKUP.findAll(text).map { it.value }.sorted().toList()
                if (expected == actual) null else "${language.code} $key: $actual, English: $expected"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `no text is empty`() {
        val empty = (listOf(english) + translations.values).flatMap { texts -> texts.filterValues { it.isBlank() }.keys }
        assertEquals(emptyList<String>(), empty)
    }

    @Test
    fun `no catalog file has a key two times`() {
        val duplicates = AppLanguage.entries.flatMap { language ->
            MessageCatalog.BASE_NAMES.flatMap { baseName ->
                val path = MessageCatalog.resourcePath(baseName, language)
                val keys = File(RESOURCES, path).readLines(Charsets.UTF_8)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }
                    .map { it.substringBefore('=').trim() }
                keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map { "$path: $it" }
            }
        }
        assertEquals(emptyList<String>(), duplicates)
    }

    @Test
    fun `each key in a tr call is in the catalog`() {
        val missing = calledKeys().filterNot { it in english.keys }
        assertEquals(emptyList<String>(), missing)
    }

    /**
     * A key is in use when a string literal in the main code is equal to it. Thus a key that an enum gets as a
     * constructor parameter also counts.
     */
    @Test
    fun `each catalog key is in use`() {
        val literals = stringLiterals()
        val unused = english.keys.filterNot { it in literals }.sorted()
        assertEquals(emptyList<String>(), unused)
    }

    @Test
    fun `a broken file gives no texts and no crash`() {
        assertFailsWith<java.nio.charset.CharacterCodingException> {
            MessageCatalog.parse(byteArrayOf(0x61, 0x3D, 0xC3.toByte(), 0x28))
        }
        assertEquals(mapOf("a" to "b"), MessageCatalog.parse("﻿a=b".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `the Spanish catalog has texts in Spanish`() {
        val spanish = Messages.of(AppLanguage.SPANISH_SPAIN)
        assertEquals("Exportaciones", spanish.text("export.table.title"))
        assertEquals("1 punto", spanish.text("export.points", mapOf("count" to 1)))
        assertEquals("3 puntos", spanish.text("export.points", mapOf("count" to 3)))
        assertTrue(spanish.text("help.points.summary").startsWith("Marca"))
    }

    @Test
    fun `Latin American Spanish uses its own words and the other Spanish texts`() {
        val latinAmerica = Messages.of(AppLanguage.SPANISH_LATIN_AMERICA)
        assertEquals("Editor de video de tenis y pádel", latinAmerica.text("app.tagline"))
        assertEquals("Enter", latinAmerica.text("key.enter"))
        assertEquals("Exportaciones", latinAmerica.text("export.table.title"))
        assertEquals("Editor de vídeo de tenis y pádel", Messages.of(AppLanguage.SPANISH_SPAIN).text("app.tagline"))
    }

    private fun mainSources(): Sequence<String> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText(Charsets.UTF_8) }

    private fun calledKeys(): Set<String> =
        mainSources().flatMap { source -> TR_CALL.findAll(source).map { it.groupValues[1] } }.toSortedSet()

    private fun stringLiterals(): Set<String> =
        mainSources().flatMap { source -> LITERAL.findAll(source).map { it.groupValues[1] } }.toSet()

    private companion object {
        val RESOURCES = File("src/main/resources")
        val TR_CALL = Regex("""(?<![\w.])(?:tr|I18n\.inAllLanguages)\(\s*"([^"$]+)"""")
        val LITERAL = Regex(""""([A-Za-z][A-Za-z0-9]*(?:\.[A-Za-z0-9]+)+)"""")
        val MARKUP = Regex("<[^>]+>")
        val KEPT_TERMS = listOf("BananaShot", "4K", "1080p", "H\\.264", "HEVC", "NVENC", "x264", "fps", "FPS", "PNG", "\\{app\\}")
            .map { Regex("(?<![A-Za-z0-9])$it(?![A-Za-z0-9])") }
    }
}
