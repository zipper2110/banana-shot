package org.litvin.ui.expiry

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.litvin.license.EffectiveExpiry
import org.litvin.license.LatestRelease
import org.litvin.license.VersionRule
import org.litvin.license.VersionRules
import org.litvin.license.check.CheckState
import org.litvin.license.check.ExpiryCommands
import org.litvin.license.check.ExpiryListener
import org.litvin.license.check.ExpiryMode
import org.litvin.license.check.ExpiryState
import org.litvin.license.update.UpdateAndRestart
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.swing.AbstractButton
import javax.swing.JFrame
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E8-S1 to E8-S5, E8-S7, and E8-S8: the expiry user interface shows the states of the controller. */
class ExpiryUiTest {
    private val commands = RecordingCommands()
    private val modes = mutableListOf<Boolean>()
    private var beforeExpired = 0
    private val tasks = mutableListOf<Runnable>()
    private var closed = 0
    private var exited = 0
    private val opened = mutableListOf<String>()
    private lateinit var frame: JFrame
    private lateinit var ui: ExpiryUi

    @BeforeEach
    fun setUp() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        onEdt {
            frame = JFrame()
            val runner = UpdateRunner(
                update = UpdateAndRestart(downloader = { _, target, progress -> target.writeText("setup"); progress(5, 5) }, launcher = { }),
                background = { tasks += it },
                exportRuns = { false },
                closeSequence = { closed++ },
                exit = { exited++ },
                openUrl = { opened += it },
                parent = { frame },
            )
            ui = ExpiryUi(
                commands,
                runner,
                frame,
                beforeExpiredMode = { beforeExpired++ },
                setExpiredMode = { modes += it },
                windowShows = { false },
                appVersion = "1.2.0",
                zone = { ZoneOffset.UTC },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        if (::ui.isInitialized) onEdt { ui.close(); frame.dispose() }
    }

    @Test
    fun `normal mode with no notice shows no bar and keeps the tabs`() {
        show(state())

        assertTrue(modes.isEmpty())
        assertEquals(0, onEdt { ui.bar.heightForWidth(1000) })
    }

    @Test
    fun `expired mode shows the banner and only the Export tab, and the dialog has the texts`() {
        show(state(mode = ExpiryMode.EXPIRED, check = CheckState.CHECKING, rules = rules(latest = "1.4.0"), dialog = true))

        assertEquals(listOf(true), modes)
        onEdt {
            assertTrue(ui.banner.isVisible)
            val text = ui.banner.shownText
            assertTrue(text.contains(ExpiryTexts.EXPIRED_TITLE))
            assertTrue(text.contains("Version 1.4.0 is available."))
            assertTrue(text.contains("The date that"))
            assertTrue(text.contains("Checking…"))
            assertNull(find(ui.banner, "expiry-banner-close"), "The user cannot close the banner")
            val checkNow = find(ui.banner, "expiry-banner-check-now") as AbstractButton
            assertFalse(checkNow.isEnabled)
            assertEquals("Checking…", checkNow.text)
            assertTrue(ui.dialog.shownText.startsWith(ExpiryTexts.EXPIRED_DEFAULT))
            assertTrue(ui.dialog.shownText.contains("Version 1.4.0 is available."))
        }
    }

    @Test
    fun `no connection shows the cannot connect text, and still expired shows no extra text`() {
        show(state(mode = ExpiryMode.EXPIRED, check = CheckState.NO_CONNECTION))
        onEdt { assertTrue(ui.banner.shownText.contains(ExpiryTexts.NO_CONNECTION)) }

        show(state(mode = ExpiryMode.EXPIRED, check = CheckState.STILL_EXPIRED))
        onEdt {
            assertFalse(ui.banner.shownText.contains(ExpiryTexts.NO_CONNECTION))
            assertFalse(ui.banner.shownText.contains(ExpiryTexts.CHECKING))
            assertTrue((find(ui.banner, "expiry-banner-check-now") as AbstractButton).isEnabled)
        }
    }

    @Test
    fun `the dialog shows the rule message, and a latest version that is the app version shows only Manually download update`() {
        val rule = VersionRule("r1", null, "1.2.0", LocalDate.of(2026, 11, 1), "Version 1.2 has a bug.")
        show(state(mode = ExpiryMode.EXPIRED, rules = rules(latest = "1.2.0"), expiry = EffectiveExpiry(rule.stopsOn, rule)))

        onEdt {
            assertTrue(ui.dialog.shownText.startsWith("Version 1.2 has a bug."))
            assertFalse(ui.dialog.shownText.contains("is available"))
            assertFalse(find(ui.banner, "expiry-banner-update-and-restart")!!.isVisible)
            assertTrue(find(ui.banner, "expiry-banner-download-update")!!.isVisible)
        }
    }

    @Test
    fun `leaving expired mode removes the banner and shows the tabs again`() {
        show(state(mode = ExpiryMode.EXPIRED))
        show(state(mode = ExpiryMode.NORMAL))

        assertEquals(listOf(true, false), modes)
        onEdt { assertFalse(ui.banner.isVisible) }
    }

    @Test
    fun `before expired mode runs on the EDT`() {
        ui.beforeExpiredMode()
        onEdt { }

        assertEquals(1, beforeExpired)
    }

    @Test
    fun `the update notice shows the version and the notes as plain text, and Later calls the controller`() {
        val notes = "<html><img src='https://example.invalid/x.png'>Faster export</html>"
        show(state(updateNotice = LatestRelease("1.4.0", "https://example.invalid/releases", notes = notes)))

        onEdt {
            assertTrue(ui.updateNotice.isVisible)
            assertEquals("${ExpiryTexts.UPDATE_TITLE}\nVersion 1.4.0 is available.\n$notes", ui.updateNotice.shownText)
            (find(ui.updateNotice, "update-notice-later") as AbstractButton).doClick()
            (find(ui.updateNotice, "update-notice-download-update") as AbstractButton).doClick()
        }
        assertEquals(listOf("later"), commands.calls)
        assertEquals(listOf("https://example.invalid/releases"), opened)
    }

    @Test
    fun `the update notice does not show in expired mode`() {
        show(state(mode = ExpiryMode.EXPIRED, updateNotice = LatestRelease("1.4.0", "https://example.invalid/releases")))

        onEdt { assertFalse(ui.updateNotice.isVisible) }
    }

    @Test
    fun `the expiry warning shows the moment and the message, and the user can close it`() {
        val rule = VersionRule("r1", null, "1.2.0", LocalDate.of(2026, 11, 20), "<b>Please update</b>")
        show(state(warning = true, expiry = EffectiveExpiry(rule.stopsOn, rule)))

        onEdt {
            val text = ui.warning.shownText
            assertTrue(text.contains("This version works until 20 November 2026, 00:00."))
            assertTrue(text.contains("<b>Please update</b>"))
            (find(ui.warning, "expiry-warning-close") as AbstractButton).doClick()
        }
        assertEquals(listOf("closeWarning"), commands.calls)
    }

    @Test
    fun `the clock behind notice has Check now and the cannot connect text`() {
        show(state(clockBehind = true, check = CheckState.NO_CONNECTION))

        onEdt {
            assertTrue(ui.clockBehind.shownText.contains(ExpiryTexts.CLOCK_BEHIND))
            assertTrue(ui.clockBehind.shownText.contains(ExpiryTexts.NO_CONNECTION_SHORT))
            (find(ui.clockBehind, "clock-behind-check-now") as AbstractButton).doClick()
            (find(ui.clockBehind, "clock-behind-close") as AbstractButton).doClick()
        }
        assertEquals(listOf("checkNow", "closeClockBehind"), commands.calls)
    }

    @Test
    fun `the wrong clock notice gives the days and the direction`() {
        show(state(wrongClock = Duration.ofHours(-36)))

        onEdt {
            assertTrue(ui.wrongClock.shownText.contains("2 days behind"))
            (find(ui.wrongClock, "wrong-clock-close") as AbstractButton).doClick()
        }
        assertEquals(listOf("closeWrongClock"), commands.calls)
    }

    @Test
    fun `Update and restart shows the progress on each button, then closes the app and exits`() {
        show(state(mode = ExpiryMode.EXPIRED, rules = rules(latest = "1.4.0")))
        onEdt { (find(ui.banner, "expiry-banner-update-and-restart") as AbstractButton).doClick() }

        onEdt {
            val dialogButton = find(ui.dialog.dialog, "expired-dialog-update-and-restart") as AbstractButton
            assertEquals("Downloading…", dialogButton.text)
            assertFalse(dialogButton.isEnabled)
            // A second click has no effect.
            (find(ui.banner, "expiry-banner-update-and-restart") as AbstractButton).doClick()
        }
        assertEquals(1, tasks.size)

        tasks.single().run()
        onEdt { }

        assertEquals(1, closed)
        assertEquals(1, exited)
        onEdt { assertEquals(ExpiryTexts.UPDATE_AND_RESTART, (find(ui.banner, "expiry-banner-update-and-restart") as AbstractButton).text) }
    }

    private fun show(state: ExpiryState) {
        onEdt { ui.apply(state) }
        // The dialog sync runs in a later event.
        onEdt { }
    }

    private fun rules(latest: String?) = VersionRules(latest?.let { LatestRelease(it, "https://example.invalid/releases") }, emptyList())

    private fun state(
        mode: ExpiryMode = ExpiryMode.NORMAL,
        check: CheckState = CheckState.NONE,
        rules: VersionRules? = null,
        expiry: EffectiveExpiry = EffectiveExpiry(LocalDate.of(2026, 11, 1), null),
        dialog: Boolean = false,
        updateNotice: LatestRelease? = null,
        warning: Boolean = false,
        clockBehind: Boolean = false,
        wrongClock: Duration? = null,
    ) = ExpiryState(
        mode = mode,
        check = check,
        currentTime = Instant.parse("2026-11-02T10:00:00Z"),
        expiry = expiry,
        rules = rules,
        expiredDialog = dialog,
        updateNotice = updateNotice,
        expiryWarning = warning,
        clockBehindNotice = clockBehind,
        wrongClock = wrongClock,
    )

    private fun find(root: Component, name: String): Component? {
        if (root.name == name) return root
        if (root is Container) root.components.forEach { child -> find(child, name)?.let { return it } }
        if (root is javax.swing.RootPaneContainer) return find(root.rootPane, name)
        return null
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        EventQueue.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private class RecordingCommands : ExpiryCommands {
        val calls = mutableListOf<String>()
        override val state: ExpiryState get() = error("not used")
        override fun addListener(listener: ExpiryListener) = Unit
        override fun checkNow() { calls += "checkNow" }
        override fun laterUpdateNotice() { calls += "later" }
        override fun closeExpiryWarning() { calls += "closeWarning" }
        override fun closeClockBehindNotice() { calls += "closeClockBehind" }
        override fun closeWrongClockNotice() { calls += "closeWrongClock" }
        override fun showExpiredDialog() { calls += "showDialog" }
        override fun closeExpiredDialog() { calls += "closeDialog" }
    }
}
