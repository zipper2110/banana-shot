package org.litvin.media.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * A Mac app that the Dock starts gets the locale of the user. mpv then refused to start, and the preview showed
 * "The video player cannot start" (B-13). The test starts a real mpv player with no window after it sets such a
 * locale.
 */
class MpvLocaleStartTest {
    private interface CLibrary : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun mpvStartsWhenTheNumericLocaleOfTheUserIsNotC() {
        assumeTrue(LibMpv.instanceOrNull() != null, "libmpv is not available")
        val video = File(javaClass.getResource("/media/ui-smoke.mp4")!!.toURI())
        val libc = Native.load(Platform.C_LIBRARY_NAME, CLibrary::class.java)
        libc.setlocale(LC_NUMERIC_MAC, "ru_RU.UTF-8") ?: libc.setlocale(LC_NUMERIC_MAC, "en_US.UTF-8")

        val frame = MpvFrameReader().use { it.read(video.absolutePath, 500, "null") }

        assertNotNull(frame, "mpv did not read a frame")
    }

    private companion object {
        const val LC_NUMERIC_MAC = 4
    }
}
