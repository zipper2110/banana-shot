package org.litvin.app

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.LanguageSettings
import org.litvin.i18n.Messages
import org.litvin.i18n.tr
import org.litvin.ui.more.LanguageSetting

/**
 * The notice after an automatic language change (B-11 E4-T11). The app follows Windows, and Windows has a language
 * that the app now has. The notice shows one time for each language:
 * - A new user does not see it. The app records it as shown at the first start.
 * - The app records it as shown when it opens. If the app stops before the notice opens, it shows at the next start.
 * - Its button "Use English" is in English, so that a user who cannot read the new language can use it. The button
 *   saves English (not "System") and restarts the app.
 */
internal class SystemLanguageNotice(
    private val settings: LanguageSettings,
    private val setting: LanguageSetting,
    private val dialogs: Dialogs,
) {
    interface Dialogs {
        /** Shows the notice. Returns true when the user clicks [useEnglish]. OK, Escape, and the close button give false. */
        fun ask(title: String, text: String, useEnglish: String, ok: String): Boolean

        /** Shows a message with an OK button. */
        fun tell(title: String, text: String)
    }

    /** Checks the notice at the start of the app. When it must show, [later] gets the task that shows it. */
    fun start(resolution: LanguageSettings.Resolution?, firstStart: Boolean, later: (() -> Unit) -> Unit) {
        if (resolution == null || !settings.needsSystemLanguageNotice(resolution)) return
        if (firstStart) {
            settings.noticeShown(resolution.language)
            return
        }
        later { show(resolution.language) }
    }

    private fun show(language: AppLanguage) {
        settings.noticeShown(language)
        val english = Messages.english()
        val useEnglish = english.text("language.notice.useEnglish")
        val title = tr("language.notice.title")
        val text = tr("language.notice.text", "language" to language.nativeName)
        if (!dialogs.ask(title, text, useEnglish, tr("common.ok"))) return
        if (!setting.save(AppLanguage.ENGLISH)) return
        val restart = setting.restart
        if (restart != null) restart() else dialogs.tell(useEnglish, english.text("settings.language.closeAndStart"))
    }
}
