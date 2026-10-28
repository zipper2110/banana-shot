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
        val failures = translations.flatMap { (language, texts) -> keyProblems(language, texts) }
        assertEquals(emptyList<String>(), failures)
    }

    /**
     * B-11 E4-T5: a machine translation often breaks a message. `catalog-check/messages_es.properties` has one
     * typical error in each key, and the checks of this class must find each error.
     */
    @Test
    fun `the catalog checks find each error of a machine translation`() {
        val source = testCatalog("catalog-check/messages.properties")
        val draft = testCatalog("catalog-check/messages_es.properties")
        val problems = catalogProblems(AppLanguage.SPANISH_SPAIN, draft, source)
        val keys = problems.map { it.substringAfter(' ').substringBefore(':') }.toSortedSet()
        assertEquals((source.keys + draft.keys - "correct.text").toSortedSet(), keys, problems.joinToString("\n"))
        assertTrue(problems.any { "uno" in it }, "The plural check names the translated keyword")
        assertTrue(problems.any { "BananaShot" in it } && problems.any { "fps" in it }, "The term check names the terms")
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
        val failures = (listOf(AppLanguage.SOURCE to english) + translations.toList()).flatMap { (language, texts) -> parseProblems(language, texts) }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `a translation has the same placeholders as English`() {
        val failures = translations.flatMap { (language, texts) -> placeholderProblems(language, texts) }
        assertEquals(emptyList<String>(), failures)
    }

    /** The "Do not translate" list of `docs/localization/glossary.md`. */
    @Test
    fun `a translation keeps the terms that are not translated`() {
        val failures = translations.flatMap { (language, texts) -> keptTermProblems(language, texts) }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `a translation keeps the markup`() {
        val failures = translations.flatMap { (language, texts) -> markupProblems(language, texts) }
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

    @Test
    fun `the German catalog has texts in German`() {
        val german = Messages.of(AppLanguage.GERMAN)
        assertEquals("Exporte", german.text("export.table.title"))
        assertEquals("0 Punkte", german.text("export.points", mapOf("count" to 0)))
        assertEquals("1 Punkt", german.text("export.points", mapOf("count" to 1)))
        assertEquals("Team 1", german.text("sport.padel.side", mapOf("number" to 1)))
        assertEquals("Projekt „X“ löschen?", german.text("projects.delete.question", mapOf("project" to "X")))
        assertTrue(german.text("help.points.summary").startsWith("Markiere"))
    }

    /**
     * B-11 E7-L-de: German uses „…“ quotes, not the English or the French quotes. A straight apostrophe before a
     * placeholder starts an ICU quote.
     */
    @Test
    fun `German texts use the German quotes`() {
        val failures = MessageCatalog.loadWithParents(AppLanguage.GERMAN).toSortedMap().mapNotNull { (key, text) ->
            GERMAN_QUOTES.find(text)?.let { "$key: ${it.value}" }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `the Japanese catalog has texts in Japanese`() {
        val japanese = Messages.of(AppLanguage.JAPANESE)
        assertEquals("エクスポート", japanese.text("export.table.title"))
        assertEquals("1 ポイント", japanese.text("export.points", mapOf("count" to 1)))
        assertEquals("3 ポイント", japanese.text("export.points", mapOf("count" to 3)))
        assertEquals("ペア1", japanese.text("sport.padel.side", mapOf("number" to 1)))
        assertEquals("プロジェクト「X」を削除しますか？", japanese.text("projects.delete.question", mapOf("project" to "X")))
        assertTrue(japanese.text("help.points.summary").startsWith("試合の各ポイント"))
    }

    /**
     * B-11 E8-L-ja: Japanese uses 「…」 quotes. A straight apostrophe before a placeholder starts an ICU quote.
     */
    @Test
    fun `Japanese texts use the Japanese quotes`() {
        val failures = MessageCatalog.loadWithParents(AppLanguage.JAPANESE).toSortedMap().mapNotNull { (key, text) ->
            JAPANESE_QUOTES.find(text)?.let { "$key: ${it.value}" }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `the Chinese catalogs have texts in Simplified and in Traditional Chinese`() {
        val simplified = Messages.of(AppLanguage.CHINESE_SIMPLIFIED)
        assertEquals("导出", simplified.text("export.table.title"))
        assertEquals("1 个回合", simplified.text("export.points", mapOf("count" to 1)))
        assertEquals("3 个回合", simplified.text("export.points", mapOf("count" to 3)))
        assertEquals("组合 1", simplified.text("sport.padel.side", mapOf("number" to 1)))
        assertEquals("要删除项目“X”吗？", simplified.text("projects.delete.question", mapOf("project" to "X")))
        assertTrue(simplified.text("help.points.summary").startsWith("标记比赛的每个回合"))
        val traditional = Messages.of(AppLanguage.CHINESE_TRADITIONAL)
        assertEquals("匯出", traditional.text("export.table.title"))
        assertEquals("3 個回合", traditional.text("export.points", mapOf("count" to 3)))
        assertEquals("組合 1", traditional.text("sport.padel.side", mapOf("number" to 1)))
        assertEquals("要刪除專案「X」嗎？", traditional.text("projects.delete.question", mapOf("project" to "X")))
        assertTrue(traditional.text("help.points.summary").startsWith("標記比賽的每個回合"))
    }

    /**
     * B-11 E8-L-zh-Hans and E8-L-zh-Hant: Simplified Chinese uses “…” quotes, and Traditional Chinese uses 「…」. A
     * straight apostrophe before a placeholder starts an ICU quote.
     */
    @Test
    fun `Chinese texts use the quotes of their script`() {
        val failures = mapOf(AppLanguage.CHINESE_SIMPLIFIED to SIMPLIFIED_CHINESE_QUOTES, AppLanguage.CHINESE_TRADITIONAL to JAPANESE_QUOTES)
            .flatMap { (language, quotes) ->
                MessageCatalog.loadWithParents(language).toSortedMap().mapNotNull { (key, text) ->
                    quotes.find(text)?.let { "${language.code} $key: ${it.value}" }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    /**
     * B-11 E8-L-zh-Hant: the Traditional texts use the Taiwan terms, not the mainland terms in traditional characters
     * (for example "影片", not "視頻"). A conversion of only the characters gives the mainland terms.
     */
    @Test
    fun `Traditional Chinese uses the Taiwan terms`() {
        val failures = MessageCatalog.loadWithParents(AppLanguage.CHINESE_TRADITIONAL).toSortedMap().mapNotNull { (key, text) ->
            MAINLAND_TERMS.find(text)?.let { "$key: ${it.value}" }
        }
        assertEquals(emptyList<String>(), failures)
    }

    /** B-11 E8-L-zh-Hant: a Traditional character in a Simplified text, or the reverse, shows that a text is wrong. */
    @Test
    fun `each Chinese catalog uses only the characters of its script`() {
        val simplified = MessageCatalog.loadWithParents(AppLanguage.CHINESE_SIMPLIFIED).toSortedMap().mapNotNull { (key, text) ->
            text.filter { it in TRADITIONAL_ONLY }.takeIf { it.isNotEmpty() }?.let { "zh-Hans $key: $it" }
        }
        val traditional = MessageCatalog.loadWithParents(AppLanguage.CHINESE_TRADITIONAL).toSortedMap().mapNotNull { (key, text) ->
            text.filter { it in SIMPLIFIED_ONLY }.takeIf { it.isNotEmpty() }?.let { "zh-Hant $key: $it" }
        }
        assertEquals(emptyList<String>(), simplified + traditional)
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

    /** B-11 E3-T20: a line never breaks between a number and its unit, so the space between them is a no-break space. */
    @Test
    fun `a number and its unit have a no-break space`() {
        val problems = (listOf(AppLanguage.SOURCE to english) + translations.toList()).flatMap { (language, texts) ->
            unitSpaceProblems(texts).map { "${language.code} $it" }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `the unit check finds a normal space and accepts a word that is not a unit`() {
        val problems = unitSpaceProblems(
            mapOf("a" to "{seconds} s", "b" to "2 MB", "c" to "{fps} fps", "d" to "{seconds}${NBSP}s", "e" to "{app} с сайта", "f" to "{count} sets"),
        )
        assertEquals(listOf("a", "b", "c"), problems.map { it.substringBefore(':') })
    }

    private fun unitSpaceProblems(texts: Map<String, String>): List<String> = texts.toSortedMap().mapNotNull { (key, text) ->
        UNIT_SPACE.find(text)?.let { "$key: «${it.value}»" }
    }

    private fun frenchTypographyProblems(texts: Map<String, String>): List<String> = texts.toSortedMap().mapNotNull { (key, text) ->
        FRENCH_SPACING.find(text)?.let { "$key: «${it.value}»" }
    }

    /**
     * All checks of one translation against [source]. Each problem starts with "<language code> <key>:". A text that
     * does not parse gets only the parse problem, because the other checks need the parsed text.
     */
    private fun catalogProblems(language: AppLanguage, texts: Map<String, String>, source: Map<String, String>): List<String> =
        keyProblems(language, texts, source) + parseProblems(language, texts) + placeholderProblems(language, texts, source) +
            keptTermProblems(language, texts, source) + markupProblems(language, texts, source) + pluralProblems(language, texts)

    private fun keyProblems(language: AppLanguage, texts: Map<String, String>, source: Map<String, String> = english): List<String> =
        (source.keys - texts.keys).sorted().map { "${language.code} $it: no text" } +
            (texts.keys - source.keys).sorted().map { "${language.code} $it: English does not have the key" }

    private fun parseProblems(language: AppLanguage, texts: Map<String, String>): List<String> = texts.toSortedMap().mapNotNull { (key, text) ->
        runCatching { MessagePattern.parse(text) }.exceptionOrNull()?.let { "${language.code} $key: ${it.message}" }
    }

    private fun placeholderProblems(language: AppLanguage, texts: Map<String, String>, source: Map<String, String> = english): List<String> =
        commonTexts(texts, source).mapNotNull { (key, sourceText, text) ->
            val expected = parsedOrNull(sourceText)?.argumentNames ?: return@mapNotNull null
            val actual = parsedOrNull(text)?.argumentNames ?: return@mapNotNull null
            if (expected == actual) null else "${language.code} $key: $actual, English: $expected"
        }

    private fun keptTermProblems(language: AppLanguage, texts: Map<String, String>, source: Map<String, String> = english): List<String> =
        commonTexts(texts, source).flatMap { (key, sourceText, text) ->
            KEPT_TERMS.filter { term -> term.containsMatchIn(sourceText) && !term.containsMatchIn(text) }
                .map { term -> "${language.code} $key: ${term.pattern}" }
        }

    private fun markupProblems(language: AppLanguage, texts: Map<String, String>, source: Map<String, String> = english): List<String> =
        commonTexts(texts, source).mapNotNull { (key, sourceText, text) ->
            val expected = MARKUP.findAll(sourceText).map { it.value }.sorted().toList()
            val actual = MARKUP.findAll(text).map { it.value }.sorted().toList()
            if (expected == actual) null else "${language.code} $key: $actual, English: $expected"
        }

    /** The key, the source text, and the translation of each key that both catalogs have, in the order of the keys. */
    private fun commonTexts(texts: Map<String, String>, source: Map<String, String>): List<Triple<String, String, String>> =
        texts.toSortedMap().mapNotNull { (key, text) -> source[key]?.let { Triple(key, it, text) } }

    private fun parsedOrNull(text: String): MessagePattern? = runCatching { MessagePattern.parse(text) }.getOrNull()

    private fun testCatalog(path: String): Map<String, String> =
        MessageCatalog.parse(requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing $path" }.use { it.readBytes() })

    private fun pluralProblems(language: AppLanguage, texts: Map<String, String>): List<String> {
        val required = PluralRules.categories(language)
        return texts.toSortedMap().flatMap { (key, text) ->
            parsedOrNull(text)?.pluralCases.orEmpty().mapNotNull { cases ->
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

        /** A normal space between a number or a placeholder (not `{app}`) and a unit. */
        val UNIT_SPACE = Regex("(?:\\d|(?<!\\{app)\\}) (?:s|ms|fps|GB|MB|KB|B|%|с|мс|秒)(?![\\p{L}\\d/])")

        /** A normal space before a high mark or after «, no space before ";", "?", "!", or », or a straight apostrophe. */
        val FRENCH_SPACING = Regex(" [:;?!%»]|« |[^\\s\\u00A0][;?!»]|«[^\\u00A0]|'")
        /** A quote that German does not use: straight, English opening, French, or a straight apostrophe. */
        val GERMAN_QUOTES = Regex("[\"”«»']")
        /** A quote that Japanese does not use: straight, English, French, or German, or a straight apostrophe. */
        val JAPANESE_QUOTES = Regex("[\"“”«»„']")
        /** A quote that Simplified Chinese does not use: straight, Japanese, French, or German, or a straight apostrophe. */
        val SIMPLIFIED_CHINESE_QUOTES = Regex("[\"「」『』«»„']")
        /**
         * Mainland terms in traditional characters. The Taiwan terms are 影片, 專案, 匯出, 匯入, 預設, 資訊, 螢幕, 滑鼠, 軟體,
         * 網路, 品質, 解析度, 位元速率, and 影格. "項目" alone is also correct in Taiwan for an item or an entry ("統計項目"),
         * so only the phrases with "project" count.
         */
        val MAINLAND_TERMS = Regex("視頻|新項目|項目名稱|打開項目|當前項目|最近的項目|導出|導入|默認|信息|屏幕|鼠標|軟件|網絡|質量|分辨率|碼率|幀")
        /** Common characters that only Simplified Chinese uses. */
        const val SIMPLIFIED_ONLY = "个们这时档设视项导选择删录击开关应软体数据网页说话认识论边处总门问间预览颜级转换线组队变让还没对过发会"
        /** The Traditional forms of [SIMPLIFIED_ONLY], in the same order. */
        const val TRADITIONAL_ONLY = "個們這時檔設視項導選擇刪錄擊開關應軟體數據網頁說話認識論邊處總門問間預覽顏級轉換線組隊變讓還沒對過發會"
        val BRAZIL_WORDS = Regex(
            "(?<![\\p{L}])(tela|arquivos?|placar(es)?|salvar|baixar|excluir|usuários?|configurações|pressione|celular|esportes?|tênis|abas?|games?|saque|você)(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )
        val KEPT_TERMS = listOf("BananaShot", "4K", "1080p", "H\\.264", "HEVC", "NVENC", "x264", "fps", "FPS", "PNG", "\\{app\\}")
            .map { Regex("(?<![A-Za-z0-9])$it(?![A-Za-z0-9])") }
    }
}
