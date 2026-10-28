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
    fun `a placeholder value shows as given and does not change the message`() {
        val messages = Messages(AppLanguage.ENGLISH, emptyMap(), mapOf("a" to "Set won by {playerName}."))
        assertEquals("Set won by {x} 'y' <b>z</b>.", messages.text("a", mapOf("playerName" to "{x} 'y' <b>z</b>")))
    }

    @Test
    fun `each English text formats in the test language with letters that the video fonts show`() {
        val english = MessageCatalog.load(AppLanguage.SOURCE)
        val pseudo = Messages(AppLanguage.ENGLISH, english, english, pseudo = true)
        english.keys.forEach { key ->
            val pattern = MessagePattern.parse(english.getValue(key))
            val args = pattern.argumentNames.associateWith { 1 }
            val text = pseudo.text(key, args)
            assertTrue(text.isBlank() || text.startsWith("<html>") || (text.startsWith("[") && text.endsWith("]")), "$key: $text")
            if (key.startsWith("video.")) {
                assertTrue(text.filter { it.isLetter() }.all { it.code < 0x180 }, "$key: $text")
            }
        }
    }

    @Test
    fun `a byte order mark does not change the first key`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "first=One\nsecond=Two\n".toByteArray()
        assertEquals(mapOf("first" to "One", "second" to "Two"), MessageCatalog.parse(bytes))
    }

    @Test
    fun `a number in a plural uses the regional format and the plural of the app language`() {
        val saved = DisplayFormats.formatLocale
        try {
            val english = mapOf("a" to "{count, plural, one {# point} other {# points}}")
            val messages = Messages(AppLanguage.ENGLISH, emptyMap(), english)
            DisplayFormats.formatLocale = java.util.Locale.forLanguageTag("de-DE")
            assertEquals("1.234 points", messages.text("a", mapOf("count" to 1234)))
            DisplayFormats.formatLocale = java.util.Locale.forLanguageTag("ar-EG")
            val arabicRegion = messages.text("a", mapOf("count" to 1234))
            assertTrue(arabicRegion.filter { it.isDigit() }.all { it in '0'..'9' }, arabicRegion)
            assertTrue(arabicRegion.endsWith(" points"), arabicRegion)
        } finally {
            DisplayFormats.formatLocale = saved
        }
    }

    @Test
    fun `a saved default text is known in all languages`() {
        assertEquals(setOf("Player 1", "Jugador 1", "Игрок 1", "Jogador 1", "Joueur 1", "Spieler 1", "プレイヤー1", "球员 1", "球員 1"), I18n.inAllLanguages("sport.tennis.side", "number" to 1))
    }
}
