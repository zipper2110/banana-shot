package org.litvin.media.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertEquals

class CNumericLocaleTest {
    private interface CLibrary : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun aNumericLocaleOfTheUserBecomesC() {
        val libc = Native.load(Platform.C_LIBRARY_NAME, CLibrary::class.java)
        val lcNumeric = if (Platform.isMac()) 4 else 1
        // A Mac app that the Dock starts gets the locale of the user. "en_US.UTF-8" is on each Mac and Linux.
        libc.setlocale(lcNumeric, "en_US.UTF-8")

        CNumericLocale.ensure()

        assertEquals("C", CNumericLocale.current())
    }
}
