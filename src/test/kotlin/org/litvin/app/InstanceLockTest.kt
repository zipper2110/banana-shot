package org.litvin.app

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InstanceLockTest {
    @TempDir
    lateinit var tempDir: Path

    private val processes = mutableListOf<Process>()

    @AfterTest
    fun stopProcesses() {
        processes.forEach { it.destroyForcibly().waitFor(10, TimeUnit.SECONDS) }
    }

    @Test
    fun `the first start gets the lock and creates the data folder`() {
        val file = tempDir.resolve("new-data-folder/instance.lock").toFile()

        val result = InstanceLock.acquire(file)

        assertIs<InstanceLock.Result.Acquired>(result).lock.close()
        assertTrue(file.isFile)
    }

    @Test
    fun `a second start in the same process does not get the lock`() {
        val file = tempDir.resolve("instance.lock").toFile()
        val first = assertIs<InstanceLock.Result.Acquired>(InstanceLock.acquire(file))

        try {
            assertEquals(InstanceLock.Result.HeldByOtherInstance, InstanceLock.acquire(file))
        } finally {
            first.lock.close()
        }
    }

    @Test
    fun `a closed lock lets the next start get the lock`() {
        val file = tempDir.resolve("instance.lock").toFile()
        assertIs<InstanceLock.Result.Acquired>(InstanceLock.acquire(file)).lock.close()

        assertIs<InstanceLock.Result.Acquired>(InstanceLock.acquire(file)).lock.close()
    }

    @Test
    fun `a start does not get the lock while another process has it`() {
        val file = tempDir.resolve("instance.lock").toFile()
        startProbeThatHoldsTheLock(file)

        assertEquals(InstanceLock.Result.HeldByOtherInstance, InstanceLock.acquire(file))
    }

    @Test
    fun `after End task of the other process, the next start gets the lock`() {
        val file = tempDir.resolve("instance.lock").toFile()
        val probe = startProbeThatHoldsTheLock(file)

        probe.destroyForcibly()
        assertTrue(probe.waitFor(30, TimeUnit.SECONDS), "The probe process did not stop.")

        // Windows releases the locks of a stopped process asynchronously. On the test machine this took
        // 10 to 25 ms. A user needs seconds to start the app again, so the app itself does not wait.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        var result = InstanceLock.acquire(file)
        while (result !is InstanceLock.Result.Acquired && System.nanoTime() < deadline) {
            Thread.sleep(10)
            result = InstanceLock.acquire(file)
        }
        assertIs<InstanceLock.Result.Acquired>(result).lock.close()
    }

    @Test
    fun `a lock file that cannot be created gives a failure, so the app can start anyway`() {
        val notAFolder = tempDir.resolve("file").toFile().apply { writeText("x") }

        val result = InstanceLock.acquire(File(notAFolder, "instance.lock"))

        assertIs<InstanceLock.Result.Failed>(result)
    }

    private fun startProbeThatHoldsTheLock(file: File): Process {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            InstanceLockProbe::class.java.name,
            file.absolutePath,
        ).redirectErrorStream(true).start()
        processes += process

        val firstLine = process.inputStream.bufferedReader().readLine()
        assertEquals(InstanceLockProbe.ACQUIRED, firstLine, "The probe process did not get the lock.")
        return process
    }
}

/** A separate process that takes the lock and keeps it until its input closes or it is stopped. */
object InstanceLockProbe {
    const val ACQUIRED = "ACQUIRED"

    @JvmStatic
    fun main(args: Array<String>) {
        when (val result = InstanceLock.acquire(File(args.single()))) {
            is InstanceLock.Result.Acquired -> {
                println(ACQUIRED)
                System.out.flush()
                System.`in`.read()
                result.lock.close()
            }
            else -> println(result)
        }
    }
}
