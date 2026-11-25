package org.litvin.autodetect

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * The identity of a video file for the work folder of the detection (chain-inventory G12). It is SHA-1 of the size
 * (decimal text), the first MiB and the last MiB of the file. The lab script `detect.py` (`video_id`) uses the same
 * method: it keeps the full value in `video.json` of the work folder and stops when the video is another one.
 */
object VideoIdentity {
    private const val MIB = 1 shl 20

    /** The full identity: 40 hex digits. */
    fun of(video: File): String {
        val size = video.length()
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(size.toString().toByteArray(Charsets.US_ASCII))
        RandomAccessFile(video, "r").use { file ->
            sha.update(read(file, 0L, minOf(size, MIB.toLong()).toInt()))
            if (size > MIB) {
                val from = maxOf(MIB.toLong(), size - MIB)
                sha.update(read(file, from, (size - from).toInt()))
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    /** The short identity in the name of a work folder: 8 hex digits. */
    fun short(video: File): String = of(video).take(8)

    private fun read(file: RandomAccessFile, from: Long, count: Int): ByteArray {
        val bytes = ByteArray(count)
        file.seek(from)
        file.readFully(bytes)
        return bytes
    }
}
