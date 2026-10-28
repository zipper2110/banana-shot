package org.litvin.ui.expiry

import org.litvin.AppInfo
import org.litvin.license.LatestRelease
import org.litvin.license.check.CheckState
import org.litvin.license.check.ExpiryCommands
import org.litvin.license.check.ExpiryListener
import org.litvin.license.check.ExpiryMode
import org.litvin.license.check.ExpiryState
import org.litvin.license.update.UpdateOptions
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.commons.UiButton
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.HierarchyEvent
import java.time.ZoneId

/**
 * The expiry user interface (E8): it shows each [ExpiryState] of the controller. It changes the components on the EDT
 * only. The controller calls the listener on its own threads, so the listener uses `invokeLater` and never waits.
 */
internal class ExpiryUi(
    private val commands: ExpiryCommands,
    runner: UpdateRunner,
    private val parent: Component,
    /** Before the app goes to expired mode during a session: save the open project and stop the player. */
    private val beforeExpiredMode: () -> Unit,
    /** Expired mode: show only the Export tab, and refuse a new export. False shows the tabs again. */
    private val setExpiredMode: (Boolean) -> Unit,
    /** True when the main window shows. The dialog shows only over a visible window. */
    private val windowShows: () -> Boolean,
    private val appVersion: String = AppInfo.version,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** The "What's new" popup (B-54). Null shows no popup. */
    private val whatsNew: WhatsNewSupport? = null,
) : ExpiryListener, AutoCloseable {
    val bar = ExpiryBar()

    private fun checkNowButton(rowName: String) = UiButton(ExpiryTexts.CHECK_NOW).apply {
        name = "$rowName-check-now"
        addActionListener { commands.checkNow() }
    }

    private val bannerUpdate = UpdateButtons("expiry-banner", runner)
    private val bannerCheckNow = checkNowButton("expiry-banner")
    val banner = NoticeRow("expiry-banner", MessageKind.ERROR, onClose = null).apply {
        setButtons(listOf(bannerCheckNow) + bannerUpdate.all)
    }

    private val noticeUpdate = UpdateButtons("update-notice", runner)
    /** The site page of the release in the update notice (B-54). */
    private var noticePageUrl: String? = null
    val noticeReadMore = SiteLink(ExpiryTexts.READ_MORE) { noticePageUrl?.let(runner::openDownloadPage) }.apply {
        name = "update-notice-read-more"
    }
    private val noticeReadMoreRow = SiteLink.row(noticeReadMore)
    val updateNotice = NoticeRow("update-notice", MessageKind.HINT, onClose = null).apply {
        setButtons(
            noticeUpdate.all + UiButton(ExpiryTexts.LATER).apply {
                name = "update-notice-later"
                addActionListener { commands.laterUpdateNotice() }
            },
        )
        setLink(noticeReadMoreRow)
    }

    private val whatsNewUpdate = UpdateButtons("whats-new", runner)
    val whatsNewDialog = WhatsNewDialog(parent, whatsNewUpdate, runner::openDownloadPage)
    /** The version whose popup this session loads or showed. The popup does not load two times. */
    private var whatsNewRequested: String? = null

    private val warningUpdate = UpdateButtons("expiry-warning", runner)
    val warning = NoticeRow("expiry-warning", MessageKind.WARNING, onClose = commands::closeExpiryWarning).apply {
        setButtons(warningUpdate.all)
    }

    private val clockBehindCheckNow = checkNowButton("clock-behind")
    val clockBehind = NoticeRow("clock-behind", MessageKind.WARNING, onClose = commands::closeClockBehindNotice).apply {
        setButtons(listOf(clockBehindCheckNow))
    }

    val wrongClock = NoticeRow("wrong-clock", MessageKind.WARNING, onClose = commands::closeWrongClockNotice)

    private val dialogUpdate = UpdateButtons("expired-dialog", runner)
    val dialog = ExpiredDialog(parent, dialogUpdate, onCheckNow = commands::checkNow, onClose = commands::closeExpiredDialog)

    private var shown: ExpiryState? = null
    /** The main window starts in normal mode. Thus, only a change of the mode changes the tabs. */
    private var expired = false
    private var closed = false

    init {
        listOf(banner, updateNotice, warning, clockBehind, wrongClock).forEach(bar::addRow)
        // The state can arrive before the main window shows. The popup then shows when the window shows.
        bar.addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && bar.isShowing) syncWhatsNew()
        }
    }

    /** Shows the current state and listens for changes. Call it on the EDT. */
    fun install() {
        check(EventQueue.isDispatchThread()) { "Install the expiry user interface on the EDT" }
        commands.addListener(this)
        apply(commands.state)
    }

    override fun beforeExpiredMode() = EventQueue.invokeLater { if (!closed) beforeExpiredMode.invoke() }

    override fun stateChanged(state: ExpiryState) = EventQueue.invokeLater { apply(state) }

    internal fun apply(state: ExpiryState) {
        if (closed) return
        shown = state
        val isExpired = state.mode == ExpiryMode.EXPIRED
        if (expired != isExpired) {
            expired = isExpired
            setExpiredMode(isExpired)
        }
        val options = UpdateOptions.of(appVersion, state.rules)
        listOf(bannerUpdate, warningUpdate, dialogUpdate).forEach { it.show(options) }
        // The notice shows the release of the notice.
        noticeUpdate.show(state.updateNotice?.let { UpdateOptions.of(appVersion, it) } ?: options)
        val checking = state.check == CheckState.CHECKING
        listOf(bannerCheckNow, clockBehindCheckNow).forEach {
            it.isEnabled = !checking
            it.text = if (checking) ExpiryTexts.CHECKING else ExpiryTexts.CHECK_NOW
        }

        val content = expiredContent(state)
        dialog.update(content)
        banner.isVisible = isExpired
        if (isExpired) {
            banner.setTexts(ExpiryTexts.EXPIRED_TITLE, listOf(ExpiryTexts.EXPIRED_BANNER, content.newVersion, content.dateUsed, content.checkState))
        }

        val normal = state.mode == ExpiryMode.NORMAL
        val latest = state.updateNotice
        updateNotice.isVisible = normal && latest != null
        if (normal && latest != null) {
            updateNotice.setTexts(ExpiryTexts.UPDATE_TITLE, listOf(ExpiryTexts.versionAvailable(latest.version), latest.notesIn()))
            noticePageUrl = latest.pageUrl
            noticeReadMoreRow.isVisible = latest.pageUrl != null
        }
        // The popup shows only in normal mode. The dialog of expired mode can show next.
        if (!normal) whatsNewDialog.hide()

        warning.isVisible = normal && state.expiryWarning
        if (warning.isVisible) {
            warning.setTexts(ExpiryTexts.WARNING_TITLE, listOf(ExpiryTexts.warning(state.expiry, zone()), state.expiry.message))
        }

        clockBehind.isVisible = normal && state.clockBehindNotice
        if (clockBehind.isVisible) {
            clockBehind.setTexts(ExpiryTexts.CLOCK_BEHIND_TITLE, listOf(ExpiryTexts.CLOCK_BEHIND, ExpiryTexts.clockBehindCheckState(state.check)))
        }

        val wrong = state.wrongClock
        val days = state.wrongClockDays
        wrongClock.isVisible = wrong != null && days != null
        if (wrong != null && days != null) {
            wrongClock.setTexts(ExpiryTexts.WRONG_CLOCK_TITLE, listOf(ExpiryTexts.wrongClock(wrong, days)))
        }

        bar.revalidate()
        bar.repaint()
        // The modal dialog blocks the call that shows it. Thus, it shows in a separate event.
        EventQueue.invokeLater(::syncDialog)
        EventQueue.invokeLater(::syncWhatsNew)
    }

    /**
     * Starts the load of the "What's new" page when a new version has a page, the main window shows, and the popup of
     * this version did not show before (B-54).
     */
    private fun syncWhatsNew() {
        val support = whatsNew ?: return
        val state = shown ?: return
        val latest = state.updateNotice ?: return
        val url = latest.whatsNewUrl ?: return
        if (closed || state.mode != ExpiryMode.NORMAL || !windowShows()) return
        if (whatsNewRequested == latest.version || !support.seen.isNew(latest.version)) return
        whatsNewRequested = latest.version
        // The page of the app language (B-11 E6). With no such page, or when it does not load, the English page.
        val ownUrl = latest.whatsNewUrlOnlyIn()?.takeIf { it != url }
        support.background.execute {
            val html = ownUrl?.let { support.load(it, WhatsNewDialog.IMAGE_WIDTH) } ?: support.load(url, WhatsNewDialog.IMAGE_WIDTH)
            EventQueue.invokeLater { showWhatsNew(support, latest, html) }
        }
    }

    private fun showWhatsNew(support: WhatsNewSupport, latest: LatestRelease, html: String?) {
        val state = shown
        if (closed || state == null || state.mode != ExpiryMode.NORMAL || state.updateNotice?.version != latest.version || !windowShows()) {
            // The popup of this version can show at the next start.
            whatsNewRequested = null
            return
        }
        if (!support.seen.isNew(latest.version)) return
        support.seen.markShown(latest.version)
        whatsNewUpdate.show(UpdateOptions.of(appVersion, latest))
        // Modal: this call returns when the popup closes.
        whatsNewDialog.show(html ?: fallbackHtml(latest), latest.pageUrl)
    }

    /** With no page, the popup shows the version and the `notes` of the app language as plain text. */
    private fun fallbackHtml(latest: LatestRelease): String = buildString {
        append("<p>").append(escapeHtml(ExpiryTexts.versionAvailable(latest.version))).append("</p>")
        latest.notesIn()?.takeIf { it.isNotBlank() }?.let { notes ->
            append("<p>").append(escapeHtml(notes).replace("\n", "<br>")).append("</p>")
        }
    }

    private fun escapeHtml(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun syncDialog() {
        val state = shown ?: return
        if (closed || !state.expiredDialog || state.mode != ExpiryMode.EXPIRED || !windowShows()) {
            dialog.hide()
            return
        }
        dialog.show()
    }

    private fun expiredContent(state: ExpiryState) = ExpiredContent(
        message = state.expiry.message ?: ExpiryTexts.EXPIRED_DEFAULT,
        newVersion = ExpiryTexts.newVersion(state.rules, appVersion),
        dateUsed = ExpiryTexts.dateUsed(state.currentTime, zone()),
        checkState = ExpiryTexts.checkState(state.check),
        checking = state.check == CheckState.CHECKING,
    )

    override fun close() {
        closed = true
        dialog.hide()
        dialog.dialog.dispose()
        whatsNewDialog.hide()
        whatsNewDialog.dialog.dispose()
    }
}
