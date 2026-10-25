package org.litvin.export

import org.litvin.AssOverlayWriter
import org.litvin.CommentOverlaySpan
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
