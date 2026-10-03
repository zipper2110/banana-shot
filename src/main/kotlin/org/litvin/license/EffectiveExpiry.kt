package org.litvin.license

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The effective expiry: the earliest of the build expiry and the `stopsOn` of each rule that matches the app
 * version ("Rules for the app" and "Expiry moment" in `build-expiry-spec.md`).
 */
data class EffectiveExpiry(
    /** The first day on which the version does not work. */
    val date: LocalDate,
    /** The rule that gives [date], or null when the build expiry gives it. */
    val rule: VersionRule?,
) {
    /** The version stops at 00:00 UTC on [date]. All computers use the same moment. */
    val moment: Instant
        get() = date.atStartOfDay(ZoneOffset.UTC).toInstant()

    /** The warning starts exactly 30 × 24 hours before [moment]. */
    val warningStart: Instant
        get() = moment - WARNING_PERIOD

    /** The rule `message`, or null. */
    val message: String?
        get() = rule?.message

    fun isExpired(now: Instant): Boolean = now >= moment

    /** True from [warningStart], also after [moment]. */
    fun isWarning(now: Instant): Boolean = now >= warningStart

    companion object {
        val WARNING_PERIOD: Duration = Duration.ofHours(30L * 24)

        /**
         * The effective expiry of [appVersion] with the rules file [rules] (the new file or the saved file).
         * The file can only make the expiry earlier. A rule with the same date as the build expiry gives the
         * expiry, so that its `message` shows. Of two rules with the same date, the first rule in the file gives it.
         */
        fun of(
            appVersion: String,
            rules: VersionRules?,
            buildExpiry: LocalDate = BuildExpiry.expiryDate(),
        ): EffectiveExpiry {
            val earliestRule = rules?.matchingRules(appVersion)?.minByOrNull { it.stopsOn }
            return if (earliestRule != null && earliestRule.stopsOn <= buildExpiry) {
                EffectiveExpiry(earliestRule.stopsOn, earliestRule)
            } else {
                EffectiveExpiry(buildExpiry, null)
            }
        }
    }
}

/** Formats an expiry moment in the local time zone with the hour, for example "29 March 2027, 16:00". */
object ExpiryMomentFormat {
    // English only until localization (B-11).
    private val FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH)

    fun format(moment: Instant, zone: ZoneId = ZoneId.systemDefault()): String = FORMAT.format(moment.atZone(zone))
}

/**
 * Writes the effective expiry to the log, for bug reports. Call [record] after each expiry check.
 * It writes a line when the effective expiry changes, and one time when a rule stops the version.
 */
class ExpiryLog(private val write: (String) -> Unit = { line -> logger.info { line } }) {
    private var last: EffectiveExpiry? = null
    private var stopWritten: EffectiveExpiry? = null

    @Synchronized
    fun record(expiry: EffectiveExpiry, now: Instant) {
        if (expiry != last) {
            last = expiry
            write("Effective expiry: ${expiry.date} (${expiry.source()})")
        }
        if (expiry.rule != null && expiry.isExpired(now) && expiry != stopWritten) {
            stopWritten = expiry
            write("Rule ${expiry.rule.id} stopped the version on ${expiry.date}")
        }
    }

    private fun EffectiveExpiry.source(): String = if (rule == null) "build expiry" else "rule ${rule.id}"

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
