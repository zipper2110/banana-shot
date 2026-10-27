package org.litvin.ui.expiry

import org.litvin.AppInfo
import org.litvin.license.EffectiveExpiry
import org.litvin.license.ExpiryMomentFormat
import org.litvin.license.Version
import org.litvin.license.VersionRules
import org.litvin.license.check.CheckState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.litvin.i18n.tr

/**
 * The texts of the expiry user interface (build-expiry-spec.md, "User interface"). Each text uses [AppInfo.NAME],
 * because the name can change. The texts are in the catalog (B-11).
 */
internal object ExpiryTexts {
    private val name get() = AppInfo.NAME

    val EXPIRED_TITLE get() = tr("expiry.expired.title", "app" to name)

    /** The dialog text when the rule has no `message`. */
    val EXPIRED_DEFAULT get() = tr("expiry.expired.default", "app" to name)

    /** The banner text. The user cannot close the banner. */
    val EXPIRED_BANNER get() = tr("expiry.expired.banner")

    val CHECKING get() = tr("expiry.checking")

    val NO_CONNECTION get() = tr("expiry.noConnection", "app" to name)

    /** The "cannot connect" text of the "Clock behind" notice. */
    val NO_CONNECTION_SHORT get() = tr("expiry.noConnection.short", "app" to name)

    val CHECKING_DATE_TITLE get() = tr("expiry.checkingDate.title")

    val CHECKING_DATE get() = tr("expiry.checkingDate", "app" to name)

    val CLOCK_BEHIND_TITLE get() = tr("expiry.clockBehind.title")

    val CLOCK_BEHIND get() = tr("expiry.clockBehind", "app" to name)

    val WRONG_CLOCK_TITLE get() = tr("expiry.wrongClock.title")

    val WARNING_TITLE get() = tr("expiry.warning.title")

    val UPDATE_TITLE get() = tr("expiry.update.title")

    /** The title of the "What's new" popup (B-54). */
    val WHATS_NEW_TITLE get() = tr("expiry.whatsNew.title")

    /** The link to the extended page about the release on the site (B-54). */
    val READ_MORE get() = tr("expiry.readMore")

    /** The state of the online check in the banner and the dialog. "Still expired" has no extra text. */
    fun checkState(check: CheckState): String? = when (check) {
        CheckState.CHECKING -> CHECKING
        CheckState.NO_CONNECTION -> NO_CONNECTION
        CheckState.NONE, CheckState.STILL_EXPIRED -> null
    }

    /** The state of the online check in the "Clock behind" notice. */
    fun clockBehindCheckState(check: CheckState): String? = when (check) {
        CheckState.CHECKING -> CHECKING
        CheckState.NO_CONNECTION -> NO_CONNECTION_SHORT
        CheckState.NONE, CheckState.STILL_EXPIRED -> null
    }

    /** The date that the app used, in local time with the hour. */
    fun dateUsed(currentTime: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        tr("expiry.dateUsed", "app" to name, "date" to ExpiryMomentFormat.format(currentTime, zone))

    /** "Version 1.4.0 is available." when `latest.version` is newer than [appVersion]. Else null. */
    fun newVersion(rules: VersionRules?, appVersion: String = AppInfo.version): String? {
        val latest = rules?.latest ?: return null
        val newer = runCatching { Version.parse(latest.version) > Version.parse(appVersion) }.getOrDefault(false)
        return if (newer) versionAvailable(latest.version) else null
    }

    fun versionAvailable(version: String) = tr("expiry.versionAvailable", "version" to version)

    /** The expiry warning: the moment in local time with the hour. */
    fun warning(expiry: EffectiveExpiry, zone: ZoneId = ZoneId.systemDefault()): String =
        tr("expiry.warning", "date" to ExpiryMomentFormat.format(expiry.moment, zone))

    /**
     * The wrong clock notice. [wrongClock] is the system time minus the server time. [days] is
     * [org.litvin.license.check.ExpiryState.wrongClockDays].
     */
    fun wrongClock(wrongClock: Duration, days: Long): String =
        tr(if (wrongClock.isNegative) "expiry.wrongClock.behind" else "expiry.wrongClock.ahead", "count" to days, "app" to name)

    val UPDATE_AND_RESTART get() = tr("expiry.updateAndRestart")
    val DOWNLOAD_UPDATE get() = tr("expiry.downloadUpdate")
    val CHECK_NOW get() = tr("expiry.checkNow")
    val LATER get() = tr("expiry.later")
    val CLOSE get() = tr("common.close")

    /** The text of "Update and restart" before the first bytes arrive. */
    val DOWNLOADING get() = tr("expiry.downloading")

    /** The text of "Update and restart" while the download runs. */
    fun downloading(bytes: Long, totalBytes: Long?): String =
        if (totalBytes != null && totalBytes > 0) tr("expiry.downloading.percent", "percent" to (bytes * 100 / totalBytes).coerceIn(0, 100))
        else tr("expiry.downloading.size", "size" to bytes / (1024 * 1024))

    val EXPORT_RUNS_TITLE get() = UPDATE_AND_RESTART

    val EXPORT_RUNS get() = tr("expiry.exportRuns")

    val DOWNLOAD_FAILED_TITLE get() = tr("expiry.downloadFailed.title")

    fun downloadFailed(reason: String) = tr("expiry.downloadFailed", "reason" to reason, "button" to DOWNLOAD_UPDATE)
}
