package org.litvin.app

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.LanguageSettings
import org.litvin.i18n.Messages
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import org.litvin.ui.more.LanguageSetting
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * B-11 E4-T11: the notice after an automatic language change. Each test uses one preferences node for all starts,
 * as one user who starts the app some times.
 */
class SystemLanguageNoticeTest {
    private val preferences = InMemoryPreferencesProvider().node("application")
    private val settings = LanguageSettings(preferences)
    private val dialogs = RecordingDialogs()
    private var restarts = 0

    @AfterTest
    fun tearDown() = I18n.install(Messages.english())

    @Test
    fun `an existing user with Windows in Spanish sees the notice one time`() {
        assertEquals(1, start())
        assertEquals(listOf("Use English"), dialogs.asked.map { it.useEnglish })
        assertEquals(0, start())
        assertEquals(1, dialogs.asked.size)
        assertNull(settings.choice, "OK keeps the setting System")
    }

    @Test
    fun `the notice is in Spanish, and only the button Use English is in English`() {
        start()
        val notice = dialogs.asked.single()
        assertEquals("Use English", notice.useEnglish)
        assertEquals("Aceptar", notice.ok)
        assertEquals(true, "Español (España)" in notice.text, notice.text)
    }

    /** OK, Escape, and the close button all give false (see `SwingApplicationFactory.languageNoticeDialogs`). */
    @Test
    fun `a user who closes the notice with Escape does not see it again`() {
        dialogs.answer = false
        start()
        assertEquals(0, start())
        assertEquals(0, start())
        assertEquals(1, dialogs.asked.size)
        assertEquals(0, restarts)
    }

    @Test
    fun `a new user with Windows in Spanish does not see the notice`() {
        assertEquals(0, start(firstStart = true))
        assertEquals(0, start())
        assertEquals(0, dialogs.asked.size)
    }

    @Test
    fun `the notice shows at the next start if the app stops before the notice opens`() {
        assertEquals(1, start(runTasks = false))
        assertEquals(1, start())
        assertEquals(1, dialogs.asked.size)
    }

    @Test
    fun `Use English stores English, not System, and restarts the app`() {
        dialogs.answer = true
        start()
        assertEquals(AppLanguage.ENGLISH, settings.choice)
        assertEquals("en", preferences.get(LanguageSettings.KEY, null))
        assertEquals(1, restarts)
        assertEquals(emptyList<Pair<String, String>>(), dialogs.told)
    }

    @Test
    fun `Use English without a restart tells the user to start the app again, in English`() {
        dialogs.answer = true
        start(canRestart = false)
        assertEquals(AppLanguage.ENGLISH, settings.choice)
        assertEquals(listOf("Use English" to Messages.english().text("settings.language.closeAndStart")), dialogs.told)
    }

    @Test
    fun `no notice for a selected language, for English, or for a Windows language that the app does not have`() {
        assertEquals(0, start(systemLocale = Locale.ITALY))
        assertEquals(0, start(systemLocale = Locale.US))
        settings.save(AppLanguage.SPANISH_SPAIN)
        assertEquals(0, start())
        assertEquals(0, dialogs.asked.size)
    }

    /**
     * One start of the app on Windows with [systemLocale]. Returns the count of the notice tasks. With [runTasks]
     * false, the app stops before the tasks run.
     */
    private fun start(
        systemLocale: Locale = Locale.forLanguageTag("es-ES"),
        firstStart: Boolean = false,
        runTasks: Boolean = true,
        canRestart: Boolean = true,
    ): Int {
        val resolution = settings.resolve(systemLocale, property = null)
        I18n.install(Messages.of(resolution.language))
        var tasks = 0
        SystemLanguageNotice(settings, setting(resolution.language, canRestart), dialogs).start(resolution, firstStart) { task ->
            tasks++
            if (runTasks) task()
        }
        return tasks
    }

    private fun setting(running: AppLanguage, canRestart: Boolean) = object : LanguageSetting {
        override val choice: AppLanguage? get() = settings.choice
        override val running: AppLanguage = running
        override val systemLanguage: AppLanguage = AppLanguage.SPANISH_SPAIN
        override fun save(choice: AppLanguage?): Boolean = runCatching { settings.save(choice) }.isSuccess
        override val restart: (() -> Unit)? = if (canRestart) ({ restarts++ }) else null
    }

    private data class Notice(val title: String, val text: String, val useEnglish: String, val ok: String)

    private class RecordingDialogs : SystemLanguageNotice.Dialogs {
        var answer = false
        val asked = mutableListOf<Notice>()
        val told = mutableListOf<Pair<String, String>>()

        override fun ask(title: String, text: String, useEnglish: String, ok: String): Boolean {
            asked += Notice(title, text, useEnglish, ok)
            return answer
        }

        override fun tell(title: String, text: String) {
            told += title to text
        }
    }
}
