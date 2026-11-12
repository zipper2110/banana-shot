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
import org.litvin.shared.util.Edition
import java.awt.geom.Rectangle2D
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The comment animations: the times, the export tags, the frozen preview states, and the clocks of the preview.
 * The animations are for the paid version (docs/backlog-paid.md), so the tests run in the paid version. [free] runs a
 * part in the free version.
 */
class CommentAnimationsTest {
    private var oldPaid: String? = null

    @BeforeTest
    fun usePaidVersion() {
        oldPaid = System.getProperty(Edition.PROPERTY)
        System.setProperty(Edition.PROPERTY, "true")
    }

    @AfterTest
    fun restoreVersion() {
        oldPaid?.let { System.setProperty(Edition.PROPERTY, it) } ?: System.clearProperty(Edition.PROPERTY)
    }

    private fun <T> free(block: () -> T): T {
        System.setProperty(Edition.PROPERTY, "false")
        try {
            return block()
        } finally {
            System.setProperty(Edition.PROPERTY, "true")
        }
    }

    @Test
    fun theFreeVersionDrawsEveryCommentWithoutAnimation() {
        assertEquals(
            listOf(CommentAnimation.FADE, CommentAnimation.POP, CommentAnimation.SLIDE_UP),
            CommentAnimation.entries.filter { it.paid },
            "Only None is free",
        )
        for (animation in CommentAnimation.entries.filter { it.paid }) {
            val comment = CommentV1(1, 0, 5_000, "Paid", animation = animation)
            free {
                assertEquals(CommentAnimation.NONE, animation.orAvailable())
                for (style in CommentStyle.entries) {
                    val still = events(style, CommentMotion.Still)
                    assertEquals(still, events(style, CommentMotion.Timed(animation, 5_000)), "Export of $animation $style")
                    assertEquals(still, events(style, CommentMotion.Frozen(animation, CommentClock(0, 5_000))), "Preview of $animation $style")
                }
                assertEquals(CommentMotion.Still, CommentAnimations.preview(comment, CommentClock(100, 5_000)))
                assertEquals(listOf(CommentAnimations.Frame.REST), CommentAnimations.previewFrames(listOf(comment), listOf(CommentClock(100, 5_000))))
            }
            assertEquals(animation, animation.orAvailable(), "The paid version has $animation")
        }
    }
    private val pos = Regex("""\\pos\([^)]*\)""")
    private val turn = Regex("""\\org\([^)]*\)\\frz-?\d+""")

    private fun events(style: CommentStyle, motion: CommentMotion, opacityPercent: Int = 100, rotationDeg: Int = 0) =
        CommentAss.events(
            "Great\nshot", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, style, opacityPercent = opacityPercent,
            rotationDeg = rotationDeg, motion = motion,
        )

    @Test
    fun aShortCommentGetsShorterFades() {
        assertEquals(300L to 300L, CommentAnimations.times(CommentAnimation.FADE, 5_000))
        assertEquals(300L to 300L, CommentAnimations.times(CommentAnimation.FADE, 600))
        assertEquals(200L to 200L, CommentAnimations.times(CommentAnimation.FADE, 400))
        assertEquals(0L to 0L, CommentAnimations.times(CommentAnimation.FADE, 0))
        assertEquals(200L to 300L, CommentAnimations.times(CommentAnimation.POP, 5_000))
        assertEquals(350L to 300L, CommentAnimations.times(CommentAnimation.SLIDE_UP, 5_000))
        assertEquals(175L to 150L, CommentAnimations.times(CommentAnimation.SLIDE_UP, 325), "Both times get shorter in the same ratio")
        assertEquals(0L to 0L, CommentAnimations.times(CommentAnimation.NONE, 5_000))
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

    private val move = Regex("""\\move\(([-\d.]+),([-\d.]+),([-\d.]+),([-\d.]+),0,(\d+)\)""")
    private val posXY = Regex("""\\pos\(([-\d.]+),([-\d.]+)\)""")
    private val scaleX = Regex("""\\fscx([\d.]+)""")

    @Test
    fun aTimedPopGrowsEachPartAroundTheCenterOfTheComment() {
        val box = CommentAss.placement("Great\nshot", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD)!!.box
        val (shadow, card, text) = events(CommentStyle.CARD, CommentMotion.Timed(CommentAnimation.POP, 5_000))

        for ((event, originY) in listOf(shadow to box.centerY + 8, card to box.centerY, text to box.centerY)) {
            assertTrue(event.startsWith("{\\fad(200,300)}"), event)
            assertTrue(event.contains("\\fscx50.0\\fscy50.0\\t(0,200,\\fscx100\\fscy100)"), event)
            val (x1, y1, x2, y2, time) = move.find(event)!!.destructured
            assertEquals("200", time)
            // The part starts at half its distance from its rotation center, so the parts stay together.
            assertEquals(box.centerX + 0.5 * (x2.toDouble() - box.centerX), x1.toDouble(), 0.11, event)
            assertEquals(originY + 0.5 * (y2.toDouble() - originY), y1.toDouble(), 0.11, event)
        }
    }

    @Test
    fun aTimedSlideUpMovesEachPartUpByTheSameDistance() {
        for (style in CommentStyle.entries) {
            for ((size, distance) in listOf(100 to 48.0, 200 to 96.0)) {
                val timed = CommentAss.events(
                    "Great\nshot", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, style, sizePercent = size,
                    motion = CommentMotion.Timed(CommentAnimation.SLIDE_UP, 5_000),
                )
                for (event in timed) {
                    assertTrue(event.startsWith("{\\fad(350,300)}"), event)
                    assertTrue(event.contains("\\fscx100\\fscy100"), "A slide does not change the size: $event")
                    val (x1, y1, x2, y2, time) = move.find(event)!!.destructured
                    assertEquals("350", time)
                    assertEquals(x2, x1, event)
                    assertEquals(distance, y1.toDouble() - y2.toDouble(), 0.11, "$style $size%: $event")
                }
            }
        }
    }

    @Test
    fun theFrozenPreviewIsTheStateOfTheExportTagsAtThatTime() {
        // libass interpolates \move, \t, and \fad linearly. Halfway through the in time, the frozen preview must
        // show the same place, size, and opacity as the export.
        for (animation in listOf(CommentAnimation.POP, CommentAnimation.SLIDE_UP)) {
            for (style in CommentStyle.entries) {
                val inMs = CommentAnimations.times(animation, 5_000).first
                val timed = events(style, CommentMotion.Timed(animation, 5_000), rotationDeg = 30)
                val frozen = events(style, CommentMotion.Frozen(animation, CommentClock(inMs / 2, 5_000)), rotationDeg = 30)
                assertEquals(timed.size, frozen.size)
                timed.zip(frozen).forEach { (export, preview) ->
                    val (x1, y1, x2, y2) = move.find(export)!!.destructured
                    val (x, y) = posXY.find(preview)!!.destructured
                    assertEquals((x1.toDouble() + x2.toDouble()) / 2, x.toDouble(), 0.11, "$animation $style: $preview")
                    assertEquals((y1.toDouble() + y2.toDouble()) / 2, y.toDouble(), 0.11, "$animation $style: $preview")
                    val size = scaleX.find(preview)!!.groupValues[1].toDouble()
                    assertEquals(if (animation == CommentAnimation.POP) 75.0 else 100.0, size, "$animation $style: $preview")
                    assertEquals(turn.find(export)?.value, turn.find(preview)?.value, "The rotation center does not move")
                }
                // The text is half transparent, as \fad gives halfway through the fade in.
                assertTrue(frozen.last().contains("\\1a${ScoreboardAss.alpha(128 / 255.0)}"), frozen.last())
            }
        }
    }

    @Test
    fun aPopOrSlideAtRestIsTheStillComment() {
        for (animation in listOf(CommentAnimation.POP, CommentAnimation.SLIDE_UP)) {
            for (style in CommentStyle.entries) {
                val rest = events(style, CommentMotion.Frozen(animation, CommentClock(2_000, 5_000)), rotationDeg = -15)
                assertEquals(events(style, CommentMotion.Still, rotationDeg = -15), rest, "$animation $style")
            }
        }
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
