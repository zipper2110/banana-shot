package org.litvin.crash

import org.litvin.shared.util.AppActivities
import org.litvin.shared.util.AppActivity
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashMonitorTest {
    private lateinit var root: File
    private lateinit var temp: File
    private val hooks = mutableListOf<() -> Unit>()
    private var now = 1_790_000_000_000L

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("crash-monitor").toFile()
        temp = root.resolve("tmp").apply { mkdirs() }
        AppActivities.reset()
    }

    @AfterTest
    fun tearDown() {
        AppActivities.reset()
        root.deleteRecursively()
    }

    private val markerFile get() = root.resolve("last-session.properties")
    private val crashFolder get() = root.resolve("logs/crashes")

    private fun monitor(pid: Long) = CrashMonitor(
        marker = SessionMarker(markerFile),
        crashFolder = crashFolder,
        appVersion = "1.4.1",
        pid = pid,
        workDir = root.resolve("start"),
        searchFolders = { old -> listOfNotNull(old.workDir?.let(::File), temp) },
        clock = { now },
        addShutdownHook = { hooks += it },
    )

    @Test
    fun `the first start and a start after a normal exit find no crash`() {
        assertNull(monitor(1).start())
        hooks.forEach { it() }
        hooks.clear()

        assertNull(monitor(2).start())
        val record = SessionMarker(markerFile).read()!!
        assertTrue(record.open)
        assertEquals(2, record.pid)
        assertEquals("1.4.1", record.appVersion)
    }

    @Test
    fun `a session that did not close is an unknown crash with its activities`() {
        monitor(1).start()
        AppActivities.set(AppActivity.EXPORT, "job-1", true)

        val crash = monitor(2).start()

        assertNotNull(crash)
        assertEquals(CrashKind.UNKNOWN, crash.kind)
        assertNull(crash.library)
        assertEquals(setOf(AppActivity.EXPORT), crash.activities)
        assertFalse(crash.hasEvidence)
    }

    @Test
    fun `a frozen session is a freeze crash`() {
        val first = monitor(1)
        first.start()
        first.setFreeze(true)

        val crash = monitor(2).start()!!

        assertEquals(CrashKind.FREEZE, crash.kind)
        assertTrue(crash.hasEvidence)
    }

    @Test
    fun `a session that recovers from a freeze and then is killed is an unknown crash`() {
        val first = monitor(1)
        first.start()
        first.setFreeze(true)
        first.setFreeze(false)

        assertEquals(CrashKind.UNKNOWN, monitor(2).start()!!.kind)
    }

    @Test
    fun `a JVM crash file gives a native crash and its safe part goes to the crash folder`() {
        monitor(4242).start()
        AppActivities.set(AppActivity.PLAYBACK, this, true)
        now += 60_000
        temp.resolve(JvmCrashFile.fileName(4242)).apply {
            writeText(JvmCrashFileTest.NATIVE_CRASH)
            setLastModified(now)
        }
        AppActivities.reset()

        val crash = monitor(4243).start()!!

        assertEquals(CrashKind.NATIVE, crash.kind)
        assertEquals(CrashLibrary.COREVIDEO, crash.library)
        assertEquals(setOf(AppActivity.PLAYBACK), crash.activities)
        assertEquals("native thread", crash.thread)
        val saved = assertNotNull(crash.savedFile)
        assertEquals(crashFolder, saved.parentFile)
        val text = saved.readText()
        assertTrue(text.startsWith("crash: native\nlibrary: corevideo\n"))
        assertTrue(text.contains("CVDisplayLink::runIOThread"))
        assertFalse(text.contains("SECRET_TOKEN"))
    }

    @Test
    fun `an old crash file of another process with the same PID is not the crash`() {
        temp.resolve(JvmCrashFile.fileName(7)).apply {
            writeText(JvmCrashFileTest.NATIVE_CRASH)
            setLastModified(now - 3_600_000)
        }
        monitor(7).start()

        assertEquals(CrashKind.UNKNOWN, monitor(8).start()!!.kind)
    }

    @Test
    fun `the crash folder keeps only the newest files`() {
        crashFolder.mkdirs()
        repeat(CrashMonitor.KEEP_FILES + 2) { index -> crashFolder.resolve("crash-2000-01-0${index}_00-00-00-pid$index.txt").writeText("old") }
        monitor(4242).start()
        temp.resolve(JvmCrashFile.fileName(4242)).apply {
            writeText(JvmCrashFileTest.NATIVE_CRASH)
            setLastModified(now + 1_000)
        }

        monitor(4243).start()

        val files = crashFolder.listFiles()!!.map { it.name }
        assertEquals(CrashMonitor.KEEP_FILES, files.size)
        assertTrue(files.any { it.endsWith("-pid4242.txt") })
    }

    @Test
    fun `the marker keeps the activities while the session runs`() {
        monitor(1).start()
        AppActivities.set(AppActivity.SLEEP_WAKE, "sleep", true)
        assertEquals(setOf(AppActivity.SLEEP_WAKE), SessionMarker(markerFile).read()!!.activities)

        AppActivities.set(AppActivity.SLEEP_WAKE, "sleep", false)
        assertEquals(emptySet(), SessionMarker(markerFile).read()!!.activities)
    }

    @Test
    fun `the summary names the activities, or idle`() {
        val crash = PreviousCrash(CrashKind.FREEZE, null, emptySet(), "1.4.1", 0L)
        assertEquals("crash: freeze\nactivity: idle\nversion: 1.4.1", crash.summary())
        val busy = crash.copy(activities = setOf(AppActivity.EXPORT, AppActivity.PLAYBACK))
        assertTrue(busy.summary().contains("activity: export, playback"))
    }
}
