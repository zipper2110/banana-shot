package org.litvin.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-11 E1-T4: a format call without a locale uses the Windows regional format. The scan reads the main sources and
 * finds such calls: `"…".format(…)`, `String.format(…)`, `toUpperCase()`, `toLowerCase()`, `DateTimeFormatter.ofPattern`
 * and `ofLocalized…` without `withLocale`, `NumberFormat`, `DecimalFormat`, `DecimalFormatSymbols`, `SimpleDateFormat`,
 * `java.text.MessageFormat`, `Collator.getInstance()`, `printf`, and `java.util.Formatter`. It also finds
 * `Locale.getDefault` outside of the `i18n` package, so that `String.format(Locale.getDefault(), …)` cannot hide the
 * default locale. The Kotlin `uppercase()` and `lowercase()` use `Locale.ROOT`, so the scan accepts them.
 *
 * A machine text uses [MachineFormats], and a text for the user uses [DisplayFormats]. The accepted calls are in the
 * allowlist `i18n/default-locale-calls.txt`, one "path | code | reason" line each.
 */
class DefaultLocaleScanTest {
    @Test
    fun `the app has no call with the default locale that is not in the allowlist`() {
        val root = File("src/main/kotlin/org/litvin")
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                findings(file.readText(Charsets.UTF_8), isI18n = path.startsWith("i18n/")).map { (line, rule, code) -> Triple(path, line, rule) to code }
            }
            .toList()
        val allowed = allowlist()

        val calls = found.filter { (place, code) -> (place.first to code) !in allowed }
            .map { (place, code) -> "${place.first}:${place.second}: ${place.third}: $code" }
        assertEquals(emptyList(), calls, "Give these calls a locale (MachineFormats or DisplayFormats), or put them into the allowlist with a reason")
        val stale = allowed - found.map { (place, code) -> place.first to code }.toSet()
        assertEquals(emptySet(), stale, "Remove these entries from the allowlist: the code has no such call now")
    }

    @Test
    fun `the scan finds each call with the default locale`() {
        val source = """
            val a = "%.2f".format(value)
            val b = String.format("%02d", n)
            val c = name.toUpperCase()
            val d = name.toLowerCase()
            val e = DateTimeFormatter.ofPattern("d MMM yyyy")
            val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            val g = NumberFormat.getInstance()
            val h = DecimalFormat("0.00")
            val i = DecimalFormatSymbols()
            val j = SimpleDateFormat("yyyy-MM-dd")
            val k = java.text.MessageFormat.format("{0}", x)
            val l = Collator.getInstance()
            out.printf("%d", n)
            val m = java.util.Formatter()
            val n = String.format(Locale.getDefault(), "%d", n)
            // val o = "%d".format(n)
            val p = "%.2f".format(Locale.ROOT, value)
            val q = String.format(Locale.US, "%02d", n)
            val r = name.uppercase()
            val s = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
            val t = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
            val u = NumberFormat.getInstance(locale)
            val v = MachineFormats.format("%02d", n)
            val w = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        """.trimIndent()

        assertEquals(
            listOf(
                1 to "\"…\".format without a locale",
                2 to "String.format without a locale",
                3 to "toUpperCase()",
                4 to "toLowerCase()",
                5 to "DateTimeFormatter.ofPattern without a locale",
                6 to "ofLocalized… without withLocale",
                7 to "NumberFormat without a locale",
                8 to "DecimalFormat without symbols",
                9 to "DecimalFormatSymbols without a locale",
                10 to "SimpleDateFormat without a locale",
                11 to "java.text.MessageFormat",
                12 to "Collator.getInstance()",
                13 to "printf",
                14 to "java.util.Formatter",
                15 to "Locale.getDefault outside of i18n",
            ),
            findings(source, isI18n = false).map { it.first to it.second },
        )
        assertEquals(emptyList(), findings("val n = Locale.getDefault(Locale.Category.FORMAT)", isI18n = true))
    }

    /** The findings of [source]: the line number, the rule, and the code of the line. */
    private fun findings(source: String, isI18n: Boolean): List<Triple<Int, String, String>> =
        source.lines().flatMapIndexed { index, raw ->
            val line = raw.trim()
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") || line.startsWith("import ")) return@flatMapIndexed emptyList()
            RULES.filter { (_, rule) -> rule.containsMatchIn(line) }
                .filterNot { (name, _) -> name.startsWith("ofLocalized") && "withLocale(" in line }
                .filterNot { (name, _) -> name.startsWith("Locale.getDefault") && isI18n }
                .map { (name, _) -> Triple(index + 1, name, line) }
        }

    private fun allowlist(): Set<Pair<String, String>> {
        val text = javaClass.getResource("/i18n/default-locale-calls.txt")!!.readText(Charsets.UTF_8)
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split(" | ")
                require(parts.size == 3) { "An allowlist line is \"path | code | reason\": $line" }
                parts[0] to parts[1]
            }
            .toSet()
    }

    private companion object {
        private const val NO_LOCALE = """(?!\s*(java\.util\.)?Locale\b)"""

        val RULES = listOf(
            "\"…\".format without a locale" to Regex(""""\s*\.format\($NO_LOCALE"""),
            "String.format without a locale" to Regex("""\bString\.format\($NO_LOCALE"""),
            "toUpperCase()" to Regex("""\.toUpperCase\(\s*\)"""),
            "toLowerCase()" to Regex("""\.toLowerCase\(\s*\)"""),
            "DateTimeFormatter.ofPattern without a locale" to Regex("""\bofPattern\(\s*("[^"]*"|[\w.]+)\s*\)"""),
            "ofLocalized… without withLocale" to Regex("""\bofLocalized\w+\("""),
            "NumberFormat without a locale" to Regex("""\bNumberFormat\.get\w*Instance\(\s*\)"""),
            "DecimalFormat without symbols" to Regex("""\bDecimalFormat\(\s*("[^"]*"|[\w.]+)?\s*\)"""),
            "DecimalFormatSymbols without a locale" to Regex("""\bDecimalFormatSymbols(\.getInstance)?\(\s*\)"""),
            "SimpleDateFormat without a locale" to Regex("""\bSimpleDateFormat\(\s*("[^"]*"|[\w.]+)?\s*\)"""),
            "java.text.MessageFormat" to Regex("""\bMessageFormat(\.format)?\("""),
            "Collator.getInstance()" to Regex("""\bCollator\.getInstance\(\s*\)"""),
            "printf" to Regex("""\bprintf\("""),
            "java.util.Formatter" to Regex("""\bjava\.util\.Formatter\(|(?<![\w.])Formatter\(\s*\)"""),
            "Locale.getDefault outside of i18n" to Regex("""\bLocale\.getDefault\("""),
        )
    }
}
