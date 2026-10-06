package org.litvin

import com.fasterxml.jackson.databind.ObjectMapper
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonFileIOTest {
    @Test
    fun `a reader never observes a partially written document`() {
        val projectDir = Files.createTempDirectory("bananashot-atomic").toFile()
        val edl = EdlV1(points = (1..400).map { PointV1(id = "point-$it", startMs = it * 1000, endMs = it * 1000 + 500) })
        EdlIO.writeForProjectDir(projectDir.absolutePath, edl)

        val failure = AtomicReference<Throwable?>()
        val started = CountDownLatch(1)
        val writer = Executors.newSingleThreadExecutor()
        val stop = AtomicReference(false)
        val writing = writer.submit {
            started.countDown()
            while (stop.get() == false) {
                EdlIO.writeForProjectDir(projectDir.absolutePath, edl)
            }
        }

        started.await(5, TimeUnit.SECONDS)
        try {
            repeat(300) {
                try {
                    val read = EdlIO.readForProjectDir(projectDir.absolutePath)
                    assertEquals(400, read.points.size, "reader saw a truncated document")
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                }
            }
        } finally {
            stop.set(true)
            writing.get(5, TimeUnit.SECONDS)
            writer.shutdownNow()
        }

        assertNull(failure.get(), "reading while a save was in flight failed: ${failure.get()}")
    }

    @Test
    fun `the destination is left untouched when serialization fails`() {
        val directory = Files.createTempDirectory("bananashot-atomic-fail").toFile()
        val target = directory.resolve("edl.json")
        val original = EdlV1(points = listOf(PointV1(id = "keep-me", startMs = 0, endMs = 1_000)))
        EdlIO.write(target.absolutePath, original)

        runCatching { JsonFileIO.writeAtomically(ObjectMapper(), target.absolutePath, Unserializable()) }

        assertEquals(1, EdlIO.read(target.absolutePath).points.size)
        assertTrue(
            directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") },
            "temporary files must not be left behind",
        )
    }

    @Test
    fun `a save waits for a short open of the destination outside the lock`() {
        val directory = Files.createTempDirectory("bananashot-atomic-busy").toFile()
        val target = directory.resolve("edl.json")
        EdlIO.write(target.absolutePath, EdlV1(points = listOf(PointV1(id = "old", startMs = 0, endMs = 1_000))))
        val updated = EdlV1(points = listOf(PointV1(id = "new", startMs = 0, endMs = 1_000)))

        // On Windows, FileInputStream does not let other threads replace the file while it is open.
        val saving = java.io.FileInputStream(target).use {
            val save = Executors.newSingleThreadExecutor()
            try {
                val result = save.submit { EdlIO.write(target.absolutePath, updated) }
                Thread.sleep(100)
                result
            } finally {
                save.shutdown()
            }
        }

        saving.get(5, TimeUnit.SECONDS)
        assertEquals("new", EdlIO.read(target.absolutePath).points.single().id)
    }

    @Test
    fun `two spellings of the same path have the same lock key`() {
        val directory = Files.createTempDirectory("bananashot-lock-key").toFile()
        val direct = directory.resolve("edl.json")
        val indirect = java.io.File(directory, "sub/../EDL.JSON")

        assertEquals(fileLockKey(direct), fileLockKey(indirect))
    }

    private class Unserializable {
        @Suppress("unused")
        val boom: Any get() = throw IllegalStateException("cannot serialize")
    }
}
