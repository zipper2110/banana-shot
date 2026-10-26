package org.litvin.ui.tabs.points

import org.litvin.media.ReplaySound
import org.litvin.media.ReplayView
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.points.ReplayZoom
import org.litvin.ui.tabs.points.ReplayDirector.Command
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers the live play order of the replays (B-50 T3). */
class ReplayDirectorTest {
    /** A point from 0 to 10 s with a replay of its last 2 s at half speed: one play is 4 s. */
    private fun point(repeats: Int = 1, effect: ReplayEffect = ReplayEffect.FADE, zoom: ReplayZoom? = null) = PointV1(
        id = "p1",
        startMs = 0,
        endMs = 10_000,
        replays = listOf(ReplayV1(8_000, 10_000, zoom = zoom, speed = 0.5, repeats = repeats, effect = effect)),
    )

    private fun director(point: PointV1) = ReplayDirector().also { it.setPoints(listOf(point)) }

    private fun List<Command>.seeks() = filterIsInstance<Command.Seek>().map { it.sourceMs }
    private fun List<Command>.views() = filterIsInstance<Command.Look>().map { it.view }
    private fun List<Command>.sounds() = filterIsInstance<Command.Sound>().map { it.sound }

    @Test
    fun theVideoDipsBeforeThePointEndAndThenPlaysTheReplay() {
        val director = director(point())

        assertTrue(director.onTime(9_000, playing = true).isEmpty())
        assertEquals(0.2, director.onTime(9_800, playing = true).views().single()!!.dip, 1e-9)

        val enter = director.onTime(10_010, playing = true)
        assertTrue(director.isActive)
        assertEquals(listOf(8_000L), enter.seeks())
        assertEquals(listOf(ReplaySound(0.5, false)), enter.sounds())
        assertEquals(listOf(ReplayView(dip = 1.0)), enter.views())
    }

    @Test
    fun oldTimesAfterTheSeekDoNotCount() {
        val director = director(point())
        director.onTime(9_900, playing = true)
        director.onTime(10_010, playing = true)

        // The player sends one more time of the old position.
        assertTrue(director.onTime(10_030, playing = true).isEmpty())
        assertTrue(director.isActive)

        director.onRestart()
        // 100 ms after the start of the part is 200 ms of play time: the dip of 250 ms is at 20%.
        assertEquals(0.2, director.onTime(8_100, playing = true).views().single()!!.dip, 1e-9)
    }

    @Test
    fun theRepeatsDipBetweenThemAndTheZoomGoesInButNotOut() {
        val director = director(point(repeats = 2, zoom = ReplayZoom(0.25, 0.25, 0.5)))
        director.onTime(9_900, playing = true)
        director.onTime(10_010, playing = true)
        director.onRestart()

        // 100 ms of the part is 200 ms of play: half of the zoom time of 400 ms. The smooth curve is at 0.5.
        val zoomIn = director.onTime(8_100, playing = true).views().single()!!
        assertEquals(0.75, zoomIn.zoomWidth, 1e-9)
        assertEquals(0.125, zoomIn.zoomX, 1e-9)

        // The end of the first play starts the second play with a short dip to black and the full zoom.
        val repeat = director.onTime(9_990, playing = true)
        assertEquals(listOf(8_000L), repeat.seeks())
        val second = repeat.views().single()!!
        assertEquals(1.0, second.dip, 1e-9)
        assertEquals(0.5, second.zoomWidth, 1e-9)
        director.onRestart()

        // Near the end of the last play, the video dips to black, and the zoom stays full (decision 12).
        val nearEnd = director.onTime(9_900, playing = true).views().single()!!
        assertEquals(0.2, nearEnd.dip, 1e-9)
        assertEquals(0.5, nearEnd.zoomWidth, 1e-9)

        // The end of the last play goes back to the point end. The video comes back from black.
        val exit = director.onTime(9_990, playing = true)
        assertFalse(director.isActive)
        assertEquals(listOf(10_000L), exit.seeks())
        assertEquals(listOf<ReplaySound?>(null), exit.sounds())
        director.onRestart()
        assertEquals(0.6, director.onTime(10_100, playing = true).views().single()!!.dip, 1e-9)
        assertEquals(listOf<ReplayView?>(null), director.onTime(10_300, playing = true).views())

        // The video does not play the replay again after the point end.
        assertTrue(director.onTime(10_400, playing = true).seeks().isEmpty())
    }

    @Test
    fun cutHasNoDipAndFlashDipsToWhite() {
        val cut = director(point(effect = ReplayEffect.CUT))
        assertTrue(cut.onTime(9_900, playing = true).isEmpty())
        assertTrue(cut.onTime(10_010, playing = true).views().isEmpty())

        val flash = director(point(effect = ReplayEffect.FLASH))
        assertTrue(flash.onTime(9_900, playing = true).views().single()!!.dipWhite)
    }

    @Test
    fun aPausedVideoAndASeekOverThePointEndDoNotStartTheReplay() {
        val paused = director(point())
        paused.onTime(9_900, playing = false)
        paused.onTime(10_010, playing = false)
        assertFalse(paused.isActive)

        val seek = director(point())
        seek.onTime(5_000, playing = true)
        seek.onTime(12_000, playing = true)
        assertFalse(seek.isActive)
    }

    @Test
    fun aSeekOfTheUserOutOfThePartStopsTheReplay() {
        val director = director(point())
        director.onTime(9_900, playing = true)
        director.onTime(10_010, playing = true)
        director.onRestart()
        director.onTime(8_500, playing = true)

        val stop = director.onTime(30_000, playing = true)
        assertFalse(director.isActive)
        assertEquals(listOf<ReplaySound?>(null), stop.sounds())
        // The picture was normal at that time, so only the sound changes back.
        assertTrue(stop.views().isEmpty())
    }

    @Test
    fun aChangedPointStopsItsReplay() {
        val director = director(point())
        director.onTime(9_900, playing = true)
        director.onTime(10_010, playing = true)
        director.onRestart()

        director.setPoints(listOf(point(repeats = 3)))
        assertFalse(director.isActive)
    }

    @Test
    fun theSecondReplayStartsWithTheZoomAlreadyIn() {
        val zoom = ReplayZoom(0.25, 0.25, 0.5)
        val first = ReplayV1(8_000, 10_000, zoom = zoom, speed = 0.5)
        val two = PointV1(id = "p1", startMs = 0, endMs = 10_000, replays = listOf(first, first))
        val director = director(two)
        director.onTime(9_900, playing = true)
        director.onTime(10_010, playing = true)
        director.onRestart()
        assertEquals(0.75, director.onTime(8_100, playing = true).views().single()!!.zoomWidth, 1e-9)

        // The second replay starts with a dip and the full zoom (decision 18).
        val next = director.onTime(9_990, playing = true)
        assertEquals(listOf(8_000L), next.seeks())
        director.onRestart()
        assertEquals(0.5, director.onTime(8_100, playing = true).views().single()!!.zoomWidth, 1e-9)
    }

    @Test
    fun theEditorPreviewOfTheSecondReplayStartsWithTheZoomAlreadyIn() {
        val director = ReplayDirector()
        director.setPoints(listOf(point(zoom = ReplayZoom(0.25, 0.25, 0.5))), firstReplayIndex = 1)
        director.onTime(9_900, playing = true)
        director.onTime(10_010, playing = true)
        director.onRestart()
        assertEquals(0.5, director.onTime(8_100, playing = true).views().single()!!.zoomWidth, 1e-9)
    }
}
