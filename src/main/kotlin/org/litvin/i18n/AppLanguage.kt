package org.litvin.i18n

import java.util.Locale

/**
 * The languages of the app interface and of the texts in the video (B-11).
 *
 * [code] is the primary language subtag. The preferences keep it, and the catalog file names use it
 * (`messages_es.properties`). [nativeName] is the name of the language in this language, so that a user
 * can find their language without the ability to read the current language.
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        /** The source language of the catalog. All other languages fall back to it. */
        val SOURCE = ENGLISH

        /** The language with the primary subtag [code] (case does not matter), or null. */
        fun ofCode(code: String?): AppLanguage? {
            val normalized = code?.trim()?.lowercase(Locale.ROOT) ?: return null
            return entries.firstOrNull { it.code == normalized }
        }
    }
}
