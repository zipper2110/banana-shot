package org.litvin.feedback

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.GZIPOutputStream

/**
 * The log data of a report (decision 12 of B-8): the last [limit] bytes of the log. They are the end of
 * `bananashot.log`, and the end of `bananashot.1.log` if the newest file is shorter than [limit].
 */
class FeedbackLog(private val folder: File, private val limit: Int = LIMIT) {
    /** The log text, or null when there is no log file. */
    fun read(): String? {
        val newestFile = folder.resolve(NEWEST)
        val newest = tail(newestFile, limit)
        val older = if (newestFile.length() < limit) tail(folder.resolve(OLDER), limit - newest.size) else ByteArray(0)
        if (newest.isEmpty() && older.isEmpty()) return null
        return (older + newest).toString(Charsets.UTF_8)
    }

    companion object {
        /** 2 MB of log text is about 200 KB with gzip. */
        const val LIMIT = 2 * 1024 * 1024
        const val NEWEST = "bananashot.log"
        const val OLDER = "bananashot.1.log"

        fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { bytes ->
            GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        /**
         * The last [count] bytes of [file]. A cut part starts after the first line break, so the text has no broken
         * line and no broken UTF-8 character.
         */
        internal fun tail(file: File, count: Int): ByteArray {
            if (count <= 0 || !file.isFile) return ByteArray(0)
            return runCatching {
                RandomAccessFile(file, "r").use { input ->
                    val length = input.length()
                    val start = (length - count).coerceAtLeast(0)
                    val bytes = ByteArray((length - start).toInt())
                    input.seek(start)
                    input.readFully(bytes)
                    if (start == 0L) bytes else bytes.copyOfRange(bytes.indexOf('\n'.code.toByte()) + 1, bytes.size)
                }
            }.getOrDefault(ByteArray(0))
        }
    }
}
