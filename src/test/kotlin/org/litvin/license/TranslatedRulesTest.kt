package org.litvin.license

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** B-11 E6-T1: the rules file can have the notes, the "What's new" page, and the messages in more languages. */
class TranslatedRulesTest {
    private fun valid(json: String) = assertIs<VersionRulesParse.Valid>(VersionRulesParser.parse(json.toByteArray()))

    @AfterTest
    fun restore() {
        I18n.install(Messages.english())
    }

    /**
     * The released builds (1.2.0 and older) read this file with the same parser as before E6-T1. This test passed
     * before the parser change, so the released builds accept the new fields and keep the English texts.
     */
    @Test
    fun `a file with the translated fields keeps all English texts and rules`() {
        val result = valid(FILE)
        val latest = result.rules.latest!!
        assertEquals("1.4.0", latest.version)
        assertEquals("Faster export.", latest.notes)
        assertEquals("https://example.invalid/whats-new/1.4.0.md", latest.whatsNewUrl)
        assertEquals(
            listOf(VersionRule("2027-01-project-save", "1.2.0", "1.2.1", LocalDate.of(2027, 1, 15), "Update to 1.2.2 or later.")),
            result.rules.rules.map { it.copy(messageByLanguage = emptyMap()) },
        )
    }

    @Test
    fun `the translated fields give the text of each language, and other languages get the English text`() {
        val result = valid(FILE)
        assertEquals(emptyList(), result.problems)
        val latest = result.rules.latest!!
        val rule = result.rules.rules.single()
        assertEquals("Exportación más rápida.", latest.notesIn(AppLanguage.SPANISH_SPAIN))
        // A variant uses the text of its main language.
        assertEquals("Exportación más rápida.", latest.notesIn(AppLanguage.SPANISH_LATIN_AMERICA))
        assertEquals("Schnellerer Export.", latest.notesIn(AppLanguage.GERMAN))
        assertEquals("Faster export.", latest.notesIn(AppLanguage.ENGLISH))
        assertEquals("Faster export.", latest.notesIn(AppLanguage.FRENCH))
        assertEquals("https://example.invalid/whats-new/1.4.0.es.md", latest.whatsNewUrlOnlyIn(AppLanguage.SPANISH_SPAIN))
        assertEquals(null, latest.whatsNewUrlOnlyIn(AppLanguage.GERMAN))
        assertEquals("Actualiza a la 1.2.2 o posterior.", rule.messageIn(AppLanguage.SPANISH_SPAIN))
        assertEquals("Update to 1.2.2 or later.", rule.messageIn(AppLanguage.RUSSIAN))
    }

    @Test
    fun `the default language is the app language, and the test language qps gets the English text`() {
        val latest = valid(FILE).rules.latest!!
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        assertEquals("Exportación más rápida.", latest.notesIn())
        I18n.install(Messages.of(AppLanguage.ENGLISH, pseudo = true))
        assertEquals("Faster export.", latest.notesIn())
    }

    @Test
    fun `a bad translated field is ignored, and the English texts and all rules stay`() {
        val result = valid(
            """
            {
              "schema": 1,
              "latest": {
                "version": "1.4.0",
                "downloadUrl": "https://example.invalid/releases/latest",
                "notes": "Faster export.",
                "notesByLanguage": ["es", "Más rápido"],
                "whatsNewUrlByLanguage": {"es": "https://example.invalid/es.md"}
              },
              "rules": [
                {"id": "a", "toVersion": "1.2.1", "stopsOn": "2027-01-15", "message": "Update.",
                 "messageByLanguage": {"es": "  ", "es-ES": "Actualiza.", "ES": "Actualiza.", "qps": "[Update]",
                                       "en": "Other English", "de": 7, "fr": {"text": "Mettez à jour."}, "ru": "Обновите."}},
                {"id": "b", "toVersion": "1.2.1", "stopsOn": "2027-02-15", "messageByLanguage": {"es": "Actualiza."}}
              ]
            }
            """,
        )
        val latest = result.rules.latest!!
        assertEquals("Faster export.", latest.notesIn(AppLanguage.SPANISH_SPAIN))
        // The English page is missing, so the Spanish page is ignored.
        assertEquals(null, latest.whatsNewUrlOnlyIn(AppLanguage.SPANISH_SPAIN))
        val (a, b) = result.rules.rules
        assertEquals(mapOf(AppLanguage.RUSSIAN to "Обновите."), a.messageByLanguage)
        assertEquals("Update.", a.messageIn(AppLanguage.SPANISH_SPAIN))
        assertEquals("Update.", a.messageIn(AppLanguage.GERMAN))
        assertEquals("Update.", a.messageIn(AppLanguage.FRENCH))
        // A rule with only translated texts has no message, as before.
        assertEquals(null, b.messageIn(AppLanguage.SPANISH_SPAIN))
        val problems = result.problems.joinToString("\n")
        listOf(
            "notesByLanguage of latest", "whatsNewUrlByLanguage of latest", "messageByLanguage.es of rule 'a'",
            "messageByLanguage.es-ES", "messageByLanguage.ES", "messageByLanguage.qps", "messageByLanguage.en",
            "messageByLanguage.de", "messageByLanguage.fr", "messageByLanguage of rule 'b'",
        ).forEach { assertTrue(it in problems, "$it in\n$problems") }
        assertEquals(10, result.problems.size, problems)
    }

    companion object {
        val FILE = """
            {
              "schema": 1,
              "latest": {
                "version": "1.4.0",
                "downloadUrl": "https://example.invalid/releases/latest",
                "notes": "Faster export.",
                "notesByLanguage": {"es": "Exportación más rápida.", "de": "Schnellerer Export."},
                "whatsNewUrl": "https://example.invalid/whats-new/1.4.0.md",
                "whatsNewUrlByLanguage": {"es": "https://example.invalid/whats-new/1.4.0.es.md"}
              },
              "rules": [
                {
                  "id": "2027-01-project-save",
                  "fromVersion": "1.2.0",
                  "toVersion": "1.2.1",
                  "stopsOn": "2027-01-15",
                  "message": "Update to 1.2.2 or later.",
                  "messageByLanguage": {"es": "Actualiza a la 1.2.2 o posterior."}
                }
              ]
            }
            """
    }
}
