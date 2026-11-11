package org.litvin.crash

import org.litvin.shared.util.AppActivity
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * The marker file of the app session (B-67). The app writes it at the start with the state "open". A normal exit
 * writes the state "closed". A crash, a kill, or a power loss leaves the state "open".
 *
 * Each write replaces the full file, so that a crash during a write leaves the old file or the new file.
 */
class SessionMarker(private val file: File) {
    data class Record(
        val open: Boolean,
        val pid: Long,
        val startedAtMillis: Long,
        val workDir: String?,
        val appVersion: String?,
        val activities: Set<AppActivity> = emptySet(),
        val freeze: Boolean = false,
    )

    /** The record in the file, or null when there is no file or the file is not correct. */
    fun read(): Record? = runCatching {
        if (!file.isFile) return null
        val properties = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
        val activityKeys = properties.getProperty(ACTIVITIES).orEmpty().split(',').map { it.trim() }.toSet()
        Record(
            open = properties.getProperty(STATE) == OPEN,
            pid = properties.getProperty(PID)?.toLongOrNull() ?: return null,
            startedAtMillis = properties.getProperty(STARTED_AT)?.toLongOrNull() ?: 0L,
            workDir = properties.getProperty(WORK_DIR),
            appVersion = properties.getProperty(APP_VERSION),
            activities = AppActivity.entries.filter { it.key in activityKeys }.toSet(),
            freeze = properties.getProperty(FREEZE) == "true",
        )
    }.getOrNull()

    /** Writes [record]. A failure does not stop the app. */
    fun write(record: Record) {
        runCatching {
            file.absoluteFile.parentFile?.mkdirs()
            val properties = Properties().apply {
                setProperty(STATE, if (record.open) OPEN else CLOSED)
                setProperty(PID, record.pid.toString())
                setProperty(STARTED_AT, record.startedAtMillis.toString())
                record.workDir?.let { setProperty(WORK_DIR, it) }
                record.appVersion?.let { setProperty(APP_VERSION, it) }
                setProperty(ACTIVITIES, record.activities.joinToString(",") { it.key })
                setProperty(FREEZE, record.freeze.toString())
            }
            val temporary = File(file.absoluteFile.parentFile, "${file.name}.tmp")
            temporary.writer(Charsets.UTF_8).use { properties.store(it, "BananaShot session marker (B-67)") }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private companion object {
        const val STATE = "state"
        const val OPEN = "open"
        const val CLOSED = "closed"
        const val PID = "pid"
        const val STARTED_AT = "startedAt"
        const val WORK_DIR = "workDir"
        const val APP_VERSION = "appVersion"
        const val ACTIVITIES = "activities"
        const val FREEZE = "freeze"
    }
}
