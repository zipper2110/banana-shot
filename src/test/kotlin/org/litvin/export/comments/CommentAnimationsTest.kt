package org.litvin.export.comments

import org.litvin.AssOverlayWriter
import org.litvin.CommentOverlaySpan
import org.litvin.export.ExportPlanner
import org.litvin.export.ExportReplays
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.points.CommentAnimation
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import org.litvin.points.PointV1
import org.litvin.points.ReplayFreeze
import org.litvin.points.ReplayV1
import java.awt.geom.Rectangle2D
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The comment animations: the times, the export tags, the frozen preview states, and the clocks of the preview. */
class CommentAnimationsTest {
    private val pos = Regex("""\\pos\([^)]*\)""")
    private val turn = Regex("""\\org\([^)]*\)\\frz-?\d+""")

    private fun events(style: CommentStyle, motion: CommentMotion, opacityPercent: Int = 100, rotationDeg: Int = 0) =
        CommentAss.events(
            "Great\nshot", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, style, opacityPercent = opacityPercent,
            rotationDeg = rotationDeg, motion = motion,
        )

    @Test
    fun aShortCommentGetsShorterFades() {
        assertEquals(300L to 300L, CommentAnimations.fadeMs(5_000))
        assertEquals(300L to 300L, CommentAnimations.fadeMs(600))
        assertEquals(200L to 200L, CommentAnimations.fadeMs(400))
        assertEquals(0L to 0L, CommentAnimations.fadeMs(0))
    }

    @Test
    fun theFadeStateIsLinearInTimeLikeLibassFad() {
        fun opacity(elapsedMs: Long) = CommentAnimations.frame(CommentAnimation.FADE, CommentClock(elapsedMs, 5_000)).opacity

        assertEquals(0.0, opacity(0))
        assertEquals(128 / 255.0, opacity(150), 1e-9, "Half of the fade in, in steps of an ASS alpha value")
        assertEquals(1.0, opacity(300))
        assertEquals(1.0, opacity(2_500))
        assertEquals(1.0, opacity(4_700))
        assertEquals(128 / 255.0, opacity(4_850), 1e-9, "Half of the fade out")
        assertEquals(0.0, opacity(5_000))
        assertEquals(CommentAnimations.Frame.REST, CommentAnimations.frame(CommentAnimation.NONE, CommentClock(0, 5_000)))
    }

    @Test
    fun theExportFadesAllEventsOfACommentTogether() {
        for (style in CommentStyle.entries) {
            val still = events(style, CommentMotion.Still)
            val timed = events(style, CommentMotion.Timed(CommentAnimation.FADE, 5_000))

            assertEquals(still.map { "{\\fad(300,300)}$it" }, timed, "Every event of $style fades, and nothing else changes")
            assertEquals(still, events(style, CommentMotion.Timed(CommentAnimation.NONE, 5_000)), "None has no tags")
        }
        val short = events(CommentStyle.CARD, CommentMotion.Timed(CommentAnimation.FADE, 400))
        assertTrue(short.all { it.startsWith("{\\fad(200,200)}") }, short.toString())
    }

    @Test
    fun theFrozenPreviewMultipliesTheOpacityOfAllParts() {
        val half = CommentMotion.Frozen(CommentAnimation.FADE, CommentClock(150, 5_000))
        val factor = 128 / 255.0
        val (shadow, card, text) = events(CommentStyle.CARD, half, opacityPercent = 50)

        // The opacity percent and the fade multiply, also for the transparent shadow and card.
        assertTrue(shadow.contains("\\1a${ScoreboardAss.alpha(0.44 * 0.5 * factor)}"), shadow)
        assertTrue(card.contains("\\1a${ScoreboardAss.alpha(0.85 * 0.5 * factor)}"), card)
        assertTrue(text.contains("\\1a${ScoreboardAss.alpha(0.5 * factor)}"), text)
        assertFalse(text.contains("\\fad"), "An osd-overlay has no event times, so the preview has no timing tags")
    }

    @Test
    fun theFrozenPreviewKeepsThePlaceAndTheRotation() {
        for (style in CommentStyle.entries) {
            val still = events(style, CommentMotion.Still, rotationDeg = 25)
            val half = events(style, CommentMotion.Frozen(CommentAnimation.FADE, CommentClock(4_850, 5_000)), rotationDeg = 25)

            assertEquals(still.size, half.size)
            assertEquals(still.map { pos.find(it)?.value }, half.map { pos.find(it)?.value }, "$style")
            assertEquals(still.map { turn.find(it)?.value }, half.map { turn.find(it)?.value }, "$style")
        }
    }

    @Test
    fun theFrozenPreviewAtRestIsTheStillComment() {
        val rest = events(CommentStyle.PILL, CommentMotion.Frozen(CommentAnimation.FADE, CommentClock(1_000, 5_000)))

        assertEquals(events(CommentStyle.PILL, CommentMotion.Still), rest)
        assertEquals(
            emptyList(),
            events(CommentStyle.PILL, CommentMotion.Frozen(CommentAnimation.FADE, CommentClock(0, 5_000))),
            "A fully transparent comment has no events",
        )
    }

    @Test
    fun previewEventsUseTheClockOfEachComment() {
        val area = Rectangle2D.Double(0.0, 0.0, 1920.0, 1080.0)
        val fading = CommentV1(1, 0, 5_000, "Fade", animation = CommentAnimation.FADE)
        val plain = CommentV1(2, 0, 5_000, "Plain")
        val still = CommentAss.previewEvents(listOf(fading, plain), area)

        assertEquals(still, CommentAss.previewEvents(listOf(fading, plain), area, listOf(null, null)))
        assertEquals(
            CommentAss.previewEvents(listOf(plain), area),
            CommentAss.previewEvents(listOf(fading, plain), area, listOf(CommentClock(0, 5_000), CommentClock(0, 5_000))),
            "The fading comment is transparent at its start. A comment without animation ignores its clock.",
        )
        val frames = CommentAnimations.previewFrames(listOf(fading, plain), listOf(CommentClock(150, 5_000)))
        assertEquals(listOf(CommentAnimations.Frame(128 / 255.0), CommentAnimations.Frame.REST), frames)
    }

    @Test
    fun aCommentOutsideItsTimeShowsStill() {
        val fading = CommentV1(1, 0, 5_000, "Fade", animation = CommentAnimation.FADE)

        // Only a drag or the open dialog shows a comment outside its time. The user must see it.
        assertEquals(CommentMotion.Still, CommentAnimations.preview(fading, CommentClock(-10, 5_000)))
        assertEquals(CommentMotion.Still, CommentAnimations.preview(fading, CommentClock(5_000, 5_000)))
        assertEquals(CommentMotion.Still, CommentAnimations.preview(fading, null))
    }

    @Test
    fun theReplayClocksAgreeWithTheExportSpans() {
        // The point comment starts before the part, so the replay shows it from the part start. The replay is slow and
        // has a still frame, so the play time is longer than the source time.
        val pointComment = CommentV1(1, 7_000, 2_500, "Point", animation = CommentAnimation.FADE)
        val replayComment = CommentV1(1, 8_500, 1_500, "Replay", animation = CommentAnimation.FADE)
        val replay = ReplayV1(8_000, 10_000, speed = 0.5, freeze = ReplayFreeze(9_000), comments = listOf(replayComment))
        val spans = ExportReplays.commentSpans(replay, listOf(pointComment))
        val pointSpan = spans.single { it.id == 1 }
        val replaySpan = spans.single { it.id == ExportReplays.REPLAY_COMMENT_ID_BASE + 1 }

        for (span in listOf(pointSpan, replaySpan)) {
            val clock = if (span === pointSpan) {
                CommentAnimations.replayPointClock(replay, pointComment, span.startMs + 100)
            } else {
                CommentAnimations.replayCommentClock(replay, replayComment, span.startMs + 100)
            }
            assertEquals(CommentClock(100, span.endMs - span.startMs), clock, "$span")
            assertEquals(CommentAnimation.FADE, span.animation)
        }
    }

    @Test
    fun theExportSpansKeepTheAnimation() {
        val comment = CommentV1(1, 1_000, 2_000, "Fade", animation = CommentAnimation.FADE)
        val point = PointV1(id = "p1", startMs = 0, endMs = 5_000)

        assertEquals(CommentAnimation.FADE, ExportPlanner.buildCommentOverlayTimeline(listOf(comment), listOf(point), false).single().animation)
        assertEquals(CommentAnimation.FADE, ExportPlanner.buildCommentOverlayTimeline(listOf(comment), listOf(point), true).single().animation)
        assertEquals(CommentClock(0, 2_000), CommentAnimations.sourceClock(comment, 1_000))
    }

    @Test
    fun theAssFileFadesEachEventFromTheSpanStart() {
        val fading = CommentOverlaySpan(1, 1_000, 3_000, "Fade", "#FFFFFF", CommentStyle.CARD, animation = CommentAnimation.FADE)
        val plain = CommentOverlaySpan(2, 4_000, 4_300, "Plain", "#FFFFFF", CommentStyle.CARD)
        val short = CommentOverlaySpan(3, 5_000, 5_400, "Short", "#FFFFFF", CommentStyle.OUTLINE, animation = CommentAnimation.FADE)
        val file = Files.createTempFile("comment_animation", ".ass").toFile()
        try {
            AssOverlayWriter.write(file, emptyList(), listOf(fading, plain, short), 1920, 1080)
            val dialogues = file.readLines().filter { it.startsWith("Dialogue:") }

            val fadingLines = dialogues.filter { ",0:00:01.00,0:00:03.00,Comment," in it }
            assertEquals(3, fadingLines.size, "Shadow, card, text: $dialogues")
            assertTrue(fadingLines.all { ",,{\\fad(300,300)}{" in it }, fadingLines.toString())
            assertTrue(dialogues.filter { ",0:00:04.00," in it }.none { "\\fad" in it }, dialogues.toString())
            assertTrue(dialogues.single { ",0:00:05.00," in it }.contains(",,{\\fad(200,200)}{"), dialogues.toString())
        } finally {
            file.delete()
        }
    }
}
