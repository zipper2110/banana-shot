package org.litvin.crash

import kotlin.test.Test
import kotlin.test.assertEquals

class FreezeWatchdogTest {
    private var now = 0L
    private val uiQueue = mutableListOf<Runnable>()
    private val freezes = mutableListOf<Boolean>()

    private val watchdog = FreezeWatchdog(
        postToUi = { uiQueue += it },
        onFreeze = { freezes += it },
        freezeMillis = 10_000,
        checkMillis = 1_000,
        clock = { now },
        threadDump = { "dump" },
    )

    private fun runUi() {
        val tasks = uiQueue.toList()
        uiQueue.clear()
        tasks.forEach { it.run() }
    }

    private fun tick(millis: Long = 1_000) {
        now += millis
        watchdog.check()
    }

    @Test
    fun `a UI thread that answers is never a freeze`() {
        repeat(30) {
            tick()
            runUi()
        }
        assertEquals(emptyList(), freezes)
    }

    @Test
    fun `a UI thread that does not answer for 10 seconds is a freeze, one time`() {
        repeat(9) { tick() }
        assertEquals(emptyList(), freezes)
        tick()
        assertEquals(listOf(true), freezes)
        repeat(5) { tick() }
        assertEquals(listOf(true), freezes)
        assertEquals(1, uiQueue.size, "the watchdog posts one task while it waits")
    }

    @Test
    fun `the freeze ends when the UI thread answers again`() {
        repeat(12) { tick() }
        runUi()
        assertEquals(listOf(true, false), freezes)
    }

    @Test
    fun `a sleep of the computer is not a freeze`() {
        tick()
        runUi()
        tick()
        // The computer sleeps: the watchdog and the UI thread both stop for one hour.
        tick(3_600_000)
        assertEquals(emptyList(), freezes)
        runUi()
        tick()
        assertEquals(emptyList(), freezes)
    }
}
