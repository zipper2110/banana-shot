package org.litvin.i18n

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * The texts of one language (B-11 plan, section 2).
 *
 * [primary] has the texts of [language]. [fallback] has the English texts. A missing, empty, or broken text of
 * [language] uses the English text, and the log gets one warning for each key. A missing English text is a test
 * failure (`MessageCatalogTest`). At runtime it shows the key, so that the app does not crash.
 *
 * With [pseudo], each text shows in the test language `qps` (see [PseudoLocalization]).
 */
class Messages(
    val language: AppLanguage,
    private val primary: Map<String, String>,
    private val fallback: Map<String, String>,
    private val pseudo: Boolean = false,
) {
    private val logger = KotlinLogging.logger {}
    private val parsed = ConcurrentHashMap<String, MessagePattern>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    /** True when the English catalog has [key]. */
    fun has(key: String): Boolean = fallback.containsKey(key)

    /** The text of [key] with the named [args]. */
    fun text(key: String, args: Map<String, Any?> = emptyMap()): String {
        val pattern = pattern(key) ?: return key
        val literal: (String) -> String = if (pseudo) PseudoLocalization::transform else { text -> text }
        val result = pattern.format(
            args,
            pluralCategory = { PluralRules.category(language, it) },
            literal = literal,
            missing = { name -> warnOnce("$key/$name") { "The text $key has no value for the argument {$name}." } },
        )
        return if (pseudo) PseudoLocalization.wrap(result) else result
    }

    private fun pattern(key: String): MessagePattern? = parsed[key] ?: resolve(key)?.also { parsed[key] = it }

    private fun resolve(key: String): MessagePattern? {
        val translated = primary[key]
        if (translated != null && translated.isNotEmpty()) {
            try {
                return MessagePattern.parse(translated)
            } catch (failure: IllegalArgumentException) {
                warnOnce(key) { "The ${language.code} text of $key does not parse. The app uses the English text. ${failure.message}" }
            }
        } else if (language != AppLanguage.SOURCE) {
            warnOnce(key) { "The ${language.code} catalog has no text for $key. The app uses the English text." }
        }
        val english = fallback[key]
        if (english == null) {
            warnOnce(key) { "The English catalog has no text for $key." }
            return null
        }
        return try {
            MessagePattern.parse(english)
        } catch (failure: IllegalArgumentException) {
            warnOnce(key) { "The English text of $key does not parse. ${failure.message}" }
            null
        }
    }

    private fun warnOnce(id: String, message: () -> String) {
        if (warned.add(id)) logger.warn(message)
    }

    companion object {
        /** The English texts from the bundled catalog. */
        fun english(): Messages = of(AppLanguage.SOURCE)

        /** The texts of [language] from the bundled catalog, with the English fallback. */
        fun of(language: AppLanguage, pseudo: Boolean = false): Messages {
            val english = MessageCatalog.load(AppLanguage.SOURCE)
            val texts = if (language == AppLanguage.SOURCE) english else MessageCatalog.loadWithParents(language)
            return Messages(language, texts, english, pseudo)
        }
    }
}

/**
 * The plural categories of the CLDR data for the shipped languages. English and Spanish have the categories
 * `one` and `other`. A later language with more forms (E7) adds its rule here.
 */
object PluralRules {
    fun category(language: AppLanguage, amount: Double): String = when (language.baseCode) {
        "en", "es" -> if (amount == 1.0) "one" else MessagePattern.OTHER
        else -> error("No plural rule for ${language.code}")
    }
}
