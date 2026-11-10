package org.litvin.media.mpv

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewSleepGuardTest {
    private class RecordingPreview(private val events: MutableList<String>, private val name: String) : SleepAwarePreview {
        override fun suspendForSleep() {
            events += "$name suspend"
        }

        override fun resumeAfterSleep() {
            events += "$name resume"
        }
    }

    @Test
    fun sleepClosesAndWakeStartsEachRegisteredPreview() {
        val events = mutableListOf<String>()
        val guard = PreviewSleepGuard()
        val first = RecordingPreview(events, "first")
        val second = RecordingPreview(events, "second")
        guard.register(first)
        guard.register(second)

        guard.onSleep("Mac")
        guard.onWake("Mac")

        assertEquals(setOf("first suspend", "second suspend"), events.take(2).toSet())
        assertEquals(setOf("first resume", "second resume"), events.drop(2).toSet())
    }

    @Test
    fun anUnregisteredPreviewGetsNoEvents() {
        val events = mutableListOf<String>()
        val guard = PreviewSleepGuard()
        val closed = RecordingPreview(events, "closed")
        guard.register(closed)
        guard.unregister(closed)

        guard.onSleep("display")
        guard.onWake("display")

        assertEquals(emptyList(), events)
    }

    @Test
    fun aFailureOfOnePreviewDoesNotStopTheOthers() {
        val events = mutableListOf<String>()
        val guard = PreviewSleepGuard()
        val failing = object : SleepAwarePreview {
            override fun suspendForSleep() = error("mpv failed")
            override fun resumeAfterSleep() = error("mpv failed")
        }
        val working = RecordingPreview(events, "working")
        guard.register(failing)
        guard.register(working)

        guard.onSleep("Mac")
        guard.onWake("Mac")

        assertEquals(listOf("working suspend", "working resume"), events)
    }
}
