package org.litvin.app

import org.litvin.analytics.AnalyticsCrash
import org.litvin.crash.CrashKind
import org.litvin.crash.CrashLibrary
import org.litvin.crash.PreviousCrash
import org.litvin.shared.util.AppActivity
import org.litvin.ui.commons.UserDialogService
import java.awt.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReportingTest {
    private class FakeDialogs : UserDialogService {
        val shown = mutableListOf<Pair<String, String>>()
        var report: (() -> Unit)? = null
        override fun showInfo(parent: Component?, message: String, title: String) = Unit
        override fun showError(parent: Component?, message: String, title: String) = Unit
        override fun confirm(parent: Component?, message: String, title: String) = false
        override fun showReportableError(parent: Component?, message: String, title: String, onReport: (() -> Unit)?) {
            shown += title to message
            report = onReport
        }
    }

    private val native = PreviousCrash(
        kind = CrashKind.NATIVE,
        library = CrashLibrary.COREVIDEO,
        activities = setOf(AppActivity.SLEEP_WAKE),
        appVersion = "1.4.1",
        startedAtMillis = 0L,
        frame = "C  [CoreVideo+0x386c]  CVXTime::translateTime",
    )

    @Test
    fun `the popup shows for a crash with evidence and opens the report with the summary`() {
        val dialogs = FakeDialogs()
        val reports = mutableListOf<Pair<String, String>>()

        CrashReporting.showPopup(native, null, dialogs) { title, message -> reports += title to message }

        assertEquals(1, dialogs.shown.size)
        assertTrue(dialogs.shown[0].first.contains("BananaShot"))
        dialogs.report!!.invoke()
        assertEquals(dialogs.shown[0].first, reports.single().first)
        assertTrue(reports.single().second.contains("frame: C  [CoreVideo+0x386c]"))
    }

    @Test
    fun `no popup without a crash or without evidence`() {
        val dialogs = FakeDialogs()
        CrashReporting.showPopup(null, null, dialogs, null)
        CrashReporting.showPopup(native.copy(kind = CrashKind.UNKNOWN, library = null), null, dialogs, null)
        assertTrue(dialogs.shown.isEmpty())
    }

    @Test
    fun `the analytics get only the values of the closed lists`() {
        assertNull(CrashReporting.analyticsCrash(null))
        assertEquals(
            AnalyticsCrash(AnalyticsCrash.Kind.NATIVE, AnalyticsCrash.Library.COREVIDEO, setOf(AnalyticsCrash.Activity.SLEEP_WAKE)),
            CrashReporting.analyticsCrash(native),
        )
    }

    @Test
    fun `each crash value has an analytics value with the same key`() {
        assertEquals(CrashKind.entries.map { it.key }, AnalyticsCrash.Kind.entries.map { it.key })
        assertEquals(CrashLibrary.entries.map { it.key }, AnalyticsCrash.Library.entries.map { it.key })
        assertEquals(AppActivity.entries.map { it.key } + "idle", AnalyticsCrash.Activity.entries.map { it.key })
    }
}
