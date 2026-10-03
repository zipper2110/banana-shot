package org.litvin.license.check

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.license.BuildExpiry
import org.litvin.license.EffectiveExpiry
import org.litvin.license.ExpiryLog
import org.litvin.license.LatestRelease
import org.litvin.license.NewWorkGate
import org.litvin.license.SavedVersionRules
import org.litvin.license.Version
import org.litvin.license.VersionRules
import org.litvin.license.VersionRulesParse
import org.litvin.license.online.RulesFetchResult
import org.litvin.license.online.RulesFetcher
import org.litvin.license.time.RunTimeCounter
import org.litvin.license.time.TimeEngine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The check controller (E6): the start sequence, the minute tick, the online check schedule, the modes, and the
 * decisions about the notices. It is headless: it gives [ExpiryState] snapshots, and the user interface (E8) shows
 * them. See "When the app does the checks" and "User interface" in `build-expiry-spec.md`.
 *
 * Call [start] one time, after the instance lock of B-19. For [StartPath.DATE_CHECK], show the "Checking the
 * date…" window and call [runDateCheck] on a thread that is not the EDT. Call [close] in the close sequence.
 *
 * The public functions are thread-safe. The read of the rules file runs with no lock, so [allowsNewWork] never
 * waits for a check.
 */
class ExpiryController(
    private val appVersion: String,
    private val engine: TimeEngine,
    private val counter: RunTimeCounter,
    private val savedRules: SavedVersionRules,
    private val fetcher: RulesFetcher,
    private val flag: ExpiredFlagStore,
    private val scheduler: ExpiryScheduler,
    private val retryTiming: RetryTiming,
    private val buildExpiry: LocalDate = BuildExpiry.expiryDate(),
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val expiryLog: ExpiryLog = ExpiryLog(),
) : NewWorkGate, ExpiryCommands, AutoCloseable {
    enum class StartPath {
        /** Normal mode. An online check runs in the background. */
        NORMAL,

        /** Path A: call [runDateCheck] while the "Checking the date…" window shows. */
        DATE_CHECK,

        /** Path B: expired mode at once, with "Checking…". */
        EXPIRED,
    }

    /** The kind of the result of one online check. It selects the next check ("Online check"). */
    private enum class ResponseKind { NO_CONNECTION, NOT_USABLE, USABLE }

    private val lock = Any()
    private val dispatchLock = Any()
    private val listeners = CopyOnWriteArrayList<ExpiryListener>()
    private var pendingBeforeExpired = 0

    private var started = false
    private var closed = false
    private var mode = ExpiryMode.NORMAL
    private var checkState = CheckState.NONE
    private var checkRunning = false
    private var now: Instant = Instant.EPOCH
    private var rules: VersionRules? = null
    private var expiry: EffectiveExpiry = EffectiveExpiry(buildExpiry, null)
    private var expiredDialog = false

    private var lastCheckCounter = 0L
    private var lastExpired = false
    private var lastWarning = false

    private var updateNoticeRelease: LatestRelease? = null
    private var hiddenUpdateVersion: Version? = null
    private var expiryWarning = false
    private var warningShownOn: LocalDate? = null
    private val seenRuleIds = mutableSetOf<String>()
    private var newRuleIds: Set<String> = emptySet()
    private var clockBehindClosed = false
    private var wrongClockShown = false
    private var wrongClock: Duration? = null

    private var tickTask: ScheduledTask? = null
    private var nextCheckTask: ScheduledTask? = null
    private var nextCheckDue = Long.MAX_VALUE

    private var published: ExpiryState? = null

    override fun addListener(listener: ExpiryListener) {
        listeners += listener
    }

    override val state: ExpiryState get() = synchronized(lock) { snapshot() }

    /**
     * The start sequence (E6-S2): the saved time, the build-date floor, "Clock behind", and the expiry check with the
     * saved rules file. Call it before the main window and before a project opens.
     */
    fun start(): StartPath = locked {
        check(!started) { "The expiry controller is already started" }
        started = true
        now = engine.start()
        rules = savedRules.load()
        updateRuleIds()
        val expired = evaluate()
        when {
            !expired -> {
                enterNormal()
                beginOnlineCheck(retries = false)
                scheduleTick()
                StartPath.NORMAL
            }
            flag.isSet() -> {
                logger.info { "Start of an expired build with the flag: path B" }
                enterExpired(CheckState.CHECKING, fromSession = false)
                beginOnlineCheck(retries = false)
                scheduleTick()
                StartPath.EXPIRED
            }
            else -> {
                logger.info { "Start of a build that looks expired, with no flag: path A" }
                mode = ExpiryMode.CHECKING_DATE
                checkState = CheckState.CHECKING
                checkRunning = true
                StartPath.DATE_CHECK
            }
        }
    }

    /**
     * Path A (E6-S3): an online check with retries, a maximum of 15 seconds. Then normal mode or expired mode. It
     * blocks, so call it on a thread that is not the EDT.
     */
    fun runDateCheck() {
        synchronized(lock) { check(mode == ExpiryMode.CHECKING_DATE) { "No date check is waiting" } }
        val result = fetchWithRetries()
        locked {
            val kind = apply(result)
            if (evaluate()) {
                enterExpired(stateAfter(kind), fromSession = false)
            } else {
                enterNormal()
                checkState = checkStateAfter(kind)
            }
            updateNotices()
            finishCheck(kind)
            scheduleTick()
        }
    }

    /** "Check now" in the dialog, the banner, or the "Clock behind" notice. No effect while a check runs. */
    override fun checkNow() = locked {
        if (started && !closed && mode != ExpiryMode.CHECKING_DATE) beginOnlineCheck(retries = false)
    }

    /**
     * The expiry check of the core functions (E7-S2): open a project and add a new export. False in expired mode.
     * During the online check of an expiry in a session (no server time), it gives true and does not wait.
     */
    override fun allowsNewWork(): Boolean = locked {
        if (!started || mode == ExpiryMode.CHECKING_DATE) return@locked false
        expiryCheck()
        mode == ExpiryMode.NORMAL
    }

    /** "Later" in the update notice: hides the notice for this version until the app closes. */
    override fun laterUpdateNotice() = locked {
        rules?.latest?.let { hiddenUpdateVersion = Version.parse(it.version) }
        updateNotices()
    }

    /** The user closed the warning. It shows again on the next calendar day. */
    override fun closeExpiryWarning() = locked { expiryWarning = false }

    override fun closeClockBehindNotice() = locked {
        clockBehindClosed = true
        updateNotices()
    }

    override fun closeWrongClockNotice() = locked { wrongClock = null }

    /** The start button of a new export in expired mode shows the dialog again. */
    override fun showExpiredDialog() = locked { if (mode == ExpiryMode.EXPIRED) expiredDialog = true }

    override fun closeExpiredDialog() = locked { expiredDialog = false }

    /** The close sequence: writes the saved time and stops the tasks. */
    override fun close() = locked {
        if (!started || closed) return@locked
        closed = true
        tickTask?.cancel()
        nextCheckTask?.cancel()
        engine.close()
    }

    private fun tick() {
        val runCheck = locked {
            if (closed) return@locked false
            now = engine.tick()
            val counterNow = counter.millis()
            val passed = expiry.isExpired(now) != lastExpired || expiry.isWarning(now) != lastWarning
            if (passed || counterNow - lastCheckCounter >= HOURLY.toMillis()) expiryCheck()
            scheduleTick()
            // A scheduled check can fire late after a sleep. The tick then starts it.
            !checkRunning && counterNow >= nextCheckDue
        }
        if (runCheck) checkNow()
    }

    /** The local expiry check (E6-S4, E6-S5). With no server time, an expiry in a session first gets an online check. */
    private fun expiryCheck() {
        val expired = evaluate()
        if (mode == ExpiryMode.NORMAL && expired) {
            if (engine.hasServerTime) {
                enterExpired(CheckState.NONE, fromSession = true)
            } else if (!checkRunning) {
                logger.info { "The build looks expired in the session with no server time: online check first" }
                beginOnlineCheck(retries = true)
            }
        }
        updateNotices()
    }

    /** Starts an online check on the scheduler. Only one check runs at a time. */
    private fun beginOnlineCheck(retries: Boolean) {
        if (checkRunning) return
        checkRunning = true
        nextCheckTask?.cancel()
        nextCheckDue = Long.MAX_VALUE
        // Normal mode shows no "Checking…", except for the "Check now" of the "Clock behind" notice.
        if (mode == ExpiryMode.EXPIRED || clockBehindVisible()) checkState = CheckState.CHECKING
        scheduler.schedule(Duration.ZERO) { runOnlineCheck(retries) }
    }

    private fun runOnlineCheck(retries: Boolean) {
        val result = if (retries) fetchWithRetries() else fetchOnce()
        locked {
            val kind = apply(result)
            val expired = evaluate()
            when {
                mode == ExpiryMode.EXPIRED && !expired -> {
                    logger.info { "The online check shows that the build has not expired: leave expired mode" }
                    enterNormal()
                    checkState = checkStateAfter(kind)
                }
                mode == ExpiryMode.EXPIRED -> checkState = stateAfter(kind)
                expired -> enterExpired(stateAfter(kind), fromSession = true)
                else -> checkState = checkStateAfter(kind)
            }
            updateNotices()
            finishCheck(kind)
        }
    }

    private fun fetchOnce(): RulesFetchResult = try {
        fetcher.fetch()
    } catch (failure: Exception) {
        logger.warn(failure) { "The read of the rules file failed" }
        RulesFetchResult.NoConnection("${failure.javaClass.simpleName}: ${failure.message}")
    }

    /** A maximum of 3 attempts, 2 seconds apart, and 15 seconds in total (path A). */
    private fun fetchWithRetries(): RulesFetchResult {
        val deadline = retryTiming.millis() + RETRY_TOTAL.toMillis()
        var last: RulesFetchResult = RulesFetchResult.NoConnection("no attempt")
        for (attempt in 1..RETRY_ATTEMPTS) {
            val remaining = deadline - retryTiming.millis()
            if (remaining <= 0) break
            last = retryTiming.withLimit(Duration.ofMillis(remaining)) { fetchOnce() }
                ?: RulesFetchResult.NoConnection("no response in ${RETRY_TOTAL.seconds} seconds")
            if (last is RulesFetchResult.ServerResponse) return last
            val pause = minOf(RETRY_PAUSE.toMillis(), deadline - retryTiming.millis())
            if (attempt == RETRY_ATTEMPTS || pause <= 0) break
            retryTiming.sleep(Duration.ofMillis(pause))
        }
        return last
    }

    /** Uses the server time and the file of a response. */
    private fun apply(result: RulesFetchResult): ResponseKind {
        if (result !is RulesFetchResult.ServerResponse) return ResponseKind.NO_CONNECTION
        result.serverTime?.let { serverTime ->
            val clockError = engine.serverTime(serverTime, result.counterAtResponse)
            if (clockError.abs() > WRONG_CLOCK_LIMIT && !wrongClockShown) {
                wrongClockShown = true
                wrongClock = clockError
            }
        }
        val body = result.body
        if (result.statusCode != 200 || body == null) return ResponseKind.NOT_USABLE
        return when (val parse = savedRules.replaceIfValid(body)) {
            is VersionRulesParse.Valid -> {
                rules = parse.rules
                updateRuleIds()
                ResponseKind.USABLE
            }
            is VersionRulesParse.UnknownSchema -> ResponseKind.USABLE
            is VersionRulesParse.NotValid -> ResponseKind.NOT_USABLE
        }
    }

    /** The expiry check: the current time with the probe, a write of the saved time, and the effective expiry. */
    private fun evaluate(): Boolean {
        now = engine.check()
        expiry = EffectiveExpiry.of(appVersion, rules, buildExpiry)
        expiryLog.record(expiry, now)
        lastCheckCounter = counter.millis()
        lastExpired = expiry.isExpired(now)
        lastWarning = expiry.isWarning(now)
        return lastExpired
    }

    private fun enterNormal() {
        mode = ExpiryMode.NORMAL
        flag.set(false)
        expiredDialog = false
        // Leaving expired mode is like a start: the warning shows at once if it applies.
        warningShownOn = null
        updateNotices()
    }

    private fun enterExpired(check: CheckState, fromSession: Boolean) {
        logger.info { "Expired mode: expiry ${expiry.date}, current time $now" }
        if (fromSession && mode == ExpiryMode.NORMAL) pendingBeforeExpired++
        mode = ExpiryMode.EXPIRED
        checkState = check
        flag.set(true)
        expiredDialog = true
        updateNotices()
    }

    /** The decisions of E6-S7. */
    private fun updateNotices() {
        val normal = mode == ExpiryMode.NORMAL
        val latest = rules?.latest
        val latestVersion = latest?.let { Version.parse(it.version) }
        updateNoticeRelease = latest.takeIf {
            normal && latestVersion != null && latestVersion > Version.parse(appVersion) &&
                hiddenUpdateVersion.let { hidden -> hidden == null || latestVersion > hidden }
        }

        if (!normal || !expiry.isWarning(now) || expiry.isExpired(now)) {
            expiryWarning = false
        } else {
            val today = now.atZone(zone()).toLocalDate()
            val newRule = expiry.rule?.id?.let { it in newRuleIds } == true
            if (warningShownOn.let { it == null || today > it } || newRule) {
                expiryWarning = true
                warningShownOn = today
            }
        }
        newRuleIds = emptySet()
    }

    private fun clockBehindVisible() = mode == ExpiryMode.NORMAL && engine.clockBehind && !clockBehindClosed

    /** The ids of the rules that the app sees for the first time in this session. */
    private fun updateRuleIds() {
        val ids = rules?.rules?.map { it.id }.orEmpty()
        newRuleIds = newRuleIds + ids.filterNot { it in seenRuleIds }
        seenRuleIds += ids
    }

    private fun stateAfter(kind: ResponseKind) =
        if (kind == ResponseKind.NO_CONNECTION) CheckState.NO_CONNECTION else CheckState.STILL_EXPIRED

    private fun checkStateAfter(kind: ResponseKind) =
        if (kind == ResponseKind.NO_CONNECTION) CheckState.NO_CONNECTION else CheckState.NONE

    /** The schedule of "Online check" (E6-S6). */
    private fun finishCheck(kind: ResponseKind) {
        checkRunning = false
        if (closed) return
        val delay = when {
            kind == ResponseKind.NO_CONNECTION && (mode == ExpiryMode.EXPIRED || engine.clockBehind) -> AFTER_NO_CONNECTION_WAITING
            mode == ExpiryMode.EXPIRED -> AFTER_STILL_EXPIRED
            kind == ResponseKind.USABLE -> AFTER_USABLE_FILE
            else -> AFTER_PROBLEM
        }
        logger.info { "Next online check in $delay" }
        nextCheckDue = counter.millis() + delay.toMillis()
        nextCheckTask = scheduler.schedule(delay) { locked { if (!closed) beginOnlineCheck(retries = false) } }
    }

    private fun scheduleTick() {
        tickTask?.cancel()
        if (!closed) tickTask = scheduler.schedule(TICK) { tick() }
    }

    private fun snapshot() = ExpiryState(
        mode = mode,
        check = checkState,
        currentTime = now,
        expiry = expiry,
        rules = rules,
        expiredDialog = expiredDialog,
        updateNotice = updateNoticeRelease,
        expiryWarning = expiryWarning,
        clockBehindNotice = clockBehindVisible(),
        wrongClock = wrongClock,
    )

    /** Runs [block] with the lock, then gives the events to the listeners with no lock. */
    private fun <T> locked(block: () -> T): T {
        val result = synchronized(lock) { block() }
        dispatch()
        return result
    }

    private fun dispatch() {
        synchronized(dispatchLock) {
            val (beforeExpired, state) = synchronized(lock) {
                val count = pendingBeforeExpired
                pendingBeforeExpired = 0
                val current = snapshot()
                val changed = current.takeIf { it != published }
                published = current
                count to changed
            }
            repeat(beforeExpired) { listeners.forEach { it.beforeExpiredMode() } }
            if (state != null) listeners.forEach { it.stateChanged(state) }
        }
    }

    companion object {
        val TICK: Duration = Duration.ofMinutes(1)
        val HOURLY: Duration = Duration.ofHours(1)
        const val RETRY_ATTEMPTS = 3
        val RETRY_PAUSE: Duration = Duration.ofSeconds(2)
        val RETRY_TOTAL: Duration = Duration.ofSeconds(15)
        val WRONG_CLOCK_LIMIT: Duration = Duration.ofHours(24)

        /** Expired mode, or "Clock behind" with no server time: the user waits for the network. */
        val AFTER_NO_CONNECTION_WAITING: Duration = Duration.ofMinutes(1)
        val AFTER_STILL_EXPIRED: Duration = Duration.ofMinutes(15)

        /** Normal mode: no connection, an HTTP status that is not 200, or a file that is not valid. */
        val AFTER_PROBLEM: Duration = Duration.ofMinutes(15)

        /** A valid file, or a file with an unknown schema. */
        val AFTER_USABLE_FILE: Duration = Duration.ofHours(24)

        private val logger = KotlinLogging.logger {}
    }
}
