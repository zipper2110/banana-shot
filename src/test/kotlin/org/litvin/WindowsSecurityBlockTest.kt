package org.litvin

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsSecurityBlockTest {
    @Test
    fun theErrorOfAProcessStartIsRead() {
        val cause = IOException("Cannot run program \"ffmpeg.exe\": CreateProcess error=4551, blocked")

        assertEquals(4551, WindowsSecurityBlock.createProcessError(cause))
        assertEquals(4551, WindowsSecurityBlock.createProcessError(RuntimeException("start", cause)))
        assertNull(WindowsSecurityBlock.createProcessError(IOException("Stream closed")))
    }

    @Test
    fun onlySecurityErrorsAreBlocks() {
        listOf(225, 226, 1260, 4550, 4551, 4559).forEach { assertTrue(WindowsSecurityBlock.isBlockError(it), "$it") }
        listOf(2, 5, 126, 193, 4560).forEach { assertFalse(WindowsSecurityBlock.isBlockError(it), "$it") }
        assertFalse(WindowsSecurityBlock.isBlockError(null))
    }
}
