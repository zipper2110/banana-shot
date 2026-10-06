package org.litvin.media.mpv

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibMpvLoadErrorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun theWindowsErrorOfAFailedLoadIsRead() {
        assumeTrue(System.getProperty("os.name").orEmpty().startsWith("Windows"), "LoadLibraryEx is a Windows function")
        val notADll = tempDir.resolve("libmpv-2.dll").toFile().apply { writeText("This is not a DLL.") }

        // 193: ERROR_BAD_EXE_FORMAT. A blocked DLL gives a different code, for example 4551.
        assertEquals(193, LibMpv.windowsLoadError(notADll))
        assertNull(LibMpv.windowsLoadError(tempDir.resolve("missing.dll").toFile()))
    }
}
