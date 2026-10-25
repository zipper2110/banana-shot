package org.litvin.i18n

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.UUID
import java.util.prefs.Preferences

class LanguageSettingsTest {
    private val node = Preferences.userRoot().node("/org/litvin/test/i18n/${UUID.randomUUID()}")
    private val settings = LanguageSettings(node)

    @AfterEach
    fun removeNode() {
        node.removeNode()
    }

    @Test
    fun `without a choice the app follows the Windows display language`() {
        assertNull(settings.choice)
        assertEquals(
            LanguageSettings.Resolution(AppLanguage.SPANISH_LATIN_AMERICA, LanguageSettings.Source.SYSTEM),
            settings.resolve(systemLocale = Locale.forLanguageTag("es-AR"), property = null),
        )
    }

    @Test
    fun `Spanish of Spain and Equatorial Guinea gives Spain, all other Spanish gives Latin America`() {
        assertEquals(AppLanguage.SPANISH_SPAIN, LanguageSettings.systemLanguage(Locale.forLanguageTag("es-ES")))
        assertEquals(AppLanguage.SPANISH_SPAIN, LanguageSettings.systemLanguage(Locale.forLanguageTag("es-GQ")))
        assertEquals(AppLanguage.SPANISH_SPAIN, LanguageSettings.systemLanguage(Locale.forLanguageTag("es")))
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, LanguageSettings.systemLanguage(Locale.forLanguageTag("es-MX")))
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, LanguageSettings.systemLanguage(Locale.forLanguageTag("es-US")))
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, LanguageSettings.systemLanguage(Locale.forLanguageTag("es-419")))
    }

    @Test
    fun `Russian of all countries gives Russian`() {
        listOf("ru", "ru-RU", "ru-BY", "ru-KZ", "ru-KG", "ru-MD", "ru-UA").forEach { tag ->
            assertEquals(AppLanguage.RUSSIAN, LanguageSettings.systemLanguage(Locale.forLanguageTag(tag)), tag)
        }
        assertEquals(AppLanguage.RUSSIAN, settings.resolve(Locale.US, property = "ru-RU").language)
    }

    @Test
    fun `Portuguese of Brazil gives Brazil and all other Portuguese gives Portugal`() {
        listOf("pt-BR", "pt").forEach { tag ->
            assertEquals(AppLanguage.PORTUGUESE_BRAZIL, LanguageSettings.systemLanguage(Locale.forLanguageTag(tag)), tag)
        }
        listOf("pt-PT", "pt-AO", "pt-MZ", "pt-CV", "pt-LU").forEach { tag ->
            assertEquals(AppLanguage.PORTUGUESE_PORTUGAL, LanguageSettings.systemLanguage(Locale.forLanguageTag(tag)), tag)
        }
        assertEquals(AppLanguage.PORTUGUESE_PORTUGAL, settings.resolve(Locale.US, property = "pt_PT").language)
    }

    @Test
    fun `French of all countries gives French`() {
        listOf("fr", "fr-FR", "fr-BE", "fr-CH", "fr-CA", "fr-LU", "fr-SN").forEach { tag ->
            assertEquals(AppLanguage.FRENCH, LanguageSettings.systemLanguage(Locale.forLanguageTag(tag)), tag)
        }
        assertEquals(AppLanguage.FRENCH, settings.resolve(Locale.US, property = "fr-CA").language)
    }

    @Test
    fun `German of all countries gives German`() {
        listOf("de", "de-DE", "de-AT", "de-CH", "de-LI", "de-LU", "de-BE").forEach { tag ->
            assertEquals(AppLanguage.GERMAN, LanguageSettings.systemLanguage(Locale.forLanguageTag(tag)), tag)
        }
        assertEquals(AppLanguage.GERMAN, settings.resolve(Locale.US, property = "de-AT").language)
    }

    @Test
    fun `Japanese gives Japanese`() {
        listOf("ja", "ja-JP").forEach { tag ->
            assertEquals(AppLanguage.JAPANESE, LanguageSettings.systemLanguage(Locale.forLanguageTag(tag)), tag)
        }
        assertEquals(AppLanguage.JAPANESE, settings.resolve(Locale.US, property = "ja-JP").language)
    }

    /** B-11 E8-L-zh-Hans: all Chinese without the traditional script or a traditional region is Simplified. */
    @Test
    fun `Chinese of mainland China, Singapore, and without a region gives Simplified Chinese`() {
        listOf("zh", "zh-CN", "zh-SG", "zh-Hans", "zh-Hans-CN", "zh-Hans-HK").forEach { tag ->
            assertEquals(AppLanguage.CHINESE_SIMPLIFIED, LanguageSettings.systemLanguage(Locale.forLanguageTag(tag)), tag)
        }
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, settings.resolve(Locale.US, property = "zh-CN").language)
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, settings.resolve(Locale.US, property = "zh-hans").language)
    }

    /** B-11 E8-T1 and E8-L-zh-Hant: Taiwan, Hong Kong, Macau, and the script Hant never give Simplified Chinese. */
    @Test
    fun `Chinese of Taiwan, Hong Kong, and Macau gives Traditional Chinese`() {
        listOf("zh-TW", "zh-HK", "zh-MO", "zh-Hant", "zh-Hant-TW", "zh-Hant-CN").forEach { tag ->
            assertEquals(AppLanguage.CHINESE_TRADITIONAL, LanguageSettings.systemLanguage(Locale.forLanguageTag(tag)), tag)
        }
        // Java on Windows gives the display locale as language and country, without a script.
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, LanguageSettings.systemLanguage(Locale.of("zh", "TW")))
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, settings.resolve(Locale.US, property = "zh_TW").language)
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, settings.resolve(Locale.US, property = "zh-Hant").language)
    }

    @Test
    fun `a saved Latin American choice keeps its tag`() {
        settings.save(AppLanguage.SPANISH_LATIN_AMERICA)
        assertEquals("es-419", node.get(LanguageSettings.KEY, null))
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, settings.choice)
    }

    @Test
    fun `a Windows language that the app does not have gives English`() {
        assertEquals(AppLanguage.ENGLISH, LanguageSettings.systemLanguage(Locale.forLanguageTag("it-IT")))
        assertEquals(AppLanguage.ENGLISH, LanguageSettings.systemLanguage(Locale.forLanguageTag("qps-ploc")))
        assertEquals(AppLanguage.ENGLISH, LanguageSettings.systemLanguage(Locale.ROOT))
    }

    @Test
    fun `a saved choice has priority over Windows`() {
        settings.save(AppLanguage.ENGLISH)
        assertEquals("en", node.get(LanguageSettings.KEY, null))
        val resolution = settings.resolve(systemLocale = Locale.forLanguageTag("es-ES"), property = null)
        assertEquals(LanguageSettings.Resolution(AppLanguage.ENGLISH, LanguageSettings.Source.STORED), resolution)
    }

    @Test
    fun `the system choice is saved as system`() {
        settings.save(null)
        assertEquals(LanguageSettings.SYSTEM, node.get(LanguageSettings.KEY, null))
        assertNull(settings.choice)
    }

    @Test
    fun `an unknown saved value works as system and stays saved`() {
        node.put(LanguageSettings.KEY, "eo")
        assertNull(settings.choice)
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, settings.resolve(Locale.forLanguageTag("es-MX"), property = null).language)
        assertEquals("eo", node.get(LanguageSettings.KEY, null))
    }

    @Test
    fun `the property has priority and qps switches on the test language`() {
        settings.save(AppLanguage.ENGLISH)
        assertEquals(
            LanguageSettings.Resolution(AppLanguage.SPANISH_SPAIN, LanguageSettings.Source.PROPERTY),
            settings.resolve(Locale.US, property = "es"),
        )
        assertEquals(
            LanguageSettings.Resolution(AppLanguage.ENGLISH, LanguageSettings.Source.PROPERTY, pseudo = true),
            settings.resolve(Locale.US, property = "QPS"),
        )
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, settings.resolve(Locale.US, property = "es-MX").language)
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, settings.resolve(Locale.US, property = "es_419").language)
        assertEquals(LanguageSettings.Source.STORED, settings.resolve(Locale.US, property = "xx").source)
    }

    @Test
    fun `the notice of an automatic change shows one time for each language`() {
        val automatic = LanguageSettings.Resolution(AppLanguage.SPANISH_SPAIN, LanguageSettings.Source.SYSTEM)
        assertTrue(settings.needsSystemLanguageNotice(automatic))
        settings.noticeShown(AppLanguage.SPANISH_SPAIN)
        assertFalse(settings.needsSystemLanguageNotice(automatic))
        assertFalse(settings.needsSystemLanguageNotice(LanguageSettings.Resolution(AppLanguage.SPANISH_SPAIN, LanguageSettings.Source.STORED)))
        assertFalse(settings.needsSystemLanguageNotice(LanguageSettings.Resolution(AppLanguage.ENGLISH, LanguageSettings.Source.SYSTEM)))
    }
}
