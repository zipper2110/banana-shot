package org.litvin.i18n

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Locale
import java.util.prefs.Preferences

/**
 * The language setting of the app (B-11 plan, section 4, E3-T21, E4-T11).
 *
 * The preferences keep the choice as a language code, or "system" to follow the Windows display language.
 * A stored value that this build does not know works as "system" and stays stored until the user changes the
 * language (plan, "Decisions", "Unknown language value").
 */
class LanguageSettings(private val preferences: Preferences) {
    private val logger = KotlinLogging.logger {}

    /** The choice of the user: null means "follow Windows". */
    val choice: AppLanguage?
        get() {
            val stored = preferences.get(KEY, null) ?: return null
            if (stored == SYSTEM) return null
            val language = AppLanguage.ofCode(stored)
            if (language == null && !stored.matches(LANGUAGE_CODE)) {
                logger.warn { "The stored language '$stored' is not a language code. The app follows Windows." }
            }
            return language
        }

    /** Saves the choice. Null follows Windows. Throws when the preferences cannot save it. */
    fun save(choice: AppLanguage?) {
        preferences.put(KEY, choice?.code ?: SYSTEM)
        preferences.flush()
    }

    /**
     * True when the app must show the notice of an automatic language change (E4-T11): the language comes from
     * Windows, it is not English, and the notice did not show for it before.
     */
    fun needsSystemLanguageNotice(resolution: Resolution): Boolean =
        resolution.source == Source.SYSTEM &&
            resolution.language != AppLanguage.SOURCE &&
            !resolution.pseudo &&
            preferences.get(NOTICE_SHOWN_KEY, null) != resolution.language.code

    /** Records that the notice showed for [language]. */
    fun noticeShown(language: AppLanguage) {
        preferences.put(NOTICE_SHOWN_KEY, language.code)
        runCatching { preferences.flush() }
    }

    /** Where the language of this start comes from. */
    enum class Source { SYSTEM, STORED, PROPERTY }

    data class Resolution(val language: AppLanguage, val source: Source, val pseudo: Boolean = false)

    /**
     * The language of this start. The property `bananashot.language` has priority: "qps" switches on the test
     * language, and a language code shows that language. Then the stored choice, then the Windows display language.
     */
    fun resolve(
        systemLocale: Locale = Locale.getDefault(Locale.Category.DISPLAY),
        property: String? = System.getProperty(PROPERTY),
    ): Resolution {
        val forced = property?.trim()?.lowercase(Locale.ROOT)
        if (!forced.isNullOrEmpty()) {
            if (forced == PseudoLocalization.CODE) return Resolution(AppLanguage.SOURCE, Source.PROPERTY, pseudo = true)
            (AppLanguage.ofCode(forced) ?: AppLanguage.forLocale(Locale.forLanguageTag(forced.replace('_', '-'))))
                ?.let { return Resolution(it, Source.PROPERTY) }
            logger.warn { "The property $PROPERTY has the unknown value '$property'. The app ignores it." }
        }
        choice?.let { return Resolution(it, Source.STORED) }
        return Resolution(systemLanguage(systemLocale), Source.SYSTEM)
    }

    companion object {
        const val KEY = "ui.language"
        const val NOTICE_SHOWN_KEY = "ui.languageNoticeShown"
        const val SYSTEM = "system"
        const val PROPERTY = "bananashot.language"

        private val LANGUAGE_CODE = Regex("[a-z]{2,3}([-_][a-z0-9]{2,8})*", RegexOption.IGNORE_CASE)

        /**
         * The app language for a Windows display language (see [AppLanguage.forLocale]): `es-ES` gives Spanish of
         * Spain, and `es-MX` or `es-AR` give Latin American Spanish. A language that the app does not have, an empty
         * locale (`und`), and the Windows pseudo-locales (`qps-ploc`) give English.
         */
        fun systemLanguage(locale: Locale): AppLanguage = AppLanguage.forLocale(locale) ?: AppLanguage.SOURCE
    }
}
