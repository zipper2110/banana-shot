package org.litvin.app

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.AnalyticsLevel
import org.litvin.analytics.AnalyticsPreferences
import org.litvin.analytics.EnabledAnalytics
import org.litvin.feedback.FeedbackReport
import org.litvin.feedback.FeedbackSystemInfo
import org.litvin.feedback.FeedbackTopic
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.LanguageSettings
import org.litvin.i18n.MessageCatalog
import org.litvin.i18n.Messages
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import java.util.Locale
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-11 E4-T10: the analytics summary and the feedback report send the language of the app and the language of
 * Windows. The tests start from the language setting and the Windows locale, as the app does at its start.
 */
class LanguageValuesTest {
    private val mapper = ObjectMapper()

    @AfterTest
    fun tearDown() = I18n.install(Messages.english())

    @Test
    fun `with the app in Spanish, the summary and the feedback report send es`() {
        val values = start(systemLocale = Locale.forLanguageTag("es-ES"))
        assertEquals(mapOf("language" to "es", "os_language" to "es"), values.summary)
        assertEquals("es" to "es", values.feedback)
    }

    @Test
    fun `Latin American Spanish also sends es`() {
        val values = start(systemLocale = Locale.forLanguageTag("es-MX"))
        assertEquals(AppLanguage.SPANISH_LATIN_AMERICA, I18n.language)
        assertEquals(mapOf("language" to "es", "os_language" to "es"), values.summary)
        assertEquals("es" to "es", values.feedback)
    }

    @Test
    fun `Spanish selected on a Windows in English sends es and en`() {
        val values = start(systemLocale = Locale.US, choice = AppLanguage.SPANISH_SPAIN)
        assertEquals(mapOf("language" to "es", "os_language" to "en"), values.summary)
        assertEquals("es" to "en", values.feedback)
    }

    /** Italian is a Windows language that the app does not have. The app shows English. */
    @Test
    fun `with System and a Windows language that the app does not have, the app language is en`() {
        val values = start(systemLocale = Locale.ITALY)
        assertEquals(mapOf("language" to "en", "os_language" to "it"), values.summary)
        assertEquals("en" to "it", values.feedback)
    }

    @Test
    fun `a language from the system property sends its own code, and qps sends en`() {
        assertEquals("ru", start(systemLocale = Locale.US, property = "ru").summary["language"])
        assertEquals("en", start(systemLocale = Locale.US, property = "qps").summary["language"])
    }

    @Test
    fun `Spanish with missing texts still sends es`() {
        I18n.install(Messages(AppLanguage.SPANISH_SPAIN, emptyMap(), MessageCatalog.load(AppLanguage.SOURCE)))
        assertEquals("es", appLanguage().key)
    }

    private data class Values(val summary: Map<String, String>, val feedback: Pair<String?, String?>)

    /** Starts the app language as `SwingMainApp` does, then reads the two language values of a summary and a report. */
    private fun start(systemLocale: Locale, choice: AppLanguage? = null, property: String? = null): Values {
        val settings = LanguageSettings(InMemoryPreferencesProvider().node("application"))
        choice?.let(settings::save)
        val resolution = settings.resolve(systemLocale, property)
        I18n.install(Messages.of(resolution.language, resolution.pseudo))
        val os = osLanguage(systemLocale)
        return Values(summaryLanguages(os), feedbackLanguages(os))
    }

    /** The language attributes of the summary after the app records its settings, as at the start of the app. */
    private fun summaryLanguages(os: AnalyticsEvent.Language): Map<String, String> {
        val bodies = LinkedBlockingQueue<String>()
        val executor = EnabledAnalytics.newDaemonExecutor()
        val session = EnabledAnalytics(
            { body -> bodies.put(body); 204 }, "1.0.0", "windows", os,
            AnalyticsPreferences.SessionStart(1, uncleanExit = false), AnalyticsLevel.ESSENTIAL, executor = executor,
        )
        try {
            next(bodies)
            session.record(AnalyticsEvent.Settings(AnalyticsEvent.Theme.DARK, AnalyticsEvent.Accent.DEFAULT, appLanguage()))
            session.sendIfChanged()
            val attributes = next(bodies)["attributes"]
            return attributes.fieldNames().asSequence().associateWith { attributes[it].textValue() }
        } finally {
            session.close()
            executor.shutdownNow()
        }
    }

    private fun feedbackLanguages(os: AnalyticsEvent.Language): Pair<String?, String?> {
        val system = FeedbackSystemInfo.current("1.0.0", appLanguage = appLanguage().key, osLanguage = os.key)
        val report = FeedbackReport(UUID.randomUUID(), FeedbackTopic.PROBLEM, "m", null, system, null, null)
        val json = mapper.readTree(report.toJson())
        return json["app_language"]?.textValue() to json["os_language"]?.textValue()
    }

    private fun next(bodies: LinkedBlockingQueue<String>): JsonNode =
        mapper.readTree(bodies.poll(2, TimeUnit.SECONDS) ?: error("no summary"))
}
