package org.litvin.export.preview

import org.litvin.RenderJob
import org.litvin.export.ExportReplays
import org.litvin.export.preview.ExportPreviewDirector.Command
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.media.ReplaySound
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayFreeze
import org.litvin.points.ReplayV1
import org.litvin.stats.StatsCardVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Covers the play of the export preview (B-75 T2 and T3). */
class ExportPreviewDirectorTest {
    private val p1 = PointV1(id = "p1", startMs = 10_000, endMs = 14_000)
    private val p2 = PointV1(id = "p2", startMs = 20_000, endMs = 26_000)
    private val p3 = PointV1(id = "p3", startMs = 40_000, endMs = 43_000)

    private var clock = 1_000_000L

    private fun director(keeps: List<PointV1>, statsCard: StatsCardVideo? = null): ExportPreviewDirector {
        val job = RenderJob(
            sourcePath = "source.mp4",
            edlSnapshot = keeps,
            presetId = "balanced",
            outWidth = 1920,
            outHeight = 1080,
            encoderLabel = "test",
            idleTrim = true,
            outputPath = "out.mp4",
            statsCard = statsCard,
            replays = ExportReplays.of(keeps, emptyList()),
        )
        return ExportPreviewDirector(ExportPreviewTimeline.of(job, 60_000), 60_000, now = { clock })
    }

    private fun List<Command>.seeks() = filterIsInstance<Command.Seek>().map { it.sourceMs }
    private fun List<Command>.dips() = filterIsInstance<Command.Look>().map { it.view?.dip ?: 0.0 }

    @Test
    fun openShowsTheFirstFramePaused() {
        val director = director(listOf(p1, p2))

        val commands = director.open()

        assertEquals(listOf(10_000L), commands.seeks())
        assertTrue(Command.Pause in commands)
        assertEquals(0L, director.outMs)
    }

    @Test
    fun theEndOfAPointCutsToTheNextPoint() {
        val director = director(listOf(p1, p2, p3))
        director.open()
        assertEquals(listOf(Command.Play), director.play())
        director.onTime(10_000, playerPlaying = true)
        director.onTime(12_000, playerPlaying = true)
        assertEquals(2_000L, director.outMs)

        val cut = director.onTime(13_990, playerPlaying = true)

        assertEquals(listOf(20_000L), cut.seeks())
        assertTrue(Command.Play in cut)
        assertEquals(1, director.index)
        assertEquals(4_000L, director.outMs)
        // An old time of the player before the seek does not count.
        assertTrue(director.onTime(13_995, playerPlaying = true).isEmpty())
        assertEquals(1, director.index)
        director.onTime(20_000, playerPlaying = true)
        director.onTime(21_000, playerPlaying = true)
        assertEquals(5_000L, director.outMs)
    }

    @Test
    fun pointsThatTouchPlayOnWithoutASeek() {
        val touching = PointV1(id = "p1b", startMs = 14_000, endMs = 18_000)
        val director = director(listOf(p1, touching))
        director.open()
        director.play()
        director.onTime(10_000, playerPlaying = true)

        val cut = director.onTime(13_990, playerPlaying = true)

        assertTrue(cut.seeks().isEmpty())
        assertEquals(1, director.index)
    }

    @Test
    fun theLastPointEndsThePreview() {
        val director = director(listOf(p1, p3))
        director.open()
        director.play()
        director.onTime(10_000, playerPlaying = true)
        director.onTime(13_990, playerPlaying = true)
        director.onTime(40_000, playerPlaying = true)

        val end = director.onTime(42_990, playerPlaying = true)

        assertEquals(listOf(Command.Pause), end)
        assertTrue(director.atEnd)
        assertFalse(director.playing)
        // Play at the end starts again from the first item.
        assertEquals(listOf(10_000L), director.play().seeks())
    }

    @Test
    fun aSeekOfTheUserGoesToTheItemAtThatTime() {
        val director = director(listOf(p1, p2, p3))
        director.open()

        val commands = director.seek(11_000)

        assertEquals(2, director.index)
        assertEquals(listOf(41_000L), commands.seeks())
        assertEquals(listOf(20_000L), director.previousItem().seeks())
        assertEquals(listOf(40_000L), director.nextItem().seeks())
    }

    @Test
    fun aReplayPlaysAfterItsPointAndTheNextPointFadesIn() {
        val withReplay = p2.copy(replays = listOf(ReplayV1(24_000, 26_000, speed = 0.5, effect = ReplayEffect.FADE)))
        val director = director(listOf(withReplay, p3))
        director.open()
        director.play()
        director.onTime(20_000, playerPlaying = true)

        // The point fades out into the replay.
        assertEquals(0.6, director.onTime(25_900, playerPlaying = true).dips().single(), 1e-9)
        val enter = director.onTime(25_990, playerPlaying = true)
        assertEquals(listOf(24_000L), enter.seeks())
        assertTrue(Command.Sound(ReplaySound(0.5, false)) in enter)
        assertIs<ExportPreviewTimeline.Replay>(director.item)

        director.onTime(24_000, playerPlaying = true)
        director.onTime(25_000, playerPlaying = true)
        assertEquals(6_000L + 2_000L, director.outMs)
        assertIs<PreviewOverlay.Replay>(director.overlay)

        val out = director.onTime(25_990, playerPlaying = true)
        // The video goes on with the next point, not at the point end.
        assertEquals(listOf(40_000L), out.seeks())
        assertEquals(2, director.index)
        assertEquals(1.0, out.dips().last(), 1e-9)
        assertTrue(Command.Sound(null) in out)
    }

    @Test
    fun theStillFrameOfAReplayHoldsForItsTime() {
        val replay = ReplayV1(24_000, 26_000, speed = 0.5, effect = ReplayEffect.CUT, freeze = ReplayFreeze(25_000, 1_000))
        val director = director(listOf(p2.copy(replays = listOf(replay)), p3))
        director.open()
        director.play()
        director.seek(6_000)
        director.onTime(24_000, playerPlaying = true)
        director.onTime(24_500, playerPlaying = true)

        val hold = director.onTime(25_000, playerPlaying = true)

        assertEquals(1, hold.filterIsInstance<Command.ShowFrame>().size)
        clock += 400
        assertTrue(director.tick().isEmpty())
        assertEquals(6_000L + 2_000L + 400L, director.outMs)
        clock += 600
        assertEquals(listOf(Command.Play), director.tick())
    }

    @Test
    fun aSeekIntoAReplayStartsThatReplayFromItsStart() {
        val withReplay = p2.copy(replays = listOf(ReplayV1(24_000, 26_000, speed = 0.5, effect = ReplayEffect.FADE)))
        val director = director(listOf(withReplay, p3))
        director.open()

        // The replay is from 6 s to 10 s of the output. A seek to 8 s goes to its start (decision 14).
        val commands = director.seek(8_000)

        assertIs<ExportPreviewTimeline.Replay>(director.item)
        assertEquals(listOf(24_000L), commands.seeks())
        assertEquals(6_000L, director.outMs)
    }

    @Test
    fun aPauseStopsTheTimeOfAStillFrame() {
        val replay = ReplayV1(24_000, 26_000, speed = 0.5, effect = ReplayEffect.CUT, freeze = ReplayFreeze(25_000, 1_000))
        val director = director(listOf(p2.copy(replays = listOf(replay)), p3))
        director.open()
        director.play()
        director.seek(6_000)
        director.onTime(24_000, playerPlaying = true)
        director.onTime(25_000, playerPlaying = true)
        clock += 300
        director.tick()

        assertEquals(listOf(Command.Pause), director.pause())
        clock += 5_000
        assertTrue(director.tick().isEmpty())
        assertEquals(6_000L + 2_000L + 300L, director.outMs)
        // Play goes on with the rest of the still frame. The player stays on the frame until the still frame ends.
        assertTrue(director.play().isEmpty())
        clock += 600
        assertTrue(director.tick().isEmpty())
        clock += 100
        assertEquals(listOf(Command.Play), director.tick())
    }

    @Test
    fun aCardShowsTheFrozenFrameForItsTime() {
        val card = StatsCardVideo(List(2) { ScoreboardScene(100.0, 100.0, emptyList()) }, pageDurationMs = 1_000L)
        val director = director(listOf(p1), statsCard = card)
        director.open()
        director.play()
        director.onTime(10_000, playerPlaying = true)

        val enter = director.onTime(13_990, playerPlaying = true)

        assertEquals(listOf(Command.ShowFrame(13_960.0)), enter.filterIsInstance<Command.ShowFrame>())
        clock += 500
        director.tick()
        assertEquals(4_500L, director.outMs)
        // A pause stops the time of the card.
        director.pause()
        clock += 5_000
        director.tick()
        assertEquals(4_500L, director.outMs)
        director.play()
        clock += 1_500
        assertEquals(listOf(Command.Pause), director.tick())
        assertTrue(director.atEnd)
    }
}
