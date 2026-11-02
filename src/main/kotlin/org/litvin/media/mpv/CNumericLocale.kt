package org.litvin.media.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * mpv does not start when the C locale for numbers (`LC_NUMERIC`) is not "C": `mpv_create` returns null and writes
 * "Non-C locale detected" to stderr. A Mac app that the Dock or Finder starts gets the locale of the user, for
 * example `ru_RU.UTF-8` (B-13). A start from a terminal usually has "C", so a source run does not show the problem.
 *
 * The mpv manual tells the host program to set `LC_NUMERIC` to "C". Java does not use the C locale for its own
 * number formats, so the texts of the app do not change. mpv has the check on all systems, but on Windows the C
 * locale of the process stays "C", so the app does not change it there.
 */
internal object CNumericLocale {
    private val logger = KotlinLogging.logger {}

    /** The value of `LC_NUMERIC` in `locale.h`: 4 on macOS and the BSDs, 1 on Linux (glibc and musl). */
    private val LC_NUMERIC: Int = if (Platform.isMac() || Platform.isFreeBSD() || Platform.isOpenBSD()) 4 else 1

    private interface CLibrary : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    private val libc: CLibrary? by lazy {
        runCatching { Native.load(Platform.C_LIBRARY_NAME, CLibrary::class.java) }
            .onFailure { logger.warn(it) { "Cannot load the C library to set LC_NUMERIC." } }
            .getOrNull()
    }

    /** The current value of `LC_NUMERIC`, or null on Windows or when the C library does not load. */
    fun current(): String? = if (Platform.isWindows()) null else libc?.setlocale(LC_NUMERIC, null)

    /** Sets `LC_NUMERIC` to "C" before mpv starts. It does nothing on Windows. */
    fun ensure() {
        if (Platform.isWindows()) return
        val c = libc ?: return
        val before = c.setlocale(LC_NUMERIC, null)
        if (before == "C") return
        val after = c.setlocale(LC_NUMERIC, "C")
        logger.info { "Set LC_NUMERIC from $before to $after for mpv." }
    }
}
