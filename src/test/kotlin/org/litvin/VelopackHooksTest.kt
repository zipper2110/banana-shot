package org.litvin

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VelopackHooksTest {
    @Test
    fun `each Velopack hook argument is a hook process`() {
        listOf("--veloapp-install", "--veloapp-obsolete", "--veloapp-updated", "--veloapp-uninstall").forEach { hook ->
            assertTrue(VelopackHooks.isHookProcess(arrayOf(hook, "1.4.0")), hook)
        }
    }

    @Test
    fun `a new hook of a later Velopack version is a hook process`() {
        assertTrue(VelopackHooks.isHookProcess(arrayOf("--veloapp-something-new")))
    }

    @Test
    fun `a normal start is not a hook process`() {
        assertFalse(VelopackHooks.isHookProcess(emptyArray()))
        assertFalse(VelopackHooks.isHookProcess(arrayOf("--diagnostics")))
        assertFalse(VelopackHooks.isHookProcess(arrayOf("D:/videos/--veloapp-install.mp4")))
    }
}
