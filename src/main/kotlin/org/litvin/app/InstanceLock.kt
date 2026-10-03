package org.litvin.app

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * The lock that lets only one instance of the app run for each Windows account (B-19).
 *
 * The lock is on a file in the data folder. Windows releases it when the process ends, also after a
 * crash or End task. Thus, a lock from an old process never blocks a start.
 */
class InstanceLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {

    override fun close() {
        try {
            lock.release()
        } finally {
            channel.close()
        }
    }

    sealed interface Result {
        /** This process has the lock. Keep [lock] reachable until the process ends. */
        data class Acquired(val lock: InstanceLock) : Result

        /** Another instance has the lock. */
        data object HeldByOtherInstance : Result

        /** The app cannot create or lock the file. The app starts anyway (fail open). */
        data class Failed(val cause: Throwable) : Result
    }

    companion object {
        fun acquire(file: File): Result {
            val channel = try {
                file.absoluteFile.parentFile?.mkdirs()
                FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            } catch (failure: IOException) {
                return Result.Failed(failure)
            } catch (failure: SecurityException) {
                return Result.Failed(failure)
            }

            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                // This JVM already has the lock, so this is not the first instance.
                null
            } catch (failure: IOException) {
                channel.closeQuietly()
                return Result.Failed(failure)
            }

            if (lock == null) {
                channel.closeQuietly()
                return Result.HeldByOtherInstance
            }
            return Result.Acquired(InstanceLock(channel, lock))
        }

        private fun FileChannel.closeQuietly() {
            try {
                close()
            } catch (_: IOException) {
            }
        }
    }
}
