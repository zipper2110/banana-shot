package org.litvin.export

import com.sun.jna.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportSleepBlockTest {
    private class FakeBlocker(private val accepts: Boolean = true) : SleepBlocker {
        val calls = mutableListOf<String>()

        override fun block(): Boolean {
            calls += "block"
            return accepts
        }

        override fun unblock() {
            calls += "unblock"
        }
    }

    @Test
    fun `blocks the sleep from the first export start to the last export end`() {
        val blocker = FakeBlocker()
        val block = ExportSleepBlock(blocker, enabled = true)

        block.exportStarted("a")
        block.exportStarted("b")
        block.exportEnded("a")
        assertTrue(block.isBlocking)
        block.exportEnded("b")

        assertFalse(block.isBlocking)
        assertEquals(listOf("block", "unblock"), blocker.calls)
    }

    @Test
    fun `does not block the sleep when the setting is off`() {
        val blocker = FakeBlocker()
        val block = ExportSleepBlock(blocker, enabled = false)

        block.exportStarted("a")
        block.exportEnded("a")

        assertEquals(emptyList(), blocker.calls)
    }

    @Test
    fun `a change of the setting applies to the export that runs now`() {
        val blocker = FakeBlocker()
        val block = ExportSleepBlock(blocker, enabled = false)

        block.exportStarted("a")
        block.setEnabled(true)
        assertTrue(block.isBlocking)
        block.setEnabled(false)

        assertFalse(block.isBlocking)
        assertEquals(listOf("block", "unblock"), blocker.calls)
    }

    @Test
    fun `close lets the computer sleep again`() {
        val blocker = FakeBlocker()
        val block = ExportSleepBlock(blocker, enabled = true)

        block.exportStarted("a")
        block.close()

        assertFalse(block.isBlocking)
        assertEquals(listOf("block", "unblock"), blocker.calls)
    }

    @Test
    fun `a refused request does not block and does not unblock`() {
        val blocker = FakeBlocker(accepts = false)
        val block = ExportSleepBlock(blocker, enabled = true)

        block.exportStarted("a")
        assertFalse(block.isBlocking)
        block.exportEnded("a")

        assertEquals(listOf("block"), blocker.calls)
    }

    @Test
    fun `the blocker of this system blocks and unblocks the sleep`() {
        if (!Platform.isMac() && !Platform.isWindows()) return
        val blocker = SleepBlocker.forPlatform()

        assertTrue(blocker.block())
        blocker.unblock()
    }
}
