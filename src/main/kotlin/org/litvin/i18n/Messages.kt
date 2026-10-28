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
        args.keys.filter { it !in pattern.argumentNames }.forEach { name ->
            warnOnce("$key/$name/unused") { "The text $key does not use the argument {$name}." }
        }
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
 * The plural categories of the CLDR data for the shipped languages (B-11 E7-T2). English, Spanish, Portuguese,
 * French, and German have the categories `one` and `other`. Russian has `one`, `few`, `many`, and `other`. Japanese
 * and Chinese have only `other`, because their nouns have no plural form ("3 ポイント", "3 个回合"). A later language
 * adds its rule here. The CLDR case `many` of Spanish, Portuguese, and French is only for compact numbers ("1 M"),
 * and the app has none.
 *
 * The rules cover the numbers that the app shows: integers and numbers with decimals. A number with decimals uses
 * the CLDR category of a visible fraction: `other` in English and Russian ("1.5 seconds", "1,5 секунды").
 * Portuguese of Brazil and French use `one` for 0, 1, and all numbers between them and 2 ("0 ponto", "0 point",
 * "1,5 seconde"). Portuguese of Portugal and German use `one` only for 1, as English ("0 Punkte", "1,5 Sekunden"). Thus the rule depends on the variant, not
 * only on the language.
 */
object PluralRules {
    fun category(language: AppLanguage, amount: Double): String = when {
        language in ZERO_AND_ONE -> if (kotlin.math.abs(amount) < 2.0) ONE else MessagePattern.OTHER
        language.baseCode in ONE_OTHER -> if (amount == 1.0) ONE else MessagePattern.OTHER
        language.baseCode == "ru" -> russian(amount)
        language.baseCode in OTHER_ONLY -> MessagePattern.OTHER
        else -> error("No plural rule for ${language.code}")
    }

    /** The categories that each plural message of [language] must have. */
    fun categories(language: AppLanguage): Set<String> = when (language.baseCode) {
        in ONE_OTHER -> setOf(ONE, MessagePattern.OTHER)
        "ru" -> setOf(ONE, FEW, MANY, MessagePattern.OTHER)
        in OTHER_ONLY -> setOf(MessagePattern.OTHER)
        else -> error("No plural rule for ${language.code}")
    }

    /** The languages with only `one` and `other`. */
    private val ONE_OTHER = setOf("en", "es", "pt", "fr", "de")

    /** The languages with only `other`. */
    private val OTHER_ONLY = setOf("ja", "zh")

    /** CLDR: one is i = 0 or 1. Thus `one` is for 0, 1, and the numbers with decimals below 2. */
    private val ZERO_AND_ONE = setOf(AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.FRENCH)

    /** CLDR: one is i % 10 = 1 and i % 100 != 11, few is i % 10 = 2..4 and i % 100 != 12..14, many is the other integers. */
    private fun russian(amount: Double): String {
        if (amount != Math.rint(amount) || amount.isInfinite()) return MessagePattern.OTHER
        val i = kotlin.math.abs(amount).toLong()
        val lastDigit = i % 10
        val lastTwo = i % 100
        return when {
            lastDigit == 1L && lastTwo != 11L -> ONE
            lastDigit in 2L..4L && lastTwo !in 12L..14L -> FEW
            else -> MANY
        }
    }

    const val ONE = "one"
    const val FEW = "few"
    const val MANY = "many"
}
