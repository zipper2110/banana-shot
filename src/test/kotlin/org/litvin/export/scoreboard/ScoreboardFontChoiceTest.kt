package org.litvin.export.scoreboard

import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardDisplay
import org.litvin.scoring.ScoreboardFont
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-13, step 10: the font that the user chooses for all text of the scoreboard. */
class ScoreboardFontChoiceTest {
    private fun display(p1Name: String = "Alice Martin", p2Name: String = "Bob Lee", badge: String? = "GOLDEN POINT"): ScoreboardDisplay =
        ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L, endMs = 1_000L, text = "", p1Name = p1Name, p2Name = p2Name,
                p1Pts = 3, p2Pts = 1, gamesP1 = 4, gamesP2 = 2, completedSets = listOf(6 to 3), badge = badge,
            ),
        )

    private fun ScoreboardScene.labels() = items.filterIsInstance<SceneItem.Label>()

    private val chosenFonts = ScoreboardFont.entries - ScoreboardFont.STYLE

    @Test
    fun theStyleFontKeepsTheFontsOfEachStyle() {
        val fonts = ScoreboardStyleId.entries.flatMap { style ->
            ScoreboardLayouts.scene(display(), ScoreboardSettingsV1(style = style, font = ScoreboardFont.STYLE)).labels().map { it.font }
        }.toSet()
        // The styles use all bundled style fonts.
        assertEquals(setOf(INTER, INTER_BLACK, LIBERATION_SANS, ARCHIVO_BLACK, GELASIO, FIRA_SANS, OPEN_SANS, CASCADIA_MONO, CAVEAT), fonts)
    }

    @Test
    fun aChosenFontDrawsAllTextOfEveryStyleAndTheBadge() {
        ScoreboardStyleId.entries.forEach { style ->
            chosenFonts.forEach { font ->
                val scene = ScoreboardLayouts.scene(display(), ScoreboardSettingsV1(style = style, font = font))
                val labels = scene.labels()
                assertTrue(labels.any { it.text == "GOLDEN POINT" }, "$style $font")
                labels.forEach { label -> assertEquals(font.family, label.font, "$style $font: ${label.text}") }
            }
        }
    }

    @Test
    fun aBlackStyleFontBecomesTheBoldFaceOfTheChosenFont() {
        ScoreboardStyleId.entries.forEach { style ->
            val own = ScoreboardLayouts.scene(display(), ScoreboardSettingsV1(style = style)).labels()
            val chosen = ScoreboardLayouts.scene(display(), ScoreboardSettingsV1(style = style, font = ScoreboardFont.GELASIO)).labels()
            assertEquals(own.size, chosen.size, "$style")
            own.zip(chosen).forEach { (before, after) ->
                val heavy = before.bold || before.font == INTER_BLACK || before.font == ARCHIVO_BLACK
                assertEquals(heavy, after.bold, "$style: ${before.text} in ${before.font}")
            }
        }
    }

    @Test
    fun aTextThatTheChosenFontCannotShowUsesTheFallbackFont() {
        // Gelasio has no Cyrillic letters. Inter has them.
        val scene = ScoreboardLayouts.scene(
            display(p1Name = "Анна Иванова"),
            ScoreboardSettingsV1(style = ScoreboardStyleId.BROADCAST, font = ScoreboardFont.GELASIO),
        )
        val name = scene.labels().single { it.text.contains("ИВАНОВА") }
        assertEquals(INTER, name.font)
        assertTrue(scene.labels().filter { it !== name }.all { it.font == GELASIO })
    }

    @Test
    fun theChosenFontAppliesOnlyWhileTheSceneIsBuilt() {
        ScoreboardLayouts.scene(display(), ScoreboardSettingsV1(font = ScoreboardFont.CAVEAT))
        assertEquals(ScoreboardFonts.Face(GELASIO, false), ScoreboardFonts.face(GELASIO, false))
        assertEquals(INTER_BLACK, ScoreboardFonts.family(INTER_BLACK, "ABC"))
        ScoreboardFonts.withChosenFamily(CAVEAT) {
            assertEquals(ScoreboardFonts.Face(CAVEAT, true), ScoreboardFonts.face(ARCHIVO_BLACK, false))
            // The result of a face gives the same face again.
            assertEquals(ScoreboardFonts.Face(CAVEAT, true), ScoreboardFonts.face(CAVEAT, true))
        }
    }

    @Test
    fun theTextOfEachChosenFontStaysInsideTheBoard() {
        ScoreboardStyleId.entries.forEach { style ->
            ScoreboardFont.entries.forEach { font ->
                val scene = ScoreboardLayouts.scene(
                    display(p1Name = "Maximilian Oberhausen", badge = null),
                    ScoreboardSettingsV1(style = style, font = font),
                )
                scene.labels().forEach { label ->
                    // The scene measures each label in the family of the label. The video draws the same family.
                    val width = ScoreboardFonts.textWidth(label.text, label.font, label.bold, label.size, label.spacing)
                    val left = when (label.anchor) {
                        TextAnchor.CENTER -> label.x - width / 2
                        TextAnchor.TOP_RIGHT, TextAnchor.MIDDLE_RIGHT -> label.x - width
                        else -> label.x
                    }
                    assertTrue(left >= -2.0 && left + width <= scene.width + 2.0, "$style $font: ${label.text} $left..${left + width} of ${scene.width}")
                }
            }
        }
    }
}
