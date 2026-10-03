package org.litvin.license.time

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemTimeTest {
    @TempDir
    lateinit var tempDir: Path

    private val javaTime = Instant.parse("2026-11-01T10:00:00Z")

    @Test
    fun `a file time later than the Java clock becomes the system time`() {
        val later = javaTime + Duration.ofDays(3)

        assertEquals(later, SystemTime(probe = { later }, javaClock = { javaTime }).withProbe())
    }

    @Test
    fun `a Java clock later than the file time stays the system time`() {
        val earlier = javaTime - Duration.ofDays(3)

        assertEquals(javaTime, SystemTime(probe = { earlier }, javaClock = { javaTime }).withProbe())
    }

    @Test
    fun `a probe with no file time gives the Java clock`() {
        assertEquals(javaTime, SystemTime(probe = { null }, javaClock = { javaTime }).withProbe())
    }

    @Test
    fun `the time without the probe does not call the probe`() {
        var calls = 0
        val systemTime = SystemTime(probe = { calls++; javaTime + Duration.ofDays(3) }, javaClock = { javaTime })

        assertEquals(javaTime, systemTime.withoutProbe())
        assertEquals(0, calls)
    }

    @Test
    fun `the probe writes a file in the data folder and reads its time`() {
        val dataFolder = tempDir.resolve("data").toFile()
        val before = Instant.now() - Duration.ofSeconds(5)

        val fileTime = assertNotNull(DataFolderFileTimeProbe.inDataFolder(dataFolder).fileTime())

        assertTrue(dataFolder.resolve(DataFolderFileTimeProbe.FILE_NAME).isFile)
        assertTrue(fileTime > before && fileTime < Instant.now() + Duration.ofSeconds(5), "fileTime=$fileTime")
    }

    @Test
    fun `a probe that cannot write the file gives no file time and no error`() {
        val notAFolder = tempDir.resolve("file").toFile().apply { writeText("x") }
        val probe = DataFolderFileTimeProbe.inDataFolder(notAFolder)

        assertNull(probe.fileTime())
        assertEquals(javaTime, SystemTime(probe = probe, javaClock = { javaTime }).withProbe())
    }
}
