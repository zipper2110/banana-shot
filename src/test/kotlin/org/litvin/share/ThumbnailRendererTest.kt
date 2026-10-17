package org.litvin.share

import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThumbnailRendererTest {
    private val texts = mapOf(
        ThumbnailField.HEAD to "BEST POINTS",
        ThumbnailField.KIND to "DOUBLES",
        ThumbnailField.TEAM1 to "Dima / Yana",
        ThumbnailField.TEAM2 to "Iliya / Marina",
        ThumbnailField.EVENT to "Friday doubles league",
        ThumbnailField.COURT to "Batumi : Court Airport",
        ThumbnailField.DATE to "11 Oct 2026",
    )

    private fun scene(style: ThumbnailStyle, place: TextPlace = style.places.first(), values: Map<ThumbnailField, String> = texts) =
        ThumbnailScene(style, place, values, ThumbnailLooks.colors(style, null))

    /** YouTube shows the video length in the bottom-right corner. */
    private val lengthBadge = Rectangle2D.Double(1050.0, 630.0, 230.0, 90.0)

    @Test
    fun noStyleAndNoPositionPutsATextInTheBottomRightCornerOrOutsideThePng() {
        for (style in ThumbnailStyle.entries) {
            for (place in style.places) {
                val layout = ThumbnailRenderer.layout(scene(style, place))
                assertTrue(layout.textBounds.isNotEmpty(), "$style $place shows no text")
                layout.textBounds.forEach { bounds ->
                    assertFalse(bounds.intersects(lengthBadge), "$style $place puts a text at $bounds in the corner of the video length")
                    assertTrue(bounds.minX >= 0 && bounds.maxX <= ThumbnailRenderer.WIDTH, "$style $place: $bounds is outside the PNG")
                    assertTrue(bounds.minY >= 0 && bounds.maxY <= ThumbnailRenderer.HEIGHT, "$style $place: $bounds is outside the PNG")
                }
            }
        }
    }

    @Test
    fun eachStyleShowsOnlyItsFields() {
        for (style in ThumbnailStyle.entries) {
            val shown = ThumbnailRenderer.layout(scene(style)).texts.map { it.first }
            val event = texts.getValue(ThumbnailField.EVENT).uppercase()
            assertEquals(ThumbnailField.EVENT in style.fields, shown.any { event in it }, "$style and the league: $shown")
        }
    }

    @Test
    fun anEmptyFieldDoesNotShow() {
        val layout = ThumbnailRenderer.layout(scene(ThumbnailStyle.VERSUS, values = texts + (ThumbnailField.TEAM2 to "  ")))

        val shown = layout.texts.map { it.first }
        assertEquals(listOf("BEST POINTS", "DIMA / YANA"), shown)
    }

    @Test
    fun theLargeWordsGoInTwoRowsWithTheNarrowestSplit() {
        assertEquals(listOf("BEST", "POINTS"), ThumbnailRenderer.rows("BEST POINTS"))
        assertEquals(listOf("WIMBLEDON"), ThumbnailRenderer.rows("WIMBLEDON"))
        assertEquals(emptyList(), ThumbnailRenderer.rows("   "))
    }

    @Test
    fun aLongTextBecomesSmallerUntilItFits() {
        val long = texts + (ThumbnailField.TEAM1 to "Aleksandra Konstantinova / Margarita Belousova")
        val layout = ThumbnailRenderer.layout(scene(ThumbnailStyle.VERSUS, values = long))

        assertTrue(layout.shrunk)
        val team = layout.textBounds[layout.texts.indexOfFirst { it.first.startsWith("ALEKSANDRA") }]
        assertTrue(team.width <= 640.0 + 0.5, "The team is ${team.width} px wide")
        assertFalse(ThumbnailRenderer.layout(scene(ThumbnailStyle.VERSUS)).shrunk)
    }

    @Test
    fun aTextSmallerThan32PxIsSmall() {
        // The card shows "vs" and the court at 26 px.
        assertTrue(ThumbnailRenderer.layout(scene(ThumbnailStyle.CARD)).small)
        assertFalse(ThumbnailRenderer.layout(scene(ThumbnailStyle.WORDS)).small)
        assertFalse(ThumbnailRenderer.layout(scene(ThumbnailStyle.POSTER)).small)
    }

    @Test
    fun longTeamNamesGoInTwoBarsInBroadcast() {
        fun teams(values: Map<ThumbnailField, String>): Pair<Rectangle2D, Rectangle2D> {
            val layout = ThumbnailRenderer.layout(scene(ThumbnailStyle.BROADCAST, values = values))
            val names = layout.texts.map { it.first }
            val team1 = names.indexOf(values.getValue(ThumbnailField.TEAM1).uppercase())
            val team2 = names.indexOf(values.getValue(ThumbnailField.TEAM2).uppercase())
            return layout.textBounds[team1] to layout.textBounds[team2]
        }

        val (short1, short2) = teams(texts)
        assertEquals(short1.y, short2.y, 0.001)
        val long = texts + (ThumbnailField.TEAM1 to "Aleksandra K. / Margarita B.") + (ThumbnailField.TEAM2 to "Iliya V. / Marina S.")
        val (long1, long2) = teams(long)
        assertTrue(long2.minY >= long1.maxY - 1, "The second team is not under the first team: $long1, $long2")
    }

    @Test
    fun theTextPositionMovesTheTexts() {
        val right = ThumbnailRenderer.layout(scene(ThumbnailStyle.WORDS, TextPlace.RIGHT)).textBounds
        val left = ThumbnailRenderer.layout(scene(ThumbnailStyle.WORDS, TextPlace.LEFT)).textBounds

        assertTrue(right.all { it.centerX > ThumbnailRenderer.WIDTH / 2 })
        assertTrue(left.all { it.centerX < ThumbnailRenderer.WIDTH / 2 })
        val top = ThumbnailRenderer.layout(scene(ThumbnailStyle.BROADCAST, TextPlace.TOP)).textBounds
        assertTrue(top.all { it.centerY < ThumbnailRenderer.HEIGHT / 2 })
    }

    @Test
    fun splitAtTheRightAlignsTheTextsAtTheRight() {
        val right = ThumbnailRenderer.layout(scene(ThumbnailStyle.SPLIT, TextPlace.RIGHT))
        assertEquals(listOf("DOUBLES", "BEST", "POINTS", "FRIDAY DOUBLES LEAGUE"), right.texts.map { it.first })
        right.textBounds.forEach { assertEquals(ThumbnailRenderer.WIDTH - 56.0, it.maxX, 1.0, "${right.texts}") }

        val left = ThumbnailRenderer.layout(scene(ThumbnailStyle.SPLIT, TextPlace.LEFT))
        left.textBounds.forEach { assertEquals(56.0, it.minX, 1.0) }
    }

    @Test
    fun theZoomShowsAPartOfTheFrame() {
        val full = ThumbnailRenderer.sourceRect(2560, 1440, FrameView())
        assertEquals(Rectangle2D.Double(0.0, 0.0, 2560.0, 1440.0), full)

        val center = ThumbnailRenderer.sourceRect(2560, 1440, FrameView(zoom = 2.0))
        assertEquals(Rectangle2D.Double(640.0, 360.0, 1280.0, 720.0), center)

        val topLeft = ThumbnailRenderer.sourceRect(2560, 1440, FrameView(zoom = 2.0, panX = 0.0, panY = 0.0))
        assertEquals(0.0, topLeft.x)
        assertEquals(0.0, topLeft.y)
    }

    @Test
    fun aFrameOfAnotherShapeFillsThePng() {
        // A 4:3 frame: the top and the bottom are cut, as the CSS "cover".
        val rect = ThumbnailRenderer.sourceRect(1440, 1080, FrameView())
        assertEquals(0.0, rect.x, 0.001)
        assertEquals(1440.0, rect.width, 0.001)
        assertEquals(810.0, rect.height, 0.001)
        assertEquals(135.0, rect.y, 0.001)
    }

    @Test
    fun theImageHasTheSizeOfAYouTubeThumbnail() {
        val frame = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
        for (style in ThumbnailStyle.entries) {
            val image = ThumbnailRenderer.render(frame, FrameView(zoom = 1.5), scene(style))
            assertEquals(1280, image.width)
            assertEquals(720, image.height)
        }
    }

    @Test
    fun theAccentColorPaintsTheLargeWords() {
        val colors = ThumbnailLooks.colors(ThumbnailStyle.WORDS, StyleLookV1(colors = mapOf("acc" to "#ff4d4d")))
        val layout = ThumbnailRenderer.layout(ThumbnailScene(ThumbnailStyle.WORDS, TextPlace.RIGHT, texts, colors))
        val image = BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics()
        ThumbnailRenderer.paintLayout(g2, layout)
        g2.dispose()

        // The middle of the "O" stroke of "POINTS" at the right.
        val bounds = layout.textBounds[layout.texts.indexOfFirst { it.first == "POINTS" }]
        val reds = (bounds.minX.toInt() until bounds.maxX.toInt()).count { x ->
            val rgb = image.getRGB(x, bounds.centerY.toInt()) and 0xFFFFFF
            rgb == 0xFF4D4D
        }
        assertTrue(reds > 20, "The large words have $reds red pixels on their middle line")
    }
}
