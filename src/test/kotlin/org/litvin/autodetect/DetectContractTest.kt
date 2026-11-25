package org.litvin.autodetect

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The contract between the lab script and the app (chain-inventory G14). */
class DetectContractTest {
    private fun video(name: String, byte: Int): File =
        File(Files.createTempDirectory("video").toFile(), name).apply { writeBytes(ByteArray(4000) { byte.toByte() }) }

    @Test
    fun `a result of a newer lab script does not import`() {
        val problem = DetectImport.check(DetectResult(version = DetectResult.CONTRACT_VERSION + 1), null)
        assertNotNull(problem)
        assertNull(DetectImport.check(DetectResult(version = DetectResult.CONTRACT_VERSION), null))
        assertNull(DetectImport.check(DetectResult(version = 1), null))
    }

    @Test
    fun `a result of another video does not import`() {
        val a = video("match.mp4", 1)
        val b = video("match.mp4", 2)
        val result = DetectResult(version = 2, video = a.absolutePath, videoId = VideoIdentity.of(a))
        assertNull(DetectImport.check(result, a))
        assertNotNull(DetectImport.check(result, b))                // the same name, other bytes
    }

    @Test
    fun `an old result with no identity compares the file name`() {
        val a = video("match.mp4", 1)
        assertNull(DetectImport.check(DetectResult(video = "D:/old/place/match.mp4"), a))
        assertNotNull(DetectImport.check(DetectResult(video = "D:/other.mp4"), a))
    }

    @Test
    fun `the lines of the lab script for the app`() {
        assertNull(ScriptLine.parse("court model: refined (far points move 0.12 m, 3 of 4 windows)"))
        assertEquals(ScriptLine("@stage", listOf("ball", "start"), "ball start"), ScriptLine.parse("@stage ball start"))
        assertEquals("D:\\work\\a b\\result.json", ScriptLine.parse("@result D:\\work\\a b\\result.json")?.text)
        assertEquals(listOf("ball", "0.250"), ScriptLine.parse("@progress ball 0.250")?.args)
    }
}
