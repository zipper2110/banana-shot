package org.litvin.shared.util

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppActivitiesTest {
    @AfterTest
    fun tearDown() = AppActivities.reset()

    @Test
    fun `an activity stays on while one owner has it, and the listener gets only changes`() {
        val changes = mutableListOf<Set<AppActivity>>()
        AppActivities.listen { changes += it }

        AppActivities.set(AppActivity.PLAYBACK, "a", true)
        AppActivities.set(AppActivity.PLAYBACK, "b", true)
        AppActivities.set(AppActivity.PLAYBACK, "a", false)
        AppActivities.set(AppActivity.EXPORT, "job", true)
        AppActivities.set(AppActivity.PLAYBACK, "b", false)
        AppActivities.set(AppActivity.PLAYBACK, "b", false)

        assertEquals(
            listOf(setOf(AppActivity.PLAYBACK), setOf(AppActivity.EXPORT, AppActivity.PLAYBACK), setOf(AppActivity.EXPORT)),
            changes,
        )
        assertEquals(setOf(AppActivity.EXPORT), AppActivities.current())
    }
}
