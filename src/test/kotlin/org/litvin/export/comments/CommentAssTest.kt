package org.litvin.export.comments

import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import java.awt.geom.Rectangle2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentAssTest {
    private fun comment(id: Int, startMs: Int, durationMs: Int, text: String = "Comment $id") =
        CommentV1(id = id, startMs = startMs, durationMs = durationMs, text = text, colorHex = "#22AAFF")

    @Test
    fun commentIsOnScreenFromItsStartToTheEndOfItsDuration() {
        val comments = listOf(comment(1, 1_000, 2_000), comment(2, 2_500, 1_000), comment(3, 9_000, 1_000, text = " "))

        assertEquals(emptyList(), CommentAss.activeAt(comments, 999).map { it.id })
        assertEquals(listOf(1), CommentAss.activeAt(comments, 1_000).map { it.id })
        assertEquals(listOf(1, 2), CommentAss.activeAt(comments, 2_600).map { it.id })
        assertEquals(listOf(2), CommentAss.activeAt(comments, 3_000).map { it.id })
        assertEquals(emptyList(), CommentAss.activeAt(comments, 3_500).map { it.id })
        assertEquals(emptyList(), CommentAss.activeAt(comments, 9_500).map { it.id }, "A blank comment shows nothing")
    }

    @Test
    fun cardEventsSetTheirOwnTagsAndStayInTheVideoArea() {
        // The preview video area starts at (100, 50) in the window.
        val events = CommentAss.events("IN {review}\nGreat point", "#22AAFF", 100.0, 50.0, 960.0, 540.0, CommentStyle.CARD)

        assertEquals(3, events.size, "Shadow, card, text: $events")
        val (shadow, card, text) = events
        assertTrue(shadow.contains("\\blur"), shadow)
        assertTrue(card.startsWith("{\\an7\\pos("), card)
        assertTrue(card.contains("\\p1}m "), card)
        assertTrue(card.contains(" b "), "The card has rounded corners: $card")
        // The text is centered in the video area, above the lower-third line.
        assertTrue(text.startsWith("{\\an2\\pos(580.0,"), text)
        assertTrue(text.contains("\\fnArial"), text)
        assertTrue(text.contains("\\1c&HFFAA22&\\1a&H00&"), "The text has the opaque comment color (ASS uses BGR): $text")
        assertTrue(text.endsWith("IN \\{review\\}\\NGreat point"), text)

        val position = Regex("""\\pos\(([\d.]+),([\d.]+)\)""")
        val (left, top) = position.find(card)!!.destructured
        assertTrue(left.toDouble() >= 100.0 && top.toDouble() >= 50.0, "The card starts in the video area: $card")
        val textY = position.find(text)!!.groupValues[2].toDouble()
        assertTrue(textY < 50.0 + 540.0 * 0.82, "The text is above the lower-third line: $text")
    }

    @Test
    fun cardFitsTheTextWidth() {
        fun cardWidth(text: String): Double {
            val card = CommentAss.events(text, "#FFFFFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD)[1]
            return Regex("""m \d+ 0 l (\d+) 0""").find(card)!!.groupValues[1].toDouble()
        }
        // "WWWW" and "iiii" have the same number of letters, but a different width.
        assertTrue(cardWidth("WWWW") > cardWidth("iiii") * 1.5, "The card follows the measured width, not the letter count")
    }

    @Test
    fun outlineIsOneTextEventWithAContrastingOutline() {
        val onYellow = CommentAss.events("whoops", "#F2E14B", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.OUTLINE)
        assertEquals(1, onYellow.size)
        assertTrue(onYellow[0].contains("\\3c&H141414&"), "A light color gets a dark outline: ${onYellow[0]}")
        assertFalse(onYellow[0].contains("\\shad0"), "A dark outline has a shadow: ${onYellow[0]}")

        val onNavy = CommentAss.events("whoops", "#102060", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.OUTLINE)
        assertTrue(onNavy[0].contains("\\3c&HFFFFFF&"), "A dark color gets a light outline: ${onNavy[0]}")
        assertTrue(onNavy[0].contains("\\shad0"), "A light outline has no shadow: ${onNavy[0]}")
    }

    @Test
    fun cardIsDarkForALightTextAndLightForADarkText() {
        val light = CommentAss.events("x", "#FFFFFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD)[1]
        val dark = CommentAss.events("x", "#000080", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD)[1]
        assertTrue(light.contains("\\1c&H1A1614&"), light)
        assertTrue(dark.contains("\\1c&HF4F4F4&"), dark)
    }

    @Test
    fun pillsHaveOnePillForEachLineAndAContrastingText() {
        val events = CommentAss.events("first\nsecond", "#E04CE0", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.PILL)

        assertEquals(6, events.size, "Two shadows, two pills, two texts: $events")
        assertTrue(events[2].contains("\\1c&HE04CE0&\\1a&H00&"), "The pill has the comment color: ${events[2]}")
        assertTrue(events[4].endsWith("first") && events[5].endsWith("second"), events.toString())

        val onYellow = CommentAss.events("x", "#F2E14B", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.PILL)
        assertTrue(onYellow[2].contains("\\1c&H141414&"), "A yellow pill gets a dark text: ${onYellow[2]}")
        val onBlue = CommentAss.events("x", "#1040C0", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.PILL)
        assertTrue(onBlue[2].contains("\\1c&HFFFFFF&"), "A blue pill gets a white text: ${onBlue[2]}")
    }

    @Test
    fun contrastColorFollowsTheLuminance() {
        assertEquals(0x141414, CommentAss.contrastColor(0xFFFFFF))
        assertEquals(0x141414, CommentAss.contrastColor(0xF2E14B))
        assertEquals(0x141414, CommentAss.contrastColor(0x22AAFF))
        assertEquals(0xFFFFFF, CommentAss.contrastColor(0x000000))
        assertEquals(0xFFFFFF, CommentAss.contrastColor(0xC0392B))
    }

    @Test
    fun wrapBreaksAtSpacesAndBreaksALongWordBetweenLetters() {
        val measure = { line: String -> line.length * 10.0 }
        assertEquals(listOf("one two", "three"), CommentAss.wrapLines("one two three", 70.0, measure))
        assertEquals(listOf("abcde", "fgh x"), CommentAss.wrapLines("abcdefgh x", 50.0, measure))
        assertEquals(listOf("abcde", "fghij", "k yy"), CommentAss.wrapLines("abcdefghijk yy", 50.0, measure))
        assertEquals(listOf("a", "", "b"), CommentAss.wrapLines("a\n\nb", 50.0, measure))
    }

    @Test
    fun blankTextOrEmptyAreaGivesNoEvents() {
        assertEquals(emptyList(), CommentAss.events("  ", "#FFFFFF", 0.0, 0.0, 1920.0, 1080.0))
        assertEquals(emptyList(), CommentAss.events("Text", "#FFFFFF", 0.0, 0.0, 0.0, 1080.0))
    }

    @Test
    fun previewEventsDrawEachCommentInItsOwnStyle() {
        val area = Rectangle2D.Double(0.0, 0.0, 1280.0, 720.0)
        val events = CommentAss.previewEvents(listOf(comment(1, 0, 1_000), comment(2, 0, 1_000).copy(style = CommentStyle.CARD)), area)
        assertEquals(4, events.size, "Outline gives one event, Card gives three: $events")
        assertTrue(events[0].endsWith("Comment 1"))
        assertTrue(events[3].endsWith("Comment 2"))
    }

    @Test
    fun previewReportsOnlyChangesOfTheCommentsOnScreen() {
        val preview = CommentPreview()
        assertFalse(preview.setComments(listOf(comment(1, 1_000, 2_000))), "No comment is on screen at 0")

        assertTrue(preview.moveTo(1_000))
        assertEquals(listOf(1), preview.shown.map { it.id })
        assertFalse(preview.moveTo(1_500), "The same comment stays on screen")
        assertTrue(preview.moveTo(3_000))
        assertEquals(emptyList(), preview.shown)

        // A new comment at the playhead shows at once.
        assertTrue(preview.setComments(listOf(comment(1, 1_000, 2_000), comment(2, 2_900, 500))))
        assertEquals(listOf(2), preview.shown.map { it.id })
    }
}
