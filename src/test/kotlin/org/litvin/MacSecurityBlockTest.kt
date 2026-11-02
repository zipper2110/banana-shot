package org.litvin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacSecurityBlockTest {
    @Test
    fun theDyldTextsOfARefusedLibraryAreABlock() {
        listOf(
            "tried: '/x/libmpv.2.dylib' (missing code signature in <56ED> '/x/libmpv.2.dylib')",
            "code signature in <56ED> '/x/libmpv.2.dylib' not valid for use in process: library load disallowed",
            "library load disallowed by system policy",
            "mapping process and mapped file (non-platform) have different Team IDs",
        ).forEach { text ->
            assertTrue(MacSecurityBlock.isLoadBlock(UnsatisfiedLinkError(text)), text)
        }
    }

    @Test
    fun theCauseOfAnErrorIsAlsoChecked() {
        val cause = IllegalStateException("libmpv is not available.", UnsatisfiedLinkError("missing code signature"))

        assertTrue(MacSecurityBlock.isLoadBlock(cause))
    }

    @Test
    fun otherErrorsAreNoBlock() {
        assertFalse(MacSecurityBlock.isLoadBlock(UnsatisfiedLinkError("tried: '/x/libmpv.dylib' (no such file)")))
        assertFalse(MacSecurityBlock.isLoadBlock(null))
    }

    @Test
    fun onlySigkillWithNoOutputIsAStopAtTheStart() {
        assertTrue(MacSecurityBlock.isKilledAtStart(137, " \n"))
        assertFalse(MacSecurityBlock.isKilledAtStart(137, "Input #0, mov,mp4"))
        assertFalse(MacSecurityBlock.isKilledAtStart(1, ""))
    }

    @Test
    fun theSystemNameTellsMacos() {
        assertTrue(MacSecurityBlock.isMac("Mac OS X"))
        assertFalse(MacSecurityBlock.isMac("Windows 11"))
    }
}
