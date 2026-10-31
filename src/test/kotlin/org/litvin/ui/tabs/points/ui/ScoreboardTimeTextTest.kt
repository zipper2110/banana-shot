package org.litvin.ui.tabs.points.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-63: the text of a fixed scoreboard time. */
class ScoreboardTimeTextTest {
    @Test
    fun `a time shows as hh mm ss`() {
        assertEquals("00:00:00", ScoreboardTimeText.format(0))
        assertEquals("00:12:30", ScoreboardTimeText.format(750_999))
        assertEquals("01:15:20", ScoreboardTimeText.format(4_520_000))
    }

    @Test
    fun `the parse accepts hh mm ss, mm ss and seconds`() {
        assertEquals(750_000L, ScoreboardTimeText.parse("12:30"))
        assertEquals(4_520_000L, ScoreboardTimeText.parse("75:20"))
        assertEquals(4_520_000L, ScoreboardTimeText.parse("1:15:20"))
        assertEquals(4_520_000L, ScoreboardTimeText.parse("01:15:20"))
        assertEquals(45_000L, ScoreboardTimeText.parse(" 45 "))
        assertEquals(750_000L, ScoreboardTimeText.parse("１２:３０"))
    }

    @Test
    fun `the parse refuses a text that is not a time`() {
        listOf("", "12:60", "ab", "12:", "1:2:3:4", "-5", "1:75:00").forEach { assertNull(ScoreboardTimeText.parse(it), it) }
    }
}
