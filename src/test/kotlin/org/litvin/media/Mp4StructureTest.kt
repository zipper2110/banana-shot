package org.litvin.media

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Mp4StructureTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun completeFileHasNoDefect() {
        assertNull(inspect(box("ftyp", 20) + box("mdat", 1000) + box("moov", 300)))
        // The index at the start ("fast start").
        assertNull(inspect(box("ftyp", 20) + box("moov", 300) + box("mdat", 1000)))
        // A 64-bit size, as a phone writes for a large file.
        assertNull(inspect(box("ftyp", 20) + largeBox("mdat", 1000) + box("moov", 300)))
    }

    @Test
    fun boxLargerThanTheRestOfTheFileMeansTruncated() {
        val complete = box("ftyp", 20) + largeBox("mdat", 1000) + box("moov", 300)

        assertEquals(Mp4Defect.TRUNCATED, inspect(complete.copyOf(500)))
        assertEquals(Mp4Defect.TRUNCATED, inspect(complete.copyOf(complete.size - 1)))
        // A file with the index at the start also can miss its end.
        assertEquals(Mp4Defect.TRUNCATED, inspect((box("ftyp", 20) + box("moov", 300) + box("mdat", 1000)).copyOf(800)))
    }

    @Test
    fun zerosAfterTheMediaDataMeanAnUnwrittenEnd() {
        assertEquals(Mp4Defect.UNWRITTEN_END, inspect(box("ftyp", 20) + largeBox("mdat", 1000) + ByteArray(300)))
        assertEquals(Mp4Defect.UNWRITTEN_END, inspect(box("ftyp", 20) + largeBox("mdat", 1000) + ByteArray(20_000)))
    }

    @Test
    fun mediaDataWithoutIndexMeansNotFinalized() {
        assertEquals(Mp4Defect.NOT_FINALIZED, inspect(box("ftyp", 20) + box("mdat", 1000)))
        // A box size of 0 means "to the end of the file". A recorder writes it while it records.
        assertEquals(Mp4Defect.NOT_FINALIZED, inspect(box("ftyp", 20) + box("mdat", 1000, sizeField = 0)))
        assertEquals(Mp4Defect.NOT_FINALIZED, inspect(box("ftyp", 20) + largeBox("mdat", 1000, sizeField = 0)))
    }

    @Test
    fun otherFilesHaveNoKnownDefect() {
        assertNull(inspect("These are notes, not a video.".toByteArray()))
        assertNull(inspect(ByteArray(0)))
        assertNull(inspect(box("ftyp", 20)))
        // Bytes that are not a box and not zeros: damaged, but the cause is not known.
        assertNull(inspect(box("ftyp", 20) + largeBox("mdat", 1000) + ByteArray(300) { 7 }))
        assertNull(Mp4Structure.inspect(tempDir.resolve("missing.mp4").toFile()))
    }

    @Test
    fun halfOfTheFixtureVideoIsTruncated() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/media/ui-smoke.mp4")).use { it.readBytes() }

        assertNull(inspect(bytes))
        assertEquals(Mp4Defect.TRUNCATED, inspect(bytes.copyOf(bytes.size / 2)))
    }

    private fun inspect(bytes: ByteArray): Mp4Defect? {
        val file = File.createTempFile("video", ".mp4", tempDir.toFile()).apply { writeBytes(bytes) }
        return Mp4Structure.inspect(file)
    }

    /** A box with a 32-bit size. [sizeField] replaces the size in the header. */
    private fun box(type: String, size: Int, sizeField: Int = size): ByteArray = bytes {
        writeInt(sizeField)
        writeBytes(type)
        write(ByteArray(size - 8) { 1 })
    }

    /** A box with a 64-bit size (size field 1). [sizeField] replaces the 64-bit size in the header. */
    private fun largeBox(type: String, size: Int, sizeField: Long = size.toLong()): ByteArray = bytes {
        writeInt(1)
        writeBytes(type)
        writeLong(sizeField)
        write(ByteArray(size - 16) { 1 })
    }

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).use(block) }.toByteArray()
}
