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
    RUSSIAN("ru", "Русский"),
    PORTUGUESE_BRAZIL("pt", "Português (Brasil)"),
    PORTUGUESE_PORTUGAL("pt-PT", "Português (Portugal)", parent = PORTUGUESE_BRAZIL),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    JAPANESE("ja", "日本語"),
    CHINESE_SIMPLIFIED("zh-Hans", "简体中文"),
    CHINESE_TRADITIONAL("zh-Hant", "繁體中文"),
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

        /** The countries that use the Portuguese of Brazil. All other countries get the Portuguese of Portugal. */
        private val BRAZIL_COUNTRIES = setOf("", "BR")

        /** The regions that write Chinese with the traditional characters, when the locale has no script. */
        private val TRADITIONAL_CHINESE_COUNTRIES = setOf("TW", "HK", "MO")

        /** The language with the tag [code] (case does not matter, "_" works as "-"), or null. */
        fun ofCode(code: String?): AppLanguage? {
            val normalized = code?.trim()?.replace('_', '-')?.lowercase(Locale.ROOT) ?: return null
            return entries.firstOrNull { it.code.lowercase(Locale.ROOT) == normalized }
        }

        /**
         * The app language for a Windows display language, or null when the app does not have it. Spanish of Spain
         * and of Equatorial Guinea gives [SPANISH_SPAIN]. Spanish of all other countries (Mexico, Argentina, the
         * United States, and so on) gives [SPANISH_LATIN_AMERICA]. Russian of all countries gives [RUSSIAN].
         * Portuguese of Brazil gives [PORTUGUESE_BRAZIL]. Portuguese of all other countries (Portugal, Angola,
         * Mozambique, and so on) gives [PORTUGUESE_PORTUGAL]. French of all countries (France, Belgium, Switzerland,
         * Canada, and so on) gives [FRENCH]. German of all countries (Germany, Austria, Switzerland, and so on) gives
         * [GERMAN]. Japanese gives [JAPANESE]. Chinese with the script `Hant`, or without a script in Taiwan, Hong
         * Kong, or Macau, gives [CHINESE_TRADITIONAL]. All other Chinese (`zh-CN`, `zh-SG`, `zh-Hans`, `zh`) gives
         * [CHINESE_SIMPLIFIED].
         */
        fun forLocale(locale: Locale): AppLanguage? = when (locale.language) {
            ENGLISH.baseCode -> ENGLISH
            SPANISH_SPAIN.baseCode -> if (locale.country in SPAIN_COUNTRIES) SPANISH_SPAIN else SPANISH_LATIN_AMERICA
            RUSSIAN.baseCode -> RUSSIAN
            PORTUGUESE_BRAZIL.baseCode -> if (locale.country in BRAZIL_COUNTRIES) PORTUGUESE_BRAZIL else PORTUGUESE_PORTUGAL
            FRENCH.baseCode -> FRENCH
            GERMAN.baseCode -> GERMAN
            JAPANESE.baseCode -> JAPANESE
            CHINESE_SIMPLIFIED.baseCode -> if (isTraditionalChinese(locale)) CHINESE_TRADITIONAL else CHINESE_SIMPLIFIED
            else -> null
        }

        private fun isTraditionalChinese(locale: Locale): Boolean = when (locale.script) {
            "Hant" -> true
            "Hans" -> false
            else -> locale.country in TRADITIONAL_CHINESE_COUNTRIES
        }
    }
}
