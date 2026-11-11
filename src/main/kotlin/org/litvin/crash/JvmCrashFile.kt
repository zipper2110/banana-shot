package org.litvin.crash

import java.io.File
import java.io.RandomAccessFile

/**
 * The crash file of the JVM, `hs_err_pid<pid>.log` (B-67).
 *
 * The JVM writes the file in the start folder of the process. When it cannot write there, it writes the file in the
 * temp folder. On Windows and macOS, this is the same folder as `java.io.tmpdir`.
 *
 * The file can contain private data: the command line, the environment variables, memory, and the registers. The app
 * keeps only the safe part (see [safePart]).
 */
object JvmCrashFile {
    /** The part of the file that the app reads. The crashed thread is at the start of the file. */
    private const val READ_LIMIT = 512 * 1024

    /** The maximum length of the safe part. */
    const val SAFE_LIMIT = 64 * 1024

    /** The facts of a crash file. */
    data class Facts(
        val kind: CrashKind,
        val library: CrashLibrary,
        val problem: String?,
        val frame: String?,
        val thread: String?,
        val safeText: String,
    )

    fun fileName(pid: Long): String = "hs_err_pid$pid.log"

    /** The first file with the name of [pid] in [folders], or null. */
    fun find(pid: Long, folders: List<File>): File? =
        folders.asSequence().distinct().map { File(it, fileName(pid)) }.firstOrNull { it.isFile }

    /** Reads the start of [file]. Null when the app cannot read it. */
    fun read(file: File): Facts? = runCatching {
        RandomAccessFile(file, "r").use { input ->
            val bytes = ByteArray(minOf(input.length(), READ_LIMIT.toLong()).toInt())
            input.readFully(bytes)
            parse(bytes.toString(Charsets.UTF_8))
        }
    }.getOrNull()

    fun parse(text: String): Facts {
        val lines = text.lines()
        val memory = MEMORY_MARKERS.any { text.contains(it) }
        val problem = lines.firstOrNull { PROBLEM.matches(it) }?.removePrefix("#")?.trim()
        val frameIndex = lines.indexOfFirst { it.trim() == "# Problematic frame:" }
        val frame = lines.getOrNull(frameIndex + 1)?.takeIf { frameIndex >= 0 && it.startsWith("#") }?.removePrefix("#")?.trim()
        return Facts(
            kind = if (memory) CrashKind.MEMORY else CrashKind.NATIVE,
            library = libraryOf(frame),
            problem = problem,
            frame = frame,
            thread = threadOf(lines),
            safeText = safePart(lines),
        )
    }

    /**
     * The header, the summary, and the crashed thread with its stack. The part stops before the registers and the
     * memory. It has no command line. The process data and the system data (with the environment variables) come
     * after the thread, so they are not in the part.
     */
    fun safePart(lines: List<String>): String {
        val kept = lines.asSequence()
            .takeWhile { line -> STOP_MARKERS.none { line.startsWith(it) } }
            .filterNot { line -> DROPPED_LINES.any { line.startsWith(it) } }
            .joinToString("\n")
            .trimEnd()
        return if (kept.length <= SAFE_LIMIT) kept else kept.take(SAFE_LIMIT).substringBeforeLast('\n')
    }

    /** The name of the crashed thread, or "native thread". */
    private fun threadOf(lines: List<String>): String? {
        val line = lines.firstOrNull { it.startsWith("Current thread") } ?: return null
        if (line.contains("native thread")) return "native thread"
        return THREAD_NAME.find(line)?.groupValues?.get(1)
    }

    /**
     * The library of a problematic frame. A frame looks like `C  [CoreVideo+0x386c]  CVXTime::…`. The letter is the
     * type: `C` is native code, `V` is the JVM, `J`, `j`, `A`, and `v` are Java code or the JVM.
     */
    fun libraryOf(frame: String?): CrashLibrary {
        if (frame.isNullOrBlank()) return CrashLibrary.OTHER
        val type = frame.trim().first()
        if (type in JVM_FRAME_TYPES) return CrashLibrary.JVM
        val name = LIBRARY.find(frame)?.groupValues?.get(1)?.lowercase() ?: return CrashLibrary.OTHER
        return when {
            name.contains("corevideo") -> CrashLibrary.COREVIDEO
            name.startsWith("libmpv") || name.startsWith("mpv") -> CrashLibrary.MPV
            FFMPEG_NAMES.any { name.startsWith(it) || name.startsWith("lib$it") } -> CrashLibrary.FFMPEG
            GRAPHICS_NAMES.any { name.contains(it) } -> CrashLibrary.GRAPHICS
            JVM_NAMES.any { name.startsWith(it) } -> CrashLibrary.JVM
            SYSTEM_NAMES.any { name.startsWith(it) } -> CrashLibrary.SYSTEM
            else -> CrashLibrary.OTHER
        }
    }

    private val PROBLEM = Regex("^#\\s+(SIG[A-Z]+|EXCEPTION_[A-Z_]+|Internal Error|Out of Memory Error).*")
    private val THREAD_NAME = Regex("\"([^\"]*)\"")
    private val LIBRARY = Regex("\\[([^+\\]]+)")
    private val MEMORY_MARKERS = listOf(
        "There is insufficient memory for the Java Runtime Environment",
        "# Out of Memory Error",
        "java.lang.OutOfMemoryError",
    )
    private val STOP_MARKERS = listOf(
        "Registers:",
        "Register to memory mapping",
        "Top of Stack:",
        "Instructions:",
        "---------------  P R O C E S S",
        "---------------  S Y S T E M",
    )
    private val DROPPED_LINES = listOf("Command Line:")
    private val JVM_FRAME_TYPES = setOf('V', 'J', 'j', 'A', 'v')
    private val FFMPEG_NAMES = listOf("avcodec", "avformat", "avutil", "avfilter", "avdevice", "swscale", "swresample", "postproc")
    private val GRAPHICS_NAMES = listOf(
        "metal", "agx", "iogpu", "gpusupport", "coregraphics", "quartzcore", "skylight", "opengl", "libgl", "vulkan",
        "moltenvk", "d3d", "dxgi", "nvwgf", "nvd3d", "nvogl", "nvldumd", "atidxx", "atio", "aticfx", "amdxx", "amdvlk",
        "igd", "igc", "ig9", "ig11", "ig12", "libawt", "awt.dll", "libjawt", "fontmanager",
    )
    private val JVM_NAMES = listOf("libjvm", "jvm.dll", "libjava", "java.dll", "libjli", "jli.dll", "libnet", "libnio")
    private val SYSTEM_NAMES = listOf(
        "libsystem", "libobjc", "libdyld", "libc++", "libc.", "corefoundation", "foundation", "appkit", "hiservices",
        "ntdll", "kernel32", "kernelbase", "ucrtbase", "msvcr", "msvcp", "vcruntime", "user32", "combase", "win32u",
    )
}
