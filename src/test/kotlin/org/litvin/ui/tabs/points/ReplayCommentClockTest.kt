package org.litvin.ui.tabs.points

import org.litvin.points.CommentV1
import org.litvin.points.ReplayFreeze
import org.litvin.points.ReplayV1
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplayCommentClockTest {
    private var nowNanos = 0L
    private val clock = ReplayCommentClock { nowNanos }

    private fun comment(id: Int, start: Int, duration: Int) =
        CommentV1(id = id, startMs = start, durationMs = duration, text = "C$id", colorHex = "#FFFFFF")

    // The still frame is from 2 s to 3 s of the play.
    private val replay = ReplayV1(
        startMs = 10_000, endMs = 12_000, speed = 0.5, freeze = ReplayFreeze(11_000, 1_000),
        comments = listOf(comment(1, 10_500, 2_000), comment(2, 11_000, 400)),
    )

    @Test
    fun outsideTheStillFrameThePlayTimeComesFromTheSourceTime() {
        assertEquals(1_000L, clock.playOffsetMs(replay, 10_500, holding = false))
        assertEquals(listOf(1), clock.comments(replay, 10_500, holding = false).map { it.id })
    }

    @Test
    fun duringTheStillFrameThePlayTimeCountsTheTimeOfTheStillFrame() {
        nowNanos = 5_000_000_000
        clock.holdStarted()
        assertEquals(listOf(1, 2), clock.comments(replay, 11_000, holding = true).map { it.id })

        nowNanos += 500_000_000
        assertEquals(2_500L, clock.playOffsetMs(replay, 11_000, holding = true))
        // The comment of 0.4 s on the still frame ended. The first comment ends at 3 s, so it still shows.
        assertEquals(listOf(1), clock.comments(replay, 11_000, holding = true).map { it.id })

        nowNanos += 5_000_000_000
        assertEquals(3_000L, clock.playOffsetMs(replay, 11_000, holding = true))

        clock.holdEnded()
        assertEquals(2_000L, clock.playOffsetMs(replay, 11_000, holding = true))
    }
}
