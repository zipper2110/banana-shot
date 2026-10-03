package org.litvin.license.time

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant

/** Gives the last-modified time of a file that it writes now, or null when it cannot write or read the file. */
fun interface FileTimeProbe {
    fun fileTime(): Instant?
}

/**
 * Writes [file] and reads its last-modified time. Windows sets this time from its own clock, so a tool that changes
 * the time only for this process does not change it.
 */
class DataFolderFileTimeProbe(private val file: File) : FileTimeProbe {
    override fun fileTime(): Instant? = try {
        file.absoluteFile.parentFile?.mkdirs()
        Files.write(file.toPath(), PROBE_CONTENT)
        Files.getLastModifiedTime(file.toPath()).toInstant()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    companion object {
        const val FILE_NAME = "time-probe"

        private val PROBE_CONTENT = "time probe".toByteArray()

        fun inDataFolder(dataFolder: File): DataFolderFileTimeProbe = DataFolderFileTimeProbe(dataFolder.resolve(FILE_NAME))
    }
}

/** The system time: the later of the Java clock and, at an expiry check, the file time from the probe. */
class SystemTime(
    private val probe: FileTimeProbe,
    private val javaClock: () -> Instant = Instant::now,
) {
    /** The Java clock only. The minute tick uses it, because the tick does not read or write files. */
    fun withoutProbe(): Instant = javaClock()

    /** The later of the Java clock and the file time. A probe that fails gives the Java clock. */
    fun withProbe(): Instant {
        val javaTime = javaClock()
        val fileTime = probe.fileTime() ?: return javaTime
        return maxOf(javaTime, fileTime)
    }
}
