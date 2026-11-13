package org.litvin.export.scoreboard

import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardTimelineBuilder
import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetailV1
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardPosition
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import org.litvin.scoring.ScoringMode
import org.litvin.scoring.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-68: the "2ND SERVE" mark next to the scoreboard during a 2nd-serve clip. */
class ScoreboardServeMarkTest {
    private fun display(badge: String? = null, serveMark: String? = null) = ScoreboardComponent.display(
        OverlaySpan(
            startMs = 0L, endMs = 1_000L, text = "", p1Name = "Ana", p2Name = "Ben",
            p1Pts = 5, p2Pts = 5, badge = badge, serveMark = serveMark,
        ),
    )

    private fun ScoreboardScene.label(text: String) = items.filterIsInstance<SceneItem.Label>().single { it.text == text }

    private fun ScoreboardScene.tabOf(label: SceneItem.Label) = items.filterIsInstance<SceneItem.Box>().single { box ->
        box.height == ScoreboardBadge.HEIGHT && label.x > box.x && label.x < box.x + box.width &&
            label.y > box.y && label.y < box.y + box.height
    }

    /**
     * Five clips: a fault, the 2nd-serve point, a point on the 1st serve, a fault, and a let on the 2nd serve. Then the
     * double fault.
     */
    private val points = listOf(
        PointV1(id = "f1", startMs = 0, endMs = 2_000),
        PointV1(id = "p1", startMs = 4_000, endMs = 10_000),
        PointV1(id = "p2", startMs = 12_000, endMs = 18_000),
        PointV1(id = "f2", startMs = 20_000, endMs = 20_000),
        PointV1(id = "l", startMs = 22_000, endMs = 24_000),
        PointV1(id = "d", startMs = 26_000, endMs = 28_000),
    )
    private val score = ScoreV1(
        outcomes = mapOf(
            "f1" to Outcome.NONE, "p1" to Outcome.P1, "p2" to Outcome.P2, "f2" to Outcome.NONE,
            "l" to Outcome.NONE, "d" to Outcome.P2,
        ),
        scoringMode = ScoringMode.ADVANCED,
        details = mapOf(
            "f1" to PointDetailV1(Ending.SERVE_FAULT),
            "p1" to PointDetailV1(Ending.WINNER),
            "f2" to PointDetailV1(Ending.SERVE_FAULT),
            "l" to PointDetailV1(Ending.LET),
            "d" to PointDetailV1(Ending.SERVE_FAULT),
        ),
    )

    @Test
    fun theClipsOnThe2ndServeGetTheMark() {
        assertEquals(setOf("p1", "l", "d"), ScoreboardTimelineBuilder.secondServeIds(points, score))
    }

    @Test
    fun basicModeAndOtherSportsHaveNoMark() {
        assertTrue(ScoreboardTimelineBuilder.secondServeIds(points, score.copy(scoringMode = ScoringMode.BASIC)).isEmpty())
        assertTrue(ScoreboardTimelineBuilder.secondServeIds(points, score.copy(sport = Sport.PADEL)).isEmpty())
    }

    @Test
    fun theSpansOfThe2ndServeClipsHaveTheMarkInTheExportOfOnlyPoints() {
        val ids = ScoreboardTimelineBuilder.secondServeIds(points, score)
        // The export skips the clip of zero length, but its fault still makes the let a 2nd serve.
        val exported = points.filter { it.endMs > it.startMs }
        val spans = ScoreboardTimelineBuilder.build(exported, score.outcomes, idleTrim = true, secondServeIds = ids)

        assertEquals(listOf(null, "2ND SERVE", null, "2ND SERVE", "2ND SERVE"), spans.map { it.serveMark })
        assertEquals("2ND SERVE", ScoreboardComponent.display(spans[1]).serveMark)
    }

    @Test
    fun aFullVideoShowsTheMarkFromTheEndOfTheFaultToTheEndOfThe2ndServe() {
        val ids = ScoreboardTimelineBuilder.secondServeIds(points, score)
        val spans = ScoreboardTimelineBuilder.build(points, score.outcomes, idleTrim = false, fullVideo = true, secondServeIds = ids)

        val marked = spans.filter { it.serveMark != null }.map { it.startMs to it.endMs }
        assertEquals(listOf(2_000L to 10_000L, 20_000L to 24_000L, 24_000L to 28_000L), marked)
    }

    @Test
    fun theFinalScoreHasNoMark() {
        val span = OverlaySpan(
            startMs = 0L, endMs = 1_000L, text = "", completedSets = listOf(6 to 4), matchEnded = true, serveMark = "2ND SERVE",
        )

        assertNull(ScoreboardComponent.display(span).serveMark)
    }

    @Test
    fun everyStyleShowsTheMarkInsideTheSceneWithoutOverlap() {
        ScoreboardStyleId.entries.forEach { style ->
            ScoreboardPosition.entries.forEach { position ->
                val settings = ScoreboardSettingsV1(style = style, position = position)
                val plain = ScoreboardLayouts.scene(display(), settings)
                val mark = ScoreboardLayouts.scene(display(serveMark = "2ND SERVE"), settings)
                val both = ScoreboardLayouts.scene(display(badge = "STAR POINT", serveMark = "2ND SERVE"), settings)

                for (scene in listOf(mark, both)) {
                    scene.label("2ND SERVE")
                    // The badge and the mark are in one row.
                    assertEquals(plain.height + ScoreboardBadge.HEIGHT + ScoreboardBadge.GAP, scene.height, 0.001, "$style $position")
                    scene.items.filterIsInstance<SceneItem.Box>().forEach { box ->
                        assertTrue(box.x >= -1 && box.x + box.width <= scene.width + 1, "$style $position: $box")
                        assertTrue(box.y >= -1 && box.y + box.height <= scene.height + 1, "$style $position: $box")
                    }
                }
                val badgeTab = both.tabOf(both.label("STAR POINT"))
                val markTab = both.tabOf(both.label("2ND SERVE"))
                assertTrue(
                    badgeTab.x + badgeTab.width <= markTab.x || markTab.x + markTab.width <= badgeTab.x,
                    "$style $position: $badgeTab $markTab",
                )
            }
        }
    }
}
