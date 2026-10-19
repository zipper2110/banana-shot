package org.litvin.i18n

import java.util.Locale

/**
 * The languages of the app interface and of the texts in the video (B-11).
 *
 * [code] is a BCP 47 language tag. The preferences keep it, and the catalog file names use it with "_" in place
 * of "-" (`messages_es_419.properties`). [nativeName] is the name of the language in this language, so that a
 * user can find their language without the ability to read the current language.
 *
 * A regional variant has a [parent]. Its catalog files have only the texts that are different from the parent.
 * All other texts come from the parent, and then from English.
 */
enum class AppLanguage(val code: String, val nativeName: String, val parent: AppLanguage? = null) {
    ENGLISH("en", "English"),
    SPANISH_SPAIN("es", "Español (España)"),
    SPANISH_LATIN_AMERICA("es-419", "Español (Latinoamérica)", parent = SPANISH_SPAIN),
    ;

    val locale: Locale get() = Locale.forLanguageTag(code)

    /** The primary language subtag, for example "es" for both Spanish variants. */
    val baseCode: String get() = locale.language

    /** This language and its parents, the most specific first. English is not in the list unless this is English. */
    val chain: List<AppLanguage> get() = generateSequence(this) { it.parent }.toList()

    companion object {
        /** The source language of the catalog. All other languages fall back to it. */
        val SOURCE = ENGLISH

        /** The countries that use the Spanish of Spain. All other countries get the Latin American variant. */
        private val SPAIN_COUNTRIES = setOf("", "ES", "GQ")

        /** The language with the tag [code] (case does not matter, "_" works as "-"), or null. */
        fun ofCode(code: String?): AppLanguage? {
            val normalized = code?.trim()?.replace('_', '-')?.lowercase(Locale.ROOT) ?: return null
            return entries.firstOrNull { it.code.lowercase(Locale.ROOT) == normalized }
        }

        /**
         * The app language for a Windows display language, or null when the app does not have it. Spanish of Spain
         * and of Equatorial Guinea gives [SPANISH_SPAIN]. Spanish of all other countries (Mexico, Argentina, the
         * United States, and so on) gives [SPANISH_LATIN_AMERICA].
         */
        fun forLocale(locale: Locale): AppLanguage? = when (locale.language) {
            ENGLISH.baseCode -> ENGLISH
            SPANISH_SPAIN.baseCode -> if (locale.country in SPAIN_COUNTRIES) SPANISH_SPAIN else SPANISH_LATIN_AMERICA
            else -> null
        }
    }
}
