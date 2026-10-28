package org.litvin.i18n

import io.github.oshai.kotlinlogging.KotlinLogging
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * The formats of the numbers and the dates that the user reads (B-11 plan, section 5).
 *
 * The format locale is the Windows regional format, not the app language. The digits are always 0–9, and
 * the calendar is always Gregorian (plan, "Decisions", "Digits" and "Calendar"). Machine texts (FFmpeg
 * arguments, ASS files, JSON) must not use this object: they use `Locale.ROOT` or `Locale.US`.
 */
object DisplayFormats {
    private val logger = KotlinLogging.logger {}

    /** The languages with number or date data in the JDK. It must stay above [formatLocale], which uses it. */
    private val FORMAT_LANGUAGES: Set<String> =
        (NumberFormat.getAvailableLocales().asSequence() + DateFormat.getAvailableLocales().asSequence())
            .map { it.language }
            .filter { it.isNotEmpty() }
            .toSet()

    /**
     * The Windows regional format. The app reads it one time at the start, so one window never shows two formats.
     * Tests set a fixed locale.
     */
    @Volatile
    var formatLocale: Locale = usableFormatLocale(Locale.getDefault(Locale.Category.FORMAT))

    /**
     * [locale], or `en-US` when [locale] has no language (`Locale.ROOT`, `und`) or the JDK has no format data for its
     * language (for example `xx-XX`). The fallback writes one warning to the log.
     */
    internal fun usableFormatLocale(locale: Locale): Locale {
        val language = locale.language
        if (language.isNotEmpty() && language != "und" && language in FORMAT_LANGUAGES) return locale
        logger.warn { "No format data for the regional format '${locale.toLanguageTag()}'. The app uses en-US." }
        return Locale.US
    }

    /**
     * The locale of the numbers, with the digits 0–9 (for example `ar-EG` has other digits by default). It is the
     * [formatLocale]. In [I18n.inVideo], it is the [formatLocale] only when its language is the video language, and
     * else the default format of the video language (plan, "Decisions", "Video number format").
     */
    private val numberLocale: Locale
        get() {
            val video = I18n.videoLanguage
            val base = if (video == null || formatLocale.language == video.baseCode) formatLocale else video.locale
            return Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build()
        }

    private fun symbols(): DecimalFormatSymbols = DecimalFormatSymbols.getInstance(numberLocale).apply { zeroDigit = '0' }

    /** The decimal separator of the format locale, for example "," for `es-ES`. */
    val decimalSeparator: Char get() = symbols().decimalSeparator

    /** [value] with grouping, for example "1,234" (`en-US`) or "1.234" (`es-ES`). */
    fun integer(value: Long): String = (NumberFormat.getIntegerInstance(numberLocale) as DecimalFormat).apply {
        decimalFormatSymbols = symbols()
    }.format(value)

    fun integer(value: Int): String = integer(value.toLong())

    /** [value] with exactly [fractionDigits] digits after the separator, for example "1,5". */
    fun decimal(value: Double, fractionDigits: Int): String = decimal(value, fractionDigits, fractionDigits)

    /** [value] with [minFractionDigits] to [maxFractionDigits] digits after the separator. */
    fun decimal(value: Double, minFractionDigits: Int, maxFractionDigits: Int, grouping: Boolean = true): String =
        (NumberFormat.getNumberInstance(numberLocale) as DecimalFormat).apply {
            decimalFormatSymbols = symbols()
            minimumFractionDigits = minFractionDigits
            maximumFractionDigits = maxFractionDigits
            isGroupingUsed = grouping
        }.format(value)

    /**
     * Reads a number that the user typed. The text can have "." or "," as the decimal separator in all
     * languages (plan, "Decisions", "Typed decimal separator"). Returns null when the text is not a number.
     */
    fun parseDecimal(text: String): Double? {
        val trimmed = text.trim().replace('−', '-')
        if (trimmed.isEmpty()) return null
        if (trimmed.count { it == ',' || it == '.' } > 1) return null
        return trimmed.replace(',', '.').toDoubleOrNull()
    }

    /**
     * A date. If the language of the format locale is the app language, the date has the month name
     * ("10 oct 2026"). If not, the date has only digits ("10/10/26"), so that an English window does not show
     * Spanish month names.
     */
    fun date(value: TemporalAccessor): String = formatter(FormatStyle.MEDIUM, FormatStyle.SHORT, time = false).format(value)

    /** A date and a time, with the same rule as [date]. The time uses the 12-hour or 24-hour form of the format locale. */
    fun dateTime(value: TemporalAccessor): String = formatter(FormatStyle.MEDIUM, FormatStyle.SHORT, time = true).format(value)

    /** A time of the day, for example "14:05" or "2:05 PM". */
    fun time(value: TemporalAccessor): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(numberLocale).withDecimalStyle(DecimalStyle.STANDARD)
            .withChronology(IsoChronology.INSTANCE).format(value)

    private fun formatter(withNames: FormatStyle, digitsOnly: FormatStyle, time: Boolean): DateTimeFormatter {
        val style = if (formatLocale.language == I18n.language.baseCode) withNames else digitsOnly
        val dateTime = DateTimeFormatter.ofLocalizedDateTime(style, FormatStyle.SHORT).withLocale(numberLocale)
        val base = if (time) dateTime else DateTimeFormatter.ofLocalizedDate(style).withLocale(numberLocale)
        return base.withDecimalStyle(DecimalStyle.STANDARD).withChronology(IsoChronology.INSTANCE)
    }

    /**
     * A whole percentage in the form of the format locale, for example "64%" (`en-US`, `es-MX`) or "64 %" with a
     * no-break space (`es-ES`, `de-DE`).
     */
    fun percent(value: Int): String = (NumberFormat.getPercentInstance(numberLocale) as DecimalFormat).apply {
        decimalFormatSymbols = symbols()
    }.format(value / 100.0)

    /**
     * The upper case of a translated text, with the rules of the app language (German "ß", Turkish "i"). In
     * [I18n.inVideo], the rules of the video language apply.
     */
    fun upper(text: String): String = text.uppercase(I18n.active.language.locale)
}
