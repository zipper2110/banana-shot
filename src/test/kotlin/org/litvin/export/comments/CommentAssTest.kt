package org.litvin.export.comments

import org.litvin.points.CommentStyle
import org.litvin.points.CommentPosition
import org.litvin.points.CommentV1
import java.awt.geom.Rectangle2D
import java.util.Locale
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

    /** B-11 E8-T4: a Japanese comment breaks between characters, and not before "、" or "。". */
    @Test
    fun japaneseCommentWrapsBetweenCharacters() {
        val measure: (String) -> Double = { it.length * 10.0 }
        val text = "ナイスショット、すばらしいポイントでした。"
        val lines = CommentAss.wrapLines(text, 70.0, measure)
        assertEquals(text, lines.joinToString(""))
        lines.forEach { line ->
            assertTrue(line.length <= 7, "\"$line\" is too long")
            assertTrue(line.first() !in "、。ー", "\"$line\" starts with a closing mark")
        }
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

    @Test
    fun commentWithoutPositionIsAtTheDefaultPosition() {
        for (style in CommentStyle.entries) {
            val withoutPosition = CommentAss.events("Great point", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, style)
            val atDefault = CommentAss.events("Great point", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, style, CommentPosition.DEFAULT)
            assertEquals(withoutPosition, atDefault, "$style")
            val placement = CommentAss.placement("Great point", 0.0, 0.0, 1920.0, 1080.0, style)!!
            assertEquals(CommentPosition.DEFAULT, placement.position, "The default position does not move: $style")
        }
    }

    @Test
    fun positionMovesTheComment() {
        // The video area starts at (100, 50) in the window.
        val text = CommentAss.events("Top left", "#FFFFFF", 100.0, 50.0, 1000.0, 500.0, position = CommentPosition(0.3, 0.25)).single()

        assertTrue(text.startsWith("{\\an2\\pos(400.0,175.0)"), text)
        val placement = CommentAss.placement("Top left", 100.0, 50.0, 1000.0, 500.0, position = CommentPosition(0.3, 0.25))!!
        assertEquals(CommentPosition(0.3, 0.25), placement.position)
        assertEquals(400.0, placement.box.centerX, 0.01)
    }

    @Test
    fun commentAtTheEdgeMovesInsideTheFrame() {
        for (style in CommentStyle.entries) {
            for (position in listOf(CommentPosition(0.0, 0.0), CommentPosition(1.0, 1.0), CommentPosition(0.02, 0.98))) {
                val placement = CommentAss.placement("A long comment\nin two lines", 100.0, 50.0, 960.0, 540.0, style, position)!!
                val box = placement.box
                assertTrue(box.x >= 100.0 - 0.01 && box.y >= 50.0 - 0.01, "$style $position: $box")
                assertTrue(box.maxX <= 1060.0 + 0.01 && box.maxY <= 590.0 + 0.01, "$style $position: $box")
                assertTrue(placement.position != position, "The position changed, because the box did not fit: $style $position")
            }
        }
    }

    @Test
    fun boxContainsTheDrawnCard() {
        val position = CommentPosition(0.7, 0.4)
        val placement = CommentAss.placement("Card", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, position)!!
        val card = CommentAss.events("Card", "#FFFFFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, position)[1]
        val (left, top) = Regex("""\\pos\(([\d.]+),([\d.]+)\)""").find(card)!!.destructured
        // The path starts "m r 0 l (w - r) 0 b w 0", so the first curve gives the width.
        val width = Regex("""l \d+ 0 b (\d+) 0""").find(card)!!.groupValues[1].toDouble()
        assertEquals(placement.box.x, left.toDouble(), 0.1)
        assertEquals(placement.box.y, top.toDouble(), 0.1)
        assertEquals(placement.box.width, width, 0.1)
    }

    @Test
    fun sizePercentScalesTheComment() {
        fun fontSize(events: List<String>) = Regex("""\\fs([\d.]+)""").find(events.last())!!.groupValues[1].toDouble()
        val normal = CommentAss.events("Size", "#FFFFFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD)
        val large = CommentAss.events("Size", "#FFFFFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, sizePercent = 200)
        assertEquals(fontSize(normal) * 2, fontSize(large), 0.1)

        val normalBox = CommentAss.placement("Size", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD)!!.box
        val largeBox = CommentAss.placement("Size", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, sizePercent = 200)!!.box
        assertEquals(normalBox.height * 2, largeBox.height, 2.0)
        assertEquals(normalBox.maxY, largeBox.maxY, 0.01, "The lower edge stays at its position")
    }

    @Test
    fun opacityPercentMakesAllPartsTransparent() {
        val events = CommentAss.events("Half", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, opacityPercent = 50)
        val (shadow, card, text) = events
        // ASS alpha is inverted: 00 is opaque, FF is transparent.
        assertTrue(text.contains("\\1a&H7F&"), "The text is half transparent: $text")
        assertTrue(card.contains("\\1a&H93&"), "The card is 85% × 50% opaque: $card")
        assertTrue(shadow.contains("\\1a&HC7&"), "The shadow is 44% × 50% opaque: $shadow")

        val pill = CommentAss.events("Half", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.PILL, opacityPercent = 50)[1]
        assertTrue(pill.contains("\\1a&H7F&"), pill)
        val outline = CommentAss.events("Half", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, opacityPercent = 50).single()
        assertTrue(outline.contains("\\3a&H7F&") && outline.contains("\\1a&H7F&"), outline)
    }

    @Test
    fun rotationTurnsAllPartsAroundTheCenterOfTheBox() {
        val events = CommentAss.events("Turn", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, rotationDeg = 30)
        val box = CommentAss.placement("Turn", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, rotationDeg = 30)!!.box
        val (shadow, card, text) = events
        val center = String.format(Locale.US, "\\org(%.1f,%.1f)", box.centerX, box.centerY)
        // ASS turns counterclockwise for a positive angle, so a clockwise rotation gives a negative \frz.
        assertTrue(card.contains("$center\\frz-30"), card)
        assertTrue(text.contains("$center\\frz-30"), text)
        // The shadow is 8 px below the card. Its center is also 8 px lower, so the shadow stays below the comment.
        val shadowCenter = String.format(Locale.US, "\\org(%.1f,%.1f)", box.centerX, box.centerY + 8)
        assertTrue(shadow.contains("$shadowCenter\\frz-30"), shadow)
    }

    @Test
    fun commentWithoutRotationHasNoRotationCenter() {
        val events = CommentAss.events("Flat", "#22AAFF", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.PILL)

        assertTrue(events.all { it.contains("\\frz0") && !it.contains("\\org") }, events.toString())
    }

    @Test
    fun rotatedCommentMovesInsideTheFrame() {
        val top = CommentPosition(0.5, 0.0)
        val flat = CommentAss.placement("Top edge", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, top)!!
        val turned = CommentAss.placement("Top edge", 0.0, 0.0, 1920.0, 1080.0, CommentStyle.CARD, top, rotationDeg = 90)!!

        assertEquals(flat.box.height / 2, flat.box.centerY, 0.01, "The flat card touches the top edge")
        // After a quarter turn, the card is as tall as it was wide. It moves down until its top touches the edge.
        assertEquals(turned.box.width / 2, turned.box.centerY, 0.01)
        assertEquals(flat.box.centerX, turned.box.centerX, 0.01)
        assertTrue(turned.position.y > flat.position.y, "The turned comment has a lower position: ${turned.position}")
    }
}
