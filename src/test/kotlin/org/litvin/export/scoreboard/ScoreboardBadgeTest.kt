package org.litvin.export.scoreboard

import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardTimelineBuilder
import org.litvin.points.PointV1
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreboardPosition
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import org.litvin.scoring.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-14: the "GOLDEN POINT" and "STAR POINT" badge next to the scoreboard. */
class ScoreboardBadgeTest {
    private fun display(badge: String?) = ScoreboardComponent.display(
        OverlaySpan(
            startMs = 0L, endMs = 1_000L, text = "", p1Name = "Team 1", p2Name = "Team 2",
            p1Pts = 5, p2Pts = 5, badge = badge,
        ),
    )

    private fun ScoreboardScene.label(text: String) = items.filterIsInstance<SceneItem.Label>().single { it.text == text }

    @Test
    fun everyStyleShowsTheBadgeInsideTheScene() {
        ScoreboardStyleId.entries.forEach { style ->
            ScoreboardPosition.entries.forEach { position ->
                val settings = ScoreboardSettingsV1(style = style, position = position)
                val plain = ScoreboardLayouts.scene(display(null), settings)
                val scene = ScoreboardLayouts.scene(display("STAR POINT"), settings)

                scene.label("STAR POINT")
                assertEquals(plain.height + ScoreboardBadge.HEIGHT + ScoreboardBadge.GAP, scene.height, 0.001, "$style $position")
                scene.items.filterIsInstance<SceneItem.Box>().forEach { box ->
                    assertTrue(box.x >= -1 && box.x + box.width <= scene.width + 1, "$style $position: $box")
                    assertTrue(box.y >= -1 && box.y + box.height <= scene.height + 1, "$style $position: $box")
                }
            }
        }
    }

    @Test
    fun theBadgeIsAwayFromTheVideoEdgeAndTheBoardDoesNotMove() {
        val frame = 1920.0 to 1080.0
        ScoreboardPosition.entries.forEach { position ->
            val settings = ScoreboardSettingsV1(position = position)
            val plain = ScoreboardLayouts.scene(display(null), settings)
            val scene = ScoreboardLayouts.scene(display("GOLDEN POINT"), settings)
            val plainAt = ScoreboardAss.place(plain, settings, 0.0, 0.0, frame.first, frame.second)
            val badgeAt = ScoreboardAss.place(scene, settings, 0.0, 0.0, frame.first, frame.second)
            // The first item of the board (its background) is at the same place in the video.
            val plainFirst = plain.items.first() as SceneItem.Box
            val movedFirst = scene.items.first() as SceneItem.Box
            assertEquals(plainAt.x + plainFirst.x * plainAt.scale, badgeAt.x + movedFirst.x * badgeAt.scale, 0.01, "$position")
            assertEquals(plainAt.y + plainFirst.y * plainAt.scale, badgeAt.y + movedFirst.y * badgeAt.scale, 0.01, "$position")

            val badgeY = scene.label("GOLDEN POINT").y
            val top = position == ScoreboardPosition.TOP_LEFT || position == ScoreboardPosition.TOP_RIGHT
            assertEquals(top, badgeY > plain.height, "$position: the badge is under a board at the top")
        }
    }

    @Test
    fun theSettingHidesTheBadge() {
        val scene = ScoreboardLayouts.scene(display("STAR POINT"), ScoreboardSettingsV1(showDecidingPoint = false))

        assertFalse(scene.items.filterIsInstance<SceneItem.Label>().any { it.text == "STAR POINT" })
        assertEquals(ScoreboardLayouts.scene(display(null), ScoreboardSettingsV1()), scene)
    }

    @Test
    fun theBadgeTextIsDarkOnALightAccentAndWhiteOnADarkAccent() {
        assertEquals(0x111111, ScoreboardBadge.textRgbOn(0xC4FF4D))
        assertEquals(0xFFFFFF, ScoreboardBadge.textRgbOn(0xD6267F))
    }

    @Test
    fun onlyTheDecidingPointGetsTheBadge() {
        // 3–3 in points: the 7th point is the golden point.
        val points = (1..7).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
        val outcomes = points.zip("1112221".toList()).associate { (p, c) -> p.id to if (c == '1') Outcome.P1 else Outcome.P2 }
        val goldenPoint = MatchRulesV1(deuce = DeuceRule.NO_AD)

        val padel = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true, rules = goldenPoint, sport = Sport.PADEL)
        assertEquals(listOf<String?>(null, null, null, null, null, null, "GOLDEN POINT"), padel.map { it.badge })

        val tennis = ScoreboardTimelineBuilder.buildSourcePointSpans(points, outcomes, rules = goldenPoint)
        assertEquals("DECIDING POINT", tennis.last().badge)

        val advantage = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true, sport = Sport.PADEL)
        assertTrue(advantage.all { it.badge == null })
        assertNull(ScoreboardComponent.display(advantage.last()).badge)
    }
}
