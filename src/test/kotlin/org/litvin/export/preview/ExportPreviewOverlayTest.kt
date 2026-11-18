package org.litvin.export.preview

import org.litvin.CommentOverlaySpan
import org.litvin.OverlaySpan
import org.litvin.RenderJob
import org.litvin.export.ExportReplay
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.points.ReplayV1
import org.litvin.stats.StatsCardVideo
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.GraphicsEnvironment
import java.awt.geom.Rectangle2D
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers the overlay of the export preview (B-75 T2 and T3). */
class ExportPreviewOverlayTest {
    private val area = Rectangle2D.Double(0.0, 0.0, 1280.0, 720.0)
    private val board = OverlaySpan(startMs = 1_000, endMs = 5_000, text = "", fadeInMs = 400, fadeOutMs = 400)
    private val comment = CommentOverlaySpan(id = 1, startMs = 2_000, endMs = 3_000, text = "Nice shot", colorHex = "#FFFFFF")

    private fun job(scoreboard: Boolean, comments: Boolean) = RenderJob(
        sourcePath = "source.mp4",
        presetId = "balanced",
        outWidth = 1920,
        outHeight = 1080,
        encoderLabel = "test",
        idleTrim = true,
        outputPath = "out.mp4",
        includeScoreboard = scoreboard,
        overlayTimeline = listOf(board),
        includeComments = comments,
        commentOverlayTimeline = listOf(comment),
    )

    @Test
    fun theVideoShowsTheBoardAndTheCommentsOfThatMoment() {
        val job = job(scoreboard = true, comments = true)

        val before = ExportPreviewOverlay.events(job, PreviewOverlay.Video(500), area)
        val boardOnly = ExportPreviewOverlay.events(job, PreviewOverlay.Video(1_500), area)
        val both = ExportPreviewOverlay.events(job, PreviewOverlay.Video(2_500), area)

        assertTrue(before.isEmpty())
        assertTrue(boardOnly.isNotEmpty())
        assertTrue(both.size > boardOnly.size)
        assertTrue(both.any { "Nice shot" in it })
    }

    @Test
    fun anExportWithoutScoreboardAndCommentsShowsNothing() {
        val job = job(scoreboard = false, comments = false)

        assertTrue(ExportPreviewOverlay.events(job, PreviewOverlay.Video(2_500), area).isEmpty())
    }

    @Test
    fun theBoardFadesAsTheExportFadesIt() {
        assertEquals(0.5, ExportPreviewOverlay.boardOpacity(board, 1_200), 1e-9)
        assertEquals(1.0, ExportPreviewOverlay.boardOpacity(board, 3_000), 1e-9)
        assertEquals(0.25, ExportPreviewOverlay.boardOpacity(board, 4_900), 1e-9)
        assertEquals("", ExportPreviewOverlay.fadeTag(board, 3_000))
        assertEquals("{\\fade(128,128,128,0,0,0,0)}", ExportPreviewOverlay.fadeTag(board, 1_200))
    }

    @Test
    fun aReplayShowsItsBadgeAndNoBoard() {
        val job = job(scoreboard = true, comments = true)
        val replay = ExportReplay(pointId = "p1", pointEndMs = 10_000, replay = ReplayV1(8_000, 10_000), badgeText = "REPLAY")
        val item = ExportPreviewTimeline.Replay(0L, replay)

        val events = ExportPreviewOverlay.events(job, PreviewOverlay.Replay(item, 500), area)

        assertTrue(events.any { "REPLAY" in it })
    }

    @Test
    fun aReplayShowsItsCommentsOnlyInTheirTime() {
        val job = job(scoreboard = true, comments = true)
        val replayComment = CommentOverlaySpan(id = 7, startMs = 400, endMs = 1_400, text = "Look at this", colorHex = "#FFFFFF")
        val replay = ExportReplay(pointId = "p1", pointEndMs = 10_000, replay = ReplayV1(8_000, 10_000), comments = listOf(replayComment))
        val item = ExportPreviewTimeline.Replay(0L, replay)

        fun textsAt(offsetMs: Long) = ExportPreviewOverlay.events(job, PreviewOverlay.Replay(item, offsetMs), area)

        assertTrue(textsAt(200).none { "Look at this" in it })
        assertTrue(textsAt(800).any { "Look at this" in it })
        assertTrue(textsAt(1_500).none { "Look at this" in it })
        // The comments of the video are not over the replay. The replay has its own list.
        assertTrue(textsAt(800).none { "Nice shot" in it })
    }

    @Test
    fun theTextsUseTheFontOfTheVideoLanguage() {
        // The Japanese and the Chinese video fonts come with Windows (ScriptFonts). Other systems skip this test.
        val families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ENGLISH).toSet()
        assumeTrue("Yu Gothic" in families && "Microsoft YaHei" in families)
        val han = CommentOverlaySpan(id = 1, startMs = 0, endMs = 1_000, text = "決勝", colorHex = "#FFFFFF")
        fun events(language: String) = ExportPreviewOverlay.events(
            job(scoreboard = false, comments = true).copy(commentOverlayTimeline = listOf(han), videoLanguage = language),
            PreviewOverlay.Video(500),
            area,
        )

        assertTrue(events("ja").any { "Yu Gothic" in it })
        assertTrue(events("zh-Hans").any { "Microsoft YaHei" in it })
    }

    @Test
    fun aCardShowsThePageOfItsTime() {
        fun page(rgb: Int) = ScoreboardScene(1920.0, 1080.0, listOf(SceneItem.Box(0.0, 0.0, 100.0, 100.0, rgb)))
        val card = StatsCardVideo(listOf(page(0xFF0000), page(0x00FF00)), pageDurationMs = 1_000)
        val item = ExportPreviewTimeline.Card(0L, card, null, null)
        val job = job(scoreboard = false, comments = false)

        val first = ExportPreviewOverlay.events(job, PreviewOverlay.Card(item, 500), area)
        val second = ExportPreviewOverlay.events(job, PreviewOverlay.Card(item, 1_500), area)
        // A time after the card end shows the last page.
        val late = ExportPreviewOverlay.events(job, PreviewOverlay.Card(item, 5_000), area)

        assertEquals(1, first.size)
        assertTrue(first != second)
        assertEquals(second, late)
    }
}
