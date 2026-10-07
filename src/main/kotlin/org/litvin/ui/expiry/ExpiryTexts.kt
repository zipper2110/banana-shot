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

/**
 * The texts of the expiry user interface (build-expiry-spec.md, "User interface"). Each text uses [AppInfo.NAME],
 * because the name can change. English only until localization (B-11).
 */
internal object ExpiryTexts {
    private val name get() = AppInfo.NAME

    val EXPIRED_TITLE get() = "This version of $name has expired"

    /** The dialog text when the rule has no `message`. */
    val EXPIRED_DEFAULT
        get() = "This version of $name has expired. Update to the new version to continue. " +
            "Your projects stay on your computer. Exports that you started before continue."

    /** The banner text. The user cannot close the banner. */
    val EXPIRED_BANNER get() = "Update to the new version to continue. Exports that you started before continue."

    const val CHECKING = "Checking…"

    val NO_CONNECTION
        get() = "$name cannot connect to the update server. It checks again every minute. " +
            "If the date is wrong, correct the clock and connect to the internet."

    /** The "cannot connect" text of the "Clock behind" notice. */
    val NO_CONNECTION_SHORT get() = "$name cannot connect to the update server."

    const val CHECKING_DATE_TITLE = "Checking the date…"

    val CHECKING_DATE get() = "$name checks the date on the update server. This takes a maximum of 15 seconds."

    const val CLOCK_BEHIND_TITLE = "The clock of this computer is behind"

    val CLOCK_BEHIND
        get() = "The date on this computer is earlier than the date that $name used before. " +
            "Check the clock of the computer and connect to the internet. " +
            "Until then, each start of $name uses 12 hours of the time that is left for this version."

    const val WRONG_CLOCK_TITLE = "The clock of this computer is wrong"

    const val WARNING_TITLE = "This version expires soon"

    const val UPDATE_TITLE = "A new version is available"

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
        "The date that $name used: ${ExpiryMomentFormat.format(currentTime, zone)}."

    /** "Version 1.4.0 is available." when `latest.version` is newer than [appVersion]. Else null. */
    fun newVersion(rules: VersionRules?, appVersion: String = AppInfo.version): String? {
        val latest = rules?.latest ?: return null
        val newer = runCatching { Version.parse(latest.version) > Version.parse(appVersion) }.getOrDefault(false)
        return if (newer) versionAvailable(latest.version) else null
    }

    fun versionAvailable(version: String) = "Version $version is available."

    /** The expiry warning: the moment in local time with the hour. */
    fun warning(expiry: EffectiveExpiry, zone: ZoneId = ZoneId.systemDefault()): String =
        "This version works until ${ExpiryMomentFormat.format(expiry.moment, zone)}. Update to the new version."

    /**
     * The wrong clock notice. [wrongClock] is the system time minus the server time. [days] is
     * [org.litvin.license.check.ExpiryState.wrongClockDays].
     */
    fun wrongClock(wrongClock: Duration, days: Long): String {
        val direction = if (wrongClock.isNegative) "behind" else "ahead"
        val count = if (days == 1L) "1 day" else "$days days"
        return "The clock of this computer is $count $direction. Correct the clock. " +
            "If you do not, $name can stop while the computer is offline."
    }

    const val UPDATE_AND_RESTART = "Update and restart"
    const val DOWNLOAD_UPDATE = "Manually download update"
    const val CHECK_NOW = "Check now"
    const val LATER = "Later"
    const val CLOSE = "Close"

    /** The text of "Update and restart" before the first bytes arrive. */
    const val DOWNLOADING = "Downloading…"

    /** The text of "Update and restart" while the download runs. */
    fun downloading(bytes: Long, totalBytes: Long?): String =
        if (totalBytes != null && totalBytes > 0) "Downloading… ${(bytes * 100 / totalBytes).coerceIn(0, 100)}%"
        else "Downloading… ${bytes / (1024 * 1024)} MB"

    const val EXPORT_RUNS_TITLE = "Update and restart"

    const val EXPORT_RUNS =
        "An export runs now. After the update, the running export starts again from the beginning. " +
            "The queued exports continue."

    const val DOWNLOAD_FAILED_TITLE = "The download failed"

    fun downloadFailed(reason: String) =
        "The download of the new version failed: $reason\n\nTry again later, or click \"$DOWNLOAD_UPDATE\" to download it in the browser."
}
