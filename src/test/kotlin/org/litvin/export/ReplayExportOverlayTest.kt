package org.litvin.export

import org.litvin.AssOverlayWriter
import org.litvin.CommentOverlaySpan
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.points.PointV1
import org.litvin.points.ReplayV1
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers the overlay of a replay pass and the sample rate probe (B-50). */
class ReplayExportOverlayTest {
    @Test
    fun theSampleRateComesFromTheFirstLineOrTheDefault() {
        assertEquals(44_100, SourceSampleRateProbe.parse("44100\n"))
        assertEquals(48_000, SourceSampleRateProbe.parse("48000,\r\n"))
        assertEquals(SourceSampleRateProbe.DEFAULT_RATE, SourceSampleRateProbe.parse(""))
        assertEquals(SourceSampleRateProbe.DEFAULT_RATE, SourceSampleRateProbe.parse("N/A\n"))
    }

    @Test
    fun aCommentInThePartShowsAtTheSameMomentOfThePlayAndLastsLonger() {
        val replay = ReplayV1(8_000, 10_000, speed = 0.5)
        val comments = listOf(
            org.litvin.points.CommentV1(id = 1, startMs = 9_000, durationMs = 3_000, text = "In", colorHex = "#FFFFFF"),
            org.litvin.points.CommentV1(id = 2, startMs = 2_000, durationMs = 1_000, text = "Before", colorHex = "#FFFFFF"),
        )

        val spans = ExportReplays.commentSpans(replay, comments)

        // The comment shows from 1 s into the part to its end: 2 s to 4 s of the play at half speed.
        assertEquals(listOf(Triple(1, 2_000L, 4_000L)), spans.map { Triple(it.id, it.startMs, it.endMs) })
    }

    @Test
    fun theReplayCommentsShowOverThePointCommentsAndTheSwitchHidesThePointComments() {
        fun comment(id: Int, start: Int, duration: Int) =
            org.litvin.points.CommentV1(id = id, startMs = start, durationMs = duration, text = "C$id", colorHex = "#FFFFFF")
        val own = comment(1, 9_000, 1_500)
        val replay = ReplayV1(8_000, 10_000, speed = 0.5, comments = listOf(own))
        val pointComments = listOf(comment(7, 9_000, 1_000))

        val both = ExportReplays.commentSpans(replay, pointComments)
        // The point comment stretches by the slow speed. The replay comment keeps its time.
        assertEquals(
            listOf(Triple(7, 2_000L, 4_000L), Triple(ExportReplays.REPLAY_COMMENT_ID_BASE + 1, 2_000L, 3_500L)),
            both.map { Triple(it.id, it.startMs, it.endMs) },
        )

        val own2 = ExportReplays.commentSpans(replay.copy(showPointComments = false), pointComments)
        assertEquals(listOf(ExportReplays.REPLAY_COMMENT_ID_BASE + 1), own2.map { it.id })
    }

    @Test
    fun anExportWithoutCommentsHasNoReplayComments() {
        val replay = ReplayV1(
            8_000, 10_000,
            comments = listOf(org.litvin.points.CommentV1(id = 1, startMs = 8_500, durationMs = 1_000, text = "Lob", colorHex = "#FFFFFF")),
        )
        val point = org.litvin.points.PointV1(id = "P", startMs = 5_000, endMs = 10_000, replays = listOf(replay))
        assertEquals(1, ExportReplays.of(listOf(point), emptyList()).single().comments.size)
        assertEquals(emptyList(), ExportReplays.of(listOf(point), emptyList(), includeComments = false).single().comments)
    }

    @Test
    fun aCommentAtTheStillFrameStaysDuringTheStillFrame() {
        val replay = ReplayV1(8_000, 10_000, speed = 0.5, freeze = org.litvin.points.ReplayFreeze(9_000, 1_000))
        fun comment(id: Int, start: Int, duration: Int) =
            org.litvin.points.CommentV1(id = id, startMs = start, durationMs = duration, text = "C$id", colorHex = "#FFFFFF")
        val comments = listOf(comment(1, 9_000, 3_000), comment(2, 8_000, 1_000), comment(3, 8_500, 1_000))

        val spans = ExportReplays.commentSpans(replay, comments)

        // The still frame is 2 s to 3 s of the play. A comment that ends at the still frame ends when it starts.
        assertEquals(
            listOf(Triple(2, 0L, 2_000L), Triple(3, 1_000L, 4_000L), Triple(1, 2_000L, 5_000L)),
            spans.map { Triple(it.id, it.startMs, it.endMs) },
        )
    }

    @Test
    fun theBadgeIsABoxAndATextAtTheUpperRight() {
        val events = ReplayBadgeAss.events("REPLAY", 1920.0, 1080.0)

        assertEquals(2, events.size)
        assertTrue("\\p1}" in events[0], events[0])
        assertTrue(events[1].endsWith("}REPLAY"), events[1])
        val left = Regex("""\\pos\(([0-9.]+),""").find(events[0])!!.groupValues[1].toDouble()
        assertTrue(left > 1920 / 2, "left $left")
        assertTrue(ReplayBadgeAss.events(" ", 1920.0, 1080.0).isEmpty())
    }

    @Test
    fun theExportBadgeHasTheLabelOfEachReplayAndTheDefaultInTheVideoLanguage() {
        val point = PointV1("p1", 10_000, 14_000).copy(
            replays = listOf(
                ReplayV1(9_000, 12_000),
                ReplayV1(10_000, 12_000, label = "Great lob"),
                ReplayV1(11_000, 13_000),
            ),
        )
        val replays = I18n.inVideo(AppLanguage.GERMAN) { ExportReplays.of(listOf(point), emptyList()) }
        assertEquals(listOf("WIEDERHOLUNG", "GREAT LOB", "WIEDERHOLUNG 3"), replays.map { it.badgeText })

        val file = Files.createTempFile("replay_label", ".ass").toFile()
        try {
            AssOverlayWriter.writeReplay(file, replays[1], 1920, 1080)
            assertTrue("}GREAT LOB" in file.readText())
        } finally {
            file.delete()
        }
    }

    @Test
    fun theReplayOverlayHasTheCommentsAndTheBadgeForThePlay() {
        val comment = CommentOverlaySpan(
            id = 1, startMs = 0, endMs = 2_000, text = "Look", colorHex = "#FFFFFF",
        )
        val replay = ExportReplay("p1", 10_000, ReplayV1(8_000, 10_000, speed = 0.5), listOf(comment), badgeText = "REPLAY")
        val file = Files.createTempFile("replay", ".ass").toFile()
        try {
            AssOverlayWriter.writeReplay(file, replay, 1920, 1080)
            val text = file.readText()
            assertTrue("Dialogue: 2000,0:00:00.00,0:00:04.00,Comment" in text, text)
            assertTrue("0:00:00.00,0:00:02.00,Comment,,0,0,0,,{" in text, text)
            assertTrue("}Look" in text, text)

            AssOverlayWriter.writeReplay(file, replay.copy(comments = emptyList(), badgeText = null), 1920, 1080)
            assertFalse("Dialogue:" in file.readText())
        } finally {
            file.delete()
        }
    }
}
