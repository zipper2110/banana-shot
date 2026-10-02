package org.litvin.media

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** A defect in the box structure of an MP4 or MOV file that proves why the file does not open. */
enum class Mp4Defect {
    /** A box says that it is larger than the rest of the file. The end of the file is missing. */
    TRUNCATED,

    /**
     * After the last complete box, the file contains only zeros. A copy sets the full file size first
     * and then writes the data, so the copy stopped before the end.
     */
    UNWRITTEN_END,

    /**
     * All boxes are complete, but there is no index (moov box). The device writes the index when the recording stops,
     * so the recording did not stop correctly.
     */
    NOT_FINALIZED,
}

/**
 * Reads the top-level boxes of an MP4 or MOV file. Each box starts with its size and its type,
 * so the check reads only some bytes of the file, also for a file of 50 GB.
 */
object Mp4Structure {
    private const val MAX_BOXES = 10_000

    // A copy writes the file in blocks, so an unwritten end has at least this many zeros.
    private const val ZERO_PROBE_BYTES = 4096

    /** Returns the defect, or null when the file is not an MP4 or MOV file, when it has no defect, or when it cannot be read. */
    fun inspect(file: File): Mp4Defect? = try {
        RandomAccessFile(file, "r").use { inspect(it) }
    } catch (_: IOException) {
        null
    }

    private fun inspect(input: RandomAccessFile): Mp4Defect? {
        val length = input.length()
        var position = 0L
        var hasMediaData = false
        var hasIndex = false
        repeat(MAX_BOXES) { index ->
            if (position == length) return if (hasMediaData && !hasIndex) Mp4Defect.NOT_FINALIZED else null
            if (length - position < 8) return null
            input.seek(position)
            val size32 = input.readInt().toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4).also { input.readFully(it) }
            val type = typeBytes.toString(Charsets.ISO_8859_1)
            if (index == 0 && type != "ftyp") return null
            if (size32 == 0L && typeBytes.all { it == 0.toByte() }) {
                return if (isZeros(input, position, length)) Mp4Defect.UNWRITTEN_END else null
            }
            // Damaged data is not a box. Its "size" proves nothing, so the cause is not known.
            if (!typeBytes.all(::isBoxTypeByte)) return null
            val size = when (size32) {
                // The box goes to the end of the file. A recorder writes this size while it records.
                0L -> length - position
                1L -> if (length - position < 16) return Mp4Defect.TRUNCATED else input.readLong()
                else -> size32
            }
            when {
                // A recorder can also leave a 64-bit size of 0 in the media data box while it records.
                type == "mdat" && size == 0L -> return if (hasIndex) null else Mp4Defect.NOT_FINALIZED
                size < 8 -> return null
                size > length - position -> return Mp4Defect.TRUNCATED
            }
            // Read all boxes also after the index: a file with the index at the start can also miss its end.
            if (type == "mdat") hasMediaData = true
            if (type == "moov") hasIndex = true
            position += size
        }
        return null
    }

    /** A box type has 4 printable characters, for example "mdat". Some QuickTime types start with "©" (0xA9). */
    private fun isBoxTypeByte(byte: Byte): Boolean {
        val value = byte.toInt() and 0xFF
        return value in 0x20..0x7E || value == 0xA9
    }

    /** True when the file has only zeros from [start] and at its end. */
    private fun isZeros(input: RandomAccessFile, start: Long, length: Long): Boolean {
        fun zerosAt(offset: Long): Boolean {
            val count = minOf(ZERO_PROBE_BYTES.toLong(), length - offset).toInt()
            val bytes = ByteArray(count)
            input.seek(offset)
            input.readFully(bytes)
            return bytes.all { it == 0.toByte() }
        }
        return zerosAt(start) && zerosAt(maxOf(start, length - ZERO_PROBE_BYTES))
    }
}
