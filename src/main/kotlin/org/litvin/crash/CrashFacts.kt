package org.litvin.crash

import org.litvin.shared.util.AppActivity
import java.io.File

/** The type of the crash of the previous session (B-67). The key is a code, not a text for the user. */
enum class CrashKind(val key: String) {
    /** The JVM crash file shows a native crash. */
    NATIVE("native"),

    /** The JVM crash file shows that the JVM had no memory. */
    MEMORY("memory"),

    /** No crash file, but the UI thread was frozen at the end of the session. */
    FREEZE("freeze"),

    /** No evidence: a kill, a force quit, or a power loss. */
    UNKNOWN("unknown"),
}

/** The library of the crashed frame, as a value of a closed list (B-67). */
enum class CrashLibrary(val key: String) {
    COREVIDEO("corevideo"),
    GRAPHICS("graphics"),
    MPV("mpv"),
    FFMPEG("ffmpeg"),
    JVM("jvm"),
    SYSTEM("system"),
    OTHER("other"),
}

/**
 * The previous session did not close normally (B-67).
 *
 * @param library the library of the crashed frame, or null when there is no crash file.
 * @param activities what the app did at the crash. Empty means idle.
 * @param problem the error line of the crash file, for example `SIGSEGV (0xb) at pc=0x0000…`.
 * @param frame the problematic frame of the crash file, for example `C  [CoreVideo+0x386c]  CVXTime::…`.
 * @param thread the name of the crashed thread.
 * @param savedFile the safe part of the crash file in `logs/crashes`, or null.
 */
data class PreviousCrash(
    val kind: CrashKind,
    val library: CrashLibrary?,
    val activities: Set<AppActivity>,
    val appVersion: String?,
    val startedAtMillis: Long?,
    val problem: String? = null,
    val frame: String? = null,
    val thread: String? = null,
    val savedFile: File? = null,
) {
    /** True when the app has a crash file or a freeze. Only then the app shows the crash popup. */
    val hasEvidence: Boolean get() = kind != CrashKind.UNKNOWN

    /**
     * The technical summary for the error text of the feedback report and for the log. It is English, as the log, and
     * the user sees it in the feedback form before a send.
     */
    fun summary(): String = buildList {
        add("crash: ${kind.key}")
        library?.let { add("library: ${it.key}") }
        problem?.let { add("problem: $it") }
        frame?.let { add("frame: $it") }
        thread?.let { add("thread: $it") }
        add("activity: ${activities.joinToString(", ") { it.key }.ifEmpty { "idle" }}")
        appVersion?.let { add("version: $it") }
    }.joinToString("\n")
}
