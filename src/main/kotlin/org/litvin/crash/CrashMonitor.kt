package org.litvin.crash

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.shared.util.AppActivities
import org.litvin.shared.util.AppActivity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Finds the crash of the previous session and keeps the facts of this session (B-67,
 * `docs/crashes/b-67-crash-evidence.md`).
 *
 * - [start] reads the old marker. A marker with the state "open" means that the previous session did not close
 *   normally. Then the monitor looks for the JVM crash file of the old PID, writes its safe part to the log and to
 *   [crashFolder], and returns a [PreviousCrash]. Then it writes the marker of this session.
 * - [activitiesChanged] and [setFreeze] keep the current facts in the marker, so that the next start knows them.
 * - [closeNormally] writes the state "closed". The shutdown hook calls it. A JVM crash or a kill does not run it.
 *
 * Call [start] only after the instance lock: two instances must not write the same marker.
 */
class CrashMonitor(
    private val marker: SessionMarker,
    private val crashFolder: File,
    private val appVersion: String,
    private val pid: Long = ProcessHandle.current().pid(),
    private val workDir: File = File("").absoluteFile,
    private val searchFolders: (SessionMarker.Record) -> List<File> = ::defaultSearchFolders,
    private val clock: () -> Long = System::currentTimeMillis,
    private val addShutdownHook: (() -> Unit) -> Unit = { action ->
        Runtime.getRuntime().addShutdownHook(Thread({ runCatching(action) }, "crash-marker-close"))
    },
) {
    private val lock = Any()
    private var record: SessionMarker.Record? = null

    /** Returns the crash of the previous session, or null when it closed normally or when there is no marker. */
    fun start(): PreviousCrash? {
        val old = marker.read()
        val crash = old?.takeIf { it.open }?.let(::analyze)
        synchronized(lock) {
            record = SessionMarker.Record(
                open = true,
                pid = pid,
                startedAtMillis = clock(),
                workDir = workDir.path,
                appVersion = appVersion,
                activities = AppActivities.current(),
            ).also(marker::write)
        }
        AppActivities.listen(::activitiesChanged)
        addShutdownHook(::closeNormally)
        logger.info { "The session marker is open (pid $pid)." }
        crash?.let(::log)
        return crash
    }

    fun activitiesChanged(activities: Set<AppActivity>) = update { it.copy(activities = activities) }

    fun setFreeze(frozen: Boolean) = update { it.copy(freeze = frozen) }

    fun closeNormally() {
        AppActivities.listen(null)
        update { it.copy(open = false) }
    }

    private fun update(change: (SessionMarker.Record) -> SessionMarker.Record) {
        synchronized(lock) {
            val current = record ?: return
            val next = change(current)
            if (next == current) return
            record = next
            marker.write(next)
        }
    }

    private fun analyze(old: SessionMarker.Record): PreviousCrash {
        // A PID can come again. An old file of another process with the same PID is not this crash.
        val file = JvmCrashFile.find(old.pid, searchFolders(old))
            ?.takeIf { it.lastModified() >= old.startedAtMillis - CLOCK_TOLERANCE_MS }
        val facts = file?.let(JvmCrashFile::read)
        val crash = PreviousCrash(
            kind = when {
                facts != null -> facts.kind
                // The app cannot read the file, but the JVM wrote it only for a crash.
                file != null -> CrashKind.NATIVE
                old.freeze -> CrashKind.FREEZE
                else -> CrashKind.UNKNOWN
            },
            library = facts?.library ?: file?.let { CrashLibrary.OTHER },
            activities = old.activities,
            appVersion = old.appVersion,
            startedAtMillis = old.startedAtMillis,
            problem = facts?.problem,
            frame = facts?.frame,
            thread = facts?.thread,
        )
        if (file == null || facts == null) return crash
        val saved = save(old, file, crash, facts.safeText)
        return crash.copy(savedFile = saved)
    }

    /** Writes the summary and the safe part to [crashFolder], and keeps only the newest [KEEP_FILES] files. */
    private fun save(old: SessionMarker.Record, file: File, crash: PreviousCrash, safeText: String): File? = runCatching {
        crashFolder.mkdirs()
        val time = FILE_TIME.format(Instant.ofEpochMilli(file.lastModified()))
        val target = File(crashFolder, "crash-$time-pid${old.pid}.txt")
        target.writeText("${crash.summary()}\n\n$safeText\n", Charsets.UTF_8)
        crashFolder.listFiles { candidate -> candidate.isFile && candidate.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_FILES)
            ?.forEach { runCatching { it.delete() } }
        target
    }.getOrNull()

    private fun log(crash: PreviousCrash) {
        val savedText = crash.savedFile?.let { file -> runCatching { file.readText(Charsets.UTF_8) }.getOrNull() }
        logger.error {
            buildString {
                append("The previous session did not close normally.\n")
                if (savedText != null) {
                    append("The JVM crash file (safe part) follows.\n").append(savedText)
                } else {
                    append(crash.summary())
                }
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** The marker can be a little newer than the crash file on a file system with a coarse time. */
        private const val CLOCK_TOLERANCE_MS = 5_000L
        const val KEEP_FILES = 5

        private val FILE_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).withZone(ZoneId.systemDefault())

        /** The folders where the JVM can write its crash file: the old start folder, then the temp folders. */
        fun defaultSearchFolders(old: SessionMarker.Record): List<File> = listOfNotNull(
            old.workDir?.let(::File),
            System.getProperty("java.io.tmpdir")?.let(::File),
            System.getenv("TMPDIR")?.let(::File),
            System.getenv("TEMP")?.let(::File),
            File("/tmp"),
            System.getProperty("user.home")?.let(::File),
        ).map { it.absoluteFile }
    }
}
