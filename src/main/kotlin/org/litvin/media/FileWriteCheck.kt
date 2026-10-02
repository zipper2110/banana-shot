package org.litvin.media

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

/** Finds out if a different program writes a file now, for example a copy in File Explorer. */
object FileWriteCheck {
    private val logger = KotlinLogging.logger {}
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /**
     * True when a different program has the file open for writing. Windows only: on other systems, it returns false.
     *
     * The check opens the file for reading and does not let other programs write while it is open.
     * Windows refuses this open (a sharing violation) when a program already has the file open for writing.
     * Programs that only read the file, for example the video players of this application, do not cause a refusal.
     */
    fun isOpenForWriting(file: File): Boolean {
        if (!isWindows) return false
        return try {
            val handle = Kernel32.INSTANCE.CreateFile(
                file.absolutePath,
                WinNT.GENERIC_READ,
                WinNT.FILE_SHARE_READ,
                null,
                WinNT.OPEN_EXISTING,
                WinNT.FILE_ATTRIBUTE_NORMAL,
                null,
            )
            if (handle == null || WinBase.INVALID_HANDLE_VALUE == handle) {
                Native.getLastError() == WinError.ERROR_SHARING_VIOLATION
            } else {
                Kernel32.INSTANCE.CloseHandle(handle)
                false
            }
        } catch (t: Throwable) {
            logger.warn(t) { "Cannot check if a program writes $file" }
            false
        }
    }
}
