package org.litvin.license.check

import org.litvin.license.EffectiveExpiry
import org.litvin.license.LatestRelease
import org.litvin.license.VersionRules
import java.time.Duration
import java.time.Instant

/** The mode of the app ("When the app does the checks" and "Expired mode" in `build-expiry-spec.md`). */
enum class ExpiryMode {
    /** Path A of "Start of a build that looks expired": the "Checking the date…" window shows. No main window yet. */
    CHECKING_DATE,
    NORMAL,
    EXPIRED,
}

/** The state of the online check, for the texts of the banner, the dialog, and the "Clock behind" notice. */
enum class CheckState {
    /** No check runs, and the last result needs no text. */
    NONE,

    /** "Checking…". "Check now" cannot be clicked. */
    CHECKING,

    /** "<app name> cannot connect to the update server…". */
    NO_CONNECTION,

    /** The server time confirms the expiry. No extra text. */
    STILL_EXPIRED,
}

/**
 * One snapshot of the expiry state. The user interface (E8) shows it. Each change gives a new snapshot to
 * [ExpiryListener.stateChanged].
 */
data class ExpiryState(
    val mode: ExpiryMode,
    val check: CheckState,
    /** The current time of the app at the last expiry check: the date that the app used. */
    val currentTime: Instant,
    val expiry: EffectiveExpiry,
    /** The rules file that applies: the new file or the saved file. Null with no valid file. */
    val rules: VersionRules?,
    /** The modal dialog of expired mode. */
    val expiredDialog: Boolean,
    /** The update notice shows for this release. */
    val updateNotice: LatestRelease?,
    val expiryWarning: Boolean,
    val clockBehindNotice: Boolean,
    /** The system time minus the server time, while the wrong clock notice shows. */
    val wrongClock: Duration?,
) {
    /** "The clock of this computer is wrong by N days." At least 1. */
    val wrongClockDays: Long?
        get() = wrongClock?.let { maxOf(1L, Math.round(it.abs().toHours() / 24.0)) }
}

/**
 * Receives the decisions of [ExpiryController]. The controller calls the listener on its own threads, after it
 * releases its lock. A listener must not wait for another thread (for example with `invokeAndWait`): the user
 * interface uses `invokeLater`.
 */
interface ExpiryListener {
    /**
     * The app goes from normal mode to expired mode during a session. Save the open project and stop the player. The
     * controller calls this before it gives the state with expired mode.
     */
    fun beforeExpiredMode() {}

    fun stateChanged(state: ExpiryState) {}
}

/** The functions that the expiry user interface (E8) calls. [ExpiryController] does them. Tests give a fake. */
interface ExpiryCommands {
    /** The current snapshot. */
    val state: ExpiryState

    fun addListener(listener: ExpiryListener)

    /** "Check now" in the dialog, the banner, or the "Clock behind" notice. */
    fun checkNow()

    /** "Later" in the update notice. */
    fun laterUpdateNotice()

    fun closeExpiryWarning()

    fun closeClockBehindNotice()

    fun closeWrongClockNotice()

    /** A click on the start button of a new export in expired mode. */
    fun showExpiredDialog()

    fun closeExpiredDialog()
}
