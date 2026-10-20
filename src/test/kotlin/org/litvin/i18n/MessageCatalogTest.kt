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

    /** The table "Words of each variant" of `docs/localization/glossary.md`. */
    @Test
    fun `Latin American Spanish has no words of Spain`() {
        val texts = MessageCatalog.loadWithParents(AppLanguage.SPANISH_LATIN_AMERICA)
        val failures = texts.mapNotNull { (key, text) ->
            SPAIN_WORDS.find(text)?.let { "$key: ${it.value}" }
        }
        assertEquals(emptyList<String>(), failures.sorted(), "Add a Latin American text to *_es_419.properties")
    }

    /** The table "Words of each variant" of the Portuguese section of `docs/localization/glossary.md`. */
    @Test
    fun `Portuguese of Portugal has no words of Brazil`() {
        val texts = MessageCatalog.loadWithParents(AppLanguage.PORTUGUESE_PORTUGAL)
        val failures = texts.mapNotNull { (key, text) ->
            BRAZIL_WORDS.find(text.replace(PLACEHOLDER_NAME, "{"))?.let { "$key: ${it.value}" }
        }
        assertEquals(emptyList<String>(), failures.sorted(), "Add a Portugal text to *_pt_PT.properties")
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

    /**
     * B-11 E7-T2: a plural has all categories of its language, and no category that the language does not have. A
     * plural with only `other` is correct when the text does not change with the number. An exact case (`=0`) is
     * not a category.
     */
    @Test
    fun `each plural has the categories of its language`() {
        val failures = (listOf(AppLanguage.SOURCE to english) + translations.toList()).flatMap { (language, texts) -> pluralProblems(language, texts) }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `the plural check finds a missing and an extra category`() {
        val russian = pluralProblems(AppLanguage.RUSSIAN, mapOf("a" to "{count, plural, one {# очко} other {# очков}}"))
        assertEquals(listOf("ru a: missing [few, many]"), russian)
        val spanish = pluralProblems(AppLanguage.SPANISH_SPAIN, mapOf("a" to "{count, plural, one {# punto} few {# puntos} other {# puntos}}"))
        assertEquals(listOf("es a: not in the language [few]"), spanish)
        assertEquals(emptyList<String>(), pluralProblems(AppLanguage.RUSSIAN, mapOf("a" to "{count, plural, other {в очереди}}")))
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
        assertEquals("Videos de tenis y pádel", latinAmerica.text("app.tagline"))
        assertEquals("Enter", latinAmerica.text("key.enter"))
        assertEquals("Exportaciones", latinAmerica.text("export.table.title"))
        assertEquals("Vídeos de tenis y pádel", Messages.of(AppLanguage.SPANISH_SPAIN).text("app.tagline"))
    }

    @Test
    fun `the Russian catalog has texts in Russian`() {
        val russian = Messages.of(AppLanguage.RUSSIAN)
        assertEquals("Экспорты", russian.text("export.table.title"))
        assertEquals("1 розыгрыш", russian.text("export.points", mapOf("count" to 1)))
        assertEquals("3 розыгрыша", russian.text("export.points", mapOf("count" to 3)))
        assertEquals("5 розыгрышей", russian.text("export.points", mapOf("count" to 5)))
        assertEquals("Пара 1", russian.text("sport.padel.side", mapOf("number" to 1)))
        assertTrue(russian.text("help.points.summary").startsWith("Отметьте"))
    }

    @Test
    fun `the Portuguese catalogs have texts of Brazil and of Portugal`() {
        val brazil = Messages.of(AppLanguage.PORTUGUESE_BRAZIL)
        assertEquals("Exportações", brazil.text("export.table.title"))
        assertEquals("1 ponto", brazil.text("export.points", mapOf("count" to 1)))
        assertEquals("3 pontos", brazil.text("export.points", mapOf("count" to 3)))
        assertEquals("Dupla 1", brazil.text("sport.padel.side", mapOf("number" to 1)))
        assertEquals("Estilo do placar", brazil.text("points.scoreboardStyle"))
        assertTrue(brazil.text("help.points.summary").startsWith("Marque"))
        val portugal = Messages.of(AppLanguage.PORTUGUESE_PORTUGAL)
        assertEquals("Estilo do marcador", portugal.text("points.scoreboardStyle"))
        assertEquals("Guardar", portugal.text("common.save"))
        assertEquals("Exportações", portugal.text("export.table.title"))
    }

    @Test
    fun `the French catalog has texts in French`() {
        val french = Messages.of(AppLanguage.FRENCH)
        assertEquals("Exports", french.text("export.table.title"))
        assertEquals("0 point", french.text("export.points", mapOf("count" to 0)))
        assertEquals("3 points", french.text("export.points", mapOf("count" to 3)))
        assertEquals("Équipe 1", french.text("sport.padel.side", mapOf("number" to 1)))
        assertEquals("Supprimer le projet «${NBSP}X${NBSP}»${NBSP}?", french.text("projects.delete.question", mapOf("project" to "X")))
        assertTrue(french.text("help.points.summary").startsWith("Marquez"))
    }

    /**
     * B-11 E7-L-fr: French has a no-break space before ":", ";", "?", "!", and "%", and inside « … ». A normal space
     * can break the line before the mark. A straight apostrophe before a placeholder starts an ICU quote, so French uses
     * the typographic apostrophe.
     */
    @Test
    fun `French texts use the French spacing and apostrophe`() {
        assertEquals(emptyList<String>(), frenchTypographyProblems(MessageCatalog.loadWithParents(AppLanguage.FRENCH)))
    }

    @Test
    fun `the French spacing check finds a normal space, a missing space, and a straight apostrophe`() {
        val problems = frenchTypographyProblems(
            mapOf(
                "a" to "Erreur : {detail}", "b" to "Supprimer ?", "c" to "« Auto »", "d" to "Merci!",
                "e" to "l'app", "f" to "Erreur${NBSP}: {detail}", "g" to "hh:mm:ss",
            ),
        )
        assertEquals(listOf("a", "b", "c", "d", "e"), problems.map { it.substringBefore(':') })
    }

    private fun frenchTypographyProblems(texts: Map<String, String>): List<String> = texts.toSortedMap().mapNotNull { (key, text) ->
        FRENCH_SPACING.find(text)?.let { "$key: «${it.value}»" }
    }

    private fun pluralProblems(language: AppLanguage, texts: Map<String, String>): List<String> {
        val required = PluralRules.categories(language)
        return texts.toSortedMap().flatMap { (key, text) ->
            MessagePattern.parse(text).pluralCases.mapNotNull { cases ->
                val categories = cases.filterNot { it.startsWith("=") }.toSet()
                when {
                    categories == setOf(MessagePattern.OTHER) -> null
                    categories - required != emptySet<String>() -> "${language.code} $key: not in the language ${(categories - required).sorted()}"
                    required - categories != emptySet<String>() -> "${language.code} $key: missing ${(required - categories).sorted()}"
                    else -> null
                }
            }
        }
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
        val SPAIN_WORDS = Regex(
            "(?<![\\p{L}])(vídeos?|ordenador(es)?|pulsa|pulsada|pulsar|Intro|ratón|móvil|caducado|caduca|en directo)(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )
        val PLACEHOLDER_NAME = Regex("\\{\\w+")

        /** The no-break space of French typography. */
        val NBSP = Char(0xA0)

        /** A normal space before a high mark or after «, no space before ";", "?", "!", or », or a straight apostrophe. */
        val FRENCH_SPACING = Regex(" [:;?!%»]|« |[^\\s\\u00A0][;?!»]|«[^\\u00A0]|'")
        val BRAZIL_WORDS = Regex(
            "(?<![\\p{L}])(tela|arquivos?|placar(es)?|salvar|baixar|excluir|usuários?|configurações|pressione|celular|esportes?|tênis|abas?|games?|saque|você)(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )
        val KEPT_TERMS = listOf("BananaShot", "4K", "1080p", "H\\.264", "HEVC", "NVENC", "x264", "fps", "FPS", "PNG", "\\{app\\}")
            .map { Regex("(?<![A-Za-z0-9])$it(?![A-Za-z0-9])") }
    }
}
