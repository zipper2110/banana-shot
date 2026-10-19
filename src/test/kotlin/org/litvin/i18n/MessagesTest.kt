package org.litvin.i18n

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessagesTest {
    @AfterEach
    fun restore() {
        I18n.install(Messages.english())
    }

    @Test
    fun `a missing or broken translation uses the English text`() {
        val english = mapOf("a" to "Apple", "b" to "{count} items", "c" to "Cherry")
        val messages = Messages(AppLanguage.SPANISH_SPAIN, mapOf("a" to "Manzana", "b" to "{count, plural, one {#}"), english)
        assertEquals("Manzana", messages.text("a"))
        assertEquals("3 items", messages.text("b", mapOf("count" to 3)))
        assertEquals("Cherry", messages.text("c"))
    }

    @Test
    fun `a key that English does not have shows the key`() {
        val messages = Messages(AppLanguage.ENGLISH, emptyMap(), emptyMap())
        assertEquals("no.such.key", messages.text("no.such.key"))
    }

    @Test
    fun `the test language changes the letters and adds brackets`() {
        val messages = Messages(AppLanguage.ENGLISH, mapOf("a" to "Export {name}"), mapOf("a" to "Export {name}"), pseudo = true)
        val text = messages.text("a", mapOf("name" to "x.mp4"))
        assertTrue(text.startsWith("[Éxpôŕţ x.mp4 "), text)
        assertTrue(text.endsWith("~]"), text)
    }

    @Test
    fun `tr uses the installed language`() {
        assertEquals("Exports", tr("export.table.title"))
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        assertEquals("Exportaciones", tr("export.table.title"))
        assertEquals(AppLanguage.SPANISH_SPAIN, I18n.language)
    }

    @Test
    fun `a saved default text is known in all languages`() {
        assertEquals(setOf("Player 1", "Jugador 1"), I18n.inAllLanguages("sport.tennis.side", "number" to 1))
    }
}
