package org.litvin.share

import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
        ThumbnailField.ROUND to "Semi-final",
        ThumbnailField.CLUB to "Batumi beach club",
        ThumbnailField.SCORE to "6-4 7-5",
    )

    /** A logo of 200 × 100 px, as a wide club logo. */
    private val logo = ThumbnailLogo("test", BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB))

    private fun scene(
        style: ThumbnailStyle,
        place: TextPlace = style.places.first(),
        values: Map<ThumbnailField, String> = texts,
        logo: ThumbnailLogo? = null,
    ) = ThumbnailScene(style, place, values, ThumbnailLooks.colors(style, null), logo)

    /** YouTube shows the video length in the bottom-right corner. */
    private val lengthBadge = Rectangle2D.Double(1050.0, 630.0, 230.0, 90.0)

    /** Very long texts that must shrink. */
    private val long = mapOf(
        ThumbnailField.HEAD to "The most incredible comeback",
        ThumbnailField.KIND to "Men's doubles open category",
        ThumbnailField.TEAM1 to "Alexander Zverev / Maximilian Marterer",
        ThumbnailField.TEAM2 to "Alexander Bublik / Mikhail Kukushkin",
        ThumbnailField.EVENT to "National Beach Tennis Championship 2026",
        ThumbnailField.COURT to "Center court",
        ThumbnailField.DATE to "September 28, 2026",
        ThumbnailField.ROUND to "Quarter-final",
        ThumbnailField.CLUB to "Beach Tennis Club Copacabana",
        ThumbnailField.SCORE to "7-6 4-6 11-9",
    )

    @Test
    fun noStyleAndNoPositionPutsATextInTheBottomRightCornerOrOutsideThePng() {
        for (style in ThumbnailStyle.entries) {
            for (place in style.places) {
                for ((name, values) in listOf("short" to texts, "long" to long)) {
                    for (withLogo in listOf(null, logo)) {
                        val layout = ThumbnailRenderer.layout(scene(style, place, values, withLogo))
                        val case = "$style $place, $name texts, logo ${withLogo != null}"
                        assertTrue(layout.textShapes.isNotEmpty(), "$case shows no text")
                        // The outlines of the letters: a rotated line box can touch the corner, but its letters do not.
                        layout.textShapes.forEachIndexed { index, shape ->
                            val text = layout.texts[index].first
                            val bounds = shape.bounds2D
                            val inCorner = Area(shape).apply { intersect(Area(lengthBadge)) }
                            assertTrue(inCorner.isEmpty, "$case puts \"$text\" at $bounds in the corner of the video length")
                            assertTrue(bounds.minX >= -0.5 && bounds.maxX <= ThumbnailRenderer.WIDTH + 0.5, "$case: \"$text\" at $bounds is outside the PNG")
                            assertTrue(bounds.minY >= -0.5 && bounds.maxY <= ThumbnailRenderer.HEIGHT + 0.5, "$case: \"$text\" at $bounds is outside the PNG")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theSampleTextsOfTheDesignFitEachNewStyle() {
        val sample = mapOf(
            ThumbnailField.HEAD to "Best rally",
            ThumbnailField.KIND to "Mixed doubles",
            ThumbnailField.TEAM1 to "Anna & Mark",
            ThumbnailField.TEAM2 to "Leo & Kate",
            ThumbnailField.EVENT to "Spring Cup 2026",
            ThumbnailField.COURT to "Court 3",
            ThumbnailField.DATE to "May 12",
            ThumbnailField.ROUND to "Semi-final",
            ThumbnailField.CLUB to "Your club",
            ThumbnailField.SCORE to "6-4 7-5",
        )
        for (style in ThumbnailStyle.entries.filter { it.showsLogo }) {
            val layout = ThumbnailRenderer.layout(scene(style, values = sample, logo = logo))
            assertFalse(layout.shrunk, "$style made a sample text smaller: ${layout.texts}")
            assertFalse(layout.small, "$style has a small sample text: ${layout.texts}")
        }
    }

    @Test
    fun theNewStylesShowTheLogoAndTheOldStylesDoNot() {
        for (style in ThumbnailStyle.entries) {
            val pictures = ThumbnailRenderer.layout(scene(style, logo = logo)).items.count { it is ThumbnailRenderer.Item.Picture }
            assertEquals(if (style.showsLogo) 1 else 0, pictures, "$style")
        }
    }

    @Test
    fun withoutALogoTheClubNameGoesUp() {
        fun club(logo: ThumbnailLogo?): Rectangle2D {
            val layout = ThumbnailRenderer.layout(scene(ThumbnailStyle.SPLASH, logo = logo))
            return layout.textBounds[layout.texts.indexOfFirst { it.first == "Batumi beach club" }]
        }
        assertTrue(club(null).y < club(logo).y - 50, "Without the logo: ${club(null)}. With the logo: ${club(logo)}")
    }

    @Test
    fun theBrandNameKeepsTheLettersAsTheUserTypesThem() {
        for (style in ThumbnailStyle.entries.filter { ThumbnailField.CLUB in it.fields }) {
            val texts = ThumbnailRenderer.layout(scene(style, logo = logo)).texts.map { it.first }
            assertFalse(texts.any { "BATUMI BEACH CLUB" in it }, "$style: $texts")
            assertTrue(texts.any { "Batumi beach club" in it }, "$style: $texts")
        }
    }

    @Test
    fun theBrandNameHasItsOwnFontAndColor() {
        fun brand(style: ThumbnailStyle, face: ThumbnailFace?, rgb: Int?): ThumbnailRenderer.Item.Text {
            val scene = scene(style, logo = logo).copy(brandFace = face, brandRgb = rgb)
            fun walk(items: List<ThumbnailRenderer.Item>): List<ThumbnailRenderer.Item.Text> = items.flatMap {
                when (it) {
                    is ThumbnailRenderer.Item.Text -> listOf(it)
                    is ThumbnailRenderer.Item.Group -> walk(it.items)
                    else -> emptyList()
                }
            }
            return walk(ThumbnailRenderer.layout(scene).items).first { it.text.equals("Batumi beach club", ignoreCase = true) }
        }
        val marker = ThumbnailFonts.base(ThumbnailFace.MARKER)!!.family

        // Under the logo, at the side of the logo, and in the info line of the official bar.
        for (style in listOf(ThumbnailStyle.SPLASH, ThumbnailStyle.SIDE_PANEL, ThumbnailStyle.OFFICIAL_BAR, ThumbnailStyle.SCORE_BUG)) {
            val styled = brand(style, ThumbnailFace.MARKER, 0xFF2E88)
            assertEquals(marker, styled.font.family, "$style")
            assertEquals(0xFF2E88, styled.color!!.rgb and 0xFFFFFF, "$style")
            assertEquals(255, styled.color!!.alpha, "$style")

            val auto = brand(style, null, null)
            assertNotEquals(marker, auto.font.family, "$style")
            assertNotEquals(0xFF2E88, auto.color!!.rgb and 0xFFFFFF, "$style")
        }
    }

    @Test
    fun theShadowOfTheBrandNameContrastsWithItsColor() {
        fun shadow(rgb: Int): Int? {
            val scene = scene(ThumbnailStyle.SPLASH, logo = logo).copy(brandRgb = rgb)
            val text = ThumbnailRenderer.layout(scene).items.filterIsInstance<ThumbnailRenderer.Item.Text>()
                .first { it.text == "Batumi beach club" }
            return text.shadow?.rgb
        }
        assertEquals(0x000000, shadow(0xFFFFFF), "A white name has a black shadow")
        assertEquals(0x000000, shadow(0xC8F031), "A lime name has a black shadow")
        assertEquals(0xFFFFFF, shadow(0x141414), "A black name has a white shadow")
        assertEquals(0xFFFFFF, shadow(0x1E3A8A), "A navy name has a white shadow")
    }

    @Test
    fun aTextWithLettersThatTheFontDoesNotHaveUsesTheFontOfTheApp() {
        // Big Shoulders Display has no Cyrillic letters.
        val layout = ThumbnailRenderer.layout(scene(ThumbnailStyle.FIGHT_CARD, values = texts + (ThumbnailField.TEAM1 to "Дима / Яна")))
        val team = layout.items.filterIsInstance<ThumbnailRenderer.Item.Text>().first { it.text == "ДИМА / ЯНА" }
        assertEquals(-1, team.font.canDisplayUpTo(team.text))
    }

    @Test
    fun theScoreBugReadsTheSetsOfTheScore() {
        assertEquals(listOf("6" to "4", "7" to "5"), ThumbnailDesigns.sets("6-4 7-5"))
        assertEquals(listOf("7" to "6", "4" to "6", "11" to "9"), ThumbnailDesigns.sets("7–6 4–6 11–9"))
        assertEquals(listOf("6" to "3"), ThumbnailDesigns.sets("6:3"))
        assertEquals(emptyList(), ThumbnailDesigns.sets("won"))
        assertEquals(listOf("7" to "6", "4" to "6"), ThumbnailDesigns.sets("7-6, 4-6"))
        // A text with other words is not a score of sets. The styles show it as the user types it.
        assertEquals(emptyList(), ThumbnailDesigns.sets("6-4 ret."))
        assertEquals(emptyList(), ThumbnailDesigns.sets("shocking score"))
    }

    @Test
    fun aScoreThatIsNotSetsShowsAsTheUserTypesIt() {
        for (style in listOf(ThumbnailStyle.SCORELINE, ThumbnailStyle.SCORE_BUG)) {
            val values = mapOf(
                ThumbnailField.TEAM1 to "Ilya",
                ThumbnailField.TEAM2 to "Dima",
                ThumbnailField.SCORE to "shocking score",
            )
            val texts = ThumbnailRenderer.layout(scene(style, values = values)).texts.map { it.first }
            assertTrue(texts.any { "shocking score" in it }, "$style: $texts")
        }
    }

    @Test
    fun aTeamNameBreaksBeforeItsSeparator() {
        assertEquals(listOf("ANNA", "& MARK"), ThumbnailDesigns.teamRows("ANNA & MARK"))
        assertEquals(listOf("DIMA", "/ YANA"), ThumbnailDesigns.teamRows("DIMA / YANA"))
        assertEquals(listOf("NADAL"), ThumbnailDesigns.teamRows("NADAL"))
    }

    @Test
    fun eachGroupHasItsStyles() {
        assertEquals(31, ThumbnailStyle.entries.size)
        assertEquals(6, StyleGroup.CLASSIC.styles.size)
        StyleGroup.entries.filter { it != StyleGroup.CLASSIC }.forEach { assertEquals(5, it.styles.size, "$it") }
        assertTrue(StyleGroup.CLASSIC.styles.none { it.showsLogo })
    }

    @Test
    fun eachStyleShowsOnlyItsFields() {
        for (style in ThumbnailStyle.entries) {
            val shown = ThumbnailRenderer.layout(scene(style)).texts.joinToString(" ") { it.first }.lowercase()
            // Some styles show the league in two rows, or with its last word in another color.
            assertEquals(ThumbnailField.EVENT in style.fields, "league" in shown, "$style and the league: $shown")
        }
    }

    @Test
    fun eachFieldOfAStyleShowsAndTheOtherFieldsDoNot() {
        for (style in ThumbnailStyle.entries) {
            val all = ThumbnailRenderer.layout(scene(style, logo = logo)).texts.map { it.first }
            for (field in ThumbnailField.entries) {
                val without = ThumbnailRenderer.layout(scene(style, values = texts - field, logo = logo)).texts.map { it.first }
                // A field of the style shows also when all other fields have a text. No other field takes its place.
                assertEquals(field in style.fields, all != without, "$style and $field")
            }
        }
    }

    @Test
    fun eachColorOfTheColorsTabChangesTheThumbnail() {
        val frame = BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB).apply {
            val g2 = createGraphics()
            g2.color = java.awt.Color(0x808080)
            g2.fillRect(0, 0, width, height)
            g2.dispose()
        }
        fun pixels(style: ThumbnailStyle, look: StyleLookV1?): IntArray {
            val scene = ThumbnailScene(style, style.places.first(), texts, ThumbnailLooks.colors(style, look), logo.takeIf { style.showsLogo })
            return ThumbnailRenderer.render(frame, FrameView(), scene).getRGB(0, 0, 1280, 720, null, 0, 1280)
        }
        for (style in ThumbnailStyle.entries) {
            val before = pixels(style, null)
            for (role in style.roles.keys) {
                val other = if (ThumbnailLooks.roleValue(style, null, role) == "#3dd65a") "#7a1fd1" else "#3dd65a"
                val after = pixels(style, StyleLookV1(colors = mapOf(role.key to other)))
                assertFalse(before.contentEquals(after), "$style shows the role $role, but the role changes nothing")
            }
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
