package org.litvin.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.litvin.i18n.TranslationCheck.Reason
import org.litvin.i18n.TranslationCheck.Renamed
import org.litvin.i18n.TranslationCheck.Stale
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/** B-11 E4-T3: the check of the missing and the stale translations, with the English and the Spanish files. */
class TranslationCheckTest {
    private val englishFiles = TranslationCheck.files(RESOURCES, AppLanguage.SOURCE).map { it.readText(Charsets.UTF_8) }
    private val english = TranslationCheck.texts(englishFiles)

    /** The Spanish files with the records of the current English texts. Thus the test does not depend on the records in the repository. */
    private val spanishFiles = TranslationCheck.files(RESOURCES, AppLanguage.SPANISH_SPAIN).map { file ->
        TranslationCheck.stamp(file.readText(Charsets.UTF_8).replace("\r\n", "\n"), english) { true }
    }

    private fun check(english: Map<String, String> = this.english, spanish: List<String> = spanishFiles) =
        TranslationCheck.check(AppLanguage.SPANISH_SPAIN, english, spanish)

    private fun List<String>.edit(change: (String) -> String): List<String> {
        val changed = map(change)
        assertTrue(changed != this, "The change did not apply")
        return changed
    }

    @Test
    fun `Spanish with the records of the current English texts is clean`() {
        val report = check()
        assertTrue(report.isClean, "$report")
    }

    @Test
    fun `a changed English text makes the key stale`() {
        val report = check(english = english + ("common.save" to "Save now"))
        assertEquals(listOf(Stale("common.save", Reason.ENGLISH_CHANGED)), report.stale)
        assertEquals(emptyList<String>(), report.missing)
    }

    @Test
    fun `a change of only a space or a punctuation mark makes the key stale`() {
        val text = english.getValue("dialog.reportProblem")
        assertEquals(listOf("dialog.reportProblem"), check(english = english + ("dialog.reportProblem" to "$text ")).stale.map { it.key })
        assertEquals(listOf("dialog.reportProblem"), check(english = english + ("dialog.reportProblem" to "$text.")).stale.map { it.key })
    }

    @Test
    fun `a changed English help text makes the key stale`() {
        val report = check(english = english + ("help.overview.summary" to "A new summary."))
        assertEquals(listOf(Stale("help.overview.summary", Reason.ENGLISH_CHANGED)), report.stale)
    }

    @Test
    fun `a removed Spanish key is missing`() {
        val report = check(spanish = spanishFiles.edit { it.replace(Regex("""(?m)^common\.save=.*\R"""), "") })
        assertEquals(listOf("common.save"), report.missing)
        assertEquals(emptyList<Stale>(), report.stale)
    }

    @Test
    fun `a removed record makes the key stale`() {
        val record = TranslationCheck.record(english.getValue("common.save"))
        val report = check(spanish = spanishFiles.edit { it.replace("$record\ncommon.save=", "common.save=") })
        assertEquals(listOf(Stale("common.save", Reason.NO_RECORD)), report.stale)
    }

    @Test
    fun `an empty Spanish value is missing, not stale`() {
        val report = check(spanish = spanishFiles.edit { it.replace(Regex("""(?m)^common\.save=.*$"""), "common.save=") })
        assertEquals(listOf("common.save"), report.missing)
        assertEquals(emptyList<Stale>(), report.stale)
    }

    @Test
    fun `a record with a wrong form makes the key stale and does not stop the check`() {
        val record = TranslationCheck.record(english.getValue("common.save"))
        val cut = record.dropLast(2)
        val report = check(spanish = spanishFiles.edit { it.replace("$record\ncommon.save=", "$cut\ncommon.save=") })
        assertEquals(listOf(Stale("common.save", Reason.BAD_RECORD, cut)), report.stale)
        val upperCase = check(spanish = spanishFiles.edit { it.replace("$record\ncommon.save=", "${record.uppercase().replace("#@EN", "#@en")}\ncommon.save=") })
        assertEquals(listOf(Reason.BAD_RECORD), upperCase.stale.map { it.reason })
    }

    @Test
    fun `a renamed English key shows the old key and the new key`() {
        val renamedEnglish = english - "common.save" + ("common.saveNow" to english.getValue("common.save"))
        val report = check(english = renamedEnglish)
        assertEquals(listOf(Renamed("common.save", "common.saveNow")), report.renamed)
        assertEquals(listOf("common.saveNow"), report.missing)
        assertEquals(emptyList<String>(), report.notInEnglish)
    }

    @Test
    fun `a removed English key is not in English`() {
        val report = check(english = english - "common.save")
        assertEquals(listOf("common.save"), report.notInEnglish)
        assertEquals(emptyList<Renamed>(), report.renamed)
    }

    @Test
    fun `a comment between the record and the key keeps the record`() {
        val record = TranslationCheck.record(english.getValue("common.save"))
        val report = check(spanish = spanishFiles.edit { it.replace("$record\ncommon.save=", "$record\n# Un comentario.\n\ncommon.save=") })
        assertTrue(report.isClean, "$report")
    }

    @Test
    fun `a regional variant gets its missing texts from the parent and checks only its own keys`() {
        val own = TranslationCheck.files(RESOURCES, AppLanguage.SPANISH_LATIN_AMERICA).map { file ->
            TranslationCheck.stamp(file.readText(Charsets.UTF_8), english) { true }
        }
        val parent = TranslationCheck.texts(spanishFiles)
        assertTrue(TranslationCheck.check(AppLanguage.SPANISH_LATIN_AMERICA, english, own, parent).isClean)
        val report = TranslationCheck.check(AppLanguage.SPANISH_LATIN_AMERICA, english + ("app.tagline" to "Tennis videos"), own, parent - "common.save")
        assertEquals(listOf("common.save"), report.missing)
        assertEquals(listOf(Stale("app.tagline", Reason.ENGLISH_CHANGED)), report.stale)
    }

    @Test
    fun `stamp replaces an old record, adds a missing record, and keeps the other lines`() {
        val content = "# Title\r\n\r\n#@en=00000000\r\na=Uno\r\n# Comment\r\nb = Dos\r\nc=Tres \\\r\n  continued\r\nunknown=X\r\n"
        val texts = mapOf("a" to "One", "b" to "Two", "c" to "Three continued")
        val stamped = TranslationCheck.stamp(content, texts) { true }
        val expected = "# Title\r\n\r\n${TranslationCheck.record("One")}\r\na=Uno\r\n# Comment\r\n${TranslationCheck.record("Two")}\r\n" +
            "b = Dos\r\n${TranslationCheck.record("Three continued")}\r\nc=Tres \\\r\n  continued\r\nunknown=X\r\n"
        assertEquals(expected, stamped)
        assertEquals(stamped, TranslationCheck.stamp(stamped, texts) { true })
        assertEquals(content, TranslationCheck.stamp(content, texts) { it == "x" })
    }

    @Test
    fun `stamp keeps a byte order mark at the start`() {
        val stamped = TranslationCheck.stamp("﻿a=Uno\n", mapOf("a" to "One")) { true }
        assertEquals("﻿${TranslationCheck.record("One")}\na=Uno\n", stamped)
        assertEquals(listOf("a"), TranslationCheck.keyLines(stamped.lines()).map { it.key })
    }

    @Test
    fun `the release check stops when a language has missing keys`(@TempDir folder: File) {
        val i18n = File(folder, "i18n").apply { mkdirs() }
        File(i18n, "messages.properties").writeText("a=One\nb=Two\n")
        File(i18n, "help.properties").writeText("help.a=Help\n")
        File(i18n, "messages_es.properties").writeText("a=Uno\n")
        val out = ByteArrayOutputStream()
        val code = TranslationCheckMain.run(listOf("--fail-on-missing", "--resources=$folder"), PrintStream(out, true, Charsets.UTF_8))
        assertEquals(1, code)
        assertTrue(out.toString(Charsets.UTF_8).contains("| es | 2 | 1 | 0 | 0 |"), out.toString(Charsets.UTF_8))
        assertEquals(0, TranslationCheckMain.run(listOf("--resources=$folder"), PrintStream(ByteArrayOutputStream())))
    }

    @Test
    fun `the stamp option writes the records of the given keys only`(@TempDir folder: File) {
        val i18n = File(folder, "i18n").apply { mkdirs() }
        File(i18n, "messages.properties").writeText("a=One\nb=Two\n")
        val spanish = File(i18n, "messages_es.properties").apply { writeText("a=Uno\nb=Dos\n") }
        val quiet = PrintStream(ByteArrayOutputStream())
        assertEquals(2, TranslationCheckMain.run(listOf("--stamp=es", "--resources=$folder"), quiet))
        assertEquals(0, TranslationCheckMain.run(listOf("--stamp=es", "--keys=a", "--resources=$folder"), quiet))
        assertEquals("${TranslationCheck.record("One")}\na=Uno\nb=Dos\n", spanish.readText())
    }

    private companion object {
        val RESOURCES = File("src/main/resources")
    }
}
