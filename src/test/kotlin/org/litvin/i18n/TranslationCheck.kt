package org.litvin.i18n

import java.io.File
import java.security.MessageDigest

/**
 * The check of the missing and the stale translations (B-11 E4-T3, plan section 13, "Changes to English texts").
 *
 * Each translated key has a record line above it: `#@en=<hash>`. The hash is the first 8 hex digits of the SHA-256
 * of the English text that the translation translates. When the English text changes (also only a space or a
 * punctuation mark), the record does not agree, and the key is stale. A stale translation stays in use. Only a
 * missing text uses the English fallback.
 *
 * - Missing: the language and its parents have no text for an English key, or the text is empty.
 * - Stale: an own key of the language has no record, a record with a wrong form, or a record of an old English text.
 * - Renamed: a key that English does not have, with the record of the English text of a missing key. Move the
 *   translation to the new key.
 * - Not in English: a key that English does not have, and that is not a renamed key.
 */
object TranslationCheck {
    /** The start of a record line. A comment line that starts with it and has a wrong form is a bad record. */
    const val RECORD_MARK = "#@en"
    const val RECORD_PREFIX = "$RECORD_MARK="

    private val RECORD_LINE = Regex("""#@en=([0-9a-f]{8})""")

    fun hash(englishText: String): String =
        MessageDigest.getInstance("SHA-256").digest(englishText.toByteArray(Charsets.UTF_8))
            .take(4).joinToString("") { "%02x".format(it) }

    fun record(englishText: String): String = RECORD_PREFIX + hash(englishText)

    enum class Reason(val text: String) {
        NO_RECORD("no record"),
        BAD_RECORD("bad record"),
        ENGLISH_CHANGED("the English text changed"),
    }

    data class Stale(val key: String, val reason: Reason, val recordLine: String? = null)

    data class Renamed(val oldKey: String, val newKey: String)

    data class Report(
        val language: AppLanguage,
        val missing: List<String>,
        val stale: List<Stale>,
        val renamed: List<Renamed>,
        val notInEnglish: List<String>,
    ) {
        val isClean: Boolean get() = missing.isEmpty() && stale.isEmpty() && renamed.isEmpty() && notInEnglish.isEmpty()
    }

    /** A key line of a catalog file, and the record line above it (only comments and empty lines between). */
    data class KeyLine(val key: String, val lineIndex: Int, val recordLine: String?, val recordLineIndex: Int?)

    /** The key lines of the [lines] of a catalog file, with the syntax of [java.util.Properties]. */
    fun keyLines(lines: List<String>): List<KeyLine> {
        val result = mutableListOf<KeyLine>()
        var recordLine: String? = null
        var recordLineIndex: Int? = null
        var index = 0
        while (index < lines.size) {
            val line = lines[index].removePrefix(BOM).trimStart()
            when {
                line.startsWith(RECORD_MARK) -> {
                    recordLine = line.trimEnd()
                    recordLineIndex = index
                }
                line.isEmpty() || line.startsWith("#") || line.startsWith("!") -> Unit
                else -> {
                    result += KeyLine(keyOf(line), index, recordLine, recordLineIndex)
                    recordLine = null
                    recordLineIndex = null
                    while (continues(lines[index]) && index + 1 < lines.size) index++
                }
            }
            index++
        }
        return result
    }

    /** The texts of the catalog files [contents]. */
    fun texts(contents: List<String>): Map<String, String> = buildMap {
        contents.forEach { putAll(MessageCatalog.parse(it.toByteArray(Charsets.UTF_8))) }
    }

    /**
     * The report of [language]. [ownFiles] are the contents of its own catalog files. [parentTexts] are the texts of
     * its parents (empty for a language without a parent).
     */
    fun check(
        language: AppLanguage,
        english: Map<String, String>,
        ownFiles: List<String>,
        parentTexts: Map<String, String> = emptyMap(),
    ): Report {
        val own = texts(ownFiles)
        val effective = parentTexts + own
        val keyLines = ownFiles.flatMap { keyLines(it.lines()) }
        val missing = english.keys.filter { effective[it].isNullOrBlank() }.sorted()
        val stale = keyLines.filter { it.key in english && !own[it.key].isNullOrBlank() }.mapNotNull { line ->
            val hash = line.recordLine?.let { RECORD_LINE.matchEntire(it)?.groupValues?.get(1) }
            when {
                line.recordLine == null -> Stale(line.key, Reason.NO_RECORD)
                hash == null -> Stale(line.key, Reason.BAD_RECORD, line.recordLine)
                hash != hash(english.getValue(line.key)) -> Stale(line.key, Reason.ENGLISH_CHANGED)
                else -> null
            }
        }.sortedBy { it.key }
        val missingByHash = missing.groupBy { hash(english.getValue(it)) }
        val extra = keyLines.filter { it.key !in english }.distinctBy { it.key }.sortedBy { it.key }
        val renamed = extra.mapNotNull { line ->
            val hash = line.recordLine?.let { RECORD_LINE.matchEntire(it)?.groupValues?.get(1) }
            missingByHash[hash]?.singleOrNull()?.let { Renamed(line.key, it) }
        }
        val notInEnglish = extra.map { it.key } - renamed.map { it.oldKey }.toSet()
        return Report(language, missing, stale, renamed, notInEnglish)
    }

    /**
     * [content] with the record of the current English text above each key that [select] accepts. A key that English
     * does not have keeps its lines. The other lines, the line breaks, and a byte order mark do not change.
     */
    fun stamp(content: String, english: Map<String, String>, select: (String) -> Boolean): String {
        val newline = if ("\r\n" in content) "\r\n" else "\n"
        val lines = content.split("\r\n", "\n")
        val replaced = mutableMapOf<Int, String>()
        val inserted = mutableMapOf<Int, String>()
        keyLines(lines).forEach { line ->
            val text = english[line.key] ?: return@forEach
            if (!select(line.key)) return@forEach
            val record = record(text)
            if (line.recordLineIndex != null) replaced[line.recordLineIndex] = record else inserted[line.lineIndex] = record
        }
        val result = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val bom = if (index == 0 && line.startsWith(BOM)) BOM else ""
            val replacement = replaced[index]
            val insertion = inserted[index]
            when {
                replacement != null -> result += bom + replacement
                insertion != null -> {
                    result += bom + insertion
                    result += line.removePrefix(bom)
                }
                else -> result += line
            }
        }
        return result.joinToString(newline)
    }

    /** The reports of all languages of the catalog files under [resources] (normally `src/main/resources`). */
    fun checkAll(resources: File): List<Report> {
        val english = texts(ownFiles(resources, AppLanguage.SOURCE))
        return AppLanguage.entries.filter { it != AppLanguage.SOURCE }.map { language ->
            val parentTexts = language.chain.drop(1).asReversed().fold(emptyMap<String, String>()) { texts, parent ->
                texts + texts(ownFiles(resources, parent))
            }
            check(language, english, ownFiles(resources, language), parentTexts)
        }
    }

    /** The own catalog files of [language] under [resources]. */
    fun files(resources: File, language: AppLanguage): List<File> =
        MessageCatalog.BASE_NAMES.map { File(resources, MessageCatalog.resourcePath(it, language)) }.filter { it.isFile }

    private fun ownFiles(resources: File, language: AppLanguage): List<String> =
        files(resources, language).map { it.readText(Charsets.UTF_8) }

    /** The report as Markdown, for the console and for the CI summary. */
    fun markdown(reports: List<Report>): String = buildString {
        appendLine("## Translation check (B-11 E4-T3)")
        appendLine()
        appendLine("A missing key uses the English text. A stale translation stays in use until a new draft updates it.")
        appendLine()
        appendLine("| Language | Missing | Stale | Renamed | Not in English |")
        appendLine("|---|---|---|---|---|")
        reports.forEach { report ->
            appendLine("| ${report.language.code} | ${report.missing.size} | ${report.stale.size} | ${report.renamed.size} | ${report.notInEnglish.size} |")
        }
        reports.filterNot { it.isClean }.forEach { report ->
            appendLine()
            appendLine("<details><summary>${report.language.code}</summary>")
            appendLine()
            section("Missing", report.missing.map { "`$it`" })
            section(
                "Stale",
                report.stale.map { stale ->
                    "`${stale.key}`: ${stale.reason.text}" + (stale.recordLine?.let { " (`$it`)" } ?: "")
                },
            )
            section("Renamed (move the translation to the new key)", report.renamed.map { "`${it.oldKey}` → `${it.newKey}`" })
            section("Not in English (remove the key, or move it to its new key)", report.notInEnglish.map { "`$it`" })
            appendLine("</details>")
        }
    }

    private fun StringBuilder.section(title: String, items: List<String>) {
        if (items.isEmpty()) return
        appendLine("$title:")
        appendLine()
        items.forEach { appendLine("- $it") }
        appendLine()
    }

    /** The key of a key line: the text up to the first `=`, `:`, or white space that has no backslash before it. */
    private fun keyOf(line: String): String = buildString {
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '\\' && index + 1 < line.length -> {
                    append(line[index + 1])
                    index++
                }
                char == '=' || char == ':' || char.isWhitespace() -> return@buildString
                else -> append(char)
            }
            index++
        }
    }

    /** True when the line ends with an odd number of backslashes, so the value continues on the next line. */
    private fun continues(line: String): Boolean = (line.length - line.trimEnd('\\').length) % 2 == 1

    private const val BOM = "﻿"
}
