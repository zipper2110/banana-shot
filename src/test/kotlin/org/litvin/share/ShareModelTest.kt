package org.litvin.share

import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.Sport
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareModelTest {
    private val dir: File = Files.createTempDirectory("share-model").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun points(vararg favorites: Int, count: Int = 9) =
        (1..count).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 6_000, favorite = it in favorites) }

    private fun project(points: List<PointV1>, score: ScoreV1 = ScoreV1(player1Name = "Dima / Yana", player2Name = "Iliya / Marina")) =
        ShareProject.build(dir.path, "Who won?", null, EdlV1(points = points), score, LocalDate.of(2026, 10, 11))

    @Test
    fun withFavoritesTheDefaultsAreBestPointsAndTheFirstFavorite() {
        val project = project(points(4, 7))

        assertEquals(ThumbnailVideoType.BEST_POINTS, project.defaultVideoType)
        assertEquals("p4", project.defaultPoint?.id)
        assertEquals("0:12", project.videoLength(ThumbnailVideoType.BEST_POINTS))
        assertEquals("0:54", project.videoLength(ThumbnailVideoType.FULL_MATCH))
    }

    @Test
    fun withoutFavoritesTheDefaultsAreFullMatchAndAPointNearTheMiddle() {
        val project = project(points())

        assertEquals(ThumbnailVideoType.FULL_MATCH, project.defaultVideoType)
        assertEquals("p5", project.defaultPoint?.id)
        assertEquals(53_000L, project.defaultPoint?.middleMs)
    }

    @Test
    fun thePresetsComeFromTheProject() {
        val score = ScoreV1(
            player1Name = "Dima / Yana",
            player2Name = "Iliya / Marina",
            scoreboard = ScoreboardSettingsV1(title = "Batumi : Court Airport"),
        )
        val presets = project(points(), score).presets(ThumbnailVideoType.FULL_MATCH, "Friday league")

        assertEquals("FULL MATCH", presets[ThumbnailField.HEAD])
        assertEquals("DOUBLES", presets[ThumbnailField.KIND])
        assertEquals("Dima / Yana", presets[ThumbnailField.TEAM1])
        assertEquals("Friday league", presets[ThumbnailField.EVENT])
        assertEquals("Batumi : Court Airport", presets[ThumbnailField.COURT])
        assertEquals("11 Oct 2026", presets[ThumbnailField.DATE])
    }

    @Test
    fun theDefaultNamesAndTheDefaultTitleGiveNoPreset() {
        val presets = project(points(), ScoreV1()).presets(ThumbnailVideoType.BEST_POINTS, null)

        assertEquals("", presets[ThumbnailField.TEAM1])
        assertEquals("", presets[ThumbnailField.TEAM2])
        assertEquals("", presets[ThumbnailField.COURT])
        assertEquals("", presets[ThumbnailField.EVENT])
        assertEquals("SINGLES", presets[ThumbnailField.KIND])
        assertEquals("PADEL", project(points(), ScoreV1(sport = Sport.PADEL)).matchKind())
        assertEquals("BEACH TENNIS", project(points(), ScoreV1(sport = Sport.BEACH_TENNIS)).matchKind())
    }

    @Test
    fun theFinalScoreNeedsScoredPoints() {
        assertNull(project(points()).finalScore)

        // Player 1 wins 24 points in a row: two sets of 6-0.
        val many = points(count = 48)
        val outcomes = many.take(48).associate { it.id to Outcome.P1 }
        val scored = project(many, ScoreV1(outcomes = outcomes))
        assertEquals("6–0 6–0", scored.finalScore)
        assertEquals("Set 2 · 0–0", scored.points[24].scoreLabel)
    }

    @Test
    fun eachVideoTypeHasItsOwnFileWithASafeName() {
        val project = project(points())

        assertEquals("Who won_-full-match.png", project.thumbnailFile(dir, ThumbnailVideoType.FULL_MATCH).name)
        assertEquals("Who won_-best-points.png", project.thumbnailFile(dir, ThumbnailVideoType.BEST_POINTS).name)
    }

    @Test
    fun autoMakesTheTextDarkOrWhite() {
        assertEquals(0x141414, ThumbnailLooks.onColor(0xC8EC46))
        assertEquals(0xFFFFFF, ThumbnailLooks.onColor(0x0B1E3A))
    }

    @Test
    fun aRoleValueOverridesTheColorSetOfTheStyle() {
        val tv = ThumbnailLooks.colors(ThumbnailStyle.BROADCAST, null)
        assertEquals(0xFFD54A, tv.accent)
        assertEquals(0x141414, tv.onAccent)

        val look = StyleLookV1(colorSet = "ocean", colors = mapOf("acc" to "#ff4d4d", "onPan" to "#c8ec46"), shadeStrength = 0.5)
        val colors = ThumbnailLooks.colors(ThumbnailStyle.CARD, look)
        assertEquals(0xFF4D4D, colors.accent)
        assertEquals(0x0B1E3A, colors.panel)
        assertEquals(0xC8EC46, colors.onPanel)
        assertEquals(0.5, colors.strength)
        assertTrue(look.customized)
        assertEquals(StyleLookV1(place = "right"), StyleLookV1(place = "right", colorSet = "red").withoutColors())
    }

    @Test
    fun aPositionThatTheStyleDoesNotHaveUsesTheDefault() {
        assertEquals(TextPlace.CENTER, ThumbnailLooks.place(ThumbnailStyle.POSTER, StyleLookV1(place = "left")))
        assertEquals(TextPlace.TOP, ThumbnailLooks.place(ThumbnailStyle.BROADCAST, StyleLookV1(place = "top")))
        assertEquals(TextPlace.BOTTOM, ThumbnailLooks.place(ThumbnailStyle.BROADCAST, null))
    }

    @Test
    fun theProjectStoresTheShareSettings() {
        val settings = ShareSettingsV1(
            videoType = "best",
            pointId = "p4",
            momentMs = 42_100,
            zoom = 1.25,
            panX = 0.35,
            style = "tv",
            looks = mapOf("tv" to StyleLookV1(place = "top", colorSet = "sunset", colors = mapOf("acc" to "auto"), shadeStrength = 0.8)),
            texts = mapOf("event" to "Club league"),
            finalScore = true,
            saved = mapOf("best" to SavedThumbnailV1("C:/x/a-best-points.png", 1_760_000_000_000, "abc")),
        )

        assertNull(ShareIO.readForProjectDir(dir.path))
        ShareIO.writeForProjectDir(dir.path, settings)
        assertEquals(settings, ShareIO.readForProjectDir(dir.path))
    }

    @Test
    fun theDefaultsForTheNextProjectHaveNoTexts() {
        val defaults = ShareDefaultsV1(style = "card", looks = mapOf("card" to StyleLookV1(place = "right")), event = "Club league")

        assertEquals(defaults, ShareIO.defaultsFromJson(ShareIO.defaultsToJson(defaults)))
    }

    @Test
    fun aTooLargePngGetsASofterFrame() {
        val file = File(dir, "noise.png")
        val random = java.util.Random(1)
        val noise = java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 720) for (x in 0 until 1280) noise.setRGB(x, y, random.nextInt(0x1000000))
        val tries = mutableListOf<Int>()

        val bytes = ThumbnailPng.write(file) { softness ->
            tries += softness
            var image = noise
            repeat(softness) { image = ThumbnailPng.soften(image) }
            image
        }

        assertEquals(file.length(), bytes)
        assertTrue(tries.size > 1, "Random noise is larger than 2 MB, so the app tries a softer frame")
        assertEquals(1280, javax.imageio.ImageIO.read(file).width)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
}
