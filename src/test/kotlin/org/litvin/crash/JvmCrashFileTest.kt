package org.litvin.crash

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmCrashFileTest {
    @Test
    fun `reads the problem, the frame, the thread, and the library of a native crash`() {
        val facts = JvmCrashFile.parse(NATIVE_CRASH)

        assertEquals(CrashKind.NATIVE, facts.kind)
        assertEquals(CrashLibrary.COREVIDEO, facts.library)
        assertEquals("SIGSEGV (0xb) at pc=0x00000001a9fc486c, pid=4242, tid=122947", facts.problem)
        assertEquals("C  [CoreVideo+0x386c]  CVXTime::translateTime(CVTimeStamp const*, CVTimeStamp*) const+0x2c", facts.frame)
        assertEquals("native thread", facts.thread)
    }

    @Test
    fun `the safe part stops before the registers and has no command line and no environment`() {
        val safe = JvmCrashFile.parse(NATIVE_CRASH).safeText

        assertTrue(safe.startsWith("#\n# A fatal error has been detected"))
        assertTrue(safe.contains("CVXTime::translateTime"))
        assertTrue(safe.contains("siginfo: si_signo: 11 (SIGSEGV)"))
        assertTrue(safe.contains("Host: \"MacBookAir10,1\""))
        assertFalse(safe.contains("Command Line"))
        assertFalse(safe.contains("Registers:"))
        assertFalse(safe.contains("x0=0x"))
        assertFalse(safe.contains("P R O C E S S"))
        assertFalse(safe.contains("SECRET_TOKEN"))
    }

    @Test
    fun `the safe part has a length limit`() {
        val long = NATIVE_CRASH.replace("siginfo:", "x".repeat(200) + "\n".repeat(1) + "y".repeat(JvmCrashFile.SAFE_LIMIT) + "\nsiginfo:")
        assertTrue(JvmCrashFile.parse(long).safeText.length <= JvmCrashFile.SAFE_LIMIT)
    }

    @Test
    fun `finds a crash of the JVM without memory`() {
        val text = """
            #
            # There is insufficient memory for the Java Runtime Environment to continue.
            # Native memory allocation (mmap) failed to map 1048576 bytes.
            ---------------  S U M M A R Y ------------
        """.trimIndent()
        val facts = JvmCrashFile.parse(text)
        assertEquals(CrashKind.MEMORY, facts.kind)
        assertEquals(CrashLibrary.OTHER, facts.library)
        assertNull(facts.frame)
    }

    @Test
    fun `reads the name of a Java thread`() {
        val text = NATIVE_CRASH.replace(
            "Current thread is native thread",
            "Current thread (0x000000012a80d800):  JavaThread \"AWT-EventQueue-0\" [_thread_in_native, id=4355]",
        )
        assertEquals("AWT-EventQueue-0", JvmCrashFile.parse(text).thread)
    }

    @Test
    fun `sorts the frame libraries into the closed list`() {
        fun library(frame: String) = JvmCrashFile.libraryOf(frame)
        assertEquals(CrashLibrary.COREVIDEO, library("C  [CoreVideo+0x386c]  CVXTime"))
        assertEquals(CrashLibrary.MPV, library("C  [libmpv.2.dylib+0x1234]"))
        assertEquals(CrashLibrary.MPV, library("C  [libmpv-2.dll+0x1234]"))
        assertEquals(CrashLibrary.FFMPEG, library("C  [libavcodec.61.dylib+0x10]"))
        assertEquals(CrashLibrary.FFMPEG, library("C  [avcodec-61.dll+0x10]"))
        assertEquals(CrashLibrary.GRAPHICS, library("C  [AGXMetalG13X+0x10]"))
        assertEquals(CrashLibrary.GRAPHICS, library("C  [nvwgf2umx.dll+0x10]"))
        assertEquals(CrashLibrary.GRAPHICS, library("C  [libawt_lwawt.dylib+0x10]"))
        assertEquals(CrashLibrary.JVM, library("V  [libjvm.dylib+0x10]  G1CollectedHeap"))
        assertEquals(CrashLibrary.JVM, library("J 1234 c2 java.lang.String.hashCode()I"))
        assertEquals(CrashLibrary.SYSTEM, library("C  [libsystem_kernel.dylib+0x10]"))
        assertEquals(CrashLibrary.SYSTEM, library("C  [ntdll.dll+0x10]"))
        assertEquals(CrashLibrary.OTHER, library("C  [libsomething.dylib+0x10]"))
        assertEquals(CrashLibrary.OTHER, library("C  0x0000000000000000"))
        assertEquals(CrashLibrary.OTHER, JvmCrashFile.libraryOf(null))
    }

    @Test
    fun `finds the file of the PID in the first folder that has it`() {
        val first = Files.createTempDirectory("crash-a").toFile()
        val second = Files.createTempDirectory("crash-b").toFile()
        try {
            val file = second.resolve(JvmCrashFile.fileName(4242)).apply { writeText(NATIVE_CRASH) }
            assertEquals(file, JvmCrashFile.find(4242, listOf(first, second)))
            assertNull(JvmCrashFile.find(4243, listOf(first, second)))
            assertEquals(CrashLibrary.COREVIDEO, JvmCrashFile.read(file)?.library)
        } finally {
            first.deleteRecursively()
            second.deleteRecursively()
        }
    }

    companion object {
        /** A synthetic crash file with the same form as a file of JDK 27 on macOS. */
        val NATIVE_CRASH = """
            #
            # A fatal error has been detected by the Java Runtime Environment:
            #
            #  SIGSEGV (0xb) at pc=0x00000001a9fc486c, pid=4242, tid=122947
            #
            # JRE version: OpenJDK Runtime Environment Temurin-27+35 (27.0+35) (build 27+35)
            # Problematic frame:
            # C  [CoreVideo+0x386c]  CVXTime::translateTime(CVTimeStamp const*, CVTimeStamp*) const+0x2c
            #

            ---------------  S U M M A R Y ------------

            Command Line: -Dbananashot.appDir=/Users/someone/Apps org.litvin.SwingMainApp
            Host: "MacBookAir10,1" arm64, 8 cores, 8G, Darwin 27.2.0
            Time: Sun Nov  1 02:00:22 2026 PST elapsed time: 557.458815 seconds (0d 0h 9m 17s)

            ---------------  T H R E A D  ---------------

            Current thread is native thread

            Stack: [0x0000000173dfc000,0x0000000173e7f000],  sp=0x0000000173e7ec00,  free space=523k
            Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)
            C  [CoreVideo+0x386c]  CVXTime::translateTime(CVTimeStamp const*, CVTimeStamp*) const+0x2c
            C  [CoreVideo+0x2a10]  CVDisplayLink::runIOThread()+0x1f4

            siginfo: si_signo: 11 (SIGSEGV), si_code: 2 (SEGV_ACCERR), si_addr: 0xc339af68a91550f6

            Registers:
             x0=0x0000000000000000  x1=0x0000000173e7ec00

            Top of Stack: (sp=0x0000000173e7ec00)
            0x0000000173e7ec00:   0000000000000000 0000000000000000

            ---------------  P R O C E S S  ---------------
            jvm_args: -Dbananashot.appDir=/Users/someone/Apps

            ---------------  S Y S T E M  ---------------
            Environment Variables:
            SECRET_TOKEN=abc
        """.trimIndent()
    }
}
